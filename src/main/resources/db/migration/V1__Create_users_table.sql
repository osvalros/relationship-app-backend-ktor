create table users
(
    id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR   NOT NULL UNIQUE,
    password   VARCHAR   NOT NULL,
    created_at timestamp NOT NULL DEFAULT now()
);