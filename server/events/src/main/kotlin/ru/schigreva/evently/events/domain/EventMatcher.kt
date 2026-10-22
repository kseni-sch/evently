package ru.schigreva.evently.events.domain

import java.util.Locale

object EventMatcher {
    fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
    }

    fun matches(event: Event, draft: EventDraft): Boolean {
        return normalize(event.title) == normalize(draft.title) && matchesDetails(event, draft)
    }

    fun matchesDetails(event: Event, draft: EventDraft): Boolean {
        if (event.startDate == null || draft.startDate == null) {
            return false
        }
        val location = normalize(event.location ?: "")
        if (location.isEmpty() || location != normalize(draft.location ?: "")) {
            return false
        }
        return event.startDate == draft.startDate &&
            (event.endDate ?: event.startDate) == (draft.endDate ?: draft.startDate) &&
            (event.startTime == null || draft.startTime == null || event.startTime == draft.startTime) &&
            (event.endTime == null || draft.endTime == null || event.endTime == draft.endTime)
    }
}
