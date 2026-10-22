package ru.schigreva.evently.collector.source.telegram

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.schigreva.evently.collector.exception.SourceUnavailableException
import java.time.OffsetDateTime

class TelegramPostParserTest {
    private val parser = TelegramPostParser()

    @Test
    fun parsesPostAndPreservesLineBreaks() {
        val document = Jsoup.parse("""
            <div class="tgme_widget_message" data-post="channel/10">
                <div class="tgme_widget_message_text">Концерт<br>Вход <b>свободный</b></div>
                <a class="tgme_widget_message_date" href="https://t.me/channel/10">
                    <time datetime="2026-10-22T12:30:00+00:00"></time>
                </a>
            </div>
        """.trimIndent())

        val posts = parser.parse(document)

        assertEquals(1, posts.size)
        assertEquals("channel/10", posts[0].externalId)
        assertEquals("Концерт\nВход свободный", posts[0].text)
        assertEquals("https://t.me/channel/10", posts[0].sourceUrl)
        assertEquals(OffsetDateTime.parse("2026-10-22T12:30:00Z"), posts[0].publishedAt)
    }

    @Test
    fun skipsPostsWithoutText() {
        val document = Jsoup.parse("""
            <div class="tgme_widget_message" data-post="channel/10"><img src="photo.jpg"></div>
            <div class="tgme_widget_message" data-post="channel/11">
                <div class="tgme_widget_message_text">   </div>
            </div>
        """.trimIndent())

        assertTrue(parser.parse(document).isEmpty())
    }

    @Test
    fun doesNotRepeatAlbumCaption() {
        val document = Jsoup.parse("""
            <div class="tgme_widget_message" data-post="channel/10">
                <div class="tgme_widget_message_text">Анонс события</div>
                <div class="tgme_widget_message_text">Анонс события</div>
                <a class="tgme_widget_message_date" href="https://t.me/channel/10">
                    <time datetime="2026-10-22T12:30:00Z"></time>
                </a>
            </div>
        """.trimIndent())

        assertEquals("Анонс события", parser.parse(document)[0].text)
    }

    @Test
    fun rejectsInvalidPublicationDate() {
        val document = Jsoup.parse("""
            <div class="tgme_widget_message" data-post="channel/10">
                <div class="tgme_widget_message_text">Анонс события</div>
                <a class="tgme_widget_message_date" href="https://t.me/channel/10">
                    <time datetime="неизвестно"></time>
                </a>
            </div>
        """.trimIndent())

        assertThrows(SourceUnavailableException::class.java) { parser.parse(document) }
    }
}
