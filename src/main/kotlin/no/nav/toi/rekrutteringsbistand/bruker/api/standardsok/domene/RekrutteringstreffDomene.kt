package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.NavAnsattRolle
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.Filterparameter
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.enAv
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.filterreglerFor
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.fylkesnumre
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.kommunenumre
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.kontornumre
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.liste

object RekrutteringstreffDomene : StandardsøkDomene(
    stinavn = "rekrutteringstreff",
    tabell = "standardsok_rekrutteringstreff",
    roller = listOf(
        NavAnsattRolle.ARBEIDSGIVER_RETTET,
        NavAnsattRolle.JOBBSOKER_RETTET,
        NavAnsattRolle.UTVIKLER,
    ),
    filterregler = filterreglerFor<RekrutteringstreffFilterparameter>(),
)

enum class RekrutteringstreffFilterparameter(
    override val navn: String,
    override val regel: (String) -> Boolean,
) : Filterparameter {
    VISNING("visning", enAv("alle", "mine", "mitt_kontor", "valgte_kontorer")),
    SORTERING("sortering", enAv("sist_oppdaterte", "nyeste", "eldste")),
    STATUSER("statuser", liste(enAv("UTKAST", "PUBLISERT", "FULLFØRT", "AVLYST", "SLETTET"))),
    PUBLISERT_STATUSER("publisertStatuser", liste(enAv("ÅPEN_FOR_SØKERE", "SVARFRIST_PASSERT"))),
    FYLKER("fylker", fylkesnumre),
    KOMMUNER("kommuner", kommunenumre),
    KONTORER("kontorer", kontornumre),
}
