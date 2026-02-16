package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import no.nav.toi.rekrutteringsbistand.bruker.api.nyheter.TestRunningApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilbakemeldingerRepositoryTest : TestRunningApplication() {

    lateinit var tilbakemeldingerRepository: TilbakemeldingerRepository
    lateinit var dataSource: DataSource

    @BeforeAll
    fun setup() {
        dataSource = appCtx.dataSource
        tilbakemeldingerRepository = TilbakemeldingerRepository(dataSource)
    }

    @AfterEach
    fun cleanup() {
        dataSource.connection.use {
            it.prepareStatement("DELETE FROM tilbakemeldinger").executeUpdate()
        }
    }

    @Test
    fun `Skal kunne opprette en tilbakemelding`() {
        val tilbakemelding = Tilbakemelding(
            navn = "Test Testesen",
            tilbakemelding = "Dette er en tilbakemelding",
            kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
            url = "/stillinger",
        )
        val lagret = tilbakemeldingerRepository.opprett(tilbakemelding)

        assertEquals(tilbakemelding.navn, lagret.navn)
        assertEquals(tilbakemelding.tilbakemelding, lagret.tilbakemelding)
        assertEquals(tilbakemelding.kategori, lagret.kategori)
        assertEquals(tilbakemelding.url, lagret.url)
        assertEquals(TilbakemeldingStatus.NY, lagret.status)
    }

    @Test
    fun `Skal kunne opprette en tilbakemelding med navn null`() {
        val tilbakemelding = Tilbakemelding(
            navn = null,
            tilbakemelding = "Anonym tilbakemelding",
            kategori = TilbakemeldingKategori.FORSLAG,
            url = "/kandidater",
        )
        val lagret = tilbakemeldingerRepository.opprett(tilbakemelding)

        assertEquals(null, lagret.navn)
        assertEquals(tilbakemelding.tilbakemelding, lagret.tilbakemelding)
    }

    @Test
    fun `Skal kunne hente alle tilbakemeldinger`() {
        val t1 = Tilbakemelding(
            navn = "Bruker 1",
            tilbakemelding = "Første tilbakemelding",
            kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
            url = "/stillinger",
        )
        val t2 = Tilbakemelding(
            navn = "Bruker 2",
            tilbakemelding = "Andre tilbakemelding",
            kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
            url = "/kandidater",
        )
        tilbakemeldingerRepository.opprett(t1)
        tilbakemeldingerRepository.opprett(t2)

        val alle = tilbakemeldingerRepository.hentAlle()

        assertEquals(2, alle.size)
        assertTrue(alle.any { it.tilbakemelding == t1.tilbakemelding })
        assertTrue(alle.any { it.tilbakemelding == t2.tilbakemelding })
    }

    @Test
    fun `Skal kunne oppdatere kategori, trelloLenke og status`() {
        val tilbakemelding = Tilbakemelding(
            navn = "Test",
            tilbakemelding = "En tilbakemelding",
            kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
            url = "/stillinger",
        )
        val lagret = tilbakemeldingerRepository.opprett(tilbakemelding)

        val oppdaterRequest = TilbakemeldingOppdaterRequest(
            kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
            trelloLenke = "https://trello.com/c/abc123",
            status = TilbakemeldingStatus.VURDERING,
        )
        val oppdatert = tilbakemeldingerRepository.oppdater(lagret.id, oppdaterRequest)

        assertEquals(TilbakemeldingKategori.STILLINGSOPPDRAG, oppdatert.kategori)
        assertEquals("https://trello.com/c/abc123", oppdatert.trelloLenke)
        assertEquals(TilbakemeldingStatus.VURDERING, oppdatert.status)
        assertEquals(lagret.tilbakemelding, oppdatert.tilbakemelding)
    }

    @Test
    fun `Skal kunne sette status til AVVIST`() {
        val lagret = tilbakemeldingerRepository.opprett(
            Tilbakemelding(
                navn = "Test",
                tilbakemelding = "Avvis denne",
                kategori = TilbakemeldingKategori.ETTERREGISTRERINGER,
                url = "/test",
            )
        )

        val oppdatert = tilbakemeldingerRepository.oppdater(
            lagret.id,
            TilbakemeldingOppdaterRequest(
                kategori = lagret.kategori,
                trelloLenke = null,
                status = TilbakemeldingStatus.AVVIST,
            )
        )

        assertEquals(TilbakemeldingStatus.AVVIST, oppdatert.status)
    }

    @Test
    fun `Skal kunne sette status til FULLFORT`() {
        val lagret = tilbakemeldingerRepository.opprett(
            Tilbakemelding(
                navn = "Test",
                tilbakemelding = "Fullfør denne",
                kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
                url = "/test",
            )
        )

        val oppdatert = tilbakemeldingerRepository.oppdater(
            lagret.id,
            TilbakemeldingOppdaterRequest(
                kategori = lagret.kategori,
                trelloLenke = null,
                status = TilbakemeldingStatus.FULLFORT,
            )
        )

        assertEquals(TilbakemeldingStatus.FULLFORT, oppdatert.status)
    }

    @Test
    fun `Skal kunne slette en tilbakemelding`() {
        val lagret = tilbakemeldingerRepository.opprett(
            Tilbakemelding(
                navn = "Test",
                tilbakemelding = "Slett denne",
                kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
                url = "/stillinger",
            )
        )

        tilbakemeldingerRepository.slett(lagret.id)

        val alle = tilbakemeldingerRepository.hentAlle()
        assertTrue(alle.none { it.id == lagret.id })
    }
}
