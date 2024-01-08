/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer
import zio.Task
import zio.ULayer
import zio.ZIO
import zio.http.URL
import zio.nio.file.Files
import zio.nio.file.Path

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.testcontainers.TestContainerOps.StartableOps

final class FusekiTestContainer extends GenericContainer[FusekiTestContainer](BuildInfo.fuseki) {

  def baseUrl: URL = {
    val urlString = s"http://$getHost:$getFirstMappedPort"
    URL.decode(urlString).getOrElse(throw new IllegalStateException(s"Invalid URL $urlString"))
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
                .uri(baseUrl.path("/$/datasets").toJavaURI)
                .POST(BodyPublishers.ofString(fusekiConfig))
                .header("Content-Type", "text/turtle; charset=utf-8")
                .build()
    _ <- ZIO
           .attempt(httpClient.send(request, BodyHandlers.ofString()))
           .filterOrFail(_.statusCode() == 200)(new IllegalStateException("Could not configure Fuseki"))
  } yield ()
}

object FusekiTestContainer {

  val adminPassword = "test"

  def initializeWithDataset(repositoryName: String): ZIO[FusekiTestContainer, Throwable, Unit] =
    ZIO.serviceWithZIO[FusekiTestContainer](_.initializeWithDataset(repositoryName))

  def make: FusekiTestContainer = new FusekiTestContainer()
    .withExposedPorts(3030)
    .withEnv("ADMIN_PASSWORD", adminPassword)
    .withEnv("JVM_ARGS", "-Xmx3G")

  val layer: ULayer[FusekiTestContainer] = make.toLayer
}
