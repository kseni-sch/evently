package ru.schigreva.evently.events.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

data class EventDraft(
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
    val sourceUrl: String
) {
    init {
        require(title.isNotBlank()) { "Название события не может быть пустым" }
        require(description.isNotBlank()) { "Описание события не может быть пустым" }
        require(sourceUrl.isNotBlank()) { "Ссылка на источник не может быть пустой" }
        require(minPrice == null || minPrice >= BigDecimal.ZERO) { "Стоимость не может быть отрицательной" }
        require(startDate == null || endDate == null || endDate >= startDate) {
            "Дата окончания не может быть раньше даты начала"
        }
    }
}
