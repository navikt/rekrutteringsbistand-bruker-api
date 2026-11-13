package no.nav.toi.rekrutteringsbistand.bruker.api

abstract class TestRunningApplication {

    companion object {
        const val lokalUrlBase = "http://localhost:8080"

        @JvmStatic
        val appCtx = TestApplicationContext(env).apply {
            // Testene kjører opprydningen selv med kjør(), så den planlagte jobben ikke sletter testdata underveis.
            startApp(startOpprydningsjobb = false)
        }
    }
}

