import OperationState.IN_PROGRESS
import OperationState.SUCCEEDED
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrationRaceConditionTest {
    private val breakPoints = RacingBreakPoints()

    private val serviceBrokerClientDelegate = mock(ServiceBrokerClient::class.java)
    private val serviceBrokerClient = RacingBreakPoints_ServiceBrokerClient(
        delegate = serviceBrokerClientDelegate,
        breakPoints = breakPoints
    )

    private val serviceRepositoryDelegate = InMemoryServiceRepository()
    private val serviceRepository = RacingBreakPoints_ServiceRepository(
        delegate = serviceRepositoryDelegate,
        breakPoints = breakPoints
    )

    private val jobScheduler = NonReschedulingThreadingJobScheduler(
        serviceRepository = serviceRepository,
        serviceBrokerClient = serviceBrokerClient,
        racingBreakPoints = breakPoints
    )

    private val provisionService = ProvisionService(
        serviceRepository = serviceRepository,
        serviceBrokerClient = serviceBrokerClient,
        jobScheduler = jobScheduler
    )

    private val deprovisionService = DeprovisionService(
        serviceRepository = serviceRepository,
        serviceBrokerClient = serviceBrokerClient,
        jobScheduler = jobScheduler
    )

    @Test
    fun `provision and deprovision while succeeded fetch call`() {
        breakPoints.expectOrder(
            listOf(
                "[main] serviceRepository.transaction() starts".asBreakPoint(),
                "[main] jobScheduler.schedule()".asBreakPoint(),
                "[main] serviceRepository.transaction() ends".asBreakPoint(),

                "[Thread-0] serviceBrokerClient.fetchLastOperation() starts".asBreakPoint(),

                "[main] jobScheduler.schedule()".asBreakPoint(),
//                "[Thread-1] serviceRepository.transaction() starts".asBreakPoint(),
                "[Thread-1] serviceRepository.update()".asBreakPoint(),
//                "[Thread-1] serviceRepository.transaction() ends".asBreakPoint(),
                "[Thread-1] serviceBrokerClient.deprovision()".asBreakPoint(),

                "[Thread-0] serviceBrokerClient.fetchLastOperation() ends".asBreakPoint(),
                "[Thread-0] serviceRepository.transaction() starts".asBreakPoint(),

                // THIS SHOULD NOT HAPPEN, but it does!
//                "[Thread-0] serviceRepository.delete()".asBreakPoint(),

                "[Thread-0] serviceRepository.transaction() ends".asBreakPoint(),

                "[Thread-1] serviceRepository.transaction() starts".asBreakPoint {
                    assertEquals(1, serviceRepositoryDelegate.services.size)
                }
            )
        )

        val newService = Service()

        given(serviceBrokerClientDelegate.provision(any()))
            .willReturn(OperationResult(state = IN_PROGRESS))
        given(serviceBrokerClientDelegate.fetchLastOperation(any()))
            .willReturn(OperationResult(state = SUCCEEDED))

        val serviceId = provisionService.provision(newService).id!!

        jobScheduler.schedule(object : Job() {
            override fun perform() {
                given(serviceBrokerClientDelegate.deprovision(serviceId))
                    .willReturn(OperationResult(state = IN_PROGRESS))

                deprovisionService.deprovision(serviceId)
            }
        })

        jobScheduler.waitForAll()

        assertEquals(breakPoints.errors, emptyList<Throwable>())
    }
}

class NonReschedulingThreadingJobScheduler(
    private val serviceRepository: ServiceRepository,
    private val serviceBrokerClient: ServiceBrokerClient,
    private val racingBreakPoints: RacingBreakPoints
) : JobScheduler {
    private val threads = mutableListOf<Thread>()

    fun waitForAll() {
        threads.forEach { it.join(1000) }
    }

    override fun schedule(job: Job) {
        racingBreakPoints.point("jobScheduler.schedule()")

        job.jobScheduler = this

        if (job is OperationStateFetcher) {
            job.serviceRepository = serviceRepository
            job.serviceBrokerClient = serviceBrokerClient
        }

        val thread = Thread(job::perform)
        threads.add(thread)
        thread.start()
    }

    override fun rescheduleIn(duration: Duration, job: Job) {
        racingBreakPoints.point("jobScheduler.rescheduleIn()")
        // do nothing
    }
}

class RacingBreakPoints_ServiceBrokerClient(
    private val delegate: ServiceBrokerClient,
    private val breakPoints: RacingBreakPoints
) : ServiceBrokerClient by delegate {
    override fun deprovision(serviceId: String): OperationResult {
        breakPoints.point("serviceBrokerClient.deprovision()")
        return delegate.deprovision(serviceId)
    }

    override fun fetchLastOperation(serviceId: String): OperationResult {
        breakPoints.point("serviceBrokerClient.fetchLastOperation() starts")
        val result = delegate.fetchLastOperation(serviceId)
        breakPoints.point("serviceBrokerClient.fetchLastOperation() ends")
        return result
    }
}

class RacingBreakPoints_ServiceRepository(
    private val delegate: ServiceRepository,
    private val breakPoints: RacingBreakPoints
) : ServiceRepository by delegate {
//    override fun create(service: Service): Service {
//        breakPoints.point("serviceRepository.create()")
//        return delegate.create(service)
//    }

    override fun update(service: Service) {
        breakPoints.point("serviceRepository.update()")
        return delegate.update(service)
    }

//    override fun load(serviceId: String): Service? {
//        breakPoints.point("serviceRepository.load()")
//        return delegate.load(serviceId)
//    }

//    override fun transaction(block: () -> Unit) {
//        breakPoints.point("serviceRepository.transaction() starts")
//        delegate.transaction(block)
//        breakPoints.point("serviceRepository.transaction() ends")
//    }

//    override fun lock(serviceId: String): Service {
//        breakPoints.point("serviceRepository.lock()")
//        return delegate.lock(serviceId)
//    }

    override fun delete(service: Service) {
        breakPoints.point("serviceRepository.delete()")
        return delegate.delete(service)
    }
}