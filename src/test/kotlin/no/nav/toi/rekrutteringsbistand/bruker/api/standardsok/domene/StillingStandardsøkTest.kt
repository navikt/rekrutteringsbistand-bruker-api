package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.StandardsøkKomponenttest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpRequest

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
            val request = HttpRequest.newBuilder()
                .uri(URI(endepunkt))
                .header("Authorization", "Bearer ${token(nyIdent(), gruppe)}")
                .GET().build()
            assertThat(send(request).statusCode()).`as`(gruppe).isEqualTo(200)
        }
    }

    @Test
    fun `søk i ett domene er ikke synlige i et annet`() {
        val navIdent = nyIdent()
        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${token(navIdent)}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"navn":"Stilling","filter":"","fritekst":[]}"""))
            .build()
        assertThat(send(request).statusCode()).isEqualTo(201)

        val treff = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/standardsok/rekrutteringstreff"))
            .header("Authorization", "Bearer ${token(navIdent)}")
            .GET().build()
        assertThat(send(treff).body()).isEqualTo("[]")
    }
}
