package org.knora.webapi.util

import java.io._

import com.typesafe.scalalogging.Logger
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.RDFWriter
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings
import org.eclipse.rdf4j.rio.turtle._
import org.knora.webapi.{IRI, OntologyConstants}
import org.rogach.scallop._
import org.slf4j.LoggerFactory

import scala.collection.immutable.TreeMap

/**
  * Updates the structure of an ontology to accommodate changes in Knora.
  */
object TransformOntology extends App {
    private val log = Logger(LoggerFactory.getLogger(this.getClass))

    private val TempFilePrefix = "TransformOntology"
    private val TempFileSuffix = ".ttl"

    private val GuiOrderTransformationOption = "guiorder"
    private val AllTransformationsOption = "all"

    private val allTransformations = Vector(
        GuiOrderTransformationOption
    )

    private val conf = new TransformOntologyConf(args)
    private val transformationOption = conf.transform()
    private val inputFile = new File(conf.input())
    private val outputFile = new File(conf.output())

    if (transformationOption == AllTransformationsOption) {
        runAllTransformations(inputFile, outputFile)
    } else {
        runTransformation(transformationOption, inputFile, outputFile)
    }

    /**
      * Runs all transformations, using temporary files as needed.
      *
      * @param inputFile  the input file.
      * @param outputFile the output file.
      */
    private def runAllTransformations(inputFile: File, outputFile: File): Unit = {
        /**
          * Associates a transformation with an input file and and output file, either of which may be a temporary file.
          *
          * @param transformation     the name of the transformation.
          * @param inputFileForTrans  the input file to be used for the transformation.
          * @param outputFileForTrans the output file to be used for the transformation.
          */
        case class TransformationWithFiles(transformation: String, inputFileForTrans: File, outputFileForTrans: File)

        // Make a list of transformations to be run, with an input file and an output file for each one, generating
        // temporary file names as needed.
        val transformationsWithFiles: Vector[TransformationWithFiles] = allTransformations.foldLeft(Vector.empty[TransformationWithFiles]) {
            case (acc, trans) =>
                // Is this is the first transformation?
                val inputFileForTrans = if (trans == allTransformations.head) {
                    // Yes. Use the user's input file as the input file for the transformation.
                    inputFile
                } else {
                    // No. Use the previous transformation's output file as the input file for this transformation.
                    acc.last.outputFileForTrans
                }

                // Is this the last transformation?
                val outputFileForTrans = if (trans == allTransformations.last) {
                    // Yes. Use the user's output file as the output file for the transformation.
                    outputFile
                } else {
                    // No. Use a temporary file.
                    File.createTempFile(TempFilePrefix, TempFileSuffix)
                }

                acc :+ TransformationWithFiles(
                    transformation = trans,
                    inputFileForTrans = inputFileForTrans,
                    outputFileForTrans = outputFileForTrans
                )
        }

        // Run all the transformations.
        for (transformationWithFiles <- transformationsWithFiles) {
            runTransformation(
                transformation = transformationWithFiles.transformation,
                inputFile = transformationWithFiles.inputFileForTrans,
                outputFile = transformationWithFiles.outputFileForTrans
            )
        }
    }

    /**
      * Runs the specified transformation.
      *
      * @param transformation the name of the transformation to be run.
      * @param inputFile      the input file.
      * @param outputFile     the output file.
      */
    private def runTransformation(transformation: String, inputFile: File, outputFile: File): Unit = {
        // println(s"Running transformation $transformation with inputFile $inputFile and outputFile $outputFile")

        val fileInputStream = new FileInputStream(inputFile)
        val fileOutputStream = new FileOutputStream(outputFile)
        val turtleParser = new TurtleParser()
        val turtleWriter = new TurtleWriter(fileOutputStream)

        // TODO: In RDF4J 2.3, TurtleWriter should be able to output anonymous blank nodes, as per https://github.com/eclipse/rdf4j/pull/890.
        // For now, it can only output labelled blank nodes.
        turtleWriter.getWriterConfig.set[java.lang.Boolean](BasicWriterSettings.PRETTY_PRINT, true)

        val handler = transformation match {
            case GuiOrderTransformationOption => new GuiOrderHandler(turtleWriter)
            case _ => throw new Exception(s"Unsupported transformation $transformation")
        }

        turtleParser.setRDFHandler(handler)
        turtleParser.parse(fileInputStream, inputFile.getAbsolutePath)
        fileOutputStream.close()
        fileInputStream.close()
    }

    /**
      * Moves `salsah-gui:guiOrder` from property definitions into cardinalities.
      *
      * @param turtleWriter an [[RDFWriter]] that writes to the output file.
      */
    private class GuiOrderHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        override def endRDF(): Unit = {
            var guiOrders: TreeMap[IRI, Int] = TreeMap.empty[IRI, Int]
            var statementsWithoutGuiOrders: TreeMap[IRI, Vector[Statement]] = TreeMap.empty[IRI, Vector[Statement]]

            statements.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    val subjectType = subjectStatements.find(_.getPredicate.stringValue == OntologyConstants.Rdf.Type).get.getObject.stringValue

                    if (subjectType == OntologyConstants.Owl.ObjectProperty) {
                        val maybeGuiOrderStatement = subjectStatements.find(_.getPredicate.stringValue == OntologyConstants.SalsahGui.GuiOrder)

                        maybeGuiOrderStatement match {
                            case Some(guiOrderStatement: Statement) =>
                                val guiOrder = guiOrderStatement.getObject.stringValue.toInt
                                guiOrders += (subjectIri -> guiOrder)
                                val subjectStatementsWithoutGuiOrders = subjectStatements.filterNot(_.getPredicate.stringValue == OntologyConstants.SalsahGui.GuiOrder)
                                statementsWithoutGuiOrders += (subjectIri -> subjectStatementsWithoutGuiOrders)

                            case None =>
                                statementsWithoutGuiOrders += (subjectIri -> subjectStatements)
                        }
                    } else {
                        statementsWithoutGuiOrders += (subjectIri -> subjectStatements)
                    }
            }

            statementsWithoutGuiOrders.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    val subjectType = subjectStatements.find(_.getPredicate.stringValue == OntologyConstants.Rdf.Type).get.getObject.stringValue

                    val outputStatements = if (subjectType == OntologyConstants.Owl.Restriction) {
                        val onProperty = subjectStatements.find(_.getPredicate.stringValue == OntologyConstants.Owl.OnProperty).get.getObject.stringValue

                        guiOrders.get(onProperty) match {
                            case Some(guiOrder) =>
                                val guiOrderStatement = valueFactory.createStatement(
                                    valueFactory.createBNode(subjectIri),
                                    valueFactory.createIRI(OntologyConstants.SalsahGui.GuiOrder),
                                    valueFactory.createLiteral(guiOrder)
                                )

                                subjectStatements :+ guiOrderStatement

                            case None =>
                                subjectStatements
                        }
                    } else {
                        subjectStatements
                    }

                    for (statement <- outputStatements) {
                        turtleWriter.handleStatement(statement)
                    }
            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Parses command-line arguments.
      */
    private class TransformOntologyConf(arguments: Seq[String]) extends ScallopConf(arguments) {
        banner(
            s"""
               |Updates the structure of a Knora ontology to accommodate changes in Knora.
               |
               |Usage: org.knora.webapi.util.TransformOntology -t [$GuiOrderTransformationOption|$AllTransformationsOption] input output
            """.stripMargin)

        val transform: ScallopOption[String] = opt[String](
            required = true,
            validate = {
                t => Set(GuiOrderTransformationOption, AllTransformationsOption).contains(t)
            },
            descr = s"Selects a transformation. Available transformations: '$GuiOrderTransformationOption' (moves 'salsah-gui:guiOrder' from properties to cardinalities), '$AllTransformationsOption' (all of the above)"
        )

        val input: ScallopOption[String] = trailArg[String](required = true, descr = "Input Turtle file")
        val output: ScallopOption[String] = trailArg[String](required = true, descr = "Output Turtle file")
        verify()
    }

}
