package ru.schigreva.evently.users.repository

import ru.schigreva.evently.users.domain.User

interface UserRepository {
    fun getOrCreate(authSubject: String, name: String): User
    fun update(authSubject: String, name: String, interests: String): User
}
