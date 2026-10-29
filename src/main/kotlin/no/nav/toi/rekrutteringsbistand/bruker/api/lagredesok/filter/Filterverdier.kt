package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter

private const val MAKS_VERDIER_PER_PARAMETER = 50

/**
 * Regel for verdien til én filterparameter. [rens] gir verdien uten ugyldige deler, eller `null` hvis ingenting
 * gyldig er igjen. Da fjernes parameteren, og frontend gir filteret standardverdien.
 */
fun interface Filterregel {
    fun erGyldig(verdi: String): Boolean
    fun rens(verdi: String): String? = verdi.takeIf(::erGyldig)
}

internal fun enAv(vararg verdier: String) = Filterregel { it in verdier }

/** Kommaseparert liste der alle verdiene må være gyldige. Tom liste er lov, for eksempel `statuser=`. */
internal fun liste(element: Filterregel) = object : Filterregel {
    override fun erGyldig(verdi: String) =
        verdi.isEmpty() || verdi.split(',').let { it.size <= MAKS_VERDIER_PER_PARAMETER && it.all(element::erGyldig) }

    override fun rens(verdi: String): String? {
        if (verdi.isEmpty()) return verdi
        val gyldige = verdi.split(',').filter(element::erGyldig).take(MAKS_VERDIER_PER_PARAMETER)
        return gyldige.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}

private val tosifret = Regex("""\d{2}""")
private val firesifret = Regex("""\d{4}""")

internal val fylkesnumre = liste { tosifret.matches(it) }
internal val kommunenumre = liste { firesifret.matches(it) }
internal val kontornumre = liste { firesifret.matches(it) }
