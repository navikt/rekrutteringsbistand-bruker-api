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
 * Opprettelse og standardbytte låser veilederens søk i domenet. Uten låsen kan to samtidige kall
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

    fun hentAlle(domene: StandardsøkDomene, navIdent: String): List<StandardsøkDto> {
        val idag = idag()
        val collator = Collator.getInstance(Locale.forLanguageTag("nb-NO"))
        return dataSource.connection.use { c ->
            repository.hentAktive(c, domene, navIdent, Utløpsregler.sisteUtløpteSistAktiv(idag))
        }
            .sortedWith(Comparator<Standardsøk> { a, b -> collator.compare(a.navn, b.navn) }.thenBy { it.id })
            .map { it.tilDto(idag) }
    }

    fun opprett(domene: StandardsøkDomene, navIdent: String, input: GyldigStandardsøkInput): StandardsøkDto {
        val idag = idag()
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = iTransaksjon { c ->
            repository.låsVeileder(c, domene, navIdent)
            val slettedeUtløpte = repository.slettUtløpteForVeileder(c, domene, navIdent, grense)
            metrikker.slettet(domene, "utlop", slettedeUtløpte)
            val antall = repository.antall(c, domene, navIdent)
            if (antall >= MAKS_ANTALL_SØK) throw StandardsøkFeil.ForMangeSøk()
            Standardsøk(
                id = UUID.randomUUID(),
                navIdent = navIdent,
                navn = input.navn,
                erStandard = antall == 0,
                filter = input.filter,
                fritekst = input.fritekst,
                sistAktiv = idag,
            ).also { repository.opprett(c, domene, it) }
        }
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

    /** Fjerner merket fra det forrige standardsøket og fornyer fristen bare for det valgte. */
    fun settSomStandard(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val idag = idag()
        iTransaksjon { c ->
            repository.låsVeileder(c, domene, navIdent)
            repository.hentAktiv(c, domene, navIdent, id, Utløpsregler.sisteUtløpteSistAktiv(idag))
                ?: throw StandardsøkFeil.IkkeFunnet()
            repository.fjernStandardmerke(c, domene, navIdent)
            repository.merkSomStandard(c, domene, navIdent, id, idag)
        }
    }

    fun slett(domene: StandardsøkDomene, navIdent: String, id: UUID) {
        val antall = dataSource.connection.use { c ->
            repository.slett(c, domene, navIdent, id, Utløpsregler.sisteUtløpteSistAktiv(idag()))
        }
        if (antall == 0) throw StandardsøkFeil.IkkeFunnet()
        metrikker.slettet(domene, "veileder")
    }

    /** Fornyer fristen før søket tas i bruk, og gir 404 hvis søket er utløpt eller ikke finnes. */
    fun registrerBruk(domene: StandardsøkDomene, navIdent: String, id: UUID): StandardsøkDto {
        val idag = idag()
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag)
        val søk = dataSource.connection.use { c ->
            repository.oppdaterSistAktivHvisEldre(c, domene, navIdent, id, idag, grense)
            repository.hentAktiv(c, domene, navIdent, id, grense)
        } ?: throw StandardsøkFeil.IkkeFunnet()
        return søk.tilDto(idag)
    }

    fun slettUtløpte(): Map<StandardsøkDomene, Int> {
        val grense = Utløpsregler.sisteUtløpteSistAktiv(idag())
        return StandardsøkDomene.entries.associateWith { domene ->
            dataSource.connection.use { c -> repository.slettUtløpte(c, domene, grense) }
                .also { metrikker.slettet(domene, "utlop", it) }
        }
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
