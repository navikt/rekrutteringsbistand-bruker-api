package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

/**
 * Feilene har faste tekster. De logges og returneres, så de skal aldri inneholde navn, filter eller søkeord.
 */
sealed class StandardsøkFeil(val status: Int, val feil: String) :
    RuntimeException(feil, null, false, false) {

    class IkkeFunnet : StandardsøkFeil(404, "Fant ikke det lagrede søket")
    class UgyldigInput(feil: String) : StandardsøkFeil(400, feil)
    class ForMangeSøk : StandardsøkFeil(409, "Du har allerede $MAKS_ANTALL_SØK lagrede søk")
    class Intern : StandardsøkFeil(500, "Noe gikk galt med lagrede søk")
}

/**
 * Brukes for å logge stacktrace uten meldingen fra det opprinnelige unntaket.
 * Meldinger fra JDBC og Jackson kan inneholde verdiene fra forespørselen.
 */
class FeilUtenMelding(opprinnelig: Throwable) :
    RuntimeException("${opprinnelig.javaClass.name} (meldingen er fjernet)") {
    init {
        stackTrace = opprinnelig.stackTrace
    }
}
