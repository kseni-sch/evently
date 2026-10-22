package ru.schigreva.evently.api.models

import ru.schigreva.evently.events.domain.EventCategory

data class CategoryResponse(val code: EventCategory, val title: String)
