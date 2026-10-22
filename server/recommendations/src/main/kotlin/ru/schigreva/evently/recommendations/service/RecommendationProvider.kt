package ru.schigreva.evently.recommendations.service

import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.recommendations.domain.EventMatch

interface RecommendationProvider {
    fun select(interests: String, events: List<Event>): List<EventMatch>
}
