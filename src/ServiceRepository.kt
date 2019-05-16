import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface ServiceRepository {
    fun create(service: Service): Service

    fun update(service: Service)

    fun load(serviceId: String): Service?

    fun transaction(block: () -> Unit)

    fun lock(serviceId: String): Service

    fun delete(service: Service)
}

class ServiceNotFoundException(id: String) :
    RuntimeException("Service $id not found")

class ServiceLockedException(id: String) :
    RuntimeException("Service $id is locked")

fun Service.deepCopy() = copy(
    lastOperation = lastOperation?.copy()
)

class InMemoryServiceRepository : ServiceRepository {
    val services = mutableMapOf<String, Service>()

    // service-id => thread-id
    private val locks = mutableMapOf<String, Long>()
    private val lock = ReentrantLock()

    override fun create(service: Service): Service {
        val id = UUID.randomUUID().toString()
        val created = service.deepCopy().copy(id = id)
        services[id] = created
        return created.deepCopy()
    }

    override fun update(service: Service) {
        val id = service.id
            ?: throw IllegalArgumentException("service id can't be null")

        checkLock(id)

        services[id] ?: throw ServiceNotFoundException(id)
        services[id] = service.deepCopy()
    }

    override fun load(serviceId: String): Service? {
        return services[serviceId]?.deepCopy()
    }

    override fun transaction(block: () -> Unit) {
        val thisThreadId = Thread.currentThread().id

        try {
            block()
        } finally {
            val toRemove = mutableListOf<String>()
            locks.forEach { serviceId, threadId ->
                if (threadId == thisThreadId) {
                    toRemove.add(serviceId)
                }
            }
            toRemove.forEach { locks.remove(it) }
        }
    }

    override fun lock(serviceId: String): Service {

        return lock.withLock {
            val thisThreadId = checkLock(serviceId)
            locks[serviceId] = thisThreadId

            services[serviceId]
                ?: throw ServiceNotFoundException(serviceId)
        }
    }

    override fun delete(service: Service) {
        val id = service.id
            ?: throw IllegalArgumentException("service id can't be null")

        checkLock(id)

        services.remove(id)
    }

    private fun checkLock(serviceId: String): Long {
        val thisThreadId = Thread.currentThread().id

        if (locks[serviceId] == null) {
            return thisThreadId
        }

        if (locks[serviceId] != thisThreadId) {
            throw ServiceLockedException(serviceId)
        }

        return thisThreadId
    }
}