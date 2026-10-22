package ru.schigreva.evently.collector.repository

import ru.schigreva.evently.collector.domain.SourcePost

interface ProcessedPostRepository {
    fun findHash(sourceUrl: String): String?
    fun save(post: SourcePost, contentHash: String)
}
