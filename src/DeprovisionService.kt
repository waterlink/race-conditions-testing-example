import OperationState.IN_PROGRESS
import OperationType.DEPROVISION
import OperationType.PROVISION

class DeprovisionService(
    private val serviceRepository: ServiceRepository,
    private val serviceBrokerClient: ServiceBrokerClient,
    private val jobScheduler: JobScheduler
) {
    fun deprovision(serviceId: String) {
        val operation = Operation(
            type = DEPROVISION,
            state = IN_PROGRESS
        )

        serviceRepository.transaction {
            val service = serviceRepository.lock(serviceId)
            val lastOperation = service.lastOperation
            if (lastOperation?.state == IN_PROGRESS && lastOperation.type != PROVISION) {
                throw OperationInProgressException()
            }

            service.lastOperation = operation
            serviceRepository.update(service)
        }

        val result = serviceBrokerClient.deprovision(serviceId)

        serviceRepository.transaction {
            val service = serviceRepository.lock(serviceId)

            if (result.state == IN_PROGRESS) {
                jobScheduler.schedule(OperationStateFetcher(serviceId))
            } else {
                operation.state = result.state
                operation.message = result.message
                serviceRepository.update(service)
            }
        }
    }
}