package no.nav.toi.rekrutteringsbistand.bruker.api

import io.javalin.security.RouteRole
import java.util.UUID

enum class NavAnsattRolle {
    JOBBSOKER_RETTET,
    ARBEIDSGIVER_RETTET,
    UTVIKLER,
    MODIA_OPPFOLGING,
    MODIA_GENERELL_TILGANG
}

enum class Tilgangsrolle: RouteRole {
    UBESKYTTET,
    BESKYTTET
}


/*
    Holder på UUID-ene som brukes for å identifisere roller i Azure AD.
    Det er ulik spesifikasjon for dev og prod.
 */
data class RolleUuidSpesifikasjon(
    private val arbeidsgiverrettet: UUID,
    private val utvikler: UUID,
    private val jobbsokerrettet: UUID,
    private val modiaOppfolging: UUID,
    private val modiaGenerellTilgang: UUID
) {
    private fun rolleForUuid(uuid: UUID): NavAnsattRolle? = when (uuid) {
        jobbsokerrettet -> NavAnsattRolle.JOBBSOKER_RETTET
        arbeidsgiverrettet -> NavAnsattRolle.ARBEIDSGIVER_RETTET
        utvikler -> NavAnsattRolle.UTVIKLER
        modiaOppfolging -> NavAnsattRolle.MODIA_OPPFOLGING
        modiaGenerellTilgang -> NavAnsattRolle.MODIA_GENERELL_TILGANG
        else -> { log.warn("Ukjent rolle-UUID: $uuid"); null }
    }

    fun rollerForUuider(uuider: Collection<UUID>): Set<NavAnsattRolle> = uuider.mapNotNull(::rolleForUuid).toSet()
}