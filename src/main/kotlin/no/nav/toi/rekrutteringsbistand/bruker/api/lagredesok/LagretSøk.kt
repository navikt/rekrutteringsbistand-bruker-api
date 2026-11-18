package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene.LagredeSøkDomene
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.filter.Fødselsnummersjekk
import java.time.LocalDate
import java.util.UUID

const val MAKS_ANTALL_SØK = 10
const val MAKS_NAVNELENGDE = 25
const val MAKS_FILTERLENGDE = 2000
const val MAKS_ANTALL_SØKEORD = 20
const val MAKS_SØKEORDLENGDE_TOTALT = 400

data class LagreSøkDto(
    val navn: String? = null,
    val filter: String? = null,
    val fritekst: List<String?>? = null,
)

data class NyttNavnDto(
    val navn: String? = null,
)

data class LagretSøkDto(
    val id: UUID,
    val navn: String,
    val erStandard: Boolean,
    val filter: String,
    val fritekst: List<String>,
    val sistAktiv: LocalDate,
    val slettes: LocalDate,
    val visSlettevarsel: Boolean,
)

data class LagretSøk(
    val id: UUID,
    val navIdent: String,
    val navn: String,
    val erStandard: Boolean,
    val filter: String,
    val fritekst: List<String>,
    val sistAktiv: LocalDate,
)

/** Validert input uten fødselsnumre. Bare denne typen kan lagres. */
class GyldigLagreSøkInput private constructor(
    val navn: String,
    val filter: String,
    val fritekst: List<String>,
) {
    companion object {
        fun fra(dto: LagreSøkDto, domene: LagredeSøkDomene): GyldigLagreSøkInput {
            val navn = navnMedGyldigLengde(dto.navn)

            val filter = dto.filter.orEmpty()
            if (filter.length > MAKS_FILTERLENGDE || !domene.filterregler.erGyldig(filter)) {
                throw LagredeSøkFeil.UgyldigInput("Filteret er ugyldig")
            }

            val søkeord = dto.fritekst.orEmpty().map { it?.trim().orEmpty() }.filter { it.isNotEmpty() }
            if (søkeord.size > MAKS_ANTALL_SØKEORD || søkeord.sumOf { it.length } > MAKS_SØKEORDLENGDE_TOTALT) {
                throw LagredeSøkFeil.UgyldigInput(
                    "Du kan lagre opptil $MAKS_ANTALL_SØKEORD søkeord med til sammen $MAKS_SØKEORDLENGDE_TOTALT tegn"
                )
            }

            if (Fødselsnummersjekk.inneholderFødselsnummer(navn) || søkeord.any(Fødselsnummersjekk::inneholderFødselsnummer)) {
                throw LagredeSøkFeil.UgyldigInput("Navnet eller søkeordene inneholder noe som kan være et fødselsnummer")
            }

            return GyldigLagreSøkInput(navn = navn, filter = filter, fritekst = søkeord)
        }
    }
}

/** Validert nytt navn uten fødselsnummer. Bare navnet endres, ikke filter eller søkeord. */
class GyldigNyttNavn private constructor(val navn: String) {
    companion object {
        fun fra(dto: NyttNavnDto): GyldigNyttNavn {
            val navn = navnMedGyldigLengde(dto.navn)
            if (Fødselsnummersjekk.inneholderFødselsnummer(navn)) {
                throw LagredeSøkFeil.UgyldigInput("Navnet inneholder noe som kan være et fødselsnummer")
            }
            return GyldigNyttNavn(navn)
        }
    }
}

private fun navnMedGyldigLengde(navn: String?): String {
    val trimmet = navn?.trim().orEmpty()
    if (trimmet.isEmpty() || trimmet.length > MAKS_NAVNELENGDE) {
        throw LagredeSøkFeil.UgyldigInput("Navnet må ha mellom 1 og $MAKS_NAVNELENGDE tegn")
    }
    return trimmet
}
