package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Testene bruker åpenbart falske tall. Filteret ser bare på antall sifre. */
class FødselsnummerfilterTest {

    private val nummer = "12345678901"

    @Test
    fun `fjerner tall med 11 sifre`() {
        val resultat = Fødselsnummerfilter.fjern(nummer)
        assertThat(resultat.tekst).isEmpty()
        assertThat(resultat.fjernet).isTrue()
    }

    @Test
    fun `fjerner også tall som ikke har gyldig dato eller kontrollsiffer`() {
        listOf("99999999999", "00000000000", "32139912345").forEach {
            assertThat(Fødselsnummerfilter.fjern("kokk $it").tekst).isEqualTo("kokk")
        }
    }

    @Test
    fun `fjerner tall med mellomrom eller punktum etter seks sifre`() {
        assertThat(Fødselsnummerfilter.fjern("Treff 123456 78901 Oslo").tekst).isEqualTo("Treff Oslo")
        assertThat(Fødselsnummerfilter.fjern("Treff 123456.78901 Oslo").tekst).isEqualTo("Treff Oslo")
    }

    @Test
    fun `fjerner tall fra et navn på søk og beholder resten`() {
        val resultat = Fødselsnummerfilter.fjern("Treff for $nummer")
        assertThat(resultat.tekst).isEqualTo("Treff for")
        assertThat(resultat.fjernet).isTrue()
    }

    @Test
    fun `fjerner flere tall i samme tekst`() {
        assertThat(Fødselsnummerfilter.fjern("$nummer og 98765432109").tekst).isEqualTo("og")
    }

    @Test
    fun `beholder organisasjonsnummer, årstall og vanlige ord`() {
        val tekst = "Bedrift 123456789 i 2026 kokk"
        val resultat = Fødselsnummerfilter.fjern(tekst)
        assertThat(resultat.tekst).isEqualTo(tekst)
        assertThat(resultat.fjernet).isFalse()
    }

    @Test
    fun `beholder tall som er del av et lengre tall`() {
        assertThat(Fødselsnummerfilter.fjern("${nummer}0").fjernet).isFalse()
        assertThat(Fødselsnummerfilter.fjern("0$nummer").fjernet).isFalse()
    }
}
