package ru.schigreva.evently.collector.source.groq.models

import java.math.BigDecimal
import ru.schigreva.evently.events.domain.EventCategory
import java.time.LocalDate
import java.time.LocalTime

data class ParsedEvent(
    val title: String,
    val description: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val location: String?,
    val minPrice: BigDecimal?,
    val priceDescription: String?,
    val category: EventCategory
)
