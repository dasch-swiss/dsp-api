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

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, HttpCookiePair}
import akka.http.scaladsl.server.RequestContext
import akka.pattern._
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.Logger
import io.igl.jwt._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.routing.authenticationmessages.{KnoraCredentialsV1, KnoraPasswordCredentialsV1, KnoraSessionCredentialsV1, SessionV1}
import org.knora.webapi.messages.v2.routing.authenticationmessages.{KnoraCredentialsV2, KnoraPasswordCredentialsV2, KnoraTokenCredentialsV2, SessionV2}
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

        val credentials: KnoraCredentialsV1 = extractCredentialsV1(requestContext)

        val settings = Settings(system)

        val userProfile = authenticateCredentialsV1(credentials) // will throw exception if not valid and thus trigger the correct response

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
      * @param credentials the user supplied [[KnoraCredentialsV1]] containing the user's login information.
      * @param system      the current [[ActorSystem]]
      * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
      *         the generated session id.
      */
    def doLoginV2(credentials: KnoraPasswordCredentialsV2)(implicit system: ActorSystem, executionContext: ExecutionContext): HttpResponse = {

        authenticateCredentialsV2(
            KnoraCredentialsV2(
                passwordCredentials = Some(credentials)
            )
        ) // will throw exception if not valid and thus trigger the correct response

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

        val credentials = extractCredentialsV1(requestContext)

        val userProfileV1 = authenticateCredentialsV1(credentials)

        HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "status" -> JsNumber(0),
                    "message" -> JsString("credentials are OK"),
                    "userProfile" -> userProfileV1.ofType(UserProfileTypeV1.RESTRICTED).toJsValue
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

        val credentials: KnoraCredentialsV2 = extractCredentialsV2(requestContext)

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
      * Used to logout the user, i.e. returns a header deleting the cookie and removes the [[UserProfileV1]] from the
      * cache.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[HttpResponse]]
      */
    def doLogoutV1(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        val credentials = extractCredentialsV1(requestContext)

        if (credentials.sessionCredentials.nonEmpty) {
            // add token to invalidation list
            val token = credentials.sessionCredentials.get.token
            CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, token, token)
        }

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

    /**
      * Used to logout the user, i.e. removes the [[UserProfileV1]] from the cache and puts the token on the 'invalidated' list.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[HttpResponse]]
      */
    def doLogoutV2(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        val credentials = extractCredentialsV2(requestContext)

        if (credentials.tokenCredentials.nonEmpty) {
            // add token to invalidation list
            val token = credentials.tokenCredentials.get.token
            CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, token, token)
        }

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

        val credentials = extractCredentialsV1(requestContext)

        if (settings.skipAuthentication) {
            // return anonymous if skipAuthentication
            log.debug("getUserProfileV1 - Authentication skipping active, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else if (credentials.isEmpty) {
            log.debug("getUserProfileV1 - No credentials found, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else {
            authenticateCredentialsV1(credentials)
            log.debug("getUserProfileV1 - Supplied credentials pass authentication")

            val userProfileV1 = getUserProfileV1ByEmail(credentials.passwordCredentials.get.email)
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
      * Tries to authenticate the credentials (email/password) by getting the [[UserProfileV1]] from the triple store and checking if
      * the password matches. Caches the user profile after successful authentication under a generated id (JWT),
      * and returns that said id and user profile.
      *
      * @param credentials the user supplied and extracted credentials.
      * @param system      the current [[ActorSystem]]
      * @return a [[SessionV1]] which holds the generated id (JWT) and the user profile.
      * @throws BadCredentialsException when email or password are empty; when user is not active; when the password
      *                                 did not match.
      */
    private def authenticateCredentialsV1(credentials: KnoraCredentialsV1)(implicit system: ActorSystem, executionContext: ExecutionContext): UserProfileV1 = {

        // check if email and password are provided
        val passwordCreds = if (credentials.passwordCredentials.nonEmpty) {
            credentials.passwordCredentials.get
        } else {
            throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
        }

        val userProfileV1 = getUserProfileV1ByEmail(passwordCreds.email)
        //log.debug(s"authenticateCredentials - userProfileV1: $userProfileV1")

        /* check if the user is active, if not, then no need to check the password */
        if (!userProfileV1.isActive) {
            log.debug("authenticateCredentialsV1 - user is not active")
            throw BadCredentialsException(BAD_CRED_USER_INACTIVE)
        }

        /* check the password and store it in the cache */
        if (userProfileV1.passwordMatch(passwordCreds.password)) {
            // create JWT and cache user profile under this id
            log.debug("authenticateCredentialsV1 - password matched")
            userProfileV1
        } else {
            log.debug(s"authenticateCredentialsV1 - password did not match")
            throw BadCredentialsException(BAD_CRED_PASSWORD_MISMATCH)
        }
    }

    /**
      * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
      * authentication is performed checking if the supplied email/password combination is valid by retrieving the
      * user's profile. In the case of the token, the token itself is validated.
      *
      * @param credentials the user supplied and extracted credentials.
      * @param system      the current [[ActorSystem]]
      * @return true if the credentials are valid. If the credentials are invalid, then the coresponding exception
      *         will be thrown.
      * @throws BadCredentialsException when email or password and token are not supplied; when user is not active;
      *                                 when the password does not match; when the supplied token is not valid.
      */
    private def authenticateCredentialsV2(credentials: KnoraCredentialsV2)(implicit system: ActorSystem, executionContext: ExecutionContext): Boolean = {

        val settings = Settings(system)

        if (credentials.isEmpty) {
            throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
        }

        val passValid: Boolean = if (credentials.passwordCredentials.nonEmpty) {

            val pc = credentials.passwordCredentials.get
            val userProfile = getUserProfileV1ByEmail(pc.email)

            /* check if the user is active, if not, then no need to check the password */
            if (!userProfile.isActive) {
                log.debug("authenticateCredentials - user is not active")
                throw BadCredentialsException(BAD_CRED_USER_INACTIVE)
            }

            userProfile.passwordMatch(pc.password)
        } else {
            false
        }

        val tokenValid: Boolean = if (credentials.tokenCredentials.nonEmpty) {

            val token = credentials.tokenCredentials.get.token

            JWTHelper.validateToken(token, settings.jwtSecretKey)
        } else {
            false
        }

        if (credentials.passwordCredentials.nonEmpty && credentials.tokenCredentials.nonEmpty && passValid && tokenValid) {
            // both email/password and token where supplied and both are valid
            log.debug("authenticateCredentialsV2 - both email/password and token where supplied and both are valid")
            true
        } else if (credentials.passwordCredentials.nonEmpty && credentials.tokenCredentials.isEmpty && passValid) {
            // only email/password supplied and valid
            log.debug("authenticateCredentialsV2 - email/password supplied and valid")
            true
        } else if (credentials.passwordCredentials.isEmpty && credentials.tokenCredentials.nonEmpty && tokenValid) {
            // only token supplied and valid
            log.debug("authenticateCredentialsV2 - token supplied and valid")
            true
        } else {
            // either both supplied but one of them not valid or one supplied and this one not valid
            log.debug("authenticateCredentialsV2 - not valid")
            throw BadCredentialsException(BAD_CRED_NOT_VALID)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // HELPER METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Tries to extract the credentials from the requestContext (parameters, auth headers, session)
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @return [[KnoraCredentialsV1]].
      */
    private def extractCredentialsV1(requestContext: RequestContext): KnoraCredentialsV1 = {
        log.debug("extractCredentialsV1 start ...")

        val credentialsFromParameters = extractCredentialsFromParametersV1(requestContext)
        log.debug("extractCredentialsV1 - credentialsFromParameters: {}", credentialsFromParameters)

        val credentialsFromHeader = extractCredentialsFromHeaderV1(requestContext)
        log.debug("extractCredentialsV1 - credentialsFromHeader: {}", credentialsFromHeader)

        // return found credentials based on precedence: 1. url parameters, 2. header (basic auth, session)
        val credentials = if (credentialsFromParameters.nonEmpty) {
            credentialsFromParameters
        } else if (credentialsFromHeader.nonEmpty) {
            credentialsFromHeader
        } else {
            KnoraCredentialsV1()
        }

        log.debug("extractCredentialsV1 - returned credentials: '{}'", credentials)
        credentials
    }

    /**
      * Tries to extract the credentials from the requestContext (parameters, auth headers, token)
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @return [[KnoraCredentialsV1]].
      */
    private def extractCredentialsV2(requestContext: RequestContext): KnoraCredentialsV2 = {
        log.debug("extractCredentialsV2 start ...")

        val credentialsFromParameters = extractCredentialsFromParametersV2(requestContext)
        log.debug("extractCredentialsV2 - credentialsFromParameters: {}", credentialsFromParameters)

        val credentialsFromHeader = extractCredentialsFromHeaderV2(requestContext)
        log.debug("extractCredentialsV2 - credentialsFromHeader: {}", credentialsFromHeader)

        // return found credentials based on precedence: 1. url parameters, 2. header (basic auth, token)
        val credentials = if (credentialsFromParameters.nonEmpty) {
            credentialsFromParameters
        } else if (credentialsFromHeader.nonEmpty) {
            credentialsFromHeader
        } else {
            KnoraCredentialsV2()
        }

        log.debug("extractCredentialsV2 - returned credentials: '{}'", credentials)
        credentials
    }

    /**
      * Tries to extract credentials supplied as URL parameters.
      *
      * @param requestContext the HTTP request context.
      * @return [[KnoraCredentialsV1]].
      */
    private def extractCredentialsFromParametersV1(requestContext: RequestContext): KnoraCredentialsV1 = {
        // extract email/password from parameters

        val params: Map[String, Seq[String]] = requestContext.request.uri.query().toMultiMap

        val maybeEmail: Option[String] = params get "email" map (_.head)
        val maybePassword: Option[String] = params get "password" map (_.head)

        val maybePassCreds: Option[KnoraPasswordCredentialsV1] = if (maybeEmail.nonEmpty && maybePassword.nonEmpty) {
            Some(KnoraPasswordCredentialsV1(maybeEmail.get, maybePassword.get))
        } else {
            None
        }

        KnoraCredentialsV1(
            passwordCredentials = maybePassCreds
        )
    }

    /**
      * Tries to extract credentials supplied as URL parameters.
      *
      * @param requestContext the HTTP request context.
      * @return [[KnoraCredentialsV1]].
      */
    private def extractCredentialsFromParametersV2(requestContext: RequestContext): KnoraCredentialsV2 = {
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

        KnoraCredentialsV2(maybePassCreds, maybeTokenCreds)
    }

    /**
      * Tries to extract the credentials (email/password, session id) from the headers.
      *
      * @param requestContext the HTTP request context.
      * @return optionally the session id.
      */
    private def extractCredentialsFromHeaderV1(requestContext: RequestContext): KnoraCredentialsV1 = {

        // Session ID
        val cookies: Seq[HttpCookiePair] = requestContext.request.cookies
        val maybeSessionId: Option[String] = cookies.find(_.name == "KnoraAuthentication") match {
            case Some(authCookie) =>
                val value = authCookie.value
                Some(value)
            case None =>
                None
        }

        val maybeSessionCreds: Option[KnoraSessionCredentialsV1] = if (maybeSessionId.nonEmpty) {
            Some(KnoraSessionCredentialsV1(maybeSessionId.get))
        } else {
            None
        }

        // Email/Password from basic auth
        val headers: Seq[HttpHeader] = requestContext.request.headers
        val (maybeEmail, maybePassword) = headers.find(_.name == "Authorization") match {
            case Some(authHeader: HttpHeader) =>

                // the authorization header can hold different schemes
                val credsArr: Array[String] = authHeader.value().split(",")

                // in v1 we only support the basic authentication scheme
                val maybeBasicAuthValue = credsArr.find(_.contains("Basic"))

                // try to decode email/password
                maybeBasicAuthValue match {
                    case Some(value) =>
                        val trimmedValue = value.substring(5).trim() // remove 'Basic '
                        val decodedValue = ByteString.fromArray(Base64.getDecoder.decode(trimmedValue)).decodeString("UTF8")
                        val decodedValueArr = decodedValue.split(":", 2)
                        (Some(decodedValueArr(0)), Some(decodedValueArr(1)))
                    case None =>
                        (None, None)
                }
            case None =>
                (None, None)
        }

        val maybePassCreds: Option[KnoraPasswordCredentialsV1] = if (maybeEmail.nonEmpty && maybePassword.nonEmpty) {
            Some(KnoraPasswordCredentialsV1(maybeEmail.get, maybePassword.get))
        } else {
            None
        }

        KnoraCredentialsV1(
            passwordCredentials = maybePassCreds,
            sessionCredentials = maybeSessionCreds
        )
    }

    /**
      * Tries to extract the credentials (email/password, token) from the authorization header.
      *
      * The header looks something like this: 'Authorization: Basic xyz, Bearer xyz.xyz.xyz'
      * if both the email/password and token are sent.
      *
      * The possibilities are: only Basic, only Bearer or Basic and Bearer together
      *
      * @param requestContext the HTTP request context.
      * @return [[KnoraCredentialsV2]].
      */
    private def extractCredentialsFromHeaderV2(requestContext: RequestContext): KnoraCredentialsV2 = {

        val headers: Seq[HttpHeader] = requestContext.request.headers
        headers.find(_.name == "Authorization") match {
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

                KnoraCredentialsV2(maybePassCreds, maybeTokenCreds)

            case None =>
                KnoraCredentialsV2()
        }
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

    val algorithm = Algorithm.HS256
    val requiredHeaders: Set[HeaderField] = Set[HeaderField](Typ)
    val requiredClaims: Set[ClaimField] = Set[ClaimField](Iss, Sub, Aud, Iat, Exp)

    /**
      * Create a JWT.
      *
      * @param userIri   the user IRI that will be encoded into the token.
      * @param secretKey the secret key used for encoding.
      * @param longevity the token's longevity in days.
      * @return a [[String]] containg the JWT.
      */
    def createToken(userIri: IRI, secretKey: String, longevity: Long): String = {

        val headers = Seq[HeaderValue](Typ("JWT"), Alg(algorithm))

        val now: Long = System.currentTimeMillis() / 1000l
        val nowPlusLongevity: Long = now + longevity * 60 * 60 * 24

        val claims = Seq[ClaimValue](Iss("webapi"), Sub(userIri), Aud("webapi"), Iat(now), Exp(nowPlusLongevity))

        val jwt = new DecodedJwt(headers, claims)

        jwt.encodedAndSigned(secretKey)
    }

    /**
      * Validates a JWT by also taking the invalidation cache into account. The invalidation cache holds invalidated
      * tokens, which would otherwise validate.
      *
      * @param token  the JWT.
      * @param secret the secret used to encode the token.
      * @return a [[Boolean]].
      */
    def validateToken(token: String, secret: String): Boolean = {

        if (CacheUtil.get[UserProfileV1](AUTHENTICATION_INVALIDATION_CACHE_NAME, token).nonEmpty) {
            // token invalidated so no need to decode
            false
        } else {
            DecodedJwt.validateEncodedJwt(
                token,
                secret,
                algorithm,
                requiredHeaders,
                requiredClaims,
                iss = Some(Iss("webapi")),
                aud = Some(Aud("webapi"))
            ).isSuccess
        }
    }

    /**
      * Extract the encoded user IRI.
      *
      * @param token  the JWT.
      * @param secret the secret used to encode the token.
      * @return an optional [[IRI]].
      */
    def extractUserIriFromToken(token: String, secret: String): Option[IRI] = {

        DecodedJwt.validateEncodedJwt(
            token,
            secret,
            algorithm,
            requiredHeaders,
            requiredClaims,
            iss = Some(Iss("webapi")),
            aud = Some(Aud("webapi"))
        ) match {
            case Success(jwt) => jwt.getClaim[Sub].map(_.value)
            case _ => None
        }
    }

}

