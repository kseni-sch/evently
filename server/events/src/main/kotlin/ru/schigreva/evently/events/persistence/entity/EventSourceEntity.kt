package ru.schigreva.evently.events.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "event_sources", schema = "evently")
open class EventSourceEntity(
    @field:Id
    @field:Column(name = "source_url", nullable = false, columnDefinition = "text")
    open var sourceUrl: String = ""
)
