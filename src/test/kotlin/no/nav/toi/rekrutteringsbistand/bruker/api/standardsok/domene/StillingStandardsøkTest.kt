package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.StandardsøkKomponenttest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StillingStandardsøkTest : StandardsøkKomponenttest(StillingDomene) {

    override val filtre = "statuser=%C3%85pen%20for%20s%C3%B8kere" to "sortering=utl%C3%B8psdato"

    @Test
    fun `alle roller i bruker-api har tilgang`() {
        listOf(
            "REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET",
            "REKRUTTERINGSBISTAND_JOBBSOKERRETTET",
            "REKRUTTERINGSBISTAND_UTVIKLER",
            "REKRUTTERINGSBISTAND_MODIA_OPPFOLGING",
            "REKRUTTERINGSBISTAND_MODIA_GENERELL_TILGANG",
        ).forEach { gruppe ->
            val request = forespørsel(endepunkt, nyIdent(), gruppe).GET().build()
            assertThat(send(request).statusCode()).`as`(gruppe).isEqualTo(200)
        }
    }

    @Test
    fun `søk i ett domene er ikke synlige i et annet`() {
        val navIdent = nyIdent()
        opprett(navIdent, "Stilling")

        val treff = send(forespørsel("$lokalUrlBase/api/standardsok/rekrutteringstreff", navIdent).GET().build())
        assertThat(treff.body()).isEqualTo("[]")
    }
}
