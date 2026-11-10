package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KandidatFilterTest {

    private val regler = LagredeSøkDomene.KANDIDATSOK.filterregler

    /**
     * Filtrene med alle verdiene frontend kan lagre, slik `byggLagretSøkFilter` i `app/kandidat/_util/lagredeSøkFilter.ts`
     * bygger dem: sorterte nøkler og verdier, `%2C` mellom verdiene, `+` for mellomrom og `%252C` for komma i en
     * verdi. Kontrakttesten i `tests/kandidat/lagrede-sok.spec.ts` i rekrutteringsbistand-frontend har de samme
     * strengene. Får frontend en ny verdi, feiler testen der. Legg da verdien til i `KandidatFilterparameter` og her,
     * og deploy bruker-api før frontend.
     */
    private val filtreFraFrontend = listOf(
        "portefolje=minebrukere",
        "portefolje=minekontorer",
        "portefolje=mittkontor",
        "portefolje=alle",
        listOf(
            "arbeidserfaring=Servit%C3%B8r",
            "borDer=ja",
            "forerkort=" + listOf(
                "A+-+Tung+motorsykkel",
                "A1+-+Lett+motorsykkel",
                "A2+-+Mellomtung+motorsykkel",
                "B+-+Personbil",
                "B+78+-+Personbil+med+automatgir",
                "BE+-+Personbil+med+tilhenger",
                "C+-+Lastebil",
                "C1+-+Lett+lastebil",
                "C1E+-+Lett+lastebil+med+tilhenger",
                "CE+-+Lastebil+med+tilhenger",
                "D+-+Buss",
                "D1+-+Minibuss",
                "D1E+-+Minibuss+med+tilhenger",
                "DE+-+Buss+med+tilhenger",
                "S+-+Sn%C3%B8scooter",
                "T+-+Traktor",
            ).joinToString("%2C"),
            "hovedmal=BEHOLDE_ARBEID%2COKE_DELTAKELSE%2CSKAFFE_ARBEID",
            "innsatsgruppe=" + listOf(
                "GRADERT_VARIG_TILPASSET_INNSATS",
                "HAR_IKKE_GJELDENDE_14A_VEDTAK",
                "SITUASJONSBESTEMT_INNSATS",
                "SPESIELT_TILPASSET_INNSATS",
                "STANDARD_INNSATS",
                "VARIG_TILPASSET_INNSATS",
            ).joinToString("%2C"),
            "kompetanse=50%25+stilling%2CC%23%2CTruckf%C3%B8rerbevis+T1-T4",
            "kontor=0301%2C4601",
            "malgruppe=hullICv%2Csenior%2Cunge",
            "omfang=DELTID%2CHELTID",
            "portefolje=valgtekontorer",
            "sprak=Engelsk%2CNorsk",
            "sted=Aurskog-H%C3%B8land+%28Kommune%29%2CBergen+%28Kommune%29%2CVestland+%28Fylke%29",
            "utdanning=bachelor%2Cdoktorgrad%2Cfagskole%2Cmaster%2Cvideregaende",
            "yrke=Kokk%2CSalg%252C+butikk",
        ).joinToString("&"),
    )

    @Test
    fun `godtar filtrene frontend bygger, og lar dem være uendret ved rensing`() {
        filtreFraFrontend.forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isTrue()
            assertThat(regler.rens(it)).`as`(it).isEqualTo(it)
        }
    }

    @Test
    fun `avviser parametere som ikke skal lagres`() {
        listOf(
            "fritekst=kokk",
            "side=2",
            "sortering=nyeste",
            "ferskhet=7",
            "orgenhet=0301",
            "visKandidatId=abc",
            "kandidatFane=cv",
            "brukStandardsok=true",
        ).forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `avviser ukjente faste verdier`() {
        listOf(
            "portefolje=mine",
            "kontor=Nav+Oslo",
            "innsatsgruppe=IKKE_VURDERT",
            "forerkort=B",
            "borDer=nei",
            "borDer=true",
            "malgruppe=ungdom",
        ).forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `godtar bare steder med type fra pam-geografi`() {
        assertThat(regler.erGyldig("sted=Hamar+%28Kommune%29")).isTrue()
        listOf(
            "sted=Hamar",
            "sted=Hamar+%28Land%29",
            "sted=+%28Kommune%29",
            "sted=Hamar+%28Kommune%29+ekstra",
            "sted=Ola+${"x".repeat(100)}+%28Kommune%29",
        ).forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `avviser frie verdier som er tomme eller lengre enn 100 tegn`() {
        assertThat(regler.erGyldig("kompetanse=${"a".repeat(100)}")).isTrue()
        assertThat(regler.erGyldig("kompetanse=${"a".repeat(101)}")).isFalse()
        assertThat(regler.erGyldig("kompetanse=a%2C%2Cb")).isFalse()
        assertThat(regler.erGyldig("kompetanse=+")).isFalse()
        assertThat(regler.erGyldig("kompetanse=${(1..51).joinToString("%2C") { "k$it" }}")).isFalse()
    }

    @Test
    fun `avviser 11 sifre i frie verdier, også med mellomrom og delt over to verdier`() {
        listOf(
            "kompetanse=12345678901",
            "arbeidserfaring=123456+78901",
            "yrke=123456%2078901",
            "kompetanse=123456%2C78901",
            "kompetanse=123456+%2C78901",
            "kompetanse=123456++78901",
            "kompetanse=Ring+123456%2C78901+i+dag",
            "sted=Ola+12345678901+%28Kommune%29",
            "kompetanse=%EF%BC%91%EF%BC%92%EF%BC%93%EF%BC%94%EF%BC%95%EF%BC%96%EF%BC%97%EF%BC%98%EF%BC%99%EF%BC%90%EF%BC%91",
        ).forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `godtar tall som ikke ligner et fødselsnummer`() {
        listOf(
            "kompetanse=Java+17%2CTruckf%C3%B8rerbevis+T1-T4",
            "kompetanse=1234567890",
            "kompetanse=123456789012",
            "kompetanse=12345%2C12345",
            "kompetanse=123456%252C78901",
            "kompetanse=123456%252C+78901",
            "sprak=123456-78901",
        ).forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isTrue()
        }
    }

    @Test
    fun `rensing fjerner verdier som ikke er gyldige, og hele listen når verdiene sammen ligner et fødselsnummer`() {
        assertThat(regler.rens("kompetanse=Kokk%2C12345678901")).isEqualTo("kompetanse=Kokk")
        assertThat(regler.rens("kompetanse=123456%2C78901&borDer=ja")).isEqualTo("borDer=ja")
        assertThat(regler.rens("kompetanse=123456+%2C78901&borDer=ja")).isEqualTo("borDer=ja")
        assertThat(regler.rens("innsatsgruppe=STANDARD_INNSATS%2CUKJENT")).isEqualTo("innsatsgruppe=STANDARD_INNSATS")
    }

    // Gjelder til frontend uten «Heltid og deltid» er i prod. Når verdien fjernes, skal testen sjekke at
    // erGyldig gir false og at rens fjerner verdien.
    @Test
    fun `godtar HELTID_OG_DELTID fra frontend som ikke er oppdatert`() {
        assertThat(regler.erGyldig("omfang=DELTID%2CHELTID_OG_DELTID")).isTrue()
        assertThat(regler.rens("omfang=HELTID_OG_DELTID&borDer=ja")).isEqualTo("omfang=HELTID_OG_DELTID&borDer=ja")
    }
}
