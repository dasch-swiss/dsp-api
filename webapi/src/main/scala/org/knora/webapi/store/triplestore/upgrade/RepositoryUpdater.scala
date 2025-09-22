/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.KnoraBaseVersionString
import org.knora.webapi.knoraBaseVersionFrom
import org.knora.webapi.messages.store.triplestoremessages.RepositoryUpdatedResponse
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.PluginForKnoraBaseVersion
import org.knora.webapi.store.triplestore.upgrade.plugins.MigrateOnlyBuiltInGraphs
import org.knora.webapi.util.FileUtil

final case class RepositoryUpdater(triplestoreService: TriplestoreService) {

  private val tmpDirNamePrefix: String = "knora"

  /**
   * Updates the repository, if necessary, to work with the current version of DSP-API.
   *
   * @return a response indicating what was done.
   */
  val maybeUpgradeRepository: Task[RepositoryUpdatedResponse] =
    getRepositoryVersion.flatMap {
      case Some(version) if version == KnoraBaseVersion =>
        ZIO.succeed(RepositoryUpdatedResponse(s"Repository is up to date at $KnoraBaseVersionString"))
      case Some(version) if version > KnoraBaseVersion =>
        val msg =
          s"Repository version is higher than the current version of dsp-api: ${version} > $KnoraBaseVersion"
        ZIO.die(new InconsistentRepositoryDataException(msg))
      case versionMaybe =>
        for {
          _ <- ZIO.logInfo(versionMaybe map { v =>
                 s"Repository not up to date. Found: $v, Required: $KnoraBaseVersion"
               } getOrElse {
                 s"Repository not up to date. Found: None, Required: $KnoraBaseVersion"
               })
          _            <- deleteTmpDirectories()
          updatePlugins = selectPluginsForNeededUpdates(versionMaybe)
          _ <-
            ZIO.logInfo(
              s"Updating repository with transformations: ${updatePlugins.map(_.versionNumber).mkString(", ")}",
            )
          result <- updateRepositoryWithSelectedPlugins(updatePlugins)
        } yield result
    }

  /**
   * Deletes directories inside tmp directory starting with `tmpDirNamePrefix`.
   */
  private def deleteTmpDirectories(): UIO[Unit] = {
    val rootDir        = new File("/tmp/")
    val getTmpToDelete = rootDir.listFiles.filter(_.getName.startsWith(tmpDirNamePrefix))
    ZIO.foreach(getTmpToDelete) { dir =>
      zio.nio.file.Files.deleteRecursive(zio.nio.file.Path(dir.getPath))
    }
  }.unit.orDie

  /**
   * Retrieves the `knora-base:ontologyVersion` version from the repository.
   *
   * @return The parsed `knora-base:ontologyVersion` as an [[Int]], if any, from the repository.
   *         Dies with an [[InconsistentRepositoryDataException]] if the version in the repository is invalid.
   */
  private def getRepositoryVersion: Task[Option[Int]] =
    triplestoreService
      .query(
        Select(
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |SELECT ?knoraBaseVersion WHERE {
            |    <http://www.knora.org/ontology/knora-base> knora-base:ontologyVersion ?knoraBaseVersion .
            |}""".stripMargin,
        ),
      )
      .map(_.getFirst("knoraBaseVersion"))
      .flatMap {
        ZIO.foreach(_) { kb =>
          ZIO
            .fromOption(knoraBaseVersionFrom(kb))
            .orDieWith(_ => new InconsistentRepositoryDataException(s"Invalid repository version: $kb"))
        }
      }

  /**
   * Constructs a list of update plugins that need to be run to update the repository.
   *
   * @param maybeRepositoryVersion the `knora-base` version, if any, from in the repository.
   * @return the plugins needed to update the repository.
   */
  private def selectPluginsForNeededUpdates(maybeRepositoryVersion: Option[Int]): Seq[PluginForKnoraBaseVersion] = {
    val repositoryVersion = maybeRepositoryVersion.getOrElse(-1)
    val plugins           = RepositoryUpdatePlan.makePluginsForVersions.filter(_.versionNumber > repositoryVersion)
    if (plugins.isEmpty) { Seq(PluginForKnoraBaseVersion(KnoraBaseVersion, new MigrateOnlyBuiltInGraphs)) }
    else { plugins }
  }

  /**
   * Updates the repository with the specified list of plugins.
   *
   * @param pluginsForNeededUpdates the plugins needed to update the repository.
   * @return a [[RepositoryUpdatedResponse]] indicating what was done.
   */
  private def updateRepositoryWithSelectedPlugins(
    pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion],
  ): UIO[RepositoryUpdatedResponse] = {
    def createEmptyFile(filename: String, dir: Path) = ZIO.attempt {
      val file = dir.resolve(filename)
      Files.deleteIfExists(file)
      Files.createFile(file)
    }
    (for {
      // The file to save the repository in.
      dir                             <- ZIO.attempt(Files.createTempDirectory(tmpDirNamePrefix))
      graphsBeforeMigrationFile       <- createEmptyFile("downloaded-repository.nq", dir)
      graphsWithAppliedMigrationsFile <- createEmptyFile("transformed-repository.nq", dir)

      // Ask the store actor to download the repository to the file.
      graphs = pluginsForNeededUpdates
                 .map(_.plugin.graphsForMigration)
                 .reduce(_ merge _)
      _ <- ZIO.logInfo(
             s"Downloading $graphs repository file. $graphsBeforeMigrationFile, $graphsWithAppliedMigrationsFile",
           )
      _ <- triplestoreService.downloadRepository(graphsBeforeMigrationFile, graphs)

      // Run the transformations to produce an output file.
      _ <- doTransformations(
             downloadedRepositoryFile = graphsBeforeMigrationFile,
             transformedRepositoryFile = graphsWithAppliedMigrationsFile,
             pluginsForNeededUpdates = pluginsForNeededUpdates,
           )

      // Drop the graphs that need to be updated.
      _ <- graphs match {
             case MigrateAllGraphs => triplestoreService.dropDataGraphByGraph()
             case MigrateSpecificGraphs(graphIris) =>
               ZIO.foreach(graphIris)(iri => triplestoreService.dropGraph(iri.value))
           }

      // Upload the transformed repository.
      _ <- ZIO.logInfo("Uploading transformed repository data...")
      _ <- triplestoreService.uploadRepository(graphsWithAppliedMigrationsFile)
    } yield RepositoryUpdatedResponse(s"Updated repository to ${org.knora.webapi.KnoraBaseVersionString}")).orDie
  }

  /**
   * Transforms a file containing a downloaded repository.
   *
   * @param downloadedRepositoryFile  the downloaded repository.
   * @param transformedRepositoryFile the transformed file.
   * @param pluginsForNeededUpdates   the plugins needed to update the repository.
   */
  private def doTransformations(
    downloadedRepositoryFile: Path,
    transformedRepositoryFile: Path,
    pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion],
  ): UIO[Unit] = (for {
    // Parse the input file.
    _     <- ZIO.logInfo("Reading repository file...")
    model <- ZIO.attempt(RdfFormatUtil.fileToRdfModel(file = downloadedRepositoryFile, rdfFormat = NQuads))
    _     <- ZIO.logInfo(s"Read ${model.size} statements.")

    // Run the update plugins.
    _ <- ZIO.foreachDiscard(pluginsForNeededUpdates) { pluginForNeededUpdate =>
           ZIO.logInfo(s"Running transformation for ${pluginForNeededUpdate.versionNumber}...") *>
             ZIO.attempt(pluginForNeededUpdate.plugin.transform(model))
         }

    // Update the built-in named graphs.
    _ <- ZIO.logInfo("Updating built-in named graphs...")
    _ <- ZIO.attempt(addBuiltInNamedGraphsToModel(model))

    // Write the output file.
    _ <- ZIO.logInfo(s"Writing output file (${model.size} statements)...")
    _ <- ZIO.attempt(RdfFormatUtil.rdfModelToFile(model, transformedRepositoryFile, NQuads))
  } yield ()).orDie

  /**
   * Adds Knora's built-in named graphs to an [[RdfModel]].
   *
   * @param model the [[RdfModel]].
   */
  private def addBuiltInNamedGraphsToModel(model: RdfModel): Unit =
    // Add each built-in named graph to the model.
    for (builtInNamedGraph <- RepositoryUpdatePlan.builtInNamedGraphs) {
      val context: String = builtInNamedGraph.name

      // Remove the existing named graph from the model.
      model.remove(
        subj = None,
        pred = None,
        obj = None,
        context = Some(context),
      )

      // Read the current named graph from a file.
      val namedGraphModel: RdfModel = readResourceIntoModel(builtInNamedGraph.path, Turtle)

      // Copy it into the model, adding the named graph IRI to each statement.
      for (statement: Statement <- namedGraphModel) {
        model.add(
          subj = statement.subj,
          pred = statement.pred,
          obj = statement.obj,
          context = Some(context),
        )
      }
    }

  /**
   * Reads a file from the CLASSPATH into an [[RdfModel]].
   *
   * @param filename  the filename.
   * @param rdfFormat the file format.
   * @return an [[RdfModel]] representing the contents of the file.
   */
  private def readResourceIntoModel(filename: String, rdfFormat: NonJsonLD): RdfModel = {
    val fileContent: String = FileUtil.readTextResource(filename)
    RdfModel.from(fileContent, rdfFormat)
  }
}

object RepositoryUpdater {
  val layer = ZLayer.derive[RepositoryUpdater]
}
