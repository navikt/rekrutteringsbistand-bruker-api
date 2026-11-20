package no.nav.toi.rekrutteringsbistand.bruker.api.schedulerConfig

import no.nav.toi.rekrutteringsbistand.bruker.api.LeaderElectionInterface
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class FastTidspunktSchedulerTest {

    private val tidssone = ZoneId.of("UTC")
    private val schedulere = mutableListOf<FastTidspunktScheduler>()

    @AfterEach
    fun stoppAlle() {
        schedulere.forEach { it.stopp() }
    }

    private class FakeJobb(private val oppførsel: () -> Unit = {}) : ScheduledTask {
        val antallKjøringer = AtomicInteger()
        override fun kjørJobb() {
            antallKjøringer.incrementAndGet()
            oppførsel()
        }
    }

    private class FakeLeaderElection(private val isLeaderSvar: () -> Boolean) : LeaderElectionInterface {
        val antallKall = AtomicInteger()
        override fun isLeader(): Boolean {
            antallKall.incrementAndGet()
            return isLeaderSvar()
        }
    }

    private fun nyScheduler(
        jobb: ScheduledTask,
        leaderElection: LeaderElectionInterface = FakeLeaderElection { true },
        kjøretidspunkt: LocalTime = LocalTime.now(tidssone).plusHours(1),
        gjentakelse: SchedulerGjentakelse = SchedulerGjentakelse.Daglig,
    ) = FastTidspunktScheduler(leaderElection, jobb, kjøretidspunkt, tidssone, gjentakelse)
        .also { schedulere += it }

    @Test
    fun `kjører jobben når instansen er leader`() {
        val jobb = FakeJobb()

        nyScheduler(jobb).wrapJobbkjøring()

        assertThat(jobb.antallKjøringer.get()).isEqualTo(1)
    }

    @Test
    fun `kjører ikke jobben når instansen ikke er leader`() {
        val jobb = FakeJobb()
        val leader = FakeLeaderElection { false }

        nyScheduler(jobb, leader).wrapJobbkjøring()

        assertThat(leader.antallKall.get()).isEqualTo(1)
        assertThat(jobb.antallKjøringer.get()).isZero()
    }

    @Test
    fun `sjekker leader på nytt ved hver kjøring`() {
        val erLeader = AtomicBoolean(false)
        val jobb = FakeJobb()
        val scheduler = nyScheduler(jobb, FakeLeaderElection { erLeader.get() })

        scheduler.wrapJobbkjøring()
        erLeader.set(true)
        scheduler.wrapJobbkjøring()

        assertThat(jobb.antallKjøringer.get()).isEqualTo(1)
    }

    @Test
    fun `feil i leader election fanges og jobben kjøres ikke`() {
        val jobb = FakeJobb()
        val scheduler = nyScheduler(jobb, FakeLeaderElection { error("Elector nede") })

        assertThatCode { scheduler.wrapJobbkjøring() }.doesNotThrowAnyException()
        assertThat(jobb.antallKjøringer.get()).isZero()
    }

    @Test
    fun `feil i jobben fanges og blokkerer ikke neste kjøring`() {
        val jobb = FakeJobb { throw RuntimeException("Jobben feilet") }
        val scheduler = nyScheduler(jobb)

        assertThatCode { scheduler.wrapJobbkjøring() }.doesNotThrowAnyException()
        assertThatCode { scheduler.wrapJobbkjøring() }.doesNotThrowAnyException()

        assertThat(jobb.antallKjøringer.get()).isEqualTo(2)
    }

    @Test
    fun `samtidig kjøring skippes når forrige pågår`() {
        val startet = CountDownLatch(1)
        val slipp = CountDownLatch(1)
        val jobb = FakeJobb {
            startet.countDown()
            slipp.await(2, TimeUnit.SECONDS)
        }
        val scheduler = nyScheduler(jobb)

        val første = thread { scheduler.wrapJobbkjøring() }
        assertThat(startet.await(2, TimeUnit.SECONDS)).isTrue()

        scheduler.wrapJobbkjøring()
        assertThat(jobb.antallKjøringer.get()).isEqualTo(1)

        slipp.countDown()
        første.join(2000)

        scheduler.wrapJobbkjøring()
        assertThat(jobb.antallKjøringer.get()).isEqualTo(2)
    }

    @Test
    fun `jobben kjører i en tråd med navn utledet fra jobbens klassenavn`() {
        val kjørt = CountDownLatch(1)
        var trådnavn: String? = null
        val jobb = FakeJobb {
            trådnavn = Thread.currentThread().name
            kjørt.countDown()
        }

        nyScheduler(jobb, kjøretidspunkt = LocalTime.now(tidssone).plusSeconds(1)).start()

        assertThat(kjørt.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(trådnavn).isEqualTo("fake-jobb")
    }

    @Test
    fun `hver N dag krever positivt antall dager`() {
        assertThatThrownBy { SchedulerGjentakelse.HverNDag(0, LocalDate.now()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("positivt")
    }

    @Test
    fun `start to ganger kaster feil`() {
        val scheduler = nyScheduler(FakeJobb())
        scheduler.start()

        assertThatThrownBy { scheduler.start() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("allerede startet")
    }

    @Test
    fun `start etter stopp kaster feil`() {
        val scheduler = nyScheduler(FakeJobb())
        scheduler.stopp()

        assertThatThrownBy { scheduler.start() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("stoppet")
    }

    @Test
    fun `stopp før start og gjentatt stopp kaster ikke feil`() {
        val scheduler = nyScheduler(FakeJobb())

        assertThatCode {
            scheduler.stopp()
            scheduler.stopp()
        }.doesNotThrowAnyException()
    }
}