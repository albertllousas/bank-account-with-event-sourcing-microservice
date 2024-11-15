package account.fixtures.containers

import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.Consumer
import org.apache.pulsar.client.api.Message
import org.apache.pulsar.client.api.PulsarClient
import org.rnorth.ducttape.unreliables.Unreliables
import org.slf4j.LoggerFactory
import org.testcontainers.containers.Network
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.concurrent.TimeUnit

class Pulsar private constructor() {

    val logger = LoggerFactory.getLogger("PulsarContainer")

    val container: PulsarContainer by lazy {
        PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:4.0.0"))
            .withNetwork(Network.newNetwork())
            .withEnv("PULSAR_PREFIX_allowAutoTopicCreation", "true")
            .withEnv("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned")
            .waitingFor(Wait.forHttp("/admin/v2/brokers/standalone").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2))
//            .withLogConsumer(Slf4jLogConsumer(logger))
            .withReuse(true)
            .also {
                it.start()
            }
    }

    val client: PulsarClient by lazy { PulsarClient.builder().serviceUrl(container.pulsarBrokerUrl).build() }

    fun cleanup() {
        val pulsarAdmin = PulsarAdmin.builder().serviceHttpUrl(container.httpServiceUrl).build()
        pulsarAdmin.topics().getList("public/default").forEach {
            topic -> pulsarAdmin.topics().delete(topic, true)
            println("Deleted topic: $topic")
        }
        pulsarAdmin.close()
        Thread.sleep(1000)
    }

    companion object {

        @Volatile
        private var instance: Pulsar? = null

        // using a singleton pattern to avoid creating multiple pulsar containers,
        // since there is a problem running multiple pulsar containers
        fun getInstance(): Pulsar {
            return instance ?: synchronized(this) {
                instance ?: Pulsar().also { instance = it }
            }
        }

        fun drain(
            consumer: Consumer<ByteArray>,
            expectedMessageCount: Int,
            timeoutSeconds: Int = 20
        ): List<Message<ByteArray>> {
            val allMessages: MutableList<Message<ByteArray>> = ArrayList()
            Unreliables.retryUntilTrue(timeoutSeconds, TimeUnit.SECONDS) {
                val messages = consumer.batchReceive()
                messages.iterator().forEachRemaining { allMessages.add(it) }
                allMessages.size >= expectedMessageCount
            }
            consumer.close()
            return allMessages
        }
    }
}
