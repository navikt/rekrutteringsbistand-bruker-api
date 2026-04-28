package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.apibuilder.ApiBuilder.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat

class NaisController(private val prometheusMeterRegistry: PrometheusMeterRegistry) {
    fun setupRoutes() {
        get("/internal/isReady", { it.status(200) }, Tilgangsrolle.UBESKYTTET)
        get("/internal/isAlive", { it.status(200) }, Tilgangsrolle.UBESKYTTET)
        get(
            "/internal/prometheus",
            { it.contentType(TextFormat.CONTENT_TYPE_004).result(prometheusMeterRegistry.scrape()) },
            Tilgangsrolle.UBESKYTTET)
    }
}