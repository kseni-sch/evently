package ru.schigreva.evently.events.persistence

import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import ru.schigreva.evently.events.persistence.entity.EventEntity
import java.util.UUID
import java.time.LocalDate

interface EventJpaRepository : JpaRepository<EventEntity, UUID>, JpaSpecificationExecutor<EventEntity> {
    @Query("select distinct e from EventEntity e join e.publications p where key(p) = :sourceUrl")
    fun findAllBySourceUrl(sourceUrl: String): List<EventEntity>

    fun findAllByStartDate(startDate: LocalDate): List<EventEntity>
}
