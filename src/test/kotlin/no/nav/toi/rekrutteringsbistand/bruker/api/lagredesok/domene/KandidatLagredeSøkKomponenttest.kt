package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkKomponenttest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.http.HttpRequest

class KandidatLagredeSøkKomponenttest : LagredeSøkKomponenttest(LagredeSøkDomene.KANDIDATSOK) {

    override val gyldigFilter = "innsatsgruppe=STANDARD_INNSATS"
    override val annetGyldigFilter = "kompetanse=Truckf%C3%B8rerbevis"

    @Test
    fun `avviser 11 sifre i en fri verdi uten å gjenta verdien i svaret`() {
        listOf(
            "kompetanse=$syntetiskNummer",
            "arbeidserfaring=123456+78901",
            "kompetanse=123456%2C78901",
        ).forEach { filter ->
            val respons = send(opprettForespørsel(nyIdent(), "Kokk i Bergen", filter))
            assertThat(respons.statusCode()).`as`(filter).isEqualTo(400)
            assertThat(respons.body()).`as`(filter).doesNotContain("123456").doesNotContain("78901")
        }
    }

    @Test
    fun `lagrer fritekst og frie verdier og gir dem tilbake uendret`() {
        val ident = nyIdent()
        val filter = "kompetanse=Salg%252C+butikk%2CKokk&sted=Bergen+%28Kommune%29"
        opprett(ident, "Kokk i Bergen", filter, listOf("servitør"))

        val lagret = hent(ident).single()
        assertThat(lagret.filter).isEqualTo(filter)
        assertThat(lagret.fritekst).containsExactly("servitør")
    }

    @Test
    fun `avviser orgenhet og andre parametere som ikke lagres`() {
        listOf("orgenhet=0301", "fritekst=kokk", "side=2", "sortering=nyeste").forEach { filter ->
            val respons = send(
                forespørsel(endepunkt, nyIdent())
                    .POST(HttpRequest.BodyPublishers.ofString(body("Kokk", filter)))
                    .build()
            )
            assertThat(respons.statusCode()).`as`(filter).isEqualTo(400)
        }
    }
}
