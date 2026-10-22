--liquibase formatted sql

--changeset kseni-sch:004
CREATE TABLE evently.users (
    id UUID PRIMARY KEY,
    auth_subject VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    interests VARCHAR(2000) NOT NULL DEFAULT '',
    CONSTRAINT users_name_check CHECK (length(trim(name)) > 0)
);
