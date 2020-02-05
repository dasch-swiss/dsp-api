/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.upgrade

import java.io._

import org.eclipse.rdf4j.model.impl.{LinkedHashModel, SimpleValueFactory}
import org.eclipse.rdf4j.model.util.Models
import org.eclipse.rdf4j.model.{Model, Statement}
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, Rio}
import org.knora.upgrade.plugins._
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.JavaUtil._
import org.knora.webapi.{InconsistentTriplestoreDataException, OntologyConstants}
import org.rogach.scallop._

import scala.collection.JavaConverters._


/**
 * Updates a dump of a Knora repository to accommodate changes in Knora.
 */
object Main extends App {
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A list of all plugins in chronological order.
     */
    val pluginsForVersions: Seq[PluginForKnoraBaseVersion] = Seq(
        PluginForKnoraBaseVersion(versionNumber = 1, plugin = new UpgradePluginPR1307, prBasedVersionString = Some("PR 1307")),
        PluginForKnoraBaseVersion(versionNumber = 2, plugin = new UpgradePluginPR1322, prBasedVersionString = Some("PR 1322")),
        PluginForKnoraBaseVersion(versionNumber = 3, plugin = new UpgradePluginPR1367, prBasedVersionString = Some("PR 1367")),
        PluginForKnoraBaseVersion(versionNumber = 4, plugin = new UpgradePluginPR1372, prBasedVersionString = Some("PR 1372")),
        PluginForKnoraBaseVersion(versionNumber = 5, plugin = new NoopPlugin, prBasedVersionString = Some("PR 1440")),
        PluginForKnoraBaseVersion(versionNumber = 6, plugin = new NoopPlugin), // PR 1206
        PluginForKnoraBaseVersion(versionNumber = 7, plugin = new NoopPlugin) // PR 1403
    )

    /**
     * The built-in named graphs that are always updated when there is a new version of knora-base.
     */
    val builtInNamedGraphs: Set[BuiltInNamedGraph] = Set(
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
    case class PluginForKnoraBaseVersion(versionNumber: Int, plugin: UpgradePlugin, prBasedVersionString: Option[String] = None) {
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
    case class BuiltInNamedGraph(filename: String, iri: String)

    // Debug.printResources(builtInNamedGraphs.map(_.filename).toSeq)

    /**
     * Constructs RDF4J values.
     */
    val valueFactory = SimpleValueFactory.getInstance

    /**
     * A map of version strings to plugins.
     */
    val pluginsForVersionsMap: Map[String, PluginForKnoraBaseVersion] = pluginsForVersions.map {
        knoraBaseVersion => knoraBaseVersion.versionString -> knoraBaseVersion
    }.toMap

    // Parse the command-line arguments.
    val conf = new TransformDataConf(args)
    val inputFile = new File(conf.input())
    val outputFile = new File(conf.output())

    // Parse the input file.
    println("Reading input file...")
    val model = readFileIntoModel(inputFile, RDFFormat.TRIG)
    println(s"Read ${model.size} statements.")

    // Get the repository's version string, if any.
    val maybeRepositoryVersionString: Option[String] = Models.getPropertyLiteral(
        model,
        valueFactory.createIRI(OntologyConstants.KnoraBase.KnoraBaseOntologyIri),
        valueFactory.createIRI(OntologyConstants.KnoraBase.OntologyVersion)
    ).toOption.map(_.stringValue)

    // Is the repository up to date?
    if (maybeRepositoryVersionString.contains(org.knora.webapi.KnoraBaseVersion)) {
        // Yes. Nothing more to do.
        println(s"Repository is up to date at version ${org.knora.webapi.KnoraBaseVersion}.")
    } else {
        // No. Construct the list of updates that it needs.
        val pluginsForNeededUpdates: Seq[PluginForKnoraBaseVersion] = maybeRepositoryVersionString match {
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

        println(s"Needed transformations: ${pluginsForNeededUpdates.map(_.versionString).mkString(", ")}")

        // Run the update plugins.
        for (pluginForNeededUpdate <- pluginsForNeededUpdates) {
            println(s"Running transformation for ${pluginForNeededUpdate.versionString}...")
            pluginForNeededUpdate.plugin.transform(model)
        }

        // Update the built-in named graphs.

        println("Updating built-in named graphs...")

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

        // Write the output file.
        println(s"Writing output file (${model.size} statements)...")
        val fileWriter = new FileWriter(outputFile)
        val bufferedWriter = new BufferedWriter(fileWriter)
        Rio.write(model, fileWriter, RDFFormat.TRIG)
        bufferedWriter.close()
        fileWriter.close()
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

    /**
     * Parses command-line arguments.
     */
    class TransformDataConf(arguments: Seq[String]) extends ScallopConf(arguments) {
        banner(
            s"""
               |Updates a dump of a repository to accommodate changes in Knora.
               |
               |Usage: org.knora.webapi.util.UpdateRepository input output
            """.stripMargin)

        val input: ScallopOption[String] = trailArg[String](required = true, descr = "Input TriG file")
        val output: ScallopOption[String] = trailArg[String](required = true, descr = "Output TriG file")
        verify()
    }

}
