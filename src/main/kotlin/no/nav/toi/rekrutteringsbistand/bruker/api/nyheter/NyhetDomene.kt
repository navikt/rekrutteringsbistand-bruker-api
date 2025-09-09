package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import java.time.LocalDateTime
import java.util.UUID

data class Nyhet (
    val nyhetId: UUID? = null,
    val tittel: String,
    val innhold: String,
    val opprettetDato: LocalDateTime? = null,
    val opprettetAv: String? = null,
    val sistEndretDato: LocalDateTime? = null,
    val sistEndretAv: String,
    val status: Status,
){
    fun tilNyhetDtoResponse(): NyhetDtoResponse {
        if (nyhetId != null && opprettetDato != null) {
            return NyhetDtoResponse(nyhetId = nyhetId, tittel = tittel, innhold = innhold, opprettetDato = opprettetDato)
        }
        throw RuntimeException("Kunne ikke konvertere nyhet til nyhetDtoResponse")
    }
}

data class NyhetDtoRequest (
    val tittel: String,
    val innhold: String,
){
    fun tilNyhet(nyhetId: UUID? = null, navIdent: String): Nyhet {
        return if (nyhetId != null) {
            Nyhet(nyhetId = nyhetId, tittel = tittel, innhold = innhold, sistEndretAv = navIdent, status = Status.AKTIV)
        } else {
            Nyhet(tittel = tittel, innhold = innhold, opprettetAv = navIdent, sistEndretAv = navIdent, status = Status.AKTIV)
        }
    }
}

data class NyhetDtoResponse (
    val nyhetId: UUID,
    val tittel: String,
    val innhold: String,
    val opprettetDato: LocalDateTime,
)