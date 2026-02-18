package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.Javalin
import io.javalin.json.JavalinJackson
import io.javalin.micrometer.MicrometerPlugin
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource


fun main() {
    val env = System.getenv()
    val appContext = ApplicationContext(env)
    appContext.startApp()
}

fun ApplicationContext.startApp(): Javalin {
    val javalin = startJavalin(
        port = 8080,
        jsonMapper = JavalinJackson(objectMapper),
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
    setupAllRoutes(javalin)

    return javalin
}

private fun ApplicationContext.setupAllRoutes(javalin: Javalin) {
    naisController.setupRoutes(javalin)
    nyheterController.setupRoutes(javalin)
    tilbakemeldingerController.setupRoutes(javalin)
}

fun startJavalin(
    port: Int = 8080,
    jsonMapper: JavalinJackson,
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

    }.beforeMatched { ctx ->
        if(ctx.routeRoles().isEmpty()) {
            return@beforeMatched
        }
        tilgangsstyring.manage(ctx = ctx,
            routeRoles = ctx.routeRoles(),
            autentiseringskonfigurasjoner = autentiseringskonfigurasjoner,
            rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
                arbeidsgiverrettet = arbeidsgiverrettet,
                utvikler = utvikler,
                jobbsokerrettet = jobbsokerrettet,
                modiaOppfolging = modiaOppfolging,
                modiaGenerellTilgang = modiaGenerellTilgang
            ))
    }.exception(IllegalArgumentException::class.java) { e, ctx ->
        log.info("IllegalArgumentException: ${e.message}", e)
        ctx.status(400).result(e.message ?: "")
    }.exception(Exception::class.java) { e, ctx ->
        log.info("Exception: ${e.message}", e)
        ctx.status(500).result(e.message ?: "")
    }.start(port)
}

private fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}
