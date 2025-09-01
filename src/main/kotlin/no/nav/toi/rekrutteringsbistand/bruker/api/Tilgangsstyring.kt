package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import io.javalin.security.RouteRole
import org.eclipse.jetty.http.HttpHeader

class Tilgangsstyring {
    fun manage(ctx: Context,
               routeRoles: Set<RouteRole>,
               autentiseringskonfigurasjoner: List<Autentiseringskonfigurasjon>,
               rolleUuidSpesifikasjon: RolleUuidSpesifikasjon) {
        require(routeRoles.size == 1) { "Støtter kun bruk av en rolle per endepunkt." }
        require(routeRoles.first() is Tilgangsrolle) { "Ukonfigurert rolle" }
        val rolle = routeRoles.first() as Tilgangsrolle

        if (rolle == Tilgangsrolle.UBESKYTTET) {
            return
        } else {
            val token = ctx.header(HttpHeader.AUTHORIZATION.name)
                ?.removePrefix("Bearer ")
                ?.trim() ?: throw UnauthorizedResponse("Mangler token")
            ctx.attribute("raw_token", token)

            val jwtVerifiers = autentiseringskonfigurasjoner.flatMap { it.jwtVerifiers() }

            val decoded = verifyJwt(jwtVerifiers, token)
            ctx.attribute("authenticatedUser", AutentisertNavBruker.fromJwt(decoded, rolleUuidSpesifikasjon))
        }
    }
}