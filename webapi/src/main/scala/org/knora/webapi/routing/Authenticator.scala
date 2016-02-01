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
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileByUsernameGetRequestV1, UserProfileGetRequestV1, UserProfileV1}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.util.CacheUtil
import org.knora.webapi.{BadCredentialsException, IRI, Settings}
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

//TODO: OAuth2

trait Authenticator {

    val BAD_CRED_PASSWORD_MISMATCH = "bad credentials: user found, but password did not match"
    val BAD_CRED_USER_NOT_FOUND = "bad credentials: user not found"
    val BAD_CRED_USERNAME_NOT_SUPPLIED = "bad credentials: no username supplied"
    val BAD_CRED_USERNAME_PASSWORD_NOT_EXTRACTABLE = "bad credentials: none found"

    val sessionStore: scala.collection.mutable.Map[String, UserProfileV1] = scala.collection.mutable.Map()
    implicit val timeout: Timeout = Duration(5, SECONDS)
    val log = Logger(LoggerFactory.getLogger("org.knora.webapi.util.authentication"))

    private val cacheName = "authenticationCache"

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // LOGIN ENTRY POINT
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Checks if the credentials provided in [[RequestContext]] are valid, and if so returns a message and cookie header
      * with the generated session id for the client to save.
      * @param requestContext
      * @param system
      * @return
      */
    def doLogin(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {
        extractCredentialsAndAuthenticate(requestContext, true) match {
            case Success(sId) =>
                HttpResponse(
                    headers = List(HttpHeaders.`Set-Cookie`(HttpCookie("KnoraAuthentication", sId))),
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("credentials are OK"),
                            "sid" -> JsString(sId)
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

    def doSessionAuthentication(requestContext: RequestContext): HttpResponse = {
        getUserProfileV1FromSessionId(requestContext) match {
            case Some(userProfile) =>
                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("session credentials are OK")
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
      * @param requestContext
      * @param system
      * @return
      */
    def doAuthenticate(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        extractCredentialsAndAuthenticate(requestContext, false) match {
            case Success(_) => {
                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                            "status" -> JsNumber(0),
                            "message" -> JsString("credentials are OK")
                        ).compactPrint
                    )
                )
            }
            case Failure(ex) => {
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

    /**
      * Tries to extract and then authenticate the credentials, while returning
      * @param requestContext
      * @return [[ Try[String] ]] contains the session id if successful, which means that the credentials could be
      *         extracted and authenticated. In the case where the credentials could not be extracted or could be
      *         extracted but not authenticated, a corresponding exception is thrown.
      */
    private def extractCredentialsAndAuthenticate(requestContext: RequestContext, session: Boolean)(implicit system: ActorSystem): Try[String] = {
        Try {
            extractCredentials(requestContext) match {
                case Some((u, p)) => authenticateCredentials(u, p, session) match {
                    case Success(sId) => sId
                    case Failure(ex) => throw ex
                }
                case None => throw BadCredentialsException(BAD_CRED_USERNAME_PASSWORD_NOT_EXTRACTABLE)
            }
        }
    }

    /**
      * Tries to authenticate the credentials by getting the [[UserProfileV1]] from the triple store and checking if the
      * password matches. Caches the user profile after successful authentication under a generated session id if 'session=true', and
      * returns that said session id (or 0 if no session is needed).
      * @param username The username
      * @param password The password
      * @return [[Try[String]] the session id under which the profile is stored if authentication is successful.
      */
    private def authenticateCredentials(username: String, password: String, session: Boolean)(implicit system: ActorSystem): Try[String] = {
        Try {
            getUserProfileByUsername(username) match {
                case Success(userProfileV1: UserProfileV1) => {
                    if (userProfileV1.passwordMatch(password)) {
                        // create session id and cache user profile under this id
                        log.debug("password matched")
                        if (session) {
                            val sId = System.currentTimeMillis().toString
                            CacheUtil.put(cacheName, sId, userProfileV1)
                            sId
                        } else {
                            "0"
                        }
                    } else {
                        log.debug("password did not match")
                        throw BadCredentialsException(BAD_CRED_PASSWORD_MISMATCH)
                    }
                }
                case Failure(ex) => {
                    throw ex
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // LOGOUT ENTRY POINT
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    def doLogout(requestContext: RequestContext)(implicit system: ActorSystem): HttpResponse = {

        val cookies: Seq[HttpCookie] = requestContext.request.cookies
        cookies.find(_.name == "KnoraAuthentication") match {
            case Some(authCookie) =>
                // maybe the value is in the cache or maybe it expired in the meantime.
                CacheUtil.remove(cacheName, authCookie.content)
            case None => // no cookie, so I can't do anything really
        }
        HttpResponse(
            headers = List(HttpHeaders.`Set-Cookie`(HttpCookie("KnoraAuthentication", "deleted", expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))),
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
      * found in the cache.
      * @param requestContext
      * @param system
      * @return [[UserProfileV1]]
      */
    def getUserProfileV1(requestContext: RequestContext)(implicit system: ActorSystem): UserProfileV1 = {
        val settings = Settings(system)
        val hardCodedUser: IRI = "http://data.knora.org/users/b83acc5f05" // testuser
        if (settings.skipAuthentication) {
            // skip authentication and return hardCodedUser
            getUserProfileByIri(hardCodedUser).getOrElse(UserProfileV1(UserDataV1(settings.fallbackLanguage)))
        }
        else {
            // let us first try to get the user profile through the session id from the cookie
            getUserProfileV1FromSessionId(requestContext) match {
                case Some(userProfile) => log.debug(s"got this through the session id: ${userProfile.toString}"); userProfile
                case None => {
                    // no cookie or valid session id, so let's look for credentials
                    extractCredentials(requestContext) match {
                        // got some credentials, lets try to get a UserProfileV1
                        case Some((u, p)) => getUserProfileByUsername(u) match {
                            // I got a UserProfileV1, which means that the password is a match
                            case Success(userProfileV1: UserProfileV1) => userProfileV1
                            // something went wrong. I'll just throw the exception upwards.
                            case Failure(ex) => throw ex
                        }
                        // no credentials found, return default UserProfileV1
                        case None => UserProfileV1(UserDataV1(settings.fallbackLanguage))
                    }
                }
            }
        }
    }


    /**
      * Try to get the session id from the cookie and return a [[UserProfileV1]] if still in the cache.
      * @param requestContext
      * @return [[ Option[UserProfileV1] ]]
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
      * @param requestContext
      * @return [[Option[(String, String)]] Either a [[Some]] containing a tuple with the username and password, or a [[None]]
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
      * @param iri
      * @param system
      * @param timeout
      * @param executionContext
      * @return
      */
    private def getUserProfileByIri(iri: IRI)(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): Option[UserProfileV1] = {
        val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

        val userProfileV1Future = responderManager ? UserProfileGetRequestV1(iri)
        Await.result(userProfileV1Future, Duration(3, SECONDS)).asInstanceOf[Option[UserProfileV1]] match {
            case Some(userProfileV1) => {
                log.debug("This user was found: " + userProfileV1.toString)
                Some(userProfileV1)
            }
            case None => log.debug("No user found by this IRI"); None


        }
    }

    /**
      * Tries to get a [[UserProfileV1]] from the cache or from the triple store matching the username.
      * @param username
      * @param system
      * @param timeout
      * @param executionContext
      * @return A [[Success(UserProfileV1)]] if a user profile matching the username is found.
      */
    private def getUserProfileByUsername(username: String)(implicit system: ActorSystem, timeout: Timeout, executionContext: ExecutionContext): Try[UserProfileV1] = {
        Try {
            val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

            if (username.nonEmpty) {
                // try to get it from the cache
                CacheUtil.get[UserProfileV1](cacheName, username) match {
                    case Some(userProfileV1) =>
                        // found a user profile in the cache
                        userProfileV1
                    case None =>
                        // didn't found one, so I will try to get it from the triple store
                        val userProfileV1Future = responderManager ? UserProfileByUsernameGetRequestV1(username)
                        Await.result(userProfileV1Future, Duration(3, SECONDS)).asInstanceOf[Option[UserProfileV1]] match {
                            case Some(userProfileV1) => {
                                log.debug("This user was found: " + userProfileV1.toString)
                                // before I return the found user profile, let's put it into the cache
                                CacheUtil.put(cacheName, username, userProfileV1)
                                userProfileV1
                            }
                            case None => {
                                log.debug("No user found by this username")
                                throw BadCredentialsException(BAD_CRED_USER_NOT_FOUND)
                            }
                        }
                }
            } else {
                throw BadCredentialsException(BAD_CRED_USERNAME_NOT_SUPPLIED)
            }
        }
    }

}
