package account.fixtures.containers

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName.parse

class EventStoreDB() {

//    val logger = LoggerFactory.getLogger("EventStoreDBContainerLogs")

    val container: GenericContainer<*> = GenericContainer(parse("eventstore/eventstore:22.10.3-buster-slim"))
        .withExposedPorts(2113, 1113)
        .withEnv("EVENTSTORE_INSECURE", "true")
        .withEnv("EVENTSTORE_ENABLE_EXTERNAL_TCP", "true")
        .withEnv("EVENTSTORE_EXT_TCP_PORT", "1113")
        .withEnv("EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP", "true")
//        .withEnv("EVENTSTORE_MEM_DB", "true") // Use in-memory DB for testing
//        .withEnv("EVENTSTORE_RUN_PROJECTIONS", "All") // Enable projections if needed
//        .withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
        .withEnv("EVENTSTORE_LOG_LEVEL", "Debug")
        .waitingFor(Wait.forListeningPort())
//        .withLogConsumer(Slf4jLogConsumer(logger))
        .also {
            it.start()
        }

    val client: EventStoreDBClient = EventStoreDBClient.create(
        EventStoreDBConnectionString.parseOrThrow("esdb://localhost:${container.getMappedPort(2113)}?tls=false")
    )

    val username = "admin"
    val password = "changeit"
    val persistentSubscriptionsClient = EventStoreDBPersistentSubscriptionsClient.create(
        EventStoreDBConnectionString.parseOrThrow("esdb://$username:$password@localhost:${container.getMappedPort(2113)}?tls=false")
    )

//    val projectionManagementClient = EventStoreDBProjectionManagementClient.create(
//        EventStoreDBConnectionString.parseOrThrow("esdb://admin:changeit@localhost:${container.getMappedPort(2113)}?tls=false")
//    )
}
