package no.nav.toi.rekrutteringsbistand.bruker.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplicationContextTest : TestRunningApplication() {

    private fun opprydningstråderForLagredeSøk(): Set<Thread> =
        Thread.getAllStackTraces().keys.filter { it.name == "lagrede-sok-opprydning" }.toSet()

    @Test
    fun `stoppApp stopper opprydningsjobben for lagrede søk`() {
        val ikkeLederElection = object : LeaderElectionInterface {
            override fun isLeader() = false
        }
        val context = ApplicationContext(appCtx.env, ikkeLederElection)
        val tråderFør = opprydningstråderForLagredeSøk()

        context.lagredeSøkOpprydningsjobb.start()
        val nyeTråder = opprydningstråderForLagredeSøk() - tråderFør
        assertThat(nyeTråder).hasSize(1)

        context.stoppApp()

        nyeTråder.forEach { it.join(2_000) }
        assertThat(nyeTråder).noneMatch { it.isAlive }
    }
}
