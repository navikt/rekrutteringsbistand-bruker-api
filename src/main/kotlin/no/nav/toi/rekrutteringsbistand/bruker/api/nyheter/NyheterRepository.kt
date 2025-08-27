package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

class NyheterRepository(private val dataSource: DataSource) {
    companion object {
        const val TABLE = "nyheter"
        const val COL_ID = "id"
        const val COL_NYHET_ID = "nyhetId"
        const val COL_TITTEL = "tittel"
        const val COL_INNHOLD = "innhold"
        const val COL_OPPRETTET_DATO = "opprettetDato"
        const val COL_OPPRETTET_AV = "opprettetAv"
        const val COL_SIST_ENDRET_DATO = "sistEndretDato"
        const val COL_SIST_ENDRET_AV = "sistEndretAv"
    }

    fun lagreNyhet(nyhet: Nyhet) {
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
          insert into $TABLE ($COL_NYHET_ID, $COL_TITTEL, $COL_INNHOLD, $COL_OPPRETTET_DATO, $COL_OPPRETTET_AV, $COL_SIST_ENDRET_DATO, $COL_SIST_ENDRET_AV)
            values(?, ?, ?, ?, ?, ?, ?)
            on conflict($COL_NYHET_ID) do update 
            set $COL_TITTEL= EXCLUDED.$COL_TITTEL, 
                $COL_INNHOLD= EXCLUDED.$COL_INNHOLD,
                ${COL_SIST_ENDRET_DATO}=EXCLUDED.${COL_SIST_ENDRET_DATO},
                ${COL_SIST_ENDRET_AV}=EXCLUDED.${COL_SIST_ENDRET_AV}
            returning id   
        """.trimIndent()
            ).use { ps ->
                ps.setObject(1, UUID.randomUUID())
                ps.setString(2, nyhet.tittel)
                ps.setString(3, nyhet.innhold)
                ps.setTimestamp(4, Timestamp.from(LocalDateTime.now().atZone(ZoneId.of("Europe/Oslo")).toInstant()))
                ps.setString(5, nyhet.opprettetAv)
                ps.setTimestamp(6, Timestamp.from(LocalDateTime.now().atZone(ZoneId.of("Europe/Oslo")).toInstant()))
                ps.setString(7, nyhet.sistEndretAv)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong("id") else error("Lagring av nyhet feilet") }
            }
        }
    }
}