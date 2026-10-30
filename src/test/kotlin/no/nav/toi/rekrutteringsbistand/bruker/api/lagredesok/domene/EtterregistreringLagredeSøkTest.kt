package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.LagredeSøkKomponenttest

class EtterregistreringLagredeSøkTest : LagredeSøkKomponenttest(LagredeSøkDomene.ETTERREGISTRERING) {

    override val filtre = "fylker=46" to "sortering=utl%C3%B8psdato"
}
