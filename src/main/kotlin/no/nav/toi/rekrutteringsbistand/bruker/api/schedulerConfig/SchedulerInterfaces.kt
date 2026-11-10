package no.nav.toi.rekrutteringsbistand.bruker.api.schedulerConfig

interface ScheduledTask {
    fun kjørJobb()
}

interface Scheduler {
    fun start()
    fun stopp()
    fun wrapJobbkjøring()
}