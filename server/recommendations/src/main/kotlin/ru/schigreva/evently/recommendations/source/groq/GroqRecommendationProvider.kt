package ru.schigreva.evently.recommendations.source.groq

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.recommendations.domain.EventMatch
import ru.schigreva.evently.recommendations.exception.RecommendationsUnavailableException
import ru.schigreva.evently.recommendations.service.RecommendationProvider
import ru.schigreva.evently.recommendations.source.groq.models.RecommendationResponse
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class GroqRecommendationProvider(
    private val mapper: ObjectMapper,
    @param:Value("\${groq.url}") private val url: String,
    @param:Value("\${groq.api-key}") private val apiKey: String,
    @param:Value("\${groq.model}") private val model: String,
    @param:Value("\${groq.timeout-ms}") private val timeoutMs: Long,
    @param:Value("\${groq.retry-delay-ms}") private val retryDelayMs: Long = 60000
) : RecommendationProvider {
    private val logger = LoggerFactory.getLogger(GroqRecommendationProvider::class.java)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()
    private val prompt = requireNotNull(javaClass.getResource("/groq/recommendation-prompt.txt")).readText()
    private val schema = mapper.readTree(javaClass.getResource("/groq/recommendation-schema.json"))

    override fun select(interests: String, events: List<Event>): List<EventMatch> {
        if (apiKey.isBlank()) {
            throw RecommendationsUnavailableException("Не настроен сервис рекомендаций")
        }
        val result = mutableListOf<EventMatch>()
        for (batch in events.chunked(5)) {
            result.addAll(selectBatch(interests, batch))
        }
        return result
    }

    private fun selectBatch(interests: String, events: List<Event>): List<EventMatch> {
        val input = mapOf(
            "interests" to interests,
            "events" to events.map {
                mapOf(
                    "id" to it.id.toString(), "title" to it.title.take(200),
                    "description" to it.description.take(1200), "category" to it.category.title,
                    "startDate" to it.startDate?.toString(), "endDate" to it.endDate?.toString(),
                    "startTime" to it.startTime?.toString(), "endTime" to it.endTime?.toString(),
                    "location" to it.location?.take(300), "minPrice" to it.minPrice,
                    "priceDescription" to it.priceDescription?.take(300)
                )
            }
        )
        val body = mapOf(
            "model" to model, "temperature" to 0, "max_completion_tokens" to 2048,
            "messages" to listOf(
                mapOf("role" to "system", "content" to prompt),
                mapOf("role" to "user", "content" to mapper.writeValueAsString(input))
            ),
            "response_format" to mapOf("type" to "json_schema", "json_schema" to mapOf(
                "name" to "recommendations", "strict" to true, "schema" to schema
            ))
        )
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Evently/1.0")
            .timeout(Duration.ofMillis(timeoutMs))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
        val response = sendRequest(request)
        if (response.statusCode() != 200) {
            throw RecommendationsUnavailableException("Сервис рекомендаций отклонил запрос: HTTP ${response.statusCode()}")
        }
        try {
            val json = mapper.readTree(response.body())
                ?: throw RecommendationsUnavailableException("Сервис рекомендаций вернул пустой ответ")
            val choice = json.path("choices").path(0)
            if (choice.path("finish_reason").asText() != "stop") {
                throw RecommendationsUnavailableException("Сервис рекомендаций вернул незавершённый ответ")
            }
            val parsed = mapper.readValue(choice.path("message").path("content").asText(), RecommendationResponse::class.java)
            val ids = events.map { it.id }.toSet()
            if (parsed.recommendations.any { it.eventId !in ids }) {
                throw RecommendationsUnavailableException("Модель выбрала событие вне переданного списка")
            }
            return parsed.recommendations
        } catch (exception: JsonProcessingException) {
            throw RecommendationsUnavailableException("Сервис рекомендаций вернул некорректные данные")
        }
    }
    private fun sendRequest(request: HttpRequest): HttpResponse<String> {
        try {
            for (attempt in 0..3) {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 429) {
                    return response
                }
                val retryAfter = response.headers().firstValue("retry-after").orElse("").toDoubleOrNull()
                val waitMs = if (retryAfter != null && retryAfter.isFinite() && retryAfter >= 0) {
                    kotlin.math.ceil(retryAfter * 1000).toLong()
                } else {
                    retryDelayMs
                }
                if (attempt == 3 || waitMs > 60000) {
                    throw RecommendationsUnavailableException("Достигнут лимит Groq. Попробуйте получить рекомендации позже")
                }
                logger.info("Лимит Groq при подборе рекомендаций, повтор через {} мс", waitMs)
                Thread.sleep(waitMs)
            }
        } catch (exception: IOException) {
            throw RecommendationsUnavailableException("Не удалось соединиться с сервисом рекомендаций", exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RecommendationsUnavailableException("Запрос рекомендаций прерван", exception)
        }
        throw RecommendationsUnavailableException("Не удалось получить рекомендации")
    }

}
