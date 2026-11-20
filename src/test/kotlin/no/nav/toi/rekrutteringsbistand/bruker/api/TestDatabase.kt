package no.nav.toi.rekrutteringsbistand.bruker.api

import no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger.Tilbakemelding
import no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger.TilbakemeldingKategori
import no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger.TilbakemeldingerRepository
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

class TestDatabase(private val dataSource: DataSource) {

    private val tilbakemeldingerRepository = TilbakemeldingerRepository(dataSource)

    fun opprettTilbakemelding(navn: String?, dato: LocalDateTime? = null, tilbakemelding: String = "Tekst fra $navn"): Tilbakemelding {
        val lagret = tilbakemeldingerRepository.opprett(
            Tilbakemelding(navn = navn, tilbakemelding = tilbakemelding, kategori = TilbakemeldingKategori.REKRUTTERINGSTREFF)
        )
        if (dato == null) return lagret
        settTilbakemeldingDato(lagret.id, dato)
        return hentTilbakemelding(lagret.id)
    }

    private fun settTilbakemeldingDato(id: UUID, dato: LocalDateTime) {
        dataSource.connection.use { c ->
            c.prepareStatement("UPDATE tilbakemeldinger SET dato = ? WHERE id = ?").use { ps ->
                ps.setTimestamp(1, Timestamp.from(dato.atZone(ZoneId.of("Europe/Oslo")).toInstant()))
                ps.setObject(2, id)
                ps.executeUpdate()
            }
        }
    }

    fun hentAlleTilbakemeldinger(): List<Tilbakemelding> =
        tilbakemeldingerRepository.hentSide(1, antallPerSide = 1000, visAlle = true).first

    fun hentTilbakemelding(id: UUID): Tilbakemelding =
        hentAlleTilbakemeldinger().single { it.id == id }

    fun slettAlleTilbakemeldinger() {
        dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM tilbakemeldinger").use { it.executeUpdate() }
        }
    }
}