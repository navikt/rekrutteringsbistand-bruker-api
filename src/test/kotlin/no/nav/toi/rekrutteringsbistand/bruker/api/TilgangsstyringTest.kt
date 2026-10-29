package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.http.Context
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TilgangsstyringTest {
    // Context brukes ikke, siden rollekravet sjekkes før noe leses fra forespørselen.
    private val ctx = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(Context::class.java)
    ) { _, _, _ -> error("Context skal ikke brukes") } as Context

    private val roller = RolleUuidSpesifikasjon(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

    @Test
    fun `endepunkt uten rolle avvises i stedet for å bli åpent`() {
        assertThatThrownBy { Tilgangsstyring().manage(ctx, emptySet(), emptyList(), roller) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `endepunkt med flere roller avvises`() {
        assertThatThrownBy {
            Tilgangsstyring().manage(ctx, setOf(Tilgangsrolle.BESKYTTET, Tilgangsrolle.UBESKYTTET), emptyList(), roller)
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
