package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Filteret er query-strengen frontend bygger med nuqs, og den lagres uendret. Da kan frontend bruke den direkte i
 * URL-en og sammenligne den med gjeldende filtre. Vi dekoder bare for å sjekke og rense. Bare kjente parametere og
 * verdier godtas, fordi filteret havner i URL-en. Se «Filteret lagres som query-streng» i `lagrede-sok.md` i
 * rekrutteringstreff-backend.
 */
class Filterregler(private val regler: Map<String, Filterregel>) {

    fun erGyldig(filter: String): Boolean {
        if (filter.isEmpty()) return true
        val parametere = filter.split('&').map { tilParameter(it) ?: return false }
        return parametere.map { it.first }.distinct().size == parametere.size &&
            parametere.all { (navn, verdi) -> regler[navn]?.erGyldig(verdi) == true }
    }

    /**
     * Fjerner parametere og verdier som ikke er gyldige lenger, for eksempel etter at et filter er tatt ut av søket.
     * Gyldige parametere beholdes tegn for tegn, så de fortsatt er like det frontend bygger.
     */
    fun rens(filter: String): String {
        if (filter.isEmpty()) return filter
        val beholdt = mutableSetOf<String>()
        return filter.split('&').mapNotNull { del ->
            val (navn, verdi) = tilParameter(del) ?: return@mapNotNull null
            val regel = regler[navn] ?: return@mapNotNull null
            val renset = when {
                regel.erGyldig(verdi) -> del
                else -> regel.rens(verdi)?.let { "${del.substringBefore('=')}=${URLEncoder.encode(it, Charsets.UTF_8)}" }
            }
            renset?.takeIf { beholdt.add(navn) }
        }.joinToString("&")
    }

    private fun tilParameter(del: String): Pair<String, String>? {
        val navnOgVerdi = del.split('=', limit = 2)
        if (navnOgVerdi.size != 2) return null
        val navn = dekod(navnOgVerdi[0]) ?: return null
        val verdi = dekod(navnOgVerdi[1]) ?: return null
        return navn to verdi
    }

    private fun dekod(tekst: String): String? =
        try {
            URLDecoder.decode(tekst, Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
}

interface Filterparameter {
    val navn: String
    val regel: Filterregel
}

inline fun <reified P> filterreglerFor(): Filterregler where P : Enum<P>, P : Filterparameter =
    Filterregler(enumValues<P>().associate { it.navn to it.regel })
