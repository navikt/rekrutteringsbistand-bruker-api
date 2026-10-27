package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.utlop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UtløpsreglerTest {

    @Test
    fun `søket slettes seks kalendermåneder etter siste aktivitet`() {
        assertThat(Utløpsregler.slettes(LocalDate.of(2026, 10, 20))).isEqualTo(LocalDate.of(2027, 4, 20))
    }

    @Test
    fun `ved månedsslutt brukes siste dag i målmåneden`() {
        val sistAktiv = LocalDate.of(2026, 8, 31)
        assertThat(Utløpsregler.slettes(sistAktiv)).isEqualTo(LocalDate.of(2027, 2, 28))
        assertThat(Utløpsregler.erUtløpt(sistAktiv, LocalDate.of(2027, 2, 27))).isFalse()
        assertThat(Utløpsregler.erUtløpt(sistAktiv, LocalDate.of(2027, 2, 28))).isTrue()
    }

    @Test
    fun `skuddår gir 29 februar`() {
        assertThat(Utløpsregler.slettes(LocalDate.of(2027, 8, 31))).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `varsel vises fra fem måneder til søket er utløpt`() {
        val sistAktiv = LocalDate.of(2026, 1, 15)
        assertThat(Utløpsregler.visSlettevarsel(sistAktiv, LocalDate.of(2026, 6, 14))).isFalse()
        assertThat(Utløpsregler.visSlettevarsel(sistAktiv, LocalDate.of(2026, 6, 15))).isTrue()
        assertThat(Utløpsregler.visSlettevarsel(sistAktiv, LocalDate.of(2026, 7, 14))).isTrue()
        assertThat(Utløpsregler.visSlettevarsel(sistAktiv, LocalDate.of(2026, 7, 15))).isFalse()
    }

    @Test
    fun `grensen for databasen gir samme svar som erUtløpt for alle datoer`() {
        var idag = LocalDate.of(2026, 1, 1)
        while (idag.isBefore(LocalDate.of(2029, 1, 1))) {
            val grense = Utløpsregler.sisteUtløpteSistAktiv(idag)
            for (dagerSiden in 0L..250L) {
                val sistAktiv = idag.minusDays(dagerSiden)
                assertThat(!sistAktiv.isAfter(grense))
                    .describedAs("idag=$idag sistAktiv=$sistAktiv")
                    .isEqualTo(Utløpsregler.erUtløpt(sistAktiv, idag))
            }
            idag = idag.plusDays(1)
        }
    }
}
