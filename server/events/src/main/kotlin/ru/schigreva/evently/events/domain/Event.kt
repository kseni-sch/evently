package ru.schigreva.evently.events.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class Event(
    val id: UUID,
    val title: String,
    val description: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val location: String?,
    val minPrice: BigDecimal?,
    val priceDescription: String?,
    val category: EventCategory,
    val sourceUrl: String,
    val sourceUrls: List<String> = listOf(sourceUrl)
)
