package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RekrutteringstreffFilterTest {

    private val regler = RekrutteringstreffDomene.filterregler

    @Test
    fun `godtar alle filterparametere i treffsøket med URL-kodede verdier`() {
        val filter = listOf(
            "visning=valgte_kontorer",
            "kontorer=0315%2C1201",
            "statuser=PUBLISERT%2CFULLF%C3%98RT",
            "publisertStatuser=%C3%85PEN_FOR_S%C3%98KERE",
            "fylker=03",
            "kommuner=0301",
            "sortering=nyeste",
        ).joinToString("&")
        assertThat(regler.erGyldig(filter)).isTrue()
    }

    @Test
    fun `avviser parametere fra andre søk`() {
        assertThat(regler.erGyldig("portefolje=intern")).isFalse()
        assertThat(regler.erGyldig("side=2")).isFalse()
    }

    @Test
    fun `avviser ugyldige verdier`() {
        assertThat(regler.erGyldig("visning=hemmelig")).isFalse()
        assertThat(regler.erGyldig("kommuner=030")).isFalse()
        assertThat(regler.erGyldig("statuser=PUBLISERT%2Ckokk")).isFalse()
        assertThat(regler.erGyldig("sortering=")).isFalse()
    }
}
