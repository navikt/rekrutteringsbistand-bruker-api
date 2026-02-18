package no.nav.toi.rekrutteringsbistand.bruker.api.tilbakemeldinger

import com.fasterxml.jackson.annotation.JsonValue

enum class TilbakemeldingKategori(@JsonValue val verdi: String) {
    REKRUTTERINGSTREFF("rekrutteringstreff"),
    STILLINGSOPPDRAG("stillingsoppdrag"),
    ETTERREGISTRERINGER("etterregistreringer"),
    JOBBSOKER("jobbsøker"),
    ANNET("annet");

    companion object {
        fun fraVerdi(verdi: String): TilbakemeldingKategori =
            entries.firstOrNull { it.verdi == verdi } ?: ANNET
    }
}

enum class TilbakemeldingStatus {
    NY,
    VURDERING,
    AVVIST,
    FULLFORT
}
