package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

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
 * En veileder med aktive søk har alltid nøyaktig ett standardsøk. Det første søket blir standard, og når
 * standardsøket slettes eller utløper, blir det sist brukte søket standard i stedet.
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
     * Har standardsøket utløpt før opprydningsjobben har kjørt, velges et nytt før listen returneres.
     */
    fun hentAlle(domene: StandardsøkDomene, navIdent: String): List<StandardsøkDto> {
        val idag = idag()
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val aktive = dataSource.connection.use { c -> repository.hentAktive(c, domene, navIdent, grense) }
        val søk = if (aktive.isEmpty() || aktive.any { it.erStandard }) {
            aktive
        } else {
            val (oppdatert, slettedeUtløpte) = iTransaksjon { c ->
                val slettedeUtløpte = sikreStandardsøk(c, domene, navIdent, grense)
                repository.hentAktive(c, domene, navIdent, grense) to slettedeUtløpte
            }
            metrikker.slettet(domene, "utlop", slettedeUtløpte)
            oppdatert
        }
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
        val (søk, slettedeUtløpte) = iTransaksjon { c ->
            val slettedeUtløpte = sikreStandardsøk(c, domene, navIdent, grense)
            val antall = repository.antall(c, domene, navIdent)
            if (antall >= MAKS_ANTALL_SØK) throw StandardsøkFeil.ForMangeSøk()
            val søk = Standardsøk(
                id = UUID.randomUUID(),
                navIdent = navIdent,
                navn = input.navn,
                erStandard = antall == 0,
                filter = input.filter,
                fritekst = input.fritekst,
                sistAktiv = idag,
            )
            repository.opprett(c, domene, søk)
            søk to slettedeUtløpte
        }
        metrikker.slettet(domene, "utlop", slettedeUtløpte)
        registrerLagring(domene, "opprettet", input)
        return søk.tilDto(idag, input.fødselsnummerFjernet)
    }

    fun erstatt(domene: StandardsøkDomene, navIdent: String, id: UUID, input: GyldigStandardsøkInput): StandardsøkDto {
        val idag = idag()
        val søk = dataSource.connection.use { c ->
            repository.erstatt(c, domene, navIdent, id, input, idag, Utløpsregler.sisteUtløpteSistAktiv(idag))
        } ?: throw StandardsøkFeil.IkkeFunnet()
        registrerLagring(domene, "erstattet", input)
        return søk.tilDto(idag, input.fødselsnummerFjernet)
    }

    /**
     * Fjerner merket fra det forrige standardsøket og fornyer fristen bare for det valgte.
     * Finnes ikke søket, for eksempel fordi det ble slettet samtidig, ruller vi tilbake så det forrige merket blir stående.
     */
    fun settSomStandard(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val idag = idag()
        iTransaksjon { c ->
            repository.låsVeileder(c, domene, navIdent)
            repository.fjernStandardmerke(c, domene, navIdent)
            val merket = repository.merkSomStandard(c, domene, navIdent, id, idag, Utløpsregler.sisteUtløpteSistAktiv(idag))
            if (merket == 0) throw StandardsøkFeil.IkkeFunnet()
        }
    }

    /** Sletter søket. Var det standardsøket, blir det sist brukte av de andre søkene standard. */
    fun slett(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        val slettedeUtløpte = iTransaksjon { c ->
            repository.låsVeileder(c, domene, navIdent)
            if (repository.slett(c, domene, navIdent, id, grense) == 0) throw StandardsøkFeil.IkkeFunnet()
            sikreStandardsøk(c, domene, navIdent, grense)
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
        return StandardsøkDomene.entries.associateWith { domene ->
            dataSource.connection.use { c -> repository.slettUtløpte(c, domene, grense) }
                .also { metrikker.slettet(domene, "utlop", it) }
        }
    }

    /**
     * Låser veilederen, sletter utløpte søk og velger et nytt standardsøk hvis det mangler.
     * Utløpte søk slettes først, fordi et utløpt standardsøk ellers blokkerer den unike indeksen.
     * Returnerer antall utløpte søk som ble slettet.
     */
    private fun sikreStandardsøk(c: Connection, domene: StandardsøkDomene, navIdent: String, grense: LocalDate): Int {
        repository.låsVeileder(c, domene, navIdent)
        val slettedeUtløpte = repository.slettUtløpteForVeileder(c, domene, navIdent, grense)
        repository.velgStandardHvisMangler(c, domene, navIdent, grense)
        return slettedeUtløpte
    }

    private fun idag(): LocalDate = LocalDate.now(clock)

    private fun registrerLagring(domene: StandardsøkDomene, type: String, input: GyldigStandardsøkInput) {
        metrikker.lagret(domene, type)
        if (input.fødselsnummerFjernet) metrikker.fødselsnummerFjernet(domene)
    }

    private fun Standardsøk.tilDto(idag: LocalDate, fritekstFiltrert: Boolean? = null) = StandardsøkDto(
        id = id,
        navn = navn,
        erStandard = erStandard,
        filter = filter,
        fritekst = fritekst,
        sistAktiv = sistAktiv,
        slettes = Utløpsregler.slettes(sistAktiv),
        visSlettevarsel = Utløpsregler.visSlettevarsel(sistAktiv, idag),
        fritekstFiltrert = fritekstFiltrert,
    )

    private fun <T> iTransaksjon(block: (Connection) -> T): T =
        dataSource.connection.use { c ->
            val opprinneligIsolasjon = c.transactionIsolation
            c.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            c.autoCommit = false
            try {
                block(c).also { c.commit() }
            } catch (e: Throwable) {
                try {
                    c.rollback()
                } catch (rollbackFeil: Exception) {
                    e.addSuppressed(rollbackFeil)
                }
                throw e
            } finally {
                c.autoCommit = true
                c.transactionIsolation = opprinneligIsolasjon
            }
        }
}
