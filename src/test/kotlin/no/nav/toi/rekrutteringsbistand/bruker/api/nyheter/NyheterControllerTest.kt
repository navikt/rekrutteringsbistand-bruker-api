package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.TimeZone
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NyheterControllerTest : TestRunningApplication() {

    companion object {
        private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
    }

    lateinit var nyheterRepository: NyheterRepository

    lateinit var dataSource : DataSource

    lateinit var lagretNyhet: Nyhet

    val testIdent = "T1234"

    @BeforeAll
    fun setup() {
        dataSource = appCtx.dataSource
        nyheterRepository = NyheterRepository(dataSource)

        val enNyhet = Nyhet(
            tittel = "Ny nyhet",
            innhold = "Dette er en ny nyhet som informerer om ting",
            opprettetAv = testIdent,
            sistEndretAv = testIdent,
            status = Status.AKTIV
        )
        lagretNyhet = nyheterRepository.lagreNyhet(enNyhet)
    }

    @Test
    fun `Henting av nyheter skal gi statuskode 401 uten access token`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter"))
            .GET().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        println(response.body())
        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Henting av nyheter skal gi statuskode 200 med gyldig access token`() {
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

    @Test
    fun `Beskyttet tilgangsrolle uten utvikler-tilgang skal ikke kunne opprette nyhet og gir statuskode 403`() {
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]
        val uuid = null
        val testNyhet = NyhetDtoRequest(
            tittel = "Ny nyhet",
            innhold = "Nytt innhold",
        )
        val testNyhetJson = objectMapper.writeValueAsString(testNyhet)

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(arbeidsgiverrettetGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter/$uuid"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .POST(HttpRequest.BodyPublishers.ofString(testNyhetJson)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Beskyttet tilgangsrolle med utvikler-tilgang skal kunne opprette nyhet og gir statuskode 201`() {
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]
        val uuid = null
        val testNyhet = NyhetDtoRequest(
            tittel = "Ny nyhet",
            innhold = "Nytt innhold",
        )
        val testNyhetJson = objectMapper.writeValueAsString(testNyhet)

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter/$uuid"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .POST(HttpRequest.BodyPublishers.ofString(testNyhetJson)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(201)
    }

    @Test
    fun `Beskyttet tilgangsrolle uten utvikler-tilgang skal ikke kunne oppdatere nyhet og gir statuskode 403`() {
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]
        val uuid = lagretNyhet.nyhetId
        val testNyhet = NyhetDtoRequest(
            tittel = "Oppdatert nyhet",
            innhold = "Nytt innhold",
        )
        val testNyhetJson = objectMapper.writeValueAsString(testNyhet)

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(arbeidsgiverrettetGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter/$uuid"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .PUT(HttpRequest.BodyPublishers.ofString(testNyhetJson)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Beskyttet tilgangsrolle med utvikler-tilgang skal kunne oppdatere nyhet og gir statuskode 200`() {
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]
        val uuid = lagretNyhet.nyhetId
        val testNyhet = NyhetDtoRequest(
            tittel = "Oppdatert nyhet",
            innhold = "Nytt innhold",
        )
        val testNyhetJson = objectMapper.writeValueAsString(testNyhet)

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter/$uuid"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .PUT(HttpRequest.BodyPublishers.ofString(testNyhetJson)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `Beskyttet tilgangsrolle uten utvikler-tilgang skal ikke kunne slette nyhet og gir statuskode 403`() {
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]
        val uuid = lagretNyhet.nyhetId

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(arbeidsgiverrettetGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter/$uuid/slett"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .PUT(HttpRequest.BodyPublishers.noBody()).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Beskyttet tilgangsrolle med utvikler-tilgang skal kunne slette nyhet og gir statuskode 200`() {
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]
        val uuid = lagretNyhet.nyhetId

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/nyheter/$uuid/slett"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .PUT(HttpRequest.BodyPublishers.noBody()).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
    }
}