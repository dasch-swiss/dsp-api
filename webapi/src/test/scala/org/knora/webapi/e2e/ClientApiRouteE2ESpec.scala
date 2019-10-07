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

package org.knora.webapi.e2e

import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.E2ESpec
import org.knora.webapi.util.FileUtil

import scala.concurrent.duration._
import sys.process._
import org.apache.commons.io.FileUtils
import org.knora.webapi.testing.tags.E2ETest

object ClientApiRouteE2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Tests client code generation.
 */
@E2ETest
class ClientApiRouteE2ESpec extends E2ESpec(ClientApiRouteE2ESpec.config) {
    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    "The client API route" should {
        "generate a Zip file of TypeScript code that compiles" in {
            val request = Get(baseApiUrl + s"/clientapi/typescript?mock=true")
            val response: HttpResponse = singleAwaitingRequest(request = request, duration = 20480.millis)
            val responseBytes: Array[Byte] = getResponseEntityBytes(response)
            val filenames: Set[String] = getZipContents(responseBytes)

            // Check that some expected filenames are included in the Zip file.

            val expectedFilenames: Set[String] = Set(
                "./package.json",
                "./tsconfig.json",
                "./knora-api-config.ts",
                "./knora-api-connection.ts",
                "./api/endpoint.ts",
                "./api/admin/admin-endpoint.ts",
                "./api/admin/users/users-endpoint.ts",
                "./models/admin/user.ts"
            )

            assert(expectedFilenames.subsetOf(filenames))

            // Unzip the file to a temporary directory.
            val tempDir: Path = Files.createTempDirectory("clientapi")
            val zipFilePath: Path = tempDir.resolve("clientapi.zip")
            val srcPath: Path = tempDir.resolve("src")
            srcPath.toFile.mkdir()
            FileUtil.writeBinaryFile(zipFilePath.toFile, responseBytes)
            unzip(zipFilePath = zipFilePath, outputPath = srcPath)

            // Run 'npm install'.
            val npmInstallExitCode: Int = Process(Seq("npm", "install"), srcPath.toFile).!
            assert(npmInstallExitCode == 0)

            // Run the TypeScript compiler.
            val typeScriptCompilerExitCode: Int = Process(Seq("./node_modules/typescript/bin/tsc"), srcPath.toFile).!
            assert(typeScriptCompilerExitCode == 0)

            // Delete the temporary directory.
            FileUtils.deleteDirectory(tempDir.toFile)
        }
    }
}
