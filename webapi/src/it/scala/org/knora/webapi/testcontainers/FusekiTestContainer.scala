/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.knora.webapi.http.version.BuildInfo
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import zio.{ZIO, _}
import zio.http.URL
import zio.macros.accessible
import zio.nio.file.{Files, Path}

import java.net.{Authenticator, PasswordAuthentication}
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

@accessible
trait FusekiTestContainer extends GenericContainer[FusekiTestContainer] {

  def baseUrl: URL = {
    val urlString = s"http://$getHost:$getFirstMappedPort"
    URL.fromString(urlString).getOrElse(throw new IllegalStateException(s"Invalid URL $urlString"))
  }

  def credentials: (String, String) = ("admin", FusekiTestContainer.adminPassword)

  private val httpClient: HttpClient = {
    val basicAuth: Authenticator = new Authenticator {
      val (username, password)               = credentials
      override def getPasswordAuthentication = new PasswordAuthentication(username, password.toCharArray)
    }
    HttpClient.newBuilder().authenticator(basicAuth).build()
  }

  def initializeWithDataset(repositoryName: String): Task[Unit] = for {
    _ <- ZIO.logInfo(s"Initializing and creating dataset $repositoryName")
    fusekiConfig <- Files
                      .readAllLines(Path(getClass.getResource("/fuseki-repository-config.ttl.template").getPath))
                      .map(_.map(line => line.replace("@REPOSITORY@", repositoryName)).mkString("\n"))
    request = HttpRequest
                .newBuilder()
                .uri(baseUrl.setPath("/$/datasets").toJavaURI)
                .POST(BodyPublishers.ofString(fusekiConfig))
                .header("Content-Type", "text/turtle; charset=utf-8")
                .build()
    _ <- ZIO
           .attempt(httpClient.send(request, BodyHandlers.ofString()))
           .filterOrFail(_.statusCode() == 200)(new IllegalStateException("Could not configure Fuseki"))
  } yield ()
}

object FusekiTestContainer {
  def apply(dockerImageName: DockerImageName): FusekiTestContainer =
    new GenericContainer[FusekiTestContainer](dockerImageName) with FusekiTestContainer

  def apply(): FusekiTestContainer =
    new GenericContainer[FusekiTestContainer](DockerImageName.parse(BuildInfo.fuseki)) with FusekiTestContainer

  val adminPassword = "test"

  private val acquire: Task[FusekiTestContainer] = {
    val container = FusekiTestContainer()
      .withExposedPorts(3030)
      .withEnv("ADMIN_PASSWORD", adminPassword)
      .withEnv("JVM_ARGS", "-Xmx3G")
    ZIO.attemptBlocking(container.start()).as(container).orDie <* ZIO.logInfo(">>> Acquire Fuseki TestContainer <<<")
  }

  private def release(container: FusekiTestContainer): UIO[Unit] =
    ZIO.attemptBlocking(container.stop()).logError.ignore <* ZIO.logInfo(">>> Release Fuseki TestContainer <<<")

  val layer: ULayer[FusekiTestContainer] = ZLayer.scoped(ZIO.acquireRelease(acquire)(release)).orDie
}
