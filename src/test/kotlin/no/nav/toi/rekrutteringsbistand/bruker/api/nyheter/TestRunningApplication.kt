package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import no.nav.toi.rekrutteringsbistand.bruker.api.startApp

abstract class TestRunningApplication {

    companion object {
        const val lokalUrlBase = "http://localhost:8080"

        @JvmStatic
        val appCtx = TestApplicationContext(env)
        val javalin = appCtx.startApp()
    }

}

