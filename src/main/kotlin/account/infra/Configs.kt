package account.infra

import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.pulsar.client.api.PulsarClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration // Review for production readiness
class Configs {

    @Bean
    fun eventStoreDBPersistentSubscriptionsClient(
        @Value("\${eventstore.host}") host: String,
        @Value("\${eventstore.port}") port: Int,
        @Value("\${eventstore.username}") username: String,
        @Value("\${eventstore.password}") password: String
    ) = EventStoreDBPersistentSubscriptionsClient.create(
            EventStoreDBConnectionString.parseOrThrow("esdb://$username:$password@$host:$port?tls=false")
        )

    @Bean
    fun eventStoreDBClient(
        @Value("\${eventstore.host}") host: String,
        @Value("\${eventstore.port}") port: Int,
    ) = EventStoreDBClient.create(EventStoreDBConnectionString.parseOrThrow("esdb://$host:$port?tls=false"))

    @Bean
    fun pulsarClient(@Value("\${pulsar.service-url}") serviceUrl: String) =
        PulsarClient.builder().serviceUrl(serviceUrl).build()

    @Bean
    fun meterRegistry() = SimpleMeterRegistry()
}
