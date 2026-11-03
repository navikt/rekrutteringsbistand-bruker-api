package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Filterregler
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.filterreglerFor

/**
 * Domenene for lagrede søk. Enumen er det eneste stedet som bestemmer gyldige domener. Databasen har ingen sjekk,
 * så et nytt domene krever ingen migrering. Alle domener er åpne for alle rollene i bruker-api.
 *
 * [stinavn] brukes i stien, som label i metrikker og som verdi i kolonnen `domene`. Endres den, må radene i
 * databasen få ny verdi.
 */
enum class LagredeSøkDomene(
    val stinavn: String,
    val filterregler: Filterregler,
) {
    REKRUTTERINGSTREFF(
        stinavn = "rekrutteringstreff",
        filterregler = filterreglerFor<RekrutteringstreffFilterparameter>(),
    ),
    STILLING(
        stinavn = "stilling",
        filterregler = filterreglerFor<StillingFilterparameter>(),
    ),
    ETTERREGISTRERING(
        stinavn = "etterregistrering",
        filterregler = filterreglerFor<EtterregistreringFilterparameter>(),
    ),
    ;

    override fun toString() = stinavn

    companion object {
        fun fraStinavn(stinavn: String): LagredeSøkDomene? = entries.firstOrNull { it.stinavn == stinavn }
    }
}
