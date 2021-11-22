package cz.osvald.rostislav

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import cz.osvald.rostislav.generated.Users
import cz.osvald.rostislav.generated.Users.name
import io.ktor.application.*
import cz.osvald.rostislav.plugins.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)


fun initDatabase(){
    val hikariConfig = HikariConfig("db.properties")
    val dataSource = HikariDataSource(hikariConfig)

    val flyway = Flyway.configure().dataSource(dataSource).load()
    flyway.migrate()

    Database.connect(dataSource)
}


@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    initDatabase()

    configureRouting()
    configureSecurity()
    configureSerialization()
}
