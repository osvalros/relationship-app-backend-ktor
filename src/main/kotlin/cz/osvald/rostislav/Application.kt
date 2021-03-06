package cz.osvald.rostislav

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import cz.osvald.rostislav.dto.Movie
import cz.osvald.rostislav.dto.Rating
import cz.osvald.rostislav.dto.UserCredentials
import cz.osvald.rostislav.generated.Movies
import cz.osvald.rostislav.generated.Ratings
import cz.osvald.rostislav.generated.Users
import cz.osvald.rostislav.generated.Users.password
import cz.osvald.rostislav.plugins.configureSecurity
import cz.osvald.rostislav.plugins.configureSerialization
import de.mkammerer.argon2.Argon2Factory
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
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
                .withExpiresAt(Calendar.getInstance().apply { add(Calendar.DATE, 7) }.time)
                .sign(Algorithm.HMAC256(environment.config.property("jwt.secret").getString()))

            call.respond(hashMapOf("token" to token))
        }
        authenticate {
            route("/users") {
                get("/me") {
                    call.respond(call.getLoggedUser())
                }
            }
            route("/movies") {
                get {
                    call.respond(transaction {
                        Movies.selectAll().apply {
                            val sortParam = call.request.queryParameters["sort"]
                            sortParam?.split(",")?.map { sortExpressionString ->
                                SortExpression.fromExpressionString(sortExpressionString)
                            }?.forEach { sortExpression ->
                                val column = when (sortExpression.column) {
                                    "id" -> Movies.id
                                    "createdAt" -> Movies.createdAt
                                    "viewedAt" -> Movies.viewedAt
                                    else -> return@forEach
                                }
                                orderBy(column, sortExpression.order)
                            }
                            val viewed = call.request.queryParameters["viewed"]?.toBoolean()
                            viewed?.let {
                                andWhere {
                                    if (viewed) Movies.viewedAt neq null else Movies.viewedAt eq null
                                }
                            }
                        }.map {
                            it.toMovie()
                        }
                    })
                }
                post {
                    val movie = call.receive<Movie>()
                    try {
                        transaction {
                            Movies.insert {
                                it[name] = movie.name
                                it[creatorId] = call.getLoggedUser().id
                            }
                        }
                    } catch (e: ExposedSQLException) {
                        call.respond(HttpStatusCode.BadRequest, "Failed to create movie (duplicate name?)")
                    }
                    call.respond("Movie created")
                }
                route("/{movieId}") {
                    get {
                        val movie = transaction {
                            Movies.select(Op.build { Movies.id eq call.parameters["movieId"]!!.toInt() }).limit(1)
                                .firstOrNull()
                        }?.toMovie()
                        if (movie == null) {
                            call.respond(HttpStatusCode.NotFound, "Movie not found")
                        } else {
                            call.respond(movie)
                        }
                    }
                    put {
                        val movie = call.receive<Movie>()
                        transaction {
                            Movies.update({ Movies.id eq call.parameters["movieId"]!!.toInt() }) {
                                it[name] = movie.name
                                it[viewedAt] = movie.viewedAt?.let { it1 -> LocalDateTime.parse(it1) }
                            }
                        }
                        call.respond("Movie updated")
                    }
                    route("ratings") {
                        get {
                            call.respond(
                                transaction {
                                    Ratings.selectAll().andWhere {
                                        Ratings.movieId eq call.parameters["movieId"]!!.toInt()
                                    }.map {
                                        it.toRating()
                                    }
                                })
                        }
                        post {
                            val rating = call.receive<Rating>()
                            val loggedUser = call.getLoggedUser()
                            val movieIdParam = call.parameters["movieId"]!!.toInt()
                            val created = transaction {
                                val whereRatingOnMovieFromLoggedUser = Op.build {
                                    AndOp(
                                        listOf(
                                            Ratings.movieId eq movieIdParam,
                                            Ratings.userId eq call.getLoggedUser().id
                                        )
                                    )
                                }
                                val ratingInDatabase = Ratings.selectOne(whereRatingOnMovieFromLoggedUser)
                                if (ratingInDatabase == null) {
                                    Ratings.insert {
                                        it[value] = rating.value
                                        it[movieId] = movieIdParam
                                        it[userId] = loggedUser.id
                                    }
                                    return@transaction true
                                } else {
                                    Ratings.update({whereRatingOnMovieFromLoggedUser}) {
                                        it[value] = rating.value
                                    }
                                    return@transaction false
                                }
                            }
                            call.respond(if (created) "Rating created" else "Rating updated")
                        }
                    }
                }
            }
        }
    }
}


fun ResultRow.toMovie(): Movie {
    return Movie(
        this[Movies.id].value,
        this[Movies.name],
        this[Movies.createdAt].toString(),
        this[Movies.viewedAt]?.toString()
    )
}

fun ResultRow.toRating(): Rating {
    return Rating(
        this[Ratings.id].value,
        this[Ratings.value],
        this[Ratings.movieId],
        this[Ratings.userId],
    )
}

fun Table.selectOne(where: Op<Boolean>) = this.select(where).limit(1).firstOrNull()

class UnauthorizedException : Exception("Unauthorized")

fun ApplicationCall.getLoggedUser(): User = transaction {
    val userName = this@getLoggedUser.principal<JWTPrincipal>()!!.payload.getClaim("username").asString()
    Users.selectOne(Op.build { Users.name eq userName })?.let {
        User(it[Users.id].value, it[Users.name])
    }
} ?: runBlocking {
    this@getLoggedUser.respond(UnauthorizedResponse())
    throw UnauthorizedException()
}


data class SortExpression(val column: String, val order: SortOrder) {
    companion object {
        fun fromExpressionString(sortExpressionString: String) =
            sortExpressionString.split(":", limit = 2).let {
                SortExpression(
                    it[0],
                    if (it.getOrNull(1) == "desc") SortOrder.DESC_NULLS_LAST else SortOrder.ASC_NULLS_LAST
                )
            }
    }
}