create table movies
(
    id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR   NOT NULL UNIQUE,
    created_at timestamp NOT NULL DEFAULT now(),
    viewed_at  timestamp,
    creator_id INT,
    CONSTRAINT fk_creator
        FOREIGN KEY (creator_id)
            REFERENCES users (id)
);