package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.security.RouteRole
import no.nav.toi.rekrutteringsbistand.bruker.api.NavAnsattRolle
import no.nav.toi.rekrutteringsbistand.bruker.api.Tilgangsrolle
import no.nav.toi.rekrutteringsbistand.bruker.api.autentisertNavBruker
import java.util.UUID

class NyheterController(
    private val objectMapper: ObjectMapper,
    private val nyheterRepository: NyheterRepository
    ) {

    fun setupRoutes(javalin: Javalin) {
        javalin.get("/api/nyheter", { ctx -> hentNyheter(ctx) }, Tilgangsrolle.BESKYTTET)
        javalin.post("/api/nyheter/{uuid}", { ctx -> opprettNyhet(ctx)}, Tilgangsrolle.BESKYTTET)
        javalin.put("/api/nyheter/{uuid}", { ctx -> oppdaterNyhet(ctx)}, Tilgangsrolle.BESKYTTET)
    }

    private fun hentNyheter(ctx: Context) {
        ctx.autentisertNavBruker().verifiserAutorisasjon(NavAnsattRolle.ARBEIDSGIVER_RETTET, NavAnsattRolle.UTVIKLER)
        ctx.json(nyheterRepository.hentNyheter().map { nyhet -> nyhet.tilNyhetDtoResponse() })
        ctx.status(200)
    }

    private fun opprettNyhet(ctx: Context){
        ctx.autentisertNavBruker().verifiserAutorisasjon(NavAnsattRolle.UTVIKLER)
        val nyhetDtoRequest  = objectMapper.readValue(ctx.body(), NyhetDtoRequest::class.java)
        nyheterRepository.lagreNyhet(nyhetDtoRequest.tilNyhet(navIdent = ctx.autentisertNavBruker().hentNavIdent()))
        ctx.status(201)

    }

    private fun oppdaterNyhet(ctx: Context){
        ctx.autentisertNavBruker().verifiserAutorisasjon(NavAnsattRolle.UTVIKLER)
        val nyhetDtoRequest  = objectMapper.readValue(ctx.body(), NyhetDtoRequest::class.java)
        val nyhetId = UUID.fromString(ctx.req().getHeader("uuid"))
        nyheterRepository.lagreNyhet(nyhetDtoRequest.tilNyhet(nyhetId = nyhetId, navIdent = ctx.autentisertNavBruker().hentNavIdent()))
        ctx.status(200)
    }

}