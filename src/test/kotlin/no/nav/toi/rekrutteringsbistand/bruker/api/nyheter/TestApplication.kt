package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import no.nav.toi.rekrutteringsbistand.bruker.api.startApp

val env = mutableMapOf(
    "NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_USERNAME" to "test",
    "NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_PASSWORD" to "test"
)

fun main() {
    val localAppCtx = TestApplicationContext(env)
    localAppCtx.startApp()
}