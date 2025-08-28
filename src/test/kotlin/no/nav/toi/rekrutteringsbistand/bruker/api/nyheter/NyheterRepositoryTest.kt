package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NyheterRepositoryTest : TestRunningApplication() {

    var nyheterRepository: NyheterRepository? = null

    @BeforeAll
    fun setup() {
        val dataSource = appCtx.dataSource
        nyheterRepository = NyheterRepository(dataSource)
    }

    @Test
    fun `Skal kunne opprette en nyhet`() {
        val nyhet = Nyhet(
            tittel = "Ny nyhet",
            innhold = "Dette er en ny nyhet som informerer om ting",
            opprettetAv = "t1234",
            sistEndretAv = "t1234",
        )
        val lagretNyhet = nyheterRepository!!.lagreNyhet(nyhet)
        assertEquals(nyhet.tittel, lagretNyhet.tittel)
        assertEquals(nyhet.innhold, lagretNyhet.innhold)
        assertEquals(nyhet.opprettetAv, lagretNyhet.opprettetAv)
        assertEquals(nyhet.sistEndretAv, lagretNyhet.sistEndretAv)
    }
}