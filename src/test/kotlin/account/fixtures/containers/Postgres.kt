package account.fixtures.containers

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import javax.sql.DataSource

class Postgres {

    val container: KtPostgreSQLContainer = KtPostgreSQLContainer()
        .withNetwork(Network.newNetwork())
        .withUsername("account-projections")
        .withPassword("account-projections")
        .withDatabaseName("account-projections")
        .waitingFor(Wait.forListeningPort())
        .also {
            it.start()
        }

    val datasource: DataSource = HikariDataSource().apply {
        driverClassName = org.postgresql.Driver::class.qualifiedName
        jdbcUrl = container.jdbcUrl
        username = container.username
        password = container.password
    }.also { Flyway(FluentConfiguration().dataSource(it)).migrate() }

    val jdbcTemplate = JdbcTemplate(datasource)
}

class KtPostgreSQLContainer : PostgreSQLContainer<KtPostgreSQLContainer>("postgres:latest")
