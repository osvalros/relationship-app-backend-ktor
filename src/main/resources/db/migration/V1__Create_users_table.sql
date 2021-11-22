create table users
(
    id       SERIAL PRIMARY KEY,
    name     VARCHAR   NOT NULL UNIQUE,
    password VARCHAR   NOT NULL,
    created  timestamp NOT NULL
);