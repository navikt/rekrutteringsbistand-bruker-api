package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkKomponenttest

class RekrutteringstreffLagredeSøkTest : LagredeSøkKomponenttest(LagredeSøkDomene.REKRUTTERINGSTREFF) {

    override val filtre = "statuser=PUBLISERT" to "sortering=eldste"
}
