package ru.schigreva.evently.recommendations.domain

import java.util.UUID

data class EventMatch(val eventId: UUID, val score: Int, val reason: String)
