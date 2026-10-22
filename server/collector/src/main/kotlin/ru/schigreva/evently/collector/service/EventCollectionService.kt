package ru.schigreva.evently.collector.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.exception.EventParserUnavailableException
import ru.schigreva.evently.collector.exception.EventParsingException
import ru.schigreva.evently.collector.repository.ProcessedPostRepository
import ru.schigreva.evently.events.domain.Event
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicBoolean

@Service
class EventCollectionService(
    private val postSource: PostSource,
    private val eventParser: EventParser,
    private val processedPostRepository: ProcessedPostRepository,
    private val postImportService: PostImportService
) {
    private val logger = LoggerFactory.getLogger(EventCollectionService::class.java)
    private val running = AtomicBoolean(false)

    fun collect(limit: Int = 100, batchSize: Int = limit): List<Event> {
        require(limit in 1..1000) { "Количество публикаций должно быть от 1 до 1000" }
        require(batchSize in 1..limit) { "Размер порции должен быть от 1 до количества публикаций" }
        if (!running.compareAndSet(false, true)) {
            logger.info("Предыдущий сбор ещё выполняется")
            return emptyList()
        }
        try {
            val posts = postSource.getPosts(limit)
            val events = mutableListOf<Event>()
            var processed = 0
            var skipped = 0

            for (post in posts) {
                val hash = contentHash(post)
                if (processedPostRepository.findHash(post.sourceUrl) == hash) {
                    skipped++
                    continue
                }
                if (processed >= batchSize) {
                    break
                }
                processed++
                try {
                    val parsedEvents = eventParser.parse(post)
                    events.addAll(postImportService.save(post, parsedEvents, hash))
                    logger.info("Из публикации {} сохранено {} событий", post.sourceUrl, parsedEvents.size)
                } catch (exception: EventParserUnavailableException) {
                    logger.warn("Сбор остановлен: {}", exception.message)
                    break
                } catch (exception: EventParsingException) {
                    logger.warn("Публикация {} пропущена: {}", post.sourceUrl, exception.message)
                } catch (exception: IllegalArgumentException) {
                    logger.warn("Некорректные события в публикации {}: {}", post.sourceUrl, exception.message)
                }
            }

            logger.info("Обработано {} публикаций, пропущено {}, сохранено {} событий", processed, skipped, events.size)
            return events
        } finally {
            running.set(false)
        }
    }

    private fun contentHash(post: SourcePost): String {
        val content = "${post.text}\n${post.publishedAt}".toByteArray(Charsets.UTF_8)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))
    }
}
