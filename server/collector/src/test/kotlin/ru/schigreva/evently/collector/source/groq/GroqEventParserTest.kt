package ru.schigreva.evently.collector.source.groq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.exception.EventParserUnavailableException
import ru.schigreva.evently.collector.exception.EventParsingException
import java.math.BigDecimal
import ru.schigreva.evently.events.domain.EventCategory
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

class GroqEventParserTest {
    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private lateinit var server: HttpServer
    private lateinit var parser: GroqEventParser
    private var status = 200
    private var retryStatuses = mutableListOf<Int>()
    private var retryAfter: String? = null
    private var requestCount = 0
    private var responseBody = ""
    private var requestBody = ""
    private val post = SourcePost(
        "channel/10", "Анонс события", "https://t.me/channel/10",
        OffsetDateTime.parse("2026-10-21T11:10:00Z")
    )

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/completions") { exchange ->
            requestCount++
            requestBody = exchange.requestBody.bufferedReader().readText()
            if (retryAfter != null) {
                exchange.responseHeaders.set("retry-after", retryAfter)
            }
            val responseStatus = if (retryStatuses.isEmpty()) status else retryStatuses.removeAt(0)
            val body = responseBody.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            exchange.sendResponseHeaders(responseStatus, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        parser = GroqEventParser(
            mapper, "http://127.0.0.1:${server.address.port}/completions",
            "test-key", "test-model", 3000, 0
        )
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun parsesMultipleEventsAndPreservesUnknownValues() {
        val first = event()
        val second = event().toMutableMap()
        second["title"] = "Выставка"
        second["minPrice"] = null
        second["location"] = null
        setResponse(listOf(first, second))

        val events = parser.parse(post)

        assertEquals(2, events.size)
        assertEquals(LocalDate.parse("2026-10-24"), events[0].startDate)
        assertEquals(LocalTime.parse("11:00"), events[0].startTime)
        assertEquals(BigDecimal.ZERO, events[0].minPrice)
        assertNull(events[0].endTime)
        assertNull(events[1].minPrice)
        assertNull(events[1].location)
        assertEquals(post.sourceUrl, events[0].sourceUrl)
        assertEquals(post.sourceUrl, events[1].sourceUrl)
    }

    @Test
    fun passesPublicationTimeAndStrictSchemaWithoutPuttingKeyInBody() {
        setResponse(emptyList())

        assertTrue(parser.parse(post).isEmpty())

        val request = mapper.readTree(requestBody)
        val input = mapper.readTree(request.path("messages").path(1).path("content").asText())
        assertTrue(input.path("publishedAt").asText().startsWith("2026-10-21T14:10"))
        assertEquals(post.text, input.path("text").asText())
        assertTrue(request.path("response_format").path("json_schema").path("strict").asBoolean())
        assertFalse(requestBody.contains("test-key"))
    }

    @Test
    fun rejectsNegativePrice() {
        val event = event().toMutableMap()
        event["minPrice"] = -100
        setResponse(listOf(event))

        assertThrows(EventParsingException::class.java) { parser.parse(post) }
    }

    @Test
    fun rejectsEndDateBeforeStartDate() {
        val event = event().toMutableMap()
        event["endDate"] = "2026-10-20"
        setResponse(listOf(event))

        assertThrows(EventParsingException::class.java) { parser.parse(post) }
    }

    @Test
    fun rejectsInvalidDate() {
        val event = event().toMutableMap()
        event["startDate"] = "неизвестно"
        setResponse(listOf(event))

        assertThrows(EventParsingException::class.java) { parser.parse(post) }
    }

    @Test
    fun retriesSameRequestAfterTemporaryRateLimit() {
        retryStatuses.addAll(listOf(429, 200))
        retryAfter = "0"
        setResponse(emptyList())

        assertTrue(parser.parse(post).isEmpty())
        assertEquals(2, requestCount)
    }

    @Test
    fun doesNotWaitForDailyLimitToReset() {
        status = 429
        retryAfter = "3600"

        assertThrows(EventParserUnavailableException::class.java) { parser.parse(post) }
        assertEquals(1, requestCount)
    }

    @Test
    fun reportsRateLimit() {
        status = 429

        val exception = assertThrows(EventParserUnavailableException::class.java) { parser.parse(post) }

        assertTrue(exception.message!!.contains("лимит"))
        assertEquals(4, requestCount)
    }

    @Test
    fun doesNotExposeProviderResponseOnAuthenticationError() {
        status = 401
        responseBody = "test-key"

        val exception = assertThrows(EventParserUnavailableException::class.java) { parser.parse(post) }

        assertTrue(exception.message!!.contains("401"))
        assertFalse(exception.message!!.contains("test-key"))
    }

    @Test
    fun rejectsTruncatedResponse() {
        responseBody = mapper.writeValueAsString(mapOf(
            "choices" to listOf(mapOf("finish_reason" to "length"))
        ))

        assertThrows(EventParsingException::class.java) { parser.parse(post) }
    }

    @Test
    fun rejectsInvalidJson() {
        responseBody = "not json"

        assertThrows(EventParsingException::class.java) { parser.parse(post) }
    }

    @Test
    fun rejectsEmptyResponse() {
        responseBody = " "

        assertThrows(EventParsingException::class.java) { parser.parse(post) }
    }

    @Test
    fun reportsMissingApiKey() {
        val parserWithoutKey = GroqEventParser(mapper, "http://localhost", "", "test-model", 3000)

        assertThrows(EventParserUnavailableException::class.java) { parserWithoutKey.parse(post) }
    }

    @Test
    fun acceptsOtherForEventsOutsideMainCategories() {
        val event = event().toMutableMap()
        event["category"] = "OTHER"
        setResponse(listOf(event))

        assertEquals(EventCategory.OTHER, parser.parse(post).single().category)
    }

    @Test
    fun rejectsCategoriesOutsideEnum() {
        val event = event().toMutableMap()
        event["category"] = "пожар"
        setResponse(listOf(event))

        assertThrows(EventParsingException::class.java) { parser.parse(post) }
    }

    private fun setResponse(events: List<Map<String, Any?>>) {
        val content = mapper.writeValueAsString(mapOf("events" to events))
        responseBody = mapper.writeValueAsString(mapOf(
            "choices" to listOf(mapOf(
                "finish_reason" to "stop",
                "message" to mapOf("content" to content)
            ))
        ))
    }

    private fun event(): Map<String, Any?> {
        return mapOf(
            "title" to "Фестиваль",
            "description" to "Лекции и мастер-классы",
            "startDate" to "2026-10-24",
            "endDate" to "2026-10-25",
            "startTime" to "11:00",
            "endTime" to null,
            "location" to "Площадка, улица, 1",
            "minPrice" to 0,
            "priceDescription" to "Бесплатно",
            "category" to "FESTIVAL_FAIR"
        )
    }
}
