package no.nav.toi.rekrutteringsbistand.bruker.api.brukerinnstillinger

import javax.sql.DataSource

class BrukerinnstillingerRepository(private val dataSource: DataSource) {
    fun hentBrukerinnstillinger(navIdent: String): BrukerinnstillingerDto =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select antallLesteNyheter, darkMode, windowMode, tekststorrelse
                from brukerinnstillinger
                where navIdent = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, navIdent)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        BrukerinnstillingerDto(
                            antallLesteNyheter = resultSet.getInt("antallLesteNyheter"),
                            darkMode = resultSet.getBoolean("darkMode"),
                            windowMode = resultSet.getBoolean("windowMode"),
                            tekststørrelse = resultSet.getString("tekststorrelse"),
                        )
                    } else {
                        BrukerinnstillingerDto()
                    }
                }
            }
        }

    fun lagreBrukerinnstillinger(navIdent: String, innstillinger: BrukerinnstillingerDto) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into brukerinnstillinger (navIdent, antallLesteNyheter, darkMode, windowMode, tekststorrelse)
                values (?, ?, ?, ?, ?)
                on conflict (navIdent) do update
                set antallLesteNyheter = excluded.antallLesteNyheter,
                    darkMode = excluded.darkMode,
                    windowMode = excluded.windowMode,
                    tekststorrelse = excluded.tekststorrelse
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, navIdent)
                statement.setInt(2, innstillinger.antallLesteNyheter)
                statement.setBoolean(3, innstillinger.darkMode)
                statement.setBoolean(4, innstillinger.windowMode)
                statement.setString(5, innstillinger.tekststørrelse)
                statement.executeUpdate()
            }
        }
    }
}