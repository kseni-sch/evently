package ru.schigreva.evently.favorites.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import ru.schigreva.evently.events.exception.EventNotFoundException
import ru.schigreva.evently.favorites.repository.FavoriteRepository
import java.util.UUID

@Repository
class FavoriteRepositoryImpl(private val jdbc: JdbcTemplate) : FavoriteRepository {
    @Transactional
    override fun add(userId: UUID, eventId: UUID) {
        val eventIds = jdbc.query(
            "SELECT id FROM evently.events WHERE id = ? FOR KEY SHARE",
            { row, _ -> row.getObject("id", UUID::class.java) }, eventId
        )
        if (eventIds.isEmpty()) {
            throw EventNotFoundException(eventId)
        }
        jdbc.update(
            "INSERT INTO evently.favorites (user_id, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            userId, eventId
        )
    }

    override fun remove(userId: UUID, eventId: UUID) {
        jdbc.update("DELETE FROM evently.favorites WHERE user_id = ? AND event_id = ?", userId, eventId)
    }

    override fun findEventIds(userId: UUID, page: Int, size: Int): List<UUID> {
        return jdbc.query(
            """
            SELECT event_id FROM evently.favorites WHERE user_id = ?
            ORDER BY saved_at DESC, event_id LIMIT ? OFFSET ?
            """.trimIndent(),
            { row, _ -> row.getObject("event_id", UUID::class.java) }, userId, size, page.toLong() * size
        )
    }

    override fun countByUserId(userId: UUID): Long {
        return requireNotNull(jdbc.queryForObject(
            "SELECT count(*) FROM evently.favorites WHERE user_id = ?", Long::class.java, userId
        ))
    }
}
