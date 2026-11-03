package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok

import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import no.nav.toi.rekrutteringsbistand.bruker.api.Tilgangsrolle
import no.nav.toi.rekrutteringsbistand.bruker.api.autentisertNavBruker
import no.nav.toi.rekrutteringsbistand.bruker.api.log
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene.LagredeSøkDomene
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class LagredeSøkController(
    private val objectMapper: ObjectMapper,
    private val service: LagredeSøkService,
) {
    fun setupRoutes() {
        get("/api/lagrede-sok/{domene}", { ctx -> hentAlleLagredeSøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        post("/api/lagrede-sok/{domene}", { ctx -> opprettLagretSøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        put("/api/lagrede-sok/{domene}/{id}", { ctx -> erstattLagretSøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        delete("/api/lagrede-sok/{domene}/{id}", { ctx -> slettLagretSøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)

        // Må stå før {id}/standard, ellers fanger den ruten segmentet «vanlig». En UUID kan aldri være «vanlig».
        put("/api/lagrede-sok/{domene}/vanlig/standard", { ctx -> velgVanligSøkHandler(ctx) }, Tilgangsrolle.BESKYTTET)
        put("/api/lagrede-sok/{domene}/{id}/standard", { ctx -> velgStandardHandler(ctx) }, Tilgangsrolle.BESKYTTET)

        post("/api/lagrede-sok/{domene}/{id}/bruk", { ctx -> registrerBrukHandler(ctx) }, Tilgangsrolle.BESKYTTET)
    }

    private fun hentAlleLagredeSøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        ctx.json(service.hentAlle(domene, navIdent))
    }

    private fun opprettLagretSøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        val input = GyldigLagreSøkInput.fra(lesBody(ctx), domene)
        ctx.status(201).json(service.opprett(domene, navIdent, input))
    }

    private fun erstattLagretSøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        val id = søkeident(ctx)
        val input = GyldigLagreSøkInput.fra(lesBody(ctx), domene)
        ctx.json(service.erstatt(domene, navIdent, id, input))
    }

    private fun slettLagretSøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        service.slett(domene, navIdent, søkeident(ctx))
        ctx.status(204)
    }

    /** Vanlig søk er standard når ingen lagrede søk er valgt. */
    private fun velgVanligSøkHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        service.velgStandard(domene, navIdent, null)
        ctx.status(204)
    }

    private fun velgStandardHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        service.velgStandard(domene, navIdent, søkeident(ctx))
        ctx.status(204)
    }

    private fun registrerBrukHandler(ctx: Context) = håndter(ctx) { domene, navIdent ->
        ctx.json(service.registrerBruk(domene, navIdent, søkeident(ctx)))
    }

    /**
     * Sjekker domene og at brukeren har en av rollene, henter Nav-identen fra tokenet og gjør uventede feil om til en fast 500.
     * Meldingen fra uventede feil logges ikke, siden JDBC og Jackson kan legge verdier fra forespørselen i den.
     */
    private fun håndter(ctx: Context, handling: (LagredeSøkDomene, String) -> Unit) {
        try {
            val domene = LagredeSøkDomene.fraStinavn(ctx.pathParam("domene")) ?: throw LagredeSøkFeil.IkkeFunnet()
            val bruker = ctx.autentisertNavBruker()
            bruker.verifiserHarEnRolle()
            handling(domene, bruker.hentNavIdent())
        } catch (e: Exception) {
            when (e) {
                is LagredeSøkFeil, is HttpResponseException -> throw e
                else -> {
                    log.error("Uventet feil i lagrede søk", FeilUtenMelding(e))
                    throw LagredeSøkFeil.Intern()
                }
            }
        }
    }

    private fun lesBody(ctx: Context): LagreSøkDto =
        try {
            objectMapper.readValue(ctx.body(), LagreSøkDto::class.java)
        } catch (e: JacksonException) {
            null
        } ?: throw LagredeSøkFeil.UgyldigInput("Forespørselen er ugyldig")

    private fun søkeident(ctx: Context): UUID =
        try {
            UUID.fromString(ctx.pathParam("id"))
        } catch (e: IllegalArgumentException) {
            throw LagredeSøkFeil.IkkeFunnet()
        }
}
