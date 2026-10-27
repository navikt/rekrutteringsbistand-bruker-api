package no.nav.toi.rekrutteringsbistand.bruker.api.standardsok.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LåsingArkitekturTest {

    @Test
    fun `advisory lock tas bare i Låsing_kt`() {
        val filerMedLås = Files.walk(Path.of("src/main/kotlin")).use { filer ->
            filer
                .filter { it.toString().endsWith(".kt") }
                .filter { Files.readString(it).contains("pg_advisory_xact_lock") }
                .map { it.fileName.toString() }
                .toList()
        }
        assertThat(filerMedLås).containsExactly("Låsing.kt")
    }
}
