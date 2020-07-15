package org.knora.webapi.store.triplestore.upgrade

import java.io._
import java.nio.file.Files

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.eclipse.rdf4j.model.impl.{LinkedHashModel, SimpleValueFactory}
import org.eclipse.rdf4j.model.{Model, Statement}
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, Rio}
import org.knora.webapi.exceptions.InconsistentTriplestoreDataException
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.PluginForKnoraBaseVersion
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.stringformatter.StringFormatter
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettingsImpl}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Updates a Knora repository to work with the current version of Knora.
 *
 * @param system   the Akka [[ActorSystem]].
 * @param appActor a reference to the main application actor.
 * @param settings the Knora application settings.
 */
class RepositoryUpdater(system: ActorSystem,
                        appActor: ActorRef,
                        settings: KnoraSettingsImpl) extends LazyLogging {

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
     * Constructs RDF4J values.
     */
    private val valueFactory: SimpleValueFactory = SimpleValueFactory.getInstance

    /**
     * A map of version strings to plugins.
     */
    private val pluginsForVersionsMap: Map[String, PluginForKnoraBaseVersion] = RepositoryUpdatePlan.pluginsForVersions.map {
        knoraBaseVersion => knoraBaseVersion.versionString -> knoraBaseVersion
    }.toMap

    /**
     * Updates the repository, if necessary, to work with the current version of Knora.
     *
     * @return a response indicating what was done.
     */
    def maybeUpdateRepository: Future[RepositoryUpdatedResponse] = {
        for {
            maybeRepositoryVersionString <- getRepositoryVersion

            // Is the repository up to date?
            repositoryUpdatedResponse: RepositoryUpdatedResponse <- if (maybeRepositoryVersionString.contains(org.knora.webapi.KnoraBaseVersion)) {
                // Yes. Nothing more to do.
                FastFuture.successful(RepositoryUpdatedResponse(s"Repository is up to date at ${org.knora.webapi.KnoraBaseVersion}"))
            } else {
                // No. Construct the list of updates that it needs.
                val pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion] = selectPluginsForNeededUpdates(maybeRepositoryVersionString)
                log.info(s"Updating repository with transformations: ${pluginsForNeededUpdates.map(_.versionString).mkString(", ")}")

                // Update it with those plugins.
                updateRepository(pluginsForNeededUpdates)
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
            repositoryVersionResponse: SparqlSelectResponse <- (appActor ? SparqlSelectRequest(knoraBaseVersionQuery)).mapTo[SparqlSelectResponse]

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
                    throw InconsistentTriplestoreDataException(s"No such repository version $repositoryVersion")
                )

                RepositoryUpdatePlan.pluginsForVersions.filter(_.versionNumber > pluginForRepositoryVersion.versionNumber)

            case None =>
                // The repository has no version string. Include all updates.
                RepositoryUpdatePlan.pluginsForVersions
        }
    }

    /**
     * Updates the repository with the specified list of plugins.
     *
     * @param pluginsForNeededUpdates the plugins needed to update the repository.
     * @return a [[RepositoryUpdatedResponse]] indicating what was done.
     */
    private def updateRepository(pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion]): Future[RepositoryUpdatedResponse] = {
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
            _: FileWrittenResponse <- (appActor ? DownloadRepositoryRequest(downloadedRepositoryFile)).mapTo[FileWrittenResponse]

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
        val model = readFileIntoModel(downloadedRepositoryFile, RDFFormat.TRIG)
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
        val fileWriter = new FileWriter(transformedRepositoryFile)
        val bufferedWriter = new BufferedWriter(fileWriter)
        Rio.write(model, fileWriter, RDFFormat.TRIG)
        bufferedWriter.close()
        fileWriter.close()
    }

    /**
     * Adds Knora's built-in named graphs to a [[Model]].
     *
     * @param model the [[Model]].
     */
    private def addBuiltInNamedGraphsToModel(model: Model): Unit = {
        for (builtInNamedGraph <- RepositoryUpdatePlan.builtInNamedGraphs) {
            val context = valueFactory.createIRI(builtInNamedGraph.iri)
            model.remove(null, null, null, context)

            val namedGraphModel: Model = readResourceIntoModel(builtInNamedGraph.filename, RDFFormat.TURTLE)

            // Set the context on each statement.
            for (statement: Statement <- namedGraphModel.asScala.toSet) {
                namedGraphModel.remove(
                    statement.getSubject,
                    statement.getPredicate,
                    statement.getObject,
                    statement.getContext
                )

                namedGraphModel.add(
                    statement.getSubject,
                    statement.getPredicate,
                    statement.getObject,
                    context
                )
            }

            model.addAll(namedGraphModel)
        }
    }

    /**
     * Reads an RDF file into a [[Model]].
     *
     * @param file   the file.
     * @param format the file format.
     * @return a [[Model]] representing the contents of the file.
     */
    def readFileIntoModel(file: File, format: RDFFormat): Model = {
        val fileReader = new FileReader(file)
        val bufferedReader = new BufferedReader(fileReader)
        val model = new LinkedHashModel()
        val trigParser: RDFParser = Rio.createParser(format)
        trigParser.setRDFHandler(new StatementCollector(model))
        trigParser.parse(bufferedReader, "")
        fileReader.close()
        bufferedReader.close()
        model
    }

    /**
     * Reads a file from the CLASSPATH into a [[Model]].
     *
     * @param filename the filename.
     * @param format   the file format.
     * @return a [[Model]] representing the contents of the file.
     */
    def readResourceIntoModel(filename: String, format: RDFFormat): Model = {
        val fileContent: String = FileUtil.readTextResource(filename)
        val stringReader = new StringReader(fileContent)
        val model = new LinkedHashModel()
        val trigParser: RDFParser = Rio.createParser(format)
        trigParser.setRDFHandler(new StatementCollector(model))
        trigParser.parse(stringReader, "")
        model
    }
}

