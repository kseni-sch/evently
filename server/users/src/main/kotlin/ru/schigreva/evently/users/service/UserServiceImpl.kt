package ru.schigreva.evently.users.service

import org.springframework.stereotype.Service
import ru.schigreva.evently.users.domain.User
import ru.schigreva.evently.users.repository.UserRepository

@Service
class UserServiceImpl(private val userRepository: UserRepository) : UserService {
    override fun getProfile(authSubject: String, name: String): User {
        validateSubject(authSubject)
        val initialName = name.trim().ifBlank { "Пользователь" }.take(100)
        return userRepository.getOrCreate(authSubject, initialName)
    }

    override fun updateProfile(authSubject: String, name: String, interests: String): User {
        validateSubject(authSubject)
        val trimmedName = name.trim()
        val trimmedInterests = interests.trim()
        require(trimmedName.length in 1..100) { "Имя должно содержать от 1 до 100 символов" }
        require(trimmedInterests.length <= 2000) { "Интересы должны содержать не больше 2000 символов" }
        return userRepository.update(authSubject, trimmedName, trimmedInterests)
    }

    private fun validateSubject(authSubject: String) {
        require(authSubject.isNotBlank() && authSubject.length <= 255) { "Некорректный идентификатор пользователя" }
    }
}
