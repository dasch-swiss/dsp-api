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

package org.knora.webapi

import java.io.File

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigValue}
import org.knora.webapi.SettingsConstants._
import org.knora.webapi.util.CacheUtil.KnoraCacheConfig

import scala.collection.JavaConversions._
import scala.concurrent.duration._


/**
  * Reads application settings that come from `application.conf`.
  */
class SettingsImpl(config: Config) extends Extension {
    val baseApiUrl = config.getString("app.http.base-api-url")
    val httpInterface = config.getString("app.http.interface")
    val tmpDataDir = config.getString("app.tmp-datadir")
    val dataDir = config.getString("app.datadir")
    val imageMimeTypes: Vector[String] = config.getList("app.sipi.image-mime-types").iterator.map {
        (mType: ConfigValue) => mType.unwrapped.toString
    }.toVector

    val sipiBaseUrl = config.getString("app.sipi.url")
    val sipiPort = config.getString("app.sipi.port")
    val sipiPrefix = config.getString("app.sipi.prefix")
    val sipiUrl = s"$sipiBaseUrl:$sipiPort/$sipiPrefix"
    val sipiPathConversionRoute = config.getString("app.sipi.path-conversion-route")
    val sipiFileConversionRoute = config.getString("app.sipi.file-conversion-route")
    val httpPort = config.getInt("app.http.port")
    val caches = config.getList("app.caches").iterator.map {
        (cacheConfigItem: ConfigValue) =>
            val cacheConfigMap = cacheConfigItem.unwrapped.asInstanceOf[java.util.HashMap[String, Any]]
            KnoraCacheConfig(cacheConfigMap("cache-name").asInstanceOf[String],
                cacheConfigMap("max-elements-in-memory").asInstanceOf[Int],
                cacheConfigMap("overflow-to-disk").asInstanceOf[Boolean],
                cacheConfigMap("eternal").asInstanceOf[Boolean],
                cacheConfigMap("time-to-live-seconds").asInstanceOf[Int],
                cacheConfigMap("time-to-idle-seconds").asInstanceOf[Int])
    }.toVector
    val defaultTimeout = Timeout(config.getInt("app.default-timeout").seconds)
    val dumpMessages = config.getBoolean("app.dump-messages")
    val showInternalErrors = config.getBoolean("app.show-internal-errors")
    val maxResultsPerSearchResultPage = config.getInt("app.max-results-per-search-result-page")
    val defaultIconSizeDimX = config.getInt("app.gui.default-icon-size.dimX")
    val defaultIconSizeDimY = config.getInt("app.gui.default-icon-size.dimY")
    val triplestoreType = config.getString("app.triplestore.dbtype")
    val triplestoreHost = config.getString("app.triplestore.host")
    val triplestorePort = triplestoreType match {
        case HTTP_SESAME_TS_TYPE => config.getInt("app.triplestore.sesame.port")
        case HTTP_GRAPH_DB_TS_TYPE => config.getInt("app.triplestore.graphdb.port")
        case HTTP_GRAPH_DB_FREE_TS_TYPE => config.getInt("app.triplestore.graphdb.port")
        case HTTP_FUSEKI_TS_TYPE => config.getInt("app.triplestore.fuseki.port")
        case other => 9999
    }
    val triplestoreDatabaseName = triplestoreType match {
        case HTTP_SESAME_TS_TYPE => config.getString("app.triplestore.sesame.repository-name")
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.repository-name")
        case HTTP_GRAPH_DB_FREE_TS_TYPE => config.getString("app.triplestore.graphdb.repository-name")
        case HTTP_FUSEKI_TS_TYPE => config.getString("app.triplestore.fuseki.repository-name")
        case other => ""
    }
    val triplestoreUsername = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.username")
        case other => ""
    }
    val triplestorePassword = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => config.getString("app.triplestore.graphdb.password")
        case other => ""
    }

    //used in the store package
    val tripleStoreConfig = config.getConfig("app.triplestore")

    val (fusekiTomcat, fusekiTomcatContext) = if (triplestoreType == HTTP_FUSEKI_TS_TYPE) {
        (config.getBoolean("app.triplestore.fuseki.tomcat"), config.getString("app.triplestore.fuseki.tomcat-context"))
    } else {
        (false, "")
    }

    private val fakeTriplestore = config.getString("app.triplestore.fake-triplestore")
    val prepareFakeTriplestore = fakeTriplestore == "prepare"
    val useFakeTriplestore = fakeTriplestore == "use"
    val fakeTriplestoreDataDir = new File(config.getString("app.triplestore.fake-triplestore-data-dir"))

    val skipAuthentication = config.getBoolean("app.skip-authentication")

    val fallbackLanguage = config.getString("user.default-language")

    // Project specific named graphs stored in a map
    // http://deploymentzone.com/2013/07/25/typesafe-config-and-maps-in-scala/
    lazy val projectNamedGraphs: Map[IRI, ProjectNamedGraphs] = {
        config.getConfigList("app.project-named-graphs").map(new ProjectNamedGraphs(_)).map(elem => (elem.project, elem)).toMap
    }
}

class ProjectNamedGraphs(params: Config) {
    val project: String = params.getString("project")
    val ontology: IRI = params.getString("ontology")
    val data: IRI = params.getString("data")
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