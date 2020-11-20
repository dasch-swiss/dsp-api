package org.knora.webapi.store.triplestore.upgrade

import java.io._
import java.nio.file.Files

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi.exceptions.InconsistentRepositoryDataException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.PluginForKnoraBaseVersion
import org.knora.webapi.util.FileUtil
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettingsImpl}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Updates a Knora repository to work with the current version of Knora.
 *
 * @param system               the Akka [[ActorSystem]].
 * @param appActor             a reference to the main application actor.
 * @param featureFactoryConfig the feature factory configuration.
 * @param settings             the Knora application settings.
 */
class RepositoryUpdater(system: ActorSystem,
                        appActor: ActorRef,
                        featureFactoryConfig: FeatureFactoryConfig,
                        settings: KnoraSettingsImpl) extends LazyLogging {
    // RDF factories.
    private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
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
    private implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

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
     * A map of version strings to plugins.
     */
    private val pluginsForVersionsMap: Map[String, PluginForKnoraBaseVersion] = RepositoryUpdatePlan.makePluginsForVersions(
        featureFactoryConfig = featureFactoryConfig,
        log = log
    ).map {
        knoraBaseVersion => knoraBaseVersion.versionString -> knoraBaseVersion
    }.toMap

    /**
     * Updates the repository, if necessary, to work with the current version of Knora.
     *
     * @return a response indicating what was done.
     */
    def maybeUpdateRepository: Future[RepositoryUpdatedResponse] = {
        for {
            foundRepositoryVersion: Option[String] <- getRepositoryVersion
            requiredRepositoryVersion = org.knora.webapi.KnoraBaseVersion

            // Is the repository up to date?
            repositoryUpToData = foundRepositoryVersion.contains(requiredRepositoryVersion)
            repositoryUpdatedResponse: RepositoryUpdatedResponse <- if (repositoryUpToData) {
                // Yes. Nothing more to do.
                FastFuture.successful(RepositoryUpdatedResponse(s"Repository is up to date at $requiredRepositoryVersion"))
            } else {
                // No. Construct the list of updates that it needs.
                log.info(s"Repository not up-to-date. Found: $foundRepositoryVersion, Required: $requiredRepositoryVersion")
                val selectedPlugins: Seq[PluginForKnoraBaseVersion] = selectPluginsForNeededUpdates(foundRepositoryVersion)
                log.info(s"Updating repository with transformations: ${selectedPlugins.map(_.versionString).mkString(", ")}")

                // Update it with those plugins.
                updateRepositoryWithSelectedPlugins(selectedPlugins)
            }
        } yield repositoryUpdatedResponse
    }

    /**
     * Determines the `knora-base` version in the repository.
     *
     * @return the `knora-base` version string, if any, in the repository.
     */
    private def getRepositoryVersion: Future[Option[String]] = {
        for {
            repositoryVersionResponse: SparqlSelectResult <- (appActor ? SparqlSelectRequest(knoraBaseVersionQuery)).mapTo[SparqlSelectResult]

            bindings = repositoryVersionResponse.results.bindings

            versionString = if (bindings.nonEmpty) {
                Some(bindings.head.rowMap("knoraBaseVersion"))
            } else {
                None
            }
        } yield versionString
    }

    /**
     * Constructs a list of update plugins that need to be run to update the repository.
     *
     * @param maybeRepositoryVersionString the `knora-base` version string, if any, in the repository.
     * @return the plugins needed to update the repository.
     */
    private def selectPluginsForNeededUpdates(maybeRepositoryVersionString: Option[String]): Seq[PluginForKnoraBaseVersion] = {
        maybeRepositoryVersionString match {
            case Some(repositoryVersion) =>
                // The repository has a version string. Get the plugins for all subsequent versions.
                val pluginForRepositoryVersion: PluginForKnoraBaseVersion = pluginsForVersionsMap.getOrElse(
                    repositoryVersion,
                    throw InconsistentRepositoryDataException(s"No such repository version $repositoryVersion")
                )

                pluginsForVersionsMap.values.filter(_.versionNumber > pluginForRepositoryVersion.versionNumber).toSeq

            case None =>
                // The repository has no version string. Include all updates.
                pluginsForVersionsMap.values.toSeq
        }
    }

    /**
     * Updates the repository with the specified list of plugins.
     *
     * @param pluginsForNeededUpdates the plugins needed to update the repository.
     * @return a [[RepositoryUpdatedResponse]] indicating what was done.
     */
    private def updateRepositoryWithSelectedPlugins(pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion]): Future[RepositoryUpdatedResponse] = {
        // Was a download directory specified in the application settings?
        val downloadDir: File = settings.upgradeDownloadDir match {
            case Some(configuredDir) =>
                // Yes. Use that directory.
                log.info(s"Repository update using configured download directory $configuredDir")
                val dirFile = new File(configuredDir)
                dirFile.mkdirs()
                dirFile

            case None =>
                // No. Create a temporary directory.
                val dirFile = Files.createTempDirectory("knora").toFile
                log.info(s"Repository update using download directory $dirFile")
                dirFile
        }

        // The file to save the repository in.
        val downloadedRepositoryFile = new File(downloadDir, "downloaded-repository.trig")
        val transformedRepositoryFile = new File(downloadDir, "transformed-repository.trig")
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
            _: RepositoryUploadedResponse <- (appActor ? UploadRepositoryRequest(transformedRepositoryFile)).mapTo[RepositoryUploadedResponse]
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
    private def doTransformations(downloadedRepositoryFile: File,
                                  transformedRepositoryFile: File,
                                  pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion]): Unit = {
        // Parse the input file.
        log.info("Reading repository file...")
        val model = readFileIntoModel(file = downloadedRepositoryFile, rdfFormat = TriG)
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
        writeModelToFile(
            rdfModel = model,
            file = transformedRepositoryFile,
            rdfFormat = TriG
        )
    }

    /**
     * Adds Knora's built-in named graphs to an [[RdfModel]].
     *
     * @param model the [[RdfModel]].
     */
    private def addBuiltInNamedGraphsToModel(model: RdfModel): Unit = {
        for (builtInNamedGraph <- RepositoryUpdatePlan.builtInNamedGraphs) {
            val context = builtInNamedGraph.iri
            model.remove(None, None, None, Some(context))

            val namedGraphModel: RdfModel = readResourceIntoModel(builtInNamedGraph.filename, Turtle)

            // Set the context on each statement.
            for (statement: Statement <- namedGraphModel) {
                namedGraphModel.removeStatement(statement)

                namedGraphModel.add(
                    statement.subj,
                    statement.pred,
                    statement.obj,
                    Some(context)
                )
            }

            model.addStatementsFromModel(namedGraphModel)
        }
    }

    /**
     * Reads an RDF file into an [[RdfModel]].
     *
     * @param file   the file.
     * @param rdfFormat the file format.
     * @return a [[RdfModel]] representing the contents of the file.
     */
    def readFileIntoModel(file: File, rdfFormat: NonJsonLD): RdfModel = {
        val fileInputStream = new BufferedInputStream(new FileInputStream(file))
        val rdfModel: RdfModel = rdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = rdfFormat)
        fileInputStream.close()
        rdfModel
    }

    /**
     * Reads a file from the CLASSPATH into an [[RdfModel]].
     *
     * @param filename the filename.
     * @param rdfFormat   the file format.
     * @return an [[RdfModel]] representing the contents of the file.
     */
    def readResourceIntoModel(filename: String, rdfFormat: NonJsonLD): RdfModel = {
        val fileContent: String = FileUtil.readTextResource(filename)
        rdfFormatUtil.parseToRdfModel(fileContent, rdfFormat)
    }

    /**
     * Writes an [[RdfModel]] to a file.
     *
     * @param rdfModel the model to be written.
     * @param file the file.
     * @param rdfFormat the file format.
     */
    def writeModelToFile(rdfModel: RdfModel, file: File, rdfFormat: NonJsonLD): Unit = {
        val fileOutputStream = new BufferedOutputStream(new FileOutputStream(file))

        rdfFormatUtil.rdfModelToOutputStream(
            rdfModel = rdfModel,
            outputStream = fileOutputStream,
            rdfFormat = rdfFormat
        )

        fileOutputStream.close()
    }
}
