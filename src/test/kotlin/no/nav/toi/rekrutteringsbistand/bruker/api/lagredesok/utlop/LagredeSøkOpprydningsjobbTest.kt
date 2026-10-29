package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.utlop

import io.javalin.Javalin
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.toi.rekrutteringsbistand.bruker.api.kobleLagredeSøkOpprydningsjobbTilLivssyklus
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkMetrikker
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkService
import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.database.LagredeSøkRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

class LagredeSøkOpprydningsjobbTest {

    @Test
    fun `start er idempotent og close stanser fremtidige kjøringer`() {
        val kjøringer = AtomicInteger()
        val førsteKjøring = CountDownLatch(1)
        val andreKjøring = CountDownLatch(1)
        val jobb = nyJobb(Duration.ofMillis(25), Duration.ofMillis(250)) {
            when (kjøringer.incrementAndGet()) {
                1 -> førsteKjøring.countDown()
                2 -> andreKjøring.countDown()
            }
        }

        try {
            jobb.start()
            jobb.start()

            assertThat(førsteKjøring.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(andreKjøring.await(100, TimeUnit.MILLISECONDS)).isFalse()

            jobb.close()
            jobb.close()
            jobb.start()

            assertThat(andreKjøring.await(400, TimeUnit.MILLISECONDS)).isFalse()
            assertThat(kjøringer.get()).isEqualTo(1)
        } finally {
            jobb.close()
        }
    }

    @Test
    fun `Javalin stop stenger opprydningsjobben`() {
        val kjøringer = AtomicInteger()
        val kjøring = CountDownLatch(1)
        val jobb = nyJobb(Duration.ofMillis(500), Duration.ofDays(1)) {
            kjøringer.incrementAndGet()
            kjøring.countDown()
        }
        val app = Javalin.create {
            it.kobleLagredeSøkOpprydningsjobbTilLivssyklus(jobb)
        }.start(0)
        var appStoppet = false

        try {
            jobb.start()
            app.stop()
            appStoppet = true

            assertThat(kjøring.await(800, TimeUnit.MILLISECONDS)).isFalse()
            assertThat(kjøringer.get()).isZero()
        } finally {
            jobb.close()
            if (!appStoppet) app.stop()
        }
    }

    private fun nyJobb(
        forsinkelse: Duration,
        intervall: Duration,
        vedTilkobling: () -> Unit,
    ): LagredeSøkOpprydningsjobb {
        val klokke = Clock.systemUTC()
        val metrikker = LagredeSøkMetrikker(SimpleMeterRegistry(), klokke)
        val dataSource = Proxy.newProxyInstance(
            LagredeSøkOpprydningsjobbTest::class.java.classLoader,
            arrayOf(DataSource::class.java),
        ) { _, method, _ ->
            if (method.name == "getConnection") {
                vedTilkobling()
                throw SQLException("Test connection")
            }
            throw UnsupportedOperationException(method.name)
        } as DataSource
        val service = LagredeSøkService(dataSource, LagredeSøkRepository(), metrikker, klokke)

        return LagredeSøkOpprydningsjobb(service, metrikker, forsinkelse, intervall)
    }
}
