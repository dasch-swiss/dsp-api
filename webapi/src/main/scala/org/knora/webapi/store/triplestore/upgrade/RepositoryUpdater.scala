package org.knora.webapi.store.triplestore.upgrade

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger
import org.knora.webapi.IRI
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.PluginForKnoraBaseVersion
import org.knora.webapi.util.FileUtil

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.io.Directory
import org.knora.webapi.config.AppConfig

import zio._
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.slf4j.LoggerFactory
import zio.macros.accessible

@accessible
trait RepositoryUpdater {

  /**
   * Updates the repository, if necessary, to work with the current version of dsp-api.
   *
   * @return a response indicating what was done.
   */
  def maybeUpdateRepository: UIO[RepositoryUpdatedResponse]
}

/**
 * Updates a DSP repository to work with the current version of DSP-API.
 *
 * Depends on [[TriplestoreService]] and [[AppConfig]].
 */
object RepositoryUpdater {
  val layer: ZLayer[TriplestoreService & AppConfig, Nothing, RepositoryUpdater] =
    ZLayer {
      for {
        ts     <- ZIO.service[TriplestoreService]
        config <- ZIO.service[AppConfig]
      } yield new RepositoryUpdater {
        
        private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

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

        private val tempDirNamePrefix: String = "knora"

        /**
         * Updates the repository, if necessary, to work with the current version of dsp-api.
         *
         * @return a response indicating what was done.
         */
        override def maybeUpdateRepository: UIO[RepositoryUpdatedResponse] =
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
                    ZIO.logInfo(
                      s"Repository not up-to-date. Found: ${foundRepositoryVersion.getOrElse("None")}, Required: $requiredRepositoryVersion"
                    )
                  _               <- deleteTempDirectories()
                  selectedPlugins <- selectPluginsForNeededUpdates(foundRepositoryVersion)
                  _ <-
                    ZIO.logInfo(
                      s"Updating repository with transformations: ${selectedPlugins.map(_.versionString).mkString(", ")}"
                    )

                  // Update it with those plugins.
                  result <- updateRepositoryWithSelectedPlugins(selectedPlugins)
                } yield result
              }
          } yield repositoryUpdatedResponse

        /**
         * Deletes directories inside temp directory starting with `tempDirNamePrefix`.
         */
        private def deleteTempDirectories(): UIO[Unit] = ZIO.attempt {
          val rootDir         = new File("/tmp/")
          val getTempToDelete = rootDir.listFiles.filter(_.getName.startsWith(tempDirNamePrefix))

          if (getTempToDelete.length != 0) {
            getTempToDelete.foreach { dir =>
              val dirToDelete = new Directory(dir)
              dirToDelete.deleteRecursively()
            }
            log.info(s"Deleted temp directories: ${getTempToDelete.map(_.getName()).mkString(", ")}")
          }
          ()
        }.orDie

        /**
         * Determines the `knora-base` version in the repository.
         *
         * @return the `knora-base` version string, if any, in the repository.
         */
        private def getRepositoryVersion(): UIO[Option[String]] =
          for {
            repositoryVersionResponse <- ts.sparqlHttpSelect(knoraBaseVersionQuery)
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
          maybeRepositoryVersionString: Option[String]
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
                  plugin.versionString -> plugin
                }.toMap

                val pluginForRepositoryVersion: PluginForKnoraBaseVersion =
                  versionsToPluginsMap.getOrElse(
                    repositoryVersion,
                    throw InconsistentRepositoryDataException(s"No such repository version $repositoryVersion")
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
          pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion]
        ): UIO[RepositoryUpdatedResponse] =
          (for {
            downloadDir <- ZIO.attempt(Files.createTempDirectory(tempDirNamePrefix))
            _           <- ZIO.logInfo(s"Repository update using download directory $downloadDir")

            // The file to save the repository in.
            downloadedRepositoryFile  <- ZIO.attempt(downloadDir.resolve("downloaded-repository.nq"))
            transformedRepositoryFile <- ZIO.attempt(downloadDir.resolve("transformed-repository.nq"))

            // Ask the store actor to download the repository to the file.
            _ <- ZIO.logInfo("Downloading repository file...")
            _ <- ts.downloadRepository(downloadedRepositoryFile)

            // Run the transformations to produce an output file.
            _ <- doTransformations(
                   downloadedRepositoryFile = downloadedRepositoryFile,
                   transformedRepositoryFile = transformedRepositoryFile,
                   pluginsForNeededUpdates = pluginsForNeededUpdates
                 )

            // Empty the repository.
            _ <- ZIO.logInfo("Emptying the repository...")
            _ <- ts.dropAllTriplestoreContent()

            // Upload the transformed repository.
            _ <- ZIO.logInfo("Uploading transformed repository data...")
            _ <- ts.uploadRepository(transformedRepositoryFile)
          } yield RepositoryUpdatedResponse(
            message = s"Updated repository to ${org.knora.webapi.KnoraBaseVersion}"
          )).orDie

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
          pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion]
        ): UIO[Unit] = ZIO.attempt {
          // Parse the input file.
          log.info("Reading repository file...")
          val model = rdfFormatUtil.fileToRdfModel(file = downloadedRepositoryFile, rdfFormat = NQuads)
          log.info(s"Read ${model.size} statements.")

          // Run the update plugins.
          for (pluginForNeededUpdate <- pluginsForNeededUpdates) {
            log.info(s"Running transformation for ${pluginForNeededUpdate.versionString}...")
            pluginForNeededUpdate.plugin.transform(model)
          }

          // Update the built-in named graphs.
          log.info("Updating built-in named graphs...")
          addBuiltInNamedGraphsToModel(model)

          // Write the output file.
          log.info(s"Writing output file (${model.size} statements)...")
          rdfFormatUtil.rdfModelToFile(
            rdfModel = model,
            file = transformedRepositoryFile,
            rdfFormat = NQuads
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
            val context: IRI = builtInNamedGraph.iri

            // Remove the existing named graph from the model.
            model.remove(
              subj = None,
              pred = None,
              obj = None,
              context = Some(context)
            )

            // Read the current named graph from a file.
            val namedGraphModel: RdfModel = readResourceIntoModel(builtInNamedGraph.filename, Turtle)

            // Copy it into the model, adding the named graph IRI to each statement.
            for (statement: Statement <- namedGraphModel) {
              model.add(
                subj = statement.subj,
                pred = statement.pred,
                obj = statement.obj,
                context = Some(context)
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
          rdfFormatUtil.parseToRdfModel(fileContent, rdfFormat)
        }
      }
    }
}
