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
import zio.nio.file.Files
import zio.nio.file.Path

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.http.HttpClient

import dsp.valueobjects.Project
import dsp.valueobjects.Project.ShortCode
import org.knora.webapi.config.Triplestore
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

@accessible
trait ProjectImportService {
  def importProject(id: ShortCode, user: UserADM): Task[Option[Path]]
  def importTrigFile(file: Path): Task[Unit]
  def query(queryString: String)(exec: QueryExecution => ResultSet): ZIO[Scope, Throwable, ResultSet]
  def querySelect(queryString: String): ZIO[Scope, Throwable, ResultSet] = query(queryString)(_.execSelect())
}

final case class Asset(belongsToProject: Project.ShortCode, internalFilename: String)
object Asset {
  def logString(it: Asset) = s"Asset(code: ${it.belongsToProject.value}, path: ${it.internalFilename}"
}

final case class ProjectImportServiceLive(
  private val config: Triplestore,
  private val exportStorage: ProjectExportStorageService,
  private val dspIngestClient: DspIngestClient
) extends ProjectImportService {

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

  private def connect(): ZIO[Scope, Throwable, RDFConnection] = {
    def acquire(fusekiUrl: URL, httpClient: HttpClient) =
      ZIO.attempt(RDFConnectionFuseki.service(fusekiUrl.encode).httpClient(httpClient).build())
    def release(connection: RDFConnection) = ZIO.attempt(connection.close()).logError.ignore
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

  override def importProject(projectShortcode: ShortCode, user: UserADM): Task[Option[Path]] = {
    val projectImport = exportStorage.projectExportFullPath(projectShortcode)
    ZIO.whenZIO(Files.exists(projectImport))(importProject(projectImport, projectShortcode))
  }

  private def importProject(projectImport: Path, project: ShortCode): Task[Path] = ZIO.scoped {
    for {
      projectImportAbsolutePath <- projectImport.toAbsolutePath
      _                         <- ZIO.logInfo(s"Importing project $projectImportAbsolutePath")
      tmpUnzipped               <- Files.createTempDirectoryScoped(Some("project-import"), fileAttributes = Nil)
      unzipped                  <- ZipUtility.unzipFile(projectImport, tmpUnzipped)
      _                         <- importTriples(unzipped, project)
      _                         <- importAssets(unzipped, project)
      _                         <- ZIO.logInfo(s"Imported project $projectImportAbsolutePath")
    } yield projectImport
  }

  private def importTriples(path: Path, projectShortCode: ShortCode) = {
    val trigFile = path / exportStorage.trigFilename(projectShortCode)
    for {
      trigFileAbsolutePath <- trigFile.toAbsolutePath
      _                    <- ZIO.logInfo(s"Importing triples from $trigFileAbsolutePath")
      _ <- ZIO
             .fail(new IllegalStateException(s"trig file does not exist in export ${path.toAbsolutePath}"))
             .whenZIO(Files.notExists(trigFile))
      _ <- importTrigFile(trigFile)
      _ <- ZIO.logInfo(s"Imported triples from $trigFileAbsolutePath")
    } yield ()
  }

  private def importAssets(unzipped: Path, project: ShortCode) = ZIO.scoped {
    val assetsDir = unzipped / ProjectExportStorageService.assetsDirectoryInExport
    (for {
      tempDir <- Files.createTempDirectoryScoped(Some("assets-import"), fileAttributes = Nil)
      zipFile <- ZipUtility.zipFolder(assetsDir, tempDir)
      _ <- dspIngestClient
             .importProject(project, zipFile)
             .tapError(err => ZIO.logError(s"Error importing assets: ${err.getMessage}"))
    } yield ()).whenZIO(Files.isDirectory(assetsDir)) orElse ZIO.logWarning(s"No assets to import $project")
  }
}

object ProjectImportServiceLive {
  val layer = ZLayer.fromFunction(ProjectImportServiceLive.apply _)
}
