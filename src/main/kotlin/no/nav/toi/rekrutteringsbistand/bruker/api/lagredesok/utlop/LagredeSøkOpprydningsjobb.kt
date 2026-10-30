package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.utlop

import no.nav.toi.rekrutteringsbistand.bruker.api.log
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.FeilUtenMelding
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkMetrikker
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkService
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Sletter søk som ikke er brukt, lagret eller valgt som standard på seks måneder.
 * Jobben er idempotent og kjører derfor på alle podene uten leader election.
 * Den logger bare antall, aldri innhold.
 */
class LagredeSøkOpprydningsjobb(
    private val service: LagredeSøkService,
    private val metrikker: LagredeSøkMetrikker,
    private val forsinkelse: Duration = Duration.ofMinutes(1),
    private val intervall: Duration = Duration.ofHours(1),
) : AutoCloseable {
    private val livssyklusLås = Any()
    private var startet = false
    private var stanset = false
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "lagrede-sok-opprydning").apply { isDaemon = true }
    }

    fun start() {
        synchronized(livssyklusLås) {
            if (startet || stanset) return
            startet = true
            executor.scheduleAtFixedRate(::kjør, forsinkelse.toMillis(), intervall.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    override fun close() {
        synchronized(livssyklusLås) {
            if (stanset) return
            stanset = true
            executor.shutdownNow()
        }
    }

    fun kjør() {
        try {
            val resultater = service.slettUtløpte()
            resultater.forEach { (domene, resultat) ->
                resultat
                    .onSuccess { antall ->
                        log.info("Opprydning av lagrede søk: slettet $antall utløpte søk for ${domene.stinavn}")
                    }
                    .onFailure { e ->
                        log.error("Opprydning av lagrede søk feilet for ${domene.stinavn}", FeilUtenMelding(e))
                    }
            }
            if (resultater.values.all { it.isSuccess }) metrikker.opprydningVellykket() else metrikker.opprydningFeilet()
        } catch (e: Exception) {
            metrikker.opprydningFeilet()
            log.error("Opprydning av lagrede søk feilet", FeilUtenMelding(e))
        }
    }
}
