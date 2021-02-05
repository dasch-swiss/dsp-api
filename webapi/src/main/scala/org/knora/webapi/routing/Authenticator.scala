/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import java.util.{Base64, UUID}

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.headers.{HttpCookie, HttpCookiePair}
import akka.http.scaladsl.model.{headers, _}
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.Logger
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{AuthenticationException, BadCredentialsException, BadRequestException}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.util.cache.CacheUtil
import org.slf4j.LoggerFactory
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtHeader, JwtSprayJson}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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
    * @param requestContext       a [[RequestContext]] containing the http request
    * @param featureFactoryConfig the feature factory configuration.
    * @param system               the current [[ActorSystem]]
    * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
    *         the generated session id.
    */
  def doLoginV1(requestContext: RequestContext, featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[HttpResponse] = {

    val settings = KnoraSettings(system)

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

    for {
      userADM <- getUserADMThroughCredentialsV2(credentials = credentials, featureFactoryConfig = featureFactoryConfig) // will return or throw
      userProfile = userADM.asUserProfileV1

      cookieDomain = Some(settings.cookieDomain)
      sessionToken = JWTHelper.createToken(userProfile.userData.user_id.get,
                                           settings.jwtSecretKey,
                                           settings.jwtLongevity)

      httpResponse = HttpResponse(
        headers = List(
          headers.`Set-Cookie`(
            HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME,
                       sessionToken,
                       domain = cookieDomain,
                       path = Some("/"),
                       httpOnly = true))), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
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
    } yield httpResponse
  }

  /**
    * Checks if the provided credentials are valid, and if so returns a JWT token for the client to save.
    *
    * @param credentials          the user supplied [[KnoraPasswordCredentialsV2]] containing the user's login information.
    * @param featureFactoryConfig the feature factory configuration.
    * @param system               the current [[ActorSystem]]
    * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
    *         the generated session id.
    */
  def doLoginV2(credentials: KnoraPasswordCredentialsV2, featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[HttpResponse] = {

    log.debug(s"doLoginV2 - credentials: $credentials")

    for {
      // will throw exception if not valid and thus trigger the correct response
      authenticated <- authenticateCredentialsV2(
        credentials = Some(credentials),
        featureFactoryConfig = featureFactoryConfig
      )

      settings = KnoraSettings(system)

      userADM <- getUserByIdentifier(
        identifier = credentials.identifier,
        featureFactoryConfig = featureFactoryConfig
      )

      cookieDomain = Some(settings.cookieDomain)
      token = JWTHelper.createToken(userADM.id, settings.jwtSecretKey, settings.jwtLongevity)

      httpResponse = HttpResponse(
        headers = List(
          headers.`Set-Cookie`(
            HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME,
                       token,
                       domain = cookieDomain,
                       path = Some("/"),
                       httpOnly = true))), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
        status = StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          JsObject(
            "token" -> JsString(token)
          ).compactPrint
        )
      )

    } yield httpResponse
  }

  def presentLoginFormV2(requestContext: RequestContext)(implicit system: ActorSystem,
                                                         executionContext: ExecutionContext): Future[HttpResponse] = {

    val settings = KnoraSettings(system)

    val apiUrl = settings.externalKnoraApiBaseUrl

    val form =
      s"""
               |<div align="center">
               |    <section class="container">
               |        <div class="login">
               |            <h1>Knora Login</h1>
               |            <form name="myform" action="${apiUrl}/v2/login" method="post">
               |                <p>
               |                    <input type="text" name="username" value="" placeholder="Username">
               |                </p>
               |                <p>
               |                    <input type="password" name="password" value="" placeholder="Password">
               |                </p>
               |                <p class="submit">
               |                    <input type="submit" name="submit" value="Login">
               |                </p>
               |            </form>
               |        </div>
               |
               |    </section>
               |
               |    <section class="about">
               |        <p class="about-author">
               |            &copy; 2015&ndash;2019 <a href="https://knora.org" target="_blank">Knora.org</a>
               |    </section>
               |</div>
        """.stripMargin

    val httpResponse = HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        form
      )
    )

    FastFuture.successful(httpResponse)
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Authentication ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
    * Checks if the credentials provided in [[RequestContext]] are valid, and if so returns a message. No session is
    * generated.
    *
    * @param requestContext       a [[RequestContext]] containing the http request
    * @param featureFactoryConfig the feature factory configuration.
    * @param system               the current [[ActorSystem]]
    * @return a [[RequestContext]]
    */
  def doAuthenticateV1(requestContext: RequestContext, featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[HttpResponse] = {

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

    for {
      // will authenticate and either return or throw
      userADM: UserADM <- getUserADMThroughCredentialsV2(
        credentials = credentials,
        featureFactoryConfig = featureFactoryConfig
      )

      userProfile: UserProfileV1 = userADM.asUserProfileV1

      httpResponse = HttpResponse(
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
    } yield httpResponse
  }

  /**
    * Checks if the credentials provided in [[RequestContext]] are valid.
    *
    * @param requestContext a [[RequestContext]] containing the http request
    * @param system         the current [[ActorSystem]]
    * @return a [[HttpResponse]]
    */
  def doAuthenticateV2(requestContext: RequestContext, featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[HttpResponse] = {

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

    for {
      // will throw exception if not valid
      authenticated <- authenticateCredentialsV2(
        credentials = credentials,
        featureFactoryConfig = featureFactoryConfig
      )

      httpResponse = HttpResponse(
        status = StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          JsObject(
            "message" -> JsString("credentials are OK")
          ).compactPrint
        )
      )
    } yield httpResponse
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

    val settings = KnoraSettings(system)
    val cookieDomain = Some(settings.cookieDomain)

    credentials match {
      case Some(sessionCreds: KnoraSessionCredentialsV2) => {
        CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, sessionCreds.token, sessionCreds.token)

        HttpResponse(
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME,
                         "",
                         domain = cookieDomain,
                         path = Some("/"),
                         httpOnly = true,
                         expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))),
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
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME,
                         "",
                         domain = cookieDomain,
                         path = Some("/"),
                         httpOnly = true,
                         expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))),
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
  @deprecated("Please use: getUserADM()", "Knora v1.7.0")
  def getUserProfileV1(requestContext: RequestContext, featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[UserProfileV1] = {

    val settings = KnoraSettings(system)

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

    if (settings.skipAuthentication) {
      // return anonymous if skipAuthentication
      log.debug(
        "getUserProfileV1 - Authentication skipping active, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
      FastFuture.successful(UserProfileV1())
    } else if (credentials.isEmpty) {
      log.debug(
        "getUserProfileV1 - No credentials found, returning default UserProfileV1 with 'anonymousUser' inside 'permissionData' set to true!")
      FastFuture.successful(UserProfileV1())
    } else {
      for {
        userADM <- getUserADMThroughCredentialsV2(credentials = credentials,
                                                  featureFactoryConfig = featureFactoryConfig)
        userProfile: UserProfileV1 = userADM.asUserProfileV1
        _ = log.debug("Authenticator - getUserProfileV1 - userProfile: {}", userProfile)

        /* we return the userProfileV1 without sensitive information */
      } yield userProfile.ofType(UserProfileTypeV1.RESTRICTED)
    }
  }

  /**
    * Returns a User that match the credentials found in the [[RequestContext]].
    * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
    * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
    * corresponding error is returned.
    *
    * @param requestContext       a [[RequestContext]] containing the http request
    * @param featureFactoryConfig the feature factory configuration.
    * @param system               the current [[ActorSystem]]
    * @return a [[UserProfileV1]]
    */
  def getUserADM(requestContext: RequestContext, featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[UserADM] = {

    val settings = KnoraSettings(system)

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)

    if (settings.skipAuthentication) {
      // return anonymous if skipAuthentication
      log.debug("getUserADM - Authentication skipping active, returning 'anonymousUser'.")
      FastFuture.successful(KnoraSystemInstances.Users.AnonymousUser)
    } else if (credentials.isEmpty) {
      log.debug("getUserADM - No credentials found, returning 'anonymousUser'.")
      FastFuture.successful(KnoraSystemInstances.Users.AnonymousUser)
    } else {

      for {
        user: UserADM <- getUserADMThroughCredentialsV2(credentials = credentials,
                                                        featureFactoryConfig = featureFactoryConfig)
        _ = log.debug("Authenticator - getUserADM - user: {}", user)

        /* we return the complete UserADM */
      } yield user.ofType(UserInformationTypeADM.FULL)
    }
  }
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

  val sessionStore: scala.collection.mutable.Map[String, UserADM] = scala.collection.mutable.Map()
  implicit val timeout: Timeout = Duration(5, SECONDS)
  val log = Logger(LoggerFactory.getLogger(this.getClass))

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
    * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
    * authentication is performed checking if the supplied email/password combination is valid by retrieving the
    * user's profile. In the case of the token, the token itself is validated. If both are supplied, then both need
    * to be valid.
    *
    * @param credentials          the user supplied and extracted credentials.
    * @param featureFactoryConfig the feature factory configuration.
    * @param system               the current [[ActorSystem]]
    * @return true if the credentials are valid. If the credentials are invalid, then the corresponding exception
    *         will be thrown.
    * @throws BadCredentialsException when no credentials are supplied; when user is not active;
    *                                 when the password does not match; when the supplied token is not valid.
    */
  def authenticateCredentialsV2(credentials: Option[KnoraCredentialsV2], featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[Boolean] = {

    for {
      settings <- FastFuture.successful(KnoraSettings(system))

      result <- credentials match {
        case Some(passCreds: KnoraPasswordCredentialsV2) => {
          for {
            user <- getUserByIdentifier(
              identifier = passCreds.identifier,
              featureFactoryConfig = featureFactoryConfig
            )

            /* check if the user is active, if not, then no need to check the password */
            _ = if (!user.isActive) {
              log.debug("authenticateCredentials - user is not active")
              throw BadCredentialsException(BAD_CRED_USER_INACTIVE)
            }

            _ = if (!user.passwordMatch(passCreds.password)) {
              log.debug("authenticateCredentialsV2 - password did not match")
              throw BadCredentialsException(BAD_CRED_NOT_VALID)
            }
          } yield true
        }
        case Some(tokenCreds: KnoraTokenCredentialsV2) => {
          if (!JWTHelper.validateToken(tokenCreds.token, settings.jwtSecretKey)) {
            log.debug("authenticateCredentialsV2 - token was not valid")
            throw BadCredentialsException(BAD_CRED_NOT_VALID)
          }
          FastFuture.successful(true)
        }
        case Some(sessionCreds: KnoraSessionCredentialsV2) => {
          if (!JWTHelper.validateToken(sessionCreds.token, settings.jwtSecretKey)) {
            log.debug("authenticateCredentialsV2 - session token was not valid")
            throw BadCredentialsException(BAD_CRED_NOT_VALID)
          }
          FastFuture.successful(true)
        }
        case None => {
          log.debug("authenticateCredentialsV2 - no credentials supplied")
          throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
        }
      }

    } yield result
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
    // log.debug("extractCredentialsV2 start ...")

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

    // log.debug("extractCredentialsFromParametersV2 - params: {}", params)

    // check for iri, email, or username parameters
    val maybeIriIdentifier: Option[String] = params.get("iri").map(_.head)
    val maybeEmailIdentifier: Option[String] = params.get("email").map(_.head)
    val maybeUsernameIdentifier: Option[String] = params.get("username").map(_.head)
    val maybeIdentifier: Option[String] =
      List(maybeIriIdentifier, maybeEmailIdentifier, maybeUsernameIdentifier).flatten.headOption
    // log.debug("extractCredentialsFromParametersV2 - maybeIdentifier: {}", maybeIdentifier)

    val maybePassword: Option[String] = params.get("password").map(_.head)
    // log.debug("extractCredentialsFromParametersV2 - maybePassword: {}", maybePassword)

    val maybePassCreds: Option[KnoraPasswordCredentialsV2] = if (maybeIdentifier.nonEmpty && maybePassword.nonEmpty) {
      Some(
        KnoraPasswordCredentialsV2(
          UserIdentifierADM(
            maybeIri = maybeIriIdentifier,
            maybeEmail = maybeEmailIdentifier,
            maybeUsername = maybeUsernameIdentifier
          ),
          maybePassword.get
        )
      )
    } else {
      None
    }

    val maybeToken: Option[String] = params get "token" map (_.head)

    val maybeTokenCreds: Option[KnoraTokenCredentialsV2] = if (maybeToken.nonEmpty) {
      Some(KnoraTokenCredentialsV2(maybeToken.get))
    } else {
      None
    }

    // log.debug("extractCredentialsFromParametersV2 - maybePassCreds: {}", maybePassCreds)
    // log.debug("extractCredentialsFromParametersV2 - maybeTokenCreds: {}", maybeTokenCreds)

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

    val maybeSessionCredentials = extractSessionCredentials(requestContext)

    val maybePasswordCredentials = extractPasswordCredentials(requestContext)

    val maybeTokenCredentials = extractTokenCredentials(requestContext)

    // prefer password over token over session
    if (maybePasswordCredentials.nonEmpty) {
      maybePasswordCredentials
    } else if (maybeTokenCredentials.nonEmpty) {
      maybeTokenCredentials
    } else {
      maybeSessionCredentials
    }
  }

  /**
    * Tries to extract the session credentials from the cookie header
    */
  private def extractSessionCredentials(requestContext: RequestContext): Option[KnoraSessionCredentialsV2] = {
    // Session token from cookie header
    val cookies: Seq[HttpCookiePair] = requestContext.request.cookies

    // extracts session credentials from cookie header
    def extract(cookies: Seq[HttpCookiePair]): Option[KnoraSessionCredentialsV2] = {
      cookies.find(_.name == "KnoraAuthentication") match {
        case Some(authCookie) =>
          val value: String = authCookie.value
          Some(KnoraSessionCredentialsV2(value))
        case None =>
          None
      }
    }

    // call the extract method
    extract(cookies)
  }

  /**
    * Tries to extract the password credentials from the authorization header
    */
  private def extractPasswordCredentials(requestContext: RequestContext): Option[KnoraPasswordCredentialsV2] = {

    val headers: Seq[HttpHeader] = requestContext.request.headers

    headers.find(_.name == "Authorization") match {
      case Some(authHeader: HttpHeader) =>
        // the authorization header can hold different schemes
        val credsArr: Array[String] = authHeader.value().split(",")

        // find the basic scheme
        val maybeBasicAuthValue = credsArr.find(_.contains("Basic"))

        // try to decode username/email and password
        maybeBasicAuthValue match {
          case Some(value) =>
            val trimmedValue = value.substring(5).trim() // remove 'Basic '
            val decodedValue = ByteString.fromArray(Base64.getDecoder.decode(trimmedValue)).decodeString("UTF8")
            val decodedValueArr = decodedValue.split(":", 2)

            if (decodedValueArr(0).contains("@")) {
              Some(
                KnoraPasswordCredentialsV2(
                  UserIdentifierADM(
                    maybeEmail = Option(decodedValueArr(0))
                  ),
                  password = decodedValueArr(1)
                )
              )
            } else {
              Some(
                KnoraPasswordCredentialsV2(
                  UserIdentifierADM(
                    maybeUsername = Option(decodedValueArr(0))
                  ),
                  password = decodedValueArr(1)
                )
              )
            }
          case None =>
            None
        }
      case None =>
        None
    }
  }

  /**
    * Tries to extract the password credentials from the authorization header
    */
  private def extractTokenCredentials(requestContext: RequestContext): Option[KnoraTokenCredentialsV2] = {

    val headers: Seq[HttpHeader] = requestContext.request.headers

    headers.find(_.name == "Authorization") match {
      case Some(authHeader: HttpHeader) =>
        // the authorization header can hold different schemes
        val credsArr: Array[String] = authHeader.value().split(",")

        // find the bearer scheme
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

        maybeTokenCreds

      case None =>
        None
    }
  }

  /**
    * Tries to retrieve a [[UserADM]] based on the supplied credentials. If both email/password and session
    * token are supplied, then the user profile for the session token is returned. This method should only be used
    * with authenticated credentials.
    *
    * @param credentials          the user supplied credentials.
    * @param featureFactoryConfig the feature factory configuration.
    * @return a [[UserADM]]
    * @throws AuthenticationException when the IRI can not be found inside the token, which is probably a bug.
    */
  private def getUserADMThroughCredentialsV2(credentials: Option[KnoraCredentialsV2],
                                             featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      executionContext: ExecutionContext): Future[UserADM] = {

    val settings = KnoraSettings(system)

    for {
      authenticated <- authenticateCredentialsV2(credentials = credentials, featureFactoryConfig = featureFactoryConfig)

      user: UserADM <- credentials match {
        case Some(passCreds: KnoraPasswordCredentialsV2) => {
          // log.debug("getUserADMThroughCredentialsV2 - used identifier: {}", passCreds.identifier)
          getUserByIdentifier(
            identifier = passCreds.identifier,
            featureFactoryConfig = featureFactoryConfig
          )
        }
        case Some(tokenCreds: KnoraTokenCredentialsV2) => {
          val userIri: IRI = JWTHelper.extractUserIriFromToken(tokenCreds.token, settings.jwtSecretKey) match {
            case Some(iri) => iri
            case None => {
              // should not happen, as the token is already validated
              throw AuthenticationException("No IRI found inside token. Please report this as a possible bug.")
            }
          }
          // log.debug("getUserADMThroughCredentialsV2 - used token")
          getUserByIdentifier(
            identifier = UserIdentifierADM(maybeIri = Some(userIri)),
            featureFactoryConfig = featureFactoryConfig
          )
        }
        case Some(sessionCreds: KnoraSessionCredentialsV2) => {
          val userIri: IRI = JWTHelper.extractUserIriFromToken(sessionCreds.token, settings.jwtSecretKey) match {
            case Some(iri) => iri
            case None => {
              // should not happen, as the token is already validated
              throw AuthenticationException("No IRI found inside token. Please report this as a possible bug.")
            }
          }
          // log.debug("getUserADMThroughCredentialsV2 - used session token")
          getUserByIdentifier(
            identifier = UserIdentifierADM(maybeIri = Some(userIri)),
            featureFactoryConfig = featureFactoryConfig
          )
        }
        case None => {
          // log.debug("getUserADMThroughCredentialsV2 - no credentials supplied")
          throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
        }
      }

    } yield user
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // TRIPLE STORE ACCESS
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
    * Tries to get a [[UserADM]].
    *
    * @param identifier           the IRI, email, or username of the user to be queried
    * @param featureFactoryConfig the feature factory configuration.
    * @param system               the current akka actor system
    * @param timeout              the timeout of the query
    * @param executionContext     the current execution context
    * @return a [[UserADM]]
    * @throws BadCredentialsException when either the supplied email is empty or no user with such an email could be found.
    */
  private def getUserByIdentifier(identifier: UserIdentifierADM, featureFactoryConfig: FeatureFactoryConfig)(
      implicit system: ActorSystem,
      responderManager: ActorRef,
      timeout: Timeout,
      executionContext: ExecutionContext): Future[UserADM] = {

    val userADMFuture = for {
      maybeUserADM <- (responderManager ? UserGetADM(
        identifier = identifier,
        userInformationTypeADM = UserInformationTypeADM.FULL,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )).mapTo[Option[UserADM]]

      user = maybeUserADM match {
        case Some(u) => u
        case None => {
          log.debug(s"getUserByIdentifier - supplied identifier not found - throwing exception")
          throw BadCredentialsException(s"$BAD_CRED_USER_NOT_FOUND")
        }
      }
      // _ = log.debug(s"getUserByIdentifier - user: $user")
    } yield user

    userADMFuture
  }
}

/**
  * Provides functions for creating, decoding, and validating JWT tokens.
  */
object JWTHelper {

  import Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME

  private val algorithm: JwtAlgorithm = JwtAlgorithm.HS256

  private val header: String = """{"typ":"JWT","alg":"HS256"}"""

  val log = Logger(LoggerFactory.getLogger(this.getClass))

  /**
    * Creates a JWT.
    *
    * @param userIri   the user IRI that will be encoded into the token.
    * @param secret    the secret key used for encoding.
    * @param longevity the token's longevity.
    * @param content   any other content to be included in the token.
    * @return a [[String]] containing the JWT.
    */
  def createToken(userIri: IRI,
                  secret: String,
                  longevity: FiniteDuration,
                  content: Map[String, JsValue] = Map.empty): String = {
    val stringFormatter = StringFormatter.getGeneralInstance

    // now in seconds
    val now: Long = System.currentTimeMillis() / 1000l

    // calculate expiration time (seconds)
    val nowPlusLongevity: Long = now + longevity.toSeconds

    val identifier: String = stringFormatter.base64EncodeUuid(UUID.randomUUID)

    val claim: String = JwtClaim(
      content = JsObject(content).compactPrint,
      issuer = Some("Knora"),
      subject = Some(userIri),
      audience = Some(Set("Knora", "Sipi")),
      issuedAt = Some(now),
      expiration = Some(nowPlusLongevity),
      jwtId = Some(identifier)
    ).toJson

    JwtSprayJson.encode(
      header = header,
      claim = claim,
      key = secret,
      algorithm = algorithm
    )
  }

  /**
    * Validates a JWT, taking the invalidation cache into account. The invalidation cache holds invalidated
    * tokens, which would otherwise validate. This method also makes sure that the required headers and claims are
    * present.
    *
    * @param token  the JWT.
    * @param secret the secret used to encode the token.
    * @return a [[Boolean]].
    */
  def validateToken(token: String, secret: String): Boolean = {
    if (CacheUtil.get[UserADM](AUTHENTICATION_INVALIDATION_CACHE_NAME, token).nonEmpty) {
      // token invalidated so no need to decode
      log.debug("validateToken - token found in invalidation cache, so not valid")
      false
    } else {
      decodeToken(token, secret).isDefined
    }
  }

  /**
    * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
    *
    * @param token  the JWT.
    * @param secret the secret used to encode the token.
    * @return an optional [[IRI]].
    */
  def extractUserIriFromToken(token: String, secret: String): Option[IRI] = {
    decodeToken(token, secret) match {
      case Some((_: JwtHeader, claim: JwtClaim)) => claim.subject
      case None                                  => None
    }
  }

  /**
    * Extracts application-specific content from a JWT token.  This method also makes sure that the required headers
    * and claims are present.
    *
    * @param token       the JWT.
    * @param secret      the secret used to encode the token.
    * @param contentName the name of the content field to be extracted.
    * @return the string value of the specified content field.
    */
  def extractContentFromToken(token: String, secret: String, contentName: String): Option[String] = {
    decodeToken(token, secret) match {
      case Some((_: JwtHeader, claim: JwtClaim)) =>
        claim.content.parseJson.asJsObject.fields.get(contentName) match {
          case Some(jsString: JsString) => Some(jsString.value)
          case _                        => None
        }

      case None => None
    }
  }

  /**
    * Decodes and validates a JWT token.
    *
    * @param token  the token to be decoded.
    * @param secret the secret used to encode the token.
    * @return the token's header and claim, or `None` if the token is invalid.
    */
  private def decodeToken(token: String, secret: String): Option[(JwtHeader, JwtClaim)] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    JwtSprayJson.decodeAll(token, secret, Seq(JwtAlgorithm.HS256)) match {
      case Success((header: JwtHeader, claim: JwtClaim, _)) =>
        val missingRequiredContent: Boolean = Set(
          header.typ.isDefined,
          claim.issuer.isDefined,
          claim.subject.isDefined,
          claim.jwtId.isDefined,
          claim.issuedAt.isDefined,
          claim.expiration.isDefined,
          claim.audience.isDefined
        ).contains(false)

        if (!missingRequiredContent) {
          Try(
            stringFormatter.validateAndEscapeIri(claim.subject.get,
                                                 throw BadRequestException("Invalid user IRI in JWT"))) match {
            case Success(_) => Some(header, claim)

            case Failure(e) =>
              log.debug(e.getMessage)
              None
          }
        } else {
          log.debug("Missing required content in JWT")
          None
        }

      case Failure(_) =>
        log.debug("Invalid JWT")
        None
    }
  }
}
