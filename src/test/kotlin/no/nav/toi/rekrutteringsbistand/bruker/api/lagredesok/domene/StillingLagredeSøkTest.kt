package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkKomponenttest

class StillingLagredeSøkTest : LagredeSøkKomponenttest(LagredeSøkDomene.STILLING) {

    override val filtre = "statuser=%C3%85pen%20for%20s%C3%B8kere" to "sortering=utl%C3%B8psdato"
}
