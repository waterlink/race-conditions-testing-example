import OperationState.IN_PROGRESS
import OperationState.SUCCEEDED
import OperationType.DEPROVISION

class OperationStateFetcher(
    @JobExport private val serviceId: String
) : Job() {
    private lateinit var service: Service
    private lateinit var intendedOperation: Operation

    @JobDependency
    lateinit var serviceRepository: ServiceRepository

    @JobDependency
    lateinit var serviceBrokerClient: ServiceBrokerClient

    override fun perform() {
        try {
            service = serviceRepository.load(serviceId) ?: return
            intendedOperation = service.lastOperation ?: return

            val result = serviceBrokerClient.fetchLastOperation(serviceId)

            if (result.state == IN_PROGRESS) {
                retry()
                return
            }

            updateLastOperation(result)
        } catch (e: Exception) {
            e.printStackTrace()
            retry()
        }
    }

    private fun retry() {
        retryIn(5.seconds)
    }

    private fun updateLastOperation(result: OperationResult) {
        serviceRepository.transaction {
            val service = serviceRepository.lock(serviceId)

            if (service.lastOperation != intendedOperation) {
                return@transaction
            }

            val operation = service.lastOperation!!
            operation.state = result.state
            operation.message = result.message
            finalizeOperation(service)
        }
    }

    private fun finalizeOperation(service: Service) {
        val operation = service.lastOperation!!
        if (operation.type == DEPROVISION && operation.state == SUCCEEDED) {
            serviceRepository.delete(service)
        } else {
            serviceRepository.update(service)
        }
    }
}