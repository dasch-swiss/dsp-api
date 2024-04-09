/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import zio._
import zio.json._

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import scala.reflect.io.Directory

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.PluginForKnoraBaseVersion
import org.knora.webapi.util.FileUtil

final case class RepositoryUpdater(triplestoreService: TriplestoreService) {
  // A SPARQL query to find out the knora-base version in a repository.
  private val knoraBaseVersionQuery =
    """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |
      |SELECT ?knoraBaseVersion WHERE {
      |    <http://www.knora.org/ontology/knora-base> knora-base:ontologyVersion ?knoraBaseVersion .
      |}""".stripMargin

  /**
   * Provides logging.
   */
  private val log: Logger = Logger(LoggerFactory.getLogger(getClass.getName))

  private val tmpDirNamePrefix: String = "knora"

  private case class RepoUpdateMetric(
    triples: Int,
    graphs: Int,
    durationSeconds: Double,
  )
  private object RepoUpdateMetric {
    implicit val codec: JsonCodec[RepoUpdateMetric] = DeriveJsonCodec.gen[RepoUpdateMetric]
  }

  private case class RepoUpdateMetrics(metrics: List[RepoUpdateMetric])
  private object RepoUpdateMetrics {
    implicit val codec: JsonCodec[RepoUpdateMetrics] = DeriveJsonCodec.gen[RepoUpdateMetrics]

    def make: RepoUpdateMetrics = RepoUpdateMetrics(List())
  }

  def getMigrationMetrics: Task[Unit] =
    for {
      durationState <- Ref.make(RepoUpdateMetrics.make)
      _             <- ZIO.logInfo("Starting dummy migration process...")
      graphs        <- getDataGraphs.debug("Data graphs")
      _             <- Clock.nanoTime
      _ <- ZIO.foreachDiscard(graphs) { graph =>
             for {
               _      <- ZIO.logInfo(s"Removing graph for next dummy migration: $graph")
               _      <- triplestoreService.dropGraph(graph)
               _      <- deleteTmpDirectories()
               metric <- doDummieMigration()
               _      <- durationState.update(metrics => RepoUpdateMetrics(metrics.metrics :+ metric))
             } yield ()
           }
      metrics    <- durationState.get
      _          <- ZIO.logInfo(s"Migration metrics: ${metrics}")
      metricsJson = metrics.toJsonPretty
      _ <- ZIO.logInfo(
             s"""|Migration metrics JSON:
                 |
                 |${metricsJson}
                 |
                 |""".stripMargin,
           )
    } yield ()

  private def getDataGraphs: Task[Seq[String]] =
    for {
      response <-
        triplestoreService.query(
          Select("SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s ?p ?o } }", isGravsearch = false),
        )
      bindings <- ZIO.succeed(response.results.bindings)
      graphs   <- ZIO.succeed(bindings.map(_.rowMap("g")))
      dataGraphs = graphs.filter { graph =>
                     val pattern = """http://www\.knora\.org/data/(.*)/.*""".r
                     graph match {
                       case pattern(shortcode) =>
                         shortcode != "0000"
                       case _ => false
                     }
                   }
    } yield dataGraphs

  private def doDummieMigration(): Task[RepoUpdateMetric] =
    for {
      triples <- getTripleCount
      graphs  <- getGraphCount
      _ <- ZIO.logInfo(s"""|Migration metrics:
                           |Triples: $triples
                           |Graphs: $graphs
                           |""".stripMargin)
      start   <- Clock.currentTime(TimeUnit.MILLISECONDS)
      dir     <- ZIO.attempt(Files.createTempDirectory(tmpDirNamePrefix))
      file    <- createEmptyFile("downloaded-repository.nq", dir)
      _       <- triplestoreService.downloadRepository(file, MigrateAllGraphs)
      _       <- triplestoreService.dropDataGraphByGraph()
      _       <- triplestoreService.uploadRepository(file)
      end     <- Clock.currentTime(TimeUnit.MILLISECONDS)
      duration = (end - start) / 1000.0
    } yield RepoUpdateMetric(triples, graphs, duration)

  private def createEmptyFile(filename: String, dir: Path) = ZIO.attempt {
    val file = dir.resolve(filename)
    Files.deleteIfExists(file)
    Files.createFile(file)
  }
  private def getTripleCount: Task[Int] =
    for {
      response <-
        triplestoreService.query(Select("SELECT (COUNT(*) AS ?count) WHERE { ?s ?p ?o }", isGravsearch = false))
      bindings <- ZIO.succeed(response.results.bindings)
      count    <- ZIO.succeed(bindings.head.rowMap("count").toInt)
    } yield count

  private def getGraphCount: Task[Int] =
    for {
      response <-
        triplestoreService.query(
          Select("SELECT (COUNT(DISTINCT ?g) AS ?count) WHERE { GRAPH ?g { ?s ?p ?o } }", isGravsearch = false),
        )
      bindings <- ZIO.succeed(response.results.bindings)
      count    <- ZIO.succeed(bindings.head.rowMap("count").toInt)
    } yield count

  /**
   * Updates the repository, if necessary, to work with the current version of DSP-API.
   *
   * @return a response indicating what was done.
   */
  val maybeUpgradeRepository: Task[RepositoryUpdatedResponse] =
    for {
      foundRepositoryVersion    <- getRepositoryVersion()
      requiredRepositoryVersion <- ZIO.succeed(org.knora.webapi.KnoraBaseVersion)

      // Is the repository up to date?
      repositoryUpToDate <- ZIO.succeed(foundRepositoryVersion.contains(requiredRepositoryVersion))

      repositoryUpdatedResponse <-
        if (repositoryUpToDate) {
          // Yes. Nothing more to do.
          ZIO.succeed(RepositoryUpdatedResponse(s"Repository is up to date at $requiredRepositoryVersion"))
        } else {
          for {
            // No. Construct the list of updates that it needs.
            _ <-
              foundRepositoryVersion match {
                case Some(foundRepositoryVersion) =>
                  ZIO.logInfo(
                    s"Repository not up to date. Found: $foundRepositoryVersion, Required: $requiredRepositoryVersion",
                  )
                case None =>
                  ZIO.logWarning(
                    s"Repository not up to date. Found: None, Required: $requiredRepositoryVersion",
                  )
              }
            _               <- deleteTmpDirectories()
            selectedPlugins <- selectPluginsForNeededUpdates(foundRepositoryVersion)
            _ <-
              ZIO.logInfo(
                s"Updating repository with transformations: ${selectedPlugins.map(_.versionNumber).mkString(", ")}",
              )

            // Update it with those plugins.
            result <- updateRepositoryWithSelectedPlugins(selectedPlugins)
          } yield result
        }
    } yield repositoryUpdatedResponse

  /**
   * Deletes directories inside tmp directory starting with `tmpDirNamePrefix`.
   */
  private def deleteTmpDirectories(): UIO[Unit] = ZIO.attempt {
    val rootDir        = new File("/tmp/")
    val getTmpToDelete = rootDir.listFiles.filter(_.getName.startsWith(tmpDirNamePrefix))

    if (getTmpToDelete.length != 0) {
      getTmpToDelete.foreach { dir =>
        val dirToDelete = new Directory(dir)
        dirToDelete.deleteRecursively()
      }
      log.info(s"Deleted tmp directories: ${getTmpToDelete.map(_.getName()).mkString(", ")}")
    }
    ()
  }.orDie

  /**
   * Determines the `knora-base` version in the repository.
   *
   * @return the `knora-base` version string, if any, in the repository.
   */
  private def getRepositoryVersion(): Task[Option[String]] =
    for {
      repositoryVersionResponse <- triplestoreService.query(Select(knoraBaseVersionQuery, isGravsearch = false))
      bindings                  <- ZIO.succeed(repositoryVersionResponse.results.bindings)
      versionString <-
        if (bindings.nonEmpty) {
          ZIO.succeed(Some(bindings.head.rowMap("knoraBaseVersion")))
        } else {
          ZIO.none
        }
    } yield versionString

  /**
   * Constructs a list of update plugins that need to be run to update the repository.
   *
   * @param maybeRepositoryVersionString the `knora-base` version string, if any, in the repository.
   * @return the plugins needed to update the repository.
   */
  private def selectPluginsForNeededUpdates(
    maybeRepositoryVersionString: Option[String],
  ): UIO[Seq[PluginForKnoraBaseVersion]] = {

    // A list of available plugins.
    val plugins: Seq[PluginForKnoraBaseVersion] =
      RepositoryUpdatePlan.makePluginsForVersions(log)

    ZIO.attempt {
      maybeRepositoryVersionString match {
        case Some(repositoryVersion) =>
          // The repository has a version string. Get the plugins for all subsequent versions.

          // Make a map of version strings to plugins.
          val versionsToPluginsMap: Map[String, PluginForKnoraBaseVersion] = plugins.map { plugin =>
            s"knora-base v${plugin.versionNumber}" -> plugin
          }.toMap

          val pluginForRepositoryVersion: PluginForKnoraBaseVersion =
            versionsToPluginsMap.getOrElse(
              repositoryVersion,
              throw InconsistentRepositoryDataException(s"No such repository version $repositoryVersion"),
            )

          plugins.filter { plugin =>
            plugin.versionNumber > pluginForRepositoryVersion.versionNumber
          }

        case None =>
          // The repository has no version string. Include all updates.
          plugins
      }
    }.orDie
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
    } yield RepositoryUpdatedResponse(
      message = s"Updated repository to ${org.knora.webapi.KnoraBaseVersion}",
    )).orDie
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
  ): UIO[Unit] = ZIO.attempt {
    // Parse the input file.
    log.info("Reading repository file...")
    val model = RdfFormatUtil.fileToRdfModel(file = downloadedRepositoryFile, rdfFormat = NQuads)
    log.info(s"Read ${model.size} statements.")

    // Run the update plugins.
    for (pluginForNeededUpdate <- pluginsForNeededUpdates) {
      log.info(s"Running transformation for ${pluginForNeededUpdate.versionNumber}...")
      pluginForNeededUpdate.plugin.transform(model)
    }

    // Update the built-in named graphs.
    log.info("Updating built-in named graphs...")
    addBuiltInNamedGraphsToModel(model)

    // Write the output file.
    log.info(s"Writing output file (${model.size} statements)...")
    RdfFormatUtil.rdfModelToFile(
      rdfModel = model,
      file = transformedRepositoryFile,
      rdfFormat = NQuads,
    )
  }.orDie

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
    RdfFormatUtil.parseToRdfModel(fileContent, rdfFormat)
  }
}

object RepositoryUpdater {
  val layer = ZLayer.derive[RepositoryUpdater]
}
