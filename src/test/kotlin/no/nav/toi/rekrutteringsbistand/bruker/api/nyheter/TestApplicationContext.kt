package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.rekrutteringsbistand.bruker.api.ApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

class TestApplicationContext(
    private val localEnv: MutableMap<String, String>,
    val localPostgres: Any = PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["DB_HOST"] = localConfig.host
            localEnv["DB_PORT"] = localConfig.getMappedPort(5432).toString()
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_BRUKER_API_REKRUTTERINGSBISTAND_BRUKER_API_JDBC_URL"] = localConfig.jdbcUrl
        }
): ApplicationContext(localEnv) {
    val mockOauth2Server = MockOAuth2Server().also { server ->
        server.start()
        localEnv["AZURE_OPENID_CONFIG_JWKS_URI"] = server.jwksUrl("azuread").toString()
        localEnv["AZURE_APP_WELL_KNOWN_URL"] = server.wellKnownUrl("azuread").toString()
    }
}