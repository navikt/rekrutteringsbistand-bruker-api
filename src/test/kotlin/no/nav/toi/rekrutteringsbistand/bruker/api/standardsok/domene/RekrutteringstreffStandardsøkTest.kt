package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.StandardsøkKomponenttest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpRequest

class RekrutteringstreffStandardsøkTest : StandardsøkKomponenttest(RekrutteringstreffDomene) {

    override val filtre = "statuser=PUBLISERT" to "sortering=eldste"

    @Test
    fun `gir 403 uten rolle for rekrutteringstreff`() {
        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${token(nyIdent(), "REKRUTTERINGSBISTAND_MODIA_GENERELL_TILGANG")}")
            .GET().build()
        assertThat(send(request).statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet veileder har tilgang`() {
        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${token(nyIdent(), "REKRUTTERINGSBISTAND_JOBBSOKERRETTET")}")
            .GET().build()
        assertThat(send(request).statusCode()).isEqualTo(200)
    }
}
