package ru.schigreva.evently

import ru.schigreva.evently.events.domain.EventCategory
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.mockito.Mockito.*
import ru.schigreva.evently.recommendations.service.RecommendationProvider
import ru.schigreva.evently.recommendations.domain.EventMatch
import ru.schigreva.evently.recommendations.exception.RecommendationsUnavailableException
import java.time.ZoneId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import com.fasterxml.jackson.databind.ObjectMapper
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.service.EventService
import ru.schigreva.evently.favorites.service.FavoriteService
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import ru.schigreva.evently.users.service.UserService
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["collector.scheduling.enabled=false"])
@AutoConfigureMockMvc
@Testcontainers
class UserApiTest {
    @Autowired
    private lateinit var mvc: MockMvc
    @Autowired
    private lateinit var jdbc: JdbcTemplate
    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var eventService: EventService
    @Autowired
    private lateinit var favoriteService: FavoriteService
    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockitoBean
    private lateinit var recommendationProvider: RecommendationProvider

    companion object {
        private const val ISSUER = "http://localhost/test-issuer"
        private val key = RSAKeyGenerator(2048).keyID("test-key").generate()
        private val keyServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/certs") { exchange ->
                val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { ISSUER }
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://127.0.0.1:${keyServer.address.port}/certs"
            }
        }

        @AfterAll
        @JvmStatic
        fun stopKeyServer() {
            keyServer.stop(0)
        }
    }

    @BeforeEach
    fun clearUsers() {
        jdbc.execute("TRUNCATE evently.event_publications, evently.favorites, evently.users, evently.events, evently.event_sources, evently.processed_posts")
    }

    @Test
    fun swaggerIsPublicAndDocumentsBearerAuthentication() {
        mvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")))
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("Evently API"))
            .andExpect(jsonPath("$.components.securitySchemes.Bearer.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.Bearer.scheme").value("bearer"))
            .andExpect(jsonPath("$.security[0].Bearer").isArray)
            .andExpect(jsonPath("$.paths['/api/events'].get.security").isEmpty)
            .andExpect(jsonPath("$.paths['/api/categories'].get.security").isEmpty)
            .andExpect(jsonPath("$.paths['/api/users/me'].get.parameters").doesNotExist())
        mvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.persistAuthorization").value(true))
    }

    @Test
    fun profileRequiresAuthenticationButCatalogIsPublic() {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").isNotEmpty)
        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Ксения","interests":"Музыка"}"""))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/events")).andExpect(status().isOk)
    }

    @Test
    fun createsProfileOnceFromVerifiedToken() {
        val auth = "Bearer " + token("user-a")
        mvc.perform(get("/api/users/me").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Ксения"))
            .andExpect(jsonPath("$.interests").value(""))
            .andExpect(jsonPath("$.authSubject").doesNotExist())
        mvc.perform(get("/api/users/me").header("Authorization", auth)).andExpect(status().isOk)
        assertEquals(1L, userCount())
    }

    @Test
    fun updatesFreeTextInterestsAndKeepsThemOnNextLogin() {
        val auth = "Bearer " + token("user-a")
        mvc.perform(put("/api/users/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":" Ксюша ","interests":"Хочу уютные концерты и мастер-классы по керамике"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Ксюша"))
        mvc.perform(get("/api/users/me").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Ксюша"))
            .andExpect(jsonPath("$.interests").value("Хочу уютные концерты и мастер-классы по керамике"))
    }

    @Test
    fun usersHaveSeparateProfiles() {
        val first = "Bearer " + token("user-a")
        val second = "Bearer " + token("user-b")
        mvc.perform(put("/api/users/me").header("Authorization", first).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Первая","interests":"Керамика"}"""))
            .andExpect(status().isOk)
        mvc.perform(get("/api/users/me").header("Authorization", second))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.interests").value(""))
        mvc.perform(get("/api/users/me").header("Authorization", first))
            .andExpect(jsonPath("$.interests").value("Керамика"))
    }

    @Test
    fun rejectsExpiredWrongIssuerWrongAudienceAndForgedTokens() {
        val otherKey = RSAKeyGenerator(2048).keyID("test-key").generate()
        val invalid = listOf(
            token("user-a", expiresAt = Instant.now().minusSeconds(300)),
            token("user-a", issuer = "http://other-issuer"),
            token("user-a", audience = "other-app"),
            token("user-a", signingKey = otherKey),
            "not-a-token"
        )
        for (value in invalid) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer $value"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.message").isNotEmpty)
        }
        assertEquals(0L, userCount())
    }

    @Test
    fun rejectsInvalidProfileBodies() {
        val auth = "Bearer " + token("user-a")
        val bodies = listOf(
            """{"name":" ","interests":""}""",
            """{"name":"${"a".repeat(101)}","interests":""}""",
            """{"name":"Ксения","interests":"${"a".repeat(2001)}"}""",
            """{"name":"Ксения"}""",
            "not-json"
        )
        for (body in bodies) {
            mvc.perform(put("/api/users/me").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").isNotEmpty)
        }
        assertEquals(0L, userCount())
    }

    @Test
    fun concurrentFirstRequestsCreateOnlyOneProfile() {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map {
                executor.submit<Any> {
                    check(start.await(10, TimeUnit.SECONDS))
                    userService.getProfile("user-a", "Ксения")
                }
            }
            start.countDown()
            tasks.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(1L, userCount())
    }

    @Test
    fun favoritesRequireAuthentication() {
        val id = UUID.randomUUID()
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/favorites/$id")).andExpect(status().isUnauthorized)
        mvc.perform(delete("/api/favorites/$id")).andExpect(status().isUnauthorized)
    }

    @Test
    fun addsListsAndRemovesFavoriteWithoutDuplicates() {
        val event = createEvents().single()
        val auth = "Bearer " + token("user-a")
        repeat(2) {
            mvc.perform(put("/api/favorites/${event.id}").header("Authorization", auth))
                .andExpect(status().isNoContent)
        }
        mvc.perform(get("/api/favorites").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(event.id.toString()))
            .andExpect(jsonPath("$.items[0].title").value(event.title))
        repeat(2) {
            mvc.perform(delete("/api/favorites/${event.id}").header("Authorization", auth))
                .andExpect(status().isNoContent)
        }
        mvc.perform(get("/api/favorites").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items").isEmpty)
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM evently.events", Long::class.java)!!)
    }

    @Test
    fun anotherUserCannotSeeOrRemoveSavedEvents() {
        val event = createEvents().single()
        val first = "Bearer " + token("user-a")
        val second = "Bearer " + token("user-b")
        mvc.perform(put("/api/favorites/${event.id}").header("Authorization", first))
            .andExpect(status().isNoContent)
        mvc.perform(get("/api/favorites").header("Authorization", second))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
        mvc.perform(delete("/api/favorites/${event.id}").header("Authorization", second))
            .andExpect(status().isNoContent)
        mvc.perform(get("/api/favorites").header("Authorization", first))
            .andExpect(jsonPath("$.total").value(1))
        mvc.perform(put("/api/favorites/${event.id}").header("Authorization", second))
            .andExpect(status().isNoContent)
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM evently.favorites", Long::class.java)!!)
    }

    @Test
    fun favoritesRejectMissingEventsAndInvalidParameters() {
        val auth = "Bearer " + token("user-a")
        mvc.perform(put("/api/favorites/${UUID.randomUUID()}").header("Authorization", auth))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").isNotEmpty)
        mvc.perform(put("/api/favorites/invalid-id").header("Authorization", auth))
            .andExpect(status().isBadRequest)
        for (query in listOf("page=-1", "size=0", "size=101", "page=2147483647")) {
            mvc.perform(get("/api/favorites?$query").header("Authorization", auth))
                .andExpect(status().isBadRequest)
        }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM evently.favorites", Long::class.java)!!)
    }

    @Test
    fun concurrentAddsCreateOnlyOneFavorite() {
        val event = createEvents().single()
        val user = userService.getProfile("user-a", "Ксения")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map {
                executor.submit {
                    check(start.await(10, TimeUnit.SECONDS))
                    favoriteService.add(user.id, event.id)
                }
            }
            start.countDown()
            tasks.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM evently.favorites", Long::class.java)!!)
    }

    @Test
    fun renamedEventKeepsItsIdAndFavorite() {
        val event = createEvents().single()
        val auth = "Bearer " + token("user-a")
        mvc.perform(put("/api/favorites/${event.id}").header("Authorization", auth))
            .andExpect(status().isNoContent)
        val renamed = EventDraft("Совсем новое название", event.description, event.startDate, event.endDate,
            event.startTime, event.endTime, event.location, event.minPrice, event.priceDescription,
            event.category, event.sourceUrl)

        val saved = eventService.saveFromSource(event.sourceUrl, listOf(renamed)).single()
        assertEquals(event.id, saved.id)
        assertEquals(renamed.title, saved.title)
        assertEquals(event.id, eventService.saveFromSource(event.sourceUrl, listOf(renamed)).single().id)
        mvc.perform(get("/api/favorites").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(event.id.toString()))
            .andExpect(jsonPath("$.items[0].title").value(renamed.title))
    }

    @Test
    fun favoriteSurvivesRemovingOneOfSeveralPublications() {
        val event = createEvents().single()
        val auth = "Bearer " + token("user-a")
        mvc.perform(put("/api/favorites/${event.id}").header("Authorization", auth))
            .andExpect(status().isNoContent)
        val other = EventDraft(event.title, event.description, event.startDate, event.endDate,
            event.startTime, event.endTime, event.location, event.minPrice, event.priceDescription,
            event.category, "https://another-source.test/events/1")
        eventService.saveFromSource(other.sourceUrl, listOf(other))
        eventService.saveFromSource(event.sourceUrl, emptyList())

        mvc.perform(get("/api/favorites").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(event.id.toString()))
            .andExpect(jsonPath("$.items[0].sourceUrls[0]").value(other.sourceUrl))
    }

    @Test
    fun updatedEventRemainsInFavoritesAndDeletedEventIsRemoved() {
        val event = createEvents().single()
        val auth = "Bearer " + token("user-a")
        mvc.perform(put("/api/favorites/${event.id}").header("Authorization", auth))
            .andExpect(status().isNoContent)
        val updated = EventDraft(event.title, "Обновлённая программа", event.startDate, event.endDate,
            event.startTime, event.endTime, event.location, event.minPrice, event.priceDescription,
            event.category, event.sourceUrl)
        eventService.saveFromSource(event.sourceUrl, listOf(updated))

        mvc.perform(get("/api/favorites").header("Authorization", auth))
            .andExpect(jsonPath("$.items[0].id").value(event.id.toString()))
            .andExpect(jsonPath("$.items[0].description").value("Обновлённая программа"))

        eventService.saveFromSource(event.sourceUrl, emptyList())
        mvc.perform(get("/api/favorites").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM evently.favorites", Long::class.java)!!)
    }

    @Test
    fun favoritesPaginationHasNoRepeatedEvents() {
        val events = createEvents(3)
        val user = userService.getProfile("user-a", "Ксения")
        for (event in events) {
            favoriteService.add(user.id, event.id)
        }
        val auth = "Bearer " + token("user-a")
        val ids = mutableSetOf<String>()
        for (page in 0..1) {
            val result = mvc.perform(get("/api/favorites").param("page", page.toString()).param("size", "2")
                .header("Authorization", auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(if (page == 0) 2 else 1))
                .andReturn()
            for (event in mapper.readTree(result.response.contentAsString).path("items")) {
                assertTrue(ids.add(event.path("id").asText()))
            }
        }
        assertEquals(events.map { it.id.toString() }.toSet(), ids)
    }

    @Test
    fun pastEventRemainsInFavoritesButIsHiddenFromDefaultCatalog() {
        val event = createEvents().single()
        val past = EventDraft(event.title, event.description, LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 1),
            event.startTime, event.endTime, event.location, event.minPrice, event.priceDescription,
            event.category, event.sourceUrl)
        eventService.saveFromSource(event.sourceUrl, listOf(past))
        val auth = "Bearer " + token("user-a")
        mvc.perform(put("/api/favorites/${event.id}").header("Authorization", auth))
            .andExpect(status().isNoContent)

        mvc.perform(get("/api/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
        mvc.perform(get("/api/favorites").header("Authorization", auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(event.id.toString()))
    }

    @Test
    fun recommendationsRequireAuthenticationAndProfileInterests() {
        mvc.perform(get("/api/recommendations")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer " + token("user-a")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Сначала укажите свои интересы в профиле"))
        verifyNoInteractions(recommendationProvider)
    }

    @Test
    fun recommendationsUseOwnInterestsAndReturnRealEvents() {
        val event = createEvents().single()
        val interests = "Нравится камерная живая музыка, а не шумные вечеринки"
        userService.updateProfile("user-a", "Ксения", interests)
        userService.updateProfile("user-b", "Другой пользователь", "Керамика")
        `when`(recommendationProvider.select(anyString(), anyList())).thenAnswer { invocation ->
            assertEquals(interests, invocation.getArgument<String>(0))
            assertEquals(listOf(event.id), invocation.getArgument<List<Event>>(1).map { it.id })
            listOf(EventMatch(event.id, 95, "Концерт соответствует интересу к живой музыке"))
        }

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer " + token("user-a")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].event.id").value(event.id.toString()))
            .andExpect(jsonPath("$[0].reason").isNotEmpty)
        verify(recommendationProvider).select(anyString(), anyList())
    }

    @Test
    fun unavailableLlmReturns503InsteadOfFakeRecommendations() {
        createEvents()
        userService.updateProfile("user-a", "Ксения", "Музыка")
        `when`(recommendationProvider.select(anyString(), anyList()))
            .thenThrow(RecommendationsUnavailableException("Достигнут лимит Groq"))

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer " + token("user-a")))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value("Достигнут лимит Groq"))
    }

    @Test
    fun recommendationsDoNotUsePastEvents() {
        val event = createEvents().single()
        val past = EventDraft(event.title, event.description, LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 1),
            null, null, event.location, event.minPrice, event.priceDescription, event.category, event.sourceUrl)
        eventService.saveFromSource(event.sourceUrl, listOf(past))
        userService.updateProfile("user-a", "Ксения", "Музыка")

        mvc.perform(get("/api/recommendations").header("Authorization", "Bearer " + token("user-a")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
        verifyNoInteractions(recommendationProvider)
    }

    private fun createEvents(count: Int = 1): List<Event> {
        val url = "https://t.me/test-channel/1"
        val date = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(7)
        val drafts = (1..count).map {
            EventDraft("Концерт $it", "Описание", date, date,
                null, null, "Санкт-Петербург", BigDecimal.ZERO, "Бесплатно", EventCategory.CONCERT, url)
        }
        return eventService.saveFromSource(url, drafts)
    }

    private fun userCount(): Long {
        return requireNotNull(jdbc.queryForObject("SELECT count(*) FROM evently.users", Long::class.java))
    }

    private fun token(
        subject: String,
        expiresAt: Instant = Instant.now().plusSeconds(300),
        issuer: String = ISSUER,
        audience: String = "evently-api",
        signingKey: RSAKey = key
    ): String {
        val claims = JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience)
            .issueTime(Date.from(Instant.now().minusSeconds(600)))
            .expirationTime(Date.from(expiresAt)).claim("name", "Ксения").build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).build(), claims)
        jwt.sign(RSASSASigner(signingKey))
        return jwt.serialize()
    }
}
