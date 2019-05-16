import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BreakPoint(
    val info: String,
    val assertion: () -> Unit
) {
    override fun toString(): String {
        return info
    }
}

fun String.asBreakPoint(assertion: () -> Unit = {}) =
    BreakPoint(info = this, assertion = assertion)

class RacingBreakPoints {
    private lateinit var order: MutableList<BreakPoint>
    private val lock = ReentrantLock()
    val errors = mutableListOf<Throwable>()

    fun point(info: String) {
        val thisThreadId = Thread.currentThread().name
        val expectedInfo = "[$thisThreadId] $info"

        val maxAttempts = 200
        var attempt = 0
        while (order.firstOrNull()?.info != expectedInfo && attempt < maxAttempts) {
            attempt++
            Thread.sleep(5)
        }

        if (order.firstOrNull()?.info != expectedInfo) {
            println("Timed out while waiting for: '$expectedInfo' [attempts: $attempt]")
            println("   Current HEAD: ${order.firstOrNull()}")
            errors.add(IllegalStateException("Waiting for $expectedInfo"))
            return
        }

        lock.withLock {
            try {
                order.first().assertion()
            } catch (e: Throwable) {
                errors.add(e)
            } finally {
                order.removeAt(0)
            }
        }

        println(expectedInfo)
    }

    fun expectOrder(order: List<BreakPoint>) {
        this.order = order.toMutableList()
    }
}