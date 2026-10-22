package ru.schigreva.evently.users.domain

import java.util.UUID

data class User(
    val id: UUID,
    val name: String,
    val interests: String
)
