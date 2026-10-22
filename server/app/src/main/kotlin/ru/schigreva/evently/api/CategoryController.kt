package ru.schigreva.evently.api

import io.swagger.v3.oas.annotations.security.SecurityRequirements
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.schigreva.evently.api.models.CategoryResponse
import ru.schigreva.evently.events.domain.EventCategory

@RestController
@SecurityRequirements
@RequestMapping("/api/categories")
class CategoryController {
    @GetMapping
    fun getCategories(): List<CategoryResponse> {
        return EventCategory.entries.map { CategoryResponse(it, it.title) }
    }
}
