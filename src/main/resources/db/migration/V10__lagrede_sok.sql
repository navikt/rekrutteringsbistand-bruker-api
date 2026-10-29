-- Lagrede søk for alle domener i én tabell. Erstatter tabellene fra V9, som bare er tatt i bruk i dev.
-- Dataene i dev beholdes ikke.
-- Gyldige verdier i domene styres av enumen LagredeSøkDomene i koden, så nye domener krever ingen migrering.
-- Navn og fritekst kan inneholde personopplysninger. Raden slettes seks måneder etter sistAktiv.
-- En veileder har høyst ett standardsøk per domene. Uten merket søk er vanlig søk standard.

DROP TABLE IF EXISTS standardsok_rekrutteringstreff;
DROP TABLE IF EXISTS standardsok_stilling;

CREATE TABLE lagrede_sok (
    id UUID PRIMARY KEY,
    domene TEXT NOT NULL,
    navIdent TEXT NOT NULL,
    navn TEXT NOT NULL,
    erStandard BOOLEAN NOT NULL DEFAULT FALSE,
    filter TEXT NOT NULL,
    fritekst TEXT[] NOT NULL DEFAULT '{}',
    sistAktiv DATE NOT NULL
);

CREATE INDEX lagrede_sok_navident_domene_idx
    ON lagrede_sok (navIdent, domene);

CREATE UNIQUE INDEX lagrede_sok_ett_standardsok_idx
    ON lagrede_sok (navIdent, domene)
    WHERE erStandard;

CREATE INDEX lagrede_sok_domene_sistaktiv_idx
    ON lagrede_sok (domene, sistAktiv);
