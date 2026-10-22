package ru.schigreva.evently.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.schigreva.evently.recommendations.domain.Recommendation
import ru.schigreva.evently.recommendations.service.RecommendationService
import ru.schigreva.evently.users.service.UserService

@RestController
@RequestMapping("/api/recommendations")
class RecommendationController(
    private val recommendationService: RecommendationService,
    private val userService: UserService
) {
    @GetMapping
    fun getRecommendations(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<Recommendation> {
        val name = jwt.getClaimAsString("name") ?: jwt.getClaimAsString("preferred_username") ?: "Пользователь"
        val user = userService.getProfile(jwt.subject, name)
        return recommendationService.recommend(user, limit)
    }
}
