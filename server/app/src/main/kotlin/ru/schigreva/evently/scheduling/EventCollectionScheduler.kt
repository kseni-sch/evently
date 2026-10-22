package ru.schigreva.evently.scheduling

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.schigreva.evently.collector.service.EventCollectionService

@Component
@EnableScheduling
@ConditionalOnProperty(name = ["collector.scheduling.enabled"], havingValue = "true")
class EventCollectionScheduler(
    private val collectionService: EventCollectionService,
    @param:Value("\${collector.scheduling.post-limit}") private val postLimit: Int,
    @param:Value("\${collector.scheduling.batch-size}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(EventCollectionScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${collector.scheduling.interval-ms}",
        initialDelayString = "\${collector.scheduling.initial-delay-ms}"
    )
    fun collectEvents() {
        try {
            collectionService.collect(postLimit, batchSize)
        } catch (exception: Exception) {
            logger.error("Не удалось завершить сбор событий", exception)
        }
    }
}
