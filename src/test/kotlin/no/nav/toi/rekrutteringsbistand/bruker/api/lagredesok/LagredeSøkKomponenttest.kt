package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok

import no.nav.toi.rekrutteringsbistand.bruker.api.JacksonConfig
import no.nav.toi.rekrutteringsbistand.bruker.api.nyheter.TestRunningApplication
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene.LagredeSøkDomene
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.utlop.Utløpsregler
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

/**
 * Felles komponenttester for alle domener. Hvert domene har en underklasse med gyldige filtre.
 */
abstract class LagredeSøkKomponenttest(protected val domene: LagredeSøkDomene) : TestRunningApplication() {

    /** To ulike filtre som er gyldige i domenet. */
    protected abstract val filtre: Pair<String, String>

    protected val client = HttpClient.newHttpClient()
    protected val objectMapper = JacksonConfig.objectMapper
    protected val endepunkt = "$lokalUrlBase/api/lagrede-sok/${domene.stinavn}"
    protected val idag = LocalDate.now(ZoneId.of("Europe/Oslo"))
    protected val syntetiskNummer = "12345678901"

    protected val andreDomener get() = LagredeSøkDomene.entries - domene

    protected fun nyIdent() = "TEST-${UUID.randomUUID()}"

    protected fun token(navIdent: String, gruppe: String = "REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET"): String =
        appCtx.mockOauth2Server.issueToken(
            "azuread",
            "testsubject",
            appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to navIdent, "groups" to listOf(appCtx.env[gruppe])),
        ).serialize()

    protected fun forespørsel(
        url: String,
        navIdent: String?,
        gruppe: String = "REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET",
    ) = HttpRequest.newBuilder()
        .uri(URI(url))
        .header("Content-Type", "application/json")
        .apply { if (navIdent != null) header("Authorization", "Bearer ${token(navIdent, gruppe)}") }

    protected fun send(request: HttpRequest): HttpResponse<String> =
        client.send(request, HttpResponse.BodyHandlers.ofString())

    protected fun sendSamtidig(forespørsler: List<HttpRequest>): List<Int> =
        forespørsler
            .map { client.sendAsync(it, HttpResponse.BodyHandlers.ofString()) }
            .map { it.join().statusCode() }

    protected fun hent(navIdent: String): List<LagretSøkDto> {
        val respons = send(forespørsel(endepunkt, navIdent).GET().build())
        assertThat(respons.statusCode()).isEqualTo(200)
        return objectMapper.readValue(respons.body(), Array<LagretSøkDto>::class.java).toList()
    }

    protected fun body(navn: String, filter: String = "", fritekst: List<String> = emptyList()) =
        objectMapper.writeValueAsString(LagreSøkDto(navn, filter, fritekst))

    protected fun opprettForespørsel(navIdent: String, navn: String, filter: String = "", fritekst: List<String> = emptyList()) =
        forespørsel(endepunkt, navIdent).POST(HttpRequest.BodyPublishers.ofString(body(navn, filter, fritekst))).build()

    protected fun opprett(navIdent: String, navn: String, filter: String = "", fritekst: List<String> = emptyList()): LagretSøkDto {
        val respons = send(opprettForespørsel(navIdent, navn, filter, fritekst))
        assertThat(respons.statusCode()).isEqualTo(201)
        return objectMapper.readValue(respons.body(), LagretSøkDto::class.java)
    }

    protected fun erstatt(navIdent: String, id: UUID, navn: String, filter: String = "", fritekst: List<String> = emptyList()) =
        send(forespørsel("$endepunkt/$id", navIdent).PUT(HttpRequest.BodyPublishers.ofString(body(navn, filter, fritekst))).build())

    protected fun settStandardForespørsel(navIdent: String, id: UUID) =
        forespørsel("$endepunkt/$id/standard", navIdent).PUT(HttpRequest.BodyPublishers.noBody()).build()

    protected fun bruk(navIdent: String, id: UUID) =
        send(forespørsel("$endepunkt/$id/bruk", navIdent).POST(HttpRequest.BodyPublishers.noBody()).build())

    protected fun slett(navIdent: String, id: UUID) =
        send(forespørsel("$endepunkt/$id", navIdent).DELETE().build())

    protected fun velgVanligSøkForespørsel(navIdent: String?) =
        forespørsel("$endepunkt/vanlig/standard", navIdent).PUT(HttpRequest.BodyPublishers.noBody()).build()

    protected fun standardValgt(valg: String): Double =
        appCtx.prometheusRegistry.find("lagrede.sok.standard.valgt")
            .tags("domene", domene.stinavn, "valg", valg)
            .counter()?.count() ?: 0.0

    protected fun settInn(
        navIdent: String,
        navn: String,
        sistAktiv: LocalDate,
        erStandard: Boolean = false,
        domene: LagredeSøkDomene = this.domene,
        filter: String = "",
    ): UUID {
        val id = UUID.randomUUID()
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO lagrede_sok (domene, id, navIdent, navn, erStandard, filter, fritekst, sistAktiv)
                VALUES (?, ?, ?, ?, ?, ?, '{}', ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, domene.stinavn)
                ps.setObject(2, id)
                ps.setString(3, navIdent)
                ps.setString(4, navn)
                ps.setBoolean(5, erStandard)
                ps.setString(6, filter)
                ps.setObject(7, sistAktiv)
                ps.executeUpdate()
            }
        }
        return id
    }

    protected data class Rad(val erStandard: Boolean, val sistAktiv: LocalDate)

    protected fun radIDatabasen(id: UUID, domene: LagredeSøkDomene = this.domene): Rad? =
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement("SELECT erStandard, sistAktiv FROM lagrede_sok WHERE domene = ? AND id = ?").use { ps ->
                ps.setString(1, domene.stinavn)
                ps.setObject(2, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) Rad(rs.getBoolean(1), rs.getObject(2, LocalDate::class.java)) else null
                }
            }
        }

    protected fun antallIDatabasen(navIdent: String, kunStandard: Boolean = false): Int =
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement(
                "SELECT count(*) FROM lagrede_sok WHERE domene = ? AND navIdent = ?" +
                    if (kunStandard) " AND erStandard" else ""
            ).use { ps ->
                ps.setString(1, domene.stinavn)
                ps.setString(2, navIdent)
                ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
        }

    protected val utløptDato get() = Utløpsregler.sisteUtløpteSistAktiv(idag)

    protected fun slettedeUtløpte(): Double =
        appCtx.prometheusRegistry.find("lagrede.sok.slettet")
            .tags("domene", domene.stinavn, "arsak", "utlop")
            .counter()?.count() ?: 0.0

    @AfterEach
    fun ryddOpp() {
        appCtx.dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM lagrede_sok").use { it.executeUpdate() }
        }
    }

    @Test
    fun `gir 401 uten token`() {
        assertThat(send(forespørsel(endepunkt, null).GET().build()).statusCode()).isEqualTo(401)
    }

    @Test
    fun `alle rollene i bruker-api har tilgang`() {
        listOf(
            "REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET",
            "REKRUTTERINGSBISTAND_JOBBSOKERRETTET",
            "REKRUTTERINGSBISTAND_UTVIKLER",
            "REKRUTTERINGSBISTAND_MODIA_OPPFOLGING",
            "REKRUTTERINGSBISTAND_MODIA_GENERELL_TILGANG",
        ).forEach { gruppe ->
            val request = forespørsel(endepunkt, nyIdent(), gruppe).GET().build()
            assertThat(send(request).statusCode()).`as`(gruppe).isEqualTo(200)
        }
    }

    @Test
    fun `token uten kjent rolle gir 403`() {
        val tokenUtenRolle = appCtx.mockOauth2Server.issueToken(
            "azuread",
            "testsubject",
            appCtx.env["AZURE_APP_CLIENT_ID"],
            mapOf("NAVident" to nyIdent(), "groups" to listOf(UUID.randomUUID().toString())),
        ).serialize()
        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer $tokenUtenRolle")
            .GET()
            .build()
        assertThat(send(request).statusCode()).isEqualTo(403)
    }

    @Test
    fun `ukjent domene gir 404`() {
        val respons = send(forespørsel("$lokalUrlBase/api/lagrede-sok/ukjent", nyIdent()).GET().build())
        assertThat(respons.statusCode()).isEqualTo(404)
    }

    @Test
    fun `tom liste uten lagrede søk`() {
        assertThat(hent(nyIdent())).isEmpty()
    }

    @Test
    fun `nye søk får frist, søkeord og plass i listen`() {
        val navIdent = nyIdent()
        val første = opprett(navIdent, "Publiserte", filtre.first, listOf("kokk"))
        val andre = opprett(navIdent, "Utkast", filtre.second)

        assertThat(andre.erStandard).isFalse()
        assertThat(første.sistAktiv).isEqualTo(idag)
        assertThat(første.slettes).isEqualTo(idag.plusMonths(6))
        assertThat(første.visSlettevarsel).isFalse()
        assertThat(første.fritekst).containsExactly("kokk")
        assertThat(hent(navIdent).map { it.navn }).containsExactly("Publiserte", "Utkast")
    }

    @Test
    fun `standardsøket står først, og resten sorteres etter norsk alfabet med æ, ø og å til slutt`() {
        val navIdent = nyIdent()
        val søk = listOf("Østlandet", "Åpne treff", "Ærlige treff", "bergen", "Arendal", "Zebra")
            .associateWith { opprett(navIdent, it, filtre.first).id }

        assertThat(send(settStandardForespørsel(navIdent, søk.getValue("Østlandet"))).statusCode()).isEqualTo(204)
        assertThat(hent(navIdent).map { it.navn })
            .containsExactly("Østlandet", "Arendal", "bergen", "Zebra", "Ærlige treff", "Åpne treff")

        assertThat(send(settStandardForespørsel(navIdent, søk.getValue("Zebra"))).statusCode()).isEqualTo(204)
        assertThat(hent(navIdent).map { it.navn })
            .containsExactly("Zebra", "Arendal", "bergen", "Ærlige treff", "Østlandet", "Åpne treff")
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

        val respons = erstatt(navIdent, annet, "Nytt navn", filtre.second, listOf("lager"))
        assertThat(respons.statusCode()).isEqualTo(200)
        val oppdatert = objectMapper.readValue(respons.body(), LagretSøkDto::class.java)

        assertThat(oppdatert.navn).isEqualTo("Nytt navn")
        assertThat(oppdatert.filter).isEqualTo(filtre.second)
        assertThat(oppdatert.fritekst).containsExactly("lager")
        assertThat(oppdatert.sistAktiv).isEqualTo(idag)
        assertThat(oppdatert.erStandard).isFalse()
        assertThat(radIDatabasen(standard)!!.erStandard).isTrue()
    }

    @Test
    fun `bytte av standard fjerner det gamle merket og fornyer bare det valgte søket`() {
        val navIdent = nyIdent()
        val gammelDato = idag.minusMonths(2)
        val gammelStandard = settInn(navIdent, "Gammel", gammelDato, erStandard = true)
        val nyStandard = settInn(navIdent, "Ny", gammelDato)

        assertThat(send(settStandardForespørsel(navIdent, nyStandard)).statusCode()).isEqualTo(204)

        assertThat(radIDatabasen(gammelStandard)).isEqualTo(Rad(erStandard = false, sistAktiv = gammelDato))
        assertThat(radIDatabasen(nyStandard)).isEqualTo(Rad(erStandard = true, sistAktiv = idag))
    }

    @Test
    fun `standardvalg av et søk som ikke finnes beholder det gamle standardsøket`() {
        val navIdent = nyIdent()
        val standard = settInn(navIdent, "Standard", idag.minusDays(3), erStandard = true)

        assertThat(send(settStandardForespørsel(navIdent, UUID.randomUUID())).statusCode()).isEqualTo(404)

        assertThat(radIDatabasen(standard)).isEqualTo(Rad(erStandard = true, sistAktiv = idag.minusDays(3)))
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
    fun `sletting av eneste søk gir tom liste uten standardsøk`() {
        val navIdent = nyIdent()
        val eneste = opprett(navIdent, "Eneste")
        assertThat(slett(navIdent, eneste.id).statusCode()).isEqualTo(204)
        assertThat(hent(navIdent)).isEmpty()
    }

    @Test
    fun `sletting av et annet søk beholder standardsøket`() {
        val navIdent = nyIdent()
        val standard = settInn(navIdent, "Standard", idag.minusMonths(3), erStandard = true)
        val annet = settInn(navIdent, "Annet", idag)

        assertThat(slett(navIdent, annet).statusCode()).isEqualTo(204)

        assertThat(radIDatabasen(standard)).isEqualTo(Rad(erStandard = true, sistAktiv = idag.minusMonths(3)))
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
    fun `avvist opprettelse beholder utløpte søk og teller dem ikke som slettet`() {
        val navIdent = nyIdent()
        repeat(MAKS_ANTALL_SØK) { settInn(navIdent, "Aktivt $it", idag) }
        val utløpt = settInn(navIdent, "Utløpt", utløptDato)
        val slettetFør = slettedeUtløpte()

        assertThat(send(opprettForespørsel(navIdent, "Ett for mye")).statusCode()).isEqualTo(409)

        assertThat(radIDatabasen(utløpt)).isNotNull()
        assertThat(slettedeUtløpte()).isEqualTo(slettetFør)
    }

    @Test
    fun `samtidige opprettelser gir aldri mer enn ti søk og ingen standardsøk`() {
        val navIdent = nyIdent()
        val forespørsler = (1..20).map { opprettForespørsel(navIdent, "Samtidig $it") }
        val statuser = sendSamtidig(forespørsler)

        assertThat(statuser.count { it == 201 }).isEqualTo(MAKS_ANTALL_SØK)
        assertThat(statuser.count { it == 409 }).isEqualTo(20 - MAKS_ANTALL_SØK)
        assertThat(antallIDatabasen(navIdent)).isEqualTo(MAKS_ANTALL_SØK)
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isZero()
    }

    @Test
    fun `samtidige standardvalg gir høyst ett standardsøk`() {
        val navIdent = nyIdent()
        val ider = (1..5).map { settInn(navIdent, "Søk $it", idag.minusDays(3)) }
        val forespørsler = (1..20).map { settStandardForespørsel(navIdent, ider[it % ider.size]) }

        assertThat(sendSamtidig(forespørsler)).containsOnly(204)
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isEqualTo(1)
    }

    @Test
    fun `samtidige valg av vanlig søk og lagrede søk gir høyst ett standardsøk`() {
        val navIdent = nyIdent()
        val ider = (1..3).map { settInn(navIdent, "Søk $it", idag.minusDays(3)) }
        val forespørsler = (1..20).map {
            if (it % 2 == 0) velgVanligSøkForespørsel(navIdent) else settStandardForespørsel(navIdent, ider[it % ider.size])
        }

        assertThat(sendSamtidig(forespørsler)).containsOnly(204)
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isLessThanOrEqualTo(1)
    }

    @Test
    fun `samtidig bruk av samme søk gir 200 på alle kall og fornyer fristen`() {
        val navIdent = nyIdent()
        val søk = settInn(navIdent, "Søk", idag.minusDays(3))
        val forespørsler = (1..10).map {
            forespørsel("$endepunkt/$søk/bruk", navIdent).POST(HttpRequest.BodyPublishers.noBody()).build()
        }

        assertThat(sendSamtidig(forespørsler)).containsOnly(200)
        assertThat(radIDatabasen(søk)!!.sistAktiv).isEqualTo(idag)
    }

    @Test
    fun `samtidig erstatting og sletting av samme søk gir ingen serverfeil`() {
        val navIdent = nyIdent()
        val søk = settInn(navIdent, "Søk", idag.minusDays(3))
        val forespørsler = (1..10).map {
            if (it % 5 == 0) {
                forespørsel("$endepunkt/$søk", navIdent).DELETE().build()
            } else {
                forespørsel("$endepunkt/$søk", navIdent)
                    .PUT(HttpRequest.BodyPublishers.ofString(body("Nytt navn $it", filtre.first))).build()
            }
        }

        assertThat(sendSamtidig(forespørsler)).allMatch { it in setOf(200, 204, 404) }
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
    fun `fødselsnummer i navn eller søkeord gir 400 uten innholdet i svaret, og ingenting lagres`() {
        val navIdent = nyIdent()
        val tilfeller = listOf(
            Triple("fødselsnummer som navn", syntetiskNummer, emptyList<String>()),
            Triple("fødselsnummer etter tekst i navn", "Treff $syntetiskNummer", emptyList()),
            Triple("fødselsnummer i søkeord", "Treff", listOf(syntetiskNummer)),
            Triple("fødselsnummer etter tekst i søkeord", "Treff", listOf("kokk", "kokk $syntetiskNummer")),
        )
        tilfeller.forEach { (beskrivelse, navn, fritekst) ->
            val respons = send(opprettForespørsel(navIdent, navn, filtre.first, fritekst))
            assertThat(respons.statusCode()).describedAs(beskrivelse).isEqualTo(400)
            assertThat(respons.body()).describedAs(beskrivelse).doesNotContain(syntetiskNummer)
        }
        assertThat(antallIDatabasen(navIdent)).isZero()
    }

    @Test
    fun `fødselsnummer ved erstatting gir 400, og søket er uendret`() {
        val navIdent = nyIdent()
        val søk = opprett(navIdent, "Før", filtre.first, listOf("kokk"))

        assertThat(erstatt(navIdent, søk.id, "Etter $syntetiskNummer", filtre.second).statusCode()).isEqualTo(400)
        assertThat(erstatt(navIdent, søk.id, "Etter", filtre.second, listOf(syntetiskNummer)).statusCode()).isEqualTo(400)

        val lagret = hent(navIdent).single()
        assertThat(lagret.navn).isEqualTo("Før")
        assertThat(lagret.filter).isEqualTo(filtre.first)
        assertThat(lagret.fritekst).containsExactly("kokk")
    }

    @Test
    fun `søk med filtre som er tatt ut av søket, kan hentes, brukes og få nytt navn`() {
        val navIdent = nyIdent()
        val id = settInn(navIdent, "Gammelt søk", idag, filter = "fjernet=gammel&${filtre.first}")

        assertThat(hent(navIdent).single().filter).isEqualTo(filtre.first)
        val brukt = objectMapper.readValue(bruk(navIdent, id).body(), LagretSøkDto::class.java)
        assertThat(brukt.filter).isEqualTo(filtre.first)

        val respons = erstatt(navIdent, id, "Nytt navn", brukt.filter)
        assertThat(respons.statusCode()).isEqualTo(200)
        assertThat(hent(navIdent).single()).extracting("navn", "filter").containsExactly("Nytt navn", filtre.first)
    }

    @Test
    fun `bruk fornyer fristen og returnerer søket, henting gjør det ikke`() {
        val navIdent = nyIdent()
        val gammelDato = idag.minusDays(10)
        val id = settInn(navIdent, "Gammelt", gammelDato)

        hent(navIdent)
        assertThat(radIDatabasen(id)!!.sistAktiv).isEqualTo(gammelDato)

        val respons = bruk(navIdent, id)
        assertThat(respons.statusCode()).isEqualTo(200)
        val søk = objectMapper.readValue(respons.body(), LagretSøkDto::class.java)
        assertThat(søk.sistAktiv).isEqualTo(idag)
        assertThat(radIDatabasen(id)!!.sistAktiv).isEqualTo(idag)

        val igjen = bruk(navIdent, id)
        assertThat(igjen.statusCode()).isEqualTo(200)
        assertThat(objectMapper.readValue(igjen.body(), LagretSøkDto::class.java).sistAktiv).isEqualTo(idag)
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
        assertThat(radIDatabasen(utløpt)!!.sistAktiv).isEqualTo(utløptDato)
    }

    @Test
    fun `utløpte søk teller ikke mot grensen, og bare opprydningsjobben sletter dem`() {
        val navIdent = nyIdent()
        val utløptStandard = settInn(navIdent, "Utløpt standard", utløptDato, erStandard = true)
        repeat(MAKS_ANTALL_SØK - 1) { settInn(navIdent, "Utløpt $it", utløptDato) }
        val slettetFør = slettedeUtløpte()

        val nytt = opprett(navIdent, "Nytt")
        assertThat(slett(navIdent, nytt.id).statusCode()).isEqualTo(204)
        opprett(navIdent, "Nytt igjen")

        assertThat(hent(navIdent).map { it.navn }).containsExactly("Nytt igjen")
        assertThat(antallIDatabasen(navIdent)).isEqualTo(MAKS_ANTALL_SØK + 1)
        assertThat(radIDatabasen(utløptStandard)).isEqualTo(Rad(erStandard = true, sistAktiv = utløptDato))
        assertThat(slettedeUtløpte()).isEqualTo(slettetFør)

        appCtx.lagredeSøkOpprydningsjobb.kjør()

        assertThat(antallIDatabasen(navIdent)).isEqualTo(1)
    }

    @Test
    fun `opprydningsjobben sletter bare utløpte søk`() {
        val navIdent = nyIdent()
        val utløpt = settInn(navIdent, "Utløpt", utløptDato)
        val aktivt = settInn(navIdent, "Aktivt", utløptDato.plusDays(1))

        appCtx.lagredeSøkOpprydningsjobb.kjør()

        assertThat(radIDatabasen(utløpt)).isNull()
        assertThat(radIDatabasen(aktivt)).isNotNull()
    }

    @Test
    fun `første søk blir ikke standard, vanlig søk er standard`() {
        val navIdent = nyIdent()
        val første = opprett(navIdent, "Første", filtre.first)

        assertThat(første.erStandard).isFalse()
        assertThat(hent(navIdent).none { it.erStandard }).isTrue()
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isZero()
    }

    @Test
    fun `vanlig søk kan velges som standard, og et lagret søk kan velges igjen etterpå`() {
        val navIdent = nyIdent()
        val søk = opprett(navIdent, "Lagret", filtre.first).id

        assertThat(send(settStandardForespørsel(navIdent, søk)).statusCode()).isEqualTo(204)
        assertThat(hent(navIdent).single().erStandard).isTrue()

        assertThat(send(velgVanligSøkForespørsel(navIdent)).statusCode()).isEqualTo(204)
        assertThat(hent(navIdent).single().erStandard).isFalse()
        assertThat(hent(navIdent).single().erStandard).`as`("henting skal ikke velge standard på nytt").isFalse()

        assertThat(send(settStandardForespørsel(navIdent, søk)).statusCode()).isEqualTo(204)
        assertThat(hent(navIdent).single().erStandard).isTrue()
    }

    @Test
    fun `å velge vanlig søk fjerner bare merket og sletter ingen søk`() {
        val navIdent = nyIdent()
        val standard = settInn(navIdent, "Standard", idag.minusDays(3), erStandard = true)
        settInn(navIdent, "Annet", idag)

        assertThat(send(velgVanligSøkForespørsel(navIdent)).statusCode()).isEqualTo(204)

        assertThat(radIDatabasen(standard)).isEqualTo(Rad(erStandard = false, sistAktiv = idag.minusDays(3)))
        assertThat(antallIDatabasen(navIdent)).isEqualTo(2)
    }

    @Test
    fun `å velge vanlig søk er idempotent og telles bare når standarden endres`() {
        val navIdent = nyIdent()
        settInn(navIdent, "Standard", idag, erStandard = true)
        val før = standardValgt("vanlig_sok")

        repeat(3) { assertThat(send(velgVanligSøkForespørsel(navIdent)).statusCode()).isEqualTo(204) }

        assertThat(standardValgt("vanlig_sok")).isEqualTo(før + 1)
        assertThat(send(velgVanligSøkForespørsel(nyIdent())).statusCode()).isEqualTo(204)
        assertThat(standardValgt("vanlig_sok")).isEqualTo(før + 1)
    }

    @Test
    fun `å velge vanlig søk når standardsøket er utløpt telles ikke, fordi vanlig søk allerede er standard`() {
        val navIdent = nyIdent()
        val utløpt = settInn(navIdent, "Utløpt standard", utløptDato, erStandard = true)
        val før = standardValgt("vanlig_sok")

        assertThat(send(velgVanligSøkForespørsel(navIdent)).statusCode()).isEqualTo(204)

        assertThat(standardValgt("vanlig_sok")).isEqualTo(før)
        assertThat(radIDatabasen(utløpt)!!.erStandard).isFalse()
    }

    @Test
    fun `valg av et lagret søk telles bare når standarden endres`() {
        val navIdent = nyIdent()
        val søk = settInn(navIdent, "Søk", idag)
        val før = standardValgt("lagret_sok")

        repeat(2) { assertThat(send(settStandardForespørsel(navIdent, søk)).statusCode()).isEqualTo(204) }

        assertThat(standardValgt("lagret_sok")).isEqualTo(før + 1)
    }

    @Test
    fun `sletting av standardsøket gjør vanlig søk til standard`() {
        val navIdent = nyIdent()
        val standard = settInn(navIdent, "Standard", idag, erStandard = true)
        settInn(navIdent, "Nylig", idag.minusDays(1))

        assertThat(slett(navIdent, standard).statusCode()).isEqualTo(204)

        assertThat(hent(navIdent).none { it.erStandard }).isTrue()
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isZero()
    }

    @Test
    fun `utløpt standardsøk gir vanlig søk, og henting skriver ikke`() {
        val navIdent = nyIdent()
        val utløpt = settInn(navIdent, "Utløpt standard", utløptDato, erStandard = true)
        settInn(navIdent, "Aktivt", idag.minusDays(1))
        val slettetFør = slettedeUtløpte()

        assertThat(hent(navIdent).none { it.erStandard }).isTrue()

        assertThat(radIDatabasen(utløpt)).isEqualTo(Rad(erStandard = true, sistAktiv = utløptDato))
        assertThat(slettedeUtløpte()).isEqualTo(slettetFør)
    }

    @Test
    fun `etter opprydningsjobben er vanlig søk standard`() {
        val navIdent = nyIdent()
        settInn(navIdent, "Utløpt standard", utløptDato, erStandard = true)
        settInn(navIdent, "Aktivt", idag.minusDays(5))

        appCtx.lagredeSøkOpprydningsjobb.kjør()

        assertThat(hent(navIdent).single().erStandard).isFalse()
        assertThat(antallIDatabasen(navIdent, kunStandard = true)).isZero()
    }

    @Test
    fun `et lagret søk kan velges som standard selv om et utløpt søk fortsatt er merket`() {
        val navIdent = nyIdent()
        val utløpt = settInn(navIdent, "Utløpt standard", utløptDato, erStandard = true)
        val aktivt = settInn(navIdent, "Aktivt", idag.minusDays(1))

        assertThat(send(settStandardForespørsel(navIdent, aktivt)).statusCode()).isEqualTo(204)

        assertThat(radIDatabasen(aktivt)!!.erStandard).isTrue()
        assertThat(radIDatabasen(utløpt)!!.erStandard).isFalse()
    }

    @Test
    fun `standardvalg med ugyldig id gir 404 og endrer ikke valget`() {
        val navIdent = nyIdent()
        val søk = opprett(navIdent, "Mitt søk")
        assertThat(send(settStandardForespørsel(navIdent, søk.id)).statusCode()).isEqualTo(204)

        listOf("tull", "undefined", "null", "42").forEach { id ->
            val respons = send(forespørsel("$endepunkt/$id/standard", navIdent).PUT(HttpRequest.BodyPublishers.noBody()).build())
            assertThat(respons.statusCode()).describedAs(id).isEqualTo(404)
            assertThat(respons.body()).doesNotContain("tull")
        }
        assertThat(hent(navIdent).single().erStandard).isTrue()
    }

    @Test
    fun `de gamle rutene for vanlig søk finnes ikke`() {
        val navIdent = nyIdent()
        val søk = opprett(navIdent, "Mitt søk")
        assertThat(send(settStandardForespørsel(navIdent, søk.id)).statusCode()).isEqualTo(204)

        val gammelDelete = forespørsel("$endepunkt/standard", navIdent).DELETE().build()
        val gammelPut = forespørsel("$endepunkt/standard", navIdent).PUT(HttpRequest.BodyPublishers.noBody()).build()
        assertThat(send(gammelDelete).statusCode()).isEqualTo(404)
        assertThat(send(gammelPut).statusCode()).isEqualTo(404)
        assertThat(hent(navIdent).single().erStandard).isTrue()
    }

    @Test
    fun `søk i andre domener er ikke synlige og kan ikke endres herfra`() {
        val navIdent = nyIdent()
        val andreSøk = andreDomener.associateWith { settInn(navIdent, "Annet domene", idag.minusDays(3), erStandard = true, domene = it) }

        assertThat(hent(navIdent)).isEmpty()
        andreSøk.values.forEach { id ->
            assertThat(erstatt(navIdent, id, "Overtatt").statusCode()).isEqualTo(404)
            assertThat(send(settStandardForespørsel(navIdent, id)).statusCode()).isEqualTo(404)
            assertThat(bruk(navIdent, id).statusCode()).isEqualTo(404)
            assertThat(slett(navIdent, id).statusCode()).isEqualTo(404)
        }
        andreSøk.forEach { (annetDomene, id) ->
            assertThat(radIDatabasen(id, annetDomene)).isEqualTo(Rad(erStandard = true, sistAktiv = idag.minusDays(3)))
        }
    }

    @Test
    fun `grensen på ti og standardvalget gjelder per domene`() {
        val navIdent = nyIdent()
        val andreStandardsøk = andreDomener.associateWith { annetDomene ->
            repeat(MAKS_ANTALL_SØK - 1) { settInn(navIdent, "Annet $it", idag, domene = annetDomene) }
            settInn(navIdent, "Annen standard", idag, erStandard = true, domene = annetDomene)
        }

        val søk = (1..MAKS_ANTALL_SØK).map { opprett(navIdent, "Søk $it") }
        assertThat(send(settStandardForespørsel(navIdent, søk.first().id)).statusCode()).isEqualTo(204)
        assertThat(send(velgVanligSøkForespørsel(navIdent)).statusCode()).isEqualTo(204)

        andreStandardsøk.forEach { (annetDomene, id) ->
            assertThat(radIDatabasen(id, annetDomene)!!.erStandard).`as`(annetDomene.stinavn).isTrue()
        }
    }

    @Test
    fun `utløpte søk i andre domener slettes ikke når et søk lagres`() {
        val navIdent = nyIdent()
        val andreUtløpte = andreDomener.associateWith { settInn(navIdent, "Utløpt", utløptDato, domene = it) }

        opprett(navIdent, "Nytt")

        andreUtløpte.forEach { (annetDomene, id) ->
            assertThat(radIDatabasen(id, annetDomene)).`as`(annetDomene.stinavn).isNotNull()
        }
    }
}
