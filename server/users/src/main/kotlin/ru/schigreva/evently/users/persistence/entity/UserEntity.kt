package ru.schigreva.evently.users.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.schigreva.evently.users.domain.User
import java.util.UUID

@Entity
@Table(name = "users", schema = "evently")
open class UserEntity(
    @field:Id
    open var id: UUID = UUID.randomUUID(),
    @field:Column(name = "auth_subject", nullable = false, unique = true)
    open var authSubject: String = "",
    @field:Column(nullable = false, length = 100)
    open var name: String = "",
    @field:Column(nullable = false, length = 2000)
    open var interests: String = ""
) {
    fun toDomain(): User = User(id, name, interests)
}
