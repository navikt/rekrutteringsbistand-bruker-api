package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Filterparameter
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Filterregel
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.enAv
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.frieVerdier
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.kontornumre
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.liste
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.stedsnavnliste

/**
 * Verdiene følger `KandidatSøkQueryparam` i frontend. Parametere som ikke beskriver selve søket, som `fritekst`,
 * `side`, `sortering`, `orgenhet`, `visKandidatId` og `brukStandardsok`, lagres ikke.
 *
 * `yrke`, `sprak`, `kompetanse` og `arbeidserfaring` er tekst fra forslag, og kompetanse og arbeidserfaring godtar
 * også tekst veilederen skriver selv. De kan ikke sjekkes mot en liste, men har en lengdegrense og stoppes hvis de
 * ligner et fødselsnummer.
 *
 * Frontend sender `kontor` bare med `portefolje=valgtekontorer`, og `borDer=ja` bare med `sted`. Vi sjekker ikke
 * kombinasjonene, fordi kandidatsøket ser bort fra dem ellers.
 */
enum class KandidatFilterparameter(
    override val navn: String,
    override val regel: Filterregel,
) : Filterparameter {
    PORTEFØLJE("portefolje", enAv("minebrukere", "valgtekontorer", "minekontorer", "mittkontor", "alle")),
    KONTOR("kontor", kontornumre),
    INNSATSGRUPPE(
        "innsatsgruppe",
        liste(
            enAv(
                "STANDARD_INNSATS",
                "SITUASJONSBESTEMT_INNSATS",
                "SPESIELT_TILPASSET_INNSATS",
                "GRADERT_VARIG_TILPASSET_INNSATS",
                "VARIG_TILPASSET_INNSATS",
                "HAR_IKKE_GJELDENDE_14A_VEDTAK",
            )
        ),
    ),
    HOVEDMÅL("hovedmal", liste(enAv("SKAFFE_ARBEID", "BEHOLDE_ARBEID", "OKE_DELTAKELSE"))),
    UTDANNING("utdanning", liste(enAv("videregaende", "fagskole", "bachelor", "master", "doktorgrad"))),
    MÅLGRUPPE("malgruppe", liste(enAv("unge", "senior", "hullICv"))),
    OMFANG("omfang", liste(enAv("HELTID", "DELTID", "HELTID_OG_DELTID"))),
    FØRERKORT(
        "forerkort",
        liste(
            enAv(
                "A1 - Lett motorsykkel",
                "A2 - Mellomtung motorsykkel",
                "A - Tung motorsykkel",
                "B - Personbil",
                "B 78 - Personbil med automatgir",
                "BE - Personbil med tilhenger",
                "C1 - Lett lastebil",
                "C1E - Lett lastebil med tilhenger",
                "C - Lastebil",
                "CE - Lastebil med tilhenger",
                "D1 - Minibuss",
                "D1E - Minibuss med tilhenger",
                "D - Buss",
                "DE - Buss med tilhenger",
                "T - Traktor",
                "S - Snøscooter",
            )
        ),
    ),
    STED("sted", stedsnavnliste),
    BOR_DER("borDer", enAv("ja")),
    YRKE("yrke", frieVerdier),
    SPRÅK("sprak", frieVerdier),
    KOMPETANSE("kompetanse", frieVerdier),
    ARBEIDSERFARING("arbeidserfaring", frieVerdier),
}
