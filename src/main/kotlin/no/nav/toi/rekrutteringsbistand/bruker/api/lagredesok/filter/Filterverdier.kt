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

private const val MAKS_FRI_VERDILENGDE = 100

/**
 * nuqs koder komma i en listeverdi som `%2C`, så det står igjen etter at filteret er dekodet én gang. Dette er den
 * eneste kodingen inne i en verdi som frontend lager, og den eneste vi gjør om før sjekkene.
 */
private fun somIFrontend(element: String) = element.replace("%2C", ",", ignoreCase = true)

/** Tekst veilederen har skrevet eller valgt fra forslag: 1 til 100 tegn og ingenting som ligner et fødselsnummer. */
private val friVerdi = Filterregel { element ->
    val somVist = somIFrontend(element)
    somVist.trim().length in 1..MAKS_FRI_VERDILENGDE && !Fødselsnummersjekk.inneholderFødselsnummer(somVist)
}

private val stedsnavnformat = Regex("""\S.{0,99} \((Kommune|Fylke)\)""")

/** Steder fra pam-geografi, som «Bergen (Kommune)» eller «Vestland (Fylke)». */
private val stedsnavn = Filterregel { element ->
    stedsnavnformat.matches(somIFrontend(element)) && friVerdi.erGyldig(element)
}

/**
 * Liste av frie verdier. Fødselsnummersjekken ser også på verdiene satt sammen med mellomrom, så et nummer delt
 * over to verdier stoppes. Ved rensing fjernes hele parameteren hvis de gyldige verdiene til sammen ligner et
 * fødselsnummer.
 */
private fun friListe(element: Filterregel): Filterregel {
    val liste = liste(element)
    fun sammenlagtLiknerFødselsnummer(verdi: String) =
        Fødselsnummersjekk.inneholderFødselsnummer(verdi.split(',').joinToString(" ", transform = ::somIFrontend))

    return object : Filterregel {
        override fun erGyldig(verdi: String) = liste.erGyldig(verdi) && !sammenlagtLiknerFødselsnummer(verdi)
        override fun rens(verdi: String) = liste.rens(verdi)?.takeUnless(::sammenlagtLiknerFødselsnummer)
    }
}

internal val frieVerdier = friListe(friVerdi)
internal val stedsnavnliste = friListe(stedsnavn)
