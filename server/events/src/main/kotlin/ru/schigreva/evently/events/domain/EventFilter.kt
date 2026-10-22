package ru.schigreva.evently.events.domain

import java.math.BigDecimal
import java.time.LocalDate

data class EventFilter(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val maxPrice: BigDecimal? = null,
    val category: EventCategory? = null
) {
    init {
        require(dateFrom == null || dateTo == null || dateTo >= dateFrom) {
            "Дата окончания периода не может быть раньше даты начала"
        }
        require(maxPrice == null || maxPrice >= BigDecimal.ZERO) { "Максимальная стоимость не может быть отрицательной" }
    }
}
