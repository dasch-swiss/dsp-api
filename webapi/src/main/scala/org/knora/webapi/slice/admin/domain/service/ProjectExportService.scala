package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.graph.Triple
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFWriter
import org.apache.jena.sparql.core.Quad
import zio.Scope
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.mutable

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl
import org.knora.webapi.messages.util.rdf.TriG
import org.knora.webapi.slice.admin.AdminConstants.adminDataGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ZScopedJavaIoStreams

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

private object TriGCombiner {

  def combineTriGFiles(files: Seq[Path], outputFile: Path): Task[Path] = ZIO.scoped {
    for {
      outFile   <- ZScopedJavaIoStreams.fileBufferedOutputStream(outputFile)
      outWriter <- createPrefixDedupStreamRDF(outFile).map { it => it.start(); it }
      _ <- ZIO.foreachDiscard(files)(file =>
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
    ZIO.acquireRelease(acquire)(release).map(dedupStream)
  }

  // Define a custom StreamRDF implementation to filter out duplicate @prefix directives
  private def dedupStream(writer: StreamRDF): StreamRDF = new StreamRDFBase {
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
    val files      = allData.map(_.dataFile)
    val targetFile = tempDir.resolve(project.shortname + ".trig")
    TriGCombiner.combineTriGFiles(files, targetFile).as(tempDir.resolve(project.shortname + ".trig"))
  }
}

object ProjectExportServiceLive {
  val layer: URLayer[
    OntologyRepo with ProjectADMService with StringFormatter with TriplestoreService,
    ProjectExportService
  ] = ZLayer.fromFunction(ProjectExportServiceLive.apply _)
}
