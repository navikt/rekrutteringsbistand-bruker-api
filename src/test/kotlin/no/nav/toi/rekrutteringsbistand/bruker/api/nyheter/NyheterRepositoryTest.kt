package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NyheterRepositoryTest : TestRunningApplication() {

    lateinit var nyheterRepository: NyheterRepository

    lateinit var dataSource : DataSource

    @BeforeAll
    fun setup() {
        dataSource = appCtx.dataSource
        nyheterRepository = NyheterRepository(dataSource)
    }

    @AfterEach
    fun cleanup() {
        dataSource.connection.use {
            it.prepareStatement("DELETE FROM nyheter").executeUpdate()
        }
    }

    @Test
    fun `Skal kunne opprette en nyhet`() {
        val nyhet = Nyhet(
            tittel = "Ny nyhet",
            innhold = "Dette er en ny nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
            status = Status.AKTIV
        )
        val lagretNyhet = nyheterRepository.lagreNyhet(nyhet)
        assertEquals(nyhet.tittel, lagretNyhet.tittel)
        assertEquals(nyhet.innhold, lagretNyhet.innhold)
        assertEquals(nyhet.opprettetAv, lagretNyhet.opprettetAv)
        assertEquals(nyhet.sistEndretAv, lagretNyhet.sistEndretAv)
    }

    @Test
    fun `Skal kunne oppdatere en eksisterende nyhet`() {
        val nyhet = Nyhet(
            tittel = "Ny nyhet",
            innhold = "Dette er en ny nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
            status = Status.AKTIV
        )
        val lagretNyhet = nyheterRepository.lagreNyhet(nyhet)
        if (lagretNyhet.nyhetId != null) {
            val hentetNyhet = nyheterRepository.hentNyhetPåId(lagretNyhet.nyhetId)

            assertEquals(nyhet.tittel, hentetNyhet.tittel)
            assertEquals(nyhet.innhold, hentetNyhet.innhold)
            assertEquals(nyhet.opprettetAv, hentetNyhet.opprettetAv)
            assertEquals(nyhet.sistEndretAv, hentetNyhet.sistEndretAv)

            val oppdatertNyhet = Nyhet(
                nyhetId = lagretNyhet.nyhetId,
                tittel = "Ny nyhet er nå oppdatert",
                innhold = "Dette er ny nyhet som informerer om enda flere ting",
                opprettetAv = "t1234",
                sistEndretAv = "t5678",
                status = Status.AKTIV
            )
            val lagretOppdatertNyhet  = nyheterRepository.lagreNyhet(oppdatertNyhet)
            if (lagretOppdatertNyhet.nyhetId != null) {
                val hentetOppdatertNyhet = nyheterRepository.hentNyhetPåId(lagretOppdatertNyhet.nyhetId)

                assertEquals(hentetNyhet.nyhetId, hentetOppdatertNyhet.nyhetId)
                assertEquals(oppdatertNyhet.tittel, hentetOppdatertNyhet.tittel)
                assertEquals(oppdatertNyhet.innhold, hentetOppdatertNyhet.innhold)
                assertEquals(hentetOppdatertNyhet.opprettetAv, hentetOppdatertNyhet.opprettetAv)
                assertEquals(oppdatertNyhet.sistEndretAv, hentetOppdatertNyhet.sistEndretAv)
            } else throw NullPointerException("nyhetId ble ikke satt ved lagring, noe er galt")

        } else throw NullPointerException("nyhetId ble ikke satt ved lagring, noe er galt")

    }

    @Test
    fun `Skal kunne hente alle nyheter`() {
        val nyhet1 = Nyhet(
            tittel = "Nyhet 1",
            innhold = "Dette er første nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
            status = Status.AKTIV
        )
        val nyhet2 = Nyhet(
            tittel = "Nyhet 2",
            innhold = "Dette er andre nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
            status = Status.AKTIV
        )
        val nyhet3 = Nyhet(
            tittel = "Nyhet 3",
            innhold = "Dette er tredje nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
            status = Status.AKTIV
        )
        nyheterRepository.lagreNyhet(nyhet1)
        nyheterRepository.lagreNyhet(nyhet2)
        nyheterRepository.lagreNyhet(nyhet3)

        val hentedeNyheter = nyheterRepository.hentNyheter()

        assertTrue(hentedeNyheter.any { it.tittel == nyhet1.tittel })
        assertTrue(hentedeNyheter.any { it.tittel == nyhet2.tittel })
        assertTrue(hentedeNyheter.any { it.tittel == nyhet3.tittel })
    }

    @Test
    fun `Skal kunne hent en nyhet på spesifikk ID`() {
        val nyhet = Nyhet(
            tittel = "Ny nyhet",
            innhold = "Dette er en ny nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
            status = Status.AKTIV
        )
        val lagretNyhet = nyheterRepository.lagreNyhet(nyhet)

        if (lagretNyhet.nyhetId != null) {
            val hentetNyhet = nyheterRepository.hentNyhetPåId(lagretNyhet.nyhetId)

            assertEquals(nyhet.tittel, hentetNyhet.tittel)
            assertEquals(nyhet.innhold, hentetNyhet.innhold)
            assertEquals(nyhet.opprettetAv, hentetNyhet.opprettetAv)
            assertEquals(nyhet.sistEndretAv, hentetNyhet.sistEndretAv)
        } else throw NullPointerException("nyhetId ble ikke satt ved lagring, noe er galt")
    }

    @Test
    fun `Skal kunne slette en nyhet ved å sette status til SLETTET`() {
        val nyhet = Nyhet(
            tittel = "Ny nyhet",
            innhold = "Dette er en ny nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
            status = Status.AKTIV
        )
        val lagretNyhet = nyheterRepository.lagreNyhet(nyhet)

        if (lagretNyhet.nyhetId != null) {
            val hentetNyhet = nyheterRepository.hentNyhetPåId(lagretNyhet.nyhetId)

            assertEquals(Status.AKTIV, hentetNyhet.status)

            nyheterRepository.slettNyhet(lagretNyhet.nyhetId, "t5678")
            val hentetSlettetNyhet = nyheterRepository.hentNyhetPåId(lagretNyhet.nyhetId)
            assertEquals(Status.SLETTET, hentetSlettetNyhet.status)
        } else throw NullPointerException("nyhetId ble ikke satt ved lagring, noe er galt")
    }
}