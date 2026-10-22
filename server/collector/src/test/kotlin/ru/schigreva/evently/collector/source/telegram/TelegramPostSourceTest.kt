package ru.schigreva.evently.collector.source.telegram

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.schigreva.evently.collector.exception.SourceUnavailableException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class TelegramPostSourceTest {
    private lateinit var server: HttpServer
    private lateinit var source: TelegramPostSource
    private val pages = mutableMapOf<String, String>()
    private val requests = AtomicInteger()

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/channel") { exchange ->
            requests.incrementAndGet()
            val query = exchange.requestURI.rawQuery ?: ""
            val page = pages[query]
            val status = if (page == null) 503 else 200
            val body = (page ?: "Источник недоступен").toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        source = TelegramPostSource("http://127.0.0.1:${server.address.port}/channel", 3000)
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun loadsHistoryAndRemovesDuplicatePosts() {
        pages[""] = post(30) + historyLink("?before=30", "30")
        pages["before=30"] = post(30) + post(29)

        val posts = source.getPosts(2)

        assertEquals(listOf("channel/30", "channel/29"), posts.map { it.externalId })
        assertEquals(2, requests.get())
    }

    @Test
    fun stopsAfterRequestedNumberOfPostsAndReturnsNewestFirst() {
        pages[""] = post(29) + post(30) + historyLink("?before=29", "29")

        val posts = source.getPosts(1)

        assertEquals(listOf("channel/30"), posts.map { it.externalId })
        assertEquals(1, requests.get())
    }

    @Test
    fun returnsAvailablePostsWhenHistoryEnds() {
        pages[""] = post(30)

        assertEquals(1, source.getPosts(20).size)
    }

    @Test
    fun reportsHttpFailure() {
        assertThrows(SourceUnavailableException::class.java) { source.getPosts(1) }
    }

    @Test
    fun reportsUnexpectedPageInsteadOfReturningEmptyList() {
        pages[""] = "<html><body>Страница входа</body></html>"

        assertThrows(SourceUnavailableException::class.java) { source.getPosts(1) }
    }

    @Test
    fun stopsIfHistoryLinkRepeats() {
        pages[""] = post(30) + historyLink("?before=30", "30")
        pages["before=30"] = post(30) + historyLink("?before=30", "30")

        assertThrows(SourceUnavailableException::class.java) { source.getPosts(2) }
        assertEquals(2, requests.get())
    }

    private fun post(id: Int): String {
        return """
            <div class="tgme_widget_message" data-post="channel/$id">
                <div class="tgme_widget_message_text">Событие $id</div>
                <a class="tgme_widget_message_date" href="https://t.me/channel/$id">
                    <time datetime="2026-10-22T12:00:${id}+00:00"></time>
                </a>
            </div>
        """.trimIndent()
    }

    private fun historyLink(href: String, before: String): String {
        return """<a class="tme_messages_more" data-before="$before" href="$href">История</a>"""
    }
}
