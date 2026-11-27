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
            "00000000001",
            "00000000002",
            "00000000034",
            "00000000056",
            "00000000078",
            "00000000090",
            "000000 00001",
            "00 00 00 000 01",
            "000000\u00A000001",
            "000000\u202F00001",
            "000000  00001",
            "000000 \u00A0 00001",
            "００００００００００１",
            "０００００００００２３",
            "０００００００００４５",
            "０００００００００６７",
            "０００００００００８９",
            "Treff 00000000001 Oslo",
            "kokk00000000001",
        ).forEach { assertThat(Fødselsnummersjekk.inneholderFødselsnummer(it)).describedAs(it).isTrue() }
    }

    @Test
    fun `godtar tekst uten 11 sifre`() {
        listOf(
            "",
            "kokk",
            "0000000001",
            "000000000012",
            "Bedrift 123456789 i 2026",
            "Treff 2025-03-12",
            "Ring 12345678",
            "Treff 12 uke 3",
            "000000.00001",
            "000000, 00001",
            "000000\t00001",
            "000000-00001",
            "000000/00001",
        ).forEach { assertThat(Fødselsnummersjekk.inneholderFødselsnummer(it)).describedAs(it).isFalse() }
    }
}
