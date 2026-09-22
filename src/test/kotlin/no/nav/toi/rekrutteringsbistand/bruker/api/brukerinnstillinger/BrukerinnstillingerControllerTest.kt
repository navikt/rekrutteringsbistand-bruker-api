package no.nav.toi.rekrutteringsbistand.bruker.api.brukerinnstillinger

import no.nav.toi.rekrutteringsbistand.bruker.api.nyheter.TestRunningApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class BrukerinnstillingerControllerTest : TestRunningApplication() {
    private val testIdent = "T1234"

    @Test
    fun `skal hente og lagre antall leste nyheter for innlogget bruker`() {
        val token = appCtx.mockOauth2Server.issueToken(
            "azuread",
            "testsubject",
            appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf(
                "NAVident" to testIdent,
                "groups" to listOf(appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]),
            ),
        )
        val client = HttpClient.newHttpClient()
        val getRequest = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/innstillinger"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .GET()
            .build()

        val førLagring = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        assertThat(førLagring.statusCode()).isEqualTo(200)
        assertThat(førLagring.body()).contains("\"antallLesteNyheter\":0")

        val putRequest = HttpRequest.newBuilder()
                        .uri(URI("$lokalUrlBase/api/innstillinger"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("""
                                {
                                    "antallLesteNyheter": 3,
                                    "darkMode": true,
                                    "windowMode": true,
                                    "tekststørrelse": "stor"
                                }
                        """.trimIndent()))
            .build()

        val lagring = client.send(putRequest, HttpResponse.BodyHandlers.ofString())
        assertThat(lagring.statusCode()).isEqualTo(200)

        val etterLagring = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        assertThat(etterLagring.statusCode()).isEqualTo(200)
        assertThat(etterLagring.body()).contains("\"antallLesteNyheter\":3")
        assertThat(etterLagring.body()).contains("\"darkMode\":true")
        assertThat(etterLagring.body()).contains("\"windowMode\":true")
        assertThat(etterLagring.body()).contains("\"tekststørrelse\":\"stor\"")
    }
}