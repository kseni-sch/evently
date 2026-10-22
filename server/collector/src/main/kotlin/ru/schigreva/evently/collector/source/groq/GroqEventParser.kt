package ru.schigreva.evently.collector.source.groq

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.exception.EventParserUnavailableException
import ru.schigreva.evently.collector.exception.EventParsingException
import ru.schigreva.evently.collector.service.EventParser
import ru.schigreva.evently.collector.source.groq.models.ParsedEventsResponse
import ru.schigreva.evently.events.domain.EventDraft
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZoneId
import org.slf4j.LoggerFactory

@Component
class GroqEventParser(
    private val objectMapper: ObjectMapper,
    @param:Value("\${groq.url}") private val url: String,
    @param:Value("\${groq.api-key}") private val apiKey: String,
    @param:Value("\${groq.model}") private val model: String,
    @param:Value("\${groq.timeout-ms}") private val timeoutMs: Long,
    @param:Value("\${groq.retry-delay-ms}") private val retryDelayMs: Long = 60000
) : EventParser {
    private val logger = LoggerFactory.getLogger(GroqEventParser::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .build()
    private val prompt = javaClass.getResource("/groq/event-parser-prompt.txt")!!.readText()
    private val schema = objectMapper.readTree(javaClass.getResource("/groq/event-parser-schema.json"))

    override fun parse(post: SourcePost): List<EventDraft> {
        if (apiKey.isBlank()) {
            throw EventParserUnavailableException("Не настроен GROQ_API_KEY")
        }

        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Evently/1.0")
            .timeout(Duration.ofMillis(timeoutMs))
            .POST(HttpRequest.BodyPublishers.ofString(createRequestBody(post)))
            .build()

        val response = sendRequest(request)
        if (response.statusCode() != 200) {
            throw EventParserUnavailableException("Groq отклонил запрос: HTTP ${response.statusCode()}")
        }

        try {
            val responseJson = objectMapper.readTree(response.body())
                ?: throw EventParsingException("Groq вернул пустой ответ")
            val choice = responseJson.path("choices").path(0)
            if (choice.path("finish_reason").asText() != "stop") {
                throw EventParsingException("Groq вернул незавершённый ответ")
            }

            val content = choice.path("message").path("content").asText()
            val result = objectMapper.readValue(content, ParsedEventsResponse::class.java)
            val events = mutableListOf<EventDraft>()
            for (event in result.events) {
                events.add(EventDraft(
                    title = event.title,
                    description = event.description,
                    startDate = event.startDate,
                    endDate = event.endDate,
                    startTime = event.startTime,
                    endTime = event.endTime,
                    location = event.location,
                    minPrice = event.minPrice,
                    priceDescription = event.priceDescription,
                    category = event.category,
                    sourceUrl = post.sourceUrl
                ))
            }
            return events
        } catch (exception: JsonProcessingException) {
            throw EventParsingException("Groq вернул некорректные данные события")
        } catch (exception: IllegalArgumentException) {
            throw EventParsingException("Groq вернул некорректные поля события")
        }
    }

    private fun sendRequest(request: HttpRequest): HttpResponse<String> {
        try {
            for (attempt in 0..3) {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 429) {
                    return response
                }
                val retryAfter = response.headers().firstValue("retry-after").orElse("")
                    .toDoubleOrNull()
                val waitMs = if (retryAfter != null && retryAfter.isFinite() && retryAfter >= 0) {
                    kotlin.math.ceil(retryAfter * 1000).toLong()
                } else {
                    retryDelayMs
                }
                if (attempt == 3 || waitMs > 60000) {
                    throw EventParserUnavailableException("Превышен лимит Groq. Сбор продолжится при следующем запуске")
                }
                logger.info("Достигнут лимит Groq, повтор запроса через {} мс", waitMs)
                Thread.sleep(waitMs)
            }
        } catch (exception: IOException) {
            throw EventParserUnavailableException("Не удалось соединиться с Groq", exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw EventParserUnavailableException("Запрос к Groq прерван", exception)
        }
        throw EventParserUnavailableException("Не удалось выполнить запрос к Groq")
    }

    private fun createRequestBody(post: SourcePost): String {
        val input = mapOf(
            "text" to post.text,
            "publishedAt" to post.publishedAt.atZoneSameInstant(ZoneId.of("Europe/Moscow"))
                .toOffsetDateTime().toString()
        )
        val body = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to prompt),
                mapOf("role" to "user", "content" to objectMapper.writeValueAsString(input))
            ),
            "temperature" to 0,
            "max_completion_tokens" to 4096,
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to mapOf("name" to "events", "strict" to true, "schema" to schema)
            )
        )
        return objectMapper.writeValueAsString(body)
    }
}
