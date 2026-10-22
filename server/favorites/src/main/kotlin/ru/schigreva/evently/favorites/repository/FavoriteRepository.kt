package ru.schigreva.evently.favorites.repository

import java.util.UUID

interface FavoriteRepository {
    fun add(userId: UUID, eventId: UUID)
    fun remove(userId: UUID, eventId: UUID)
    fun findEventIds(userId: UUID, page: Int, size: Int): List<UUID>
    fun countByUserId(userId: UUID): Long
}
