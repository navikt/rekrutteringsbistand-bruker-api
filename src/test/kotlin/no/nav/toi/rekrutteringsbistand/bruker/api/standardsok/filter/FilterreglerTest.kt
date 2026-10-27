package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Tester mekanikken i [Filterregler] med egne testparametere. Reglene for hvert domene testes i domenepakkene. */
class FilterreglerTest {

    private enum class TestParameter(
        override val navn: String,
        override val regel: (String) -> Boolean,
    ) : Filterparameter {
        FARGE("farge", enAv("rød", "blå")),
        FYLKER("fylker", fylkesnumre),
    }

    private val regler = filterreglerFor<TestParameter>()

    @Test
    fun `tomt filter er gyldig`() {
        assertThat(regler.erGyldig("")).isTrue()
    }

    @Test
    fun `godtar kjente parametere med URL-kodede verdier`() {
        assertThat(regler.erGyldig("farge=r%C3%B8d&fylker=03%2C46")).isTrue()
    }

    @Test
    fun `godtar tom liste`() {
        assertThat(regler.erGyldig("fylker=")).isTrue()
    }

    @Test
    fun `avviser ukjente parametere, så fritekst ikke kan lagres i filteret`() {
        assertThat(regler.erGyldig("fritekst=kokk")).isFalse()
        assertThat(regler.erGyldig("farge=blå&side=2")).isFalse()
    }

    @Test
    fun `avviser ugyldige verdier`() {
        assertThat(regler.erGyldig("farge=grønn")).isFalse()
        assertThat(regler.erGyldig("farge=")).isFalse()
        assertThat(regler.erGyldig("fylker=abc")).isFalse()
        assertThat(regler.erGyldig("fylker=03%2Ckokk")).isFalse()
    }

    @Test
    fun `avviser feil format`() {
        assertThat(regler.erGyldig("farge")).isFalse()
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
