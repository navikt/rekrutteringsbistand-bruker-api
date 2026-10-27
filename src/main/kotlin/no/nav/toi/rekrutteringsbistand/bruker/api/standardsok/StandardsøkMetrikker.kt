package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.StandardsøkDomene
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong

/** Målingene har bare domene og type som label, aldri Nav-ident, søkeident, navn eller søkeord. */
class StandardsøkMetrikker(private val registry: MeterRegistry, private val clock: Clock) {

    private val sistVellykketOpprydning = AtomicLong(0).also {
        registry.gauge("standardsok.opprydning.sist.vellykket.sekunder", it)
    }

    fun lagret(domene: StandardsøkDomene, type: String) =
        teller("standardsok.lagret", "domene", domene.stinavn, "type", type).increment()

    fun slettet(domene: StandardsøkDomene, årsak: String, antall: Int = 1) {
        if (antall > 0) teller("standardsok.slettet", "domene", domene.stinavn, "arsak", årsak).increment(antall.toDouble())
    }

    fun fødselsnummerFjernet(domene: StandardsøkDomene) =
        teller("standardsok.fodselsnummer.fjernet", "domene", domene.stinavn).increment()

    fun opprydningVellykket() = sistVellykketOpprydning.set(clock.instant().epochSecond)

    fun opprydningFeilet() = teller("standardsok.opprydning.feil").increment()

    private fun teller(navn: String, vararg tags: String): Counter = registry.counter(navn, *tags)
}
