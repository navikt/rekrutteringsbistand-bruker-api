package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.StandardsøkKomponenttest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RekrutteringstreffStandardsøkTest : StandardsøkKomponenttest(RekrutteringstreffDomene) {

    override val filtre = "statuser=PUBLISERT" to "sortering=eldste"

    @Test
    fun `gir 403 uten rolle for rekrutteringstreff`() {
        val request = forespørsel(endepunkt, nyIdent(), "REKRUTTERINGSBISTAND_MODIA_GENERELL_TILGANG").GET().build()
        assertThat(send(request).statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet veileder har tilgang`() {
        val request = forespørsel(endepunkt, nyIdent(), "REKRUTTERINGSBISTAND_JOBBSOKERRETTET").GET().build()
        assertThat(send(request).statusCode()).isEqualTo(200)
    }

    @Test
    fun `å velge vanlig søk krever rolle for rekrutteringstreff`() {
        val utenRolle =
            forespørsel("$endepunkt/standard", nyIdent(), "REKRUTTERINGSBISTAND_MODIA_GENERELL_TILGANG").DELETE().build()
        assertThat(send(utenRolle).statusCode()).isEqualTo(403)
    }

    @Test
    fun `å velge vanlig søk i rekrutteringstreff endrer ikke standardsøket i stilling`() {
        val navIdent = nyIdent()
        val stillingssøk = settInn(navIdent, "Stilling", idag, erStandard = true, domene = StillingDomene)

        assertThat(send(velgVanligSøkForespørsel(navIdent)).statusCode()).isEqualTo(204)
        assertThat(radIDatabasen(stillingssøk, StillingDomene)!!.erStandard).isTrue()
    }
}
