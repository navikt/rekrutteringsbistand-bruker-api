package no.nav.toi.rekrutteringsbistand.bruker.api

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.TimeUnit

private const val NAV_IDENT_CLAIM = "NAVident"
private const val PID_CLAIM = "pid"

private val log = noClassLogger()

class Autentiseringskonfigurasjon(
    private val issuer: String,
    private val jwksUri: String,
    private val audience: String
) {
    fun jwtVerifiers() = listOf(NAV_IDENT_CLAIM, PID_CLAIM).map { identClaim ->
        JWT.require(algorithm(jwksUri))
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaimPresence(identClaim)
            .build()
    }
}

class AutentisertNavBruker(
    private val navIdent: String,
    private val roller: Set<NavAnsattRolle>,
    private val jwt: String
){
    companion object {
        fun fromJwt(jwt: DecodedJWT, rolleUuidSpesifikasjon: RolleUuidSpesifikasjon): AutentisertNavBruker {
            val navIdentClaim = jwt.getClaim(NAV_IDENT_CLAIM)
            return AutentisertNavBruker(
                    navIdent = navIdentClaim.asString(),
                    roller = jwt.getClaim("groups")
                        .asList(UUID::class.java)
                        .let(rolleUuidSpesifikasjon::rollerForUuider),
                    jwt = jwt.token,
                )
        }
        fun Context.hentNavIdent(): String =
            attribute<AutentisertNavBruker>("authenticatedUser")?.hentNavIdent() ?: throw UnauthorizedResponse("Not authenticated")
    }

    fun verifiserAutorisasjon(vararg gyldigeRoller: NavAnsattRolle) {
        if(!erEnAvRollene(*gyldigeRoller)) {
            throw ForbiddenResponse()
        }
    }

    fun erEnAvRollene(vararg gyldigeRoller: NavAnsattRolle) = roller.any { it in (gyldigeRoller.toList() + NavAnsattRolle.UTVIKLER) }
    fun hentNavIdent() = navIdent
}

fun verifyJwt(verifiers: List<JWTVerifier>, token: String): DecodedJWT {
    for (verifier in verifiers) {
        try {
            return verifier.verify(token)
        } catch (e: JWTVerificationException) {
            // prøv neste verifier
        } catch (e: SigningKeyNotFoundException) {
            // prøv neste verifier
        }
    }
    log.error("Token verifisering feilet")
    throw UnauthorizedResponse("Token verifisering feilet")
}

private fun algorithm(jwksUri: String): Algorithm {
    val jwkProvider = JwkProviderBuilder(URI(jwksUri).toURL())
        .cached(10, 1, TimeUnit.HOURS)
        .rateLimited(false)
        .build()
    return Algorithm.RSA256(object : RSAKeyProvider {
        override fun getPublicKeyById(keyId: String): RSAPublicKey =
            jwkProvider.get(keyId).publicKey as RSAPublicKey

        override fun getPrivateKey() = throw UnsupportedOperationException()
        override fun getPrivateKeyId() = throw UnsupportedOperationException()
    })
}

fun Context.autentisertNavBruker() = attribute<AutentisertNavBruker>("autentisertNavBruker")
    ?: run {
        log.error("Ingen autentisert Nav-bruker funnet")
        throw InternalServerErrorResponse()
    }