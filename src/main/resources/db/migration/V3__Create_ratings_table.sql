create table ratings
(
    id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    value      SMALLINT  NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    user_id    INT       NOT NULL,
    CONSTRAINT fk_user_id
        FOREIGN KEY (user_id)
            REFERENCES users (id),
    movie_id   INT       NOT NULL,
    CONSTRAINT fk_movie_id
        FOREIGN KEY (movie_id)
            REFERENCES movies (id)
);

comment on column ratings.value is 'Value of rating as a whole number from range <0, 10>.';
