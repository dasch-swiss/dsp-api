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

package org.knora.webapi.util

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import org.knora.webapi.ClientApiGenerationException

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Represents a client API for the purposes of generating client test data.
 */
trait ClientApi {
    /**
     * The name of a directory in which the client test data can be generated.
     */
    val directoryName: String

    /**
     * The endpoints available in the API.
     */
    val endpoints: Seq[ClientEndpoint]

    /**
     * Returns test data for this API and its endpoints.
     *
     * @param testDataDirectoryPath the path of the top-level test data directory.
     * @return a set of test data files to be used for testing this API and its endpoints.
     */
    def getTestData(testDataDirectoryPath: Seq[String])(implicit executionContext: ExecutionContext,
                                                        actorSystem: ActorSystem,
                                                        materializer: ActorMaterializer): Future[Set[TestDataFileContent]] = {
        for {
            endpointTestData <- Future.sequence {
                endpoints.map {
                    endpoint: ClientEndpoint =>
                        for {
                            endpointTestData: Set[TestDataFileContent] <- endpoint.getTestData
                        } yield endpointTestData.map {
                            sourceCodeFileContent: TestDataFileContent =>
                                sourceCodeFileContent.copy(
                                    filePath = sourceCodeFileContent.filePath.copy(
                                        directoryPath = testDataDirectoryPath :+ directoryName :+ endpoint.directoryName
                                    )
                                )
                        }
                }
            }
        } yield endpointTestData.flatten.toSet
    }
}

/**
 * Represents a client endpoint.
 */
trait ClientEndpoint {
    /**
     * The name of a directory in which the endpoint test data can be generated.
     */
    val directoryName: String

    /**
     * Makes a request to Knora to get test data.
     *
     * @param request the request to send.
     * @return the string value of the response.
     */
    protected def doTestDataRequest(request: HttpRequest)(implicit executionContext: ExecutionContext,
                                                          actorSystem: ActorSystem,
                                                          materializer: ActorMaterializer): Future[String] = {
        for {
            response <- Http().singleRequest(request)
            responseStr <- response.entity.toStrict(10240.millis).map(_.data.decodeString("UTF-8"))

            _ = if (response.status.isFailure) {
                throw ClientApiGenerationException(s"Failed to get test data: $responseStr")
            }
        } yield responseStr
    }

    /**
     * Returns test data for this endpoint.
     *
     * @return a set of test data files to be used for testing this endpoint. The directory paths should be empty.
     */
    def getTestData(implicit executionContext: ExecutionContext,
                    actorSystem: ActorSystem,
                    materializer: ActorMaterializer): Future[Set[TestDataFileContent]]
}

/**
 * Represents the filesystem path of a file containing generated test data.
 *
 * @param directoryPath the path of the directory containing the file,
 *                      relative to the root directory of the source tree.
 * @param filename      the filename, without the file extension.
 * @param fileExtension the file extension.
 */
case class TestDataFilePath(directoryPath: Seq[String], filename: String, fileExtension: String) {
    override def toString: String = {
        (directoryPath :+ filename + "." + fileExtension).mkString("/")
    }
}

object TestDataFilePath {
    /**
     * A convenience method that makes a path for a JSON file in the current directory.
     *
     * @param filename the filename.
     * @return the file path.
     */
    def makeJsonPath(filename: String): TestDataFilePath = {
        TestDataFilePath(
            directoryPath = Seq.empty,
            filename = filename,
            fileExtension = "json"
        )
    }
}

/**
 * Represents a file containing generated client API test data.
 *
 * @param filePath the file path in which the test data should be saved.
 * @param text     the source code.
 */
case class TestDataFileContent(filePath: TestDataFilePath, text: String)
