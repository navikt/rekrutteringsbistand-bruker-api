package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EtterregistreringFilterTest {

    private val regler = LagredeSøkDomene.ETTERREGISTRERING.filterregler

    @Test
    fun `godtar filtrene i etterregistreringssøket med URL-kodede verdier`() {
        val filter = listOf(
            "sortering=utl%C3%B8psdato",
            "inkludering=STATLIG_INKLUDERINGSDUGNAD",
            "inkluderingUnderkategori=PRIORITERT_M%C3%85LGRUPPE__HULL_I_CV_EN",
            "fylker=46",
            "kommuner=4601",
        ).joinToString("&")
        assertThat(regler.erGyldig(filter)).isTrue()
    }

    @Test
    fun `avviser filtre fra stillingssøket som etterregistreringen ikke har`() {
        listOf(
            "portefolje=mittKontor",
            "statuser=Fullf%C3%B8rt",
            "stillingskategori=FORMIDLING",
        ).forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `avviser parametere som ikke skal lagres`() {
        listOf("fritekst=kokk", "side=2", "brukStandardsok=true", "visStillingId=x").forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `avviser ugyldige verdier`() {
        assertThat(regler.erGyldig("sortering=nyeste")).isFalse()
        assertThat(regler.erGyldig("fylker=4")).isFalse()
    }
}
