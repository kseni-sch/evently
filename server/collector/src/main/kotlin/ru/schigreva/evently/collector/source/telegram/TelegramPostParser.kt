package ru.schigreva.evently.collector.source.telegram

import org.jsoup.nodes.Document
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.exception.SourceUnavailableException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class TelegramPostParser {
    fun parse(document: Document): List<SourcePost> {
        val posts = mutableListOf<SourcePost>()
        val messages = document.select(".tgme_widget_message[data-post]")

        for (message in messages) {
            val textElement = message.selectFirst(".tgme_widget_message_text")
            if (textElement == null) {
                continue
            }

            val text = textElement.wholeText().trim()
            if (text.isBlank()) {
                continue
            }

            val externalId = message.attr("data-post")
            val dateLink = message.selectFirst("a.tgme_widget_message_date[href]")
            val timeElement = dateLink?.selectFirst("time[datetime]")
            if (externalId.isBlank() || dateLink == null || timeElement == null) {
                throw SourceUnavailableException("Не удалось прочитать данные публикации $externalId")
            }

            val sourceUrl = dateLink.absUrl("href")
            if (sourceUrl.isBlank()) {
                throw SourceUnavailableException("У публикации $externalId отсутствует ссылка")
            }

            val publishedAt: OffsetDateTime
            try {
                publishedAt = OffsetDateTime.parse(timeElement.attr("datetime"))
            } catch (exception: DateTimeParseException) {
                throw SourceUnavailableException("Не удалось прочитать дату публикации $externalId", exception)
            }

            val post = SourcePost(externalId, text, sourceUrl, publishedAt)
            posts.add(post)
        }

        return posts
    }
}
