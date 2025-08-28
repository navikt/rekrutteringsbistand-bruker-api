package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import java.sql.ResultSet
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
        const val COL_NYHET_ID = "nyhetId"
        const val COL_TITTEL = "tittel"
        const val COL_INNHOLD = "innhold"
        const val COL_OPPRETTET_DATO = "opprettetDato"
        const val COL_OPPRETTET_AV = "opprettetAv"
        const val COL_SIST_ENDRET_DATO = "sistEndretDato"
        const val COL_SIST_ENDRET_AV = "sistEndretAv"
    }

    fun lagreNyhet(nyhet: Nyhet): Nyhet =
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
            returning $COL_NYHET_ID  , $COL_TITTEL, $COL_INNHOLD, $COL_OPPRETTET_DATO, $COL_OPPRETTET_AV, $COL_SIST_ENDRET_DATO, $COL_SIST_ENDRET_AV
        """.trimIndent()
            ).use { ps ->
                ps.setObject(1, nyhet.nyhetId ?: UUID.randomUUID())
                ps.setString(2, nyhet.tittel)
                ps.setString(3, nyhet.innhold)
                ps.setTimestamp(4, Timestamp.from(LocalDateTime.now().atZone(ZoneId.of("Europe/Oslo")).toInstant()))
                ps.setString(5, nyhet.opprettetAv)
                ps.setTimestamp(6, Timestamp.from(LocalDateTime.now().atZone(ZoneId.of("Europe/Oslo")).toInstant()))
                ps.setString(7, nyhet.sistEndretAv)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toNyhet() else error("Lagring av nyhet feilet") }
            }
        }

    private fun ResultSet.toNyhet(): Nyhet =
        Nyhet(
            nyhetId = getObject(COL_NYHET_ID, UUID::class.java),
            tittel = getString(COL_TITTEL),
            innhold = getString(COL_INNHOLD),
            opprettetDato = getTimestamp(COL_OPPRETTET_DATO).toLocalDateTime(),
            opprettetAv = getString(COL_OPPRETTET_AV),
            sistEndretDato = getTimestamp(COL_SIST_ENDRET_DATO).toLocalDateTime(),
            sistEndretAv = getString(COL_SIST_ENDRET_AV),
        )
}