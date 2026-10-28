package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter

private const val MAKS_VERDIER_PER_PARAMETER = 50

internal fun enAv(vararg verdier: String): (String) -> Boolean = { it in verdier }

/** Kommaseparert liste der alle verdiene må være gyldige. Tom liste er lov, for eksempel `statuser=`. */
internal fun liste(gyldig: (String) -> Boolean): (String) -> Boolean = { verdi ->
    if (verdi.isEmpty()) true
    else verdi.split(',').let { verdier -> verdier.size <= MAKS_VERDIER_PER_PARAMETER && verdier.all(gyldig) }
}

private val tosifret = Regex("""\d{2}""")
private val firesifret = Regex("""\d{4}""")

internal val fylkesnumre = liste(tosifret::matches)
internal val kommunenumre = liste(firesifret::matches)
internal val kontornumre = liste(firesifret::matches)
