package ru.schigreva.evently.events.service

import org.springframework.stereotype.Service
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.domain.EventFilter
import ru.schigreva.evently.events.domain.EventPage
import ru.schigreva.evently.events.exception.EventNotFoundException
import java.util.UUID
import java.time.Clock
import java.time.LocalDate
import ru.schigreva.evently.events.repository.EventRepository

@Service
class EventServiceImpl(
    private val eventRepository: EventRepository,
    private val clock: Clock
) : EventService {
    override fun getById(id: UUID): Event {
        return eventRepository.findById(id) ?: throw EventNotFoundException(id)
    }

    override fun findByIds(ids: List<UUID>): List<Event> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return eventRepository.findByIds(ids)
    }

    override fun findAll(filter: EventFilter, page: Int, size: Int): EventPage {
        require(page >= 0) { "Номер страницы не может быть отрицательным" }
        require(size in 1..100) { "Размер страницы должен быть от 1 до 100" }
        require(page.toLong() * size <= Int.MAX_VALUE) { "Номер страницы слишком большой" }
        val actualFilter = if (filter.dateFrom == null && filter.dateTo == null) {
            filter.copy(dateFrom = LocalDate.now(clock))
        } else {
            filter
        }
        return eventRepository.findAll(actualFilter, page, size)
    }

    override fun saveFromSource(sourceUrl: String, events: List<EventDraft>): List<Event> {
        require(sourceUrl.isNotBlank()) { "Ссылка на источник не может быть пустой" }
        require(events.all { it.sourceUrl == sourceUrl }) { "События должны относиться к одному источнику" }
        require(events.map { it.title }.distinct().size == events.size) {
            "В одной публикации не должно быть событий с одинаковым названием"
        }
        return eventRepository.saveFromSource(sourceUrl, events)
    }
}
