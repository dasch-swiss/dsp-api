/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
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
import akka.pattern._
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.Logger
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileByIRIGetRequestV1, UserProfileByUsernameGetRequestV1, UserProfileV1}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.util.CacheUtil
import org.knora.webapi.{IRI, InvalidCredentialsException, NotFoundException, Settings}
import org.slf4j.LoggerFactory
import spray.http._
import spray.json.{JsNumber, JsObject, JsString}
import spray.routing._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}


// needs Java 1.8 !!!
import java.util.Base64


/**
  * This trait is used in routes that need authentication support. It provides methods that use the [[RequestContext]]
  * to extract credentials, authenticate provided credentials, and look up cached credentials through the use of the
  * session id. All private methods used in this trait can be found in the companion object.
  */
trait Authenticator {

    /* Import companion object */

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
    def doLogin(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        extractCredentialsAndAuthenticate(requestContext, true) match {
            case Success(sId) =>

                val userProfile = getUserProfileV1(requestContext)

                HttpResponse(
                    headers = List(HttpHeaders.`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sId))),
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("credentials are OK"),
                            "sid" -> JsString(sId),
                            "userdata" -> userProfile.userData.toJsValue
                        ).compactPrint
                    )
                )
            case Failure(ex) =>
                HttpResponse(
                    status = StatusCodes.Unauthorized,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(2),
                            "message" -> JsString(ex.getMessage)
                        ).compactPrint
                    )
                )
        }
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
    def doSessionAuthentication(requestContext: RequestContext): HttpResponse = {
        getUserProfileV1FromSessionId(requestContext) match {
            case Some(userProfile) =>
                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("session credentials are OK"),
                            "userdata" -> userProfile.userData.toJsValue
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
    def doAuthenticate(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        extractCredentialsAndAuthenticate(requestContext, false) match {
            case Success(_) => {

                val userProfile = getUserProfileV1(requestContext)

                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("credentials are OK"),
                            "userdata" -> userProfile.userData.toJsValue
                        ).compactPrint
                    )
                )
            }
            case Failure(ex) => {
                log.debug(s"extractCredentialsAndAuthenticate returned Failure: ${ex.toString}")
                HttpResponse(
                    status = StatusCodes.Unauthorized,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(2),
                            "message" -> JsString(ex.getMessage)
                        ).compactPrint
                    )
                )
            }
        }
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
    def doLogout(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        val cookies: Seq[HttpCookie] = requestContext.request.cookies
        cookies.find(_.name == "KnoraAuthentication") match {
            case Some(authCookie) =>
                // maybe the value is in the cache or maybe it expired in the meantime.
                CacheUtil.remove(cacheName, authCookie.content)
            case None => // no cookie, so I can't do anything really
        }
        HttpResponse(
            headers = List(HttpHeaders.`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "deleted", expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))),
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
      * Returns a [[UserProfileV1]] matching the credentials found in the [[RequestContext]].
      * The credentials can be username/password as parameters, auth headers, or username in a cookie if the profile is
      * found in the cache. If no credentials are found, then a default [[UserProfileV1]] is returned. If the credentials
      * not correct, then the corresponding error is returned.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param system         the current [[ActorSystem]]
      * @return a [[UserProfileV1]]
      */
    def getUserProfileV1(requestContext: RequestContext)(implicit system: ActorSystem): UserProfileV1 = {
        val settings = Settings(system)
        val hardCodedUser: IRI = "http://data.knora.org/users/b83acc5f05" // testuser
        if (settings.skipAuthentication) {
            // skip authentication and return hardCodedUser
            getUserProfileByIri(hardCodedUser).getOrElse(UserProfileV1(UserDataV1(lang = settings.fallbackLanguage))).getCleanUserProfileV1
        }
        else {
            // let us first try to get the user profile through the session id from the cookie
            getUserProfileV1FromSessionId(requestContext) match {
                case Some(userProfile) =>
                    log.debug(s"Got this UserProfileV1 through the session id: '${userProfile.toString}'")
                    /* we return the userProfileV1 without sensitive information */
                    userProfile.getCleanUserProfileV1
                case None => {
                    log.debug("No cookie or valid session id, so let's look for supplied credentials")
                    extractCredentials(requestContext) match {
                        case Some((u, p)) =>
                            log.debug(s"found some credentials '$u', '$p', lets try to authenticate them first")
                            authenticateCredentials(u, p, session = false) match {
                                case Success(_) =>
                                    log.debug("Supplied credentials pass authentication, get the UserProfileV1")
                                    getUserProfileByUsername(u) match {
                                        case Success(userProfileV1: UserProfileV1) =>
                                            log.debug(s"I got a UserProfileV1 '${userProfileV1.toString}', which means that the password is a match")
                                            /* we return the userProfileV1 without sensitive information */
                                            userProfileV1.getCleanUserProfileV1
                                        case Failure(ex) =>
                                            log.debug(s"Something went wrong. Just throwing this exception: $ex")
                                            throw ex
                                    }
                                case Failure(ex) =>
                                    log.debug(s"Supplied credentials didn't pass authentication. Rethrowing this exception: $ex")
                                    throw ex
                            }
                        case None =>
                            log.debug("No credentials found, returning default UserProfileV1!")
                            UserProfileV1(UserDataV1(lang = settings.fallbackLanguage))
                    }
                }
            }
        }
    }
}

/**
  * This companion object holds all private methods used in the trait. This division is needed so that we can test
  * the private methods directly with scalatest as described in [1] and [3]
  *
  * [1] https://groups.google.com/forum/#!topic/scalatest-users/FeaO__f1dN4
  * [2] http://doc.scalatest.org/2.2.6/index.html#org.scalatest.PrivateMethodTester
  **/
object Authenticator {

    val INVALID_CREDENTIALS_USERNAME_OR_PASSWORD = "Invalid username or password"
    val INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED = "No username supplied"
    val INVALID_CREDENTIALS_NON_FOUND = "No credentials could be found"

    val KNORA_AUTHENTICATION_COOKIE_NAME = "KnoraAuthentication"

    val sessionStore: scala.collection.mutable.Map[String, UserProfileV1] = scala.collection.mutable.Map()
    implicit val timeout: Timeout = Duration(5, SECONDS)
    val log = Logger(LoggerFactory.getLogger("org.knora.webapi.util.authentication"))

    val cacheName = "authenticationCache"

    /**
      * Tries to extract and then authenticate the credentials.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @param session        a flag if set true then the creation of the session id and caching of the user profile will be skipped
      * @param system         the current [[ActorSystem]]
      * @return [[ Try[String] ]] containing the session id if successful, which means that the credentials could be
      *         extracted and authenticated. In the case where the credentials could not be extracted or could be
      *         extracted but not authenticated, a corresponding exception is thrown.
      */
    private def extractCredentialsAndAuthenticate(requestContext: RequestContext, session: Boolean)(implicit system: ActorSystem): Try[String] = {
        Try {
            extractCredentials(requestContext) match {
                case Some((u, p)) => {
                    log.debug(s"extractCredentials returned 'user: $u', and 'password: $p' ")
                    authenticateCredentials(u, p, session) match {
                        case Success(sId) => sId
                        case Failure(ex) => throw ex
                    }
                }
                case None => {
                    log.debug(s"extractCredentials returned 'None'")
                    throw InvalidCredentialsException(INVALID_CREDENTIALS_NON_FOUND)
                }
            }
        }
    }

    /**
      * Tries to authenticate the credentials by getting the [[UserProfileV1]] from the triple store and checking if the
      * password matches. Caches the user profile after successful authentication under a generated session id if 'session=true', and
      * returns that said session id (or 0 if no session is needed).
      *
      * @param username the username of the user
      * @param password the password of th user
      * @param session  a [[Boolean]] if set true then a session id will be created and the user profile cached
      * @param system   the current [[ActorSystem]]
      * @return a [[Try[String]] which is the session id under which the profile is stored if authentication was successful.
      */
    private def authenticateCredentials(username: String, password: String, session: Boolean)(implicit system: ActorSystem): Try[String] = {
        Try {
            getUserProfileByUsername(username) match {
                case Success(userProfile: UserProfileV1) => {
                    // password needs to match AND user needs to be active
                    log.debug(s"Successfully got UserProfileV1: ${userProfile.toString}")
                    log.debug(s"Username: $username, password: $password")
                    log.debug(s"Check password match: ${userProfile.passwordMatch(password)}")
                    log.debug(s"Check user active: ${userProfile.userData.isActiveUser.getOrElse(false)}")
                    if (userProfile.passwordMatch(password) && userProfile.userData.isActiveUser.getOrElse(false)) {
                        // create session id and cache user profile under this id
                        log.debug("password matched")
                        if (session) {
                            val sId = userProfile.getDigest
                            CacheUtil.put[UserProfileV1](cacheName, sId, userProfile)
                            sId
                        } else {
                            "0"
                        }
                    } else {
                        log.debug("password did not match")
                        throw InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD)
                    }
                }
                case Failure(ex) => {
                    //log.debug(s"Unsuccessful: ${ex.toString}" )
                    throw ex
                }
            }
        }
    }

    /**
      * Try to get the session id from the cookie and return a [[UserProfileV1]] if still in the cache.
      *
      * @param requestContext a [[RequestContext]] containing the http request
      * @return a [[ Option[UserProfileV1] ]]
      */
    private def getUserProfileV1FromSessionId(requestContext: RequestContext): Option[UserProfileV1] = {
        val cookies: Seq[HttpCookie] = requestContext.request.cookies
        cookies.find(_.name == "KnoraAuthentication") match {
            case Some(authCookie) =>
                val value = CacheUtil.get[UserProfileV1](cacheName, authCookie.content)
                log.debug(s"Found this session id: ${authCookie.content} leading to this content in the cache: $value")
                value
            case None =>
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
      * @return a [[Option[(String, String)]] either a [[Some]] containing a tuple with the username and password, or a [[None]]
      *         if no credentials could be found.
      */
    private def extractCredentials(requestContext: RequestContext): Option[(String, String)] = {
        log.debug("extractCredentials start ...")
        val params: Map[String, Seq[String]] = requestContext.request.uri.query.toMultiMap
        val headers: Seq[HttpHeader] = requestContext.request.headers


        // extract username / password from parameters
        val usernameFromParams: String = params get "username" match {
            case Some(value) => value.head
            case None => ""
        }

        val passwordFromParams: String = params get "password" match {
            case Some(value) => value.head
            case None => ""
        }

        if (usernameFromParams.length > 0 && passwordFromParams.length > 0) {
            log.debug("usernameFromParams: " + usernameFromParams)
            log.debug("passwordFromParams: " + passwordFromParams)
        } else {
            log.debug("no credentials sent as parameters")
        }

        // extract username / password from the auth header
        val authHeaderList = headers filter (httpHeader => httpHeader.lowercaseName.equals("authorization"))
        val authHeaderEncoded = if (authHeaderList.nonEmpty) {
            authHeaderList.head.value.substring(6)
        } else {
            ""
        }

        log.debug(s"autheaderEnocoded.nonEmpty: " + authHeaderEncoded.nonEmpty)

        val (usernameFromHeader: String, passwordFromHeader: String) = if (authHeaderEncoded.nonEmpty) {
            val authHeaderDecoded = ByteString.fromArray(Base64.getDecoder.decode(authHeaderEncoded)).decodeString("UTF8")
            val authHeaderDecodedArr = authHeaderDecoded.split(":", 2)
            (authHeaderDecodedArr(0), authHeaderDecodedArr(1))
        } else {
            ("", "")
        }

        if (usernameFromHeader.nonEmpty && passwordFromHeader.nonEmpty) {
            log.debug("usernameFromHeader: " + usernameFromHeader)
            log.debug("passwordFromHeader: " + passwordFromHeader)
        } else {
            log.debug("no credentials found in the header")
        }

        // get only one set of credentials based on precedence
        val (username, password) = if (usernameFromParams.nonEmpty && passwordFromParams.nonEmpty) {
            (usernameFromParams, passwordFromParams)
        } else if (usernameFromHeader.nonEmpty && passwordFromHeader.nonEmpty) {
            (usernameFromHeader, passwordFromHeader)
        } else {
            ("", "")
        }

        (username, password) match {
            case (u, p) if u.nonEmpty && p.nonEmpty => log.debug(s"found credentials: '$u' , '$p' "); Some((u, p))
            case _ => log.debug("No credentials could be extracted"); None
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
      * @return a [[Option(UserProfileV1)]]
      */
    private def getUserProfileByIri(iri: IRI)(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): Try[UserProfileV1] = {
        try {
            val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)
            log.debug(s"username: $iri")
            if (iri.nonEmpty) {
                val userProfileV1Future = responderManager ? UserProfileByIRIGetRequestV1(iri)
                val userProfileV1 = Await.result(userProfileV1Future, Duration(3, SECONDS)).asInstanceOf[UserProfileV1]
                log.debug("This user was found: " + userProfileV1.toString)
                Success(userProfileV1)
            } else {
                log.debug("No user IRI supplied")
                Failure(InvalidCredentialsException(INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED))
            }
        } catch {
            case nfe: NotFoundException =>  {
                log.debug("No user found by this IRI inside the triplestore")
                Failure(InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD))
            }
        }


    }

    /**
      * Tries to get a [[UserProfileV1]] from the triple store matching the username.
      *
      * @param username         the username of the user to be queried
      * @param system           the current akka actor system
      * @param timeout          the timeout of the query
      * @param executionContext the current execution context
      * @return a [[Success(UserProfileV1)]]
      */
    private def getUserProfileByUsername(username: String)(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): Try[UserProfileV1] = {
        try {
            val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)
            log.debug(s"username: $username")
            if (username.nonEmpty) {
                val userProfileV1Future = responderManager ? UserProfileByUsernameGetRequestV1(username, false)
                val userProfileV1: UserProfileV1 = Await.result(userProfileV1Future, Duration(3, SECONDS)).asInstanceOf[UserProfileV1]
                log.debug("Found this user in the triplestore: " + userProfileV1.toString)
                Success(userProfileV1)
            } else {
                log.debug("No username supplied")
                Failure(InvalidCredentialsException(INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED))
            }
        } catch {
            case nfe: NotFoundException =>  {
                log.debug("No user found by this username inside the triplestore")
                Failure(InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD))
            }
        }
    }
}


