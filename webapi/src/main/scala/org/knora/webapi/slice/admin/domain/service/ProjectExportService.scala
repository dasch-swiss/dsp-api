/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZIO
import zio.macros.accessible

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.FileWrittenResponse
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphFileRequest
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
   * @param file the path to the file to which the project should be exported
   * @return the [[Path]] to the file to which the project was exported
   */
  def exportProjectTriples(project: KnoraProject, file: Path): Task[Path]
}

/**
 * Represents a named graph to be saved to a TriG file.
 *
 * @param graphIri the IRI of the named graph.
 * @param tempDir  the directory in which the file is to be saved.
 */
private case class NamedGraphTrigFile(graphIri: IRI, tempDir: Path) {
  lazy val dataFile: Path = {
    val filename = graphIri.replaceAll("[.:/]", "_") + ".trig"
    tempDir.resolve(filename)
  }
}

/**
 * A [[RdfStreamProcessor]] for combining several named graphs into one.
 *
 * @param formattingStreamProcessor a [[RdfStreamProcessor]] for writing the combined result.
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
  ontologyRepo: OntologyRepo,
  projectRepo: ProjectADMService,
  stringFormatter: StringFormatter,
  triplestoreService: TriplestoreService,
  messageRelay: MessageRelay
) extends ProjectExportService {

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

  override def exportProjectTriples(project: KnoraProject, file: Path): Task[Path] = {
    val tempDir = Files.createTempDirectory(project.shortname)
    for {
      _                 <- ZIO.logInfo("Downloading project data to temporary directory " + tempDir.toAbsolutePath)
      projectOntologies <- ontologyRepo.findByProject(project).map(_.map(_.ontologyMetadata.ontologyIri.toIri))

      // Download the project's named graphs.

      projectDataNamedGraph: IRI = ProjectADMService.projectDataNamedGraphV2(project).value
      graphsToDownload: Seq[IRI] = projectOntologies :+ projectDataNamedGraph
      projectSpecificNamedGraphTrigFiles: Seq[NamedGraphTrigFile] =
        graphsToDownload.map(graphIri => NamedGraphTrigFile(graphIri = graphIri, tempDir = tempDir))

      _ <- ZIO.foreachDiscard(projectSpecificNamedGraphTrigFiles) { trigFile =>
             for {
               fileWrittenResponse <- messageRelay.ask[FileWrittenResponse](
                                        NamedGraphFileRequest(
                                          graphIri = trigFile.graphIri,
                                          outputFile = trigFile.dataFile,
                                          outputFormat = TriG
                                        )
                                      )
             } yield fileWrittenResponse
           }

      // Download the project's admin data.

      adminDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = adminDataGraph, tempDir = tempDir)

      projectIri      = project.id.value
      adminDataSparql = twirl.queries.sparql.admin.txt.getProjectAdminData(projectIri)
      _ <- triplestoreService.sparqlHttpConstructFile(
             sparql = adminDataSparql.toString(),
             graphIri = adminDataNamedGraphTrigFile.graphIri,
             outputFile = adminDataNamedGraphTrigFile.dataFile,
             outputFormat = TriG
           )

      // Download the project's permission data.

      permissionDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = permissionsDataGraph, tempDir = tempDir)

      permissionDataSparql = twirl.queries.sparql.admin.txt.getProjectPermissions(projectIri)
      _ <- triplestoreService.sparqlHttpConstructFile(
             sparql = permissionDataSparql.toString(),
             graphIri = permissionDataNamedGraphTrigFile.graphIri,
             outputFile = permissionDataNamedGraphTrigFile.dataFile,
             outputFormat = TriG
           )

      // Stream the combined results into the output file.

      namedGraphTrigFiles: Seq[NamedGraphTrigFile] =
        projectSpecificNamedGraphTrigFiles :+ adminDataNamedGraphTrigFile :+ permissionDataNamedGraphTrigFile
      resultFile: Path = tempDir.resolve(project.shortname + ".trig")
      _               <- combineGraphs(namedGraphTrigFiles = namedGraphTrigFiles, resultFile = resultFile)
    } yield resultFile
  }
}
object ProjectExportServiceLive {

  val layer = zio.ZLayer.fromFunction(ProjectExportServiceLive.apply _)
}
