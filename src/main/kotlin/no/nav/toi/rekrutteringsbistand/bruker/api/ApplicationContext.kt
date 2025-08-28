package no.nav.toi.rekrutteringsbistand.bruker.api


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.*
import javax.sql.DataSource

@Suppress("MemberVisibilityCanBePrivate")
open class ApplicationContext(envInn: Map<String, String>) {

    val env: Map<String, String> by lazy {envInn}

    val dataSource = createDataSource()

    val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))

    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT).also { registry ->
        ClassLoaderMetrics().bindTo(registry)
        JvmMemoryMetrics().bindTo(registry)
        JvmGcMetrics().bindTo(registry)
        JvmThreadMetrics().bindTo(registry)
        UptimeMetrics().bindTo(registry)
        ProcessorMetrics().bindTo(registry)
        LogbackMetrics().bindTo(registry)
    }

    val naisController = NaisController(prometheusRegistry)

    private fun getEnv(key: String): String =
        env[key] ?: throw NullPointerException("Det finnes ingen miljøvariabel med navn [$key]")

    private fun createDataSource(): DataSource =
        HikariConfig().apply {
            val base = getEnv("NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_JDBC_URL")
            jdbcUrl = "$base&reWriteBatchedInserts=true"
            username = getEnv("NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_USERNAME")
            password = getEnv("NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_PASSWORD")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 4
            minimumIdle = 1
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            initializationFailTimeout = 5_000
            validate()
        }.let(::HikariDataSource)
}