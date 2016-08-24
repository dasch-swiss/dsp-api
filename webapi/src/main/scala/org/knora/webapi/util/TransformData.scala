/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import java.io._

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.rio.turtle._
import org.eclipse.rdf4j.rio.{RDFHandler, RDFWriter}
import org.knora.webapi.messages.v1.responder.valuemessages.{IntervalValueV1, JulianDayCountValueV1, KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.rogach.scallop._

import scala.collection.immutable.TreeMap

/**
  * Updates the structure of Knora repository data to accommodate changes in Knora.
  */
object TransformData extends App {
    private val PermissionsTransformation = "permissions"
    private val ValueHasStringTransformation = "strings"

    private val HasRestrictedViewPermission = "http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission"
    private val HasViewPermission = "http://www.knora.org/ontology/knora-base#hasViewPermission"
    private val HasModifyPermission = "http://www.knora.org/ontology/knora-base#hasModifyPermission"
    private val HasDeletePermission = "http://www.knora.org/ontology/knora-base#hasDeletePermission"
    private val HasChangeRightsPermission = "http://www.knora.org/ontology/knora-base#hasChangeRightsPermission"

    private val allOldPermissions = Set(
        HasRestrictedViewPermission,
        HasViewPermission,
        HasModifyPermission,
        HasDeletePermission,
        HasChangeRightsPermission
    )

    private val oldPermissionIri2Abbreviation = Map(
        HasRestrictedViewPermission -> OntologyConstants.KnoraBase.RestrictedViewPermission,
        HasViewPermission -> OntologyConstants.KnoraBase.ViewPermission,
        HasModifyPermission -> OntologyConstants.KnoraBase.ModifyPermission,
        HasDeletePermission -> OntologyConstants.KnoraBase.DeletePermission,
        HasChangeRightsPermission -> OntologyConstants.KnoraBase.ChangeRightsPermission
    )

    val conf = new Conf(args)
    val transformation = conf.transform()
    val inputFile = conf.input()
    val outputFile = conf.output()

    val fileInputStream = new FileInputStream(new File(inputFile))
    val fileOutputStream = new FileOutputStream(new File(outputFile))
    val turtleParser = new TurtleParser()
    val turtleWriter = new TurtleWriter(fileOutputStream)

    val handler = transformation match {
        case PermissionsTransformation => new PermissionsHandler(turtleWriter)
        case ValueHasStringTransformation => new ValueHasStringHandler(turtleWriter)
        case _ => throw new Exception(s"Unsupported transformation $transformation")
    }

    turtleParser.setRDFHandler(handler)
    turtleParser.parse(fileInputStream, inputFile)
    fileOutputStream.close()
    fileInputStream.close()

    /**
      * An abstract [[RDFHandler]] that collects all statements so they can be processed when the end of the
      * input file is reached. Subclasses need to implement only `endRDF`.
      */
    protected abstract class StatementCollectingHandler(turtleWriter: RDFWriter) extends RDFHandler {
        protected val valueFactory = SimpleValueFactory.getInstance()
        protected var statements = TreeMap.empty[IRI, Vector[Statement]]

        override def handleStatement(st: Statement): Unit = {
            val subjectIri = st.getSubject.stringValue()
            val currentStatementsForSubject = statements.getOrElse(subjectIri, Vector.empty[Statement])
            statements += (subjectIri -> (currentStatementsForSubject :+ st))
        }

        override def handleComment(comment: IRI): Unit = {}


        override def handleNamespace(prefix: IRI, uri: IRI): Unit = {
            turtleWriter.handleNamespace(prefix, uri)
        }

        override def startRDF(): Unit = {
            turtleWriter.startRDF()
        }
    }

    /**
      * Transforms old-style Knora permissions statements into new-style permissions statements.
      */
    private class PermissionsHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        override def endRDF(): Unit = {
            statements.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    // Write the statements about each resource.
                    val subjectPermissions = subjectStatements.foldLeft(Map.empty[String, Set[IRI]]) {
                        case (acc, st) =>
                            val predicateStr = st.getPredicate.stringValue

                            // If a statement describes an old-style permission, save it.
                            if (allOldPermissions.contains(predicateStr)) {
                                val group = st.getObject.stringValue()
                                val abbreviation = oldPermissionIri2Abbreviation(predicateStr)
                                val currentGroupsForPermission = acc.getOrElse(abbreviation, Set.empty[IRI])
                                acc + (abbreviation -> (currentGroupsForPermission + group))
                            } else {
                                // Otherwise, write it.
                                turtleWriter.handleStatement(st)
                                acc
                            }
                    }

                    // Write the resource's permissions as a single statement.
                    if (subjectPermissions.nonEmpty) {
                        val permissionLiteral = PermissionUtilV1.formatPermissions(subjectPermissions)

                        val permissionStatement = valueFactory.createStatement(
                            valueFactory.createIRI(subjectIri),
                            valueFactory.createIRI(OntologyConstants.KnoraBase.HasPermissions),
                            valueFactory.createLiteral(permissionLiteral.get)
                        )

                        turtleWriter.handleStatement(permissionStatement)
                    }
            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Adds missing `knora-base:valueHasString` statements.
      */
    private class ValueHasStringHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        private def getObject(subjectStatements: Vector[Statement], predicateIri: IRI): String = {
            subjectStatements.find(_.getPredicate.stringValue == predicateIri).get.getObject.stringValue
        }

        private def maybeWriteValueHasString(subjectIri: IRI, subjectStatements: Vector[Statement]): Unit = {
            val resourceClass = getObject(subjectStatements, OntologyConstants.Rdf.Type)

            // Is this Knora value object?
            if (resourceClass.startsWith(OntologyConstants.KnoraBase.KnoraBasePrefixExpansion) && resourceClass.endsWith("Value")) {
                // Yes. Does it already have a valueHasString?
                val maybeValueHasStringStatement: Option[Statement] = subjectStatements.find(_.getPredicate.stringValue == OntologyConstants.KnoraBase.ValueHasString)

                if (maybeValueHasStringStatement.isEmpty) {
                    // No. Generate one.

                    val stringLiteral = resourceClass match {
                        case OntologyConstants.KnoraBase.IntValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasInteger)
                        case OntologyConstants.KnoraBase.BooleanValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasBoolean)
                        case OntologyConstants.KnoraBase.UriValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasUri)
                        case OntologyConstants.KnoraBase.DecimalValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasDecimal)

                        case OntologyConstants.KnoraBase.DateValue =>
                            val startJDC = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasStartJDC)
                            val endJDC = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasEndJDC)
                            val startPrecision = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasStartPrecision)
                            val endPrecision = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasEndPrecision)
                            val calendar = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasCalendar)

                            val jdcValue = JulianDayCountValueV1(
                                dateval1 = startJDC.toInt,
                                dateval2 = endJDC.toInt,
                                calendar = KnoraCalendarV1.lookup(calendar),
                                dateprecision1 = KnoraPrecisionV1.lookup(startPrecision),
                                dateprecision2 = KnoraPrecisionV1.lookup(endPrecision)
                            )

                            jdcValue.toString

                        case OntologyConstants.KnoraBase.ColorValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasColor)
                        case OntologyConstants.KnoraBase.GeomValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasGeometry)
                        case OntologyConstants.KnoraBase.StillImageFileValue => getObject(subjectStatements, OntologyConstants.KnoraBase.OriginalFilename)
                        case OntologyConstants.KnoraBase.ListValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasListNode)

                        case OntologyConstants.KnoraBase.IntervalValue =>
                            val intervalStart = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasIntervalStart)
                            val intervalEnd = getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasIntervalEnd)

                            val intervalValue = IntervalValueV1(
                                timeval1 = BigDecimal(intervalStart),
                                timeval2 = BigDecimal(intervalEnd)
                            )

                            intervalValue.toString

                        case OntologyConstants.KnoraBase.GeonameValue => getObject(subjectStatements, OntologyConstants.KnoraBase.ValueHasGeonameCode)
                        case OntologyConstants.KnoraBase.LinkValue => getObject(subjectStatements, OntologyConstants.Rdf.Object)

                        case _ => throw InconsistentTriplestoreDataException(s"Unsupported value type $resourceClass")
                    }

                    val valueHasStringStatement = valueFactory.createStatement(
                        valueFactory.createIRI(subjectIri),
                        valueFactory.createIRI(OntologyConstants.KnoraBase.ValueHasString),
                        valueFactory.createLiteral(stringLiteral)
                    )

                    turtleWriter.handleStatement(valueHasStringStatement)
                }
            }
        }

        override def endRDF(): Unit = {
            statements.foreach {
                case (subjectIri: IRI, subjectStatements: Vector[Statement]) =>
                    subjectStatements.foreach(st => turtleWriter.handleStatement(st))
                    maybeWriteValueHasString(subjectIri, subjectStatements)
            }

            turtleWriter.endRDF()
        }
    }

    /**
      * Parses command-line arguments.
      */
    class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
        banner(
            s"""
               |Updates the structure of Knora repository data to accommodate changes in Knora.
               |
               |Usage: org.knora.webapi.util.TransformData -t [$PermissionsTransformation|$ValueHasStringTransformation] input output
            """.stripMargin)

        val transform = opt[String](
            required = true,
            validate = t => Set(PermissionsTransformation, ValueHasStringTransformation).contains(t),
            descr = s"Selects a transformation. Available transformations: '$PermissionsTransformation' (combines old-style multiple permission statements into single permission statements), '$ValueHasStringTransformation' (adds missing valueHasString)"
        )

        val input = trailArg[String](required = true, descr = "Input Turtle file")
        val output = trailArg[String](required = true, descr = "Output Turtle file")
    }
}
