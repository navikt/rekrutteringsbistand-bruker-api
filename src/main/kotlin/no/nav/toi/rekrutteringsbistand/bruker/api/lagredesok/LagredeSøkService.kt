package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.database.LagredeSøkRepository
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.database.iTransaksjon
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.database.medLåstNavIdent
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene.LagredeSøkDomene
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.utlop.Utløpsregler
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.sql.DataSource

/** En veileder har høyst ett standardsøk per domene. Uten merket søk er vanlig søk standard. */
class LagredeSøkService(
    private val dataSource: DataSource,
    private val repository: LagredeSøkRepository,
    private val metrikker: LagredeSøkMetrikker,
    private val clock: Clock,
) {

    fun hentAlle(domene: LagredeSøkDomene, navIdent: String): List<LagretSøkDto> {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.connection.use { c -> repository.hentAktive(c, domene, navIdent, utløpsgrense) }
        val collator = Collator.getInstance(Locale.forLanguageTag("nb-NO"))
        return søk
            .sortedWith(
                compareByDescending<LagretSøk> { it.erStandard }
                    .thenComparing({ it.navn }, collator)
                    .thenBy { it.id },
            )
            .map { it.tilDto(domene, idag) }
    }

    fun opprett(domene: LagredeSøkDomene, navIdent: String, input: GyldigLagreSøkInput): LagretSøkDto {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            // Utløpte søk teller ikke. Opprydningsjobben sletter dem.
            val antall = repository.antallAktive(c, domene, navIdent, utløpsgrense)
            if (antall >= MAKS_ANTALL_SØK) throw LagredeSøkFeil.ForMangeSøk()
            val søk = LagretSøk(
                id = UUID.randomUUID(),
                navIdent = navIdent,
                navn = input.navn,
                erStandard = false,
                filter = input.filter,
                fritekst = input.fritekst,
                sistAktiv = idag,
            )
            repository.opprett(c, domene, søk)
            søk
        }
        metrikker.lagret(domene, Lagringstype.OPPRETTET)
        return søk.tilDto(domene, idag)
    }

    fun erstatt(domene: LagredeSøkDomene, navIdent: String, id: UUID, input: GyldigLagreSøkInput): LagretSøkDto {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            repository.erstatt(c, domene, navIdent, id, input, idag, utløpsgrense)
        } ?: throw LagredeSøkFeil.IkkeFunnet()
        metrikker.lagret(domene, Lagringstype.ERSTATTET)
        return søk.tilDto(domene, idag)
    }

    /**
     * Flytter standardvalget til søket med [id], eller til vanlig søk når [id] er `null`.
     * Finnes ikke søket, kastes [LagredeSøkFeil.IkkeFunnet], og transaksjonen rulles tilbake, så forrige valg står.
     */
    fun velgStandard(domene: LagredeSøkDomene, navIdent: String, id: UUID?) {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val endret = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            val forrige = repository.hentStandardId(c, domene, navIdent, utløpsgrense)
            repository.fjernStandardmerke(c, domene, navIdent)
            if (id != null && repository.merkSomStandard(c, domene, navIdent, id, idag, utløpsgrense) == 0) {
                throw LagredeSøkFeil.IkkeFunnet()
            }
            forrige != id
        }
        if (endret) {
            metrikker.standardValgt(domene, if (id == null) Standardvalgtype.VANLIG_SØK else Standardvalgtype.LAGRET_SØK)
        }
    }

    fun slett(domene: LagredeSøkDomene, navIdent: String, id: UUID) {
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        dataSource.medLåstNavIdent(domene, navIdent) { c ->
            if (repository.slett(c, domene, navIdent, id, utløpsgrense) == 0) throw LagredeSøkFeil.IkkeFunnet()
        }
        metrikker.slettet(domene, Sletteårsak.VEILEDER)
    }

    fun registrerBruk(domene: LagredeSøkDomene, navIdent: String, id: UUID): LagretSøkDto {
        val idag = idag()
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.medLåstNavIdent(domene, navIdent) { c ->
            val søk = repository.hentAktiv(c, domene, navIdent, id, utløpsgrense) ?: throw LagredeSøkFeil.IkkeFunnet()
            val alleredeBruktIdag = !søk.sistAktiv.isBefore(idag)
            if (alleredeBruktIdag) return@medLåstNavIdent søk
            val oppdatert = repository.oppdaterSistAktiv(c, domene, navIdent, id, idag, utløpsgrense)
            if (oppdatert == 0) throw LagredeSøkFeil.IkkeFunnet()
            søk.copy(sistAktiv = idag)
        }
        return søk.tilDto(domene, idag)
    }

    /**
     * Hvert domene ryddes for seg, så en feil i ett domene ikke stopper de andre.
     * `READ COMMITTED` gjør at jobben kan kjøre samtidig på flere pods: sletter to pods samme rad,
     * hopper den siste over raden i stedet for å feile med `40001`, som den ville gjort under `REPEATABLE READ`.
     */
    fun slettUtløpte(): Map<LagredeSøkDomene, Result<Int>> {
        val utløpsgrense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        return LagredeSøkDomene.entries.associateWith { domene ->
            try {
                val antall = dataSource.iTransaksjon { c -> repository.slettUtløpte(c, domene, utløpsgrense) }
                metrikker.slettet(domene, Sletteårsak.UTLØP, antall)
                Result.success(antall)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun idag(): LocalDate = LocalDate.now(clock)

    /** Filteret renses, så søk med filtre som er tatt ut av søket, fortsatt kan brukes og få nytt navn. */
    private fun LagretSøk.tilDto(domene: LagredeSøkDomene, idag: LocalDate) = LagretSøkDto(
        id = id,
        navn = navn,
        erStandard = erStandard,
        filter = domene.filterregler.rens(filter),
        fritekst = fritekst,
        sistAktiv = sistAktiv,
        slettes = Utløpsregler.slettes(sistAktiv),
        visSlettevarsel = Utløpsregler.visSlettevarsel(sistAktiv, idag),
    )
}
