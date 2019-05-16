import OperationState.IN_PROGRESS
import OperationType.PROVISION

class ProvisionService(
    private val serviceRepository: ServiceRepository,
    private val serviceBrokerClient: ServiceBrokerClient,
    private val jobScheduler: JobScheduler
) {
    fun provision(newService: Service): Service {
        val operation = Operation(
            type = PROVISION,
            state = IN_PROGRESS
        )

        newService.lastOperation = operation
        val service = serviceRepository.create(newService)

        val result = serviceBrokerClient.provision(service)

        serviceRepository.transaction {
            serviceRepository.lock(service.id!!)

            if (result.state == IN_PROGRESS) {
                jobScheduler.schedule(OperationStateFetcher(service.id))
            } else {
                operation.state = result.state
                operation.message = result.message
                serviceRepository.update(service)
            }
        }

        return service
    }
}