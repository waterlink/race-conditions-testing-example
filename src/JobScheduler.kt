import java.time.Duration

val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())

abstract class Job {
    @JobDependency
    lateinit var jobScheduler: JobScheduler

    abstract fun perform()

    fun retryIn(duration: Duration) {
        jobScheduler.rescheduleIn(duration, this)
    }
}

annotation class JobExport
annotation class JobDependency

interface JobScheduler {
    fun schedule(job: Job)
    fun rescheduleIn(duration: Duration, job: Job)
}