package org.knora.webapi.store.triplestore.upgrade

import java.nio.file.{Files, Path, Paths}
import java.io.File
import scala.reflect.io.Directory

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.InconsistentRepositoryDataException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettingsImpl}
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.PluginForKnoraBaseVersion
import org.knora.webapi.util.FileUtil

import scala.concurrent.{ExecutionContext, Future}

/**
 * Updates a Knora repository to work with the current version of Knora.
 *
 * @param system               the Akka [[ActorSystem]].
 * @param appActor             a reference to the main application actor.
 * @param featureFactoryConfig the feature factory configuration.
 * @param settings             the Knora application settings.
 */
class RepositoryUpdater(
  system: ActorSystem,
  appActor: ActorRef,
  featureFactoryConfig: FeatureFactoryConfig,
  settings: KnoraSettingsImpl
) extends LazyLogging {
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)

  // A SPARQL query to find out the knora-base version in a repository.
  private val knoraBaseVersionQuery =
    """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |
      |SELECT ?knoraBaseVersion WHERE {
      |    <http://www.knora.org/ontology/knora-base> knora-base:ontologyVersion ?knoraBaseVersion .
      |}""".stripMargin

  /**
   * The execution context for futures created in Knora actors.
   */
  private implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  /**
   * A string formatter.
   */
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * The application's default timeout for `ask` messages.
   */
  private implicit val timeout: Timeout = settings.defaultTimeout

  /**
   * Provides logging.
   */
  private val log: Logger = logger

  /**
   * A list of available plugins.
   */
  private val plugins: Seq[PluginForKnoraBaseVersion] = RepositoryUpdatePlan.makePluginsForVersions(
    featureFactoryConfig = featureFactoryConfig,
    log = log
  )

  private val tempDirNamePrefix: String = "knora"

  /**
    * Deletes directories inside temp directory starting with `tempDirNamePrefix`.
    */  
  def deleteTempDirectories(): Unit = {
    val rootDir = new File("/tmp/")
    val getTempToDelete = rootDir.listFiles.filter(_.getName.startsWith(tempDirNamePrefix))

    if (getTempToDelete.length != 0) {
      getTempToDelete.foreach(dir => {
        val dirToDelete = new Directory(dir)
        dirToDelete.deleteRecursively()
      })
      log.info(s"Deleted temp directories: ${getTempToDelete.map(_.getName()).mkString(", ")}")
    }
  }

  /**
   * Updates the repository, if necessary, to work with the current version of Knora.
   *
   * @return a response indicating what was done.
   */
  def maybeUpdateRepository: Future[RepositoryUpdatedResponse] =
    for {
      foundRepositoryVersion: Option[String] <- getRepositoryVersion
      requiredRepositoryVersion = org.knora.webapi.KnoraBaseVersion

      // Is the repository up to date?
      repositoryUpToDate: Boolean = foundRepositoryVersion.contains(requiredRepositoryVersion)

      repositoryUpdatedResponse: RepositoryUpdatedResponse <-
        if (repositoryUpToDate) {
          // Yes. Nothing more to do.
          FastFuture.successful(RepositoryUpdatedResponse(s"Repository is up to date at $requiredRepositoryVersion"))
        } else {
          // No. Construct the list of updates that it needs.
          log.info(
            s"Repository not up-to-date. Found: ${foundRepositoryVersion.getOrElse("None")}, Required: $requiredRepositoryVersion"
          )

          deleteTempDirectories()

          val selectedPlugins: Seq[PluginForKnoraBaseVersion] = selectPluginsForNeededUpdates(foundRepositoryVersion)
          log.info(s"Updating repository with transformations: ${selectedPlugins.map(_.versionString).mkString(", ")}")

          // Update it with those plugins.
          updateRepositoryWithSelectedPlugins(selectedPlugins)
        }
    } yield repositoryUpdatedResponse

  /**
   * Determines the `knora-base` version in the repository.
   *
   * @return the `knora-base` version string, if any, in the repository.
   */
  private def getRepositoryVersion: Future[Option[String]] =
    for {
      repositoryVersionResponse: SparqlSelectResult <- (appActor ? SparqlSelectRequest(knoraBaseVersionQuery))
        .mapTo[SparqlSelectResult]

      bindings = repositoryVersionResponse.results.bindings

      versionString =
        if (bindings.nonEmpty) {
          Some(bindings.head.rowMap("knoraBaseVersion"))
        } else {
          None
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
  ): Seq[PluginForKnoraBaseVersion] =
    maybeRepositoryVersionString match {
      case Some(repositoryVersion) =>
        // The repository has a version string. Get the plugins for all subsequent versions.

        // Make a map of version strings to plugins.
        val versionsToPluginsMap: Map[String, PluginForKnoraBaseVersion] = plugins.map { plugin =>
          plugin.versionString -> plugin
        }.toMap

        val pluginForRepositoryVersion: PluginForKnoraBaseVersion = versionsToPluginsMap.getOrElse(
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

  /**
   * Updates the repository with the specified list of plugins.
   *
   * @param pluginsForNeededUpdates the plugins needed to update the repository.
   * @return a [[RepositoryUpdatedResponse]] indicating what was done.
   */
  private def updateRepositoryWithSelectedPlugins(
    pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion]
  ): Future[RepositoryUpdatedResponse] = {
    val downloadDir: Path = Files.createTempDirectory(tempDirNamePrefix)
    log.info(s"Repository update using download directory $downloadDir")

    // The file to save the repository in.
    val downloadedRepositoryFile = downloadDir.resolve("downloaded-repository.nq")
    val transformedRepositoryFile = downloadDir.resolve("transformed-repository.nq")
    log.info("Downloading repository file...")

    for {
      // Ask the store actor to download the repository to the file.
      _: FileWrittenResponse <- (appActor ? DownloadRepositoryRequest(
        outputFile = downloadedRepositoryFile,
        featureFactoryConfig = featureFactoryConfig
      )).mapTo[FileWrittenResponse]

      // Run the transformations to produce an output file.
      _ = doTransformations(
        downloadedRepositoryFile = downloadedRepositoryFile,
        transformedRepositoryFile = transformedRepositoryFile,
        pluginsForNeededUpdates = pluginsForNeededUpdates
      )

      _ = log.info("Emptying the repository...")

      // Empty the repository.
      _: DropAllRepositoryContentACK <- (appActor ? DropAllTRepositoryContent()).mapTo[DropAllRepositoryContentACK]

      _ = log.info("Uploading transformed repository data...")

      // Upload the transformed repository.
      _: RepositoryUploadedResponse <- (appActor ? UploadRepositoryRequest(transformedRepositoryFile))
        .mapTo[RepositoryUploadedResponse]
    } yield RepositoryUpdatedResponse(
      message = s"Updated repository to ${org.knora.webapi.KnoraBaseVersion}"
    )
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
    pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion]
  ): Unit = {
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
  }

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
  def readResourceIntoModel(filename: String, rdfFormat: NonJsonLD): RdfModel = {
    val fileContent: String = FileUtil.readTextResource(filename)
    rdfFormatUtil.parseToRdfModel(fileContent, rdfFormat)
  }
}
