package no.nav.toi.rekrutteringsbistand.bruker.api.nyheter

import no.nav.toi.rekrutteringsbistand.bruker.api.startApp

abstract class TestRunningApplication {

    companion object {
        const val lokalUrlBase = "http://localhost:8080"

        @JvmStatic
        val appCtx = TestApplicationContext(env)
        // Testene kjører opprydningen selv med kjør(), så den planlagte jobben ikke sletter testdata underveis.
        val javalin = appCtx.startApp(startOpprydningsjobb = false)
    }

}

