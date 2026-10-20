package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import no.nav.toi.rekrutteringsbistand.bruker.api.NavAnsattRolle

/**
 * Hvert domene har sin egen tabell med lik oppbygning. Tabellnavnet kommer herfra og aldri fra input.
 */
enum class StandardsøkDomene(
    val stinavn: String,
    val tabell: String,
    val roller: List<NavAnsattRolle>,
    val filterregler: Filterregler,
) {
    REKRUTTERINGSTREFF(
        stinavn = "rekrutteringstreff",
        tabell = "standardsok_rekrutteringstreff",
        roller = listOf(
            NavAnsattRolle.ARBEIDSGIVER_RETTET,
            NavAnsattRolle.JOBBSOKER_RETTET,
            NavAnsattRolle.UTVIKLER,
        ),
        filterregler = rekrutteringstreffFilterregler,
    );

    companion object {
        fun fraStinavn(stinavn: String): StandardsøkDomene? = entries.firstOrNull { it.stinavn == stinavn }
    }
}
