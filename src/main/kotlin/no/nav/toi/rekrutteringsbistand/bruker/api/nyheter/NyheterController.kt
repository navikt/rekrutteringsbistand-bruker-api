package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.http.Context

class NyheterController(
    private val objectMapper: ObjectMapper,
    ) {

    fun setupRoutes(javalin: Javalin) {
        javalin.get("/api/nyheter") { ctx ->}
    }

    private fun hentNyheter(ctx: Context) {

    }

    private fun opprettNyhet(ctx: Context){
        val nyhet  = objectMapper.readValue(ctx.body(), Nyhet::class.java)

    }

}