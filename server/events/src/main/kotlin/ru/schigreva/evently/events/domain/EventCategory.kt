package ru.schigreva.evently.events.domain

enum class EventCategory(val title: String) {
    CONCERT("Концерты"),
    EXHIBITION("Выставки"),
    THEATER("Театр"),
    MOVIE("Кино"),
    EXCURSION("Экскурсии"),
    WORKSHOP("Мастер-классы"),
    LECTURE("Лекции"),
    FESTIVAL_FAIR("Фестивали и ярмарки"),
    SPORT("Спорт"),
    PROMOTION("Акции"),
    OTHER("Другое")
}
