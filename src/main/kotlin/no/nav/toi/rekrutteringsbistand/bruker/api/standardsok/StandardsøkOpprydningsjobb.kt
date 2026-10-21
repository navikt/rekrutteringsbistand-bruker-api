package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import no.nav.toi.rekrutteringsbistand.bruker.api.log
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Sletter søk som ikke er brukt, lagret eller valgt som standard på seks måneder.
 * Jobben er idempotent og kjører derfor på alle podene uten leader election.
 * Den logger bare antall, aldri innhold.
 */
class StandardsøkOpprydningsjobb(
    private val service: StandardsøkService,
    private val metrikker: StandardsøkMetrikker,
    private val forsinkelse: Duration = Duration.ofMinutes(1),
    private val intervall: Duration = Duration.ofHours(1),
) : AutoCloseable {
    private val livssyklusLås = Any()
    private var startet = false
    private var stanset = false
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "standardsok-opprydning").apply { isDaemon = true }
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
            val slettet = service.slettUtløpte()
            metrikker.opprydningVellykket()
            slettet.forEach { (domene, antall) ->
                log.info("Opprydning av lagrede søk: slettet $antall utløpte søk for ${domene.stinavn}")
            }
        } catch (e: Exception) {
            metrikker.opprydningFeilet()
            log.error("Opprydning av lagrede søk feilet", FeilUtenMelding(e))
        }
    }
}
