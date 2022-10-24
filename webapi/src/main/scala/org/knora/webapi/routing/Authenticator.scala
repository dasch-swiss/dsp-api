/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.model.headers.HttpCookiePair
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import org.apache.commons.codec.binary.Base32
import org.slf4j.LoggerFactory
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import pdi.jwt.JwtSprayJson
import spray.json._

import java.util.Base64
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dsp.errors.AuthenticationException
import dsp.errors.BadCredentialsException
import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.util.cache.CacheUtil

/**
 * This trait is used in routes that need authentication support. It provides methods that use the [[RequestContext]]
 * to extract credentials, authenticate provided credentials, and look up cached credentials through the use of the
 * session id. All private methods used in this trait can be found in the companion object.
 */
trait Authenticator extends InstrumentationSupport {

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
   * @param appConfig            the application's configuration.
   * @param system               the current [[ActorSystem]]
   * @param appActor             a reference to the application actor
   * @param executionContext     the current execution context
   * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
   *         the generated session id.
   */
  def doLoginV1(requestContext: RequestContext, appConfig: AppConfig)(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[HttpResponse] = {

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext, appConfig)

    for {
      userADM <- getUserADMThroughCredentialsV2(
                   credentials = credentials,
                   appConfig
                 ) // will return or throw
      userProfile = userADM.asUserProfileV1

      cookieDomain = Some(appConfig.cookieDomain)
      sessionToken = JWTHelper.createToken(
                       userProfile.userData.user_id.get,
                       appConfig.jwtSecretKey,
                       appConfig.jwtLongevityAsDuration,
                       appConfig.knoraApi.externalKnoraApiHostPort
                     )

      httpResponse = HttpResponse(
                       headers = List(
                         headers.`Set-Cookie`(
                           HttpCookie(
                             calculateCookieName(appConfig),
                             sessionToken,
                             domain = cookieDomain,
                             path = Some("/"),
                             httpOnly = true
                           )
                         )
                       ), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
                       status = StatusCodes.OK,
                       entity = HttpEntity(
                         ContentTypes.`application/json`,
                         JsObject(
                           "status"      -> JsNumber(0),
                           "message"     -> JsString("credentials are OK"),
                           "sid"         -> JsString(sessionToken),
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
   * @param appConfig            the application's configuration.
   * @param system               the current [[ActorSystem]]
   * @param appActor             a reference to the application actor
   * @param executionContext     the current execution context
   * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
   *         the generated session id.
   */
  def doLoginV2(credentials: KnoraPasswordCredentialsV2, appConfig: AppConfig)(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[HttpResponse] = {

    log.debug(s"doLoginV2 - credentials: $credentials")

    for {
      // will throw exception if not valid and thus trigger the correct response
      _ <- authenticateCredentialsV2(
             credentials = Some(credentials),
             appConfig
           )

      userADM <- getUserByIdentifier(
                   identifier = credentials.identifier
                 )

      cookieDomain = Some(appConfig.cookieDomain)
      token = JWTHelper.createToken(
                userADM.id,
                appConfig.jwtSecretKey,
                appConfig.jwtLongevityAsDuration,
                appConfig.knoraApi.externalKnoraApiHostPort
              )

      httpResponse = HttpResponse(
                       headers = List(
                         headers.`Set-Cookie`(
                           HttpCookie(
                             calculateCookieName(appConfig),
                             token,
                             domain = cookieDomain,
                             path = Some("/"),
                             httpOnly = true
                           )
                         )
                       ), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
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

  /**
   * Returns a simple login form for testing purposes
   *
   * @param requestContext    a [[RequestContext]] containing the http request
   * @param appConfig         the application's configuration.
   * @param system            the current [[ActorSystem]]
   * @param executionContext  the current execution context
   * @return                  a [[HttpResponse]] with an html login form
   */
  def presentLoginFormV2(
    requestContext: RequestContext,
    appConfig: AppConfig
  )(implicit system: ActorSystem, executionContext: ExecutionContext): Future[HttpResponse] = {

    val apiUrl = appConfig.knoraApi.externalKnoraApiBaseUrl

    val form =
      s"""
         |<div align="center">
         |    <section class="container">
         |        <div class="login">
         |            <h1>DSP-API Login</h1>
         |            <form name="myform" action="$apiUrl/v2/login" method="post">
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
         |            &copy; 2015&ndash;2022 <a href="https://dasch.swiss" target="_blank">dasch.swiss</a>
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
   * @param appConfig            the application's configuration.
   * @param system               the current [[ActorSystem]]
   * @param appActor             a reference to the application actor
   * @param executionContext  the current execution context
   * @return a [[HttpResponse]]
   */
  def doAuthenticateV1(requestContext: RequestContext, appConfig: AppConfig)(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[HttpResponse] = {

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext, appConfig)

    for {
      // will authenticate and either return or throw
      userADM: UserADM <- getUserADMThroughCredentialsV2(
                            credentials = credentials,
                            appConfig
                          )

      userProfile: UserProfileV1 = userADM.asUserProfileV1

      httpResponse = HttpResponse(
                       status = StatusCodes.OK,
                       entity = HttpEntity(
                         ContentTypes.`application/json`,
                         JsObject(
                           "status"      -> JsNumber(0),
                           "message"     -> JsString("credentials are OK"),
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
   * @param appConfig            the application's configuration.
   * @param system         the current [[ActorSystem]]
   * @param appActor             a reference to the application actor
   * @param executionContext  the current execution context
   * @return a [[HttpResponse]]
   */
  def doAuthenticateV2(requestContext: RequestContext, appConfig: AppConfig)(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[HttpResponse] = {

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext, appConfig)

    for {
      // will throw exception if not valid
      _ <- authenticateCredentialsV2(
             credentials = credentials,
             appConfig
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
   * @param appConfig      the application's configuration
   * @param system         the current [[ActorSystem]]
   * @return a [[HttpResponse]]
   */
  def doLogoutV2(requestContext: RequestContext, appConfig: AppConfig)(implicit system: ActorSystem): HttpResponse = {

    val credentials  = extractCredentialsV2(requestContext, appConfig)
    val cookieDomain = Some(appConfig.cookieDomain)

    credentials match {
      case Some(KnoraSessionCredentialsV2(sessionToken)) =>
        CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, sessionToken, sessionToken)

        HttpResponse(
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(
                calculateCookieName(appConfig),
                "",
                domain = cookieDomain,
                path = Some("/"),
                httpOnly = true,
                expires = Some(DateTime(1970, 1, 1, 0, 0, 0)),
                maxAge = Some(0)
              )
            )
          ),
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK")
            ).compactPrint
          )
        )
      case Some(KnoraJWTTokenCredentialsV2(jwtToken)) =>
        CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, jwtToken, jwtToken)

        HttpResponse(
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(
                calculateCookieName(appConfig),
                "",
                domain = cookieDomain,
                path = Some("/"),
                httpOnly = true,
                expires = Some(DateTime(1970, 1, 1, 0, 0, 0))
              )
            )
          ),
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK")
            ).compactPrint
          )
        )
      case _ =>
        // nothing to do
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK")
            ).compactPrint
          )
        )
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // GET USER PROFILE / AUTHENTICATION ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a User that match the credentials found in the [[RequestContext]].
   * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
   * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
   * corresponding error is returned.
   *
   * @param requestContext       a [[RequestContext]] containing the http request
   * @param appConfig            the application's configuration.
   * @param system               the current [[ActorSystem]]
   * @param appActor             a reference to the application actor
   * @param executionContext  the current execution context
   * @return a [[UserADM]]
   */
  def getUserADM(requestContext: RequestContext, appConfig: AppConfig)(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[UserADM] = {

    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext, appConfig)

    if (credentials.isEmpty) {
      log.debug("getUserADM - No credentials found, returning 'anonymousUser'.")
      FastFuture.successful(KnoraSystemInstances.Users.AnonymousUser)
    } else {

      for {
        user: UserADM <- getUserADMThroughCredentialsV2(
                           credentials = credentials,
                           appConfig
                         )
        _ = log.debug("Authenticator - getUserADM - user: {}", user)

        /* we return the complete UserADM */
      } yield user.ofType(UserInformationTypeADM.Full)
    }
  }
}

/**
 * This companion object holds all private methods used in the trait. This division is needed so that we can test
 * the private methods directly with scalatest as described in [[https://groups.google.com/forum/#!topic/scalatest-users/FeaO\_\_f1dN4]]
 * and [[http://doc.scalatest.org/2.2.6/index.html#org.scalatest.PrivateMethodTester]]
 */
object Authenticator extends InstrumentationSupport {

  val BAD_CRED_PASSWORD_MISMATCH  = "bad credentials: user found, but password did not match"
  val BAD_CRED_USER_NOT_FOUND     = "bad credentials: user not found"
  val BAD_CRED_EMAIL_NOT_SUPPLIED = "bad credentials: no email supplied"
  val BAD_CRED_NONE_SUPPLIED      = "bad credentials: none found"
  val BAD_CRED_USER_INACTIVE      = "bad credentials: user inactive"
  val BAD_CRED_NOT_VALID          = "bad credentials: not valid"

  val AUTHENTICATION_INVALIDATION_CACHE_NAME = "authenticationInvalidationCache"

  val sessionStore: scala.collection.mutable.Map[String, UserADM] = scala.collection.mutable.Map()
  val log: Logger                                                 = Logger(LoggerFactory.getLogger(this.getClass))

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
   * authentication is performed checking if the supplied email/password combination is valid by retrieving the
   * user's profile. In the case of the token, the token itself is validated. If both are supplied, then both need
   * to be valid.
   *
   * @param credentials          the user supplied and extracted credentials.
   * @param appConfig            the application's configuration
   * @param system               the current [[ActorSystem]]
   * @param appActor             a reference to the application actor
   * @param executionContext  the current execution context
   * @return true if the credentials are valid. If the credentials are invalid, then the corresponding exception
   *         will be thrown.
   * @throws BadCredentialsException when no credentials are supplied; when user is not active;
   *                                 when the password does not match; when the supplied token is not valid.
   */
  def authenticateCredentialsV2(
    credentials: Option[KnoraCredentialsV2],
    appConfig: AppConfig
  )(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[Boolean] =
    for {
      result <- credentials match {
                  case Some(passCreds: KnoraPasswordCredentialsV2) =>
                    for {
                      user <- getUserByIdentifier(
                                identifier = passCreds.identifier
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
                  case Some(KnoraJWTTokenCredentialsV2(jwtToken)) =>
                    if (
                      !JWTHelper.validateToken(
                        jwtToken,
                        appConfig.jwtSecretKey,
                        appConfig.knoraApi.externalKnoraApiHostPort
                      )
                    ) {
                      log.debug("authenticateCredentialsV2 - token was not valid")
                      throw BadCredentialsException(BAD_CRED_NOT_VALID)
                    }
                    FastFuture.successful(true)
                  case Some(KnoraSessionCredentialsV2(sessionToken)) =>
                    if (
                      !JWTHelper.validateToken(
                        sessionToken,
                        appConfig.jwtSecretKey,
                        appConfig.knoraApi.externalKnoraApiHostPort
                      )
                    ) {
                      log.debug("authenticateCredentialsV2 - session token was not valid")
                      throw BadCredentialsException(BAD_CRED_NOT_VALID)
                    }
                    FastFuture.successful(true)
                  case None =>
                    log.debug("authenticateCredentialsV2 - no credentials supplied")
                    throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
                }

    } yield result

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // HELPER METHODS
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Tries to extract the credentials from the requestContext (parameters, auth headers, token)
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @param appConfig            the application's configuration.
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsV2(
    requestContext: RequestContext,
    appConfig: AppConfig
  ): Option[KnoraCredentialsV2] = {

    val credentialsFromParameters: Option[KnoraCredentialsV2] = extractCredentialsFromParametersV2(requestContext)
    log.debug("extractCredentialsV2 - credentialsFromParameters: {}", credentialsFromParameters)

    val credentialsFromHeaders: Option[KnoraCredentialsV2] = extractCredentialsFromHeaderV2(requestContext, appConfig)
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
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsFromParametersV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {
    // extract email/password from parameters
    val params: Map[String, Seq[String]] = requestContext.request.uri.query().toMultiMap

    // check for iri, email, or username parameters
    val maybeIriIdentifier: Option[String]      = params.get("iri").map(_.head)
    val maybeEmailIdentifier: Option[String]    = params.get("email").map(_.head)
    val maybeUsernameIdentifier: Option[String] = params.get("username").map(_.head)
    val maybeIdentifier: Option[String] =
      List(maybeIriIdentifier, maybeEmailIdentifier, maybeUsernameIdentifier).flatten.headOption

    val maybePassword: Option[String] = params.get("password").map(_.head)

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

    val maybeTokenCreds: Option[KnoraJWTTokenCredentialsV2] = if (maybeToken.nonEmpty) {
      Some(KnoraJWTTokenCredentialsV2(maybeToken.get))
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
   * @param requestContext   the HTTP request context.
   * @param appConfig        the application's configuration.
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsFromHeaderV2(
    requestContext: RequestContext,
    appConfig: AppConfig
  ): Option[KnoraCredentialsV2] = {

    // Session token from cookie header
    val cookies: Seq[HttpCookiePair] = requestContext.request.cookies
    val maybeSessionCreds: Option[KnoraSessionCredentialsV2] =
      cookies.find(_.name == calculateCookieName(appConfig)) match {
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
            val trimmedValue    = value.substring(5).trim() // remove 'Basic '
            val decodedValue    = ByteString.fromArray(Base64.getDecoder.decode(trimmedValue)).decodeString("UTF8")
            val decodedValueArr = decodedValue.split(":", 2)
            (Some(decodedValueArr(0)), Some(decodedValueArr(1)))
          case None =>
            (None, None)
        }

        val maybePassCreds: Option[KnoraPasswordCredentialsV2] = if (maybeEmail.nonEmpty && maybePassword.nonEmpty) {
          Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = maybeEmail), maybePassword.get))
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

        val maybeTokenCreds: Option[KnoraJWTTokenCredentialsV2] = if (maybeToken.nonEmpty) {
          Some(KnoraJWTTokenCredentialsV2(maybeToken.get))
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
   * Tries to retrieve a [[UserADM]] based on the supplied credentials. If both email/password and session
   * token are supplied, then the user profile for the session token is returned. This method should only be used
   * with authenticated credentials.
   *
   * @param credentials          the user supplied credentials.
   * @param appConfig            the application's configuration.
   * @param system               the current [[ActorSystem]]
   * @param appActor             a reference to the application actor
   * @param executionContext  the current execution context
   * @return a [[UserADM]]
   * @throws AuthenticationException when the IRI can not be found inside the token, which is probably a bug.
   */
  private def getUserADMThroughCredentialsV2(
    credentials: Option[KnoraCredentialsV2],
    appConfig: AppConfig
  )(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[UserADM] =
    for {
      _ <- authenticateCredentialsV2(credentials, appConfig)

      user <- credentials match {
                case Some(passCreds: KnoraPasswordCredentialsV2) =>
                  getUserByIdentifier(
                    identifier = passCreds.identifier
                  )
                case Some(KnoraJWTTokenCredentialsV2(jwtToken)) =>
                  val userIri: IRI = JWTHelper.extractUserIriFromToken(
                    jwtToken,
                    appConfig.jwtSecretKey,
                    appConfig.knoraApi.externalKnoraApiHostPort
                  ) match {
                    case Some(iri) => iri
                    case None      =>
                      // should not happen, as the token is already validated
                      throw AuthenticationException(
                        "No IRI found inside token. Please report this as a possible bug."
                      )
                  }
                  getUserByIdentifier(
                    identifier = UserIdentifierADM(maybeIri = Some(userIri))
                  )
                case Some(KnoraSessionCredentialsV2(sessionToken)) =>
                  val userIri: IRI = JWTHelper.extractUserIriFromToken(
                    sessionToken,
                    appConfig.jwtSecretKey,
                    appConfig.knoraApi.externalKnoraApiHostPort
                  ) match {
                    case Some(iri) => iri
                    case None      =>
                      // should not happen, as the token is already validated
                      throw AuthenticationException(
                        "No IRI found inside token. Please report this as a possible bug."
                      )
                  }
                  getUserByIdentifier(
                    identifier = UserIdentifierADM(maybeIri = Some(userIri))
                  )
                case None =>
                  throw BadCredentialsException(BAD_CRED_NONE_SUPPLIED)
              }

    } yield user

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // TRIPLE STORE ACCESS
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Tries to get a [[UserADM]].
   *
   * @param identifier           the IRI, email, or username of the user to be queried
   * @param system               the current akka actor system
   * @param appActor             a reference to the application actor
   * @param executionContext     the current execution context
   * @return a [[UserADM]]
   * @throws BadCredentialsException when either the supplied email is empty or no user with such an email could be found.
   */
  private def getUserByIdentifier(identifier: UserIdentifierADM)(implicit
    system: ActorSystem,
    appActor: ActorRef,
    executionContext: ExecutionContext
  ): Future[UserADM] = tracedFuture("authenticator-get-user-by-identifier") {
    for {
      maybeUserADM <-
        appActor
          .ask(
            UserGetADM(
              identifier = identifier,
              userInformationTypeADM = UserInformationTypeADM.Full,
              requestingUser = KnoraSystemInstances.Users.SystemUser
            )
          )(Duration(100, SECONDS))
          .mapTo[Option[UserADM]]

      user = maybeUserADM match {
               case Some(u) => u
               case None =>
                 log.debug(s"getUserByIdentifier - supplied identifier not found - throwing exception")
                 throw BadCredentialsException(s"$BAD_CRED_USER_NOT_FOUND")
             }
    } yield user
  }

  /**
   * Calculates the cookie name, where the external host and port are encoded as a base32 string
   * to make the name of the cookie unique between environments.
   *
   * The default padding needs to be changed from '=' to '9' because '=' is not allowed inside the cookie!!!
   * This also needs to be changed in all the places that base32 is used to calculate the cookie name, e.g., sipi.
   *
   * @param appConfig the application's configuration.
   * @return the calculated cookie name as [[String]]
   */
  def calculateCookieName(appConfig: AppConfig): String = {
    //
    val base32 = new Base32('9'.toByte)
    "KnoraAuthentication" + base32.encodeAsString(appConfig.knoraApi.externalKnoraApiHostPort.getBytes())
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
   * @param issuer    the principal that issued the JWT.
   * @param content   any other content to be included in the token.
   * @return a [[String]] containing the JWT.
   */
  def createToken(
    userIri: IRI,
    secret: String,
    longevity: Duration,
    issuer: String,
    content: Map[String, JsValue] = Map.empty
  ): String = {
    val stringFormatter = StringFormatter.getGeneralInstance

    // now in seconds
    val now: Long = System.currentTimeMillis() / 1000L

    // calculate expiration time (seconds)
    val nowPlusLongevity: Long = now + longevity.toSeconds

    val identifier: String = stringFormatter.base64EncodeUuid(UUID.randomUUID)

    val claim: String = JwtClaim(
      content = JsObject(content).compactPrint,
      issuer = Some(issuer),
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
   * @param issuer the principal that issued the JWT.
   * @return a [[Boolean]].
   */
  def validateToken(token: String, secret: String, issuer: String): Boolean =
    if (CacheUtil.get[UserADM](AUTHENTICATION_INVALIDATION_CACHE_NAME, token).nonEmpty) {
      // token invalidated so no need to decode
      log.debug("validateToken - token found in invalidation cache, so not valid")
      false
    } else {
      decodeToken(token, secret, issuer).isDefined
    }

  /**
   * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
   *
   * @param token  the JWT.
   * @param secret the secret used to encode the token.
   * @param issuer the principal that issued the JWT.
   * @return an optional [[IRI]].
   */
  def extractUserIriFromToken(token: String, secret: String, issuer: String): Option[IRI] =
    decodeToken(token, secret, issuer) match {
      case Some((_: JwtHeader, claim: JwtClaim)) => claim.subject
      case None                                  => None
    }

  /**
   * Extracts application-specific content from a JWT token.  This method also makes sure that the required headers
   * and claims are present.
   *
   * @param token       the JWT.
   * @param secret      the secret used to encode the token.
   * @param contentName the name of the content field to be extracted.
   * @param issuer      the principal that issued the JWT.
   * @return the [[String]] value of the specified content field.
   */
  def extractContentFromToken(token: String, secret: String, contentName: String, issuer: String): Option[String] =
    decodeToken(token, secret, issuer) match {
      case Some((_: JwtHeader, claim: JwtClaim)) =>
        claim.content.parseJson.asJsObject.fields.get(contentName) match {
          case Some(jsString: JsString) => Some(jsString.value)
          case _                        => None
        }

      case None => None
    }

  /**
   * Decodes and validates a JWT token.
   *
   * @param token  the token to be decoded.
   * @param secret the secret used to encode the token.
   * @param issuer      the principal that issued the JWT.
   * @return the token's header and claim, or `None` if the token is invalid.
   */
  private def decodeToken(token: String, secret: String, issuer: String): Option[(JwtHeader, JwtClaim)] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    JwtSprayJson.decodeAll(token, secret, Seq(JwtAlgorithm.HS256)) match {
      case Success((header: JwtHeader, claim: JwtClaim, _)) =>
        val missingRequiredContent: Boolean = Set(
          header.typ.isDefined,
          claim.issuer.isDefined && claim.issuer.contains(issuer),
          claim.subject.isDefined,
          claim.jwtId.isDefined,
          claim.issuedAt.isDefined,
          claim.expiration.isDefined,
          claim.audience.isDefined
        ).contains(false)

        if (!missingRequiredContent) {
          Try(
            stringFormatter
              .validateAndEscapeIri(claim.subject.get, throw BadRequestException("Invalid user IRI in JWT"))
          ) match {
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
