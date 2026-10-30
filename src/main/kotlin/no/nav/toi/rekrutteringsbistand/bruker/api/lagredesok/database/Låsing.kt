package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.database

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene.LagredeSøkDomene
import java.sql.Connection
import javax.sql.DataSource

/*
 * Alle låser og transaksjonsgrenser for lagrede søk står i denne fila, og bare her bruker vi pg_advisory_xact_lock.
 *
 * Grensen på antall søk og regelen om ett standardsøk gjelder en gruppe rader per veileder og domene.
 * Når veilederen ikke har noen rader ennå, finnes det ingen rad å låse med FOR UPDATE, så vi bruker en
 * advisory lock i stedet. Den gjelder på tvers av podene og slippes når transaksjonen er ferdig.
 *
 * Reglene og begrunnelsen står i docs/2-arkitektur/transaksjoner.md i rekrutteringstreff-backend.
 */

internal fun <T> DataSource.medLåstNavIdent(domene: LagredeSøkDomene, navIdent: String, block: (Connection) -> T): T =
    iTransaksjon { c ->
        c.låsNavIdent(domene, navIdent)
        block(c)
    }

private fun Connection.låsNavIdent(domene: LagredeSøkDomene, navIdent: String) {
    prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { ps ->
        ps.setString(1, "lagrede-sok:${domene.stinavn}:$navIdent")
        ps.executeQuery().use { it.next() }
    }
}

/**
 * Starter en transaksjon med READ COMMITTED, så tellingen etter en lås ser det andre transaksjoner har committet.
 * Poolen bruker REPEATABLE READ, der øyeblikksbildet ville vært tatt før låsen, og der samtidige endringer av
 * samme rad gir serialiseringsfeil. Alle skrivinger til lagrede søk går derfor gjennom denne funksjonen.
 */
internal fun <T> DataSource.iTransaksjon(block: (Connection) -> T): T =
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
