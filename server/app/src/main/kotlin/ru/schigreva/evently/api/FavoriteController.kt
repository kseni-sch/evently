package ru.schigreva.evently.api

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.schigreva.evently.events.domain.EventPage
import ru.schigreva.evently.favorites.service.FavoriteService
import ru.schigreva.evently.users.service.UserService
import java.util.UUID

@RestController
@RequestMapping("/api/favorites")
class FavoriteController(
    private val favoriteService: FavoriteService,
    private val userService: UserService
) {
    @GetMapping
    fun getFavorites(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): EventPage {
        return favoriteService.findAll(userId(jwt), page, size)
    }

    @PutMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addFavorite(@AuthenticationPrincipal jwt: Jwt, @PathVariable eventId: UUID) {
        favoriteService.add(userId(jwt), eventId)
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFavorite(@AuthenticationPrincipal jwt: Jwt, @PathVariable eventId: UUID) {
        favoriteService.remove(userId(jwt), eventId)
    }

    private fun userId(jwt: Jwt): UUID {
        val name = jwt.getClaimAsString("name") ?: jwt.getClaimAsString("preferred_username") ?: "Пользователь"
        return userService.getProfile(jwt.subject, name).id
    }
}
