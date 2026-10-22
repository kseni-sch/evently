package ru.schigreva.evently.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import ru.schigreva.evently.api.error.ApiError

@Configuration
class SecurityConfig {
    @Bean
    fun authenticationEntryPoint(mapper: ObjectMapper): AuthenticationEntryPoint {
        return AuthenticationEntryPoint { _, response, _ ->
            response.status = 401
            response.setHeader("WWW-Authenticate", "Bearer")
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            mapper.writeValue(response.writer, ApiError("Необходимо войти в аккаунт"))
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity, entryPoint: AuthenticationEntryPoint): SecurityFilterChain {
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**", "/api/categories", "/actuator/health").permitAll()
                it.requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                it.requestMatchers("/error").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt(Customizer.withDefaults())
                it.authenticationEntryPoint(entryPoint)
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
        return http.build()
    }
}
