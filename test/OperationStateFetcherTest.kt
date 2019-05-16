import OperationState.IN_PROGRESS
import OperationState.SUCCEEDED
import OperationType.DEPROVISION
import OperationType.PROVISION
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OperationStateFetcherTest {
    private val serviceRepository = InMemoryServiceRepository()
    private val serviceBrokerClient = mock(ServiceBrokerClient::class.java)

    @Test
    fun `handles operation type change gracefully`() {
        val service = serviceRepository.create(
            Service(
                lastOperation = Operation(
                    type = PROVISION,
                    state = IN_PROGRESS
                )
            )
        )
        val serviceId = service.id!!

        val job = OperationStateFetcher(serviceId).also {
            it.serviceRepository = serviceRepository
            it.serviceBrokerClient = serviceBrokerClient
        }

        given(serviceBrokerClient.fetchLastOperation(serviceId)).will {
            // emulate racing deprovision while call to client is in progress
            service.lastOperation = Operation(
                type = DEPROVISION,
                state = IN_PROGRESS
            )
            serviceRepository.update(service)

            return@will OperationResult(
                state = SUCCEEDED
            )
        }

        job.perform()

        val found = serviceRepository.load(serviceId)
        assertNotNull(found)
        assertEquals(
            found.lastOperation, Operation(
                type = DEPROVISION,
                state = IN_PROGRESS
            )
        )
    }

    @Test
    fun `perform on provision`() {
        val service = serviceRepository.create(
            Service(
                lastOperation = Operation(
                    type = PROVISION,
                    state = IN_PROGRESS
                )
            )
        )
        val serviceId = service.id!!

        val job = OperationStateFetcher(serviceId).also {
            it.serviceRepository = serviceRepository
            it.serviceBrokerClient = serviceBrokerClient
        }

        given(serviceBrokerClient.fetchLastOperation(serviceId))
            .willReturn(OperationResult(state = SUCCEEDED))

        job.perform()

        val found = serviceRepository.load(serviceId)
        assertNotNull(found)
        assertEquals(
            found.lastOperation, Operation(
                type = PROVISION,
                state = SUCCEEDED
            )
        )
    }

    @Test
    fun `perform on deprovision`() {
        val service = serviceRepository.create(
            Service(
                lastOperation = Operation(
                    type = DEPROVISION,
                    state = IN_PROGRESS
                )
            )
        )
        val serviceId = service.id!!

        val job = OperationStateFetcher(serviceId).also {
            it.serviceRepository = serviceRepository
            it.serviceBrokerClient = serviceBrokerClient
        }

        given(serviceBrokerClient.fetchLastOperation(serviceId))
            .willReturn(OperationResult(state = SUCCEEDED))

        job.perform()

        val found = serviceRepository.load(serviceId)
        assertNull(found)
    }

}