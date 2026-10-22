package ru.schigreva.evently.api.error

import org.springframework.http.HttpStatus
import ru.schigreva.evently.recommendations.exception.RecommendationsUnavailableException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import ru.schigreva.evently.events.exception.EventNotFoundException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(RecommendationsUnavailableException::class)
    fun handleUnavailable(exception: RecommendationsUnavailableException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError(exception.message!!))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Некорректные данные"
        return ResponseEntity.badRequest().body(ApiError(message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleInvalidBody(): ResponseEntity<ApiError> {
        return ResponseEntity.badRequest().body(ApiError("Некорректное тело запроса"))
    }

    @ExceptionHandler(EventNotFoundException::class)
    fun handleNotFound(exception: EventNotFoundException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError(exception.message!!))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleInvalidType(exception: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> {
        return ResponseEntity.badRequest().body(ApiError("Некорректный параметр: ${exception.name}"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalidArgument(exception: IllegalArgumentException): ResponseEntity<ApiError> {
        return ResponseEntity.badRequest().body(ApiError(exception.message ?: "Некорректные параметры запроса"))
    }
}
