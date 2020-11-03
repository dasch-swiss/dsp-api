/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.feature

import akka.http.scaladsl.model.{HttpHeader, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.RequestContext
import org.knora.webapi.exceptions.{BadRequestException, FeatureToggleException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.settings.KnoraSettings.FeatureToggleBaseConfig
import org.knora.webapi.settings.KnoraSettingsImpl

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
 * Represents a feature toggle.
 *
 * @param featureName the name of the feature.
 * @param isEnabled   `true` if the toggle is enabled.
 * @param version     if the toggle is enabled and has versions, specifies the configured version
 *                    of the feature.
 */
case class FeatureToggle(featureName: String,
                         isEnabled: Boolean,
                         version: Option[Int]) {

    /**
     * Gets a required version number, checks that it is a supported version, and converts it to
     * a case object for use in matching.
     *
     * @param versions case objects representing the supported versions of the feature.
     * @tparam T a sealed trait that includes all the case objects that represent supported versions of the feature.
     * @return the version number.
     */
    def checkVersion[T <: Version](versions: T*): T = {
        val configuredVersion: Int = version.getOrElse(throw FeatureToggleException(s"Feature toggle $featureName requires a version number"))

        if (configuredVersion < 1 || configuredVersion > versions.size) {
            throw FeatureToggleException(s"Invalid version number $configuredVersion for toggle $featureName")
        }

        versions(configuredVersion - 1)
    }
}

object FeatureToggle {
    /**
     * The name of the HTTP request header containing feature toggles.
     */
    val REQUEST_HEADER: String = "X-Knora-Feature-Toggles"
    val REQUEST_HEADER_LOWERCASE: String = REQUEST_HEADER.toLowerCase

    /**
     * The name of the HTTP response header indicating which feature toggles
     * are enabled.
     */
    val RESPONSE_HEADER: String = "X-Knora-Feature-Toggles-Enabled"
    val RESPONSE_HEADER_LOWERCASE: String = RESPONSE_HEADER.toLowerCase

    // Strings that we accept as Boolean true values.
    private val TRUE_STRINGS: Set[String] = Set("true", "yes", "on")

    // Strings that we accept as Boolean false values.
    private val FALSE_STRINGS: Set[String] = Set("false", "no", "off")

    /**
     * Constructs a default [[FeatureToggle]] from a [[FeatureToggleBaseConfig]].
     *
     * @param baseConfig a feature toggle's base configuration.
     * @return a [[FeatureToggle]] representing the feature's default setting.
     */
    def fromBaseConfig(baseConfig: FeatureToggleBaseConfig): FeatureToggle = {
        FeatureToggle(
            featureName = baseConfig.featureName,
            isEnabled = baseConfig.enabledByDefault,
            version = baseConfig.defaultVersion
        )
    }

    /**
     * Parses the configuration of a feature toggle from non-base configuration.
     *
     * @param featureName     the name of the feature.
     * @param isEnabledStr    `true`, `yes`, or `on` if the feature should be enabled; `false`, `no`, or `off`
     *                        if it should be disabled.
     * @param maybeVersionStr the version of the feature that should be used.
     * @param baseConfig      the base configuration of the toggle.
     * @return a [[FeatureToggle]] for the toggle.
     */
    def parse(featureName: String,
              isEnabledStr: String,
              maybeVersionStr: Option[String],
              baseConfig: FeatureToggleBaseConfig)(implicit stringFormatter: StringFormatter): FeatureToggle = {
        if (!baseConfig.overrideAllowed) {
            throw BadRequestException(s"Feature toggle $featureName cannot be overridden")
        }

        // Accept the boolean values that are accepted in application.conf.
        val isEnabled: Boolean = if (TRUE_STRINGS.contains(isEnabledStr.toLowerCase)) {
            true
        } else if (FALSE_STRINGS.contains(isEnabledStr.toLowerCase)) {
            false
        } else {
            throw BadRequestException(s"Invalid boolean '$isEnabledStr' in feature toggle $featureName")
        }

        val version: Option[Int] = maybeVersionStr.map {
            versionStr =>
                val versionInt = stringFormatter.validateInt(versionStr, throw BadRequestException(s"Invalid version number '$versionStr' in feature toggle $featureName"))

                if (!baseConfig.availableVersions.contains(versionInt)) {
                    throw BadRequestException(s"Feature $featureName has no version $versionInt")
                }

                versionInt
        }

        if (isEnabled && version.isEmpty && baseConfig.availableVersions.nonEmpty) {
            throw BadRequestException(s"You must specify a version number to enable feature toggle $featureName")
        }

        FeatureToggle(
            featureName = featureName,
            isEnabled = isEnabled,
            version = version
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
     * Returns an [[HttpHeader]] indicating which feature toggles are enabled.
     */
    def getHttpResponseHeader: Option[HttpHeader] = {
        // Get the set of toggles that are enabled.
        val enabledToggles: Set[FeatureToggle] = getAllBaseConfigs.map {
            baseConfig: FeatureToggleBaseConfig => getToggle(baseConfig.featureName)
        }.filter(_.isEnabled)

        // Are any toggles enabled?
        if (enabledToggles.nonEmpty) {
            // Yes. Assemble a header value describing them.
            val headerValue: String = enabledToggles.map {
                featureToggle =>
                    val headerValueBuilder = new StringBuilder
                    headerValueBuilder.append(featureToggle.featureName)

                    featureToggle.version match {
                        case Some(definedVersion) => headerValueBuilder.append(":").append(definedVersion)
                        case None => ()
                    }

                    headerValueBuilder.toString
            }.mkString(",")

            Some(RawHeader(FeatureToggle.RESPONSE_HEADER, headerValue))
        } else {
            // No. Don't return a header.
            None
        }
    }

    /**
     * Adds an [[HttpHeader]] to an [[HttpResponse]] indicating which feature toggles are enabled.
     */
    def addHeaderToHttpResponse(httpResponse: HttpResponse): HttpResponse = {
        getHttpResponseHeader match {
            case Some(header) => httpResponse.withHeaders(header)
            case None => httpResponse
        }
    }

    /**
     * Returns a feature toggle, taking into account the base configuration
     * and the parent configuration.
     *
     * @param featureName the name of the feature.
     * @return the feature toggle.
     */
    def getToggle(featureName: String): FeatureToggle = {
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
class KnoraSettingsFeatureFactoryConfig(private val knoraSettings: KnoraSettingsImpl) extends FeatureFactoryConfig(None) {
    private val baseConfigs: Map[String, FeatureToggleBaseConfig] = knoraSettings.featureToggles.map {
        baseConfig => baseConfig.featureName -> baseConfig
    }.toMap

    override protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig = {
        baseConfigs.getOrElse(featureName, throw BadRequestException(s"No such feature: $featureName"))
    }

    override protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig] = {
        baseConfigs.values.toSet
    }

    override protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle] = {
        Some(FeatureToggle.fromBaseConfig(getBaseConfig(featureName)))
    }
}

/**
 * An abstract class for feature factory configs that don't represent the base configuration.
 *
 * @param parent the parent config.
 */
abstract class OverridingFeatureFactoryConfig(parent: FeatureFactoryConfig) extends FeatureFactoryConfig(Some(parent)) {
    protected val featureToggles: Map[String, FeatureToggle]

    override protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig = {
        parent.getBaseConfig(featureName)
    }

    override protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig] = {
        parent.getAllBaseConfigs
    }

    override protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle] = {
        featureToggles.get(featureName)
    }
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from a header in an HTTP request.
 *
 * @param requestContext the HTTP request context.
 * @param parent         the parent [[FeatureFactoryConfig]].
 */
class RequestContextFeatureFactoryConfig(private val requestContext: RequestContext,
                                         parent: FeatureFactoryConfig)(implicit stringFormatter: StringFormatter) extends OverridingFeatureFactoryConfig(parent) {

    import FeatureToggle._

    private def invalidHeaderValue: Nothing = throw BadRequestException(s"Invalid value for header $REQUEST_HEADER")

    // Read feature toggles from an HTTP header.
    protected override val featureToggles: Map[String, FeatureToggle] = Try {
        // Was the feature toggle header submitted?
        requestContext.request.headers.find(_.lowercaseName == REQUEST_HEADER_LOWERCASE) match {
            case Some(featureToggleHeader: HttpHeader) =>

                // Yes. Parse it into comma-separated key-value pairs, each representing a feature toggle.
                featureToggleHeader.value.split(',').map {
                    headerValueItem: String =>
                        headerValueItem.split('=').map(_.trim) match {
                            case Array(featureNameAndVersionStr: String, enabledStr: String) =>
                                val featureNameAndVersion: Array[String] = featureNameAndVersionStr.split(':').map(_.trim)
                                val featureName: String = featureNameAndVersion.head

                                // Does this toggle setting specify a version number?
                                val maybeVersionStr: Option[String] = if (featureNameAndVersion.length == 2) {
                                    // Yes.
                                    Some(featureNameAndVersion.last)
                                } else {
                                    // No.
                                    None
                                }

                                featureName -> FeatureToggle.parse(
                                    featureName = featureName,
                                    isEnabledStr = enabledStr,
                                    maybeVersionStr = maybeVersionStr,
                                    baseConfig = parent.getBaseConfig(featureName)
                                )

                            case _ => invalidHeaderValue
                        }
                }.toMap

            case None => Map.empty[String, FeatureToggle]
        }
    } match {
        case Success(parsedToggles) => parsedToggles

        case Failure(ex) =>
            ex match {
                case badRequest: BadRequestException => throw badRequest
                case _ => invalidHeaderValue
            }
    }
}

/**
 * A [[FeatureFactoryConfig]] with a fixed configuration, to be used in tests.
 *
 * @param testToggles the toggles to be used.
 */
class TestFeatureFactoryConfig(testToggles: Set[FeatureToggle], parent: FeatureFactoryConfig) extends OverridingFeatureFactoryConfig(parent) {
    protected override val featureToggles: Map[String, FeatureToggle] = testToggles.map {
        setting => setting.featureName -> setting
    }.toMap
}
