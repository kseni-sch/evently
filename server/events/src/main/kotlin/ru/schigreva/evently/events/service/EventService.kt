package ru.schigreva.evently.events.service

import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.domain.EventFilter
import ru.schigreva.evently.events.domain.EventPage
import java.util.UUID

interface EventService {
    fun getById(id: UUID): Event
    fun findByIds(ids: List<UUID>): List<Event>
    fun findAll(filter: EventFilter, page: Int, size: Int): EventPage
    fun saveFromSource(sourceUrl: String, events: List<EventDraft>): List<Event>
}
