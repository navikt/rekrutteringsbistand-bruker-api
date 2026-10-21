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
        val brukteParametere = mutableSetOf<String>()
        for (del in filter.split('&')) {
            val navnOgVerdi = del.split('=', limit = 2)
            if (navnOgVerdi.size != 2) return false
            val navn = dekod(navnOgVerdi[0]) ?: return false
            val verdi = dekod(navnOgVerdi[1]) ?: return false
            val regel = gyldigeVerdier[navn] ?: return false
            if (!brukteParametere.add(navn)) return false
            if (!regel(verdi)) return false
        }
        return true
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

val rekrutteringstreffFilterregler = Filterregler(
    mapOf(
        "visning" to enAv("alle", "mine", "mitt_kontor", "valgte_kontorer"),
        "sortering" to enAv("sist_oppdaterte", "nyeste", "eldste"),
        "statuser" to liste(enAv("UTKAST", "PUBLISERT", "FULLFØRT", "AVLYST", "SLETTET")),
        "publisertStatuser" to liste(enAv("ÅPEN_FOR_SØKERE", "SVARFRIST_PASSERT")),
        "fylker" to liste(tosifret::matches),
        "kommuner" to liste(firesifret::matches),
        "kontorer" to liste(firesifret::matches),
    )
)
