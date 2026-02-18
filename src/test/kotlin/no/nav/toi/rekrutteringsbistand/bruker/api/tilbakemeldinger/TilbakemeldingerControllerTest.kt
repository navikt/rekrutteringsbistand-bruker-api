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

    @Test
    fun `Henting av tilbakemeldinger skal returnere riktig responsstruktur`() {
        lagTestTilbakemelding()
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]

        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))

        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger?side=1"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .GET().build()
        val response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString())

        val body = objectMapper.readValue(response.body(), TilbakemeldingerPageResponse::class.java)
        assertThat(body.tilbakemeldinger).hasSize(1)
        assertThat(body.side).isEqualTo(1)
        assertThat(body.totalSider).isEqualTo(1)
        assertThat(body.totaltAntall).isEqualTo(1)

        val tilbakemelding = body.tilbakemeldinger.first()
        assertThat(tilbakemelding.navn).isEqualTo("Test Testesen")
        assertThat(tilbakemelding.tilbakemelding).isEqualTo("En test-tilbakemelding")
        assertThat(tilbakemelding.kategori).isEqualTo(TilbakemeldingKategori.REKRUTTERINGSTREFF)
        assertThat(tilbakemelding.status).isEqualTo(TilbakemeldingStatus.NY)
        assertThat(tilbakemelding.id).isNotNull()
        assertThat(tilbakemelding.dato).isNotNull()
    }

    @Test
    fun `Opprettelse av tilbakemelding skal returnere opprettet tilbakemelding i body`() {
        val arbeidsgiverrettetGruppe = appCtx.env["REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"]
        val opprettRequest = TilbakemeldingOpprettRequest(
            navn = "Ny Bruker",
            tilbakemelding = "Noe å melde",
            kategori = TilbakemeldingKategori.STILLINGSOPPDRAG,
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
        val body = objectMapper.readValue(response.body(), TilbakemeldingResponse::class.java)
        assertThat(body.navn).isEqualTo("Ny Bruker")
        assertThat(body.tilbakemelding).isEqualTo("Noe å melde")
        assertThat(body.kategori).isEqualTo(TilbakemeldingKategori.STILLINGSOPPDRAG)
        assertThat(body.status).isEqualTo(TilbakemeldingStatus.NY)
        assertThat(body.trelloLenke).isNull()
    }

    @Test
    fun `Oppdatering av tilbakemelding skal returnere oppdatert tilbakemelding i body`() {
        val lagret = lagTestTilbakemelding()
        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]
        val oppdaterRequest = TilbakemeldingOppdaterRequest(
            kategori = TilbakemeldingKategori.ETTERREGISTRERINGER,
            trelloLenke = "https://trello.com/c/xyz",
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
        val body = objectMapper.readValue(response.body(), TilbakemeldingResponse::class.java)
        assertThat(body.id).isEqualTo(lagret.id)
        assertThat(body.kategori).isEqualTo(TilbakemeldingKategori.ETTERREGISTRERINGER)
        assertThat(body.trelloLenke).isEqualTo("https://trello.com/c/xyz")
        assertThat(body.status).isEqualTo(TilbakemeldingStatus.VURDERING)
        assertThat(body.tilbakemelding).isEqualTo("En test-tilbakemelding")
    }

    @Test
    fun `Henting med visAlle=true skal inkludere avviste og fullforte`() {
        val lagret = lagTestTilbakemelding()
        tilbakemeldingerRepository.oppdater(lagret.id, TilbakemeldingOppdaterRequest(
            lagret.kategori, null, TilbakemeldingStatus.AVVIST
        ))
        lagTestTilbakemelding()

        val utviklerGruppe = appCtx.env["REKRUTTERINGSBISTAND_UTVIKLER"]
        val accessToken = appCtx.mockOauth2Server.issueToken("azuread",
            "testsubject", appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to testIdent, "groups" to listOfNotNull(utviklerGruppe)))

        val utenVisAlle = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger?side=1"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .GET().build()
        val responsUten = HttpClient.newBuilder().build().send(utenVisAlle, HttpResponse.BodyHandlers.ofString())
        val bodyUten = objectMapper.readValue(responsUten.body(), TilbakemeldingerPageResponse::class.java)
        assertThat(bodyUten.totaltAntall).isEqualTo(1)

        val medVisAlle = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/tilbakemeldinger?side=1&visAlle=true"))
            .header("Authorization", "Bearer ${accessToken.serialize()}")
            .GET().build()
        val responsMed = HttpClient.newBuilder().build().send(medVisAlle, HttpResponse.BodyHandlers.ofString())
        val bodyMed = objectMapper.readValue(responsMed.body(), TilbakemeldingerPageResponse::class.java)
        assertThat(bodyMed.totaltAntall).isEqualTo(2)
        assertThat(bodyMed.tilbakemeldinger).hasSize(2)
    }
}
