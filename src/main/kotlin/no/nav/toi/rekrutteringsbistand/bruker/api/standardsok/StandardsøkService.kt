package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.database.StandardsøkRepository
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.database.medLåstNavIdent
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.StandardsøkDomene
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.Standardsøkdomener
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.utlop.Utløpsregler
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.sql.DataSource

/** En veileder har høyst ett standardsøk per domene. Uten merket søk er vanlig søk standard. */
class StandardsøkService(
    private val dataSource: DataSource,
    private val repository: StandardsøkRepository,
    private val metrikker: StandardsøkMetrikker,
    private val clock: Clock,
) {

    fun hentAlle(domene: StandardsøkDomene, navIdent: String): List<StandardsøkDto> {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.connection.use { c -> repository.hentAktive(c, domene, navIdent, utløpsgrense) }
        val collator = Collator.getInstance(Locale.forLanguageTag("nb-NO"))
        return søk
            .sortedWith(
                compareByDescending<Standardsøk> { it.erStandard }
                    .thenComparing({ it.navn }, collator)
                    .thenBy { it.id },
            )
            .map { it.tilDto(idag) }
    }

    fun opprett(domene: StandardsøkDomene, navIdent: String, input: GyldigStandardsøkInput): StandardsøkDto {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val (søk, slettedeUtløpte) = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            val slettedeUtløpte = repository.slettUtløpteForNavIdent(c, domene, navIdent, utløpsgrense)
            val antall = repository.antall(c, domene, navIdent)
            if (antall >= MAKS_ANTALL_SØK) throw StandardsøkFeil.ForMangeSøk()
            val søk = Standardsøk(
                id = UUID.randomUUID(),
                navIdent = navIdent,
                navn = input.navn,
                erStandard = false,
                filter = input.filter,
                fritekst = input.fritekst,
                sistAktiv = idag,
            )
            repository.opprett(c, domene, søk)
            søk to slettedeUtløpte
        }
        metrikker.slettet(domene, Sletteårsak.UTLØP, slettedeUtløpte)
        metrikker.lagret(domene, Lagringstype.OPPRETTET)
        return søk.tilDto(idag)
    }

    fun erstatt(domene: StandardsøkDomene, navIdent: String, id: UUID, input: GyldigStandardsøkInput): StandardsøkDto {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.connection.use { c ->
            repository.erstatt(c, domene, navIdent, id, input, idag, utløpsgrense)
        } ?: throw StandardsøkFeil.IkkeFunnet()
        metrikker.lagret(domene, Lagringstype.ERSTATTET)
        return søk.tilDto(idag)
    }

    fun settSomStandard(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val endret = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            val forrige = repository.hentStandardId(c, domene, navIdent)
            repository.fjernStandardmerke(c, domene, navIdent)
            val merket = repository.merkSomStandard(c, domene, navIdent, id, idag, utløpsgrense)
            if (merket == 0) throw StandardsøkFeil.IkkeFunnet()
            forrige != id
        }
        if (endret) metrikker.standardValgt(domene, Standardvalgtype.LAGRET_SØK)
    }

    fun velgVanligSøkSomStandard(domene: StandardsøkDomene, navIdent: String) {
        val fjernet = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            repository.fjernStandardmerke(c, domene, navIdent)
        }
        if (fjernet > 0) metrikker.standardValgt(domene, Standardvalgtype.VANLIG_SØK)
    }

    fun slett(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        val slettedeUtløpte = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            if (repository.slett(c, domene, navIdent, id, utløpsgrense) == 0) throw StandardsøkFeil.IkkeFunnet()
            repository.slettUtløpteForNavIdent(c, domene, navIdent, utløpsgrense)
        }
        metrikker.slettet(domene, Sletteårsak.VEILEDER)
        metrikker.slettet(domene, Sletteårsak.UTLØP, slettedeUtløpte)
    }

    fun registrerBruk(domene: StandardsøkDomene, navIdent: String, id: UUID): StandardsøkDto {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.connection.use { c ->
            val søk = repository.hentAktiv(c, domene, navIdent, id, utløpsgrense) ?: throw StandardsøkFeil.IkkeFunnet()
            val alleredeBruktIdag = !søk.sistAktiv.isBefore(idag)
            if (alleredeBruktIdag) return@use søk
            val oppdatert = repository.oppdaterSistAktiv(c, domene, navIdent, id, idag, utløpsgrense)
            if (oppdatert == 0) throw StandardsøkFeil.IkkeFunnet()
            søk.copy(sistAktiv = idag)
        }
        return søk.tilDto(idag)
    }

    fun slettUtløpte(): Map<StandardsøkDomene, Int> {
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        return Standardsøkdomener.alle.associateWith { domene ->
            dataSource.connection.use { c -> repository.slettUtløpte(c, domene, utløpsgrense) }
                .also { metrikker.slettet(domene, Sletteårsak.UTLØP, it) }
        }
    }

    private fun idag(): LocalDate = LocalDate.now(clock)

    private fun Standardsøk.tilDto(idag: LocalDate) = StandardsøkDto(
        id = id,
        navn = navn,
        erStandard = erStandard,
        filter = filter,
        fritekst = fritekst,
        sistAktiv = sistAktiv,
        slettes = Utløpsregler.slettes(sistAktiv),
        visSlettevarsel = Utløpsregler.visSlettevarsel(sistAktiv, idag),
    )
}
