/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.feature

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, HttpResponse}
import akka.http.scaladsl.server.RequestContext
import org.knora.webapi.exceptions.{BadRequestException, FeatureToggleException}
import org.knora.webapi.settings.KnoraSettings.FeatureToggleBaseConfig
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.annotation.tailrec
import scala.util.control.Exception._
import scala.util.{Failure, Success, Try}

/**
 * A tagging trait for module-specific factories that produce implementations of features.
 */
trait FeatureFactory

/**
 * A tagging trait for classes that implement features returned by feature factories.
 */
trait Feature

/**
 * A tagging trait for case objects representing feature versions.
 */
trait Version

/**
 * A trait representing the state of a feature toggle.
 */
sealed trait FeatureToggleState

/**
 * Indicates that a feature toggle is off.
 */
case object ToggleStateOff extends FeatureToggleState

/**
 * Indicates that a feature toggle is on.
 *
 * @param version the configured version of the toggle.
 */
case class ToggleStateOn(version: Int) extends FeatureToggleState

/**
 * Represents a feature toggle state, for use in match-case expressions.
 */
sealed trait MatchableState[+T]

/**
 * A matchable object indicating that a feature toggle is off.
 */
case object Off extends MatchableState[Nothing]

/**
 * A matchable object indicating that a feature toggle is on.
 *
 * @param versionObj a case object representing the enabled version of the toggle.
 * @tparam T the type of the case object.
 */
case class On[T <: Version](versionObj: T) extends MatchableState[T]

/**
 * Represents a feature toggle.
 *
 * @param featureName the name of the feature toggle.
 * @param state       the state of the feature toggle.
 */
case class FeatureToggle(featureName: String, state: FeatureToggleState) {

  /**
   * Returns `true` if this toggle is enabled.
   */
  def isEnabled: Boolean =
    state match {
      case ToggleStateOn(_) => true
      case ToggleStateOff   => false
    }

  /**
   * Returns a [[MatchableState]] indicating the state of this toggle, for use in match-case expressions.
   *
   * @param versionObjects case objects representing the supported versions of the feature, in ascending
   *                       order by version number.
   * @tparam T a sealed trait implemented by the version objects.
   * @return one of the objects in `versionObjects`, or [[Off]].
   */
  def getMatchableState[T <: Version](versionObjects: T*): MatchableState[T] =
    state match {
      case ToggleStateOn(version) =>
        if (version < 1) {
          // Shouldn't happen; this error should have been caught already.
          throw FeatureToggleException(s"Invalid version number $version for toggle $featureName")
        }

        if (versionObjects.size < version) {
          // The caller didn't pass enough version objects.
          throw FeatureToggleException(s"Not enough version objects for $featureName")
        }

        // Return the version object whose position in the sequence corresponds to the configured version.
        // This relies on the fact that version numbers must be an ascending sequence of consecutive
        // integers starting from 1.
        On(versionObjects(version - 1))

      case ToggleStateOff => Off
    }
}

object FeatureToggle {

  /**
   * The name of the HTTP request header containing feature toggles.
   */
  val REQUEST_HEADER: String = "X-Knora-Feature-Toggles"
  val REQUEST_HEADER_LOWERCASE: String = REQUEST_HEADER.toLowerCase

  /**
   * The name of the HTTP response header that lists configured feature toggles.
   */
  val RESPONSE_HEADER: String = REQUEST_HEADER
  val RESPONSE_HEADER_LOWERCASE: String = REQUEST_HEADER_LOWERCASE

  /**
   * Constructs a default [[FeatureToggle]] from a [[FeatureToggleBaseConfig]].
   *
   * @param baseConfig a feature toggle's base configuration.
   * @return a [[FeatureToggle]] representing the feature's default setting.
   */
  def fromBaseConfig(baseConfig: FeatureToggleBaseConfig): FeatureToggle =
    FeatureToggle(
      featureName = baseConfig.featureName,
      state = if (baseConfig.enabledByDefault) {
        ToggleStateOn(baseConfig.defaultVersion)
      } else {
        ToggleStateOff
      }
    )

  /**
   * Constructs a feature toggle from non-base configuration.
   *
   * @param featureName  the name of the feature.
   * @param isEnabled    `true` if the feature should be enabled.
   * @param maybeVersion the version of the feature that should be used.
   * @param baseConfig   the base configuration of the toggle.
   * @return a [[FeatureToggle]] for the toggle.
   */
  def apply(
    featureName: String,
    isEnabled: Boolean,
    maybeVersion: Option[Int],
    baseConfig: FeatureToggleBaseConfig
  ): FeatureToggle = {
    if (!baseConfig.overrideAllowed) {
      throw BadRequestException(s"Feature toggle $featureName cannot be overridden")
    }

    for (version: Int <- maybeVersion) {
      if (!baseConfig.availableVersions.contains(version)) {
        throw BadRequestException(s"Feature toggle $featureName has no version $version")
      }
    }

    val state: FeatureToggleState = (isEnabled, maybeVersion) match {
      case (true, Some(definedVersion)) => ToggleStateOn(definedVersion)
      case (false, None)                => ToggleStateOff
      case (true, None) =>
        throw BadRequestException(s"You must specify a version number to enable feature toggle $featureName")
      case (false, Some(_)) =>
        throw BadRequestException(s"You cannot specify a version number when disabling feature toggle $featureName")
    }

    FeatureToggle(
      featureName = featureName,
      state = state
    )
  }
}

/**
 * An abstract class representing configuration for a [[FeatureFactory]] from a particular
 * configuration source.
 *
 * @param maybeParent if this [[FeatureFactoryConfig]] has no setting for a particular
 *                    feature toggle, it delegates to its parent.
 */
abstract class FeatureFactoryConfig(protected val maybeParent: Option[FeatureFactoryConfig]) {

  /**
   * Gets the base configuration for a feature toggle.
   *
   * @param featureName the name of the feature.
   * @return the toggle's base configuration.
   */
  protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig

  /**
   * Gets the base configurations of all feature toggles.
   */
  protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig]

  /**
   * Returns a feature toggle in the configuration source of this [[FeatureFactoryConfig]].
   *
   * @param featureName the name of a feature.
   * @return the configuration of the feature toggle in this [[FeatureFactoryConfig]]'s configuration
   *         source, or `None` if the source contains no configuration for that feature toggle.
   */
  protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle]

  /**
   * Returns a string giving the state of all feature toggles.
   */
  def makeToggleSettingsString: Option[String] = {
    // Convert each toggle to its string representation.
    val enabledToggles: Set[String] = getAllBaseConfigs.map { baseConfig: FeatureToggleBaseConfig =>
      val featureToggle: FeatureToggle = getToggle(baseConfig.featureName)

      val toggleStateStr: String = featureToggle.state match {
        case ToggleStateOn(version) => s":$version=on"
        case ToggleStateOff         => s"=off"
      }

      s"${featureToggle.featureName}$toggleStateStr"
    }

    // Are any toggles enabled?
    if (enabledToggles.nonEmpty) {
      // Yes. Return a header.
      Some(enabledToggles.mkString(","))
    } else {
      // No. Don't return a header.
      None
    }
  }

  /**
   * Returns an [[HttpHeader]] giving the state of all feature toggles.
   */
  def makeHttpResponseHeader: Option[HttpHeader] =
    makeToggleSettingsString.map { settingsStr: String =>
      RawHeader(FeatureToggle.RESPONSE_HEADER, settingsStr)
    }

  /**
   * Adds an [[HttpHeader]] to an [[HttpResponse]] indicating which feature toggles are enabled.
   */
  def addHeaderToHttpResponse(httpResponse: HttpResponse): HttpResponse =
    makeHttpResponseHeader match {
      case Some(header) => httpResponse.withHeaders(header)
      case None         => httpResponse
    }

  /**
   * Returns a feature toggle, taking into account the base configuration
   * and the parent configuration.
   *
   * @param featureName the name of the feature.
   * @return the feature toggle.
   */
  @tailrec
  final def getToggle(featureName: String): FeatureToggle = {
    // Get the base configuration for the feature.
    val baseConfig: FeatureToggleBaseConfig = getBaseConfig(featureName)

    // Do we represent the base configuration?
    maybeParent match {
      case None =>
        // Yes. Return our setting.
        FeatureToggle.fromBaseConfig(baseConfig)

      case Some(parent) =>
        // No. Can the default setting be overridden?
        if (baseConfig.overrideAllowed) {
          // Yes. Do we have a setting for this feature?
          getLocalConfig(featureName) match {
            case Some(setting) =>
              // Yes. Return our setting.
              setting

            case None =>
              // We don't have a setting for this feature. Delegate to the parent.
              parent.getToggle(featureName)
          }
        } else {
          // The default setting can't be overridden. Return it.
          FeatureToggle.fromBaseConfig(baseConfig)
        }
    }
  }
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from the application's configuration file.
 *
 * @param knoraSettings a [[KnoraSettingsImpl]] representing the configuration in the application's
 *                      configuration file.
 */
class KnoraSettingsFeatureFactoryConfig(knoraSettings: KnoraSettingsImpl) extends FeatureFactoryConfig(None) {
  private val baseConfigs: Map[String, FeatureToggleBaseConfig] = knoraSettings.featureToggles.map { baseConfig =>
    baseConfig.featureName -> baseConfig
  }.toMap

  override protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig =
    baseConfigs.getOrElse(featureName, throw BadRequestException(s"No such feature: $featureName"))

  override protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig] =
    baseConfigs.values.toSet

  override protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle] =
    Some(FeatureToggle.fromBaseConfig(getBaseConfig(featureName)))
}

/**
 * An abstract class for feature factory configs that don't represent the base configuration.
 *
 * @param parent the parent config.
 */
abstract class OverridingFeatureFactoryConfig(parent: FeatureFactoryConfig) extends FeatureFactoryConfig(Some(parent)) {
  protected val featureToggles: Map[String, FeatureToggle]

  override protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig =
    parent.getBaseConfig(featureName)

  override protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig] =
    parent.getAllBaseConfigs

  override protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle] =
    featureToggles.get(featureName)
}

object RequestContextFeatureFactoryConfig {
  // Strings that we accept as Boolean true values.
  val TRUE_STRINGS: Set[String] = Set("true", "yes", "on")

  // Strings that we accept as Boolean false values.
  val FALSE_STRINGS: Set[String] = Set("false", "no", "off")
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from a header in an HTTP request.
 *
 * @param requestContext the HTTP request context.
 * @param parent         the parent [[FeatureFactoryConfig]].
 */
class RequestContextFeatureFactoryConfig(requestContext: RequestContext, parent: FeatureFactoryConfig)
    extends OverridingFeatureFactoryConfig(parent) {
  import FeatureToggle._
  import RequestContextFeatureFactoryConfig._

  private def invalidHeaderValue: Nothing = throw BadRequestException(s"Invalid value for header $REQUEST_HEADER")

  // Read feature toggles from an HTTP header.
  protected override val featureToggles: Map[String, FeatureToggle] = Try {
    // Was the feature toggle header submitted?
    requestContext.request.headers.find(_.lowercaseName == REQUEST_HEADER_LOWERCASE) match {
      case Some(featureToggleHeader: HttpHeader) =>
        // Yes. Parse it into comma-separated key-value pairs, each representing a feature toggle.
        val toggleSeq: Seq[(String, FeatureToggle)] = featureToggleHeader.value
          .split(',')
          .map { headerValueItem: String =>
            headerValueItem.split('=').map(_.trim) match {
              case Array(featureNameAndVersionStr: String, isEnabledStr: String) =>
                val featureNameAndVersion: Array[String] = featureNameAndVersionStr.split(':').map(_.trim)
                val featureName: String = featureNameAndVersion.head

                // Accept the boolean values that are accepted in application.conf.
                val isEnabled: Boolean = if (TRUE_STRINGS.contains(isEnabledStr.toLowerCase)) {
                  true
                } else if (FALSE_STRINGS.contains(isEnabledStr.toLowerCase)) {
                  false
                } else {
                  throw BadRequestException(s"Invalid boolean '$isEnabledStr' in feature toggle $featureName")
                }

                val maybeVersion: Option[Int] = featureNameAndVersion.drop(1).headOption.map { versionStr: String =>
                  allCatch
                    .opt(versionStr.toInt)
                    .getOrElse(
                      throw BadRequestException(s"Invalid version number '$versionStr' in feature toggle $featureName")
                    )
                }

                featureName -> FeatureToggle(
                  featureName = featureName,
                  isEnabled = isEnabled,
                  maybeVersion = maybeVersion,
                  baseConfig = parent.getBaseConfig(featureName)
                )

              case _ => invalidHeaderValue
            }
          }
          .toSeq

        if (toggleSeq.size > toggleSeq.map(_._1).toSet.size) {
          throw BadRequestException(s"You cannot set the same feature toggle more than once per request")
        }

        toggleSeq.toMap

      case None =>
        // No feature toggle header was submitted.
        Map.empty[String, FeatureToggle]
    }
  } match {
    case Success(parsedToggles) => parsedToggles

    case Failure(ex) =>
      ex match {
        case badRequest: BadRequestException => throw badRequest
        case _                               => invalidHeaderValue
      }
  }
}

/**
 * A [[FeatureFactoryConfig]] with a fixed configuration, to be used in tests.
 *
 * @param testToggles the toggles to be used.
 */
class TestFeatureFactoryConfig(testToggles: Set[FeatureToggle], parent: FeatureFactoryConfig)
    extends OverridingFeatureFactoryConfig(parent) {
  protected override val featureToggles: Map[String, FeatureToggle] = testToggles.map { setting =>
    setting.featureName -> setting
  }.toMap
}
