package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.TimeZone

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NyheterControllerTest : TestRunningApplication() {

    companion object {
        private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
    }

    val testIdent = "T1234"

    @Test
    fun `Skal gi 401 uten access token`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter"))
            .GET().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        println(response.body())
        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Skal gi 200 med gyldig access token`() {
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe, arbeidsgiverrettetGruppe)))
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .GET().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
    }
}