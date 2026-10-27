package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.NavAnsattRolle
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.Filterregler

/**
 * Et område med lagrede søk, som rekrutteringstreff eller stilling. Hvert domene er et objekt i egen fil
 * med sin egen tabell med lik oppbygning, sine roller og sine filterregler. Resten av standardsøk er felles.
 * Tabellnavnet kommer herfra og aldri fra input. Nye domener legges til i [Standardsøkdomener].
 */
abstract class StandardsøkDomene(
    val stinavn: String,
    val tabell: String,
    val roller: List<NavAnsattRolle>,
    val filterregler: Filterregler,
) {
    override fun toString() = stinavn
}

object Standardsøkdomener {
    val alle: List<StandardsøkDomene> = listOf(RekrutteringstreffDomene, StillingDomene)

    fun fraStinavn(stinavn: String): StandardsøkDomene? = alle.firstOrNull { it.stinavn == stinavn }
}
