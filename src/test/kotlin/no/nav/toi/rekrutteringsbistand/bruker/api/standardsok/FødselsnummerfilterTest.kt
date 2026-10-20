package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Testene lager nummer med gyldige kontrollsifre når de kjører, så kildekoden ikke inneholder nummer som ligner ekte.
 * Vanlige fødselsnumre lages med fødselsår i 1854 (individnummer 500–749), så de kan ikke tilhøre levende personer.
 */
class FødselsnummerfilterTest {

    private val fødselsnummer = FødselsnummerTestdata.lagNummer(dag = 31, måned = 12, år = 1854, individer = 500..749)
    private val dNummer = FødselsnummerTestdata.lagNummer(dag = 31 + 40, måned = 12, år = 1854, individer = 500..749)
    private val hNummer = FødselsnummerTestdata.lagNummer(dag = 15, måned = 12 + 40, år = 1854, individer = 500..749)
    private val syntetiskNummer = FødselsnummerTestdata.lagNummer(dag = 15, måned = 6 + 80, år = 1990, individer = 0..499)

    @Test
    fun `fjerner gyldig fødselsnummer`() {
        val resultat = Fødselsnummerfilter.fjern(fødselsnummer)
        assertThat(resultat.tekst).isEmpty()
        assertThat(resultat.fjernet).isTrue()
    }

    @Test
    fun `fjerner D-nummer, H-nummer og syntetiske nummer`() {
        listOf(dNummer, hNummer, syntetiskNummer).forEach { nummer ->
            assertThat(Fødselsnummerfilter.fjern("kokk $nummer").tekst).isEqualTo("kokk")
        }
    }

    @Test
    fun `fjerner fødselsnummer med mellomrom eller punktum etter seks sifre`() {
        val medMellomrom = "${fødselsnummer.take(6)} ${fødselsnummer.drop(6)}"
        val medPunktum = "${fødselsnummer.take(6)}.${fødselsnummer.drop(6)}"
        assertThat(Fødselsnummerfilter.fjern("Treff $medMellomrom Oslo").tekst).isEqualTo("Treff Oslo")
        assertThat(Fødselsnummerfilter.fjern("Treff $medPunktum Oslo").tekst).isEqualTo("Treff Oslo")
    }

    @Test
    fun `fjerner fødselsnummer fra et navn på søk og beholder resten`() {
        val resultat = Fødselsnummerfilter.fjern("Treff for $fødselsnummer")
        assertThat(resultat.tekst).isEqualTo("Treff for")
        assertThat(resultat.fjernet).isTrue()
    }

    @Test
    fun `fjerner flere fødselsnumre i samme tekst`() {
        assertThat(Fødselsnummerfilter.fjern("$fødselsnummer og $dNummer").tekst).isEqualTo("og")
    }

    @Test
    fun `beholder 11 sifre med feil kontrollsiffer`() {
        val feilKontrollsiffer = fødselsnummer.dropLast(1) + ((fødselsnummer.last() - '0' + 1) % 10)
        val resultat = Fødselsnummerfilter.fjern(feilKontrollsiffer)
        assertThat(resultat.tekst).isEqualTo(feilKontrollsiffer)
        assertThat(resultat.fjernet).isFalse()
    }

    @Test
    fun `beholder 11 sifre med ugyldig dato selv om kontrollsifrene stemmer`() {
        val ugyldigDato = FødselsnummerTestdata.lagNummer(dag = 32, måned = 1, år = 1990, individer = 0..499)
        assertThat(Fødselsnummerfilter.fjern(ugyldigDato).fjernet).isFalse()
    }

    @Test
    fun `beholder organisasjonsnummer, årstall og vanlige ord`() {
        val tekst = "Bedrift 123456789 i 2026 kokk"
        val resultat = Fødselsnummerfilter.fjern(tekst)
        assertThat(resultat.tekst).isEqualTo(tekst)
        assertThat(resultat.fjernet).isFalse()
    }

    @Test
    fun `beholder fødselsnummer som er del av et lengre tall`() {
        assertThat(Fødselsnummerfilter.fjern("${fødselsnummer}0").fjernet).isFalse()
        assertThat(Fødselsnummerfilter.fjern("0$fødselsnummer").fjernet).isFalse()
    }
}
