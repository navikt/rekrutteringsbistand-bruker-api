package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.http.Context
import no.nav.toi.rekrutteringsbistand.bruker.api.NavAnsattRolle
import no.nav.toi.rekrutteringsbistand.bruker.api.Tilgangsrolle
import no.nav.toi.rekrutteringsbistand.bruker.api.autentisertNavBruker
import no.nav.toi.rekrutteringsbistand.bruker.api.log
import java.util.UUID

class TilbakemeldingerController(
    private val objectMapper: ObjectMapper,
    private val tilbakemeldingerRepository: TilbakemeldingerRepository
) {

    fun setupRoutes(javalin: Javalin) {
        javalin.get("/api/tilbakemeldinger", { ctx -> hentTilbakemeldinger(ctx) }, Tilgangsrolle.BESKYTTET)
        javalin.post("/api/tilbakemeldinger", { ctx -> opprettTilbakemelding(ctx) }, Tilgangsrolle.BESKYTTET)
        javalin.put("/api/tilbakemeldinger/{uuid}", { ctx -> oppdaterTilbakemelding(ctx) }, Tilgangsrolle.BESKYTTET)
        javalin.delete("/api/tilbakemeldinger/{uuid}", { ctx -> slettTilbakemelding(ctx) }, Tilgangsrolle.BESKYTTET)
    }

    private fun hentTilbakemeldinger(ctx: Context) {
        ctx.autentisertNavBruker().verifiserAutorisasjon(NavAnsattRolle.UTVIKLER)

        val side = ctx.queryParam("side")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val (tilbakemeldinger, totalt) = tilbakemeldingerRepository.hentSide(side)
        val totalSider = if (totalt == 0) 1 else (totalt + 24) / 25

        log.info("Henter tilbakemeldinger side $side av $totalSider")
        ctx.json(TilbakemeldingerPageResponse(
            tilbakemeldinger = tilbakemeldinger.map { it.tilResponse() },
            side = side,
            totalSider = totalSider,
            totaltAntall = totalt,
        ))
        ctx.status(200)
    }

    private fun opprettTilbakemelding(ctx: Context) {
        ctx.autentisertNavBruker().verifiserAutorisasjon(
            NavAnsattRolle.ARBEIDSGIVER_RETTET,
            NavAnsattRolle.UTVIKLER,
            NavAnsattRolle.JOBBSOKER_RETTET,
            NavAnsattRolle.MODIA_OPPFOLGING,
            NavAnsattRolle.MODIA_GENERELL_TILGANG
        )

        val request = objectMapper.readValue(ctx.body(), TilbakemeldingOpprettRequest::class.java)
        val tilbakemelding = tilbakemeldingerRepository.opprett(request.tilTilbakemelding())
        log.info("Opprettet tilbakemelding med id ${tilbakemelding.id}")
        ctx.json(tilbakemelding.tilResponse())
        ctx.status(201)
    }

    private fun oppdaterTilbakemelding(ctx: Context) {
        ctx.autentisertNavBruker().verifiserAutorisasjon(NavAnsattRolle.UTVIKLER)

        val id = UUID.fromString(ctx.pathParam("uuid"))
        val request = objectMapper.readValue(ctx.body(), TilbakemeldingOppdaterRequest::class.java)
        val oppdatert = tilbakemeldingerRepository.oppdater(id, request)
        log.info("Oppdaterte tilbakemelding med id $id")
        ctx.json(oppdatert.tilResponse())
        ctx.status(200)
    }

    private fun slettTilbakemelding(ctx: Context) {
        ctx.autentisertNavBruker().verifiserAutorisasjon(NavAnsattRolle.UTVIKLER)

        val id = UUID.fromString(ctx.pathParam("uuid"))
        tilbakemeldingerRepository.slett(id)
        log.info("Slettet tilbakemelding med id $id")
        ctx.status(200)
    }
}
