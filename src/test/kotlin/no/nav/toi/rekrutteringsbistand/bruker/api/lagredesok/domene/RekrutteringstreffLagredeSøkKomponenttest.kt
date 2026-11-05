package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkKomponenttest

class RekrutteringstreffLagredeSøkKomponenttest : LagredeSøkKomponenttest(LagredeSøkDomene.REKRUTTERINGSTREFF) {

    override val gyldigFilter = "statuser=PUBLISERT"
    override val annetGyldigFilter = "sortering=eldste"
}
