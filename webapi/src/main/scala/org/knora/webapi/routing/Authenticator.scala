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
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, HttpCookiePair}
import akka.http.scaladsl.server.RequestContext
import akka.pattern._
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.Logger
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.util.CacheUtil
import org.slf4j.LoggerFactory
import spray.json.{JsNumber, JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Success, Try}


// needs Java 1.8 !!!
import java.util.Base64


/**
  * Represents credentials that a user can supply.
  *
  * @param email     the optionally supplied email.
  * @param password  the optionally supplied password.
  * @param sessionId the optionally supplied session id.
  */
case class KnoraCredentials(email: Option[String] = None,
                            password: Option[String] = None,
                            sessionId: Option[String] = None) {
    def isEmpty: Boolean = this.email.isEmpty && this.password.isEmpty && this.sessionId.isEmpty
}

/**
  * Represents the session containing the id under which a user profile is stored and the user profile itself.
  *
  * @param sid           the session id.
  * @param userProfileV1 the [[UserProfileV1]] the session id is referring to.
  */
case class SessionV1(sid: Option[String], userProfileV1: UserProfileV1)

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

        val credentials: KnoraCredentials = extractCredentials(requestContext)

        // check if session was created
        val (sId, userProfile) = authenticateCredentialsV1(credentials, sessionEnabled = true) match {
            case SessionV1(Some(sId), userProfile) => (sId, userProfile)
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
    def doSessionAuthenticationV1(requestContext: RequestContext): HttpResponse = {

        val credentials: KnoraCredentials = extractCredentials(requestContext)

        getUserProfileV1FromSessionCache(credentials) match {
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

        val credentials = extractCredentials(requestContext)

        val userProfileV1 = authenticateCredentialsV1(credentials, sessionEnabled = false) match {
            case SessionV1(_, userProfileV1) => userProfileV1
            case _ => throw AuthenticationException()
        }

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

        val credentials = extractCredentials(requestContext)

        val userProfileV1: UserProfileV1 = if (settings.skipAuthentication) {
            // return anonymous if skipAuthentication
            log.debug("Authentication skipping active, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else if (credentials.isEmpty) {
            log.debug("No credentials found, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
            UserProfileV1()
        } else {
            // let us first try to get the user profile through the session id if available in the credentials
            getUserProfileV1FromSessionCache(credentials) match {
                case Some(userProfile: UserProfileV1) =>
                    log.debug(s"Got this UserProfileV1 through the session id: '${userProfile.toString}'")
                    /* we return the userProfileV1 without sensitive information */
                    userProfile.ofType(UserProfileTypeV1.RESTRICTED)
                case None => {
                    log.debug("No session id found or not valid, so let's try with email / password")
                    val session: SessionV1 = authenticateCredentialsV1(credentials, sessionEnabled = false)
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
    val BAD_CRED_EMAIL_PASSWORD_NOT_EXTRACTABLE = "bad credentials: none found"
    val BAD_CRED_USER_INACTIVE = "bad credentials: user inactive"

    val KNORA_AUTHENTICATION_COOKIE_NAME = "KnoraAuthentication"
    val AUTHENTICATION_CACHE_NAME = "authenticationCache"

    val sessionStore: scala.collection.mutable.Map[String, UserProfileV1] = scala.collection.mutable.Map()
    implicit val timeout: Timeout = Duration(5, SECONDS)
    val log = Logger(LoggerFactory.getLogger(this.getClass))

    /**
      * Tries to authenticate the credentials by getting the [[UserProfileV1]] from the triple store and checking if the
      * password matches. Caches the user profile after successful authentication under a generated session id if
      * 'sessionEnabled=true', and returns that said session id and user profile.
      *
      * @param credentials    the user supplied and extracted credentials.
      * @param sessionEnabled a [[Boolean]] if set true then a session id will be created and the user profile cached
      * @param system         the current [[ActorSystem]]
      * @return a [[Try[String]] which is the session id under which the profile is stored if authentication was successful.
      */
    private def authenticateCredentialsV1(credentials: KnoraCredentials, sessionEnabled: Boolean)(implicit system: ActorSystem, executionContext: ExecutionContext): SessionV1 = {

        // check if email and password are provided
        if (credentials.email.isEmpty || credentials.password.isEmpty) {
            throw BadCredentialsException(BAD_CRED_EMAIL_PASSWORD_NOT_EXTRACTABLE)
        }

        val userProfileV1 = getUserProfileV1ByEmail(credentials.email.get)
        //log.debug(s"authenticateCredentials - userProfileV1: $userProfileV1")

        /* check if the user is active, if not, then no need to check the password */
        if (!userProfileV1.isActive) {
            log.debug("authenticateCredentials - user is not active")
            throw BadCredentialsException(BAD_CRED_USER_INACTIVE)
        }

        /* check the password and if session is started, store it in the cache */
        if (userProfileV1.passwordMatch(credentials.password.get)) {
            // create session id and cache user profile under this id
            log.debug("authenticateCredentials - password matched")
            if (sessionEnabled) {
                val sId = UUID.randomUUID().toString
                CacheUtil.put(AUTHENTICATION_CACHE_NAME, sId, userProfileV1)
                SessionV1(Some(sId), userProfileV1)
            } else {
                SessionV1(None, userProfileV1)
            }
        } else {
            log.debug(s"authenticateCredentials - password did not match")
            throw BadCredentialsException(BAD_CRED_PASSWORD_MISMATCH)
        }
    }

    /**
      * Try to get the session id from the cookie and return a [[UserProfileV1]] if still in the cache.
      *
      * @param knoraCredentials the user supplied credentials.
      * @return a [[ Option[UserProfileV1] ]]
      */
    private def getUserProfileV1FromSessionCache(knoraCredentials: KnoraCredentials): Option[UserProfileV1] = {
        knoraCredentials match {
            case KnoraCredentials(_, _, Some(sessionId)) =>
                val value: Option[UserProfileV1] = CacheUtil.get[UserProfileV1](AUTHENTICATION_CACHE_NAME, sessionId)
                log.debug(s"Found this session id: $sessionId leading to this content in the cache: $value")
                value
            case _ =>
                log.debug(s"No session id found")
                None
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // HELPER METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Tries to extract the credentials from the requestContext (parameters, auth headers)
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @return [[KnoraCredentials]]
      */
    private def extractCredentials(requestContext: RequestContext): KnoraCredentials = {
        log.debug("extractCredentials start ...")

        val cookies: Seq[HttpCookiePair] = requestContext.request.cookies
        val sessionId: Option[String] = cookies.find(_.name == "KnoraAuthentication") match {
            case Some(authCookie) =>
                val value = authCookie.value
                log.debug(s"Found this session id: $value")
                Some(value)
            case None =>
                log.debug(s"No session id found")
                None
        }

        val params: Map[String, Seq[String]] = requestContext.request.uri.query().toMultiMap
        val headers: Seq[HttpHeader] = requestContext.request.headers


        // extract email / password from parameters
        val emailFromParams: Option[String] = params get "email" map (_.head)

        val passwordFromParams: Option[String] = params get "password" map (_.head)

        if (emailFromParams.nonEmpty && passwordFromParams.nonEmpty) {
            log.debug("emailFromParams: " + emailFromParams)
            log.debug("passwordFromParams: " + passwordFromParams)
        } else {
            log.debug("no credentials sent as parameters")
        }

        // extract email / password from the auth header
        val authHeaderList = headers filter (httpHeader => httpHeader.lowercaseName.equals("authorization"))
        val authHeaderEncoded = if (authHeaderList.nonEmpty) {
            authHeaderList.head.value.substring(6)
        } else {
            ""
        }

        log.debug(s"authHeaderEncoded.nonEmpty: " + authHeaderEncoded.nonEmpty)

        val (emailFromHeader: Option[String], passwordFromHeader: Option[String]) = if (authHeaderEncoded.nonEmpty) {
            val authHeaderDecoded = ByteString.fromArray(Base64.getDecoder.decode(authHeaderEncoded)).decodeString("UTF8")
            val authHeaderDecodedArr = authHeaderDecoded.split(":", 2)
            (Some(authHeaderDecodedArr(0)), Some(authHeaderDecodedArr(1)))
        } else {
            (None, None)
        }

        if (emailFromHeader.nonEmpty && passwordFromHeader.nonEmpty) {
            log.debug("emailFromHeader: " + emailFromHeader)
            log.debug("passwordFromHeader: " + passwordFromHeader)
        } else {
            log.debug("no credentials found in the header")
        }

        // get only one set of credentials based on precedence
        val (email: Option[String], password: Option[String]) = if (emailFromParams.nonEmpty && passwordFromParams.nonEmpty) {
            (emailFromParams, passwordFromParams)
        } else if (emailFromHeader.nonEmpty && passwordFromHeader.nonEmpty) {
            (emailFromHeader, passwordFromHeader)
        } else {
            (None, None)
        }

        (email, password) match {
            case (e, p) if e.nonEmpty && p.nonEmpty => {
                log.debug(s"found credentials: '$e' , '$p' ")
                KnoraCredentials(email = e, password = p, sessionId)
            }
            case _ if sessionId.nonEmpty => {
                log.debug("found session id: {}", sessionId.get)
                KnoraCredentials(sessionId = sessionId)
            }
            case _ => {
                log.debug("No credentials could be extracted")
                KnoraCredentials()
            }
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

