package ru.schigreva.evently.users.persistence

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import ru.schigreva.evently.users.domain.User
import ru.schigreva.evently.users.repository.UserRepository
import java.util.UUID

@Repository
class UserRepositoryImpl(
    private val jpaRepository: UserJpaRepository,
    private val entityManager: EntityManager
) : UserRepository {
    @Transactional
    override fun getOrCreate(authSubject: String, name: String): User {
        createIfAbsent(authSubject, name)
        return requireNotNull(jpaRepository.findByAuthSubject(authSubject)).toDomain()
    }

    @Transactional
    override fun update(authSubject: String, name: String, interests: String): User {
        createIfAbsent(authSubject, name)
        val user = requireNotNull(jpaRepository.findByAuthSubject(authSubject))
        user.name = name
        user.interests = interests
        return jpaRepository.save(user).toDomain()
    }

    private fun createIfAbsent(authSubject: String, name: String) {
        entityManager.createNativeQuery(
            """
            INSERT INTO evently.users (id, auth_subject, name, interests)
            VALUES (:id, :subject, :name, '') ON CONFLICT (auth_subject) DO NOTHING
            """.trimIndent()
        ).setParameter("id", UUID.randomUUID())
            .setParameter("subject", authSubject)
            .setParameter("name", name)
            .executeUpdate()
    }
}
