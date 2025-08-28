package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import java.time.LocalDateTime
import java.util.UUID

data class Nyhet (
    val nyhetId: UUID? = null,
    val tittel: String,
    val innhold: String,
    val opprettetDato: LocalDateTime? = null,
    val opprettetAv: String,
    val sistEndretDato: LocalDateTime? = null,
    val sistEndretAv: String,
)

data class NyhetDtoRequest (
    val tittel: String,
    val innhold: String,
)

data class NyhetDtoResponse (
    val nyhetId: UUID,
    val tittel: String,
    val innhold: String,
    val opprettetDato: LocalDateTime,
)