package ru.schigreva.evently.favorites.service

import org.springframework.stereotype.Service
import ru.schigreva.evently.events.domain.EventPage
import ru.schigreva.evently.events.service.EventService
import ru.schigreva.evently.favorites.repository.FavoriteRepository
import java.util.UUID

@Service
class FavoriteServiceImpl(
    private val favoriteRepository: FavoriteRepository,
    private val eventService: EventService
) : FavoriteService {
    override fun add(userId: UUID, eventId: UUID) {
        eventService.getById(eventId)
        favoriteRepository.add(userId, eventId)
    }

    override fun remove(userId: UUID, eventId: UUID) {
        favoriteRepository.remove(userId, eventId)
    }

    override fun findAll(userId: UUID, page: Int, size: Int): EventPage {
        require(page >= 0) { "Номер страницы не может быть отрицательным" }
        require(size in 1..100) { "Размер страницы должен быть от 1 до 100" }
        require(page.toLong() * size <= Int.MAX_VALUE) { "Номер страницы слишком большой" }
        val ids = favoriteRepository.findEventIds(userId, page, size)
        val events = eventService.findByIds(ids).associateBy { it.id }
        val items = ids.mapNotNull { events[it] }
        return EventPage(items, favoriteRepository.countByUserId(userId), page, size)
    }
}
