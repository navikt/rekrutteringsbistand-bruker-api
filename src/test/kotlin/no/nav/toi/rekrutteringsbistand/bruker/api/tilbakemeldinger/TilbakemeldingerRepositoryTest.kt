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
        )
        val lagret = tilbakemeldingerRepository.opprett(tilbakemelding)

        assertEquals(tilbakemelding.navn, lagret.navn)
        assertEquals(tilbakemelding.tilbakemelding, lagret.tilbakemelding)
        assertEquals(tilbakemelding.kategori, lagret.kategori)
        assertEquals(TilbakemeldingStatus.NY, lagret.status)
    }

    @Test
    fun `Skal kunne opprette en tilbakemelding med navn null`() {
        val tilbakemelding = Tilbakemelding(
            navn = null,
            tilbakemelding = "Anonym tilbakemelding",
            kategori = TilbakemeldingKategori.ANNET,
        )
        val lagret = tilbakemeldingerRepository.opprett(tilbakemelding)

        assertEquals(null, lagret.navn)
        assertEquals(tilbakemelding.tilbakemelding, lagret.tilbakemelding)
    }

    @Test
    fun `Skal kunne hente tilbakemeldinger med paginering`() {
        val t1 = Tilbakemelding(
            navn = "Bruker 1",
            tilbakemelding = "Første tilbakemelding",
            kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
        )
        val t2 = Tilbakemelding(
            navn = "Bruker 2",
            tilbakemelding = "Andre tilbakemelding",
            kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
        )
        tilbakemeldingerRepository.opprett(t1)
        tilbakemeldingerRepository.opprett(t2)

        val (side1, totalt) = tilbakemeldingerRepository.hentSide(1, 25)

        assertEquals(2, totalt)
        assertEquals(2, side1.size)
        assertTrue(side1.any { it.tilbakemelding == t1.tilbakemelding })
        assertTrue(side1.any { it.tilbakemelding == t2.tilbakemelding })
    }

    @Test
    fun `Skal returnere riktig side ved paginering`() {
        repeat(3) { i ->
            tilbakemeldingerRepository.opprett(
                Tilbakemelding(
                    navn = "Bruker $i",
                    tilbakemelding = "Tilbakemelding $i",
                    kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
                )
            )
        }

        val (side1, totalt1) = tilbakemeldingerRepository.hentSide(1, 2)
        val (side2, totalt2) = tilbakemeldingerRepository.hentSide(2, 2)

        assertEquals(3, totalt1)
        assertEquals(2, side1.size)
        assertEquals(3, totalt2)
        assertEquals(1, side2.size)
    }

    @Test
    fun `Skal filtrere bort avviste og fullforte tilbakemeldinger naar visAlle er false`() {
        val ny = tilbakemeldingerRepository.opprett(
            Tilbakemelding(navn = "A", tilbakemelding = "Ny", kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF)
        )
        val avvist = tilbakemeldingerRepository.opprett(
            Tilbakemelding(navn = "B", tilbakemelding = "Avvist", kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF)
        )
        val fullfort = tilbakemeldingerRepository.opprett(
            Tilbakemelding(navn = "C", tilbakemelding = "Fullført", kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF)
        )
        tilbakemeldingerRepository.oppdater(avvist.id, TilbakemeldingOppdaterRequest(avvist.kategori, null, TilbakemeldingStatus.AVVIST))
        tilbakemeldingerRepository.oppdater(fullfort.id, TilbakemeldingOppdaterRequest(fullfort.kategori, null, TilbakemeldingStatus.FULLFORT))

        val (filtrert, filtrertTotalt) = tilbakemeldingerRepository.hentSide(1, visAlle = false)
        assertEquals(1, filtrertTotalt)
        assertEquals(1, filtrert.size)
        assertEquals(ny.id, filtrert[0].id)

        val (alle, alleTotalt) = tilbakemeldingerRepository.hentSide(1, visAlle = true)
        assertEquals(3, alleTotalt)
        assertEquals(3, alle.size)
    }

    @Test
    fun `Skal kunne oppdatere kategori, trelloLenke og status`() {
        val tilbakemelding = Tilbakemelding(
            navn = "Test",
            tilbakemelding = "En tilbakemelding",
            kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
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
            )
        )

        tilbakemeldingerRepository.slett(lagret.id)

        val (tilbakemeldinger, _) = tilbakemeldingerRepository.hentSide(1)
        assertTrue(tilbakemeldinger.none { it.id == lagret.id })
    }
}
