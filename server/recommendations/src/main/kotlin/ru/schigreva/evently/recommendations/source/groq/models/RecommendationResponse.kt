package ru.schigreva.evently.recommendations.source.groq.models

import ru.schigreva.evently.recommendations.domain.EventMatch

data class RecommendationResponse(val recommendations: List<EventMatch>)
