/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi

import java.io.File

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigValue}
import org.knora.webapi.SettingsConstants._
import org.knora.webapi.util.CacheUtil.KnoraCacheConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration._


/**
  * Reads application settings that come from `application.conf`.
  */
class SettingsImpl(config: Config) extends Extension {

    // print config
    val printConfig: Boolean = config.getBoolean("app.print-config")

    // used for communication inside the knora stack
    val internalKnoraApiHost: String = config.getString("app.knora-api.internal-host")
    val internalKnoraApiPort: Int = config.getInt("app.knora-api.internal-port")
    val internalKnoraApiBaseUrl: String = "http://" + internalKnoraApiHost + (if (internalKnoraApiPort != 80) ":" + internalKnoraApiPort else "")

    // used for communication between the outside and the knora stack, e.g., browser
    val externalKnoraApiProtocol: String = config.getString("app.knora-api.external-protocol")
    val externalKnoraApiHost: String = config.getString("app.knora-api.external-host")
    val externalKnoraApiPort: Int = config.getInt("app.knora-api.external-port")
    val externalKnoraApiHostPort: String = externalKnoraApiHost + (if (externalKnoraApiPort != 80) ":" + externalKnoraApiPort else "")
    val externalKnoraApiBaseUrl: String = externalKnoraApiProtocol + "://" + externalKnoraApiHost + (if (externalKnoraApiPort != 80) ":" + externalKnoraApiPort else "")


    val salsah1BaseUrl: String = config.getString("app.salsah1.base-url")
    val salsah1ProjectIconsBasePath: String = config.getString("app.salsah1.project-icons-basepath")

    val tmpDataDir: String = config.getString("app.tmp-datadir")
    val dataDir: String = config.getString("app.datadir")

    val imageMimeTypes: Vector[String] = config.getList("app.sipi.image-mime-types").iterator.asScala.map {
        (mType: ConfigValue) => mType.unwrapped.toString
    }.toVector

    val internalSipiProtocol: String = config.getString("app.sipi.internal-protocol")
    val internalSipiHost: String = config.getString("app.sipi.internal-host")
    val internalSipiPort: Int = config.getInt("app.sipi.internal-port")
    val internalSipiBaseUrl: String = internalSipiProtocol + "://" + internalSipiHost + (if (internalSipiPort != 80) ":" + internalSipiPort else "")

    val externalSipiProtocol: String = config.getString("app.sipi.external-protocol")
    val externalSipiHost: String = config.getString("app.sipi.external-host")
    val externalSipiPort: Int = config.getInt("app.sipi.external-port")
    val externalSipiBaseUrl: String = externalSipiProtocol + "://" + externalSipiHost + (if (externalSipiPort != 80) ":" + externalSipiPort else "")


    val sipiPrefix: String = config.getString("app.sipi.prefix")
    val sipiFileServerPrefix: String = config.getString("app.sipi.file-server-path")

    val externalSipiIIIFGetUrl: String = s"$externalSipiBaseUrl/$sipiPrefix"

    val internalSipiFileServerGetUrl: String = s"$internalSipiBaseUrl/$sipiFileServerPrefix/$sipiPrefix"
    val externalSipiFileServerGetUrl: String = s"$externalSipiBaseUrl/$sipiFileServerPrefix/$sipiPrefix"

    val internalSipiImageConversionUrl: String = s"$internalSipiBaseUrl"

    val sipiPathConversionRoute: String = config.getString("app.sipi.path-conversion-route")
    val sipiFileConversionRoute: String = config.getString("app.sipi.file-conversion-route")

    val caches: Vector[KnoraCacheConfig] = config.getList("app.caches").iterator.asScala.map {
        (cacheConfigItem: ConfigValue) =>
            val cacheConfigMap = cacheConfigItem.unwrapped.asInstanceOf[java.util.HashMap[String, Any]].asScala
            KnoraCacheConfig(cacheConfigMap("cache-name").asInstanceOf[String],
                cacheConfigMap("max-elements-in-memory").asInstanceOf[Int],
                cacheConfigMap("overflow-to-disk").asInstanceOf[Boolean],
                cacheConfigMap("eternal").asInstanceOf[Boolean],
                cacheConfigMap("time-to-live-seconds").asInstanceOf[Int],
                cacheConfigMap("time-to-idle-seconds").asInstanceOf[Int])
    }.toVector

    val defaultTimeout: Timeout = Timeout(config.getInt("app.default-timeout").seconds)
    val defaultRestoreTimeout: Timeout = Timeout(config.getInt("app.default-restore-timeout").seconds)
    val dumpMessages: Boolean = config.getBoolean("app.dump-messages")
    val showInternalErrors: Boolean = config.getBoolean("app.show-internal-errors")
    val maxResultsPerSearchResultPage: Int = config.getInt("app.max-results-per-search-result-page")
    val defaultIconSizeDimX: Int = config.getInt("app.gui.default-icon-size.dimX")
    val defaultIconSizeDimY: Int = config.getInt("app.gui.default-icon-size.dimY")
    val triplestoreType: String = config.getString("app.triplestore.dbtype")
    val triplestoreHost: String = config.getString("app.triplestore.host")

    val v2ResultsPerPage: Int = config.getInt("app.v2.resources-sequence.results-per-page")
    val searchValueMinLength: Int = config.getInt("app.v2.fulltext-search.search-value-min-length")

    val triplestorePort: Int = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getInt("app.triplestore.graphdb.port")
        case HTTP_FUSEKI_TS_TYPE => config.getInt("app.triplestore.fuseki.port")
        case other => 9999
    }

    val triplestoreDatabaseName: String = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.repository-name")
        case HTTP_FUSEKI_TS_TYPE => config.getString("app.triplestore.fuseki.repository-name")
        case other => ""
    }

    val triplestoreUsername: String = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.username")
        case other => ""
    }

    val triplestorePassword: String = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.password")
        case other => ""
    }

    //used in the store package
    val tripleStoreConfig: Config = config.getConfig("app.triplestore")

    val (fusekiTomcat, fusekiTomcatContext) = if (triplestoreType == HTTP_FUSEKI_TS_TYPE) {
        (config.getBoolean("app.triplestore.fuseki.tomcat"), config.getString("app.triplestore.fuseki.tomcat-context"))
    } else {
        (false, "")
    }

    private val fakeTriplestore: String = config.getString("app.triplestore.fake-triplestore")
    val prepareFakeTriplestore: Boolean = fakeTriplestore == "prepare"
    val useFakeTriplestore: Boolean = fakeTriplestore == "use"
    val fakeTriplestoreDataDir: File = new File(config.getString("app.triplestore.fake-triplestore-data-dir"))

    val skipAuthentication: Boolean = config.getBoolean("app.skip-authentication")

    val jwtSecretKey: String = config.getString("app.jwt-secret-key")
    val jwtLongevity: Long = config.getLong("app.jwt-longevity")

    val fallbackLanguage: String = config.getString("user.default-language")

    val profileQueries: Boolean = config.getBoolean("app.triplestore.profile-queries")

    val routesToReject: Seq[String] = config.getList("app.routes-to-reject").iterator.asScala.map {
        (mType: ConfigValue) => mType.unwrapped.toString
    }.toSeq

    // monitoring reporters
    val prometheusReporter: Boolean = config.getBoolean("app.monitoring.prometheus-reporter")
    val zipkinReporter: Boolean = config.getBoolean("app.monitoring.zipkin-reporter")
    val jaegerReporter: Boolean = config.getBoolean("app.monitoring.jaeger-reporter")

}

object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {

    override def lookup() = Settings

    override def createExtension(system: ExtendedActorSystem) =
        new SettingsImpl(system.settings.config)

    /**
      * Java API: retrieve the Settings extension for the given system.
      */
    override def get(system: ActorSystem): SettingsImpl = super.get(system)
}