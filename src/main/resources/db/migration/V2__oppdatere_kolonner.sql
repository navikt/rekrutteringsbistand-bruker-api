ALTER TABLE nyheter
    RENAME COLUMN beskrivelse TO innhold;

ALTER TABLE nyheter
    ADD COLUMN sistEndretAv TEXT,
    ADD COLUMN sistEndretDato TIMESTAMP;