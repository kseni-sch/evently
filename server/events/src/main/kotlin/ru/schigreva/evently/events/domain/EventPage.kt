package ru.schigreva.evently.events.domain

data class EventPage(
    val items: List<Event>,
    val total: Long,
    val page: Int,
    val size: Int
)
