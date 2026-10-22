package ru.schigreva.evently.recommendations.service

import ru.schigreva.evently.recommendations.domain.Recommendation
import ru.schigreva.evently.users.domain.User

interface RecommendationService {
    fun recommend(user: User, limit: Int): List<Recommendation>
}
