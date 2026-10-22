package ru.schigreva.evently.api

import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventFilter
import ru.schigreva.evently.events.domain.EventCategory
import ru.schigreva.evently.events.domain.EventPage
import ru.schigreva.evently.events.service.EventService
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RestController
@SecurityRequirements
@RequestMapping("/api/events")
class EventController(private val eventService: EventService) {
    @GetMapping
    fun getEvents(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): EventPage {
        return eventService.findAll(EventFilter(dateFrom, dateTo, maxPrice, parseCategory(category)), page, size)
    }

    @GetMapping("/{id}")
    fun getEvent(@PathVariable id: UUID): Event {
        return eventService.getById(id)
    }
    private fun parseCategory(value: String?): EventCategory? {
        if (value == null) {
            return null
        }
        return EventCategory.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Неизвестная категория. Список категорий доступен в /api/categories")
    }

}
