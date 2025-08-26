package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.Javalin
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat

class NaisController(private val prometheusMeterRegistry: PrometheusMeterRegistry) {
    fun setupRoutes(javalin: Javalin) {
        javalin.get("/internal/isReady", { it.status(200) })
        javalin.get("/internal/isAlive", { it.status(200) })
        javalin.get(
            "/internal/prometheus",
            { it.contentType(TextFormat.CONTENT_TYPE_004).result(prometheusMeterRegistry.scrape()) })
    }
}