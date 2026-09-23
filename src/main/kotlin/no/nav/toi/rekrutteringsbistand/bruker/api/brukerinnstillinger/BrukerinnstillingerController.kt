package no.nav.toi.rekrutteringsbistand.bruker.api.brukerinnstillinger

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.http.Context
import no.nav.toi.rekrutteringsbistand.bruker.api.NavAnsattRolle
import no.nav.toi.rekrutteringsbistand.bruker.api.Tilgangsrolle
import no.nav.toi.rekrutteringsbistand.bruker.api.autentisertNavBruker

data class BrukerinnstillingerDto(
    val antallLesteNyheter: Int = 0,
    val darkMode: Boolean = false,
    val windowMode: Boolean = false,
    val tekststørrelse: String = "standard",
)

class BrukerinnstillingerController(
    private val objectMapper: ObjectMapper,
    private val brukerinnstillingerRepository: BrukerinnstillingerRepository,
) {
    fun setupRoutes() {
        get("/api/innstillinger", { ctx -> hentBrukerinnstillinger(ctx) }, Tilgangsrolle.BESKYTTET)
        put("/api/innstillinger", { ctx -> lagreBrukerinnstillinger(ctx) }, Tilgangsrolle.BESKYTTET)
    }

    private fun hentBrukerinnstillinger(ctx: Context) {
        val navIdent = hentAutorisertNavIdent(ctx)
        ctx.json(brukerinnstillingerRepository.hentBrukerinnstillinger(navIdent))
    }

    private fun lagreBrukerinnstillinger(ctx: Context) {
        val navIdent = hentAutorisertNavIdent(ctx)
        val dto = objectMapper.readValue(ctx.body(), BrukerinnstillingerDto::class.java)
        require(dto.antallLesteNyheter >= 0) { "antallLesteNyheter kan ikke være negativ" }
        require(dto.tekststørrelse in setOf("liten", "standard", "stor", "ekstra-stor")) {
            "Ugyldig tekststørrelse"
        }
        brukerinnstillingerRepository.lagreBrukerinnstillinger(navIdent, dto)
        ctx.json(dto)
    }

    private fun hentAutorisertNavIdent(ctx: Context): String {
        val bruker = ctx.autentisertNavBruker()
        bruker.verifiserAutorisasjon(
            NavAnsattRolle.ARBEIDSGIVER_RETTET,
            NavAnsattRolle.UTVIKLER,
            NavAnsattRolle.JOBBSOKER_RETTET,
            NavAnsattRolle.MODIA_OPPFOLGING,
            NavAnsattRolle.MODIA_GENERELL_TILGANG,
        )
        return bruker.hentNavIdent()
    }
}