package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Fjerner fødselsnumre fra tekst før lagring. Brukes på både navnet på søket og søkeordene.
 * Søket mot treffene filtreres ikke, bare det vi lagrer.
 *
 * Et tall fjernes når det har 11 sifre (også med mellomrom eller punktum etter 6 sifre),
 * datoen er gyldig og begge kontrollsifrene stemmer med modulus 11. Datoen kan være
 * D-nummer (dag + 40), H-nummer (måned + 40) eller syntetisk (måned + 40 eller + 80).
 * Andre tall blir stående. Reglene for nye fødselsnumre fra 2032 er ikke med.
 */
object Fødselsnummerfilter {

    data class Resultat(val tekst: String, val fjernet: Boolean)

    private val kandidat = Regex("""(?<!\d)(\d{6})[ .]?(\d{5})(?!\d)""")
    private val mellomrom = Regex("""\s+""")
    private val vekter1 = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
    private val vekter2 = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

    fun fjern(tekst: String): Resultat =
        if (kandidat.findAll(tekst).any(::erFødselsnummer)) {
            val renset = kandidat.replace(tekst) { if (erFødselsnummer(it)) " " else it.value }
            Resultat(renset.replace(mellomrom, " ").trim(), true)
        } else {
            Resultat(tekst, false)
        }

    private fun erFødselsnummer(treff: MatchResult): Boolean =
        erGyldig(treff.groupValues[1] + treff.groupValues[2])

    fun erGyldig(sifre: String): Boolean {
        if (sifre.length != 11 || !sifre.all { it in '0'..'9' }) return false
        val s = sifre.map { it - '0' }
        return kontrollsifreStemmer(s) && datoErGyldig(s)
    }

    private fun kontrollsifreStemmer(s: List<Int>): Boolean =
        kontrollsiffer(s, vekter1) == s[9] && kontrollsiffer(s, vekter2) == s[10]

    /** Returnerer null når kontrollsifferet blir 10, som aldri er gyldig. */
    private fun kontrollsiffer(s: List<Int>, vekter: IntArray): Int? {
        val sum = vekter.indices.sumOf { vekter[it] * s[it] }
        return when (val siffer = 11 - sum % 11) {
            11 -> 0
            10 -> null
            else -> siffer
        }
    }

    private fun datoErGyldig(s: List<Int>): Boolean {
        val dag = (s[0] * 10 + s[1]).let { if (it in 41..71) it - 40 else it }
        val måned = (s[2] * 10 + s[3]).let {
            when (it) {
                in 41..52 -> it - 40
                in 81..92 -> it - 80
                else -> it
            }
        }
        val år = s[4] * 10 + s[5]
        // Individnummeret avgjør århundret. Vi godtar datoen hvis den finnes i ett av dem, så vi fjerner heller for mye enn for lite.
        return listOf(1800, 1900, 2000).any { erDato(it + år, måned, dag) }
    }

    private fun erDato(år: Int, måned: Int, dag: Int): Boolean =
        try {
            LocalDate.of(år, måned, dag)
            true
        } catch (e: DateTimeException) {
            false
        }
}
