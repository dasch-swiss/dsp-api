CREATE TABLE project
(
    id         INTEGER PRIMARY KEY,
    shortcode  TEXT    NOT NULL UNIQUE,
    created_at INTEGER NOT NULL
);
