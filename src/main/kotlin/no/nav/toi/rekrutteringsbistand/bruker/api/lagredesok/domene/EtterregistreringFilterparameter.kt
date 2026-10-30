package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Filterparameter
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Filterregel

/**
 * Etterregistreringssøket bruker stillingssøket med færre filtre: sortering, geografi og inkludering.
 * Verdiene er de samme som i [StillingFilterparameter].
 */
enum class EtterregistreringFilterparameter(
    override val navn: String,
    override val regel: Filterregel,
) : Filterparameter {
    SORTERING(StillingFilterparameter.SORTERING),
    INKLUDERING(StillingFilterparameter.INKLUDERING),
    INKLUDERING_UNDERKATEGORI(StillingFilterparameter.INKLUDERING_UNDERKATEGORI),
    FYLKER(StillingFilterparameter.FYLKER),
    KOMMUNER(StillingFilterparameter.KOMMUNER),
    ;

    constructor(stilling: StillingFilterparameter) : this(stilling.navn, stilling.regel)
}
