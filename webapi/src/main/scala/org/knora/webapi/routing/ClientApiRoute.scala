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

package org.knora.webapi.routing

import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model.headers.{ContentDispositionTypes, `Content-Disposition`}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi._
import org.knora.webapi.routing.admin.AdminClientApi
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.clientapi._

import scala.concurrent.Future

class ClientApiRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {
    private val TYPESCRIPT: String = "typescript"

    private val apiDefs = Seq(
        new AdminClientApi(routeData)
    )

    def knoraApiPath: Route = {

        path("clientapi" / Segment) { target: String =>
            get {
                // Respond with a Content-Disposition header specifying the filename of the generated Zip file.
                respondWithHeader(`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> s"$target-client-api.zip"))) {
                    requestContext =>
                        // Construct the generator back end for the specified target.
                        val generatorBackEnd: GeneratorBackEnd = target match {
                            case TYPESCRIPT => new TypeScriptBackEnd
                            case _ => throw ClientApiGenerationException(s"Unknown target: $target")
                        }

                        val httpResponseFuture: Future[HttpResponse] = for {
                            requestingUser <- getUserADM(requestContext)

                            // Construct the generator front end.
                            generatorFrontEnd = new GeneratorFrontEnd(routeData, requestingUser)

                            // Get the class definitions from the front end.
                            backEndInputFutures: Seq[Future[ClientApiBackendInput]] = apiDefs.map {
                                apiDef =>
                                    for {
                                        classDefs <- generatorFrontEnd.getClientClassDefs(apiDef)
                                    } yield ClientApiBackendInput(apiDef, classDefs)
                            }

                            backEndInputSeq: Seq[ClientApiBackendInput] <- Future.sequence(backEndInputFutures)
                            backEndInputs = backEndInputSeq.toSet

                            // Generate source code.
                            sourceCode: Set[ClientSourceCodeFileContent] = generatorBackEnd.generateClientSourceCode(backEndInputs)

                            // Generate a Zip file from the source code.
                            zipFileBytes = generateZipFile(sourceCode)
                        } yield HttpResponse(
                            status = StatusCodes.OK,
                            entity = HttpEntity(bytes = zipFileBytes)
                        )

                        requestContext.complete(httpResponseFuture)
                }
            }
        }
    }

    private def generateZipFile(sourceCode: Set[ClientSourceCodeFileContent]): Array[Byte] = {
        // Generate the contents of the Zip file: a Map of file names to file contents (byte arrays).
        val zipFileContents: Map[String, Array[Byte]] = sourceCode.map {
            fileContent: ClientSourceCodeFileContent =>
                fileContent.filePath -> fileContent.text.getBytes(StandardCharsets.UTF_8)
        }.toMap

        FileUtil.createZipFileBytes(zipFileContents)
    }
}
