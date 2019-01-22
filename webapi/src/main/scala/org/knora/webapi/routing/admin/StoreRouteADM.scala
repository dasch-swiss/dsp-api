/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.admin

import java.io.File

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import io.swagger.annotations.Api
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.storesmessages.{ResetTriplestoreContentRequestADM, StoresADMJsonProtocol}
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.store.triplestore.util.TriplestoreDataUtil

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * A route used to send requests which can directly affect the data stored inside the triplestore.
  */

@Api(value = "store", produces = "application/json")
@Path("/admin/store")
class StoreRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with StoresADMJsonProtocol with TriplestoreDataUtil {

    /**
      * Creates the temporary file.
      *
      * @param fileInfo the file's info.
      * @return the temp file.
      */
    private def tempDestination(fileInfo: FileInfo): File =
        File.createTempFile(fileInfo.fileName, ".tmp")

    override def knoraApiPath = Route {
        path("admin" / "store") {
            get {
                requestContext =>

                    /** Maybe return some statistics about the store, e.g., what triplestore, number of triples in
                      * each named graph and in total, etc.
                      */
                    // TODO: Implement some simple return
                    requestContext.complete("Hello World")
            }
        } ~ path("admin" / "store" / "ResetTriplestoreContent") {
            post {
                /* ResetTriplestoreContent */
                entity(as[Seq[RdfDataObject]]) { apiRequest =>
                    requestContext =>

                        val dataWithPrependedDefaultData = prependDefaultData(apiRequest, settings)

                        val requestMessage = Future.successful(ResetTriplestoreContentRequestADM(dataWithPrependedDefaultData))

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )(timeout = 5.minutes, executionContext = executionContext)
                }
            }
        }
        /*
        ~ path("admin" / "triplestore") {
            get {
                requestContext =>

                    /** Maybe return some statistics about the triplestore, e.g., what triplestore, number of triples in
                      * each named graph and in total, etc.
                      */
                    // TODO: Implement some simple return
                    requestContext.complete("Hello World")
            }
        } ~ path("admin" / "triplestore" / "command" / "ResetTriplestoreContent") {
            post {
                /* ResetTriplestoreContent */
                storeUploadedFiles("ResetTriplestoreContentData", tempDestination) { files =>
                    val graphsWithFile = files.foldLeft(StatusCodes.OK) {
                        case (status, (metadata, file)) =>
                            // do something with the file and file metadata ...
                            // temp files will be deleted later

                            metadata.
                    }




                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )(timeout = 5.minutes, executionContext = executionContext)


                    complete(finalStatus)
            }
        }
        */

    }
}
