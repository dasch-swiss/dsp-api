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

import org.eclipse.rdf4j.model.{Resource, Statement}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.rio.turtle._
import org.eclipse.rdf4j.rio.{RDFHandler, RDFWriter}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.rogach.scallop._

import scala.collection.immutable.{Iterable, TreeMap}

/**
  * Updates the structure of Knora repository data to accommodate changes in Knora.
  */
object TransformData extends App {
    private val PermissionsTransformation = "permissions"
    private val ValueHasStringTransformation = "strings"
    private val StandoffTransformation = "standoff"

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
        case StandoffTransformation => new StandoffHandler(turtleWriter)
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

        protected def getObject(subjectStatements: Vector[Statement], predicateIri: IRI): String = {
            subjectStatements.find(_.getPredicate.stringValue == predicateIri).get.getObject.stringValue
        }

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
      * Changes standoff blank nodes into `StandoffTag` objects.
      */
    private class StandoffHandler(turtleWriter: RDFWriter) extends StatementCollectingHandler(turtleWriter: RDFWriter) {
        private val knoraIdUtil = new KnoraIdUtil

        // The obsolete standoffHasAttribute predicate.
        private val StandoffHasAttribute = OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasAttribute"

        // A Map of some old standoff class names to new ones.
        private val oldToNewClassIris = Map(
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "StandoffLink" -> OntologyConstants.KnoraBase.StandoffLinkTag,
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "StandoffHref" -> OntologyConstants.KnoraBase.StandoffHrefTag
        )

        // A map of old standoff predicates to new ones.
        private val oldToNewPredicateIris = Map(
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasStart" -> OntologyConstants.KnoraBase.StandoffTagHasStart,
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasEnd" -> OntologyConstants.KnoraBase.StandoffTagHasEnd,
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasLink" -> OntologyConstants.KnoraBase.StandoffTagHasLink,
            OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "standoffHasHref" -> OntologyConstants.KnoraBase.StandoffTagHasHref
        )

        override def endRDF(): Unit = {
            // Make a flat list of all statements in the data.
            val allStatements: Vector[Statement] = statements.values.flatten.toVector

            // Find all the old standoff tag IRIs in the data, and make a Map of old standoff blank node identifiers
            // to new standoff tag IRIs.
            val standoffTagIriMap: Map[IRI, IRI] = allStatements.foldLeft(Map.empty[IRI, IRI]) {
                case (acc, statement: Statement) =>
                    val predicate = statement.getPredicate.stringValue()

                    // Does this statement point to a standoff node?
                    if (predicate == OntologyConstants.KnoraBase.ValueHasStandoff) {
                        // Yes.
                        val oldStandoffIri = statement.getObject.stringValue()

                        // Is the object an old-style blank node identifier?
                        if (!oldStandoffIri.contains("/standoff/")) {
                            // Yes. Make a new IRI for it.
                            val valueObjectIri = statement.getSubject.stringValue()
                            val newStandoffIri = knoraIdUtil.makeRandomStandoffTagIri(valueObjectIri)
                            acc + (oldStandoffIri -> newStandoffIri)
                        } else {
                            // No. Leave it as is.
                            acc
                        }
                    } else {
                        acc
                    }
            }

            // Replace blank node identifiers with IRIs.
            val statementsWithNewIris = allStatements.map {
                statement =>
                    val subject = statement.getSubject.stringValue()
                    val predicate = statement.getPredicate.stringValue()
                    val obj = statement.getObject
                    val objStr = statement.getObject.stringValue()

                    // If the subject is a standoff blank node identifier, replace it with the corresponding IRI.
                    val newSubject = standoffTagIriMap.getOrElse(subject, subject)

                    // If the object is a standoff blank node identifier, replace it with the corresponding IRI.
                    val newObject = predicate match {
                        case OntologyConstants.KnoraBase.ValueHasStandoff =>
                            valueFactory.createIRI(standoffTagIriMap.getOrElse(objStr, objStr))

                        case _ => obj
                    }

                    valueFactory.createStatement(
                        valueFactory.createIRI(newSubject),
                        valueFactory.createIRI(predicate),
                        newObject
                    )
            }

            // Separate out the statements about standoff tags, and group them by subject IRI.
            val standoffTagIris = standoffTagIriMap.values.toSet
            val (standoffStatements: Vector[Statement], nonStandoffStatements: Vector[Statement]) = statementsWithNewIris.partition(statement => standoffTagIris.contains(statement.getSubject.stringValue()))
            val groupedStandoffStatements: Map[Resource, Vector[Statement]] = standoffStatements.groupBy(_.getSubject)

            // Transform the structure of each standoff tag.
            val transformedStandoff: Vector[Statement] = groupedStandoffStatements.flatMap {
                case (tag, tagStatements) =>
                    val oldTagClassIri = getObject(tagStatements, OntologyConstants.Rdf.Type)
                    val tagName = getObject(tagStatements, StandoffHasAttribute)

                    // If the old tag name is "_link", the tag is either a StandoffLink or a StandoffHref. Map the old class name the new one.
                    val newTagClassIri = if (tagName == "_link") {
                        oldToNewClassIris(oldTagClassIri)
                    } else {
                        // Otherwise, generate the new class name from the tag name.
                        StandoffTagV1.EnumValueToIri(StandoffTagV1.lookup(tagName, () => throw InconsistentTriplestoreDataException(s"Unrecognised standoff tag name $tagName")))
                    }

                    // Throw away the standoffHasAttribute statement.
                    val tagStatementsWithoutStandoffHasAttribute = tagStatements.filterNot(_.getPredicate.stringValue == StandoffHasAttribute)

                    // Transform the remaining statements.
                    tagStatementsWithoutStandoffHasAttribute.map {
                        statement =>
                            val oldPredicate = statement.getPredicate.stringValue

                            oldPredicate match {
                                case OntologyConstants.Rdf.Type =>
                                    // Replace the old rdf:type with the new one.
                                    valueFactory.createStatement(
                                        statement.getSubject,
                                        statement.getPredicate,
                                        valueFactory.createIRI(newTagClassIri)
                                    )

                                case _ =>
                                    // Replace other old predicates with new ones.
                                    val newPredicate = oldToNewPredicateIris.getOrElse(oldPredicate, oldPredicate)

                                    // println(s"Replacing $oldPredicate with $newPredicate")

                                    valueFactory.createStatement(
                                        statement.getSubject,
                                        valueFactory.createIRI(oldToNewPredicateIris.getOrElse(oldPredicate, oldPredicate)),
                                        statement.getObject
                                    )
                            }
                    }
            }.toVector

            // Recombine the transformed standoff tags with the rest of the statements in the data.
            val allTransformedStatements = transformedStandoff ++ nonStandoffStatements

            // Sort them by subject IRI.
            val sortedStatements = allTransformedStatements.sortBy(_.getSubject.stringValue())

            // Write them to the output file.
            for (statement <- sortedStatements) {
                turtleWriter.handleStatement(statement)
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
               |Usage: org.knora.webapi.util.TransformData -t [$PermissionsTransformation|$ValueHasStringTransformation|$StandoffTransformation] input output
            """.stripMargin)

        val transform = opt[String](
            required = true,
            validate = t => Set(PermissionsTransformation, ValueHasStringTransformation, StandoffTransformation).contains(t),
            descr = s"Selects a transformation. Available transformations: '$PermissionsTransformation' (combines old-style multiple permission statements into single permission statements), '$ValueHasStringTransformation' (adds missing valueHasString), '$StandoffTransformation' (transforms old-style standoff into new-style standoff)"
        )

        val input = trailArg[String](required = true, descr = "Input Turtle file")
        val output = trailArg[String](required = true, descr = "Output Turtle file")
        verify()
    }
}
