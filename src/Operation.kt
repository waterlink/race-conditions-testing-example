enum class OperationType {
    PROVISION, UPDATE, DEPROVISION
}

enum class OperationState {
    IN_PROGRESS, SUCCEEDED, FAILED
}

class OperationInProgressException : RuntimeException(
    "There is an operation in progress"
)

data class Operation(val id: String? = null,
                     val type: OperationType,
                     var state: OperationState,
                     var message: String? = null)