package ru.schigreva.evently.favorites.service

import ru.schigreva.evently.events.domain.EventPage
import java.util.UUID

interface FavoriteService {
    fun add(userId: UUID, eventId: UUID)
    fun remove(userId: UUID, eventId: UUID)
    fun findAll(userId: UUID, page: Int, size: Int): EventPage
}
