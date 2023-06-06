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
import zio.macros.accessible
import zio.nio.file.Path

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.http.HttpClient

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Triplestore

@accessible
trait ImportService {
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
    HttpClient
      .newBuilder()
      .connectTimeout(config.queryTimeoutAsDuration)
      .authenticator(basicAuth)
      .build()
  }

  private def connect(): ZIO[Scope, Throwable, RDFConnection] = {
    def acquire(fusekiUrl: URL, httpClient: HttpClient) =
      ZIO.attempt(RDFConnectionFuseki.service(fusekiUrl.encode).httpClient(httpClient).build())
    def release(con: RDFConnection) = ZIO.attempt(con.close()).logError.ignore
    ZIO.acquireRelease(acquire(fusekiBaseUrl.setPath(s"/${config.fuseki.repositoryName}"), httpClient))(release)
  }

  override def importTrigFile(file: Path): Task[Unit] = ZIO.scoped {
    for {
      _            <- ZIO.logInfo(s"Importing $file into ${config.fuseki.repositoryName}")
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
}

object ImportServiceLive {
  val layer: URLayer[AppConfig, ImportService] =
    ZLayer.fromZIO(ZIO.serviceWith[AppConfig](config => ImportServiceLive(config.triplestore)))
}
