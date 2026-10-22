--liquibase formatted sql

--changeset kseni-sch:003
CREATE TABLE evently.processed_posts (
    source_url TEXT PRIMARY KEY,
    external_id TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
