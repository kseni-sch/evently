package ru.schigreva.evently.collector.service

import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.events.domain.EventDraft

interface EventParser {
    fun parse(post: SourcePost): List<EventDraft>
}
