package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

class TilbakemeldingerRepository(private val dataSource: DataSource) {
    companion object {
        const val TABLE = "tilbakemeldinger"
        const val COL_ID = "id"
        const val COL_NAVN = "navn"
        const val COL_TILBAKEMELDING = "tilbakemelding"
        const val COL_DATO = "dato"
        const val COL_STATUS = "status"
        const val COL_TRELLO_LENKE = "trelloLenke"
        const val COL_KATEGORI = "kategori"
        const val COL_URL = "url"
    }

    fun opprett(tilbakemelding: Tilbakemelding): Tilbakemelding =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO $TABLE ($COL_ID, $COL_NAVN, $COL_TILBAKEMELDING, $COL_DATO, $COL_STATUS, $COL_TRELLO_LENKE, $COL_KATEGORI, $COL_URL)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING $COL_ID, $COL_NAVN, $COL_TILBAKEMELDING, $COL_DATO, $COL_STATUS, $COL_TRELLO_LENKE, $COL_KATEGORI, $COL_URL;
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, tilbakemelding.id)
                ps.setString(2, tilbakemelding.navn)
                ps.setString(3, tilbakemelding.tilbakemelding)
                ps.setTimestamp(4, Timestamp.from(LocalDateTime.now().atZone(ZoneId.of("Europe/Oslo")).toInstant()))
                ps.setString(5, tilbakemelding.status.name)
                ps.setString(6, tilbakemelding.trelloLenke)
                ps.setString(7, tilbakemelding.kategori.verdi)
                ps.setString(8, tilbakemelding.url)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.toTilbakemelding() else error("Opprettelse av tilbakemelding feilet")
                }
            }
        }

    fun hentSide(side: Int, antallPerSide: Int = 25): Pair<List<Tilbakemelding>, Int> {
        val offset = (side - 1) * antallPerSide
        return dataSource.connection.use { c ->
            val totalt = c.prepareStatement("SELECT COUNT(*) FROM $TABLE").use { ps ->
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val liste = mutableListOf<Tilbakemelding>()
            c.prepareStatement(
                """
                SELECT $COL_ID, $COL_NAVN, $COL_TILBAKEMELDING, $COL_DATO, $COL_STATUS, $COL_TRELLO_LENKE, $COL_KATEGORI, $COL_URL
                FROM $TABLE
                ORDER BY $COL_DATO DESC
                LIMIT ? OFFSET ?
                """.trimIndent()
            ).use { ps ->
                ps.setInt(1, antallPerSide)
                ps.setInt(2, offset)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        liste.add(rs.toTilbakemelding())
                    }
                }
            }
            liste to totalt
        }
    }

    fun oppdater(id: UUID, request: TilbakemeldingOppdaterRequest): Tilbakemelding =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                UPDATE $TABLE
                SET $COL_KATEGORI = ?, $COL_TRELLO_LENKE = ?, $COL_STATUS = ?
                WHERE $COL_ID = ?
                RETURNING $COL_ID, $COL_NAVN, $COL_TILBAKEMELDING, $COL_DATO, $COL_STATUS, $COL_TRELLO_LENKE, $COL_KATEGORI, $COL_URL;
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, request.kategori.verdi)
                ps.setString(2, request.trelloLenke)
                ps.setString(3, request.status.name)
                ps.setObject(4, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.toTilbakemelding() else error("Oppdatering av tilbakemelding feilet")
                }
            }
        }

    fun slett(id: UUID) =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                DELETE FROM $TABLE WHERE $COL_ID = ?
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, id)
                ps.executeUpdate()
            }
        }

    private fun ResultSet.toTilbakemelding(): Tilbakemelding =
        Tilbakemelding(
            id = getObject(COL_ID, UUID::class.java),
            navn = getString(COL_NAVN),
            tilbakemelding = getString(COL_TILBAKEMELDING),
            dato = getTimestamp(COL_DATO).toLocalDateTime(),
            status = TilbakemeldingStatus.valueOf(getString(COL_STATUS)),
            trelloLenke = getString(COL_TRELLO_LENKE),
            kategori = TilbakemeldingKategori.fraVerdi(getString(COL_KATEGORI)),
            url = getString(COL_URL),
        )
}
