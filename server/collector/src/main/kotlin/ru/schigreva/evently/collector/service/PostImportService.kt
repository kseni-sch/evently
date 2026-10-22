package ru.schigreva.evently.collector.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.repository.ProcessedPostRepository
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.service.EventService

@Service
class PostImportService(
    private val eventService: EventService,
    private val processedPostRepository: ProcessedPostRepository
) {
    @Transactional
    fun save(post: SourcePost, events: List<EventDraft>, contentHash: String): List<Event> {
        val saved = eventService.saveFromSource(post.sourceUrl, events)
        processedPostRepository.save(post, contentHash)
        return saved
    }
}
