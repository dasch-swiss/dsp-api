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

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.RequestContext
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.settings.KnoraSettings.FeatureToggleBaseConfig
import org.knora.webapi.settings.KnoraSettingsImpl

/**
 * A tagging trait for module-specific factories that produce implementations of features.
 */
trait FeatureFactory

/**
 * A tagging trait for classes that implement features returned by feature factories.
 */
trait Feature

/**
 * Represents a feature toggle.
 *
 * @param featureName the name of the feature.
 * @param isEnabled   `true` if the feature should be enabled, `false` if it should be disabled.
 * @param version     the version of the feature that should be enabled, or `None` if the feature
 *                    doesn't have versions.
 */
case class FeatureToggle(featureName: String,
                         isEnabled: Boolean,
                         version: Option[Int])

object FeatureToggle {
    private val TRUE_STRINGS: Set[String] = Set("true", "yes", "on")
    private val FALSE_STRINGS: Set[String] = Set("false", "no", "off")

    /**
     * Constructs a default [[FeatureToggle]] from a [[FeatureToggleBaseConfig]].
     *
     * @param baseConfig a feature toggle's base configuration.
     * @return a [[FeatureToggle]] representing the feature's default setting.
     */
    def fromFeatureToggleBaseConfig(baseConfig: FeatureToggleBaseConfig): FeatureToggle = {
        FeatureToggle(
            featureName = baseConfig.featureName,
            isEnabled = baseConfig.enabledByDefault,
            version = baseConfig.defaultVersion
        )
    }

    /**
     * Parses the values of a feature toggle from non-base configuration.
     *
     * @param featureName     the name of the feature.
     * @param enabledStr      `true`, `yes`, or `on` if the feature should be enabled; `false`, `no`, or `off`
     *                        if it should be disabled.
     * @param maybeVersionStr the version of the feature that should be used.
     * @param baseConfig      the base configuration of the toggle.
     * @return a [[FeatureToggle]] for the toggle.
     */
    def parse(featureName: String,
              enabledStr: String,
              maybeVersionStr: Option[String],
              baseConfig: FeatureToggleBaseConfig)(implicit stringFormatter: StringFormatter): FeatureToggle = {
        if (!baseConfig.overrideAllowed) {
            throw BadRequestException(s"Feature toggle '$featureName' cannot be overridden")
        }

        // Accept the boolean values that are accepted in application.conf.
        val enabled: Boolean = if (TRUE_STRINGS.contains(enabledStr.toLowerCase)) {
            true
        } else if (FALSE_STRINGS.contains(enabledStr.toLowerCase)) {
            false
        } else {
            throw BadRequestException(s"Invalid boolean '$enabledStr' in feature toggle $featureName")
        }

        val version: Option[Int] = maybeVersionStr.map {
            versionStr =>
                val versionInt = stringFormatter.validateInt(versionStr, throw BadRequestException(s"Invalid version number in feature toggle $featureName: $versionStr"))

                if (!baseConfig.availableVersions.contains(versionInt)) {
                    throw BadRequestException(s"Feature '$featureName' has no version $versionInt")
                }

                versionInt
        }

        if (version.isEmpty && baseConfig.availableVersions.nonEmpty) {
            throw BadRequestException(s"You must specify a version number for feature toggle $featureName")
        }

        FeatureToggle(
            featureName = featureName,
            isEnabled = enabled,
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
    protected[feature] def getToggleBaseConfig(featureName: String): FeatureToggleBaseConfig

    /**
     * Gets the base configurations of all feature toggles.
     */
    protected[feature] def getAllToggleBaseConfigs: Set[FeatureToggleBaseConfig]

    /**
     * Returns the setting of a feature toggle in the local configuration source
     * of this [[FeatureFactoryConfig]].
     *
     * @param featureName the name of a feature.
     * @return the setting for that feature in this [[FeatureFactoryConfig]]'s configuration
     *         source, or `None` if the source contains no setting for that feature.
     */
    protected[feature] def getLocalToggleSetting(featureName: String): Option[FeatureToggle]

    /**
     * Returns the settings of all enabled features, taking into account the base configuration
     * and the parent configuration.
     */
    def getAllEnabledFeatures: Set[FeatureToggle] = {
        val allBaseConfigs: Set[FeatureToggleBaseConfig] = getAllToggleBaseConfigs

        allBaseConfigs.map {
            baseConfig: FeatureToggleBaseConfig => getToggleSetting(baseConfig.featureName)
        }.filter(_.isEnabled)
    }

    /**
     * Returns the setting of a feature toggle, taking into account the base configuration
     * and the parent configuration.
     *
     * @param featureName the name of the feature.
     * @return the setting of the feature toggle.
     */
    def getToggleSetting(featureName: String): FeatureToggle = {
        // Get the base configuration for the feature.
        val baseConfig: FeatureToggleBaseConfig = getToggleBaseConfig(featureName)

        // Do we represent the base configuration?
        maybeParent match {
            case None =>
                // Yes. Return our setting.
                FeatureToggle.fromFeatureToggleBaseConfig(baseConfig)

            case Some(parent) =>
                // No. Can the default setting be overridden?
                if (baseConfig.overrideAllowed) {
                    // Yes. Do we have a setting for this feature?
                    getLocalToggleSetting(featureName) match {
                        case Some(setting) =>
                            // Yes. Return our setting.
                            setting

                        case None =>
                            // We don't have a setting for this feature. Delegate to the parent.
                            parent.getToggleSetting(featureName)
                    }
                } else {
                    // The default setting can't be overridden. Return it.
                    FeatureToggle.fromFeatureToggleBaseConfig(baseConfig)
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

    override protected[feature] def getToggleBaseConfig(featureName: String): FeatureToggleBaseConfig = {
        baseConfigs.getOrElse(featureName, throw BadRequestException(s"No such feature: $featureName"))
    }

    override protected[feature] def getAllToggleBaseConfigs: Set[FeatureToggleBaseConfig] = {
        baseConfigs.values.toSet
    }

    override protected[feature] def getLocalToggleSetting(featureName: String): Option[FeatureToggle] = {
        Some(FeatureToggle.fromFeatureToggleBaseConfig(getToggleBaseConfig(featureName)))
    }
}

/**
 * An abstract class for feature factory configs that don't represent the base configuration.
 *
 * @param parent the parent config.
 */
abstract class OverridingFeatureFactoryConfig(parent: FeatureFactoryConfig) extends FeatureFactoryConfig(Some(parent)) {
    protected val featureToggles: Map[String, FeatureToggle]

    override protected[feature] def getToggleBaseConfig(featureName: String): FeatureToggleBaseConfig = {
        parent.getToggleBaseConfig(featureName)
    }

    override protected[feature] def getAllToggleBaseConfigs: Set[FeatureToggleBaseConfig] = {
        parent.getAllToggleBaseConfigs
    }

    override protected[feature] def getLocalToggleSetting(featureName: String): Option[FeatureToggle] = {
        featureToggles.get(featureName)
    }
}

object RequestContextFeatureFactoryConfig {
    /**
     * The name of the HTTP header containing feature toggles.
     */
    val FEATURE_TOGGLE_HEADER: String = "X-Knora-Feature-Toggle"
    val FEATURE_TOGGLE_HEADER_LOWERCASE: String = FEATURE_TOGGLE_HEADER.toLowerCase
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from a header in an HTTP request.
 *
 * @param requestContext the HTTP request context.
 * @param parent         the parent [[FeatureFactoryConfig]].
 */
class RequestContextFeatureFactoryConfig(private val requestContext: RequestContext,
                                         parent: FeatureFactoryConfig)(implicit stringFormatter: StringFormatter) extends OverridingFeatureFactoryConfig(parent) {

    import RequestContextFeatureFactoryConfig._

    // Read feature toggles from an HTTP header.
    protected override val featureToggles: Map[String, FeatureToggle] = {
        // Was the feature toggle header submitted?
        requestContext.request.headers.find(_.lowercaseName == FEATURE_TOGGLE_HEADER_LOWERCASE) match {
            case Some(featureToggleHeader: HttpHeader) =>
                // Yes. Parse it into comma-separated key-value pairs, each representing a feature toggle.
                featureToggleHeader.value.split(',').map {
                    headerValueItem: String =>
                        headerValueItem.split('=').map(_.trim) match {
                            case Array(featureName: String, featureToggle: String) =>
                                val baseConfig = parent.getToggleBaseConfig(featureName)

                                // Does this toggle setting specify a version number?
                                val toggleSetting = featureToggle.split(';').map(_.trim) match {
                                    case Array(enabledStr: String) =>
                                        // Yes.
                                        FeatureToggle.parse(
                                            featureName = featureName,
                                            enabledStr = enabledStr,
                                            maybeVersionStr = None,
                                            baseConfig = baseConfig
                                        )

                                    case Array(enabledStr: String, versionStr: String) =>
                                        // No.
                                        FeatureToggle.parse(
                                            featureName = featureName,
                                            enabledStr = enabledStr,
                                            maybeVersionStr = Some(versionStr),
                                            baseConfig = baseConfig
                                        )
                                }

                                featureName -> toggleSetting

                            case _ =>
                                throw BadRequestException(s"Invalid value for header $FEATURE_TOGGLE_HEADER: ${featureToggleHeader.value}")
                        }
                }.toMap

            case None => Map.empty
        }
    }
}

/**
 * A [[FeatureFactoryConfig]] with a fixed configuration, to be used in tests.
 *
 * @param testSettings the settings to be used.
 */
class TestFeatureFactoryConfig(testSettings: Set[FeatureToggle], parent: FeatureFactoryConfig) extends OverridingFeatureFactoryConfig(parent) {
    protected override val featureToggles: Map[String, FeatureToggle] = testSettings.map {
        setting => setting.featureName -> setting
    }.toMap
}
