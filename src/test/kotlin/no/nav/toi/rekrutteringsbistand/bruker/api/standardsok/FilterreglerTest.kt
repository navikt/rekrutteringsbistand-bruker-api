package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FilterreglerTest {

    private val regler = StandardsøkDomene.REKRUTTERINGSTREFF.filterregler

    @Test
    fun `tomt filter er gyldig`() {
        assertThat(regler.erGyldig("")).isTrue()
    }

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
    fun `godtar tom statusliste`() {
        assertThat(regler.erGyldig("statuser=")).isTrue()
    }

    @Test
    fun `avviser ukjente parametere, så fritekst ikke kan lagres i filteret`() {
        assertThat(regler.erGyldig("fritekst=kokk")).isFalse()
        assertThat(regler.erGyldig("visning=alle&side=2")).isFalse()
    }

    @Test
    fun `avviser ugyldige verdier`() {
        assertThat(regler.erGyldig("visning=hemmelig")).isFalse()
        assertThat(regler.erGyldig("fylker=abc")).isFalse()
        assertThat(regler.erGyldig("kommuner=030")).isFalse()
        assertThat(regler.erGyldig("statuser=PUBLISERT%2Ckokk")).isFalse()
        assertThat(regler.erGyldig("sortering=")).isFalse()
    }

    @Test
    fun `avviser feil format`() {
        assertThat(regler.erGyldig("visning")).isFalse()
        assertThat(regler.erGyldig("fylker=03&fylker=46")).isFalse()
        assertThat(regler.erGyldig("fylker=%ZZ")).isFalse()
        assertThat(regler.erGyldig("&")).isFalse()
    }

    @Test
    fun `avviser for mange verdier i én parameter`() {
        val mange = (1..51).joinToString("%2C") { "03" }
        assertThat(regler.erGyldig("fylker=$mange")).isFalse()
    }
}
