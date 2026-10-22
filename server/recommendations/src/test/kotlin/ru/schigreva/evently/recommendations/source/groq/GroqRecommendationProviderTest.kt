package ru.schigreva.evently.recommendations.source.groq

import ru.schigreva.evently.events.domain.EventCategory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.recommendations.exception.RecommendationsUnavailableException
import java.net.InetSocketAddress
import java.util.UUID

class GroqRecommendationProviderTest {
    private val mapper = jacksonObjectMapper()
    private lateinit var server: HttpServer
    private lateinit var provider: GroqRecommendationProvider
    private var status = 200
    private var body = ""
    private var requestBody = ""
    private var calls = 0
    private var transientLimit = false
    private val event = Event(UUID.randomUUID(), "Керамика", "Лепка из глины", null, null, null, null,
        "Студия", null, null, EventCategory.WORKSHOP, "https://t.me/channel/1")

    @BeforeEach
    fun start() {
        body = response(emptyList())
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/completions") { exchange ->
            calls++
            requestBody = exchange.requestBody.bufferedReader().readText()
            val code = if (transientLimit && calls == 1) 429 else status
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("retry-after", "0")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        provider = GroqRecommendationProvider(mapper, "http://127.0.0.1:${server.address.port}/completions",
            "test-key", "test-model", 3000, 0)
    }

    @AfterEach
    fun stop() { server.stop(0) }

    @Test
    fun sendsFreeTextAndEventFactsAndReadsMatch() {
        body = response(listOf(mapOf("eventId" to event.id.toString(), "score" to 95, "reason" to "Лепка из глины")))
        val interests = "Хочу лепить посуду, без громкой музыки"

        val result = provider.select(interests, listOf(event))

        assertEquals(event.id, result.single().eventId)
        val request = mapper.readTree(requestBody)
        val input = mapper.readTree(request.path("messages").path(1).path("content").asText())
        assertEquals(interests, input.path("interests").asText())
        assertEquals(event.description, input.path("events").path(0).path("description").asText())
        assertTrue(request.path("response_format").path("json_schema").path("strict").asBoolean())
        assertFalse(requestBody.contains("test-key"))
    }

    @Test
    fun splitsLongCatalogIntoSmallRequests() {
        val events = (1..6).map { event.copy(id = UUID.randomUUID()) }
        assertTrue(provider.select("Керамика", events).isEmpty())
        assertEquals(2, calls)
    }

    @Test
    fun retriesAfterTemporaryRateLimit() {
        transientLimit = true
        assertTrue(provider.select("Керамика", listOf(event)).isEmpty())
        assertEquals(2, calls)
    }

    @Test
    fun rejectsEventOutsideBatch() {
        body = response(listOf(mapOf("eventId" to UUID.randomUUID().toString(), "score" to 90, "reason" to "Причина")))
        assertThrows(RecommendationsUnavailableException::class.java) { provider.select("Керамика", listOf(event)) }
    }

    @Test
    fun rejectsBrokenAndUnfinishedResponses() {
        val invalid = listOf("not-json", " ", """{"choices":[{"finish_reason":"length"}]}""")
        for (value in invalid) {
            body = value
            assertThrows(RecommendationsUnavailableException::class.java) { provider.select("Керамика", listOf(event)) }
        }
    }

    @Test
    fun doesNotExposeProviderResponseOnError() {
        status = 401
        body = "test-key"
        val error = assertThrows(RecommendationsUnavailableException::class.java) { provider.select("Керамика", listOf(event)) }
        assertFalse(error.message!!.contains("test-key"))
    }

    private fun response(items: List<Map<String, Any>>): String {
        return mapper.writeValueAsString(mapOf("choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf(
            "content" to mapper.writeValueAsString(mapOf("recommendations" to items))
        )))))
    }
}
