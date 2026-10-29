package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.database

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.GyldigLagreSøkInput
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagretSøk
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene.LagredeSøkDomene
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

/**
 * Alle domener ligger i tabellen `lagrede_sok`, og hver spørring har `domene` i WHERE.
 * Alle spørringer mot ett søk har i tillegg både `id` og `navIdent` i WHERE, så ingen kan lese eller endre andres søk.
 */
class LagredeSøkRepository {

    private val kolonner = "id, navIdent, navn, erStandard, filter, fritekst, sistAktiv"

    fun hentAktive(c: Connection, domene: LagredeSøkDomene, navIdent: String, utløpsgrense: LocalDate): List<LagretSøk> =
        c.prepareStatement(
            "SELECT $kolonner FROM lagrede_sok WHERE domene = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setString(2, navIdent)
            ps.setObject(3, utløpsgrense)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.tilLagretSøk()) } }
        }

    fun hentAktiv(c: Connection, domene: LagredeSøkDomene, navIdent: String, id: UUID, utløpsgrense: LocalDate): LagretSøk? =
        c.prepareStatement(
            "SELECT $kolonner FROM lagrede_sok WHERE domene = ? AND id = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setObject(2, id)
            ps.setString(3, navIdent)
            ps.setObject(4, utløpsgrense)
            ps.executeQuery().use { rs -> if (rs.next()) rs.tilLagretSøk() else null }
        }

    fun antall(c: Connection, domene: LagredeSøkDomene, navIdent: String): Int =
        c.prepareStatement("SELECT count(*) FROM lagrede_sok WHERE domene = ? AND navIdent = ?").use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setString(2, navIdent)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    fun opprett(c: Connection, domene: LagredeSøkDomene, søk: LagretSøk) {
        c.prepareStatement(
            "INSERT INTO lagrede_sok (domene, $kolonner) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setObject(2, søk.id)
            ps.setString(3, søk.navIdent)
            ps.setString(4, søk.navn)
            ps.setBoolean(5, søk.erStandard)
            ps.setString(6, søk.filter)
            ps.setArray(7, c.createArrayOf("text", søk.fritekst.toTypedArray()))
            ps.setObject(8, søk.sistAktiv)
            ps.executeUpdate()
        }
    }

    fun erstatt(
        c: Connection,
        domene: LagredeSøkDomene,
        navIdent: String,
        id: UUID,
        input: GyldigLagreSøkInput,
        idag: LocalDate,
        utløpsgrense: LocalDate,
    ): LagretSøk? =
        c.prepareStatement(
            """
            UPDATE lagrede_sok
            SET navn = ?, filter = ?, fritekst = ?, sistAktiv = ?
            WHERE domene = ? AND id = ? AND navIdent = ? AND sistAktiv > ?
            RETURNING $kolonner
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, input.navn)
            ps.setString(2, input.filter)
            ps.setArray(3, c.createArrayOf("text", input.fritekst.toTypedArray()))
            ps.setObject(4, idag)
            ps.setString(5, domene.stinavn)
            ps.setObject(6, id)
            ps.setString(7, navIdent)
            ps.setObject(8, utløpsgrense)
            ps.executeQuery().use { rs -> if (rs.next()) rs.tilLagretSøk() else null }
        }

    fun hentStandardId(c: Connection, domene: LagredeSøkDomene, navIdent: String): UUID? =
        c.prepareStatement("SELECT id FROM lagrede_sok WHERE domene = ? AND navIdent = ? AND erStandard").use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setString(2, navIdent)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java) else null }
        }

    fun fjernStandardmerke(c: Connection, domene: LagredeSøkDomene, navIdent: String): Int =
        c.prepareStatement(
            "UPDATE lagrede_sok SET erStandard = FALSE WHERE domene = ? AND navIdent = ? AND erStandard"
        ).use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setString(2, navIdent)
            ps.executeUpdate()
        }

    fun merkSomStandard(
        c: Connection,
        domene: LagredeSøkDomene,
        navIdent: String,
        id: UUID,
        idag: LocalDate,
        utløpsgrense: LocalDate,
    ): Int =
        c.prepareStatement(
            """
            UPDATE lagrede_sok SET erStandard = TRUE, sistAktiv = ?
            WHERE domene = ? AND id = ? AND navIdent = ? AND sistAktiv > ?
            """.trimIndent()
        ).use { ps ->
            ps.setObject(1, idag)
            ps.setString(2, domene.stinavn)
            ps.setObject(3, id)
            ps.setString(4, navIdent)
            ps.setObject(5, utløpsgrense)
            ps.executeUpdate()
        }

    fun oppdaterSistAktiv(
        c: Connection,
        domene: LagredeSøkDomene,
        navIdent: String,
        id: UUID,
        idag: LocalDate,
        utløpsgrense: LocalDate,
    ): Int =
        c.prepareStatement(
            "UPDATE lagrede_sok SET sistAktiv = ? WHERE domene = ? AND id = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setObject(1, idag)
            ps.setString(2, domene.stinavn)
            ps.setObject(3, id)
            ps.setString(4, navIdent)
            ps.setObject(5, utløpsgrense)
            ps.executeUpdate()
        }

    fun slett(c: Connection, domene: LagredeSøkDomene, navIdent: String, id: UUID, utløpsgrense: LocalDate): Int =
        c.prepareStatement(
            "DELETE FROM lagrede_sok WHERE domene = ? AND id = ? AND navIdent = ? AND sistAktiv > ?"
        ).use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setObject(2, id)
            ps.setString(3, navIdent)
            ps.setObject(4, utløpsgrense)
            ps.executeUpdate()
        }

    fun slettUtløpteForNavIdent(c: Connection, domene: LagredeSøkDomene, navIdent: String, utløpsgrense: LocalDate): Int =
        c.prepareStatement("DELETE FROM lagrede_sok WHERE domene = ? AND navIdent = ? AND sistAktiv <= ?").use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setString(2, navIdent)
            ps.setObject(3, utløpsgrense)
            ps.executeUpdate()
        }

    fun slettUtløpte(c: Connection, domene: LagredeSøkDomene, utløpsgrense: LocalDate): Int =
        c.prepareStatement("DELETE FROM lagrede_sok WHERE domene = ? AND sistAktiv <= ?").use { ps ->
            ps.setString(1, domene.stinavn)
            ps.setObject(2, utløpsgrense)
            ps.executeUpdate()
        }

    private fun ResultSet.tilLagretSøk() = LagretSøk(
        id = getObject("id", UUID::class.java),
        navIdent = getString("navIdent"),
        navn = getString("navn"),
        erStandard = getBoolean("erStandard"),
        filter = getString("filter"),
        fritekst = (getArray("fritekst").array as Array<*>).map { it as String },
        sistAktiv = getObject("sistAktiv", LocalDate::class.java),
    )
}
