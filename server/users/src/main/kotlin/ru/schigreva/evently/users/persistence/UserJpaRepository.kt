package ru.schigreva.evently.users.persistence

import org.springframework.data.jpa.repository.JpaRepository
import ru.schigreva.evently.users.persistence.entity.UserEntity
import java.util.UUID

interface UserJpaRepository : JpaRepository<UserEntity, UUID> {
    fun findByAuthSubject(authSubject: String): UserEntity?
}
