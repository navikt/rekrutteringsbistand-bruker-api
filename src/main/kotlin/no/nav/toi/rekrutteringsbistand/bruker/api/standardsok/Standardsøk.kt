package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.StandardsøkDomene
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.filter.Fødselsnummerfilter
import java.time.LocalDate
import java.util.UUID

const val MAKS_ANTALL_SØK = 10
const val MAKS_NAVNELENGDE = 25
const val MAKS_FILTERLENGDE = 2000
const val MAKS_ANTALL_SØKEORD = 10
const val MAKS_SØKEORDLENGDE_TOTALT = 200

data class LagreStandardsøkDto(
    val navn: String? = null,
    val filter: String? = null,
    val fritekst: List<String?>? = null,
)

data class StandardsøkDto(
    val id: UUID,
    val navn: String,
    val erStandard: Boolean,
    val filter: String,
    val fritekst: List<String>,
    val sistAktiv: LocalDate,
    val slettes: LocalDate,
    val visSlettevarsel: Boolean,
    val fritekstFiltrert: Boolean? = null,
)

data class Standardsøk(
    val id: UUID,
    val navIdent: String,
    val navn: String,
    val erStandard: Boolean,
    val filter: String,
    val fritekst: List<String>,
    val sistAktiv: LocalDate,
)

/** Input etter validering og fjerning av fødselsnumre. Bare denne typen kan lagres. */
class GyldigStandardsøkInput private constructor(
    val navn: String,
    val filter: String,
    val fritekst: List<String>,
    val fødselsnummerFjernet: Boolean,
) {
    companion object {
        fun fra(dto: LagreStandardsøkDto, domene: StandardsøkDomene): GyldigStandardsøkInput {
            val navn = dto.navn?.trim().orEmpty()
            if (navn.isEmpty() || navn.length > MAKS_NAVNELENGDE) {
                throw StandardsøkFeil.UgyldigInput("Navnet må ha mellom 1 og $MAKS_NAVNELENGDE tegn")
            }

            val filter = dto.filter.orEmpty()
            if (filter.length > MAKS_FILTERLENGDE || !domene.filterregler.erGyldig(filter)) {
                throw StandardsøkFeil.UgyldigInput("Filteret er ugyldig")
            }

            val søkeord = dto.fritekst.orEmpty().map { it?.trim().orEmpty() }.filter { it.isNotEmpty() }
            if (søkeord.size > MAKS_ANTALL_SØKEORD || søkeord.sumOf { it.length } > MAKS_SØKEORDLENGDE_TOTALT) {
                throw StandardsøkFeil.UgyldigInput(
                    "Du kan lagre opptil $MAKS_ANTALL_SØKEORD søkeord med til sammen $MAKS_SØKEORDLENGDE_TOTALT tegn"
                )
            }

            val rensetNavn = Fødselsnummerfilter.fjern(navn)
            if (rensetNavn.tekst.isEmpty()) {
                throw StandardsøkFeil.UgyldigInput("Navnet kan ikke bare være et fødselsnummer")
            }
            val rensedeSøkeord = søkeord.map(Fødselsnummerfilter::fjern)

            return GyldigStandardsøkInput(
                navn = rensetNavn.tekst,
                filter = filter,
                fritekst = rensedeSøkeord.map { it.tekst }.filter { it.isNotEmpty() },
                fødselsnummerFjernet = rensetNavn.fjernet || rensedeSøkeord.any { it.fjernet },
            )
        }
    }
}
