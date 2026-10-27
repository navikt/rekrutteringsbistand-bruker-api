package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import java.sql.Connection
import javax.sql.DataSource

/*
 * Alle låser og transaksjonsgrenser for standardsøk står i denne fila, og bare her bruker vi pg_advisory_xact_lock.
 *
 * Grensen på antall søk og regelen om ett standardsøk gjelder en gruppe rader per veileder og domene.
 * Når veilederen ikke har noen rader ennå, finnes det ingen rad å låse med FOR UPDATE, så vi bruker en
 * advisory lock i stedet. Den gjelder på tvers av podene og slippes når transaksjonen er ferdig.
 *
 * Reglene og begrunnelsen står i docs/2-arkitektur/transaksjoner.md i rekrutteringstreff-backend.
 */

/** Låser veilederens søk i domenet til transaksjonen er ferdig. */
internal fun Connection.låsVeileder(domene: StandardsøkDomene, navIdent: String) {
    prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { ps ->
        ps.setString(1, "${domene.tabell}:$navIdent")
        ps.executeQuery().use { it.next() }
    }
}

/**
 * Starter en transaksjon med READ COMMITTED, så tellingen etter en lås ser det andre transaksjoner har committet.
 * Poolen bruker REPEATABLE READ, der øyeblikksbildet ville vært tatt før låsen.
 */
internal fun <T> DataSource.executeInTransaction(block: (Connection) -> T): T =
    connection.use { c ->
        val opprinneligIsolasjon = c.transactionIsolation
        c.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (e: Throwable) {
            try {
                c.rollback()
            } catch (rollbackFeil: Exception) {
                e.addSuppressed(rollbackFeil)
            }
            throw e
        } finally {
            c.autoCommit = true
            c.transactionIsolation = opprinneligIsolasjon
        }
    }
