-- Lagrede søk. Hvert domene har sin egen tabell med lik oppbygning.
-- Navn og fritekst kan inneholde personopplysninger. Raden slettes seks måneder etter sistAktiv.
-- Den delvise unike indeksen er siste vern mot flere standardsøk per veileder. Servicen låser per veileder
-- og sørger for at en veileder med aktive søk alltid har nøyaktig ett standardsøk.

CREATE TABLE standardsok_rekrutteringstreff (
    id UUID PRIMARY KEY,
    navIdent TEXT NOT NULL,
    navn TEXT NOT NULL,
    erStandard BOOLEAN NOT NULL DEFAULT FALSE,
    filter TEXT NOT NULL,
    fritekst TEXT[] NOT NULL DEFAULT '{}',
    sistAktiv DATE NOT NULL
);

CREATE INDEX standardsok_rekrutteringstreff_navident_idx
    ON standardsok_rekrutteringstreff (navIdent);

CREATE UNIQUE INDEX standardsok_rekrutteringstreff_ett_standardsok_idx
    ON standardsok_rekrutteringstreff (navIdent)
    WHERE erStandard;

CREATE INDEX standardsok_rekrutteringstreff_sistaktiv_idx
    ON standardsok_rekrutteringstreff (sistAktiv);

CREATE TABLE standardsok_stilling (
    id UUID PRIMARY KEY,
    navIdent TEXT NOT NULL,
    navn TEXT NOT NULL,
    erStandard BOOLEAN NOT NULL DEFAULT FALSE,
    filter TEXT NOT NULL,
    fritekst TEXT[] NOT NULL DEFAULT '{}',
    sistAktiv DATE NOT NULL
);

CREATE INDEX standardsok_stilling_navident_idx
    ON standardsok_stilling (navIdent);

CREATE UNIQUE INDEX standardsok_stilling_ett_standardsok_idx
    ON standardsok_stilling (navIdent)
    WHERE erStandard;

CREATE INDEX standardsok_stilling_sistaktiv_idx
    ON standardsok_stilling (sistAktiv);
