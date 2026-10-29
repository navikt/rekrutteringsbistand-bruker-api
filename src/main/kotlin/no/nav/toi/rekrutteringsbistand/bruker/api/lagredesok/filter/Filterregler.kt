package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Filteret er en query-streng som frontend bruker direkte i URL-en. Vi godtar bare kjente parametere og verdier,
 * så navn eller fritekst ikke kan lagres i filteret og havne i URL-en senere.
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
     * Gyldige parametere beholdes tegn for tegn, så filteret fortsatt er likt det frontend bygger. Resultatet er
     * alltid gyldig.
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
