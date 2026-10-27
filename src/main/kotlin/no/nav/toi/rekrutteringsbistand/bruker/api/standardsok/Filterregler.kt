package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import java.net.URLDecoder

private const val MAKS_VERDIER_PER_PARAMETER = 50

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

private fun enAv(vararg verdier: String): (String) -> Boolean = { it in verdier }

/** Kommaseparert liste der alle verdiene må være gyldige. Tom liste er lov, for eksempel `statuser=`. */
private fun liste(gyldig: (String) -> Boolean): (String) -> Boolean = { verdi ->
    if (verdi.isEmpty()) true
    else verdi.split(',').let { verdier -> verdier.size <= MAKS_VERDIER_PER_PARAMETER && verdier.all(gyldig) }
}

private val tosifret = Regex("""\d{2}""")
private val firesifret = Regex("""\d{4}""")

enum class TreffFilterParameter(val navn: String, val regel: (String) -> Boolean) {
    VISNING("visning", enAv("alle", "mine", "mitt_kontor", "valgte_kontorer")),
    SORTERING("sortering", enAv("sist_oppdaterte", "nyeste", "eldste")),
    STATUSER("statuser", liste(enAv("UTKAST", "PUBLISERT", "FULLFØRT", "AVLYST", "SLETTET"))),
    PUBLISERT_STATUSER("publisertStatuser", liste(enAv("ÅPEN_FOR_SØKERE", "SVARFRIST_PASSERT"))),
    FYLKER("fylker", liste(tosifret::matches)),
    KOMMUNER("kommuner", liste(firesifret::matches)),
    KONTORER("kontorer", liste(firesifret::matches)),
}

val rekrutteringstreffFilterregler = Filterregler(
    TreffFilterParameter.entries.associate { it.navn to it.regel }
)
