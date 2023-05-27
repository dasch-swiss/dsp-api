/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.graph.Triple
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFWriter
import org.apache.jena.sparql.core.Quad
import zio.Chunk
import zio.Scope
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.OutputStream
import scala.collection.mutable

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants.KnoraBase.KnoraBaseOntologyIri
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.twirl.queries.sparql.admin.txt._
import org.knora.webapi.messages.util.rdf.TriG
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ZScopedJavaIoStreams

case class ProjectExportInfo(projectShortname: String, path: Path)
@accessible
trait ProjectExportService {
  def exportProject(project: KnoraProject, user: UserADM): Task[Path]

  def importProject(project: KnoraProject, user: UserADM): Task[Option[Path]]

  def listExports(): Task[Chunk[ProjectExportInfo]]

  /**
   * Exports a project to a file.
   * The file format is TriG.
   * The data exported is:
   * * the project metadata
   * * the project's permission data
   * * the triples of the project's ontologies
   *
   * @param project the project to be exported
   * @return the [[Path]] to the file to which the project was exported
   */
  def exportProjectTriples(project: KnoraProject): Task[Path]

  /**
   * Exports a project to a file.
   * The file format is TriG.
   * The data exported is:
   * * the project metadata
   * * the project's permission data
   * * the triples of the project's ontologies
   *
   * @param project    the project to be exported
   * @param targetFile the file to which the project is to be exported
   * @return the [[Path]] to the file to which the project was exported
   */
  def exportProjectTriples(project: KnoraProject, targetFile: Path): Task[Path]

}

/**
 * Represents a named graph to be saved to a TriG file.
 *
 * @param graphIri the IRI of the named graph.
 * @param tempDir  the directory in which the file is to be saved.
 */
private case class NamedGraphTrigFile(graphIri: InternalIri, tempDir: Path) {
  lazy val dataFile: Path = {
    val filename = graphIri.value.replaceAll("[.:/]", "_") + ".trig"
    tempDir / filename
  }
}

private object TriGCombiner {

  def combineTrigFiles(inputFiles: Iterable[Path], outputFile: Path): Task[Path] = ZIO.scoped {
    for {
      outFile   <- ZScopedJavaIoStreams.fileBufferedOutputStream(outputFile)
      outWriter <- createPrefixDedupStreamRDF(outFile).map { it => it.start(); it }
      _ <- ZIO.foreachDiscard(inputFiles)(file =>
             // Combine the files and write the output to the given OutputStream
             for {
               is <- ZScopedJavaIoStreams.fileInputStream(file)
               _  <- ZIO.attemptBlocking(RDFParser.source(is).lang(Lang.TRIG).parse(outWriter))
             } yield ()
           )
    } yield outputFile
  }

  private def createPrefixDedupStreamRDF(os: OutputStream): ZIO[Scope, Throwable, StreamRDF] = {
    def acquire                    = ZIO.attempt(StreamRDFWriter.getWriterStream(os, Lang.TRIG))
    def release(writer: StreamRDF) = ZIO.attempt(writer.finish()).logError.ignore
    ZIO.acquireRelease(acquire)(release).map(dedupPrefixStream)
  }

  // Define a custom StreamRDF implementation to filter out duplicate @prefix directives
  private def dedupPrefixStream(writer: StreamRDF): StreamRDF = new StreamRDFBase {
    private val prefixes = mutable.Set[String]()
    override def prefix(prefix: String, iri: String): Unit =
      if (!prefixes.contains(prefix)) {
        writer.prefix(prefix, iri)
        prefixes.add(prefix)
      }
    override def triple(triple: Triple): Unit = writer.triple(triple)
    override def quad(quad: Quad): Unit       = writer.quad(quad)
    override def base(base: String): Unit     = writer.base(base)
    override def finish(): Unit               = writer.finish()
    override def start(): Unit                = writer.start()
  }
}

final case class ProjectExportServiceLive(
  private val projectService: ProjectADMService,
  private val triplestoreService: TriplestoreService,
  private val assetService: AssetService,
  private val exportDirectory: Path
) extends ProjectExportService {

  private val assetsDirectoryInExport                       = "assets"
  private def projectExportDirectory(project: KnoraProject) = exportDirectory / s"${project.shortname}"
  private def projectExportFilename(project: KnoraProject)  = s"${project.shortname}-export.zip"
  private def projectExportPath(project: KnoraProject) =
    projectExportDirectory(project) / projectExportFilename(project)
  private def trigExportFilePath(project: KnoraProject, tempDir: Path) = tempDir / trigFilename(project)
  private def trigFilename(project: KnoraProject)                      = s"${project.shortname}.trig"

  override def exportProjectTriples(project: KnoraProject): Task[Path] =
    Files
      .createTempDirectory(Some(project.shortname), fileAttributes = Nil)
      .map(trigExportFilePath(project, _))
      .flatMap(exportProjectTriples(project, _))

  override def exportProjectTriples(project: KnoraProject, targetFile: Path): Task[Path] = ZIO.scoped {
    for {
      tempDir         <- Files.createTempDirectoryScoped(Some(project.shortname), fileAttributes = Nil)
      ontologyAndData <- downloadOntologyAndData(project, tempDir)
      adminData       <- downloadProjectAdminData(project, tempDir)
      permissionData  <- downloadPermissionData(project, tempDir)
      resultFile      <- mergeDataToFile(ontologyAndData :+ adminData :+ permissionData, targetFile)
    } yield resultFile
  }

  private def downloadOntologyAndData(project: KnoraProject, tempDir: Path): Task[List[NamedGraphTrigFile]] = for {
    allGraphsTrigFile <-
      projectService.getNamedGraphsForProject(project).map(_.map(NamedGraphTrigFile(_, tempDir)))
    files <- ZIO.foreach(allGraphsTrigFile)(file =>
               triplestoreService.sparqlHttpGraphFile(file.graphIri, file.dataFile, TriG).as(file)
             )
  } yield files

  /**
   * Downloads the admin related project metadata.
   * The data is saved to a file in TriG format.
   * The data contains:
   * * the project itself
   * * the users which are members of the project
   * * the groups which belong to the project
   * @param project The project to be exported.
   * @param targetDir The folder in which the file is to be saved.
   * @return A [[NamedGraphTrigFile]] containing the named graph and location of the file.
   */
  private def downloadProjectAdminData(project: KnoraProject, targetDir: Path): Task[NamedGraphTrigFile] = {
    val graphIri = adminDataNamedGraph
    val file     = NamedGraphTrigFile(graphIri, targetDir)
    for {
      query <- ZIO.attempt(getProjectAdminData(project.id.value))
      _     <- triplestoreService.sparqlHttpConstructFile(query.toString(), graphIri, file.dataFile, TriG)
    } yield file
  }

  private def downloadPermissionData(project: KnoraProject, tempDir: Path) = {
    val graphIri = permissionsDataNamedGraph
    val file     = NamedGraphTrigFile(graphIri, tempDir)
    for {
      query <- ZIO.attempt(getProjectPermissions(project.id.value))
      _     <- triplestoreService.sparqlHttpConstructFile(query.toString(), graphIri, file.dataFile, TriG)
    } yield file
  }

  private def mergeDataToFile(allData: Seq[NamedGraphTrigFile], targetFile: Path): Task[Path] =
    TriGCombiner.combineTrigFiles(allData.map(_.dataFile), targetFile)

  override def exportProject(project: KnoraProject, user: UserADM): Task[Path] = ZIO.scoped {
    val projectExportDir = projectExportDirectory(project)
    for {
      _            <- Files.createDirectories(projectExportDir)
      collectDir   <- Files.createTempDirectoryScoped(Some(project.shortname), fileAttributes = Nil)
      _            <- exportProjectTriples(project, trigExportFilePath(project, collectDir))
      _            <- exportProjectAssets(project, collectDir, user)
      zipped       <- ZipUtility.zipFolder(collectDir, projectExportDir, Some(projectExportFilename(project)))
      fileSize     <- Files.size(zipped)
      absolutePath <- zipped.toAbsolutePath
      _            <- ZIO.logInfo(s"Exported project ${project.shortname} to $absolutePath ($fileSize bytes)")
    } yield zipped
  }

  private def exportProjectAssets(project: KnoraProject, tempDir: Path, user: UserADM): ZIO[Any, Throwable, Path] = {
    val exportedAssetsDir = tempDir / assetsDirectoryInExport
    for {
      _ <- Files.createDirectory(exportedAssetsDir)
      _ <- assetService.exportProjectAssets(project, exportedAssetsDir, user)
    } yield exportedAssetsDir
  }

  override def importProject(project: KnoraProject, user: UserADM): Task[Option[Path]] = {
    val projectImport = projectExportPath(project)
    ZIO.whenZIO(Files.exists(projectImport))(importProject(projectImport, project))
  }

  private def importProject(projectImport: Path, project: KnoraProject): Task[Path] = ZIO.scoped {
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
  private def importTriples(path: Path, project: KnoraProject) = {
    val trigFile = path / trigFilename(project)
    for {
      trigFileAbsolutePath <- trigFile.toAbsolutePath
      _                    <- ZIO.logInfo(s"Importing triples from $trigFileAbsolutePath")
      _ <- ZIO
             .fail(new IllegalStateException(s"trig file does not exist in export ${path.toAbsolutePath}"))
             .whenZIO(Files.notExists(trigFile))
      _ <- ZIO.logInfo(s"Imported triples from $trigFileAbsolutePath")
    } yield ()
  }

  private def importAssets(unzipped: Path, project: KnoraProject) = {
    val assetsDir = unzipped / assetsDirectoryInExport
    (for {
      assetDirAbsolutePath <- assetsDir.toAbsolutePath
      _                    <- ZIO.logInfo(s"Importing assets from $assetDirAbsolutePath")
      assets <- Files
                  .find(assetsDir, 1)((path, attr) => attr.isRegularFile && !path.filename.endsWith(Path(".json")))
                  .map(filepath => Asset(project, filepath.filename.toString))
                  .runCollect
      _ <- ZIO.logInfo(s"Found ${assets.size} from $assetDirAbsolutePath")
      _ <- ZIO.foreachParDiscard(assets)(asset => ZIO.logInfo(s"Importing asset ${Asset.logString(asset)}"))
      _ <- ZIO.logInfo(s"Imported assets from $assetDirAbsolutePath")
    } yield ()).whenZIO(
      Files
        .isDirectory(assetsDir)
        .tap(isDirectory => ZIO.logInfo(s"No assets found in $assetsDir").when(!isDirectory))
    )
  }

  override def listExports(): Task[Chunk[ProjectExportInfo]] =
    Files
      .list(exportDirectory)
      .filterZIO(Files.isDirectory(_))
      .flatMap(projectDirectory =>
        Files.list(projectDirectory).filterZIO(Files.isRegularFile(_)).map(file => (projectDirectory, file))
      )
      .map(toProjectExportInfo)
      .runCollect

  private def toProjectExportInfo(projectDirAndFile: (Path, Path)) = {
    val (projectDirectory, exportFile) = projectDirAndFile
    val projectShortName               = projectDirectory.filename.toString()
    ProjectExportInfo(projectShortName, exportFile)
  }
}

trait AssetService {
  def exportProjectAssets(project: KnoraProject, tempDir: Path, user: UserADM): Task[Path]
}
case class Asset(belongsToProject: KnoraProject, internalFilename: String)
object Asset {
  def logString(asset: Asset) = s"asset:${asset.belongsToProject.shortcode}/${asset.internalFilename}"
}

final case class AssetServiceLive(
  private val triplestoreService: TriplestoreService,
  private val sipiClient: IIIFService,
  private val ontologyRepo: OntologyRepo
) extends AssetService {

  override def exportProjectAssets(project: KnoraProject, directory: Path, user: UserADM): Task[Path] = for {
    _ <- ZIO.logDebug(s"Exporting assets ${project.id}")
    assets <- determineAssets(project)
                .tap(it => ZIO.logInfo(s"Found ${it.size} assets for project ${project.shortcode}"))
    _ <-
      ZIO
        .foreachPar(assets)(sipiClient.downloadAsset(_, directory, user))
        .withParallelism(10)
        .tap { downloadedAssets =>
          val nrPresent    = assets.size
          val nrDownloaded = downloadedAssets.flatten.size
          ZIO.logInfo(s"Successfully downloaded $nrDownloaded/$nrPresent files for project ${project.shortcode}")
        }
  } yield directory

  private def determineAssets(project: KnoraProject): Task[List[Asset]] = {
    val projectGraph = ProjectADMService.projectDataNamedGraphV2(project)
    for {
      ontologyGraphs <- ontologyRepo.findOntologyGraphsByProject(project)
      query           = findAllAssets(ontologyGraphs :+ InternalIri(KnoraBaseOntologyIri), projectGraph)
      _              <- ZIO.logDebug(s"Querying assets for project ${project.id} = $query")
      result         <- triplestoreService.sparqlHttpSelect(query)
      bindings        = result.results.bindings
      assets          = bindings.flatMap(row => row.rowMap.get("internalFilename")).map(Asset(project, _)).toList
    } yield assets
  }
}

object AssetServiceLive {
  val layer: URLayer[OntologyRepo with IIIFService with TriplestoreService, AssetServiceLive] =
    ZLayer.fromFunction(AssetServiceLive.apply _)
}

object ProjectExportServiceLive {
  val layer: URLayer[AppConfig with AssetService with ProjectADMService with TriplestoreService, ProjectExportService] =
    ZLayer.fromZIO(
      for {
        exportDirectory    <- ZIO.serviceWith[AppConfig](_.tmpDataDirPath / "project-export")
        _                  <- Files.createDirectories(exportDirectory).orDie
        assetService       <- ZIO.service[AssetService]
        projectService     <- ZIO.service[ProjectADMService]
        triplestoreService <- ZIO.service[TriplestoreService]
      } yield {
        ProjectExportServiceLive(projectService, triplestoreService, assetService, exportDirectory)
      }
    )
}
