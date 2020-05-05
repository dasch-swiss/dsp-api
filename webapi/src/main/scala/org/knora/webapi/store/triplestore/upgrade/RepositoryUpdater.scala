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
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.store.triplestore.upgrade.plugins._
import org.knora.webapi.util.{FileUtil, StringFormatter}
import org.knora.webapi.{InconsistentTriplestoreDataException, KnoraDispatchers, SettingsImpl}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Updates a Knora repository to work with the current version of Knora.
 *
 * @param system the Akka [[ActorSystem]].
 * @param storeActorRef a reference to the store actor.
 * @param settings the Knora application settings.
 */
class RepositoryUpdater(system: ActorSystem,
                        storeActorRef: ActorRef, // TODO: is this the best way to get this?
                        settings: SettingsImpl) extends LazyLogging {

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
     * Provides logging. TODO: get this to print this class's name in the log messages.
     */
    private val log: Logger = logger

    /**
     * A list of all plugins in chronological order.
     */
    private val pluginsForVersions: Seq[PluginForKnoraBaseVersion] = Seq(
        PluginForKnoraBaseVersion(versionNumber = 1, plugin = new UpgradePluginPR1307, prBasedVersionString = Some("PR 1307")),
        PluginForKnoraBaseVersion(versionNumber = 2, plugin = new UpgradePluginPR1322, prBasedVersionString = Some("PR 1322")),
        PluginForKnoraBaseVersion(versionNumber = 3, plugin = new UpgradePluginPR1367, prBasedVersionString = Some("PR 1367")),
        PluginForKnoraBaseVersion(versionNumber = 4, plugin = new UpgradePluginPR1372, prBasedVersionString = Some("PR 1372")),
        PluginForKnoraBaseVersion(versionNumber = 5, plugin = new NoopPlugin, prBasedVersionString = Some("PR 1440")),
        PluginForKnoraBaseVersion(versionNumber = 6, plugin = new NoopPlugin), // PR 1206
        PluginForKnoraBaseVersion(versionNumber = 7, plugin = new NoopPlugin), // PR 1403
        PluginForKnoraBaseVersion(versionNumber = 8, plugin = new NoopPlugin) // PR 1615
    )

    /**
     * The built-in named graphs that are always updated when there is a new version of knora-base.
     */
    private val builtInNamedGraphs: Set[BuiltInNamedGraph] = Set(
        BuiltInNamedGraph(
            filename = "knora-ontologies/knora-admin.ttl",
            iri = "http://www.knora.org/ontology/knora-admin"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/knora-base.ttl",
            iri = "http://www.knora.org/ontology/knora-base"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/salsah-gui.ttl",
            iri = "http://www.knora.org/ontology/salsah-gui"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/standoff-onto.ttl",
            iri = "http://www.knora.org/ontology/standoff"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/standoff-data.ttl",
            iri = "http://www.knora.org/data/standoff"
        )
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Represents an update plugin with its knora-base version number and version string.
     *
     * @param versionNumber        the knora-base version number that the plugin's transformation produces.
     * @param plugin               the plugin.
     * @param prBasedVersionString the plugin's PR-based version string (not used for new plugins).
     */
    private case class PluginForKnoraBaseVersion(versionNumber: Int, plugin: UpgradePlugin, prBasedVersionString: Option[String] = None) {
        lazy val versionString: String = {
            prBasedVersionString match {
                case Some(str) => str
                case None => s"knora-base v$versionNumber"
            }
        }
    }

    /**
     * Represents a Knora built-in named graph.
     *
     * @param filename the filename containing the named graph.
     * @param iri      the IRI of the named graph.
     */
    private case class BuiltInNamedGraph(filename: String, iri: String)

    /**
     * Constructs RDF4J values.
     */
    private val valueFactory: SimpleValueFactory = SimpleValueFactory.getInstance

    /**
     * A map of version strings to plugins.
     */
    private val pluginsForVersionsMap: Map[String, PluginForKnoraBaseVersion] = pluginsForVersions.map {
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
            repositoryVersionResponse: SparqlSelectResponse <- (storeActorRef ? SparqlSelectRequest(knoraBaseVersionQuery)).mapTo[SparqlSelectResponse]

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

                pluginsForVersions.filter(_.versionNumber > pluginForRepositoryVersion.versionNumber)

            case None =>
                // The repository has no version string. Include all updates.
                pluginsForVersions
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
            _: FileWrittenResponse <- (storeActorRef ? DownloadRepositoryRequest(downloadedRepositoryFile)).mapTo[FileWrittenResponse]

            // Run the transformations to produce an output file.
            _ = doTransformations(
                downloadedRepositoryFile = downloadedRepositoryFile,
                transformedRepositoryFile = transformedRepositoryFile,
                pluginsForNeededUpdates = pluginsForNeededUpdates
            )

            _ = log.info("Emptying the repository...")

            // Empty the repository.
            _: DropAllRepositoryContentACK <- (storeActorRef ? DropAllTRepositoryContent()).mapTo[DropAllRepositoryContentACK]

            _ = log.info("Uploading transformed repository data...")

            // Upload the transformed repository.
            _: RepositoryUploadedResponse <- (storeActorRef ? UploadRepositoryRequest(transformedRepositoryFile)).mapTo[RepositoryUploadedResponse]
        } yield RepositoryUpdatedResponse(
            message = s"Updated repository to ${org.knora.webapi.KnoraBaseVersion}"
        )
    }

    /**
     * Transforms a file containing a downloaded repository.
     *
     * @param downloadedRepositoryFile the downloaded repository.
     * @param transformedRepositoryFile the transformed file.
     * @param pluginsForNeededUpdates the plugins needed to update the repository.
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
        for (builtInNamedGraph <- builtInNamedGraphs) {
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

