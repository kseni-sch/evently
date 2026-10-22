package ru.schigreva.evently.api.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:NotBlank(message = "Имя не может быть пустым")
    @field:Size(max = 100, message = "Имя должно содержать не больше 100 символов")
    val name: String,
    @field:Size(max = 2000, message = "Интересы должны содержать не больше 2000 символов")
    val interests: String
)
