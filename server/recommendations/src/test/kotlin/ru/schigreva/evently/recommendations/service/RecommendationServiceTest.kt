package ru.schigreva.evently.recommendations.service

import ru.schigreva.evently.events.domain.EventCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventPage
import ru.schigreva.evently.events.domain.EventFilter
import ru.schigreva.evently.events.service.EventService
import ru.schigreva.evently.recommendations.domain.EventMatch
import ru.schigreva.evently.recommendations.exception.RecommendationsUnavailableException
import ru.schigreva.evently.users.domain.User
import java.time.LocalDate
import java.util.UUID

class RecommendationServiceTest {
    private val eventService = mock(EventService::class.java)
    private val provider = mock(RecommendationProvider::class.java)
    private val service = RecommendationServiceImpl(eventService, provider)
    private val user = User(UUID.randomUUID(), "Ксения", "Хочу тихие творческие занятия, без шумных концертов")
    private val first = event("Керамика")
    private val second = event("Рисование")

    @BeforeEach
    fun setUp() {
        `when`(eventService.findAll(EventFilter(), 0, 100)).thenReturn(EventPage(listOf(first, second), 2, 0, 100))
    }

    @Test
    fun preservesFullInterestsAndSortsRecommendationsByScore() {
        `when`(provider.select(user.interests, listOf(first, second))).thenReturn(listOf(
            EventMatch(first.id, 75, "Работа с глиной"), EventMatch(second.id, 95, "Спокойное занятие живописью")))

        val result = service.recommend(user, 1)

        assertEquals(1, result.size)
        assertEquals(second.id, result[0].event.id)
        assertEquals("Спокойное занятие живописью", result[0].reason)
        verify(provider).select(user.interests, listOf(first, second))
    }

    @Test
    fun requiresInterestsBeforeCallingProvider() {
        assertThrows(IllegalArgumentException::class.java) { service.recommend(user.copy(interests = " "), 10) }
        verifyNoInteractions(provider)
    }

    @Test
    fun emptyCatalogDoesNotCallProvider() {
        `when`(eventService.findAll(EventFilter(), 0, 100)).thenReturn(EventPage(emptyList(), 0, 0, 100))
        assertTrue(service.recommend(user, 10).isEmpty())
        verifyNoInteractions(provider)
    }

    @Test
    fun noMatchesReturnsEmptyList() {
        `when`(provider.select(anyString(), anyList())).thenReturn(emptyList())
        assertTrue(service.recommend(user, 10).isEmpty())
    }

    @Test
    fun rejectsInventedDuplicateAndInvalidMatches() {
        val invalid = listOf(
            listOf(EventMatch(UUID.randomUUID(), 90, "Причина")),
            listOf(EventMatch(first.id, 90, "Причина"), EventMatch(first.id, 80, "Ещё причина")),
            listOf(EventMatch(first.id, 101, "Причина")),
            listOf(EventMatch(first.id, 0, "Причина")),
            listOf(EventMatch(first.id, 90, " "))
        )
        for (matches in invalid) {
            `when`(provider.select(anyString(), anyList())).thenReturn(matches)
            assertThrows(RecommendationsUnavailableException::class.java) { service.recommend(user, 10) }
        }
    }

    @Test
    fun invalidLimitDoesNotCallProvider() {
        for (limit in listOf(0, 21)) {
            assertThrows(IllegalArgumentException::class.java) { service.recommend(user, limit) }
        }
        verifyNoInteractions(provider)
    }

    private fun event(title: String): Event {
        return Event(UUID.randomUUID(), title, "Описание", LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30),
            null, null, "Санкт-Петербург", null, null, EventCategory.WORKSHOP, "https://t.me/channel/1")
    }
}
