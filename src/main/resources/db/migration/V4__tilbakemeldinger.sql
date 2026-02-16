CREATE TABLE tilbakemeldinger (
    id UUID PRIMARY KEY,
    navn VARCHAR(255),
    tilbakemelding TEXT NOT NULL,
    dato TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NY',
    trelloLenke VARCHAR(1024),
    kategori VARCHAR(50) NOT NULL,
    url VARCHAR(1024) NOT NULL
);
