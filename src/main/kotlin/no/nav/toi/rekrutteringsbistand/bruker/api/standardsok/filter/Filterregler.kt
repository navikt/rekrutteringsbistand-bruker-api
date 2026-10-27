package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter

import java.net.URLDecoder

/**
 * Filteret er en query-streng som frontend bruker direkte i URL-en. Vi godtar bare kjente parametere og verdier,
 * så navn eller fritekst ikke kan lagres i filteret og havne i URL-en senere.
 */
class Filterregler(private val gyldigeVerdier: Map<String, (String) -> Boolean>) {

    fun erGyldig(filter: String): Boolean {
        if (filter.isEmpty()) return true
        val parametere = filter.split('&').map { tilParameter(it) ?: return false }
        return parametere.map { it.first }.distinct().size == parametere.size &&
            parametere.all { (navn, verdi) -> gyldigeVerdier[navn]?.invoke(verdi) == true }
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

/** En tillatt parameter i filteret. Hvert domene har en enum med sine parametere. */
interface Filterparameter {
    val navn: String
    val regel: (String) -> Boolean
}

inline fun <reified P> filterreglerFor(): Filterregler where P : Enum<P>, P : Filterparameter =
    Filterregler(enumValues<P>().associate { it.navn to it.regel })
