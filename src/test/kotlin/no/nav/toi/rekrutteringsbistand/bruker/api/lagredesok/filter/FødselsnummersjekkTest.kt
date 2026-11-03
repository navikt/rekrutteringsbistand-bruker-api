package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Testene bruker åpenbart falske tall. Sjekken ser bare på antall sifre.
 * Frontend har samme regel i `components/lagrede-søk/fødselsnummer.ts`. Endres tilfellene her, må regelen der følge med.
 */
class FødselsnummersjekkTest {

    @Test
    fun `avviser tekst med 11 sifre`() {
        listOf(
            "12345678901",
            "99999999999",
            "123456 78901",
            "123456.78901",
            "12 34 56 789 01",
            "123456\u00A078901",
            "123456\t78901",
            "123456-78901",
            "123456/78901",
            "１２３４５６７８９０１",
            "Treff 12345678901 Oslo",
            "kokk12345678901",
        ).forEach { assertThat(Fødselsnummersjekk.inneholderFødselsnummer(it)).describedAs(it).isTrue() }
    }

    @Test
    fun `godtar tekst uten 11 sifre`() {
        listOf(
            "",
            "kokk",
            "1234567890",
            "123456789012",
            "Bedrift 123456789 i 2026",
            "Treff 2025-03-12",
            "Ring 12345678",
            "Treff 12 uke 3",
        ).forEach { assertThat(Fødselsnummersjekk.inneholderFødselsnummer(it)).describedAs(it).isFalse() }
    }
}
