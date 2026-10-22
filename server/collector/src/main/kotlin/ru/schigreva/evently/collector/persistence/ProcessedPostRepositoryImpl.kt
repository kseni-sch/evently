package ru.schigreva.evently.collector.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import ru.schigreva.evently.collector.domain.SourcePost
import ru.schigreva.evently.collector.repository.ProcessedPostRepository

@Repository
class ProcessedPostRepositoryImpl(private val jdbc: JdbcTemplate) : ProcessedPostRepository {
    override fun findHash(sourceUrl: String): String? {
        return jdbc.query(
            "SELECT content_hash FROM evently.processed_posts WHERE source_url = ?",
            { row, _ -> row.getString("content_hash") }, sourceUrl
        ).firstOrNull()
    }

    override fun save(post: SourcePost, contentHash: String) {
        jdbc.update(
            """
            INSERT INTO evently.processed_posts (source_url, external_id, published_at, content_hash)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (source_url) DO UPDATE
            SET external_id = EXCLUDED.external_id, published_at = EXCLUDED.published_at,
                content_hash = EXCLUDED.content_hash, processed_at = CURRENT_TIMESTAMP
            """.trimIndent(),
            post.sourceUrl, post.externalId, post.publishedAt, contentHash
        )
    }
}
