package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

/** Lager testnumre når testen kjører. Vanlige datoer bruker det historiske året 1854. */
internal object FødselsnummerTestdata {

    private val vekter1 = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
    private val vekter2 = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

    fun lagNummer(dag: Int, måned: Int, år: Int, individer: IntRange): String =
        individer.firstNotNullOf { individ ->
            val ni = "%02d%02d%02d%03d".format(dag, måned, år % 100, individ)
            val sifre = ni.map { it - '0' }
            val k1 = kontrollsiffer(sifre, vekter1) ?: return@firstNotNullOf null
            val k2 = kontrollsiffer(sifre + k1, vekter2) ?: return@firstNotNullOf null
            "$ni$k1$k2"
        }

    private fun kontrollsiffer(sifre: List<Int>, vekter: IntArray): Int? =
        when (val rest = 11 - vekter.indices.sumOf { vekter[it] * sifre[it] } % 11) {
            11 -> 0
            10 -> null
            else -> rest
        }
}
