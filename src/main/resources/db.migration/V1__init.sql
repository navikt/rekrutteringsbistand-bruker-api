CREATE TABLE nyheter (
    id BIGSERIAL PRIMARY KEY,
    nyhetId UUID NOT NULL UNIQUE,
    tittel TEXT,
    beskrivelse TEXT,
    opprettetDato TIMESTAMP,
    opprettetAv TEXT
);