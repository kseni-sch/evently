package ru.schigreva.evently.users.service

import ru.schigreva.evently.users.domain.User

interface UserService {
    fun getProfile(authSubject: String, name: String): User
    fun updateProfile(authSubject: String, name: String, interests: String): User
}
