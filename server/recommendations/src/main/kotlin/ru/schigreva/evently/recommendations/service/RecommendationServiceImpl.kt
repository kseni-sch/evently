package ru.schigreva.evently.recommendations.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.schigreva.evently.events.domain.EventFilter
import ru.schigreva.evently.events.service.EventService
import ru.schigreva.evently.recommendations.domain.Recommendation
import ru.schigreva.evently.recommendations.exception.RecommendationsUnavailableException
import ru.schigreva.evently.users.domain.User

@Service
class RecommendationServiceImpl(
    private val eventService: EventService,
    private val provider: RecommendationProvider
) : RecommendationService {
    private val logger = LoggerFactory.getLogger(RecommendationServiceImpl::class.java)

    override fun recommend(user: User, limit: Int): List<Recommendation> {
        require(limit in 1..20) { "Количество рекомендаций должно быть от 1 до 20" }
        require(user.interests.isNotBlank()) { "Сначала укажите свои интересы в профиле" }
        val events = eventService.findAll(EventFilter(), 0, 100).items
        if (events.isEmpty()) {
            return emptyList()
        }
        val byId = events.associateBy { it.id }
        val matches = provider.select(user.interests, events)
        if (matches.map { it.eventId }.distinct().size != matches.size || matches.any {
            it.eventId !in byId || it.score !in 1..100 || it.reason.isBlank() || it.reason.length > 1000
        }) {
            throw RecommendationsUnavailableException("Модель вернула некорректные рекомендации")
        }
        val result = matches.sortedByDescending { it.score }.take(limit).map {
            Recommendation(requireNotNull(byId[it.eventId]), it.reason)
        }
        logger.info("Для пользователя {} выбрано {} рекомендаций из {} событий", user.id, result.size, events.size)
        return result
    }
}
