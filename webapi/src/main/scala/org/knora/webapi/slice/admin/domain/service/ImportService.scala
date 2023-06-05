/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.query.QueryExecution
import org.apache.jena.query.ResultSet
import org.apache.jena.rdfconnection.RDFConnection
import org.apache.jena.rdfconnection.RDFConnectionFuseki
import zio._
import zio.http.URL
import zio.nio.file.Files
import zio.nio.file.Path
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Triplestore

trait ImportService {
  def createDataset(): Task[Unit]
  def configureFuseki(): Task[Unit]
  def importTrigFile(file: Path): Task[Unit]
  def query(queryString: String)(exec: QueryExecution => ResultSet): ZIO[Scope, Throwable, ResultSet]
  def querySelect(queryString: String): ZIO[Scope, Throwable, ResultSet] = query(queryString)(_.execSelect())
}
final case class ImportServiceLive(config: Triplestore) extends ImportService {

  private val fusekiBaseUrl: URL = {
    val str      = config.host + ":" + config.fuseki.port
    val protocol = if (config.useHttps) "https://" else "http://"
    URL.fromString(protocol + str).getOrElse(throw new IllegalStateException(s"Invalid fuseki url: $str"))
  }
  private val httpClient = {
    val basicAuth = new Authenticator {
      override def getPasswordAuthentication =
        new PasswordAuthentication(config.fuseki.username, config.fuseki.password.toCharArray)
    }
    HttpClient.newBuilder().authenticator(basicAuth).build()
  }

  private def makePostRequest(url: URL): HttpRequest = makePostRequestWithBody(url, HttpRequest.BodyPublishers.noBody())

  private def makePostRequestWithBody(url: URL, body: BodyPublisher): HttpRequest = HttpRequest
    .newBuilder()
    .uri(url.toJavaURI)
    .POST(body)
    .build()

  override def createDataset(): Task[Unit] = {
    val url = fusekiBaseUrl.setPath("/$/datasets?dbType=" + config.dbtype + "&dbName=" + config.fuseki.repositoryName)
    ZIO.attempt(httpClient.send(makePostRequest(url), HttpResponse.BodyHandlers.ofString()))
  }

  private def connect(): ZIO[Scope, Throwable, RDFConnection] = {
    def acquire(fusekiUrl: URL, httpClient: HttpClient) =
      ZIO.attempt(RDFConnectionFuseki.service(fusekiUrl.encode).httpClient(httpClient).build())
    def release(con: RDFConnection) = ZIO.attempt(con.close()).logError.ignore
    ZIO.acquireRelease(acquire(fusekiBaseUrl.setPath(s"/${config.fuseki.repositoryName}"), httpClient))(release)
  }

  override def importTrigFile(file: Path): Task[Unit] = ZIO.scoped {
    for {
      connection   <- connect()
      absolutePath <- file.toAbsolutePath
      _            <- ZIO.attempt(connection.loadDataset(absolutePath.toString()))
      _            <- ZIO.logDebug(s"Imported $absolutePath into ${config.fuseki.repositoryName}")
    } yield ()
  }

  override def query(query: String)(executor: QueryExecution => ResultSet): ZIO[Scope, Throwable, ResultSet] = {
    val acquire                            = connect().map(_.query(query))
    def release(queryExec: QueryExecution) = ZIO.attempt(queryExec.close()).unit.logError.ignore
    ZIO.acquireRelease(acquire)(release).map(executor)
  }

  override def configureFuseki(): Task[Unit] = {
    val configFile = "/Users/christian/git/dasch/dsp-api/webapi/scripts/fuseki-repository-config.ttl.template"
    for {
      content <- Files
                   .readAllLines(Path(configFile))
                   .map(_.map(line => line.replace("@REPOSITORY@", config.fuseki.repositoryName)).mkString("\n"))
      post = HttpRequest
               .newBuilder()
               .uri(fusekiBaseUrl.setPath("/$/datasets").toJavaURI)
               .POST(BodyPublishers.ofByteArray(content.getBytes()))
               .header("Content-Type", "text/turtle; charset=utf-8")
               .build()
      response <- ZIO.attempt(httpClient.send(post, HttpResponse.BodyHandlers.ofString()))
    } yield response
  }
}

object ImportServiceLive {
  val layer: URLayer[AppConfig, ImportService] =
    ZLayer.fromZIO(ZIO.serviceWith[AppConfig](config => ImportServiceLive(config.triplestore)))
}
