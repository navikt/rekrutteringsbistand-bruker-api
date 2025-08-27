package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

class NyheterRepositoryTest {

    companion object {
        private var lokalPostgres: PostgreSQLContainer<*>? = null
        private fun getLokalPostgres(): PostgreSQLContainer<*> =
            lokalPostgres ?: PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
                .withDatabaseName("dbname")
                .withUsername("username")
                .withPassword("pwd")
                .also { it.start(); lokalPostgres = it }
    }

    val dataSource: DataSource = HikariDataSource(
        HikariConfig().apply {
            val pg = getLokalPostgres()
            jdbcUrl = pg.jdbcUrl
            username = pg.username
            password = pg.password
            driverClassName = "org.postgresql.Driver"
            minimumIdle = 1
            maximumPoolSize = 10
            initializationFailTimeout = 5_000
            validate()
        }
    )
}