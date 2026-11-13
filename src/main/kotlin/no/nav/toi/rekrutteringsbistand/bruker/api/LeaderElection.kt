package no.nav.toi.rekrutteringsbistand.bruker.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface LeaderElectionInterface : AutoCloseable {
    fun isLeader(): Boolean
    override fun close() {}
}

class LeaderElection(
    private val electorUri: URI,
    private val hostname: String,
) : LeaderElectionInterface {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(1))
        .build()

    override fun isLeader(): Boolean {
        val leader = hentLeader()
        log.info("Leader election: hostname=$hostname, leader=$leader")
        return hostname == leader
    }

    private fun hentLeader(): String {
        val request = HttpRequest.newBuilder(electorUri)
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Elector returnerte HTTP-status ${response.statusCode()}" }

        val leader = JacksonConfig.objectMapper.readValue(response.body(), Elector::class.java).name
        check(!leader.isNullOrBlank()) { "Elector returnerte ikke navn på leader" }
        return leader
    }

    override fun close() = httpClient.close()
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Elector(val name: String? = null)