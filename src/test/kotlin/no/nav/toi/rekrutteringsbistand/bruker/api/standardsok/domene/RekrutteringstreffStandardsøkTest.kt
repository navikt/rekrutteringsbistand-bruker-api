package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.StandardsøkKomponenttest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpRequest
import java.util.UUID

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

    @Test
    fun `å velge vanlig søk krever rolle for rekrutteringstreff`() {
        val utenRolle = HttpRequest.newBuilder()
            .uri(URI("$endepunkt/standard"))
            .header("Authorization", "Bearer " + token(nyIdent(), "REKRUTTERINGSBISTAND_MODIA_GENERELL_TILGANG"))
            .DELETE().build()
        assertThat(send(utenRolle).statusCode()).isEqualTo(403)
    }

    @Test
    fun `å velge vanlig søk i rekrutteringstreff endrer ikke standardsøket i stilling`() {
        val navIdent = nyIdent()
        val stillingssøk = UUID.randomUUID()
        val tabell = StillingDomene.tabell
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement(
                "INSERT INTO $tabell (id, navIdent, navn, erStandard, filter, fritekst, sistAktiv) VALUES (?, ?, 'Stilling', TRUE, '', '{}', ?)"
            ).use { ps ->
                ps.setObject(1, stillingssøk)
                ps.setString(2, navIdent)
                ps.setObject(3, idag)
                ps.executeUpdate()
            }
        }
        try {
            assertThat(send(velgVanligSøkForespørsel(navIdent)).statusCode()).isEqualTo(204)

            val erStandard = appCtx.dataSource.connection.use { c ->
                c.prepareStatement("SELECT erStandard FROM $tabell WHERE id = ?").use { ps ->
                    ps.setObject(1, stillingssøk)
                    ps.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
                }
            }
            assertThat(erStandard).isTrue()
        } finally {
            appCtx.dataSource.connection.use { c ->
                c.prepareStatement("DELETE FROM $tabell WHERE navIdent = ?").use { ps ->
                    ps.setString(1, navIdent)
                    ps.executeUpdate()
                }
            }
        }
    }
}
