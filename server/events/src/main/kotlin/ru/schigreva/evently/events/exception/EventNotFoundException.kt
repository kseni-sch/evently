package ru.schigreva.evently.events.exception

import java.util.UUID

class EventNotFoundException(id: UUID) : RuntimeException("Событие $id не найдено")
