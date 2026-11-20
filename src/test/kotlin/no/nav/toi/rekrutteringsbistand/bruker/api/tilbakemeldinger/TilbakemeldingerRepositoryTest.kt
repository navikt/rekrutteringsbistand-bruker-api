package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import no.nav.toi.rekrutteringsbistand.bruker.api.TestRunningApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilbakemeldingerRepositoryTest : TestRunningApplication() {

    lateinit var tilbakemeldingerRepository: TilbakemeldingerRepository
    lateinit var dataSource: DataSource

    // Testdata for å slippe å definere tilbakemeldinger i hver test
    private val tilbakemeldingMedNavn = Tilbakemelding(
        navn = "A123456",
        tilbakemelding = "Savner filter på arbeidssted",
        kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
    )

    private val annenTilbakemeldingMedNavn = Tilbakemelding(
        navn = "B123456",
        tilbakemelding = "Vanskelig å finne stillingsoppdrag",
        kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
    )

    private val anonymTilbakemelding = Tilbakemelding(
        navn = null,
        tilbakemelding = "Anonym tilbakemelding",
        kategori = TilbakemeldingKategori.ANNET,
    )

    private val behandletTilbakemelding = Tilbakemelding(
        navn = "C123456",
        tilbakemelding = "Ønsker eksport til Excel",
        kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
        status = TilbakemeldingStatus.VURDERING,
        trelloLenke = "https://trello.com/c/x",
    )

    @BeforeAll
    fun setup() {
        dataSource = appCtx.dataSource
        tilbakemeldingerRepository = TilbakemeldingerRepository(dataSource)
    }

    @AfterEach
    fun cleanup() {
        testDatabase.slettAlleTilbakemeldinger()
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

    @Test
    fun `slettNavnBatch med tom liste returnerer 0 og endrer ingenting`() {
        val tilbakemelding =  tilbakemeldingerRepository.opprett(tilbakemeldingMedNavn)

        val antallSlettede = tilbakemeldingerRepository.slettNavnBatch(emptyList())

        assertEquals(0, antallSlettede)
        assertEquals(tilbakemeldingMedNavn.navn, testDatabase.hentTilbakemelding(tilbakemelding.id).navn)
    }

    @Test
    fun `slettNavnBatch sletter navn for angitte id-er og returnerer antall`() {
        val tilbakemelding1 = tilbakemeldingerRepository.opprett(tilbakemeldingMedNavn)
        val tilbakemelding2 = tilbakemeldingerRepository.opprett(annenTilbakemeldingMedNavn)

        val antallSlettede = tilbakemeldingerRepository.slettNavnBatch(listOf(tilbakemelding1.id, tilbakemelding2.id))

        assertEquals(2, antallSlettede)
        assertNull(testDatabase.hentTilbakemelding(tilbakemelding1.id).navn)
        assertNull(testDatabase.hentTilbakemelding(tilbakemelding2.id).navn)
    }

    @Test
    fun `slettNavnBatch sletter ikke navn fra tilbakemeldinger som ikke er i listen`() {
        val slettes = tilbakemeldingerRepository.opprett(tilbakemeldingMedNavn)
        val beholdes = tilbakemeldingerRepository.opprett(annenTilbakemeldingMedNavn)

        tilbakemeldingerRepository.slettNavnBatch(listOf(slettes.id))

        assertNull(testDatabase.hentTilbakemelding(slettes.id).navn)
        assertEquals(annenTilbakemeldingMedNavn.navn, testDatabase.hentTilbakemelding(beholdes.id).navn)
    }

    @Test
    fun `slettNavnBatch beholder øvrige felter`() {
        val tilbakemelding = tilbakemeldingerRepository.opprett(behandletTilbakemelding)
        val tilbakemeldingFørSletting = tilbakemelding.copy()

        tilbakemeldingerRepository.slettNavnBatch(listOf(tilbakemelding.id))

        assertEquals(tilbakemeldingFørSletting.copy(navn = null), testDatabase.hentTilbakemelding(tilbakemelding.id))
    }

    @Test
    fun `slettNavnBatch med ukjente id-er returnerer 0`() {
        val tilbakemelding = tilbakemeldingerRepository.opprett(behandletTilbakemelding)

        val antallSlettede = tilbakemeldingerRepository.slettNavnBatch(listOf(UUID.randomUUID(), UUID.randomUUID()))

        assertEquals(0, antallSlettede)
        assertEquals(behandletTilbakemelding.navn, testDatabase.hentTilbakemelding(tilbakemelding.id).navn)
    }

    @Test
    fun `slettNavnBatch med blanding av kjente og ukjente id-er teller kun kjente`() {
        val kjentTilbakemelding = tilbakemeldingerRepository.opprett(tilbakemeldingMedNavn)

        val antallSlettede = tilbakemeldingerRepository.slettNavnBatch(listOf(kjentTilbakemelding.id, UUID.randomUUID()))

        assertEquals(1, antallSlettede)
        assertNull(testDatabase.hentTilbakemelding(kjentTilbakemelding.id).navn)
    }

    @Test
    fun `slettNavnBatch med duplikate id-er oppdaterer raden én gang`() {
        val tilbakemelding = tilbakemeldingerRepository.opprett(tilbakemeldingMedNavn)

        val antallSlettede = tilbakemeldingerRepository.slettNavnBatch(listOf(tilbakemelding.id, tilbakemelding.id))

        assertEquals(1, antallSlettede)
        assertNull(testDatabase.hentTilbakemelding(tilbakemelding.id).navn)
    }

    @Test
    fun `slettNavnBatch teller ikke rader der navn allerede er null`() {
        val anonymTilbakemelding = tilbakemeldingerRepository.opprett(this@TilbakemeldingerRepositoryTest.anonymTilbakemelding)
        val navngittTilbakemelding = testDatabase.opprettTilbakemelding("Navngitt")

        val antallSlettede = tilbakemeldingerRepository.slettNavnBatch(listOf(anonymTilbakemelding.id, navngittTilbakemelding.id))

        assertEquals(1, antallSlettede)
        assertNull(testDatabase.hentTilbakemelding(anonymTilbakemelding.id).navn)
        assertNull(testDatabase.hentTilbakemelding(navngittTilbakemelding.id).navn)
    }

    @Test
    fun `hentTilbakemeldingerDatoPassertMedAngittTid returnerer også tilbakemeldinger uten navn`() {
        val anonymTilbakemelding = testDatabase.opprettTilbakemelding(null, LocalDateTime.now().minusMonths(3))

        val resultat = tilbakemeldingerRepository.hentTilbakemeldingerDatoPassertMedAngittTid(2, 0)

        assertEquals(listOf(anonymTilbakemelding), resultat)
    }

    @Test
    fun `hentTilbakemeldingerDatoPassertMedAngittTid returnerer kun tilbakemeldinger eldre enn angitte måneder`() {
        val gammelTilbakemelding = testDatabase.opprettTilbakemelding("Gammel", LocalDateTime.now().minusMonths(2).minusDays(1))
        testDatabase.opprettTilbakemelding("Ny", LocalDateTime.now().minusMonths(2).plusDays(1))

        val resultat = tilbakemeldingerRepository.hentTilbakemeldingerDatoPassertMedAngittTid(2, 0)

        assertEquals(listOf(gammelTilbakemelding), resultat)
    }

    @Test
    fun `hentTilbakemeldingerDatoPassertMedAngittTid returnerer kun tilbakemeldinger eldre enn angitte dager`() {
        val gammelTilbakemelding = testDatabase.opprettTilbakemelding("Gammel", LocalDateTime.now().minusDays(11))
        testDatabase.opprettTilbakemelding("Ny", LocalDateTime.now().minusDays(9))

        val resultat = tilbakemeldingerRepository.hentTilbakemeldingerDatoPassertMedAngittTid(0, 10)

        assertEquals(listOf(gammelTilbakemelding.id), resultat.map { it.id })
    }

    @Test
    fun `hentTilbakemeldingerDatoPassertMedAngittTid kombinerer måneder og dager`() {
        val gammelTilbakemelding = testDatabase.opprettTilbakemelding("Gammel", LocalDateTime.now().minusMonths(2).minusDays(6))
        testDatabase.opprettTilbakemelding("Innenfor", LocalDateTime.now().minusMonths(2).minusDays(4))

        val resultat = tilbakemeldingerRepository.hentTilbakemeldingerDatoPassertMedAngittTid(2, 5)

        assertEquals(listOf(gammelTilbakemelding), resultat)
    }

    @Test
    fun `hentTilbakemeldingerDatoPassertMedAngittTid returnerer tom liste når ingen er gamle nok`() {
        tilbakemeldingerRepository.opprett(tilbakemeldingMedNavn)

        val resultat = tilbakemeldingerRepository.hentTilbakemeldingerDatoPassertMedAngittTid(2, 0)

        assertTrue(resultat.isEmpty())
    }

    @Test
    fun `hentTilbakemeldingerDatoPassertMedAngittTid returnerer alle felter`() {
        val tilbakemelding = testDatabase.opprettTilbakemelding("Bruker", LocalDateTime.now().minusMonths(3))
        val forventetResultat = tilbakemeldingerRepository.oppdater(
            tilbakemelding.id,
            TilbakemeldingOppdaterRequest(TilbakemeldingKategori.ANNET, "https://trello.com/c/y", TilbakemeldingStatus.FULLFORT)
        )

        val resultat = tilbakemeldingerRepository.hentTilbakemeldingerDatoPassertMedAngittTid(2, 0)

        assertEquals(listOf(forventetResultat), resultat)
    }
}
