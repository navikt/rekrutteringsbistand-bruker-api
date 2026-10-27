package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.domene.StandardsøkDomene
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong

/** Målingene har bare domene og faste verdier som label, aldri Nav-ident, søkeident, navn eller søkeord. */
class StandardsøkMetrikker(private val registry: MeterRegistry, private val clock: Clock) {

    private val sistVellykketOpprydning = AtomicLong(0).also {
        registry.gauge("standardsok.opprydning.sist.vellykket.sekunder", it)
    }

    fun lagret(domene: StandardsøkDomene, type: String) =
        teller("standardsok.lagret", "domene", domene.stinavn, "type", type).increment()

    fun slettet(domene: StandardsøkDomene, årsak: String, antall: Int = 1) {
        if (antall > 0) teller("standardsok.slettet", "domene", domene.stinavn, "arsak", årsak).increment(antall.toDouble())
    }

    /** Telles bare når standardvalget faktisk endres, ikke når veilederen velger det som allerede er standard. */
    fun standardValgt(domene: StandardsøkDomene, valg: Standardvalgtype) =
        teller("standardsok.standard.valgt", "domene", domene.stinavn, "valg", valg.label).increment()

    fun opprydningVellykket() = sistVellykketOpprydning.set(clock.instant().epochSecond)

    fun opprydningFeilet() = teller("standardsok.opprydning.feil").increment()

    private fun teller(navn: String, vararg tags: String): Counter = registry.counter(navn, *tags)
}

enum class Standardvalgtype(val label: String) {
    LAGRET_SØK("lagret_sok"),
    VANLIG_SØK("vanlig_sok"),
}
