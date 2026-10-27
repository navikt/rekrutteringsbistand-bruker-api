package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import no.nav.toi.rekrutteringsbistand.bruker.api.Tilgangsrolle
import no.nav.toi.rekrutteringsbistand.bruker.api.autentisertNavBruker
import no.nav.toi.rekrutteringsbistand.bruker.api.log
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.StandardsøkDomene
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.Standardsøkdomener
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class StandardsøkController(
    private val objectMapper: ObjectMapper,
    private val service: StandardsøkService,
) {
    fun setupRoutes() {
        get("/api/standardsok/{domene}", { ctx -> hentAlleStandardsøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        post("/api/standardsok/{domene}", { ctx -> opprettStandardsøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        put("/api/standardsok/{domene}/{id}", { ctx -> erstattStandardsøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        // Må stå før DELETE /{id}, ellers fanger den ruten segmentet «standard».
        delete("/api/standardsok/{domene}/standard", { ctx -> velgVanligSøkSomStandardHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        delete("/api/standardsok/{domene}/{id}", { ctx -> slettStandardsøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        put("/api/standardsok/{domene}/{id}/standard", { ctx -> settSomStandardHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        post("/api/standardsok/{domene}/{id}/bruk", { ctx -> registrerBrukHandler(ctx) }, Tilgangsrolle.BESKYTTET)
    }

    private fun hentAlleStandardsøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        ctx.json(service.hentAlle(domene, navIdent))
    }

    private fun opprettStandardsøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        val input = GyldigStandardsøkInput.fra(lesBody(ctx), domene)
        ctx.status(201).json(service.opprett(domene, navIdent, input))
    }

    private fun erstattStandardsøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        val id = søkeident(ctx)
        val input = GyldigStandardsøkInput.fra(lesBody(ctx), domene)
        ctx.json(service.erstatt(domene, navIdent, id, input))
    }

    private fun slettStandardsøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        service.slett(domene, navIdent, søkeident(ctx))
        ctx.status(204)
    }

    private fun settSomStandardHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        service.settSomStandard(domene, navIdent, søkeident(ctx))
        ctx.status(204)
    }

    private fun velgVanligSøkSomStandardHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        service.velgVanligSøkSomStandard(domene, navIdent)
        ctx.status(204)
    }

    private fun registrerBrukHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        ctx.json(service.registrerBruk(domene, navIdent, søkeident(ctx)))
    }

    /**
     * Sjekker domene og rolle, henter Nav-identen fra tokenet og gjør uventede feil om til en fast 500.
     * Meldingen fra uventede feil logges ikke, siden JDBC og Jackson kan legge verdier fra forespørselen i den.
     */
    private fun håndter(ctx: Context, handling: (StandardsøkDomene, String) -> Unit) {
        try {
            val domene = Standardsøkdomener.fraStinavn(ctx.pathParam("domene")) ?: throw StandardsøkFeil.IkkeFunnet()
            val bruker = ctx.autentisertNavBruker()
            bruker.verifiserAutorisasjon(*domene.roller.toTypedArray())
            handling(domene, bruker.hentNavIdent())
        } catch (e: Exception) {
            when (e) {
                is StandardsøkFeil, is HttpResponseException -> throw e
                else -> {
                    log.error("Uventet feil i lagrede søk", FeilUtenMelding(e))
                    throw StandardsøkFeil.Intern()
                }
            }
        }
    }

    private fun lesBody(ctx: Context): LagreStandardsøkDto =
        try {
            objectMapper.readValue(ctx.body(), LagreStandardsøkDto::class.java)
        } catch (e: JacksonException) {
            null
        } ?: throw StandardsøkFeil.UgyldigInput("Forespørselen er ugyldig")

    private fun søkeident(ctx: Context): UUID =
        try {
            UUID.fromString(ctx.pathParam("id"))
        } catch (e: IllegalArgumentException) {
            throw StandardsøkFeil.IkkeFunnet()
        }
}
