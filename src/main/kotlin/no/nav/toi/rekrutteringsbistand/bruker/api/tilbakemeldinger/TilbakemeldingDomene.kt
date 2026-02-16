package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import java.time.LocalDateTime
import java.util.UUID

data class Tilbakemelding(
    val id: UUID = UUID.randomUUID(),
    val navn: String?,
    val tilbakemelding: String,
    val dato: LocalDateTime = LocalDateTime.now(),
    val status: TilbakemeldingStatus = TilbakemeldingStatus.NY,
    val trelloLenke: String? = null,
    val kategori: TilbakemeldingKategori,
    val url: String? = null,
)

data class TilbakemeldingOpprettRequest(
    val navn: String? = null,
    val tilbakemelding: String,
    val kategori: TilbakemeldingKategori,
    val url: String? = null,
) {
    fun tilTilbakemelding(): Tilbakemelding = Tilbakemelding(
        navn = navn,
        tilbakemelding = tilbakemelding,
        kategori = kategori,
        url = url,
        trelloLenke = null,
    )
}

data class TilbakemeldingOppdaterRequest(
    val kategori: TilbakemeldingKategori,
    val trelloLenke: String?,
    val status: TilbakemeldingStatus,
)

data class TilbakemeldingResponse(
    val id: UUID,
    val navn: String?,
    val tilbakemelding: String,
    val dato: LocalDateTime,
    val status: TilbakemeldingStatus,
    val trelloLenke: String?,
    val kategori: TilbakemeldingKategori,
    val url: String?,
)

data class TilbakemeldingerPageResponse(
    val tilbakemeldinger: List<TilbakemeldingResponse>,
    val side: Int,
    val totalSider: Int,
    val totaltAntall: Int,
)

fun Tilbakemelding.tilResponse() = TilbakemeldingResponse(
    id = id,
    navn = navn,
    tilbakemelding = tilbakemelding,
    dato = dato,
    status = status,
    trelloLenke = trelloLenke,
    kategori = kategori,
    url = url,
)
