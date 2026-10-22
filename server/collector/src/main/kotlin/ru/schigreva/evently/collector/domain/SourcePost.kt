package ru.schigreva.evently.collector.domain

import java.time.OffsetDateTime

data class SourcePost(
    val externalId: String,
    val text: String,
    val sourceUrl: String,
    val publishedAt: OffsetDateTime
)
