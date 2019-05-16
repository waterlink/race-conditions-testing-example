data class OperationResult(
    val state: OperationState,
    val message: String? = null
)

interface ServiceBrokerClient {
    fun provision(service: Service): OperationResult
    fun deprovision(serviceId: String): OperationResult
    fun fetchLastOperation(serviceId: String): OperationResult
}

open class DummyServiceBrokerClient {
    open fun provision(service: Service): OperationResult {
        return OperationResult(state = OperationState.IN_PROGRESS)
    }

    open fun deprovision(serviceId: String): OperationResult {
        return OperationResult(state = OperationState.IN_PROGRESS)
    }

    open fun fetchLastOperation(serviceId: String): OperationResult {
        return OperationResult(state = OperationState.IN_PROGRESS)
    }
}