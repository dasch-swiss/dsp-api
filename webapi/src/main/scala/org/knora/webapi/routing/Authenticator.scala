/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, orr
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{HttpCookie, HttpCookiePair}
import akka.http.scaladsl.model.{headers, _}
import akka.http.scaladsl.server.RequestContext
import akka.pattern._
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.Logger
import io.igl.jwt._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.util.CacheUtil
import org.slf4j.LoggerFactory
import spray.json.{JsNumber, JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

// needs Java 1.8 !!!
import java.util.Base64

/**
  * This trait is used in routes that need authentication support. It provides methods that use the [[RequestContext]]
  * to extract credentials, authenticate provided credentials, and look up cached credentials through the use of the
  * session id. All private methods used in this trait can be found in the companion object.
  */
trait Authenticator {

    // Import companion object
    import Authenticator._

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // LOGIN ENTRY POINT
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Checks if the credentials provided in [[RequestContext]] are valid, and if so returns a message and cookie header
      * with the generated session id for the client to save.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
      *         the generated session id.
      */
    def doLoginV1(requestContext: RequestContext)(implicit system: ActorSystem, executionContext: ExecutionContext): HttpResponse = {

        val settings = Settings(system)

        val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

        val userProfile = getUserProfileV1ThroughCredentialsV2(credentials) // will return or throw

        val sessionToken = JWTHelper.createToken(userProfile.userData.user_id.get, settings.jwtSecretKey, settings.jwtLongevity)

        HttpResponse(
            headers = List(headers.`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sessionToken, path = Some("/")))), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "status" -> JsNumber(0),
                    "message" -> JsString("credentials are OK"),
                    "sid" -> JsString(sessionToken),
                    "userProfile" -> userProfile.ofType(UserProfileTypeV1.RESTRICTED).toJsValue
                ).compactPrint
            )
        )
    }

    /**
      * Checks if the provided credentials are valid, and if so returns a JWT token for the client to save.
      *
      * @param credentials the user supplied [[KnoraPasswordCredentialsV2]] containing the user's login information.
      * @param system      the current [[ActorSystem]]
      * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
      *         the generated session id.
      */
    def doLoginV2(credentials: KnoraPasswordCredentialsV2)(implicit system: ActorSystem, executionContext: ExecutionContext): HttpResponse = {

        authenticateCredentialsV2(Some(credentials)) // will throw exception if not valid and thus trigger the correct response

        val settings = Settings(system)

        val userProfile = getUserProfileV1ByEmail(credentials.email)

        val token = JWTHelper.createToken(userProfile.userData.user_id.get, settings.jwtSecretKey, settings.jwtLongevity)

        HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "token" -> JsString(token)
                ).compactPrint
            )
        )
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Authentication ENTRY POINT
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Checks if the credentials provided in [[RequestContext]] are valid, and if so returns a message. No session is
      * generated.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[RequestContext]]
      */
    def doAuthenticateV1(requestContext: RequestContext)(implicit system: ActorSystem, executionContext: ExecutionContext): HttpResponse = {

        val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

        val userProfile = getUserProfileV1ThroughCredentialsV2(credentials) // will authenticate and either return or throw

        HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "status" -> JsNumber(0),
                    "message" -> JsString("credentials are OK"),
                    "userProfile" -> userProfile.ofType(UserProfileTypeV1.RESTRICTED).toJsValue
                ).compactPrint
            )
        )
    }

    /**
      * Checks if the credentials provided in [[RequestContext]] are valid.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[HttpResponse]]
      */
    def doAuthenticateV2(requestContext: RequestContext)(implicit system: ActorSystem, executionContext: ExecutionContext): HttpResponse = {

        val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

        authenticateCredentialsV2(credentials) // will throw exception if not valid

        HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "message" -> JsString("credentials are OK")
                ).compactPrint
            )
        )
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // LOGOUT ENTRY POINT
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Used to logout the user, i.e. returns a header deleting the cookie and puts the token on the 'invalidated' list.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[HttpResponse]]
      */
    def doLogoutV2(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        val credentials = extractCredentialsV2(requestContext)

        credentials match {
            case Some(sessionCreds: KnoraSessionCredentialsV2) => {
                CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, sessionCreds.token, sessionCreds.token)

                HttpResponse(
                    headers = List(headers.`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "deleted", expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))),
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("Logout OK")
                        ).compactPrint
                    )
                )

            }
            case Some(tokenCreds: KnoraTokenCredentialsV2) => {
                CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, tokenCreds.token, tokenCreds.token)

                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("Logout OK")
                        ).compactPrint
                    )
                )
            }
            case _ => {
                // nothing to do
                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("Logout OK")
                        ).compactPrint
                    )
                )
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GET USER PROFILE / AUTHENTICATION ENTRY POINT
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Returns a UserProfile of the supplied type that match the credentials found in the [[RequestContext]].
      * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
      * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
      * corresponding error is returned.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[UserProfileV1]]
      */
    def getUserProfileV1(requestContext: RequestContext)(implicit system: ActorSystem, executionContext: ExecutionContext): UserProfileV1 = {

        val settings = Settings(system)

        val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

        if (settings.skipAuthentication) {
            // return anonymous if skipAuthentication
            log.debug("getUserProfileV1 - Authentication skipping active, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else if (credentials.isEmpty) {
            log.debug("getUserProfileV1 - No credentials found, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else {
            val userProfileV1 = getUserProfileV1ThroughCredentialsV2(credentials)
            log.debug("getUserProfileV1 - I got a UserProfileV1: {}", userProfileV1.toString)

            /* we return the userProfileV1 without sensitive information */
            userProfileV1.ofType(UserProfileTypeV1.RESTRICTED)
        }
    }

    // def getUserProfileV2(requestContext: RequestContext)(implicit system: ActorSystem, executionContext: ExecutionContext): UserProfileV2 = ???
}

/**
  * This companion object holds all private methods used in the trait. This division is needed so that we can test
  * the private methods directly with scalatest as described in [[https://groups.google.com/forum/#!topic/scalatest-users/FeaO\_\_f1dN4]]
  * and [[http://doc.scalatest.org/2.2.6/index.html#org.scalatest.PrivateMethodTester]]
  */
object Authenticator {

    val BAD_CRED_PASSWORD_MISMATCH = "bad credentials: user found, but password did not match"
    val BAD_CRED_USER_NOT_FOUND = "bad credentials: user not found"
    val BAD_CRED_EMAIL_NOT_SUPPLIED = "bad credentials: no email supplied"
    val BAD_CRED_NONE_SUPPLIED = "bad credentials: none found"
    val BAD_CRED_USER_INACTIVE = "bad credentials: user inactive"
    val BAD_CRED_NOT_VALID = "bad credentials: not valid"

    val KNORA_AUTHENTICATION_COOKIE_NAME = "KnoraAuthentication"
    val AUTHENTICATION_INVALIDATION_CACHE_NAME = "authenticationInvalidationCache"

    val sessionStore: scala.collection.mutable.Map[String, UserProfileV1] = scala.collection.mutable.Map()
    implicit val timeout: Timeout = Duration(5, SECONDS)
    val log = Logger(LoggerFactory.getLogger(this.getClass))

    /**
      * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
      * authentication is performed checking if the supplied email/password combination is valid by retrieving the
      * user's profile. In the case of the token, the token itself is validated. If both are supplied, then both need
      * to be valid.
      *
      * @param credentials the user supplied and extracted credentials.
      * @param system      the current [[ActorSystem]]
      * @return true if the credentials are valid. If the credentials are invalid, then the corresponding exception
      *         will be thrown.
      * @throws BadCredentialsException when no credentials are supplied; when user is not active;
      *                                 when the password does not match; when the supplied token is not valid.
      */
    private def authenticateCredentialsV2(credentials: Option[KnoraCredentialsV2])(implicit system: ActorSystem, executionContext: ExecutionContext): Boolean = {

        val settings = Settings(system)

        if (credentials.isEmpty) {
            throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
        }

        credentials match {
            case Some(passCreds: KnoraPasswordCredentialsV2) => {
                val userProfile = getUserProfileV1ByEmail(passCreds.email)

                /* check if the user is active, if not, then no need to check the password */
                if (!userProfile.isActive) {
                    log.debug("authenticateCredentials - user is not active")
                    throw BadCredentialsException(BAD_CRED_USER_INACTIVE)
                }

                if (!userProfile.passwordMatch(passCreds.password)) {
                    log.debug("authenticateCredentialsV2 - password did not match")
                    throw BadCredentialsException(BAD_CRED_NOT_VALID)
                }
            }
            case Some(tokenCreds: KnoraTokenCredentialsV2) => {
                if (!JWTHelper.validateToken(tokenCreds.token, settings.jwtSecretKey)) {
                    log.debug("authenticateCredentialsV2 - token was not valid")
                    throw BadCredentialsException(BAD_CRED_NOT_VALID)
                }
            }
            case Some(sessionCreds: KnoraSessionCredentialsV2) => {
                if (!JWTHelper.validateToken(sessionCreds.token, settings.jwtSecretKey)) {
                    log.debug("authenticateCredentialsV2 - session token was not valid")
                    throw BadCredentialsException(BAD_CRED_NOT_VALID)
                }
            }
            case None => {
                log.debug("authenticateCredentialsV2 - no credentials supplied")
                throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
            }
        }

        true
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // HELPER METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Tries to extract the credentials from the requestContext (parameters, auth headers, token)
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @return [[KnoraCredentialsV2]].
      */
    private def extractCredentialsV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {
        log.debug("extractCredentialsV2 start ...")

        val credentialsFromParameters: Option[KnoraCredentialsV2] = extractCredentialsFromParametersV2(requestContext)
        log.debug("extractCredentialsV2 - credentialsFromParameters: {}", credentialsFromParameters)

        val credentialsFromHeaders: Option[KnoraCredentialsV2] = extractCredentialsFromHeaderV2(requestContext)
        log.debug("extractCredentialsV2 - credentialsFromHeader: {}", credentialsFromHeaders)

        // return found credentials based on precedence: 1. url parameters, 2. header (basic auth, token)
        val credentials = if (credentialsFromParameters.nonEmpty) {
            credentialsFromParameters
        } else {
            credentialsFromHeaders
        }

        log.debug("extractCredentialsV2 - returned credentials: '{}'", credentials)
        credentials
    }

    /**
      * Tries to extract credentials supplied as URL parameters.
      *
      * @param requestContext the HTTP request context.
      * @return [[KnoraCredentialsV2]].
      */
    private def extractCredentialsFromParametersV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {
        // extract email/password from parameters

        val params: Map[String, Seq[String]] = requestContext.request.uri.query().toMultiMap

        val maybeEmail: Option[String] = params get "email" map (_.head)
        val maybePassword: Option[String] = params get "password" map (_.head)

        val maybePassCreds: Option[KnoraPasswordCredentialsV2] = if (maybeEmail.nonEmpty && maybePassword.nonEmpty) {
            Some(KnoraPasswordCredentialsV2(maybeEmail.get, maybePassword.get))
        } else {
            None
        }

        val maybeToken: Option[String] = params get "token" map (_.head)

        val maybeTokenCreds: Option[KnoraTokenCredentialsV2] = if (maybeToken.nonEmpty) {
            Some(KnoraTokenCredentialsV2(maybeToken.get))
        } else {
            None
        }

        // prefer password credentials
        if (maybePassCreds.nonEmpty) {
            maybePassCreds
        } else {
            maybeTokenCreds
        }
    }

    /**
      * Tries to extract the credentials (email/password, token) from the authorization header and the session token
      * from the cookie header.
      *
      * The authorization header looks something like this: 'Authorization: Basic xyz, Bearer xyz.xyz.xyz'
      * if both the email/password and token are sent.
      *
      * If more then one set of credentials is found, then they are selected as follows:
      *    1. email/password
      *    2. authorization token
      *    3. session token
      *
      * @param requestContext the HTTP request context.
      * @return an optional [[KnoraCredentialsV2]].
      */
    private def extractCredentialsFromHeaderV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {

        // Session token from cookie header
        val cookies: Seq[HttpCookiePair] = requestContext.request.cookies
        val maybeSessionCreds: Option[KnoraSessionCredentialsV2] = cookies.find(_.name == "KnoraAuthentication") match {
            case Some(authCookie) =>
                val value: String = authCookie.value
                Some(KnoraSessionCredentialsV2(value))
            case None =>
                None
        }

        // Authorization header
        val headers: Seq[HttpHeader] = requestContext.request.headers
        val (maybePassCreds, maybeTokenCreds) = headers.find(_.name == "Authorization") match {
            case Some(authHeader: HttpHeader) =>

                // the authorization header can hold different schemes
                val credsArr: Array[String] = authHeader.value().split(",")


                // in v2 we support the basic scheme
                val maybeBasicAuthValue = credsArr.find(_.contains("Basic"))

                // try to decode email/password
                val (maybeEmail, maybePassword) = maybeBasicAuthValue match {
                    case Some(value) =>
                        val trimmedValue = value.substring(5).trim() // remove 'Basic '
                    val decodedValue = ByteString.fromArray(Base64.getDecoder.decode(trimmedValue)).decodeString("UTF8")
                        val decodedValueArr = decodedValue.split(":", 2)
                        (Some(decodedValueArr(0)), Some(decodedValueArr(1)))
                    case None =>
                        (None, None)
                }

                val maybePassCreds: Option[KnoraPasswordCredentialsV2] = if (maybeEmail.nonEmpty && maybePassword.nonEmpty) {
                    Some(KnoraPasswordCredentialsV2(maybeEmail.get, maybePassword.get))
                } else {
                    None
                }


                // and the bearer scheme
                val maybeToken = credsArr.find(_.contains("Bearer")) match {
                    case Some(value) =>
                        Some(value.substring(6).trim()) // remove 'Bearer '
                    case None =>
                        None
                }

                val maybeTokenCreds: Option[KnoraTokenCredentialsV2] = if (maybeToken.nonEmpty) {
                    Some(KnoraTokenCredentialsV2(maybeToken.get))
                } else {
                    None
                }

                (maybePassCreds, maybeTokenCreds)

            case None =>
                (None, None)
        }

        // prefer password over token over session
        if (maybePassCreds.nonEmpty) {
            maybePassCreds
        } else if (maybeTokenCreds.nonEmpty) {
            maybeTokenCreds
        } else {
            maybeSessionCreds
        }
    }

    /**
      * Tries to retrieve a [[UserProfileV1]] based on the supplied credentials. If both email/password and session
      * token are supplied, then the user profile for the session token is returned. This method should only be used
      * with authenticated credentials.
      *
      * @param credentials the user supplied credentials.
      * @return a [[UserProfileV1]]
      * @throws AuthenticationException when the IRI can not be found inside the token, which is probably a bug.
      */
    private def getUserProfileV1ThroughCredentialsV2(credentials: Option[KnoraCredentialsV2])(implicit system: ActorSystem, executionContext: ExecutionContext): UserProfileV1 = {

        val settings = Settings(system)

        authenticateCredentialsV2(credentials)

        val userProfile: UserProfileV1 = credentials match {
            case Some(passCreds: KnoraPasswordCredentialsV2) => {
                log.debug("getUserProfileV1ThroughCredentialsV2 - used email")
                getUserProfileV1ByEmail(passCreds.email)
            }
            case Some(tokenCreds: KnoraTokenCredentialsV2) => {
                val userIri: IRI = JWTHelper.extractUserIriFromToken(tokenCreds.token, settings.jwtSecretKey) match {
                    case Some(iri) => iri
                    case None => {
                        // should not happen, as the token is already validated
                        throw AuthenticationException("No IRI found inside token. Please report this as a possible bug.")
                    }
                }
                log.debug("getUserProfileV1ThroughCredentialsV2 - used token")
                getUserProfileV1ByIri(userIri)
            }
            case Some(sessionCreds: KnoraSessionCredentialsV2) => {
                val userIri: IRI = JWTHelper.extractUserIriFromToken(sessionCreds.token, settings.jwtSecretKey) match {
                    case Some(iri) => iri
                    case None => {
                        // should not happen, as the token is already validated
                        throw AuthenticationException("No IRI found inside token. Please report this as a possible bug.")
                    }
                }
                log.debug("getUserProfileV1ThroughCredentialsV2 - used session token")
                getUserProfileV1ByIri(userIri)
            }
            case None => {
                log.debug("getUserProfileV1ThroughCredentialsV2 - no credentials supplied")
                throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
            }
        }

        log.debug("getUserProfileV1ThroughCredentialsV2 - userProfile: {}", userProfile)
        userProfile
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // TRIPLE STORE ACCESS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Get a user profile with the specific IRI from the triple store
      *
      * @param iri              the IRI of the user to be queried
      * @param system           the current akka actor system
      * @param timeout          the timeout of the query
      * @param executionContext the current execution context
      * @return a [[UserProfileV1]]
      * @throws BadCredentialsException when no user can be found with the supplied IRI.
      */
    private def getUserProfileV1ByIri(iri: IRI)(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): UserProfileV1 = {
        val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)
        val userProfileV1Future = for {
            maybeUserProfile <- (responderManager ? UserProfileByIRIGetV1(iri, UserProfileTypeV1.FULL)).mapTo[Option[UserProfileV1]]
            userProfileV1 = maybeUserProfile match {
                case Some(up) => up
                case None => {
                    log.debug(s"getUserProfileV1ByIri - supplied IRI not found - throwing exception")
                    throw BadCredentialsException(s"$BAD_CRED_USER_NOT_FOUND")
                }
            }
        } yield userProfileV1

        // TODO: return the future here instead of using Await.
        Await.result(userProfileV1Future, Duration(3, SECONDS))
    }

    /**
      * Tries to get a [[UserProfileV1]] from the cache or from the triple store matching the email.
      *
      * @param email            the email of the user to be queried
      * @param system           the current akka actor system
      * @param timeout          the timeout of the query
      * @param executionContext the current execution context
      * @return a [[UserProfileV1]]
      * @throws BadCredentialsException when either the supplied email is empty or no user with such an email could be found.
      */
    private def getUserProfileV1ByEmail(email: String)(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): UserProfileV1 = {

        val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

        if (email.nonEmpty) {
            val userProfileV1Future = for {
                maybeUserProfileV1 <- (responderManager ? UserProfileByEmailGetV1(email, UserProfileTypeV1.FULL)).mapTo[Option[UserProfileV1]]
                userProfileV1 = maybeUserProfileV1 match {
                    case Some(up) => up
                    case None => {
                        log.debug(s"getUserProfileV1ByEmail - supplied email not found - throwing exception")
                        throw BadCredentialsException(s"$BAD_CRED_USER_NOT_FOUND")
                    }
                }
                _ = log.debug(s"getUserProfileV1ByEmail - userProfileV1: $userProfileV1")
            } yield userProfileV1

            // TODO: return the future here instead of using Await.
            Await.result(userProfileV1Future, Duration(3, SECONDS))
        } else {
            throw BadCredentialsException(BAD_CRED_EMAIL_NOT_SUPPLIED)
        }
    }
}

object JWTHelper {

    import Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME

    // the encryption algorithm we chose to use.
    val algorithm = Algorithm.HS256

    // the headers which need to be present inside the JWT
    val requiredHeaders: Set[HeaderField] = Set[HeaderField](Typ)

    // the claims that need to be present inside the JWT
    // Iss: issuer, Sub: subject, Aud: audience, Iat: ussued at, Exp: expier date, Jti: unique identifier
    val requiredClaims: Set[ClaimField] = Set[ClaimField](Iss, Sub, Aud, Iat, Exp, Jti)

    val log = Logger(LoggerFactory.getLogger(this.getClass))

    /**
      * Create a JWT.
      *
      * @param userIri   the user IRI that will be encoded into the token.
      * @param secretKey the secret key used for encoding.
      * @param longevity the token's longevity in days.
      * @return a [[String]] containg the JWT.
      */
    def createToken(userIri: IRI, secretKey: String, longevity: Long): String = {

        // create required headers
        val headers = Seq[HeaderValue](Typ("JWT"), Alg(algorithm))

        val now: Long = System.currentTimeMillis() / 1000l

        // calculate longevity (days)
        val nowPlusLongevity: Long = now + longevity * 60 * 60 * 24

        val identifier: String = UUID.randomUUID().toString()

        // Add required claims
        // Iss: issuer, Sub: subject, Aud: audience, Iat: ussued at, Exp: expier date, Jti: unique identifier
        val claims = Seq[ClaimValue](Iss("webapi"), Sub(userIri), Aud("webapi"), Iat(now), Exp(nowPlusLongevity), Jti(identifier))

        val jwt = new DecodedJwt(headers, claims)

        jwt.encodedAndSigned(secretKey)
    }

    /**
      * Validates a JWT by also taking the invalidation cache into account. The invalidation cache holds invalidated
      * tokens, which would otherwise validate. Further, it makes sure that the required headers and claims are present.
      *
      * @param token  the JWT.
      * @param secret the secret used to encode the token.
      * @return a [[Boolean]].
      */
    def validateToken(token: String, secret: String): Boolean = {

        if (CacheUtil.get[UserProfileV1](AUTHENTICATION_INVALIDATION_CACHE_NAME, token).nonEmpty) {
            // token invalidated so no need to decode
            log.debug("validateToken - token found in invalidation cache, so not valid")
            false
        } else {
            DecodedJwt.validateEncodedJwt(
                jwt = token,
                key = secret,
                requiredAlg = algorithm,
                requiredHeaders = requiredHeaders,
                requiredClaims = requiredClaims,
                iss = Some(Iss("webapi")),
                aud = Some(Aud("webapi"))
            ).isSuccess
        }
    }

    /**
      * Extract the encoded user IRI. Further, it makes sure that the required headers and claims are present.
      *
      * @param token  the JWT.
      * @param secret the secret used to encode the token.
      * @return an optional [[IRI]].
      */
    def extractUserIriFromToken(token: String, secret: String): Option[IRI] = {

        DecodedJwt.validateEncodedJwt(
            jwt = token,
            key = secret,
            requiredAlg = algorithm,
            requiredHeaders = requiredHeaders,
            requiredClaims = requiredClaims,
            iss = Some(Iss("webapi")),
            aud = Some(Aud("webapi"))
        ) match {
            case Success(jwt) => jwt.getClaim[Sub].map(_.value)
            case _ => None
        }
    }

}

