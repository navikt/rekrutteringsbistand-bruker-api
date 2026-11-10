package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import no.nav.toi.rekrutteringsbistand.bruker.api.LeaderElectionInterface
import no.nav.toi.rekrutteringsbistand.bruker.api.log
import no.nav.toi.rekrutteringsbistand.bruker.api.schedulerConfig.FastTidspunktScheduler
import no.nav.toi.rekrutteringsbistand.bruker.api.schedulerConfig.ScheduledTask
import no.nav.toi.rekrutteringsbistand.bruker.api.schedulerConfig.Scheduler
import no.nav.toi.rekrutteringsbistand.bruker.api.schedulerConfig.SchedulerGjentakelse
import java.time.LocalTime
import java.time.ZoneId

class TilbakemeldingerOpprydningScheduler(
    private val tilbakemeldingerRepository: TilbakemeldingerRepository,
    leaderElection: LeaderElectionInterface
) : ScheduledTask, Scheduler {

    companion object {
        private const val ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES = 2
    }

    private val scheduler: Scheduler = FastTidspunktScheduler(
        leaderElection = leaderElection,
        scheduledTask = this,
        kjøretidspunkt = LocalTime.of(4, 0),
        tidssone = ZoneId.of("Europe/Oslo"),
        gjentakelse = SchedulerGjentakelse.Daglig,
    )

    override fun start() {
        scheduler.start()
    }

    override fun stopp() {
        scheduler.stopp()
    }

    override fun wrapJobbkjøring() {
        scheduler.wrapJobbkjøring()
    }

    override fun kjørJobb() {
        log.info("Starter opprydning av tilbakemeldinger")
        slettNavnFraTilbakemeldinger()
        log.info("Opprydning av tilbakemeldinger ferdig")
    }

    private fun slettNavnFraTilbakemeldinger() {
        val tilbakemeldingerSomSkalFåSlettetNavn =
            tilbakemeldingerRepository.hentTilbakemeldingerDatoPassertMedAngittTid(
                ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES.toLong(),
                0
            )
        if (tilbakemeldingerSomSkalFåSlettetNavn.isEmpty()) {
            log.info("Ingen tilbakemeldinger å slette navn (ident) fra")
            return
        }
        val tilbakemeldingIder = tilbakemeldingerSomSkalFåSlettetNavn.map { it.id }

        log.info("Start opprydning/sletting av navn (identer) fra tilbakemeldinger")
        val antallTilbakemeldingerMedSlettetNavn = tilbakemeldingerRepository.slettNavnBatch(tilbakemeldingIder)
        log.info("${antallTilbakemeldingerMedSlettetNavn} tilbakemelding(er) eldre enn ${ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES} har fått navn (ident) slettet")
    }
}