package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkKomponenttest

class StillingLagredeSøkKomponenttest : LagredeSøkKomponenttest(LagredeSøkDomene.STILLING) {

    override val gyldigFilter = "statuser=%C3%85pen%20for%20s%C3%B8kere"
    override val annetGyldigFilter = "sortering=utl%C3%B8psdato"
}
