/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.OutputStream
import scala.collection.mutable

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.util.rdf.TriG
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.util.ZScopedJavaIoStreams

trait ProjectExportService {

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
      outWriter <- createPrefixDedupStreamRDF(outFile).map { it =>
                     it.start(); it
                   }
      _ <- ZIO.foreachDiscard(inputFiles)(file =>
             // Combine the files and write the output to the given OutputStream
             for {
               is <- ZScopedJavaIoStreams.fileInputStream(file)
               _  <- ZIO.attemptBlocking(RDFParser.source(is).lang(Lang.TRIG).parse(outWriter))
             } yield (),
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
    private val prefixes                                   = mutable.Set[String]()
    override def prefix(prefix: String, iri: String): Unit =
      if (!prefixes.contains(prefix)) {
        writer.prefix(prefix, iri)
        val _ = prefixes.add(prefix)
      }
    override def triple(triple: Triple): Unit = writer.triple(triple)
    override def quad(quad: Quad): Unit       = writer.quad(quad)
    override def base(base: String): Unit     = writer.base(base)
    override def finish(): Unit               = writer.finish()
    override def start(): Unit                = writer.start()
  }
}

final class ProjectExportServiceLive(
  projectService: KnoraProjectService,
  triplestore: TriplestoreService,
) extends ProjectExportService {

  private def trigExportFilePath(project: KnoraProject, tempDir: Path) = tempDir / s"${project.shortcode}.trig"

  override def exportProjectTriples(project: KnoraProject): Task[Path] =
    Files
      .createTempDirectory(Some(project.shortname.value), fileAttributes = Nil)
      .map(trigExportFilePath(project, _))
      .flatMap(exportProjectTriples(project, _))

  private def exportProjectTriples(project: KnoraProject, targetFile: Path): Task[Path] = ZIO.scoped {
    for {
      tempDir         <- Files.createTempDirectoryScoped(Some(project.shortname.value), fileAttributes = Nil)
      ontologyAndData <- downloadOntologyAndData(project, tempDir)
      adminData       <- downloadProjectAdminData(project.id, tempDir)
      permissionData  <- downloadPermissionData(project, tempDir)
      resultFile      <- mergeDataToFile(ontologyAndData :+ adminData :+ permissionData, targetFile)
    } yield resultFile
  }

  private def downloadOntologyAndData(project: KnoraProject, tempDir: Path): Task[List[NamedGraphTrigFile]] = for {
    allGraphsTrigFile <-
      projectService.getNamedGraphsForProject(project).map(_.map(NamedGraphTrigFile(_, tempDir)))
    files <-
      ZIO.foreach(allGraphsTrigFile)(file =>
        Files.deleteIfExists(file.dataFile) *> Files.createFile(file.dataFile) *>
          triplestore.downloadGraph(file.graphIri, file.dataFile, TriG).as(file),
      )
  } yield files

  /**
   * Downloads the admin related project metadata.
   * The data is saved to a file in TriG format.
   * The data contains:
   * * the project itself
   * * the users which are members of the project
   * * the groups which belong to the project
   * @param projectId The IRI of the project to be exported.
   * @param targetDir The folder in which the file is to be saved.
   * @return A [[NamedGraphTrigFile]] containing the named graph and location of the file.
   */
  private def downloadProjectAdminData(projectId: ProjectIri, targetDir: Path): Task[NamedGraphTrigFile] = {
    val file = NamedGraphTrigFile(adminDataNamedGraph, targetDir)
    triplestore
      .queryToFile(ProjectExportQueries.adminData(projectId), adminDataNamedGraph, file.dataFile, TriG)
      .as(file)
  }

  private def downloadPermissionData(project: KnoraProject, tempDir: Path) = {
    val graphIri = permissionsDataNamedGraph
    val file     = NamedGraphTrigFile(graphIri, tempDir)
    triplestore.queryToFile(ProjectExportQueries.permissionData(project.id), graphIri, file.dataFile, TriG).as(file)
  }

  private def mergeDataToFile(allData: Seq[NamedGraphTrigFile], targetFile: Path): Task[Path] =
    TriGCombiner.combineTrigFiles(allData.map(_.dataFile), targetFile)
}

object ProjectExportServiceLive {
  val layer = ZLayer.derive[ProjectExportServiceLive]
}

/** The CONSTRUCT queries backing the admin and permission parts of a project export. */
private[service] object ProjectExportQueries {

  /**
   * The admin metadata of a project: the project itself, the users which are members of it,
   * and the groups which belong to it.
   */
  def adminData(projectId: ProjectIri): Construct = {
    val projectIri = Iri.unsafeFrom(projectId.value)
    Construct(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |
               |CONSTRUCT {
               |  $projectIri ?projectPred ?projectObj .
               |  ?user ?userPred ?userObj .
               |  ?group ?groupPred ?groupObj .
               |}
               |WHERE {
               |  ${Fragments.union(
          sparql"$projectIri ?projectPred ?projectObj .",
          sparql"""|?user ?userPred ?userObj ;
                             |  knora-admin:isInProject $projectIri .""",
          sparql"""|?group ?groupPred ?groupObj ;
                             |  knora-admin:belongsToProject $projectIri .""",
        )}
               |}""".render,
    )
  }

  /** All permissions which are attached to the given project. */
  def permissionData(projectId: ProjectIri): Construct = {
    val projectIri = Iri.unsafeFrom(projectId.value)
    Construct(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |
               |CONSTRUCT {
               |  ?s ?p ?o .
               |}
               |WHERE {
               |  ?s knora-admin:forProject $projectIri ;
               |    ?p ?o .
               |}""".render,
    )
  }
}
