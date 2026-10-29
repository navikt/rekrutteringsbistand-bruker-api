package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.json.JavalinJackson3
import io.javalin.json.JsonMapper
import io.javalin.micrometer.MicrometerPlugin
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkFeil
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.utlop.LagredeSøkOpprydningsjobb
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource


fun main() {
    val env = System.getenv()
    val appContext = ApplicationContext(env)
    appContext.startApp()
}

fun ApplicationContext.startApp(startOpprydningsjobb: Boolean = true): Javalin {
    val javalin = startJavalin(
        port = 8080,
        jsonMapper = JavalinJackson3(JacksonConfig.objectMapper),
        meterRegistry = prometheusRegistry,
        dataSource = dataSource,
        tilgangsstyring = tilgangsstyring,
        autentiseringskonfigurasjoner = autentiseringskonfigurasjoner,
        arbeidsgiverrettet = arbeidsgiverrettet,
        utvikler = utvikler,
        jobbsokerrettet = jobbsokerrettet,
        modiaOppfolging = modiaOppfolging,
        modiaGenerellTilgang = modiaGenerell
    )
    if (startOpprydningsjobb) lagredeSøkOpprydningsjobb.start()

    return javalin
}

private fun ApplicationContext.setupAllRoutes() {
    naisController.setupRoutes()
    nyheterController.setupRoutes()
    tilbakemeldingerController.setupRoutes()
    brukerinnstillingerController.setupRoutes()
    lagredeSøkController.setupRoutes()
}

fun ApplicationContext.startJavalin(
    port: Int = 8080,
    jsonMapper: JsonMapper,
    meterRegistry: PrometheusMeterRegistry,
    dataSource: DataSource,
    tilgangsstyring: Tilgangsstyring,
    autentiseringskonfigurasjoner: List<Autentiseringskonfigurasjon>,
    utvikler: UUID,
    arbeidsgiverrettet: UUID,
    jobbsokerrettet: UUID,
    modiaOppfolging: UUID,
    modiaGenerellTilgang: UUID
): Javalin {
    kjørFlywayMigreringer(dataSource)
    val log = LoggerFactory.getLogger("javalin")
    val micrometerPlugin = MicrometerPlugin { micrometerConfig ->
        micrometerConfig.registry = meterRegistry
    }
    return Javalin.create {
        it.router.ignoreTrailingSlashes = true
        it.router.treatMultipleSlashesAsSingleSlash = true
        it.http.defaultContentType = "application/json"
        it.jsonMapper(jsonMapper)
        it.registerPlugin(micrometerPlugin)
        it.kobleLagredeSøkOpprydningsjobbTilLivssyklus(lagredeSøkOpprydningsjobb)
        it.routes.apiBuilder { setupAllRoutes() }
        it.routes.beforeMatched { ctx ->
            if (ctx.routeRoles().isEmpty()) {
                // TODO: Burde vi kreve routerole. Vi trenger ikke håndtere både tom input og ubeskyttet, bedre å kreve ubeskytett routerole?
                return@beforeMatched
            }
            tilgangsstyring.manage(
                ctx = ctx,
                routeRoles = ctx.routeRoles(),
                autentiseringskonfigurasjoner = autentiseringskonfigurasjoner,
                rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
                    arbeidsgiverrettet = arbeidsgiverrettet,
                    utvikler = utvikler,
                    jobbsokerrettet = jobbsokerrettet,
                    modiaOppfolging = modiaOppfolging,
                    modiaGenerellTilgang = modiaGenerellTilgang
                )
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
    }.start(port)
}

internal fun JavalinConfig.kobleLagredeSøkOpprydningsjobbTilLivssyklus(
    jobb: LagredeSøkOpprydningsjobb,
) {
    events.serverStopping { jobb.close() }
}

private fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}
