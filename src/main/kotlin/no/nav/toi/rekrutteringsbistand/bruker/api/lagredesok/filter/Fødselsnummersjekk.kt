package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter

import java.text.Normalizer

/**
 * Finner tekst som ligner et fødselsnummer, så vi kan avvise navn og søkeord før lagring.
 * Søket mot treffene sjekkes ikke, bare det vi lagrer. Frontend har samme regel i `components/lagrede-søk/fødselsnummer.ts`.
 *
 * Vi avviser alle tall med 11 sifre, uten å sjekke dato eller kontrollsiffer. Da får vi med D-nummer, H-nummer,
 * syntetiske nummer, tastefeil og fødselsnumre etter reglene fra 2032. Vi avviser heller for mye enn for lite.
 * Mellom to sifre kan det stå ett skilletegn (mellomrom, hardt mellomrom, tab, punktum, bindestrek eller skråstrek),
 * så `123456 78901` og `12 34 56 789 01` også stoppes. NFKC gjør sifre i full bredde om til vanlige sifre.
 * Tall med flere enn 11 sifre på rad stoppes ikke.
 *
 * Sjekken er et sikkerhetsnett mot at noen skriver et fødselsnummer ved et uhell, ikke mot bevisst omgåelse.
 * Skilletegnene står eksplisitt, fordi `\s` matcher ulike tegn i Kotlin og JavaScript.
 */
object Fødselsnummersjekk {

    private val elleveSifre = Regex("""(?<!\d)\d(?:[ \t\u00A0\u202F.\-/]?\d){10}(?!\d)""")

    fun inneholderFødselsnummer(tekst: String): Boolean =
        elleveSifre.containsMatchIn(Normalizer.normalize(tekst, Normalizer.Form.NFKC))
}
