package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import no.nav.toi.rekrutteringsbistand.bruker.api.LeaderElectionInterface
import no.nav.toi.rekrutteringsbistand.bruker.api.TestRunningApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilbakemeldingerOpprydningSchedulerTest : TestRunningApplication() {

    private lateinit var dataSource: DataSource
    private lateinit var repository: TilbakemeldingerRepository
    private lateinit var scheduler: TilbakemeldingerOpprydningScheduler

    private val leder = object : LeaderElectionInterface {
        override fun isLeader() = true
    }

    companion object {
        private const val ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES = 2.toLong()
    }

    @BeforeAll
    fun setup() {
        dataSource = appCtx.dataSource
        repository = TilbakemeldingerRepository(dataSource)
        scheduler = TilbakemeldingerOpprydningScheduler(repository, leder)
    }

    @AfterEach
    fun cleanup() {
        testDatabase.slettAlleTilbakemeldinger()
    }

    @Test
    fun `sletter navn fra tilbakemelding eldre enn ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES måneder`() {
        val gammelTilbakemelding =  testDatabase.opprettTilbakemelding("Gammel Bruker", LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES).minusDays(1))

        scheduler.kjørJobb()

        assertThat(testDatabase.hentTilbakemelding(gammelTilbakemelding.id).navn).isNull()
    }

    @Test
    fun `beholder navn på tilbakemelding nyere enn ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES måneder`() {
        val nyereTilbakemelding = testDatabase.opprettTilbakemelding("Nyere Bruker", LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES).plusDays(1))

        scheduler.kjørJobb()

        assertThat(testDatabase.hentTilbakemelding(nyereTilbakemelding.id).navn).isEqualTo("Nyere Bruker")
    }

    @Test
    fun `grensen går ved nøyaktig ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES måneder`() {
        val tilbakemeldingRettFørGrensen = testDatabase.opprettTilbakemelding("Så vidt gammel nok", LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES).minusMinutes(5))
        val tilbakemeldingRettEtterGrensen = testDatabase.opprettTilbakemelding("Ikke gammel nok", LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES).plusMinutes(5))

        scheduler.kjørJobb()

        assertThat(testDatabase.hentTilbakemelding(tilbakemeldingRettFørGrensen.id).navn).isNull()
        assertThat(testDatabase.hentTilbakemelding(tilbakemeldingRettEtterGrensen.id).navn).isEqualTo("Ikke gammel nok")
    }

    @Test
    fun `sletter kun navn, øvrige felter beholdes`() {
        val gammelTilbakemelding = testDatabase.opprettTilbakemelding("Gammel", LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES.plus(1)))
        repository.oppdater(
            gammelTilbakemelding.id,
            TilbakemeldingOppdaterRequest(
                kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
                trelloLenke = "https://trello.com/c/x",
                status = TilbakemeldingStatus.VURDERING,
            )
        )
        val tilbakemeldingFørJobbkjøring = testDatabase.hentTilbakemelding(gammelTilbakemelding.id)

        scheduler.kjørJobb()

        val tilbakemeldingEtterJobbkjøring = testDatabase.hentTilbakemelding(gammelTilbakemelding.id)
        assertThat(tilbakemeldingEtterJobbkjøring.navn).isNull()
        assertThat(tilbakemeldingEtterJobbkjøring).usingRecursiveComparison().ignoringFields("navn").isEqualTo(tilbakemeldingFørJobbkjøring)
    }

    @Test
    fun `sletter ikke selve tilbakemeldingene`() {
        repeat(3) { testDatabase.opprettTilbakemelding("Gammel $it", LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES.plus(1))) }
        repeat(2) { testDatabase.opprettTilbakemelding("Ny $it", LocalDateTime.now().minusDays(1)) }

        scheduler.kjørJobb()

        assertThat( testDatabase.hentAlleTilbakemeldinger()).hasSize(5)
    }

    @Test
    fun `håndterer blanding av gamle og nye tilbakemeldinger i samme kjøring`() {
        val gamleTilbakemeldinger = (1..5).map { testDatabase.opprettTilbakemelding("Gammel $it", LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES).minusDays(it.toLong())) }
        val nyeTilbakemeldinger = (1..3).map { testDatabase.opprettTilbakemelding("Ny $it", LocalDateTime.now().minusDays(it.toLong())) }

        scheduler.kjørJobb()

        gamleTilbakemeldinger.forEach { assertThat(testDatabase.hentTilbakemelding(it.id).navn).isNull() }
        nyeTilbakemeldinger.forEach { assertThat(testDatabase.hentTilbakemelding(it.id).navn).isEqualTo(it.navn) }
    }

    @Test
    fun `gammel tilbakemelding som allerede er anonym forblir uendret`() {
        val anonymTilbakemelding = testDatabase.opprettTilbakemelding(
            null,
            LocalDateTime.now().minusMonths(ANTALL_MÅNEDER_ETTER_TILBAKEMELDING_NAVN_SLETTES.plus(4)),
            "Anonym tilbakemelding")

        scheduler.kjørJobb()

        assertThat(testDatabase.hentTilbakemelding(anonymTilbakemelding.id).navn).isNull()
        assertThat(testDatabase.hentTilbakemelding(anonymTilbakemelding.id).tilbakemelding).isEqualTo("Anonym tilbakemelding")
    }

    @Test
    fun `kjører uten feil når det ikke finnes tilbakemeldinger`() {
        assertThatCode { scheduler.kjørJobb() }.doesNotThrowAnyException()
    }

    @Test
    fun `kjører uten feil når ingen tilbakemeldinger er gamle nok`() {
        val ny = testDatabase.opprettTilbakemelding("Ny")

        assertThatCode { scheduler.kjørJobb() }.doesNotThrowAnyException()
        assertThat(testDatabase.hentTilbakemelding(ny.id).navn).isEqualTo("Ny")
    }

    @Test
    fun `jobben er idempotent ved gjentatte kjøringer`() {
        val gammelTilbakemelding = testDatabase.opprettTilbakemelding("Gammel", LocalDateTime.now().minusMonths(3))
        val nyTilbakemelding = testDatabase.opprettTilbakemelding("Ny", LocalDateTime.now().minusDays(1))

        repeat(3) { scheduler.kjørJobb() }

        assertThat(testDatabase.hentTilbakemelding(gammelTilbakemelding.id).navn).isNull()
        assertThat(testDatabase.hentTilbakemelding(nyTilbakemelding.id).navn).isEqualTo("Ny")
    }
}