package no.nav.toi.rekrutteringsbistand.bruker.api

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI

class LeaderElectionTest {

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply { start() }

    @AfterEach
    fun stopp() {
        server.stop(0)
    }

    private fun electorSvarer(status: Int, body: String) {
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun leaderElection(hostname: String = "pod-1") =
        LeaderElection(URI.create("http://localhost:${server.address.port}/"), hostname)

    @Test
    fun `er leader når navnet fra elector er lik hostname`() {
        electorSvarer(200, """{"name":"pod-1","last_update":"2026-11-20T12:00:00Z"}""")

        leaderElection().use { assertThat(it.isLeader()).isTrue() }
    }

    @Test
    fun `er ikke leader når navnet fra elector er en annen pod`() {
        electorSvarer(200, """{"name":"pod-2","last_update":"2026-11-20T12:00:00Z"}""")

        leaderElection().use { assertThat(it.isLeader()).isFalse() }
    }

    @Test
    fun `kaster feil når elector svarer med annen status enn 200`() {
        electorSvarer(500, "")

        leaderElection().use { leaderElection ->
            assertThatThrownBy { leaderElection.isLeader() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("500")
        }
    }

    @Test
    fun `kaster feil når elector ikke returnerer navn`() {
        electorSvarer(200, """{"last_update":"2026-11-20T12:00:00Z"}""")

        leaderElection().use { leaderElection ->
            assertThatThrownBy { leaderElection.isLeader() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("navn på leader")
        }
    }

    @Test
    fun `kaster feil når elector returnerer tomt navn`() {
        electorSvarer(200, """{"name":""}""")

        leaderElection().use { leaderElection ->
            assertThatThrownBy { leaderElection.isLeader() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("navn på leader")
        }
    }
}
