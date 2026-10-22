package ru.schigreva.evently.events.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import ru.schigreva.evently.events.domain.Event
import ru.schigreva.evently.events.domain.EventMatcher
import ru.schigreva.evently.events.domain.EventDraft
import ru.schigreva.evently.events.domain.EventCategory
import ru.schigreva.evently.events.domain.EventFilter
import ru.schigreva.evently.events.domain.EventPage
import ru.schigreva.evently.events.persistence.entity.EventEntity
import ru.schigreva.evently.events.repository.EventRepository

@Repository
class EventRepositoryImpl(
    private val jpaRepository: EventJpaRepository,
    private val entityManager: EntityManager
) : EventRepository {
    @Transactional(readOnly = true)
    override fun findById(id: UUID): Event? {
        return jpaRepository.findById(id).orElse(null)?.toDomain()
    }

    @Transactional(readOnly = true)
    override fun findByIds(ids: List<UUID>): List<Event> {
        return jpaRepository.findAllById(ids).map { it.toDomain() }
    }

    @Transactional(readOnly = true)
    override fun findAll(filter: EventFilter, page: Int, size: Int): EventPage {
        val specification = Specification<EventEntity> { root, _, builder ->
            val conditions = mutableListOf<Predicate>()
            if (filter.dateFrom != null) {
                val endDate = builder.coalesce(root.get<LocalDate>("endDate"), root.get<LocalDate>("startDate"))
                conditions.add(builder.greaterThanOrEqualTo(endDate, filter.dateFrom))
            }
            if (filter.dateTo != null) {
                conditions.add(builder.lessThanOrEqualTo(root.get("startDate"), filter.dateTo))
            }
            if (filter.maxPrice != null) {
                conditions.add(builder.lessThanOrEqualTo(root.get("minPrice"), filter.maxPrice))
            }
            if (filter.category != null) {
                conditions.add(builder.equal(root.get<EventCategory>("category"), filter.category))
            }
            builder.and(*conditions.toTypedArray())
        }
        val pageable = PageRequest.of(page, size, Sort.by("startDate", "id"))
        val result = jpaRepository.findAll(specification, pageable)
        return EventPage(result.content.map { it.toDomain() }, result.totalElements, page, size)
    }

    @Transactional
    override fun saveFromSource(sourceUrl: String, events: List<EventDraft>): List<Event> {
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtext('evently.events.import'))").singleResult
        entityManager.createNativeQuery(
            "INSERT INTO evently.event_sources (source_url) VALUES (:url) ON CONFLICT DO NOTHING"
        ).setParameter("url", sourceUrl).executeUpdate()

        val existing = jpaRepository.findAllBySourceUrl(sourceUrl)
        val saved = mutableListOf<EventEntity>()
        val titles = events.map { EventMatcher.normalize(it.title) }.toSet()
        val previousTitles = existing.map { EventMatcher.normalize(it.publications.getValue(sourceUrl)) }.toSet()
        for (event in events) {
            val previous = existing.filter {
                EventMatcher.normalize(it.publications.getValue(sourceUrl)) == EventMatcher.normalize(event.title)
            }.singleOrNull()
            val renamed = existing.filter {
                it.id !in saved.map { savedEvent -> savedEvent.id } &&
                    EventMatcher.normalize(it.publications.getValue(sourceUrl)) !in titles &&
                    EventMatcher.matchesDetails(it.toDomain(), event)
            }.singleOrNull()?.takeIf { candidate ->
                events.count {
                    EventMatcher.normalize(it.title) !in previousTitles &&
                        EventMatcher.matchesDetails(candidate.toDomain(), it)
                } == 1
            }
            val candidates = event.startDate?.let { jpaRepository.findAllByStartDate(it) } ?: emptyList()
            val matches = candidates.filter { EventMatcher.matches(it.toDomain(), event) }
            val ownEvent = (previous ?: renamed)?.takeIf {
                it.publications.size == 1 || EventMatcher.matchesDetails(it.toDomain(), event)
            }
            val entity = ownEvent ?: matches.singleOrNull() ?: EventEntity()
            if (entity.publications.isEmpty() || (entity.publications.size == 1 && entity.publications.containsKey(sourceUrl))) {
                entity.update(event)
            } else {
                entity.enrich(event)
            }
            entity.publications[sourceUrl] = event.title
            saved.add(jpaRepository.saveAndFlush(entity))
        }

        val savedIds = saved.map { it.id }.toSet()
        for (entity in existing.filter { it.id !in savedIds }) {
            entity.publications.remove(sourceUrl)
            if (entity.publications.isEmpty()) {
                jpaRepository.delete(entity)
            }
        }
        jpaRepository.flush()
        return saved.distinctBy { it.id }.map { it.toDomain() }
    }
}
