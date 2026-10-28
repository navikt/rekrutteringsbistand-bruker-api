package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.NavAnsattRolle
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.Filterparameter
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.enAv
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.filterreglerFor
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.fylkesnumre
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.kommunenumre
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.liste

object StillingDomene : StandardsøkDomene(
    stinavn = "stilling",
    tabell = "standardsok_stilling",
    roller = NavAnsattRolle.entries,
    filterregler = filterreglerFor<StillingFilterparameter>(),
)

/**
 * Verdiene følger stillingssøket i frontend. Parametere som ikke beskriver selve søket,
 * som `fritekst`, `side`, `brukStandardsok`, `modal` og `utenOppdrag`, lagres ikke.
 */
enum class StillingFilterparameter(
    override val navn: String,
    override val regel: (String) -> Boolean,
) : Filterparameter {
    PORTEFØLJE("portefolje", enAv("intern", "visMine", "mittKontor", "arbeidsplassen")),
    SORTERING("sortering", enAv("publiseringsdato", "utløpsdato", "mestRelevant")),
    STATUSER(
        "statuser",
        liste(enAv("Åpen for søkere", "Stengt for søkere", "Utløpt - Stengt for søkere", "Fullført", "Ikke publisert")),
    ),
    STILLINGSKATEGORI(
        "stillingskategori",
        liste(enAv("STILLING", "ARBEIDSTRENING", "JOBBMESSE", "FORMIDLING", "REKRUTTERINGSTREFF_FORMIDLING")),
    ),
    INKLUDERING(
        "inkludering",
        liste(enAv("INKLUDERING", "TILTAK_ELLER_VIRKEMIDDEL", "PRIORITERT_MÅLGRUPPE", "STATLIG_INKLUDERINGSDUGNAD")),
    ),
    INKLUDERING_UNDERKATEGORI(
        "inkluderingUnderkategori",
        liste(
            enAv(
                "INKLUDERING__ARBEIDSTID",
                "INKLUDERING__FYSISK",
                "INKLUDERING__ARBEIDSMILJØ",
                "INKLUDERING__GRUNNLEGGENDE",
                "TILTAK_ELLER_VIRKEMIDDEL__LØNNSTILSKUDD",
                "TILTAK_ELLER_VIRKEMIDDEL__MENTORTILSKUDD",
                "TILTAK_ELLER_VIRKEMIDDEL__LÆRLINGPLASS",
                "PRIORITERT_MÅLGRUPPE__UNGE_UNDER_30",
                "PRIORITERT_MÅLGRUPPE__SENIORER_OVER_45",
                "PRIORITERT_MÅLGRUPPE__KOMMER_FRA_LAND_UTENFOR_EØS",
                "PRIORITERT_MÅLGRUPPE__HULL_I_CV_EN",
                "PRIORITERT_MÅLGRUPPE__LITE_ELLER_INGEN_UTDANNING",
                "PRIORITERT_MÅLGRUPPE__LITE_ELLER_INGEN_ARBEIDSERFARING",
            )
        ),
    ),
    FYLKER("fylker", fylkesnumre),
    KOMMUNER("kommuner", kommunenumre),
}
