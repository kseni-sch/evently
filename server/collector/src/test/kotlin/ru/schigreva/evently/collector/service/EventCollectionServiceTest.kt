package ru.schigreva.evently.collector.service

import ru.schigreva.evently.events.domain.EventCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.exception.EventParserUnavailableException
import ru.schigreva.evently.collector.exception.EventParsingException
import ru.schigreva.evently.collector.exception.SourceUnavailableException
import ru.schigreva.evently.collector.repository.ProcessedPostRepository
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.service.EventService
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventCollectionServiceTest {
    private val source = mock(PostSource::class.java)
    private val parser = mock(EventParser::class.java)
    private val eventService = mock(EventService::class.java)
    private val hashes = mutableMapOf<String, String>()
    private val repository = object : ProcessedPostRepository {
        override fun findHash(sourceUrl: String): String? = hashes[sourceUrl]
        override fun save(post: SourcePost, contentHash: String) {
            hashes[post.sourceUrl] = contentHash
        }
    }
    private val importer = PostImportService(eventService, repository)
    private val service = EventCollectionService(source, parser, repository, importer)

    @BeforeEach
    fun setUp() {
        `when`(eventService.saveFromSource(anyString(), anyList())).thenAnswer { invocation ->
            invocation.getArgument<List<EventDraft>>(1).map {
                Event(UUID.randomUUID(), it.title, it.description, it.startDate, it.endDate,
                    it.startTime, it.endTime, it.location, it.minPrice, it.priceDescription,
                    it.category, it.sourceUrl)
            }
        }
    }

    @Test
    fun keepsMultipleEventsAndMarksPostWithoutEvents() {
        `when`(source.getPosts(2)).thenReturn(listOf(post("1"), post("2")))
        `when`(parser.parse(post("1"))).thenReturn(listOf(event("Концерт"), event("Выставка")))
        `when`(parser.parse(post("2"))).thenReturn(emptyList())

        val events = service.collect(2, 2)

        assertEquals(listOf("Концерт", "Выставка"), events.map { it.title })
        assertEquals(2, hashes.size)
        verify(eventService).saveFromSource(post("2").sourceUrl, emptyList())
    }

    @Test
    fun defaultCollectionProcessesAllHundredPosts() {
        val posts = (1..100).map { post(it.toString()) }
        `when`(source.getPosts(100)).thenReturn(posts)
        for (post in posts) {
            `when`(parser.parse(post)).thenReturn(emptyList())
        }

        service.collect()

        assertEquals(100, hashes.size)
    }

    @Test
    fun unchangedPostIsSkippedEvenAfterServiceRestart() {
        `when`(source.getPosts(1)).thenReturn(listOf(post("1")))
        `when`(parser.parse(post("1"))).thenReturn(emptyList())
        service.collect(1, 1)
        val restarted = EventCollectionService(source, parser, repository, importer)

        restarted.collect(1, 1)

        verify(parser, times(1)).parse(post("1"))
    }

    @Test
    fun changedPostIsParsedAgain() {
        val original = post("1")
        val changed = original.copy(text = "Изменённый текст")
        `when`(source.getPosts(1)).thenReturn(listOf(original), listOf(changed))
        `when`(parser.parse(original)).thenReturn(emptyList())
        `when`(parser.parse(changed)).thenReturn(emptyList())
        service.collect(1, 1)
        val oldHash = hashes[original.sourceUrl]

        service.collect(1, 1)

        verify(parser).parse(changed)
        assertNotEquals(oldHash, hashes[original.sourceUrl])
    }

    @Test
    fun nextRunContinuesWithRemainingPosts() {
        val posts = (1..4).map { post(it.toString()) }
        `when`(source.getPosts(4)).thenReturn(posts)
        for (post in posts) {
            `when`(parser.parse(post)).thenReturn(emptyList())
        }

        service.collect(4, 2)
        assertEquals(2, hashes.size)
        service.collect(4, 2)
        assertEquals(4, hashes.size)
        for (post in posts) {
            verify(parser, times(1)).parse(post)
        }
    }

    @Test
    fun providerFailureStopsCollectionAndIsRetriedLater() {
        `when`(source.getPosts(2)).thenReturn(listOf(post("1"), post("2")))
        `when`(parser.parse(post("1")))
            .thenThrow(EventParserUnavailableException("Превышен лимит"))
            .thenReturn(emptyList())
        `when`(parser.parse(post("2"))).thenReturn(emptyList())

        service.collect(2, 2)
        assertTrue(hashes.isEmpty())
        verify(parser, never()).parse(post("2"))
        service.collect(2, 2)
        assertEquals(2, hashes.size)
    }

    @Test
    fun sourceFailureDoesNotBlockNextRun() {
        `when`(source.getPosts(1))
            .thenThrow(SourceUnavailableException("Источник недоступен"))
            .thenReturn(emptyList())

        assertThrows(SourceUnavailableException::class.java) { service.collect(1, 1) }
        assertTrue(service.collect(1, 1).isEmpty())
        verify(source, times(2)).getPosts(1)
    }

    @Test
    fun concurrentRunDoesNotStartAnotherCollection() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        `when`(source.getPosts(1)).thenAnswer {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            emptyList<SourcePost>()
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<List<Event>> { service.collect(1, 1) }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertTrue(service.collect(1, 1).isEmpty())
            release.countDown()
            first.get(10, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
        verify(source, times(1)).getPosts(1)
    }

    @Test
    fun invalidPostDoesNotBlockOtherPostsAndIsRetriedLater() {
        `when`(source.getPosts(2)).thenReturn(listOf(post("1"), post("2")))
        `when`(parser.parse(post("1"))).thenThrow(EventParsingException("Некорректная дата"))
            .thenReturn(emptyList())
        `when`(parser.parse(post("2"))).thenReturn(emptyList())

        service.collect(2, 2)

        assertFalse(hashes.containsKey(post("1").sourceUrl))
        assertTrue(hashes.containsKey(post("2").sourceUrl))
        service.collect(2, 2)
        assertEquals(2, hashes.size)
        verify(parser, times(1)).parse(post("2"))
    }

    @Test
    fun invalidPostsCountTowardsBatchLimit() {
        `when`(source.getPosts(2)).thenReturn(listOf(post("1"), post("2")))
        `when`(parser.parse(post("1"))).thenThrow(EventParsingException("Некорректный ответ"))

        service.collect(2, 1)

        verify(parser, never()).parse(post("2"))
        assertTrue(hashes.isEmpty())
    }

    @Test
    fun invalidEventListDoesNotBlockFollowingPosts() {
        `when`(source.getPosts(2)).thenReturn(listOf(post("1"), post("2")))
        `when`(parser.parse(post("1"))).thenReturn(listOf(event("Концерт"), event("Концерт")))
        `when`(parser.parse(post("2"))).thenReturn(emptyList())
        `when`(eventService.saveFromSource(post("1").sourceUrl, listOf(event("Концерт"), event("Концерт"))))
            .thenThrow(IllegalArgumentException("Повторяющиеся названия"))

        service.collect(2, 2)

        assertFalse(hashes.containsKey(post("1").sourceUrl))
        assertTrue(hashes.containsKey(post("2").sourceUrl))
    }

    private fun post(id: String): SourcePost {
        return SourcePost(id, "Текст", "https://t.me/channel/$id", OffsetDateTime.parse("2026-10-21T12:00:00Z"))
    }

    private fun event(title: String): EventDraft {
        return EventDraft(title, "Описание", null, null, null, null, null, null, null, EventCategory.OTHER, post("1").sourceUrl)
    }
}
