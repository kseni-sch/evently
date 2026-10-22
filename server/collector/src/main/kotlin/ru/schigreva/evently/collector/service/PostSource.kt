package ru.schigreva.evently.collector.service

import ru.schigreva.evently.collector.domain.SourcePost

interface PostSource {
    fun getPosts(limit: Int = 20): List<SourcePost>
}
