package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.toi.rekrutteringsbistand.bruker.api.nyheter.TestRunningApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.TimeZone
import java.util.UUID
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilbakemeldingerControllerTest : TestRunningApplication() {

    companion object {
        private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
    }

    lateinit var tilbakemeldingerRepository: TilbakemeldingerRepository
    lateinit var dataSource: DataSource

    val testIdent = "T1234"

    @BeforeAll
    fun setup() {
        dataSource = appCtx.dataSource
        tilbakemeldingerRepository = TilbakemeldingerRepository(dataSource)
    }

    @AfterEach
    fun cleanup() {
        dataSource.connection.use {
            it.prepareStatement("DELETE FROM tilbakemeldinger").executeUpdate()
        }
    }

    private fun lagTestTilbakemelding(): Tilbakemelding =
        tilbakemeldingerRepository.opprett(
            Tilbakemelding(
                navn = "Test Testesen",
                tilbakemelding = "En test-tilbakemelding",
                kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
                url = "/stillinger",
            )
        )

    @Test
    fun `Henting av tilbakemeldinger skal gi statuskode 401 uten access token`() {
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger"))
            .GET().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Henting av tilbakemeldinger skal gi 403 uten utvikler-rolle`() {
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(arbeidsgiverrettetGruppe)))
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .GET().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Henting av tilbakemeldinger skal gi 200 med utvikler-rolle`() {
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .GET().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `Opprettelse av tilbakemelding skal gi 201 med hvilken som helst autentisert rolle`() {
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]
        val opprettRequest = TilbakemeldingOpprettRequest(
            navn = "Test Testesen",
            tilbakemelding = "Dette fungerer ikke",
            kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
            url = "/stillinger",
        )
        val json = objectMapper.writeValueAsString(opprettRequest)

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(arbeidsgiverrettetGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(201)
    }

    @Test
    fun `Opprettelse av tilbakemelding skal gi 401 uten access token`() {
        val opprettRequest = TilbakemeldingOpprettRequest(
            navn = "Test",
            tilbakemelding = "Test",
            kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF,
            url = "/test",
        )
        val json = objectMapper.writeValueAsString(opprettRequest)

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger"))
            .POST(HttpRequest.BodyPublishers.ofString(json)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Oppdatering av tilbakemelding skal gi 403 uten utvikler-rolle`() {
        val lagret = lagTestTilbakemelding()
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]
        val oppdaterRequest = TilbakemeldingOppdaterRequest(
            kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
            trelloLenke = "https://trello.com/c/abc",
            status = TilbakemeldingStatus.VURDERING,
        )
        val json = objectMapper.writeValueAsString(oppdaterRequest)

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(arbeidsgiverrettetGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger/${lagret.id}"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .PUT(HttpRequest.BodyPublishers.ofString(json)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Oppdatering av tilbakemelding skal gi 200 med utvikler-rolle`() {
        val lagret = lagTestTilbakemelding()
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]
        val oppdaterRequest = TilbakemeldingOppdaterRequest(
            kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
            trelloLenke = "https://trello.com/c/abc",
            status = TilbakemeldingStatus.VURDERING,
        )
        val json = objectMapper.writeValueAsString(oppdaterRequest)

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger/${lagret.id}"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .PUT(HttpRequest.BodyPublishers.ofString(json)).build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `Sletting av tilbakemelding skal gi 403 uten utvikler-rolle`() {
        val lagret = lagTestTilbakemelding()
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(arbeidsgiverrettetGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger/${lagret.id}"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .DELETE().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Sletting av tilbakemelding skal gi 200 med utvikler-rolle`() {
        val lagret = lagTestTilbakemelding()
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger/${lagret.id}"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .DELETE().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
    }
}
