package account.acceptance

import account.fixtures.containers.EventStoreDB
import account.fixtures.containers.Postgres
import account.fixtures.containers.Pulsar
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.restassured.RestAssured
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext(classMode = AFTER_CLASS)
abstract class BaseAcceptanceTest {

    init {
        RestAssured.defaultParser = io.restassured.parsing.Parser.JSON
    }

    @LocalServerPort
    protected val servicePort: Int = 0

    protected val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    companion object {
        var postgres = Postgres()
        var pulsar = Pulsar.getInstance()
        var eventStore = EventStoreDB()
    }

    @BeforeEach
    fun setUp() {
        postgres = Postgres()
        pulsar = Pulsar.getInstance()
        eventStore = EventStoreDB()
    }

    @AfterEach
    fun tearDown() {
        pulsar.cleanup()
        eventStore.container.stop()
        postgres.container.stop()
    }


    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {

        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "spring.datasource.url=" + postgres.container.jdbcUrl,
                "spring.datasource.password=" + postgres.container.password,
                "spring.datasource.username=" + postgres.container.username,
                "spring.flyway.url=" + postgres.container.jdbcUrl,
                "spring.flyway.password=" + postgres.container.password,
                "spring.flyway.username=" + postgres.container.username,
                "eventstore.host=localhost",
                "eventstore.port=" + eventStore.container.getMappedPort(2113),
                "eventstore.password=" + eventStore.password,
                "eventstore.username=" + eventStore.username,
                "pulsar.service-url=" + pulsar.container.pulsarBrokerUrl,
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}
