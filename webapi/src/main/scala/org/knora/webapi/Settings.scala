/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
    val httpsKeystore: String = config.getString("app.http.https.keystore")
    val httpsKeystorePassword: String = config.getString("app.http.https.keystore-password")

    val knoraApiHost: String = config.getString("app.http.knora-api.host")
    val knoraApiHttpPort: Int = config.getInt("app.http.knora-api.http-port")
    val knoraApiHttpsPort: Int = config.getInt("app.http.knora-api.https-port")
    val knoraApiUseHttp: Boolean = config.getBoolean("app.http.knora-api.use-http")
    val knoraApiUseHttps: Boolean = config.getBoolean("app.http.knora-api.use-https")
    val knoraApiHttpsBaseUrl: String = s"https://$knoraApiHost:$knoraApiHttpsPort"
    val knoraApiHttpBaseUrl: String = s"http://$knoraApiHost:$knoraApiHttpPort"
    val knoraApiDefaultBaseUrl: String = if (knoraApiUseHttps) knoraApiHttpsBaseUrl else knoraApiHttpBaseUrl

    val salsahBaseUrl: String = config.getString("app.http.salsah.base-url")
    val salsahProjectIconsBasePath: String = config.getString("app.http.salsah.project-icons-basepath")

    val tmpDataDir: String = config.getString("app.tmp-datadir")
    val dataDir: String = config.getString("app.datadir")

    val imageMimeTypes: Vector[String] = config.getList("app.sipi.image-mime-types").iterator.asScala.map {
        (mType: ConfigValue) => mType.unwrapped.toString
    }.toVector

    val sipiBaseUrl: String = config.getString("app.sipi.url")
    val sipiPort: Int = config.getInt("app.sipi.port")
    val sipiPrefix: String = config.getString("app.sipi.prefix")
    val sipiIIIFGetUrl: String = s"$sipiBaseUrl:$sipiPort/$sipiPrefix"
    val sipiFileServerPrefix: String = config.getString("app.sipi.file-server-path")
    val sipiFileServerGetUrl: String = s"$sipiBaseUrl:$sipiPort/$sipiFileServerPrefix/$sipiPrefix"
    val sipiImageConversionUrl: String = s"$sipiBaseUrl:$sipiPort"
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

    val triplestorePort: Int = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getInt("app.triplestore.graphdb.port")
        case HTTP_GRAPH_DB_FREE_TS_TYPE => config.getInt("app.triplestore.graphdb-free.port")
        case HTTP_STARDOG_TS_TYPE => config.getInt("app.triplestore.stardog.port")
        case HTTP_ALLEGRO_TS_TYPE => config.getInt("app.triplestore.allegro.port")
        case HTTP_FUSEKI_TS_TYPE => config.getInt("app.triplestore.fuseki.port")
        case HTTP_VIRTUOSO_TYPE => config.getInt("app.triplestore.virtuoso.port")
        case other => 9999
    }

    val triplestoreDatabaseName: String = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.repository-name")
        case HTTP_GRAPH_DB_FREE_TS_TYPE => config.getString("app.triplestore.graphdb-free.repository-name")
        case HTTP_STARDOG_TS_TYPE => config.getString("app.triplestore.stardog.repository-name")
        case HTTP_ALLEGRO_TS_TYPE => config.getString("app.triplestore.allegro.repository-name")
        case HTTP_FUSEKI_TS_TYPE => config.getString("app.triplestore.fuseki.repository-name")
        case other => ""
    }

    val triplestoreUsername: String = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.username")
        case HTTP_GRAPH_DB_FREE_TS_TYPE => config.getString("app.triplestore.graphdb-free.username")
        case HTTP_STARDOG_TS_TYPE => config.getString("app.triplestore.stardog.username")
        case HTTP_ALLEGRO_TS_TYPE => config.getString("app.triplestore.allegro.username")
        case HTTP_VIRTUOSO_TYPE => config.getString("app.triplestore.virtuoso.username")
        case other => ""
    }

    val triplestorePassword: String = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.password")
        case HTTP_GRAPH_DB_FREE_TS_TYPE => config.getString("app.triplestore.graphdb-free.password")
        case HTTP_STARDOG_TS_TYPE => config.getString("app.triplestore.stardog.password")
        case HTTP_ALLEGRO_TS_TYPE => config.getString("app.triplestore.stardog.password")
        case HTTP_VIRTUOSO_TYPE => config.getString("app.triplestore.virtuoso.password")
        case other => ""
    }

    //used in the store package
    val tripleStoreConfig: Config = config.getConfig("app.triplestore")

    private val fakeTriplestore: String = config.getString("app.triplestore.fake-triplestore")
    val prepareFakeTriplestore: Boolean = fakeTriplestore == "prepare"
    val useFakeTriplestore: Boolean = fakeTriplestore == "use"
    val fakeTriplestoreDataDir: File = new File(config.getString("app.triplestore.fake-triplestore-data-dir"))

    val skipAuthentication: Boolean = config.getBoolean("app.skip-authentication")

    val jwtSecretKey: String = config.getString("app.jwt-secret-key")
    val jwtLongevity: Long = config.getLong("app.jwt-longevity")

    val fallbackLanguage: String = config.getString("user.default-language")

    val profileQueries: Boolean = config.getBoolean("app.triplestore.profile-queries")
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