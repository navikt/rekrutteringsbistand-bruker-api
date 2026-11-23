package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.Javalin
import io.javalin.json.JavalinJackson3
import io.javalin.micrometer.MicrometerPlugin
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkFeil
import org.slf4j.LoggerFactory
import java.net.URI

private val log = noClassLogger()

fun main() {
    val env = System.getenv()
    val leaderElection = LeaderElection(
        electorUri = URI.create(env.getValue("ELECTOR_GET_URL")),
        hostname = env.getValue("HOSTNAME"),
    )
    val appContext = ApplicationContext(env, leaderElection)

    try {
        appContext.startApp()
    } catch (e: Exception) {
        log.error("Oppstart feilet", e)
        appContext.stoppApp()
        throw e
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        appContext.stoppApp()
        leaderElection.close()
    })
}

private fun ApplicationContext.setupAllRoutes() {
    naisController.setupRoutes()
    nyheterController.setupRoutes()
    tilbakemeldingerController.setupRoutes()
    brukerinnstillingerController.setupRoutes()
    lagredeSøkController.setupRoutes()
}

internal fun ApplicationContext.startJavalin(port: Int): Javalin {
    val log = LoggerFactory.getLogger("javalin")
    val rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
        arbeidsgiverrettet = arbeidsgiverrettet,
        utvikler = utvikler,
        jobbsokerrettet = jobbsokerrettet,
        modiaOppfolging = modiaOppfolging,
        modiaGenerellTilgang = modiaGenerell,
    )

    val server = Javalin.create {
        it.router.ignoreTrailingSlashes = true
        it.router.treatMultipleSlashesAsSingleSlash = true
        it.http.defaultContentType = "application/json"
        it.jsonMapper(JavalinJackson3(JacksonConfig.objectMapper))
        it.registerPlugin(MicrometerPlugin { micrometer -> micrometer.registry = prometheusRegistry })
        it.routes.apiBuilder { setupAllRoutes() }
        it.routes.beforeMatched { ctx ->
            tilgangsstyring.manage(
                ctx = ctx,
                routeRoles = ctx.routeRoles(),
                autentiseringskonfigurasjoner = autentiseringskonfigurasjoner,
                rolleUuidSpesifikasjon = rolleUuidSpesifikasjon,
            )
        }.exception(LagredeSøkFeil::class.java) { e, ctx ->
            ctx.status(e.status).json(mapOf("feil" to e.feil))
        }.exception(IllegalArgumentException::class.java) { e, ctx ->
            log.info("IllegalArgumentException: ${e.message}", e)
            ctx.status(400).result(e.message ?: "")
        }.exception(Exception::class.java) { e, ctx ->
            log.info("Exception: ${e.message}", e)
            ctx.status(500).result(e.message ?: "")
        }
    }

    try {
        return server.start(port)
    } catch (e: Exception) {
        runCatching { server.stop() }.onFailure(e::addSuppressed)
        throw e
    }
}