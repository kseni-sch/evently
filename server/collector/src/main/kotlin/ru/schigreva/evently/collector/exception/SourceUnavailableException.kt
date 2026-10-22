package ru.schigreva.evently.collector.exception

class SourceUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
