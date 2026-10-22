package ru.schigreva.evently.api

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.schigreva.evently.api.models.UpdateProfileRequest
import ru.schigreva.evently.users.domain.User
import ru.schigreva.evently.users.service.UserService

@RestController
@RequestMapping("/api/users/me")
class UserController(private val userService: UserService) {
    @GetMapping
    fun getProfile(@AuthenticationPrincipal jwt: Jwt): User {
        val name = jwt.getClaimAsString("name") ?: jwt.getClaimAsString("preferred_username") ?: "Пользователь"
        return userService.getProfile(jwt.subject, name)
    }

    @PutMapping
    fun updateProfile(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: UpdateProfileRequest): User {
        return userService.updateProfile(jwt.subject, request.name, request.interests)
    }
}
