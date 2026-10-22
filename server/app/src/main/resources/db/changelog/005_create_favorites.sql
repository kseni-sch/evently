--liquibase formatted sql

--changeset kseni-sch:005
CREATE TABLE evently.favorites (
    user_id UUID NOT NULL REFERENCES evently.users(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES evently.events(id) ON DELETE CASCADE,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, event_id)
);

CREATE INDEX favorites_user_saved_at_idx ON evently.favorites(user_id, saved_at DESC, event_id);
CREATE INDEX favorites_event_id_idx ON evently.favorites(event_id);
