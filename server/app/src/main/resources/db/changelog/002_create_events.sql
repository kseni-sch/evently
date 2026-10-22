--liquibase formatted sql

--changeset kseni-sch:002
CREATE TABLE evently.event_sources (
    source_url TEXT PRIMARY KEY
);

CREATE TABLE evently.events (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    start_date DATE,
    end_date DATE,
    start_time TIME,
    end_time TIME,
    location TEXT,
    min_price NUMERIC(12, 2),
    price_description TEXT,
    category TEXT NOT NULL,
    CONSTRAINT events_category_check CHECK (category IN (
        'CONCERT', 'EXHIBITION', 'THEATER', 'MOVIE', 'EXCURSION',
        'WORKSHOP', 'LECTURE', 'FESTIVAL_FAIR', 'SPORT', 'PROMOTION', 'OTHER'
    )),
    CONSTRAINT events_price_check CHECK (min_price IS NULL OR min_price >= 0),
    CONSTRAINT events_dates_check CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE evently.event_publications (
    event_id UUID NOT NULL REFERENCES evently.events(id) ON DELETE CASCADE,
    source_url TEXT NOT NULL REFERENCES evently.event_sources(source_url),
    title TEXT NOT NULL,
    PRIMARY KEY (event_id, source_url)
);

CREATE INDEX events_start_date_idx ON evently.events(start_date);
CREATE INDEX event_publications_source_url_idx ON evently.event_publications(source_url);
