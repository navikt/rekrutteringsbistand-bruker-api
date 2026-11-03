package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Filterparameter
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Filterregel
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.enAv
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.fylkesnumre
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.kommunenumre
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.kontornumre
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.liste

enum class RekrutteringstreffFilterparameter(
    override val navn: String,
    override val regel: Filterregel,
) : Filterparameter {
    VISNING("visning", enAv("alle", "mine", "mitt_kontor", "valgte_kontorer")),
    SORTERING("sortering", enAv("sist_oppdaterte", "nyeste", "eldste")),
    STATUSER("statuser", liste(enAv("UTKAST", "PUBLISERT", "FULLFØRT", "AVLYST", "SLETTET"))),
    PUBLISERT_STATUSER("publisertStatuser", liste(enAv("ÅPEN_FOR_SØKERE", "SVARFRIST_PASSERT"))),
    FYLKER("fylker", fylkesnumre),
    KOMMUNER("kommuner", kommunenumre),
    KONTORER("kontorer", kontornumre),
}
