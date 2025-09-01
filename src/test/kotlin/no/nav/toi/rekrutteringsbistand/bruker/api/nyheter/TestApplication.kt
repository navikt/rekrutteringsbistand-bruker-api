package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import no.nav.toi.rekrutteringsbistand.bruker.api.startApp

val env = mutableMapOf(
    "NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_USERNAME" to "test",
    "NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_PASSWORD" to "test",
    "REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET" to "3b85ba1c-74d6-47c5-a5ae-face7818b212", // tilfeldig generert UUID
    "REKRUTTERINGSBISTAND_UTVIKLER" to "796c475b-44bc-4d6e-81bc-f422765e3be0", // tilfeldig generert UUID
    "AZURE_APP_CLIENT_ID" to "testClientId",
    "AZURE_OPENID_CONFIG_ISSUER" to "azuread",
    "AZURE_OPENID_CONFIG_JWKS_URI" to "azuread",
)

fun main() {
    val localAppCtx = TestApplicationContext(env)
    localAppCtx.startApp()
}