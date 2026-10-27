package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.database.StandardsøkRepository
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.database.executeInTransaction
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.database.låsVeileder
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.StandardsøkDomene
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.Standardsøkdomener
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.utlop.Utløpsregler
import java.sql.Connection
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.sql.DataSource

/**
 * Regler for lagrede søk: eierskap, grensen på [MAKS_ANTALL_SØK], standardvalg og utløp.
 *
 * En veileder har høyst ett lagret standardsøk per domene. Uten et merket søk er vanlig søk standard.
 * Bare [settSomStandard] merker et søk, så et søk blir aldri standard uten at veilederen har valgt det.
 * Slettes eller utløper standardsøket, blir vanlig søk standard.
 *
 * Endringer i standardvalget og antallet låser veilederens søk i domenet. Uten låsen kan to samtidige kall
 * begge telle 9 søk og gi 11, eller gi to standardsøk. Transaksjonene kjører med READ COMMITTED,
 * så tellingen etter låsen ser det andre kall har committet mens vi ventet. Under REPEATABLE READ,
 * som er standard i poolen, ville øyeblikksbildet vært tatt før låsen og tellingen vært utdatert.
 */
class StandardsøkService(
    private val dataSource: DataSource,
    private val repository: StandardsøkRepository,
    private val metrikker: StandardsøkMetrikker,
    private val clock: Clock,
) {

    /**
     * Standardsøket kommer først. Resten sorteres etter navn med norsk alfabet og deretter etter id,
     * så menyen ikke bytter rekkefølge når et søk brukes.
     * Henting skriver aldri. Utløpte søk er skjult til de slettes.
     */
    fun hentAlle(domene: StandardsøkDomene, navIdent: String): List<StandardsøkDto> {
        val idag = idag()
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.connection.use { c -> repository.hentAktive(c, domene, navIdent, grense) }
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
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val (søk, slettedeUtløpte) = dataSource.executeInTransaction { c ->
            val slettedeUtløpte = låsOgSlettUtløpte(c, domene, navIdent, grense)
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
        metrikker.slettet(domene, "utlop", slettedeUtløpte)
        metrikker.lagret(domene, "opprettet")
        return søk.tilDto(idag)
    }

    fun erstatt(domene: StandardsøkDomene, navIdent: String, id: UUID, input: GyldigStandardsøkInput): StandardsøkDto {
        val idag = idag()
        val søk = dataSource.connection.use { c ->
            repository.erstatt(c, domene, navIdent, id, input, idag, Utløpsregler.sisteUtløpteSistAktiv(idag))
        } ?: throw StandardsøkFeil.IkkeFunnet()
        metrikker.lagret(domene, "erstattet")
        return søk.tilDto(idag)
    }

    /**
     * Fjerner merket fra det forrige standardsøket og fornyer fristen bare for det valgte.
     * Finnes ikke søket, for eksempel fordi det ble slettet samtidig, ruller vi tilbake så det forrige merket blir stående.
     */
    fun settSomStandard(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val idag = idag()
        val endret = dataSource.executeInTransaction { c ->
            c.låsVeileder(domene, navIdent)
            val forrige = repository.hentStandardId(c, domene, navIdent)
            repository.fjernStandardmerke(c, domene, navIdent)
            val merket = repository.merkSomStandard(c, domene, navIdent, id, idag, Utløpsregler.sisteUtløpteSistAktiv(idag))
            if (merket == 0) throw StandardsøkFeil.IkkeFunnet()
            forrige != id
        }
        if (endret) metrikker.standardValgt(domene, Standardvalgtype.LAGRET_SØK)
    }
    
    fun velgVanligSøkSomStandard(domene: StandardsøkDomene, navIdent: String) {
        val fjernet = dataSource.executeInTransaction { c ->
            c.låsVeileder(domene, navIdent)
            repository.fjernStandardmerke(c, domene, navIdent)
        }
        if (fjernet > 0) metrikker.standardValgt(domene, Standardvalgtype.VANLIG_SØK)
    }

    /** Sletter søket. Var det standardsøket, blir vanlig søk standard. */
    fun slett(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        val slettedeUtløpte = dataSource.executeInTransaction { c ->
            c.låsVeileder(domene, navIdent)
            if (repository.slett(c, domene, navIdent, id, grense) == 0) throw StandardsøkFeil.IkkeFunnet()
            repository.slettUtløpteForVeileder(c, domene, navIdent, grense)
        }
        metrikker.slettet(domene, "veileder")
        metrikker.slettet(domene, "utlop", slettedeUtløpte)
    }

    /**
     * Fornyer fristen før søket tas i bruk, og gir 404 hvis søket er utløpt eller ikke finnes.
     * Vi skriver bare når datoen er eldre enn i dag, så bruk gir høyst én skriving per søk i døgnet.
     */
    fun registrerBruk(domene: StandardsøkDomene, navIdent: String, id: UUID): StandardsøkDto {
        val idag = idag()
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.connection.use { c ->
            val søk = repository.hentAktiv(c, domene, navIdent, id, grense) ?: throw StandardsøkFeil.IkkeFunnet()
            if (!søk.sistAktiv.isBefore(idag)) return@use søk
            val oppdatert = repository.oppdaterSistAktiv(c, domene, navIdent, id, idag, grense)
            if (oppdatert == 0) throw StandardsøkFeil.IkkeFunnet()
            søk.copy(sistAktiv = idag)
        }
        return søk.tilDto(idag)
    }

    fun slettUtløpte(): Map<StandardsøkDomene, Int> {
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        return Standardsøkdomener.alle.associateWith { domene ->
            dataSource.connection.use { c -> repository.slettUtløpte(c, domene, grense) }
                .also { metrikker.slettet(domene, "utlop", it) }
        }
    }

    /**
     * Låser veilederen og sletter utløpte søk før tellingen, fordi [StandardsøkRepository.antall] teller alle rader.
     * Returnerer antall utløpte søk som ble slettet.
     */
    private fun låsOgSlettUtløpte(c: Connection, domene: StandardsøkDomene, navIdent: String, grense: LocalDate): Int {
        c.låsVeileder(domene, navIdent)
        return repository.slettUtløpteForVeileder(c, domene, navIdent, grense)
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
