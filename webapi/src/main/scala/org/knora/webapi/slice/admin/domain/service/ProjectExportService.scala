package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.rdf.RdfFormatUtil
import org.knora.webapi.messages.util.rdf.RdfInputStreamSource
import org.knora.webapi.messages.util.rdf.RdfStreamProcessor
import org.knora.webapi.messages.util.rdf.Statement
import org.knora.webapi.messages.util.rdf.TriG
import org.knora.webapi.slice.admin.AdminConstants.adminDataGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

@accessible
trait ProjectExportService {

  /**
   * Exports a project to a file.
   * The file format is TriG.
   * The data exported is:
   *  * the project metadata
   *  * the project's permission data
   *  * the triples of the project's ontologies
   *
   * @param project the project to be exported
   * @return the [[Path]] to the file to which the project was exported
   */
  def exportProjectTriples(project: KnoraProject): Task[Path]
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
    tempDir.resolve(filename)
  }
}

/**
 * An [[RdfStreamProcessor]] for combining several named graphs into one.
 *
 * @param formattingStreamProcessor an [[RdfStreamProcessor]] for writing the combined result.
 */
private class CombiningRdfProcessor(formattingStreamProcessor: RdfStreamProcessor) extends RdfStreamProcessor {
  private var startedStatements = false

  // Ignore this, since it will be done before the first file is written.
  override def start(): Unit = {}

  // Ignore this, since it will be done after the last file is written.
  override def finish(): Unit = {}

  override def processNamespace(prefix: IRI, namespace: IRI): Unit =
    // Only accept namespaces from the first graph, to prevent conflicts.
    if (!startedStatements) {
      formattingStreamProcessor.processNamespace(prefix, namespace)
    }

  override def processStatement(statement: Statement): Unit = {
    startedStatements = true
    formattingStreamProcessor.processStatement(statement)
  }
}

final case class ProjectExportServiceLive(
  private val ontologyRepo: OntologyRepo,
  private val projectService: ProjectADMService,
  private val stringFormatter: StringFormatter,
  private val triplestoreService: TriplestoreService
) extends ProjectExportService {

  override def exportProjectTriples(project: KnoraProject): Task[Path] = {
    val tempDir = Files.createTempDirectory(project.shortname)
    for {
      _ <-
        ZIO.logDebug(s"Downloading project ${project.shortcode} data to temporary directory ${tempDir.toAbsolutePath}")
      ontologyAndData <- downloadOntologyAndData(project, tempDir)
      adminData       <- downloadProjectAdminData(project, tempDir)
      permissionData  <- downloadPermissionData(project, tempDir)
      resultFile      <- mergeDataToFile(ontologyAndData :+ adminData :+ permissionData, project, tempDir)
    } yield resultFile
  }

  private def downloadOntologyAndData(project: KnoraProject, tempDir: Path): Task[List[NamedGraphTrigFile]] = for {
    allGraphsTrigFile <-
      projectService.getNamedGraphsForProject(project).map(_.map(NamedGraphTrigFile(_, tempDir)))
    files <- ZIO.foreach(allGraphsTrigFile)(file =>
               triplestoreService.sparqlHttpGraphFile(file.graphIri, file.dataFile, TriG).as(file)
             )
  } yield files

  private def downloadProjectAdminData(project: KnoraProject, tempDir: Path): Task[NamedGraphTrigFile] = {
    val graphIri = adminDataGraph
    val file     = NamedGraphTrigFile(graphIri, tempDir)
    for {
      query <- ZIO.attempt(twirl.queries.sparql.admin.txt.getProjectAdminData(project.id.value))
      _     <- triplestoreService.sparqlHttpConstructFile(query.toString(), graphIri, file.dataFile, TriG)
    } yield file
  }

  private def downloadPermissionData(project: KnoraProject, tempDir: Path) = {
    val graphIri = permissionsDataGraph
    val file     = NamedGraphTrigFile(graphIri, tempDir)
    for {
      query <- ZIO.attempt(twirl.queries.sparql.admin.txt.getProjectPermissions(project.id.value))
      _     <- triplestoreService.sparqlHttpConstructFile(query.toString(), graphIri, file.dataFile, TriG)
    } yield file
  }

  private def mergeDataToFile(allData: Seq[NamedGraphTrigFile], project: KnoraProject, tempDir: Path): Task[Path] = {
    val filename         = project.shortname + ".trig"
    val resultFile: Path = tempDir.resolve(filename)
    combineGraphs(allData, resultFile).as(resultFile)
  }

  /**
   * Combines several TriG files into one.
   *
   * @param namedGraphTrigFiles the TriG files to combine.
   * @param resultFile          the output file.
   */
  private def combineGraphs(namedGraphTrigFiles: Seq[NamedGraphTrigFile], resultFile: Path): Task[Unit] = ZIO.attempt {
    val rdfFormatUtil: RdfFormatUtil                                = RdfFeatureFactory.getRdfFormatUtil()
    var maybeBufferedFileOutputStream: Option[BufferedOutputStream] = None

    val trigFileTry: Try[Unit] = Try {
      maybeBufferedFileOutputStream = Some(new BufferedOutputStream(Files.newOutputStream(resultFile)))

      val formattingStreamProcessor: RdfStreamProcessor = rdfFormatUtil.makeFormattingStreamProcessor(
        outputStream = maybeBufferedFileOutputStream.get,
        rdfFormat = TriG
      )

      val combiningRdfProcessor = new CombiningRdfProcessor(formattingStreamProcessor)
      formattingStreamProcessor.start()

      for (namedGraphTrigFile: NamedGraphTrigFile <- namedGraphTrigFiles) {
        val namedGraphTry: Try[Unit] = Try {
          rdfFormatUtil.parseWithStreamProcessor(
            rdfSource =
              RdfInputStreamSource(new BufferedInputStream(Files.newInputStream(namedGraphTrigFile.dataFile))),
            rdfFormat = TriG,
            rdfStreamProcessor = combiningRdfProcessor
          )
        }

        Files.delete(namedGraphTrigFile.dataFile)

        namedGraphTry match {
          case Success(_)  => ()
          case Failure(ex) => throw ex
        }
      }

      formattingStreamProcessor.finish()
    }

    maybeBufferedFileOutputStream.foreach(_.close)

    trigFileTry match {
      case Success(_)  => ()
      case Failure(ex) => throw ex
    }
  }
}

object ProjectExportServiceLive {
  val layer: URLayer[
    OntologyRepo with ProjectADMService with StringFormatter with TriplestoreService,
    ProjectExportService
  ] = ZLayer.fromFunction(ProjectExportServiceLive.apply _)
}
