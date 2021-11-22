package cz.osvald.rostislav

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import cz.osvald.rostislav.dto.Movie
import cz.osvald.rostislav.dto.UserCredentials
import cz.osvald.rostislav.generated.Movies
import cz.osvald.rostislav.generated.Users
import cz.osvald.rostislav.generated.Users.password
import cz.osvald.rostislav.plugins.configureSecurity
import cz.osvald.rostislav.plugins.configureSerialization
import de.mkammerer.argon2.Argon2Factory
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)


fun initDatabase() {
    val hikariConfig = HikariConfig("db.properties")
    val dataSource = HikariDataSource(hikariConfig)

    val flyway = Flyway.configure().dataSource(dataSource).load()
    flyway.migrate()

    Database.connect(dataSource)
}


@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    initDatabase()

    val argon2 = Argon2Factory.create()
    fun hash(password: String) = argon2.hash(22, 65536, 1, password.toCharArray())

    configureSecurity()
    configureSerialization()

    routing {
        post("/register") {
            val user = call.receive<UserCredentials>()
            transaction {
                Users.insert {
                    it[name] = user.name
                    it[password] = hash(user.password)
                }
            }
            call.respond(HttpStatusCode.OK, "User created")
        }
        post("/login") {
            val user = call.receive<UserCredentials>()
            val databaseUser = transaction {
                Users.select(Op.build { Users.name eq user.name }).limit(1).firstOrNull()
            }
            if (databaseUser == null || !argon2.verify(databaseUser[password], user.password.toCharArray())) {
                call.respond(HttpStatusCode.Unauthorized, "Wrong name or password.")
            }
            val token = JWT.create()
                .withClaim("username", user.name)
                .withExpiresAt(Date(System.currentTimeMillis() + 60000))
                .sign(Algorithm.HMAC256(environment.config.property("jwt.secret").getString()))

            call.respond(hashMapOf("token" to token))
        }
        authenticate {
            route("/movies") {
                get {
                    call.respond(transaction {
                        Movies.selectAll().map {
                            Movie(
                                it[Movies.id].value,
                                it[Movies.name],
                                it[Movies.createdAt].toString(),
                                it[Movies.viewedAt]?.toString()
                            )
                        }
                    })
                }
            }
        }
    }
}
