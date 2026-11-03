package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Tester mekanikken i [Filterregler] med egne testparametere. Reglene for hvert domene testes i domenepakkene. */
class FilterreglerTest {

    private enum class TestParameter(
        override val navn: String,
        override val regel: Filterregel,
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

    @Test
    fun `rens beholder et gyldig filter tegn for tegn`() {
        val filter = "farge=r%C3%B8d&fylker=03%2C46"
        assertThat(regler.rens(filter)).isEqualTo(filter)
        assertThat(regler.rens("")).isEqualTo("")
        assertThat(regler.rens("fylker=")).isEqualTo("fylker=")
    }

    @Test
    fun `rens fjerner parametere som ikke finnes lenger`() {
        assertThat(regler.rens("fjernet=1&farge=bl%C3%A5")).isEqualTo("farge=bl%C3%A5")
        assertThat(regler.rens("fjernet=1")).isEqualTo("")
    }

    @Test
    fun `rens fjerner ugyldige verdier i en liste og beholder resten`() {
        assertThat(regler.rens("fylker=03%2Cfjernet%2C46")).isEqualTo("fylker=03%2C46")
    }

    @Test
    fun `rens fjerner parameteren når ingen verdier er gyldige, så frontend bruker standardverdien`() {
        assertThat(regler.rens("farge=gr%C3%B8nn&fylker=03")).isEqualTo("fylker=03")
        assertThat(regler.rens("fylker=fjernet%2Cogs%C3%A5")).isEqualTo("")
    }

    @Test
    fun `rens fjerner feil format og duplikater, og resultatet er alltid gyldig`() {
        val filter = "farge&fylker=%ZZ&&fylker=03&fylker=46&farge=r%C3%B8d"
        val renset = regler.rens(filter)
        assertThat(renset).isEqualTo("fylker=03&farge=r%C3%B8d")
        assertThat(regler.erGyldig(renset)).isTrue()
    }
}
