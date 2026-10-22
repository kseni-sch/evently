package ru.schigreva.evently.recommendations.domain

import ru.schigreva.evently.events.domain.Event

data class Recommendation(val event: Event, val reason: String)
