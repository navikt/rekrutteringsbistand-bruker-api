package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

/**
 * Ren SQL mot domenetabellene. Servicen eier transaksjonene og sender inn tilkoblingen.
 * `grense` er seneste utløpte `sistAktiv` fra [Utløpsregler.sisteUtløpteSistAktiv]. Aktive søk har `sistAktiv > grense`.
 * Alle spørringer mot ett søk har både `id` og `navIdent` i WHERE, så ingen kan lese eller endre andres søk.
 */
class StandardsøkRepository {

    private val kolonner = "id, navIdent, navn, erStandard, filter, fritekst, sistAktiv"

    fun hentAktive(c: Connection, domene: StandardsøkDomene, navIdent: String, grense: LocalDate): List<Standardsøk> =
        c.prepareStatement(
            "SELECT $kolonner FROM ${domene.tabell} WHERE navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setString(1, navIdent)
            ps.setObject(2, grense)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.tilStandardsøk()) } }
        }

    fun hentAktiv(c: Connection, domene: StandardsøkDomene, navIdent: String, id: UUID, grense: LocalDate): Standardsøk? =
        c.prepareStatement(
            "SELECT $kolonner FROM ${domene.tabell} WHERE id = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setObject(1, id)
            ps.setString(2, navIdent)
            ps.setObject(3, grense)
            ps.executeQuery().use { rs -> if (rs.next()) rs.tilStandardsøk() else null }
        }

    /** Låser veilederens søk i domenet til transaksjonen er ferdig, på tvers av podene. */
    fun låsVeileder(c: Connection, domene: StandardsøkDomene, navIdent: String) {
        c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { ps ->
            ps.setString(1, "${domene.tabell}:$navIdent")
            ps.executeQuery().use { it.next() }
        }
    }

    fun antall(c: Connection, domene: StandardsøkDomene, navIdent: String): Int =
        c.prepareStatement("SELECT count(*) FROM ${domene.tabell} WHERE navIdent = ?").use { ps ->
            ps.setString(1, navIdent)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    fun opprett(c: Connection, domene: StandardsøkDomene, søk: Standardsøk) {
        c.prepareStatement(
            "INSERT INTO ${domene.tabell} ($kolonner) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setObject(1, søk.id)
            ps.setString(2, søk.navIdent)
            ps.setString(3, søk.navn)
            ps.setBoolean(4, søk.erStandard)
            ps.setString(5, søk.filter)
            ps.setArray(6, c.createArrayOf("text", søk.fritekst.toTypedArray()))
            ps.setObject(7, søk.sistAktiv)
            ps.executeUpdate()
        }
    }

    fun erstatt(
        c: Connection,
        domene: StandardsøkDomene,
        navIdent: String,
        id: UUID,
        input: GyldigStandardsøkInput,
        idag: LocalDate,
        grense: LocalDate,
    ): Standardsøk? =
        c.prepareStatement(
            """
            UPDATE ${domene.tabell}
            SET navn = ?, filter = ?, fritekst = ?, sistAktiv = ?
            WHERE id = ? AND navIdent = ? AND sistAktiv > ?
            RETURNING $kolonner
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, input.navn)
            ps.setString(2, input.filter)
            ps.setArray(3, c.createArrayOf("text", input.fritekst.toTypedArray()))
            ps.setObject(4, idag)
            ps.setObject(5, id)
            ps.setString(6, navIdent)
            ps.setObject(7, grense)
            ps.executeQuery().use { rs -> if (rs.next()) rs.tilStandardsøk() else null }
        }

    fun fjernStandardmerke(c: Connection, domene: StandardsøkDomene, navIdent: String) {
        c.prepareStatement(
            "UPDATE ${domene.tabell} SET erStandard = FALSE WHERE navIdent = ? AND erStandard"
        ).use { ps ->
            ps.setString(1, navIdent)
            ps.executeUpdate()
        }
    }

    fun merkSomStandard(
        c: Connection,
        domene: StandardsøkDomene,
        navIdent: String,
        id: UUID,
        idag: LocalDate,
        grense: LocalDate,
    ): Int =
        c.prepareStatement(
            "UPDATE ${domene.tabell} SET erStandard = TRUE, sistAktiv = ? WHERE id = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setObject(1, idag)
            ps.setObject(2, id)
            ps.setString(3, navIdent)
            ps.setObject(4, grense)
            ps.executeUpdate()
        }

    /**
     * Gjør det sist brukte aktive søket til standard når veilederen ikke har et aktivt standardsøk.
     * Utløpte rader må være slettet først, ellers stopper den unike indeksen oppdateringen.
     */
    fun velgStandardHvisMangler(c: Connection, domene: StandardsøkDomene, navIdent: String, grense: LocalDate): Int =
        c.prepareStatement(
            """
            UPDATE ${domene.tabell} SET erStandard = TRUE
            WHERE id = (
                SELECT id FROM ${domene.tabell}
                WHERE navIdent = ? AND sistAktiv > ?
                ORDER BY sistAktiv DESC, navn, id
                LIMIT 1
            )
            AND NOT EXISTS (SELECT 1 FROM ${domene.tabell} WHERE navIdent = ? AND erStandard)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, navIdent)
            ps.setObject(2, grense)
            ps.setString(3, navIdent)
            ps.executeUpdate()
        }

    fun oppdaterSistAktiv(
        c: Connection,
        domene: StandardsøkDomene,
        navIdent: String,
        id: UUID,
        idag: LocalDate,
        grense: LocalDate,
    ): Int =
        c.prepareStatement(
            "UPDATE ${domene.tabell} SET sistAktiv = ? WHERE id = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setObject(1, idag)
            ps.setObject(2, id)
            ps.setString(3, navIdent)
            ps.setObject(4, grense)
            ps.executeUpdate()
        }

    fun slett(c: Connection, domene: StandardsøkDomene, navIdent: String, id: UUID, grense: LocalDate): Int =
        c.prepareStatement(
            "DELETE FROM ${domene.tabell} WHERE id = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setObject(1, id)
            ps.setString(2, navIdent)
            ps.setObject(3, grense)
            ps.executeUpdate()
        }

    fun slettUtløpteForVeileder(c: Connection, domene: StandardsøkDomene, navIdent: String, grense: LocalDate): Int =
        c.prepareStatement("DELETE FROM ${domene.tabell} WHERE navIdent = ? AND sistAktiv <= ?").use { ps ->
            ps.setString(1, navIdent)
            ps.setObject(2, grense)
            ps.executeUpdate()
        }

    fun slettUtløpte(c: Connection, domene: StandardsøkDomene, grense: LocalDate): Int =
        c.prepareStatement("DELETE FROM ${domene.tabell} WHERE sistAktiv <= ?").use { ps ->
            ps.setObject(1, grense)
            ps.executeUpdate()
        }

    private fun ResultSet.tilStandardsøk() = Standardsøk(
        id = getObject("id", UUID::class.java),
        navIdent = getString("navIdent"),
        navn = getString("navn"),
        erStandard = getBoolean("erStandard"),
        filter = getString("filter"),
        fritekst = (getArray("fritekst").array as Array<*>).map { it as String },
        sistAktiv = getObject("sistAktiv", LocalDate::class.java),
    )
}
