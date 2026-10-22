package ru.schigreva.evently

import ru.schigreva.evently.events.domain.EventCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import java.time.OffsetDateTime
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.repository.ProcessedPostRepository
import ru.schigreva.evently.collector.service.PostImportService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.service.EventService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["collector.scheduling.enabled=false"])
@AutoConfigureMockMvc
@Import(EventPersistenceTest.FixedTimeConfig::class)
@Testcontainers
class EventPersistenceTest {
    @Autowired
    private lateinit var eventService: EventService
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var mvc: MockMvc
    @Autowired
    private lateinit var mapper: ObjectMapper

    @Autowired
    private lateinit var importer: PostImportService
    @Autowired
    private lateinit var processedPosts: ProcessedPostRepository

    @TestConfiguration(proxyBeanMethods = false)
    class FixedTimeConfig {
        @Bean
        @Primary
        fun testClock(): Clock = Clock.fixed(Instant.parse("2026-10-21T22:30:00Z"), ZoneId.of("Europe/Moscow"))
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @BeforeEach
    fun clearEvents() {
        jdbc.execute("TRUNCATE evently.event_publications, evently.favorites, evently.events, evently.event_sources, evently.processed_posts")
    }

    @Test
    fun repeatedImportUpdatesEventWithoutChangingId() {
        val original = event("Концерт")
        val first = eventService.saveFromSource(original.sourceUrl, listOf(original)).single()
        val updated = original.copy(description = "Новая программа", minPrice = BigDecimal("500.00"))

        val second = eventService.saveFromSource(original.sourceUrl, listOf(updated)).single()

        assertEquals(first.id, second.id)
        assertEquals(1L, count())
        assertEquals("Новая программа", jdbc.queryForObject("SELECT description FROM evently.events", String::class.java))
        assertEquals(BigDecimal("500.00"), jdbc.queryForObject("SELECT min_price FROM evently.events", BigDecimal::class.java))
    }

    @Test
    fun preservesIdsWhenOrderChangesAndRemovesMissingEvents() {
        val concert = event("Концерт")
        val exhibition = event("Выставка")
        val first = eventService.saveFromSource(concert.sourceUrl, listOf(concert, exhibition))

        val reordered = eventService.saveFromSource(concert.sourceUrl, listOf(exhibition, concert))
        assertEquals(first.map { it.id }.reversed(), reordered.map { it.id })

        eventService.saveFromSource(concert.sourceUrl, listOf(concert))
        assertEquals(1L, count())
        eventService.saveFromSource(concert.sourceUrl, emptyList())
        assertEquals(0L, count())
    }

    @Test
    fun mergesSameEventFromDifferentSources() {
        val first = event("Концерт")
        val second = first.copy(title = "КОНЦЕРТ!", sourceUrl = "https://another-site.test/events/2")

        val saved = eventService.saveFromSource(first.sourceUrl, listOf(first)).single()
        val duplicate = eventService.saveFromSource(second.sourceUrl, listOf(second)).single()

        assertEquals(saved.id, duplicate.id)
        assertEquals(setOf(first.sourceUrl, second.sourceUrl), duplicate.sourceUrls.toSet())
        assertEquals(1L, count())
        assertNull(jdbc.queryForObject("SELECT min_price FROM evently.events LIMIT 1", BigDecimal::class.java))
    }

    @Test
    fun rollsBackWholePostIfSavingFails() {
        val original = event("Концерт")
        eventService.saveFromSource(original.sourceUrl, listOf(original))
        val invalidForDatabase = event("Выставка").copy(minPrice = BigDecimal("1000000000000"))

        assertThrows(RuntimeException::class.java) {
            eventService.saveFromSource(original.sourceUrl, listOf(invalidForDatabase))
        }

        assertEquals(1L, count())
        assertEquals("Концерт", jdbc.queryForObject("SELECT title FROM evently.events", String::class.java))
    }

    @Test
    fun concurrentImportsDoNotCreateDuplicates() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val concert = event("Концерт")
        try {
            val tasks = (1..2).map {
                executor.submit<Any> {
                    check(start.await(10, TimeUnit.SECONDS))
                    eventService.saveFromSource(concert.sourceUrl, listOf(concert))
                }
            }
            start.countDown()
            for (task in tasks) {
                task.get(15, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1L, count())
    }

    @Test
    fun rejectsEventsFromAnotherPostBeforeSaving() {
        val concert = event("Концерт")

        assertThrows(IllegalArgumentException::class.java) {
            eventService.saveFromSource("https://t.me/channel/2", listOf(concert))
        }
        assertThrows(IllegalArgumentException::class.java) {
            eventService.saveFromSource(concert.sourceUrl, listOf(concert, concert))
        }
        assertEquals(0L, count())
    }

    @Test
    fun getsEventByIdWithUnknownFields() {
        val draft = event("Концерт").copy(minPrice = BigDecimal.ZERO)
        val saved = eventService.saveFromSource(draft.sourceUrl, listOf(draft)).single()

        mvc.perform(get("/api/events/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(saved.id.toString()))
            .andExpect(jsonPath("$.title").value("Концерт"))
            .andExpect(jsonPath("$.startDate").value("2026-10-30"))
            .andExpect(jsonPath("$.minPrice").value(0))
            .andExpect(jsonPath("$.startTime").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun missingEventReturns404() {
        mvc.perform(get("/api/events/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").isNotEmpty)
    }

    @Test
    fun dateFilterIncludesEventsOverlappingPeriod() {
        val festival = event("Фестиваль").copy(
            startDate = LocalDate.of(2026, 10, 20), endDate = LocalDate.of(2026, 10, 25))
        val later = event("Концерт")
        val unknown = event("Без даты").copy(startDate = null, endDate = null)
        eventService.saveFromSource(festival.sourceUrl, listOf(festival, later, unknown))

        mvc.perform(get("/api/events").param("dateFrom", "2026-10-25").param("dateTo", "2026-10-26"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Фестиваль"))
    }

    @Test
    fun combinesBudgetAndCategoryFilters() {
        val free = event("Бесплатный концерт").copy(minPrice = BigDecimal.ZERO)
        val paid = event("Платный концерт").copy(minPrice = BigDecimal("500"))
        val unknown = event("Без цены")
        val exhibition = event("Выставка").copy(minPrice = BigDecimal.ZERO, category = EventCategory.EXHIBITION)
        eventService.saveFromSource(free.sourceUrl, listOf(free, paid, unknown, exhibition))

        mvc.perform(get("/api/events").param("maxPrice", "0").param("category", " concert "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Бесплатный концерт"))

        mvc.perform(get("/api/events").param("maxPrice", "500").param("category", "CONCERT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun paginationHasStableOrderAndTotal() {
        val drafts = listOf(event("Первый"), event("Второй"), event("Третий"))
        eventService.saveFromSource(drafts[0].sourceUrl, drafts)
        val ids = mutableSetOf<String>()

        for (page in 0..2) {
            val result = mvc.perform(get("/api/events").param("page", page.toString()).param("size", "1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn()
            val id = mapper.readTree(result.response.contentAsString).path("items").path(0).path("id").asText()
            assertTrue(ids.add(id))
        }
        mvc.perform(get("/api/events").param("page", "3").param("size", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun invalidParametersReturn400() {
        val invalidQueries = listOf(
            "maxPrice=-1", "maxPrice=abc", "dateFrom=invalid", "dateFrom=2026-02-30",
            "dateFrom=2026-10-30&dateTo=2026-10-20", "page=-1", "page=abc",
            "size=0", "size=101", "page=2147483647", "category="
        )
        for (query in invalidQueries) {
            mvc.perform(get("/api/events?$query"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").isNotEmpty)
        }
        mvc.perform(get("/api/events/invalid-id"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").isNotEmpty)
    }

    @Test
    fun emptyCatalogReturns200AndEmptyPage() {
        mvc.perform(get("/api/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun eventAndProcessedMarkerCommitTogether() {
        val draft = event("Концерт")
        val post = SourcePost("channel/1", "Текст", draft.sourceUrl, OffsetDateTime.parse("2026-10-21T12:00:00Z"))
        importer.save(post, listOf(draft), "a".repeat(64))
        assertEquals("a".repeat(64), processedPosts.findHash(post.sourceUrl))

        assertThrows(RuntimeException::class.java) {
            importer.save(post, listOf(draft.copy(description = "Изменено")), "b".repeat(65))
        }

        assertEquals("a".repeat(64), processedPosts.findHash(post.sourceUrl))
        assertEquals("Описание", jdbc.queryForObject("SELECT description FROM evently.events", String::class.java))
    }

    @Test
    fun postWithoutEventsIsStillMarkedAsProcessed() {
        val post = SourcePost("channel/1", "Описание парка", "https://t.me/channel/1",
            OffsetDateTime.parse("2026-10-21T12:00:00Z"))

        importer.save(post, emptyList(), "a".repeat(64))

        assertEquals("a".repeat(64), processedPosts.findHash(post.sourceUrl))
        assertEquals(0L, count())
    }

    @Test
    fun failedEventSaveDoesNotMarkPostAsProcessed() {
        val draft = event("Концерт").copy(minPrice = BigDecimal("1000000000000"))
        val post = SourcePost("channel/1", "Текст", draft.sourceUrl, OffsetDateTime.parse("2026-10-21T12:00:00Z"))

        assertThrows(RuntimeException::class.java) {
            importer.save(post, listOf(draft), "a".repeat(64))
        }

        assertNull(processedPosts.findHash(post.sourceUrl))
        assertEquals(0L, count())
    }

    @Test
    fun defaultCatalogIncludesTodayOngoingAndFutureEventsInMoscow() {
        val today = LocalDate.of(2026, 10, 22)
        val past = event("Прошедшее").copy(startDate = today.minusDays(1), endDate = today.minusDays(1))
        val current = event("Сегодня").copy(startDate = today, endDate = today)
        val ongoing = event("Многодневное").copy(startDate = today.minusDays(2), endDate = today.plusDays(1))
        val future = event("Будущее")
        val unknown = event("Без даты").copy(startDate = null, endDate = null)
        val drafts = listOf(past, current, ongoing, future, unknown).map { it.copy(minPrice = BigDecimal.ZERO) }
        val saved = eventService.saveFromSource(past.sourceUrl, drafts)

        mvc.perform(get("/api/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items[*].title").value(org.hamcrest.Matchers.containsInAnyOrder("Сегодня", "Многодневное", "Будущее")))
        mvc.perform(get("/api/events").param("maxPrice", "0"))
            .andExpect(jsonPath("$.total").value(3))
        mvc.perform(get("/api/events/${saved[0].id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Прошедшее"))
        assertEquals(5L, count())
    }

    @Test
    fun explicitPeriodAllowsPastEvents() {
        val past = event("Прошедшее").copy(startDate = LocalDate.of(2026, 10, 10), endDate = LocalDate.of(2026, 10, 10))
        eventService.saveFromSource(past.sourceUrl, listOf(past, event("Будущее")))

        mvc.perform(get("/api/events").param("dateFrom", "2026-10-01").param("dateTo", "2026-10-15"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Прошедшее"))
        mvc.perform(get("/api/events").param("dateTo", "2026-10-15"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
    }

    @Test
    fun categoriesArePublicAndContainOther() {
        mvc.perform(get("/api/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(EventCategory.entries.size))
            .andExpect(jsonPath("$[?(@.code == 'OTHER')].title").value(org.hamcrest.Matchers.contains("Другое")))
    }

    @Test
    fun filtersOtherCategoryAndRejectsUnknownCategory() {
        val other = event("Настольные игры").copy(category = EventCategory.OTHER)
        eventService.saveFromSource(other.sourceUrl, listOf(other, event("Концерт")))

        mvc.perform(get("/api/events").param("category", "OTHER"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].category").value("OTHER"))
        mvc.perform(get("/api/events").param("category", "unknown"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun databaseRejectsCategoryOutsideEnum() {
        val saved = eventService.saveFromSource("https://t.me/channel/1", listOf(event("Концерт"))).single()

        assertThrows(org.springframework.dao.DataIntegrityViolationException::class.java) {
            jdbc.update("UPDATE evently.events SET category = 'invalid' WHERE id = ?", saved.id)
        }
        assertEquals(EventCategory.CONCERT, eventService.getById(saved.id).category)
    }

    @Test
    fun doesNotMergeDifferentDatesLocationsOrTimes() {
        val original = event("Концерт").copy(startTime = java.time.LocalTime.of(18, 0))
        val variants = listOf(
            original,
            original.copy(startDate = original.startDate!!.plusDays(1), endDate = original.endDate!!.plusDays(1)),
            original.copy(location = "Другая площадка"),
            original.copy(startTime = java.time.LocalTime.of(20, 0))
        )
        variants.forEachIndexed { index, draft ->
            val source = "https://example.test/$index"
            eventService.saveFromSource(source, listOf(draft.copy(sourceUrl = source)))
        }
        assertEquals(4L, count())
    }

    @Test
    fun doesNotMergeEventsWithoutDateOrLocation() {
        for (index in 1..2) {
            val source = "https://example.test/$index"
            val drafts = listOf(event("Без даты").copy(startDate = null, endDate = null, sourceUrl = source),
                event("Без места").copy(location = null, sourceUrl = source))
            eventService.saveFromSource(source, drafts)
        }
        assertEquals(4L, count())
    }

    @Test
    fun doesNotMergeAmbiguousSessionWithoutTime() {
        val first = event("Концерт").copy(startTime = java.time.LocalTime.of(18, 0))
        val second = first.copy(startTime = java.time.LocalTime.of(20, 0), sourceUrl = "https://example.test/2")
        val unknown = first.copy(startTime = null, sourceUrl = "https://example.test/3")
        listOf(first, second, unknown).forEach { eventService.saveFromSource(it.sourceUrl, listOf(it)) }
        assertEquals(3L, count())
    }

    @Test
    fun preservesEventWhenOneOfItsPublicationsDisappears() {
        val first = event("Концерт")
        val second = first.copy(sourceUrl = "https://example.test/2")
        val saved = eventService.saveFromSource(first.sourceUrl, listOf(first)).single()
        eventService.saveFromSource(second.sourceUrl, listOf(second))

        eventService.saveFromSource(first.sourceUrl, emptyList())

        assertEquals(listOf(second.sourceUrl), eventService.getById(saved.id).sourceUrls)
        assertEquals(1L, count())
        eventService.saveFromSource(second.sourceUrl, emptyList())
        assertEquals(0L, count())
    }

    @Test
    fun repeatedImportRecognizesPublicationTitleAndKeepsKnownDetails() {
        val first = event("Концерт").copy(startTime = java.time.LocalTime.of(18, 0), minPrice = BigDecimal("500"))
        val second = first.copy(title = "КОНЦЕРТ!", startTime = null, minPrice = null, sourceUrl = "https://example.test/2")
        val saved = eventService.saveFromSource(first.sourceUrl, listOf(first)).single()
        repeat(2) { eventService.saveFromSource(second.sourceUrl, listOf(second)) }
        val result = eventService.getById(saved.id)
        assertEquals(1L, count())
        assertEquals(first.startTime, result.startTime)
        assertEquals(BigDecimal("500.00"), result.minPrice)
        assertEquals(2, result.sourceUrls.size)
    }

    @Test
    fun changingOnePublicationDoesNotMoveSharedEventToAnotherDate() {
        val first = event("Концерт")
        val second = first.copy(sourceUrl = "https://example.test/2")
        val saved = eventService.saveFromSource(first.sourceUrl, listOf(first)).single()
        eventService.saveFromSource(second.sourceUrl, listOf(second))

        val changed = second.copy(startDate = second.startDate!!.plusDays(1), endDate = second.endDate!!.plusDays(1))
        val updated = eventService.saveFromSource(changed.sourceUrl, listOf(changed)).single()

        assertNotEquals(saved.id, updated.id)
        assertEquals(first.startDate, eventService.getById(saved.id).startDate)
        assertEquals(listOf(first.sourceUrl), eventService.getById(saved.id).sourceUrls)
        assertEquals(2L, count())
    }

    @Test
    fun concurrentImportsFromDifferentSourcesDoNotCreateDuplicates() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val tasks = (1..2).map { index ->
                executor.submit<Any> {
                    check(start.await(10, TimeUnit.SECONDS))
                    val draft = event("Концерт").copy(sourceUrl = "https://example.test/$index")
                    eventService.saveFromSource(draft.sourceUrl, listOf(draft))
                }
            }
            start.countDown()
            tasks.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(1L, count())
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM evently.event_publications", Long::class.java)!!)
    }

    @Test
    fun renamingSharedPublicationKeepsEventAndAllLinks() {
        val first = event("Концерт")
        val second = first.copy(sourceUrl = "https://example.test/2")
        val saved = eventService.saveFromSource(first.sourceUrl, listOf(first)).single()
        eventService.saveFromSource(second.sourceUrl, listOf(second))

        val renamed = first.copy(title = "Новая концертная программа")
        val result = eventService.saveFromSource(first.sourceUrl, listOf(renamed)).single()

        assertEquals(saved.id, result.id)
        assertEquals(2, result.sourceUrls.size)
        assertEquals(1L, count())
        assertEquals(saved.id, eventService.saveFromSource(first.sourceUrl, listOf(renamed)).single().id)
    }

    @Test
    fun renameDoesNotTakeIdOfAnotherEventWithSameDetails() {
        val first = event("Первый концерт")
        val second = event("Второй концерт")
        val saved = eventService.saveFromSource(first.sourceUrl, listOf(first, second))

        val renamed = first.copy(title = "Новое название")
        val result = eventService.saveFromSource(first.sourceUrl, listOf(renamed, second))

        assertEquals(saved[1].id, result[1].id)
        assertEquals(saved[0].id, result[0].id)
        assertNotEquals(saved[1].id, result[0].id)
        assertEquals(2L, count())
    }

    @Test
    fun renameDoesNotGuessBetweenSeveralOldEvents() {
        val first = event("Первое событие")
        val saved = eventService.saveFromSource(first.sourceUrl, listOf(first, event("Второе событие")))
        val result = eventService.saveFromSource(first.sourceUrl, listOf(first.copy(title = "Новое название"))).single()

        assertTrue(saved.none { it.id == result.id })
        assertEquals(1L, count())
    }

    private fun count(): Long {
        return jdbc.queryForObject("SELECT count(*) FROM evently.events", Long::class.java)!!
    }

    private fun event(title: String): EventDraft {
        return EventDraft(title, "Описание", LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30),
            null, null, "Санкт-Петербург", null, null, EventCategory.CONCERT, "https://t.me/channel/1")
    }
}
