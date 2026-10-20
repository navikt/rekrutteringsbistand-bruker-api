package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import no.nav.toi.rekrutteringsbistand.bruker.api.JacksonConfig
import no.nav.toi.rekrutteringsbistand.bruker.api.nyheter.TestRunningApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture

class StandardsøkControllerTest : TestRunningApplication() {

    private val client = HttpClient.newHttpClient()
    private val objectMapper = JacksonConfig.objectMapper
    private val endepunkt = "$lokalUrlBase/api/standardsok/rekrutteringstreff"
    private val idag = LocalDate.now(ZoneId.of("Europe/Oslo"))

    private fun nyIdent() = "TEST-${UUID.randomUUID()}"

    private fun token(navIdent: String, gruppe: String = "REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"): String =
        appCtx.mockOauth2Server.issueToken(
            "azuread",
            "testsubject",
            appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to navIdent, "groups" to listOf(appCtx.env[gruppe])),
        ).serialize()

    private fun forespørsel(url: String, navIdent: String?) = HttpRequest.newBuilder()
        .uri(URI(url))
        .header("Content-Type", "application/json")
        .apply { if (navIdent != null) header("Authorization", "Bearer ${token(navIdent)}") }

    private fun send(request: HttpRequest): HttpResponse<String> =
        client.send(request, HttpResponse.BodyHandlers.ofString())

    private fun hent(navIdent: String): List<StandardsøkDto> {
        val respons = send(forespørsel(endepunkt, navIdent).GET().build())
        assertThat(respons.statusCode()).isEqualTo(200)
        return objectMapper.readValue(respons.body(), Array<StandardsøkDto>::class.java).toList()
    }

    private fun body(navn: String, filter: String = "", fritekst: List<String> = emptyList()) =
        objectMapper.writeValueAsString(LagreStandardsøkDto(navn, filter, fritekst))

    private fun opprettForespørsel(navIdent: String, navn: String, filter: String = "", fritekst: List<String> = emptyList()) =
        forespørsel(endepunkt, navIdent).POST(HttpRequest.BodyPublishers.ofString(body(navn, filter, fritekst))).build()

    private fun opprett(navIdent: String, navn: String, filter: String = "", fritekst: List<String> = emptyList()): StandardsøkDto {
        val respons = send(opprettForespørsel(navIdent, navn, filter, fritekst))
        assertThat(respons.statusCode()).isEqualTo(201)
        return objectMapper.readValue(respons.body(), StandardsøkDto::class.java)
    }

    private fun erstatt(navIdent: String, id: UUID, navn: String, filter: String = "", fritekst: List<String> = emptyList()) =
        send(forespørsel("$endepunkt/$id", navIdent).PUT(HttpRequest.BodyPublishers.ofString(body(navn, filter, fritekst))).build())

    private fun settStandardForespørsel(navIdent: String, id: UUID) =
        forespørsel("$endepunkt/$id/standard", navIdent).PUT(HttpRequest.BodyPublishers.noBody()).build()

    private fun bruk(navIdent: String, id: UUID) =
        send(forespørsel("$endepunkt/$id/bruk", navIdent).POST(HttpRequest.BodyPublishers.noBody()).build())

    private fun slett(navIdent: String, id: UUID) =
        send(forespørsel("$endepunkt/$id", navIdent).DELETE().build())

    private fun settInn(navIdent: String, navn: String, sistAktiv: LocalDate, erStandard: Boolean = false): UUID {
        val id = UUID.randomUUID()
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO standardsok_rekrutteringstreff (id, navIdent, navn, erStandard, filter, fritekst, sistAktiv)
                VALUES (?, ?, ?, ?, '', '{}', ?)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, id)
                ps.setString(2, navIdent)
                ps.setString(3, navn)
                ps.setBoolean(4, erStandard)
                ps.setObject(5, sistAktiv)
                ps.executeUpdate()
            }
        }
        return id
    }

    private fun radIDatabasen(id: UUID): Pair<Boolean, LocalDate>? =
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement("SELECT erStandard, sistAktiv FROM standardsok_rekrutteringstreff WHERE id = ?").use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getBoolean(1) to rs.getObject(2, LocalDate::class.java) else null
                }
            }
        }

    private fun antallIDatabasen(navIdent: String, kunStandard: Boolean = false): Int =
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement(
                "SELECT count(*) FROM standardsok_rekrutteringstreff WHERE navIdent = ?" +
                    if (kunStandard) " AND erStandard" else ""
            ).use { ps ->
                ps.setString(1, navIdent)
                ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
        }

    private val utløptDato get() = Utløpsregler.sisteUtløpteSistAktiv(idag)

    @AfterEach
    fun ryddOpp() {
        appCtx.dataSource.connection.use { it.prepareStatement("DELETE FROM standardsok_rekrutteringstreff").executeUpdate() }
    }

    @Test
    fun `gir 401 uten token`() {
        assertThat(send(forespørsel(endepunkt, null).GET().build()).statusCode()).isEqualTo(401)
    }

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
    fun `ukjent domene gir 404`() {
        val respons = send(forespørsel("$lokalUrlBase/api/standardsok/ukjent", nyIdent()).GET().build())
        assertThat(respons.statusCode()).isEqualTo(404)
    }

    @Test
    fun `tom liste uten lagrede søk`() {
        assertThat(hent(nyIdent())).isEmpty()
    }

    @Test
    fun `første søk blir standard, de neste blir ikke det`() {
        val navIdent = nyIdent()
        val første = opprett(navIdent, "Publiserte", "statuser=PUBLISERT", listOf("kokk"))
        val andre = opprett(navIdent, "Utkast", "statuser=UTKAST")

        assertThat(første.erStandard).isTrue()
        assertThat(andre.erStandard).isFalse()
        assertThat(første.sistAktiv).isEqualTo(idag)
        assertThat(første.slettes).isEqualTo(idag.plusMonths(6))
        assertThat(første.visSlettevarsel).isFalse()
        assertThat(første.fritekst).containsExactly("kokk")
        assertThat(første.fritekstFiltrert).isFalse()
        assertThat(hent(navIdent).map { it.navn }).containsExactly("Publiserte", "Utkast")
    }

    @Test
    fun `like navn er lov, og endring eller sletting av ett søk påvirker ikke det andre`() {
        val navIdent = nyIdent()
        val første = opprett(navIdent, "Mine treff", "fylker=03")
        val andre = opprett(navIdent, "Mine treff", "fylker=46")

        assertThat(erstatt(navIdent, første.id, "Mine treff", "fylker=50").statusCode()).isEqualTo(200)
        assertThat(slett(navIdent, første.id).statusCode()).isEqualTo(204)

        val gjenværende = hent(navIdent)
        assertThat(gjenværende).hasSize(1)
        assertThat(gjenværende.single().id).isEqualTo(andre.id)
        assertThat(gjenværende.single().filter).isEqualTo("fylker=46")
    }

    @Test
    fun `erstatting endrer innhold og fornyer fristen, men ikke standardvalget`() {
        val navIdent = nyIdent()
        val standard = settInn(navIdent, "Standard", idag.minusMonths(1), erStandard = true)
        val annet = settInn(navIdent, "Annet", idag.minusMonths(2))

        val respons = erstatt(navIdent, annet, "Nytt navn", "sortering=eldste", listOf("lager"))
        assertThat(respons.statusCode()).isEqualTo(200)
        val oppdatert = objectMapper.readValue(respons.body(), StandardsøkDto::class.java)

        assertThat(oppdatert.navn).isEqualTo("Nytt navn")
        assertThat(oppdatert.filter).isEqualTo("sortering=eldste")
        assertThat(oppdatert.fritekst).containsExactly("lager")
        assertThat(oppdatert.sistAktiv).isEqualTo(idag)
        assertThat(oppdatert.erStandard).isFalse()
        assertThat(radIDatabasen(standard)!!.first).isTrue()
    }

    @Test
    fun `bytte av standard fjerner det gamle merket og fornyer bare det valgte søket`() {
        val navIdent = nyIdent()
        val gammelDato = idag.minusMonths(2)
        val gammelStandard = settInn(navIdent, "Gammel", gammelDato, erStandard = true)
        val nyStandard = settInn(navIdent, "Ny", gammelDato)

        assertThat(send(settStandardForespørsel(navIdent, nyStandard)).statusCode()).isEqualTo(204)

        assertThat(radIDatabasen(gammelStandard)).isEqualTo(false to gammelDato)
        assertThat(radIDatabasen(nyStandard)).isEqualTo(true to idag)
    }

    @Test
    fun `sletting fjerner søket og gir 404 andre gang`() {
        val navIdent = nyIdent()
        val søk = opprett(navIdent, "Slettes")
        assertThat(slett(navIdent, søk.id).statusCode()).isEqualTo(204)
        assertThat(hent(navIdent)).isEmpty()
        assertThat(slett(navIdent, søk.id).statusCode()).isEqualTo(404)
    }

    @Test
    fun `sletting av standardsøket velger ikke et annet standardsøk`() {
        val navIdent = nyIdent()
        val standard = opprett(navIdent, "Standard")
        opprett(navIdent, "Annet")
        slett(navIdent, standard.id)
        assertThat(hent(navIdent).single().erStandard).isFalse()
    }

    @Test
    fun `en veileder kan ikke se eller endre en annen veileders søk`() {
        val eier = nyIdent()
        val annen = nyIdent()
        val søk = opprett(eier, "Mitt søk")

        assertThat(hent(annen)).isEmpty()
        assertThat(erstatt(annen, søk.id, "Overtatt").statusCode()).isEqualTo(404)
        assertThat(slett(annen, søk.id).statusCode()).isEqualTo(404)
        assertThat(send(settStandardForespørsel(annen, søk.id)).statusCode()).isEqualTo(404)
        assertThat(bruk(annen, søk.id).statusCode()).isEqualTo(404)
        assertThat(hent(eier).single().navn).isEqualTo("Mitt søk")
    }

    @Test
    fun `ugyldig søkeident gir 404`() {
        assertThat(bruk(nyIdent(), UUID.randomUUID()).statusCode()).isEqualTo(404)
        val respons = send(forespørsel("$endepunkt/ikke-en-uuid/bruk", nyIdent()).POST(HttpRequest.BodyPublishers.noBody()).build())
        assertThat(respons.statusCode()).isEqualTo(404)
    }

    @Test
    fun `det ellevte søket gir 409`() {
        val navIdent = nyIdent()
        repeat(MAKS_ANTALL_SØK) { opprett(navIdent, "Søk $it") }
        assertThat(send(opprettForespørsel(navIdent, "Ett for mye")).statusCode()).isEqualTo(409)
        assertThat(antallIDatabasen(navIdent)).isEqualTo(MAKS_ANTALL_SØK)
    }

    @Test
    fun `samtidige opprettelser gir aldri mer enn ti søk og bare ett standardsøk`() {
        val navIdent = nyIdent()
        val forespørsler = (1..20).map { opprettForespørsel(navIdent, "Samtidig $it") }
        val statuser = forespørsler
            .map { client.sendAsync(it, HttpResponse.BodyHandlers.ofString()) }
            .let { CompletableFuture.allOf(*it.toTypedArray()).join(); it.map { svar -> svar.get().statusCode() } }

        assertThat(statuser.count { it == 201 }).isEqualTo(MAKS_ANTALL_SØK)
        assertThat(statuser.count { it == 409 }).isEqualTo(20 - MAKS_ANTALL_SØK)
        assertThat(antallIDatabasen(navIdent)).isEqualTo(MAKS_ANTALL_SØK)
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isEqualTo(1)
    }

    @Test
    fun `samtidige standardvalg gir høyst ett standardsøk`() {
        val navIdent = nyIdent()
        val ider = (1..5).map { settInn(navIdent, "Søk $it", idag.minusDays(3)) }
        val svar = (1..20).map { client.sendAsync(settStandardForespørsel(navIdent, ider[it % ider.size]), HttpResponse.BodyHandlers.ofString()) }
        CompletableFuture.allOf(*svar.toTypedArray()).join()

        assertThat(svar.map { it.get().statusCode() }).containsOnly(204)
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isEqualTo(1)
    }

    @Test
    fun `navn på 25 tegn godtas, 26 tegn gir 400`() {
        val navIdent = nyIdent()
        val søk = opprett(navIdent, "a".repeat(25))
        assertThat(send(opprettForespørsel(navIdent, "a".repeat(26))).statusCode()).isEqualTo(400)
        assertThat(erstatt(navIdent, søk.id, "a".repeat(26)).statusCode()).isEqualTo(400)
        assertThat(send(opprettForespørsel(navIdent, "  ")).statusCode()).isEqualTo(400)
    }

    @Test
    fun `ugyldig input gir 400 uten innholdet i svaret`() {
        val navIdent = nyIdent()
        val hemmelig = "hemmeligverdi"

        val ukjentParameter = send(opprettForespørsel(navIdent, "Søk", "fritekst=$hemmelig"))
        assertThat(ukjentParameter.statusCode()).isEqualTo(400)
        assertThat(ukjentParameter.body()).doesNotContain(hemmelig)

        val forMangeSøkeord = send(opprettForespørsel(navIdent, "Søk", fritekst = (1..11).map { "$hemmelig$it" }))
        assertThat(forMangeSøkeord.statusCode()).isEqualTo(400)
        assertThat(forMangeSøkeord.body()).doesNotContain(hemmelig)

        val ugyldigJson = send(forespørsel(endepunkt, navIdent).POST(HttpRequest.BodyPublishers.ofString("{\"navn\": [\"$hemmelig\"")).build())
        assertThat(ugyldigJson.statusCode()).isEqualTo(400)
        assertThat(ugyldigJson.body()).doesNotContain(hemmelig)

        val feilType = send(forespørsel(endepunkt, navIdent).POST(HttpRequest.BodyPublishers.ofString("{\"fritekst\": {\"$hemmelig\": 1}}")).build())
        assertThat(feilType.statusCode()).isEqualTo(400)
        assertThat(feilType.body()).doesNotContain(hemmelig)
    }

    @Test
    fun `fødselsnummer fjernes fra navnet på søket og fra søkeordene`() {
        val navIdent = nyIdent()
        val nummer = syntetiskFødselsnummer()

        val søk = opprett(navIdent, "Treff $nummer", fritekst = listOf("kokk $nummer", nummer))

        assertThat(søk.navn).isEqualTo("Treff")
        assertThat(søk.fritekst).containsExactly("kokk")
        assertThat(søk.fritekstFiltrert).isTrue()
        val lagret = hent(navIdent).single()
        assertThat(lagret.navn).isEqualTo("Treff")
        assertThat(lagret.fritekst).containsExactly("kokk")
    }

    @Test
    fun `fødselsnummer fjernes fra navnet også ved erstatting`() {
        val navIdent = nyIdent()
        val søk = opprett(navIdent, "Før")
        val respons = erstatt(navIdent, søk.id, "Etter ${syntetiskFødselsnummer()}")
        val oppdatert = objectMapper.readValue(respons.body(), StandardsøkDto::class.java)
        assertThat(oppdatert.navn).isEqualTo("Etter")
        assertThat(oppdatert.fritekstFiltrert).isTrue()
    }

    @Test
    fun `navn som bare er et fødselsnummer gir 400`() {
        val navIdent = nyIdent()
        val respons = send(opprettForespørsel(navIdent, syntetiskFødselsnummer()))
        assertThat(respons.statusCode()).isEqualTo(400)
        assertThat(antallIDatabasen(navIdent)).isZero()
    }

    @Test
    fun `bruk fornyer fristen og returnerer søket, henting gjør det ikke`() {
        val navIdent = nyIdent()
        val gammelDato = idag.minusDays(10)
        val id = settInn(navIdent, "Gammelt", gammelDato)

        hent(navIdent)
        assertThat(radIDatabasen(id)!!.second).isEqualTo(gammelDato)

        val respons = bruk(navIdent, id)
        assertThat(respons.statusCode()).isEqualTo(200)
        val søk = objectMapper.readValue(respons.body(), StandardsøkDto::class.java)
        assertThat(søk.sistAktiv).isEqualTo(idag)
        assertThat(radIDatabasen(id)!!.second).isEqualTo(idag)
    }

    @Test
    fun `søk etter fem måneder har slettevarsel`() {
        val navIdent = nyIdent()
        settInn(navIdent, "Snart borte", idag.minusMonths(5))
        val søk = hent(navIdent).single()
        assertThat(søk.visSlettevarsel).isTrue()
        assertThat(søk.slettes).isEqualTo(idag.minusMonths(5).plusMonths(6))
    }

    @Test
    fun `utløpte søk skjules, gir 404 og kan ikke vekkes til live`() {
        val navIdent = nyIdent()
        val utløpt = settInn(navIdent, "Utløpt", utløptDato, erStandard = true)

        assertThat(hent(navIdent)).isEmpty()
        assertThat(bruk(navIdent, utløpt).statusCode()).isEqualTo(404)
        assertThat(erstatt(navIdent, utløpt, "Nytt liv").statusCode()).isEqualTo(404)
        assertThat(send(settStandardForespørsel(navIdent, utløpt)).statusCode()).isEqualTo(404)
        assertThat(slett(navIdent, utløpt).statusCode()).isEqualTo(404)
        assertThat(radIDatabasen(utløpt)!!.second).isEqualTo(utløptDato)
    }

    @Test
    fun `utløpte søk teller ikke mot grensen og hindrer ikke automatisk standardvalg`() {
        val navIdent = nyIdent()
        settInn(navIdent, "Utløpt standard", utløptDato, erStandard = true)
        repeat(MAKS_ANTALL_SØK - 1) { settInn(navIdent, "Utløpt $it", utløptDato) }

        val nytt = opprett(navIdent, "Nytt")

        assertThat(nytt.erStandard).isTrue()
        assertThat(antallIDatabasen(navIdent)).isEqualTo(1)
    }

    @Test
    fun `opprydningsjobben sletter bare utløpte søk`() {
        val navIdent = nyIdent()
        val utløpt = settInn(navIdent, "Utløpt", utløptDato)
        val aktivt = settInn(navIdent, "Aktivt", utløptDato.plusDays(1))

        appCtx.standardsøkOpprydningsjobb.kjør()

        assertThat(radIDatabasen(utløpt)).isNull()
        assertThat(radIDatabasen(aktivt)).isNotNull()
    }

    /** Syntetisk nummer (måned + 80) med gyldige kontrollsifre, laget når testen kjører. */
    private fun syntetiskFødselsnummer(): String =
        (0..499).firstNotNullOf { individ ->
            val ni = "158690" + "%03d".format(individ)
            val sifre = ni.map { it - '0' }
            val k1 = kontroll(sifre, intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)) ?: return@firstNotNullOf null
            val k2 = kontroll(sifre + k1, intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)) ?: return@firstNotNullOf null
            "$ni$k1$k2"
        }

    private fun kontroll(sifre: List<Int>, vekter: IntArray): Int? =
        when (val rest = 11 - vekter.indices.sumOf { vekter[it] * sifre[it] } % 11) {
            11 -> 0
            10 -> null
            else -> rest
        }
}
