package ru.schigreva.evently

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EventlyApplication

fun main(args: Array<String>) {
    runApplication<EventlyApplication>(*args)
}
