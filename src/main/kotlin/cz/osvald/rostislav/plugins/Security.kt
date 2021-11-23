package cz.osvald.rostislav.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*

fun Application.configureSecurity() {

    authentication {
        jwt {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(environment.config.property("jwt.secret").getString()))
                    .build()
            )

            validate { credential ->
                if (!credential.payload.getClaim("username").asString().isNullOrEmpty()) JWTPrincipal(credential.payload) else null
            }
        }
    }

}
