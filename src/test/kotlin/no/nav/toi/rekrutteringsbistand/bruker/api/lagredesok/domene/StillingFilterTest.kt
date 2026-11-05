package no.nav.toi.rekrutteringsbistand.bruker.api.lagredesok.domene

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StillingFilterTest {

    private val regler = LagredeSøkDomene.STILLING.filterregler

    /**
     * Filtrene med alle verdiene frontend kan lagre, slik `byggLagretSøkFilter` bygger dem: sorterte nøkler og
     * verdier, `%2C` mellom verdiene og `+` for mellomrom. Kontrakttesten i `tests/stilling/lagrede-sok.spec.ts`
     * i rekrutteringsbistand-frontend har de samme strengene. Får frontend en ny verdi, feiler testen der.
     * Legg da verdien til i `StillingFilterparameter` og her, og deploy bruker-api før frontend.
     */
    private val filtreFraFrontend = listOf(
        "portefolje=visMine",
        "portefolje=mittKontor",
        "portefolje=arbeidsplassen&utenOppdrag=true",
        "sortering=utl%C3%B8psdato",
        "sortering=mestRelevant",
        "statuser=",
        listOf(
            "fylker=03%2C46",
            "inkludering=" + listOf(
                "INKLUDERING",
                "PRIORITERT_M%C3%85LGRUPPE",
                "STATLIG_INKLUDERINGSDUGNAD",
                "TILTAK_ELLER_VIRKEMIDDEL",
            ).joinToString("%2C"),
            "inkluderingUnderkategori=" + listOf(
                "INKLUDERING__ARBEIDSMILJ%C3%98",
                "INKLUDERING__ARBEIDSTID",
                "INKLUDERING__FYSISK",
                "INKLUDERING__GRUNNLEGGENDE",
                "PRIORITERT_M%C3%85LGRUPPE__HULL_I_CV_EN",
                "PRIORITERT_M%C3%85LGRUPPE__KOMMER_FRA_LAND_UTENFOR_E%C3%98S",
                "PRIORITERT_M%C3%85LGRUPPE__LITE_ELLER_INGEN_ARBEIDSERFARING",
                "PRIORITERT_M%C3%85LGRUPPE__LITE_ELLER_INGEN_UTDANNING",
                "PRIORITERT_M%C3%85LGRUPPE__SENIORER_OVER_45",
                "PRIORITERT_M%C3%85LGRUPPE__UNGE_UNDER_30",
                "TILTAK_ELLER_VIRKEMIDDEL__L%C3%86RLINGPLASS",
                "TILTAK_ELLER_VIRKEMIDDEL__L%C3%98NNSTILSKUDD",
                "TILTAK_ELLER_VIRKEMIDDEL__MENTORTILSKUDD",
            ).joinToString("%2C"),
            "kommuner=0301%2C4601",
            "statuser=" + listOf(
                "Fullf%C3%B8rt",
                "Ikke+publisert",
                "Stengt+for+s%C3%B8kere",
                "Utl%C3%B8pt+-+Stengt+for+s%C3%B8kere",
                "%C3%85pen+for+s%C3%B8kere",
            ).joinToString("%2C"),
            "stillingskategori=" + listOf(
                "ARBEIDSTRENING",
                "FORMIDLING",
                "JOBBMESSE",
                "REKRUTTERINGSTREFF_FORMIDLING",
                "STILLING",
            ).joinToString("%2C"),
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
    fun `godtar alle filterparametere i stillingssøket med URL-kodede verdier`() {
        val filter = listOf(
            "portefolje=mittKontor",
            "sortering=utl%C3%B8psdato",
            "statuser=%C3%85pen%20for%20s%C3%B8kere%2CUtl%C3%B8pt%20-%20Stengt%20for%20s%C3%B8kere",
            "stillingskategori=STILLING%2CJOBBMESSE",
            "inkludering=STATLIG_INKLUDERINGSDUGNAD",
            "inkluderingUnderkategori=PRIORITERT_M%C3%85LGRUPPE__HULL_I_CV_EN",
            "fylker=46",
            "kommuner=4601",
        ).joinToString("&")
        assertThat(regler.erGyldig(filter)).isTrue()
    }

    @Test
    fun `godtar utenOppdrag bare med verdien true`() {
        assertThat(regler.erGyldig("portefolje=arbeidsplassen&utenOppdrag=true")).isTrue()
        listOf(
            "utenOppdrag=false",
            "utenOppdrag=TRUE",
            "utenOppdrag=",
            "utenOppdrag=ja",
            "utenOppdrag=true%2Ctrue",
            "utenOppdrag=true%00",
            "utenOppdrag=true+",
            "utenOppdrag=true&utenOppdrag=true",
            "utenOppdrag=true&uten%4Fppdrag=true",
        ).forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
        assertThat(regler.rens("portefolje=arbeidsplassen&utenOppdrag=false")).isEqualTo("portefolje=arbeidsplassen")
    }

    @Test
    fun `avviser parametere som ikke skal lagres`() {
        listOf("fritekst=kokk", "side=2", "brukStandardsok=true", "modal=x").forEach {
            assertThat(regler.erGyldig(it)).`as`(it).isFalse()
        }
    }

    @Test
    fun `avviser gamle verdier og parametere fra andre søk`() {
        assertThat(regler.erGyldig("statuser=publisert%2Cutl%C3%B8pt")).isFalse()
        assertThat(regler.erGyldig("portefolje=alle")).isFalse()
        assertThat(regler.erGyldig("visning=mine")).isFalse()
    }
}
