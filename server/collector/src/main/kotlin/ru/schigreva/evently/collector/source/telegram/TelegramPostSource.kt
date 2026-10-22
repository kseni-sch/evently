package ru.schigreva.evently.collector.source.telegram

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.exception.SourceUnavailableException
import ru.schigreva.evently.collector.service.PostSource
import java.io.IOException

@Component
class TelegramPostSource(
    @param:Value("\${collector.telegram.url}") private val channelUrl: String,
    @param:Value("\${collector.telegram.timeout-ms}") private val timeoutMs: Int
) : PostSource {
    private val parser = TelegramPostParser()
    private val logger = LoggerFactory.getLogger(TelegramPostSource::class.java)

    override fun getPosts(limit: Int): List<SourcePost> {
        require(limit > 0) { "Количество публикаций должно быть положительным" }

        val posts = linkedMapOf<String, SourcePost>()
        val visitedPages = mutableSetOf<String>()
        var pageUrl: String? = channelUrl

        while (pageUrl != null && posts.size < limit) {
            if (!visitedPages.add(pageUrl)) {
                throw SourceUnavailableException("Источник повторно вернул ту же страницу истории")
            }

            val document = loadPage(pageUrl)
            if (document.select(".tgme_widget_message[data-post]").isEmpty()) {
                throw SourceUnavailableException("На странице $pageUrl не найдены публикации Telegram")
            }

            val pagePosts = parser.parse(document)
            for (post in pagePosts) {
                posts.putIfAbsent(post.externalId, post)
            }
            logger.info("Получено {} текстовых публикаций со страницы {}", pagePosts.size, pageUrl)

            val historyLink = document.selectFirst("a.tme_messages_more[data-before]")
            pageUrl = historyLink?.absUrl("href")
            if (pageUrl != null && pageUrl.isBlank()) {
                pageUrl = null
            }
        }

        val result = posts.values.sortedByDescending { it.publishedAt.toInstant() }.take(limit)
        logger.info("Сбор из {} завершён: {} публикаций", channelUrl, result.size)
        return result
    }

    private fun loadPage(url: String): Document {
        try {
            return Jsoup.connect(url).timeout(timeoutMs).get()
        } catch (exception: IOException) {
            throw SourceUnavailableException("Не удалось загрузить страницу $url", exception)
        }
    }
}
