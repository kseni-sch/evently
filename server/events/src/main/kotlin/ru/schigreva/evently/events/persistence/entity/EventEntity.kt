package ru.schigreva.evently.events.persistence.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.domain.EventCategory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "events", schema = "evently")
open class EventEntity(
    @field:Id
    open var id: UUID = UUID.randomUUID(),
    @field:Column(nullable = false, columnDefinition = "text")
    open var title: String = "",
    @field:Column(nullable = false, columnDefinition = "text")
    open var description: String = "",
    @field:Column(name = "start_date")
    open var startDate: LocalDate? = null,
    @field:Column(name = "end_date")
    open var endDate: LocalDate? = null,
    @field:Column(name = "start_time")
    open var startTime: LocalTime? = null,
    @field:Column(name = "end_time")
    open var endTime: LocalTime? = null,
    @field:Column(columnDefinition = "text")
    open var location: String? = null,
    @field:Column(name = "min_price", precision = 12, scale = 2)
    open var minPrice: BigDecimal? = null,
    @field:Column(name = "price_description", columnDefinition = "text")
    open var priceDescription: String? = null,
    @field:Column(nullable = false, columnDefinition = "text")
    @field:Enumerated(EnumType.STRING)
    open var category: EventCategory = EventCategory.OTHER,
    @field:ElementCollection
    @field:CollectionTable(name = "event_publications", schema = "evently",
        joinColumns = [JoinColumn(name = "event_id")])
    @field:MapKeyColumn(name = "source_url", columnDefinition = "text")
    @field:Column(name = "title", nullable = false, columnDefinition = "text")
    open var publications: MutableMap<String, String> = mutableMapOf()
) {
    fun update(event: EventDraft) {
        title = event.title
        description = event.description
        startDate = event.startDate
        endDate = event.endDate
        startTime = event.startTime
        endTime = event.endTime
        location = event.location
        minPrice = event.minPrice
        priceDescription = event.priceDescription
        category = event.category
    }

    fun enrich(event: EventDraft) {
        if (startTime == null) startTime = event.startTime
        if (endTime == null) endTime = event.endTime
        if (endDate == null) endDate = event.endDate
        if (minPrice == null) minPrice = event.minPrice
        if (priceDescription == null) priceDescription = event.priceDescription
        if (category == EventCategory.OTHER) category = event.category
    }

    fun toDomain(): Event {
        val urls = publications.keys.sorted()
        return Event(id, title, description, startDate, endDate, startTime,
            endTime, location, minPrice, priceDescription, category, urls.first(), urls)
    }
}
