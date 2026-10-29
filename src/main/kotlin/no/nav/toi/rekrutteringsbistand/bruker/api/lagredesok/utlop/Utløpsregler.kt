package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.utlop

import java.time.LocalDate

/**
 * Et søk slettes seks kalendermåneder etter `sistAktiv`, og får varsel etter fem.
 * Finnes ikke dagen i målmåneden, brukes månedens siste dag (slik `plusMonths` virker).
 * Søket er utløpt fra og med slettedatoen. API, oppslag og opprydningsjobb bruker de samme reglene.
 */
object Utløpsregler {
    private const val MÅNEDER_TIL_SLETTING = 6L
    private const val MÅNEDER_TIL_VARSEL = 5L

    fun slettes(sistAktiv: LocalDate): LocalDate = sistAktiv.plusMonths(MÅNEDER_TIL_SLETTING)

    fun erUtløpt(sistAktiv: LocalDate, idag: LocalDate): Boolean = !idag.isBefore(slettes(sistAktiv))

    fun visSlettevarsel(sistAktiv: LocalDate, idag: LocalDate): Boolean =
        !idag.isBefore(sistAktiv.plusMonths(MÅNEDER_TIL_VARSEL)) && !erUtløpt(sistAktiv, idag)

    /**
     * Spørringene bruker `sistAktiv <= utløpsgrense` for utløpte søk og `sistAktiv > utløpsgrense` for aktive,
     * så databasen og koden er enige om hvilke søk som er utløpt.
     */
    fun sisteUtløpteSistAktiv(idag: LocalDate): LocalDate {
        var dato = idag.minusMonths(MÅNEDER_TIL_SLETTING)
        while (erUtløpt(dato.plusDays(1), idag)) {
            dato = dato.plusDays(1)
        }
        return dato
    }
}
