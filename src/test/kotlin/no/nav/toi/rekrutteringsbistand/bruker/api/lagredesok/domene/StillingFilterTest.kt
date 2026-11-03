package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StillingFilterTest {

    private val regler = LagredeSøkDomene.STILLING.filterregler

    @Test
    fun `godtar alle filterparametere i stillingssøket med URL-kodede verdier`() {
        val filter = listOf(
            "portefolje=mittKontor",
            "sortering=utl%C3%B8psdato",
            "statuser=%C3%85pen%20for%20s%C3%B8kere%2CUtl%C3%B8pt%20-%20Stengt%20for%20s%C3%B8kere",
            "stillingskategori=STILLING%2CJOBBMESSE",
            "inkludering=STATLIG_INKLUDERINGSDUGNAD",
            "inkluderingUnderkategori=PRIORITERT_M%C3%85LGRUPPE__HULL_I_CV_EN",
            "fylker=46",
            "kommuner=4601",
        ).joinToString("&")
        assertThat(regler.erGyldig(filter)).isTrue()
    }

    @Test
    fun `avviser parametere som ikke skal lagres`() {
        listOf("fritekst=kokk", "side=2", "brukStandardsok=true", "utenOppdrag=true", "modal=x").forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `avviser gamle verdier og parametere fra andre søk`() {
        assertThat(regler.erGyldig("statuser=publisert%2Cutl%C3%B8pt")).isFalse()
        assertThat(regler.erGyldig("portefolje=alle")).isFalse()
        assertThat(regler.erGyldig("visning=mine")).isFalse()
    }
}
