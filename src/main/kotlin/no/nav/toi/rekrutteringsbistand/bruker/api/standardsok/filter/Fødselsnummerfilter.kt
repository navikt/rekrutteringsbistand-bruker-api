package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter

/**
 * Fjerner fødselsnumre fra tekst før lagring. Brukes på både navnet på søket og søkeordene.
 * Søket mot treffene filtreres ikke, bare det vi lagrer.
 *
 * Vi fjerner alle tall med 11 sifre (også med mellomrom eller punktum etter 6 sifre), uten å sjekke dato
 * eller kontrollsiffer. Da får vi med D-nummer, H-nummer, syntetiske nummer og tastefeil, og reglene for nye
 * fødselsnumre fra 2032 trenger ingen oppdatering. Vi fjerner heller for mye enn for lite.
 * Tall med færre eller flere sifre blir stående.
 */
object Fødselsnummerfilter {

    data class Resultat(val tekst: String, val fjernet: Boolean)

    private val elleveSifre = Regex("""(?<!\d)\d{6}[ .]?\d{5}(?!\d)""")
    private val mellomrom = Regex("""\s+""")

    fun fjern(tekst: String): Resultat =
        if (elleveSifre.containsMatchIn(tekst)) {
            Resultat(elleveSifre.replace(tekst, " ").replace(mellomrom, " ").trim(), true)
        } else {
            Resultat(tekst, false)
        }
}
