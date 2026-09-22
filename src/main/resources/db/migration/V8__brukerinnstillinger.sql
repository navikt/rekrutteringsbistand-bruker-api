CREATE TABLE brukerinnstillinger (
    navIdent TEXT PRIMARY KEY,
    antallLesteNyheter INTEGER NOT NULL DEFAULT 0 CHECK (antallLesteNyheter >= 0),
    darkMode BOOLEAN NOT NULL DEFAULT FALSE,
    windowMode BOOLEAN NOT NULL DEFAULT FALSE,
    tekststorrelse TEXT NOT NULL DEFAULT 'standard',
    CONSTRAINT brukerinnstillinger_tekststorrelse_sjekk
        CHECK (tekststorrelse IN ('liten', 'standard', 'stor', 'ekstra-stor'))
);