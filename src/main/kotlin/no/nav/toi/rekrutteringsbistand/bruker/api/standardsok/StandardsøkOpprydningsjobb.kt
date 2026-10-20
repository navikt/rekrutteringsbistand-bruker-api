package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import no.nav.toi.rekrutteringsbistand.bruker.api.log
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sletter søk som ikke er brukt, lagret eller valgt som standard på seks måneder.
 * Jobben er idempotent og kjører derfor på alle podene uten leader election.
 * Den logger bare antall, aldri innhold.
 */
class StandardsøkOpprydningsjobb(
    private val service: StandardsøkService,
    private val metrikker: StandardsøkMetrikker,
    private val forsinkelse: Duration = Duration.ofMinutes(1),
    private val intervall: Duration = Duration.ofDays(1),
) {
    private val startet = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "standardsok-opprydning").apply { isDaemon = true }
    }

    fun start() {
        if (startet.getAndSet(true)) return
        executor.scheduleAtFixedRate(::kjør, forsinkelse.toMillis(), intervall.toMillis(), TimeUnit.MILLISECONDS)
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
