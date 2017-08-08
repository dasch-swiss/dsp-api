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
import org.knora.webapi.messages.v1.routing.authenticationmessages.{KnoraCredentialsV1, SessionV1}
import org.knora.webapi.messages.v2.routing.authenticationmessages.{KnoraCredentialsV2, SessionV2}
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

        // check if session was created
        val (sId, userProfile) = authenticateCredentialsV1(credentials) match {
            case session@SessionV1(id, profile) => {
                writeUserProfileV1ToCache(session)
                (id, profile)
            }
            case _ => throw AuthenticationException("Session ID not created. Please report this as a possible bug.")
        }

        HttpResponse(
            headers = List(headers.`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sId, path = Some("/")))), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "status" -> JsNumber(0),
                    "message" -> JsString("credentials are OK"),
                    "sid" -> JsString(sId),
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
    def doLoginV2(credentials: KnoraCredentialsV2)(implicit system: ActorSystem, executionContext: ExecutionContext): HttpResponse = {

        val token = authenticateCredentialsV2(credentials) match {
            case session@SessionV2(token, _) => {
                writeUserProfileV2ToCache(session)
                token
            }
            case _ => throw AuthenticationException("Session ID not created. Please report this as a possible bug.")
        }

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
    // Session Authentication ENTRY POINT
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Checks if the provided session id is valid, i.e. if a [[UserProfileV1]] can be retrieved from the cache for the
      * supplied session id.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @return a [[HttpRequest]]
      */
    def doSessionAuthenticationV1(requestContext: RequestContext)(implicit system: ActorSystem, executionContext: ExecutionContext): HttpResponse = {

        val credentials: KnoraCredentialsV1 = extractCredentialsV1(requestContext)

        getUserProfileV1FromCache(credentials) match {
            case Some(userProfile) =>
                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("session credentials are OK"),
                            "userProfile" -> userProfile.ofType(UserProfileTypeV1.RESTRICTED).toJsValue
                        ).compactPrint
                    )
                )
            case None =>
                HttpResponse(
                    status = StatusCodes.Unauthorized,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(2),
                            "message" -> JsString("session credentials not OK: invalid session ID")
                        ).compactPrint
                    )
                )
        }
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

        val userProfileV1 = authenticateCredentialsV1(credentials).userProfileV1

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

        val credentials = extractCredentialsV2(requestContext)

        val token = authenticateCredentialsV2(credentials).token

        HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "status" -> JsNumber(0),
                    "message" -> JsString("credentials are OK"),
                    "token" -> JsString(token)
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

        val cookies: Seq[HttpCookiePair] = requestContext.request.cookies
        cookies.find(_.name == "KnoraAuthentication") match {
            case Some(authCookie) =>
                // maybe the value is in the cache or maybe it expired in the meantime.
                CacheUtil.remove(AUTHENTICATION_CACHE_NAME, authCookie.value)
            case None => // no cookie, so I can't do anything really
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

        val headers: Seq[HttpHeader] = requestContext.request.headers
        headers.find(_.name == "Authorization") match {
            case Some(authHeader: HttpHeader) =>
                val token = authHeader.value().substring(0,5).trim() // remove 'Bearer '
                log.debug("doLogoutV2 - invalidating token: {}", token)

                // remove the user's profile from cache
                CacheUtil.remove(AUTHENTICATION_CACHE_NAME, token)

                // add token to invalidation list
                CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, token, token)
            case None => {} // not token sent, so nothing to remove or invalidate
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
      * The credentials can be email/password as parameters, auth headers, or email in a cookie if the profile is
      * found in the cache. If no credentials are found, then a default UserProfile is returned. If the credentials
      * are not correct, then the corresponding error is returned.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[UserProfileV1]]
      */
    def getUserProfileV1(requestContext: RequestContext)(implicit system: ActorSystem, executionContext: ExecutionContext): UserProfileV1 = {

        val settings = Settings(system)

        val credentials = extractCredentialsV1(requestContext)

        val userProfileV1: UserProfileV1 = if (settings.skipAuthentication) {
            // return anonymous if skipAuthentication
            log.debug("Authentication skipping active, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else if (credentials.isEmpty) {
            log.debug("No credentials found, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else {
            // let us first try to get the user profile through the session id if available in the credentials
            getUserProfileV1FromCache(credentials) match {
                case Some(userProfile: UserProfileV1) =>
                    log.debug(s"Got this UserProfileV1 through the session id: '${userProfile.toString}'")
                    /* we return the userProfileV1 without sensitive information */
                    userProfile.ofType(UserProfileTypeV1.RESTRICTED)
                case None => {
                    log.debug("No session id found or not valid, so let's try with email / password")
                    val session: SessionV1 = authenticateCredentialsV1(credentials)
                    log.debug("Supplied credentials pass authentication")

                    val userProfileV1 = session.userProfileV1
                    log.debug(s"I got a UserProfileV1: {}", userProfileV1.toString)

                    /* we return the userProfileV1 without sensitive information */
                    userProfileV1.ofType(UserProfileTypeV1.RESTRICTED)
                }
            }
        }

        userProfileV1
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
    val BAD_CRED_EMAIL_PASSWORD_NOT_AVAILABLE = "bad credentials: none found"
    val BAD_CRED_USER_INACTIVE = "bad credentials: user inactive"

    val KNORA_AUTHENTICATION_COOKIE_NAME = "KnoraAuthentication"
    val AUTHENTICATION_CACHE_NAME = "authenticationCache"
    val AUTHENTICATION_INVALIDATION_CACHE_NAME = "authenticationInvalidationCache"

    val sessionStore: scala.collection.mutable.Map[String, UserProfileV1] = scala.collection.mutable.Map()
    implicit val timeout: Timeout = Duration(5, SECONDS)
    val log = Logger(LoggerFactory.getLogger(this.getClass))

    /**
      * Tries to authenticate the credentials by getting the [[UserProfileV1]] from the triple store and checking if
      * the password matches. Caches the user profile after successful authentication under a generated id (JWT),
      * and returns that said id and user profile.
      *
      * @param credentials the user supplied and extracted credentials.
      * @param system      the current [[ActorSystem]]
      * @return a [[SessionV1]] which holds the generated id (JWT) and the user profile.
      * @throws BadCredentialsException when email or password are empty; when user is not active; when the password
      *                                 did not match.
      */
    private def authenticateCredentialsV1(credentials: KnoraCredentialsV1)(implicit system: ActorSystem, executionContext: ExecutionContext): SessionV1 = {

        val settings = Settings(system)

        // check if email and password are provided
        if (credentials.email.isEmpty || credentials.password.isEmpty) {
            throw BadCredentialsException(BAD_CRED_EMAIL_PASSWORD_NOT_AVAILABLE)
        }

        val userProfileV1 = getUserProfileV1ByEmail(credentials.email.get)
        //log.debug(s"authenticateCredentials - userProfileV1: $userProfileV1")

        /* check if the user is active, if not, then no need to check the password */
        if (!userProfileV1.isActive) {
            log.debug("authenticateCredentials - user is not active")
            throw BadCredentialsException(BAD_CRED_USER_INACTIVE)
        }

        /* check the password and store it in the cache */
        if (userProfileV1.passwordMatch(credentials.password.get)) {
            // create JWT and cache user profile under this id
            log.debug("authenticateCredentials - password matched")
            val sId = JWTHelper.createToken(userProfileV1.userData.user_id.get, settings.jwtSecretKey, settings.jwtLongevity)
            SessionV1(sId, userProfileV1)
        } else {
            log.debug(s"authenticateCredentials - password did not match")
            throw BadCredentialsException(BAD_CRED_PASSWORD_MISMATCH)
        }
    }

    /**
      * Tries to authenticate the credentials by getting the [[UserProfileV1]] from the triple store and checking if
      * the password matches. Caches the user profile after successful authentication under a generated id (JWT),
      * and returns that said id and user profile.
      *
      * @param credentials the user supplied and extracted credentials.
      * @param system      the current [[ActorSystem]]
      * @return a [[SessionV1]] which holds the generated id (JWT) and the user profile.
      * @throws BadCredentialsException when email or password are empty; when user is not active; when the password
      *                                 did not match.
      */
    private def authenticateCredentialsV2(credentials: KnoraCredentialsV2)(implicit system: ActorSystem, executionContext: ExecutionContext): SessionV2 = {

        val settings = Settings(system)

        // FIXME: extend this, as I can now receive email/password or token as credentials

        // check if email and password are provided
        if (credentials.email.isEmpty || credentials.password.isEmpty) {
            throw BadCredentialsException(BAD_CRED_EMAIL_PASSWORD_NOT_AVAILABLE)
        }

        val userProfileV1 = getUserProfileV1ByEmail(credentials.email.get)
        //log.debug(s"authenticateCredentials - userProfileV1: $userProfileV1")

        /* check if the user is active, if not, then no need to check the password */
        if (!userProfileV1.isActive) {
            log.debug("authenticateCredentials - user is not active")
            throw BadCredentialsException(BAD_CRED_USER_INACTIVE)
        }

        /* check the password and store it in the cache */
        if (userProfileV1.passwordMatch(credentials.password.get)) {
            // create JWT and cache user profile under this id
            log.debug("authenticateCredentials - password matched")
            val token = JWTHelper.createToken(userProfileV1.userData.user_id.get, settings.jwtSecretKey, settings.jwtLongevity)
            SessionV2(token, userProfileV1)
        } else {
            log.debug(s"authenticateCredentials - password did not match")
            throw BadCredentialsException(BAD_CRED_PASSWORD_MISMATCH)
        }
    }

    /**
      * Writes the user's profile to cache.
      *
      * @param session a [[SessionV1]] which holds the identifier (JWT) and the user profile.
      * @return true if writing was successful.
      * @throws ApplicationCacheException when there is a problem writing the user's profile to cache.
      */
    private def writeUserProfileV1ToCache(session: SessionV1): Boolean = {
        CacheUtil.put(AUTHENTICATION_CACHE_NAME, session.sid, session.userProfileV1)
        if (CacheUtil.get(AUTHENTICATION_CACHE_NAME, session.sid).nonEmpty) {
            true
        } else {
            throw ApplicationCacheException("Writing the user's profile to cache was not successful.")
        }
    }


    /**
      * Writes the user's profile to cache.
      *
      * @param session a [[SessionV2]] which holds the identifier (JWT) and the user profile.
      * @return true if writing was successful.
      * @throws ApplicationCacheException when there is a problem writing the user's profile to cache.
      */
    private def writeUserProfileV2ToCache(session: SessionV2): Boolean = {
        CacheUtil.put(AUTHENTICATION_CACHE_NAME, session.token, session.userProfile)
        if (CacheUtil.get(AUTHENTICATION_CACHE_NAME, session.token).nonEmpty) {
            true
        } else {
            throw ApplicationCacheException("Writing the user's profile to cache was not successful.")
        }
    }

    /**
      * Try to get the [[UserProfileV1]] if still in the cache, by using the session id. Since the session
      * if is a JWT, we first check if the token is valid, and only then try to use it to get the user's
      * profile from the cache.
      *
      * @param knoraCredentials the user supplied credentials.
      * @return a [[ Option[UserProfileV1] ]]
      */
    private def getUserProfileV1FromCache(knoraCredentials: KnoraCredentialsV1)(implicit system: ActorSystem, executionContext: ExecutionContext): Option[UserProfileV1] = {

        val settings = Settings(system)

        knoraCredentials match {
            case KnoraCredentialsV1(_, _, Some(sessionId)) =>
                if(JWTHelper.validateToken(sessionId, settings.jwtSecretKey)) {
                    val value: Option[UserProfileV1] = CacheUtil.get[UserProfileV1](AUTHENTICATION_CACHE_NAME, sessionId)
                    log.debug(s"getUserProfileV1FromCache- Found this session id: {} leading to this content in the cache: {}", sessionId, value)
                    value
                } else {
                    log.debug("getUserProfileV1FromCache - Session id invalid")
                    None
                }
            case _ =>
                log.debug(s"getUserProfileV1FromCache - No session id found inside the credentials.")
                None
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

        KnoraCredentialsV1(maybeEmail, maybePassword)
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
        val maybeToken: Option[String] = params get "token" map (_.head)

        KnoraCredentialsV2(maybeEmail, maybePassword, maybeToken)
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

        KnoraCredentialsV1(maybeEmail, maybePassword, maybeSessionId)
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

                // and the bearer scheme
                val maybeToken = credsArr.find(_.contains("Bearer")) match {
                    case Some(value) =>
                        Some(value.substring(6).trim()) // remove 'Bearer '
                    case None =>
                        None
                }

                KnoraCredentialsV2(maybeEmail, maybePassword, maybeToken)
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
            // try to get it from the cache
            CacheUtil.get[UserProfileV1](AUTHENTICATION_CACHE_NAME, email) match {
                case Some(userProfile) =>
                    // found a user profile in the cache
                    log.debug(s"getUserProfileV1ByEmail - cache hit: $userProfile")
                    userProfile
                case None =>
                    // didn't find one, so I will try to get it from the triple store
                    val userProfileV1Future = for {
                        maybeUserProfileV1 <- (responderManager ? UserProfileByEmailGetV1(email, UserProfileTypeV1.FULL)).mapTo[Option[UserProfileV1]]
                        userProfileV1 = maybeUserProfileV1 match {
                            case Some(up) => up
                            case None => {
                                log.debug(s"getUserProfileV1ByEmail - supplied email not found - throwing exception")
                                throw BadCredentialsException(s"$BAD_CRED_USER_NOT_FOUND")
                            }
                        }
                        _ = CacheUtil.put(AUTHENTICATION_CACHE_NAME, email, userProfileV1)
                        _ = log.debug(s"getUserProfileV1ByEmail - from triplestore: $userProfileV1")
                    } yield userProfileV1

                    // TODO: return the future here instead of using Await.
                    Await.result(userProfileV1Future, Duration(3, SECONDS))
            }
        } else {
            throw BadCredentialsException(BAD_CRED_EMAIL_NOT_SUPPLIED)
        }
    }
}

object JWTHelper {

    val algorithm = Algorithm.HS256
    val requiredHeaders = Set[HeaderField](Typ)
    val requiredClaims = Set[ClaimField](Iss, Sub, Aud, Iat, Exp)

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
      * Validate a JWT.
      *
      * @param token  the JWT.
      * @param secret the secret used to encode the token.
      * @return a [[Boolean]].
      */
    def validateToken(token: String, secret: String): Boolean = {

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

