/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.ActorSelection
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{CheckSubClassRequestV1, CheckSubClassResponseV1}
import org.knora.webapi.messages.v1.responder.resourcemessages.LocationV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.VariableResultsRow
import org.knora.webapi.responders.v1.GroupedProps._
import org.knora.webapi.util.{DateUtilV1, ErrorHandlingMap, InputValidation}

import scala.concurrent.{ExecutionContext, Future}


/**
  * Converts data from SPARQL query results into [[ApiValueV1]] objects.
  */
class ValueUtilV1(private val settings: SettingsImpl) {

    /**
      * Given a [[ValueProps]] containing details of a `knora-base:Value` object, creates a [[ApiValueV1]].
      *
      * @param valueProps a [[GroupedProps.ValueProps]] resulting from querying the `Value`, in which the keys are RDF predicates,
      *                   and the values are lists of the objects of each predicate.
      * @return a [[ApiValueV1]] representing the `Value`.
      */
    def makeValueV1(valueProps: ValueProps): ApiValueV1 = {
        val valueTypeIri = valueProps.literalData(OntologyConstants.Rdf.Type).literals.head
        val valueFunction = valueFunctions(valueTypeIri)
        valueFunction(valueProps)
    }

    def makeSipiImagePreviewGetUrlFromFilename(filename: String): String = {
        s"${settings.sipiIIIFGetUrl}/$filename/full/full/0/default.jpg"
    }

    /**
      * Creates a IIIF URL for accessing an image file via Sipi.
      *
      * @param imageFileValueV1 the image file value representing the image.
      * @return a Sipi URL.
      */
    def makeSipiImageGetUrlFromFilename(imageFileValueV1: StillImageFileValueV1): String = {
        if (!imageFileValueV1.isPreview) {
            // not a thumbnail
            // calculate the correct size from the source image depending on the given dimensions
            s"${settings.sipiIIIFGetUrl}/${imageFileValueV1.internalFilename}/full/${imageFileValueV1.dimX},${imageFileValueV1.dimY}/0/default.jpg"
        } else {
            // thumbnail
            makeSipiImagePreviewGetUrlFromFilename(imageFileValueV1.internalFilename)
        }
    }

    // A Map of MIME types to Knora API v1 binary format name.
    private val mimeType2V1Format = new ErrorHandlingMap(Map(
        "application/octet-stream" -> "BINARY-UNKNOWN",
        "image/jpeg" -> "JPEG",
        "image/jp2" -> "JPEG2000",
        "application/pdf" -> "PDF",
        "application/postscript" -> "POSTSCRIPT",
        "application/vnd.ms-powerpoint" -> "PPT",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPTX",
        "application/rtf" -> "RTF",
        "video/salsah" -> "WEBVIDEO",
        "text/sgml" -> "SGML",
        "image/tiff" -> "TIFF",
        "application/msword" -> "WORD",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "WORDX",
        "application/vnd.ms-excel" -> "XLS",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLSX",
        "application/xml" -> "XML",
        "application/zip" -> "ZIP",
        "application/x-compressed-zip" -> "ZIP"
    ), { key: String => s"Unknown MIME type: $key" })


    /**
      * Converts a [[FileValueV1]] (which is used internally by the Knora API server) to a [[LocationV1]] (which is
      * used in certain API responses).
      *
      * @param fileValueV1 a [[FileValueV1]].
      * @return a [[LocationV1]].
      */
    def fileValueV12LocationV1(fileValueV1: FileValueV1): LocationV1 = {
        fileValueV1 match {
            case stillImageFileValueV1: StillImageFileValueV1 =>
                LocationV1(
                    format_name = mimeType2V1Format(stillImageFileValueV1.internalMimeType),
                    origname = stillImageFileValueV1.originalFilename,
                    nx = Some(stillImageFileValueV1.dimX),
                    ny = Some(stillImageFileValueV1.dimY),
                    path = makeSipiImageGetUrlFromFilename(stillImageFileValueV1)
                )
            case otherType => throw NotImplementedException(s"Type not yet implemented: ${otherType.valueTypeIri}")
        }
    }

    /**
      * Creates a URL pointing to the given resource class icon. From the resource class Iri it gets the ontology specific path, i.e. the ontology name.
      * If the resource class Iri is "http://www.knora.org/ontology/knora-base#Region", the ontology name would be "knora-base".
      * To the base path, the icon name is appended. In case of a region with the icon name "region.gif",
      * "http://salsahapp:port/project-icons-basepath/knora-base/region.gif" is returned.
      *
      * This method requires the Iri segment before the last slash to be a unique identifier for all the ontologies used with Knora..
      *
      * @param resourceClassIri the Iri of the resource class in question.
      * @param iconsSrc         the name of the icon file.
      */
    def makeResourceClassIconURL(resourceClassIri: IRI, iconsSrc: String): IRI = {
        // get ontology name, e.g. "knora-base" from "http://www.knora.org/ontology/knora-base#Region"
        // add +1 to ignore the slash
        val ontologyName = resourceClassIri.substring(resourceClassIri.lastIndexOf('/') + 1, resourceClassIri.lastIndexOf('#'))

        // create URL: combine salsah-address and port, project icons base path, ontology name, icon name
        settings.baseSALSAHUrl + settings.projectIconsBasePath + ontologyName + '/' + iconsSrc
    }

    /**
      * Creates [[ValueProps]] from a List of [[VariableResultsRow]] representing a value object
      * (the triples where the given value object is the subject in).
      *
      * A [[VariableResultsRow]] is expected to have the following members (SPARQL variable names):
      *
      * - objPred: the object predicate (e.g. http://www.knora.org/ontology/knora-base#valueHasString).
      * - objObj: The string representation of the value assigned to objPred.
      *
      * In one given row, objPred **must** indicate the type of the given value object using rdfs:type (e.g. http://www.knora.org/ontology/knora-base#TextValue)
      *
      * In case the given value object contains standoff (objPred is http://www.knora.org/ontology/knora-base#valueHasStandoff),
      * it has the following additional members compared those mentioned above:
      *
      * - predStandoff: the standoff predicate (e.g. http://www.knora.org/ontology/knora-base#standoffHasStart)
      * - objStandoff: the string representation of the value assigned to predStandoff
      *
      * @param valueIri the IRI of the value that was queried.
      * @param objRows  SPARQL results.
      * @return a [[ValueProps]] representing the SPARQL results.
      */
    def createValueProps(valueIri: IRI, objRows: Seq[VariableResultsRow]): ValueProps = {
        val (values: Map[String, ValueLiterals], standoff: Seq[Map[IRI, String]]) = groupKnoraValueObjectPredicateRows(objRows.map(_.rowMap))
        ValueProps(new ErrorHandlingMap(values, { key: IRI => s"Predicate $key not found in value $valueIri" }), standoff)
    }

    /**
      * Converts three lists of SPARQL query results representing all the properties of a resource into a [[GroupedPropertiesByType]].
      *
      * Each [[VariableResultsRow]] is expected to have the following SPARQL variables:
      *
      * - prop: the IRI of the resource property (e.g. http://www.knora.org/ontology/knora-base#hasComment)
      * - obj: the IRI of the object that the property points to, which may be either a value object (an ordinary value or a reification) or another resource
      * - objPred: the IRI of each predicate of `obj` (e.g. for its literal contents, or for its permissions)
      * - objObj: the object of each `objPred`
      *
      * The remaining members are identical to those documented in [[createValueProps]].
      *
      * @param rowsWithOrdinaryValues SPARQL result rows describing properties that point to ordinary values (not link values).
      * @param rowsWithLinkValues     SPARQL result rows describing properties that point link values (reifications of links to resources).
      * @param rowsWithLinks          SPARQL result rows describing properties that point to resources.
      * @return a [[GroupedPropertiesByType]] representing the SPARQL results.
      */
    def createGroupedPropsByType(rowsWithOrdinaryValues: Seq[VariableResultsRow],
                                 rowsWithLinkValues: Seq[VariableResultsRow],
                                 rowsWithLinks: Seq[VariableResultsRow]): GroupedPropertiesByType = {
        GroupedPropertiesByType(
            groupedOrdinaryValueProperties = groupKnoraPropertyRows(rowsWithOrdinaryValues),
            groupedLinkValueProperties = groupKnoraPropertyRows(rowsWithLinkValues),
            groupedLinkProperties = groupKnoraPropertyRows(rowsWithLinks)
        )
    }

    /**
      * Checks that a value type is valid for the `knora-base:objectClassConstraint` of a property.
      *
      * @param propertyIri                   the IRI of the property.
      * @param valueType                     the IRI of the value type.
      * @param propertyObjectClassConstraint the IRI of the property's `knora-base:objectClassConstraint`.
      * @param responderManager              a reference to the Knora API Server responder manager.
      * @return A future containing Unit on success, or a failed future if the value type is not valid for the property's range.
      */
    def checkValueTypeForPropertyObjectClassConstraint(propertyIri: IRI,
                                                       valueType: IRI,
                                                       propertyObjectClassConstraint: IRI,
                                                       responderManager: ActorSelection)
                                                      (implicit timeout: Timeout, executionContext: ExecutionContext): Future[Unit] = {
        if (propertyObjectClassConstraint == valueType) {
            Future.successful(())
        } else {
            for {
                checkSubClassResponse <- (responderManager ? CheckSubClassRequestV1(
                    subClassIri = valueType,
                    superClassIri = propertyObjectClassConstraint
                )).mapTo[CheckSubClassResponseV1]

                _ = if (!checkSubClassResponse.isSubClass) {
                    throw OntologyConstraintException(s"Property $propertyIri requires a value of type $propertyObjectClassConstraint")
                }
            } yield ()
        }
    }

    /**
      * Creates a tuple that can be turned into a [[ValueProps]] representing both literal values and standoff.
      *
      * It expects the members documented in [[createValueProps]].
      *
      * @param objRows the value object with its predicates
      * @return a tuple containing (1) the values (literal or linking) and (2) standoff nodes if given
      */
    private def groupKnoraValueObjectPredicateRows(objRows: Seq[Map[String, String]]): (Map[String, ValueLiterals], Seq[Map[IRI, String]]) = {

        objRows.map(_ - "obj").filter(_.get("objObj").nonEmpty).groupBy(_ ("objPred")).foldLeft((Map.empty[String, ValueLiterals], Vector.empty[Map[IRI, String]])) {
            // grouped by value object predicate (e.g. hasString)
            case (acc: (Map[String, ValueLiterals], Seq[Map[IRI, String]]), (objPredIri: IRI, values: Seq[Map[String, String]])) =>

                if (objPredIri == OntologyConstants.KnoraBase.ValueHasStandoff) {
                    // standoff information

                    val groupByNode: Seq[Map[String, String]] = values.groupBy(_ ("objObj")).map {
                        case (blankNodeIri: IRI, values: Seq[Map[String, String]]) =>
                            // get rid of the IRI of the blank nodes used for doing the grouping
                            values
                    }.map {
                        // here, we have a List with one element for each standoff node (groupBy)
                        case values: Seq[Map[String, String]] => values.map {
                            case value: Map[String, String] => Map(value("predStandoff") -> value("objStandoff"))
                        }.foldLeft(Map.empty[String, String]) {
                            // for each standoff node, we want to have just one Map
                            case (node: Map[String, String], (value: Map[String, String])) => node ++ value
                        }
                    }.toVector

                    (acc._1, acc._2 ++ groupByNode) // the accumulator is a 2 tuple, add standoff to the second part
                } else {
                    // non standoff value

                    val value: (String, ValueLiterals) = (objPredIri, ValueLiterals(values.map {
                        case value: Map[String, String] =>
                            value("objObj")
                    }))

                    (acc._1 + value, acc._2) // the accumulator is a 2 tuple, add ValueData to the first part
                }
        }
    }

    /**
      *
      * Given a list of result rows from the `get-resource-properties-and-values` SPARQL query, groups the rows first by property,
      * then by property object, and finally by property object predicate. In case the results contain standoff information, the standoff nodes are grouped
      * according to their blank node Iri. If the first row of results has a `linkValue` column, this is taken to mean that the property
      * is a link property and that the value of `linkValue` is the IRI of the corresponding `knora-base:LinkValue`; that IRI is then
      * added to the literals in the results, with the key [[OntologyConstants.KnoraBase.LinkValue]].
      *
      * For example, suppose we have the following rows for a property that points to Knora values.
      *
      * {{{
      * prop                obj                                                     objPred                            objObj
      * ---------------------------------------------------------------------------------------------------------------------------------------------------
      * incunabula:pagenum       http://data.knora.org/8a0b1e75/values/61cb927602        knora-base:valueHasString          a1r, Titelblatt
      * incunabula:pagenum       http://data.knora.org/8a0b1e75/values/61cb927602        knora-base:hasViewPermission       knora-base:KnownUser
      * incunabula:pagenum       http://data.knora.org/8a0b1e75/values/61cb927602        knora-base:hasViewPermission       knora-base:UnknownUser
      * }}}
      *
      * The result will be a [[GroupedProperties]] containing a [[ValueProps]] with two keys, `valueHasString` and `hasPermission`.
      *
      * @param rows the SPARQL query result rows to group, which are expected to contain the columns given in the description of the `createGroupedPropsByType`
      *             method.
      * @return a [[GroupedProperties]] representing the SPARQL results.
      */
    private def groupKnoraPropertyRows(rows: Seq[VariableResultsRow]): GroupedProperties = {
        val gp: Map[String, ValueObjects] = rows.groupBy(_.rowMap("prop")).map {
            // grouped by resource property (e.g. hasComment)
            case (resProp: String, rows: Seq[VariableResultsRow]) =>
                val vo = (resProp, rows.map(_.rowMap - "prop").groupBy(_ ("obj")).map {
                    // grouped by value object IRI
                    case (objIri: IRI, objRows: Seq[Map[String, String]]) =>
                        val (literals: Map[String, ValueLiterals], standoff: Seq[Map[IRI, String]]) = groupKnoraValueObjectPredicateRows(objRows)

                        val vp: ValueProps = ValueProps(
                            new ErrorHandlingMap(literals, { key: IRI => s"Predicate $key not found for property object $objIri" }),
                            standoff
                        )

                        (objIri, vp)
                })

                (resProp, GroupedProps.ValueObjects(vo._2))
        }

        GroupedProps.GroupedProperties(gp)
    }

    /**
      * A [[Map]] of value type IRIs to functions that can generate [[ApiValueV1]] instances for those types.
      */
    private val valueFunctions: Map[IRI, (ValueProps) => ApiValueV1] = new ErrorHandlingMap(Map(
        OntologyConstants.KnoraBase.TextValue -> makeTextValue,
        OntologyConstants.KnoraBase.IntValue -> makeIntValue,
        OntologyConstants.KnoraBase.DecimalValue -> makeDecimalValue,
        OntologyConstants.KnoraBase.BooleanValue -> makeBooleanValue,
        OntologyConstants.KnoraBase.UriValue -> makeUriValue,
        OntologyConstants.KnoraBase.DateValue -> makeDateValue,
        OntologyConstants.KnoraBase.ColorValue -> makeColorValue,
        OntologyConstants.KnoraBase.GeomValue -> makeGeomValue,
        OntologyConstants.KnoraBase.GeonameValue -> makeGeonameValue,
        OntologyConstants.KnoraBase.ListValue -> makeListValue,
        OntologyConstants.KnoraBase.IntervalValue -> makeIntervalValue,
        OntologyConstants.KnoraBase.StillImageFileValue -> makeStillImageValue,
        OntologyConstants.KnoraBase.LinkValue -> makeLinkValue
    ), { key: IRI => s"Unknown value type: $key" })

    /**
      * Converts a [[ValueProps]] into an [[IntegerValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return an [[IntegerValueV1]].
      */
    private def makeIntValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        IntegerValueV1(predicates(OntologyConstants.KnoraBase.ValueHasInteger).literals.head.toInt)
    }

    /**
      * Converts a [[ValueProps]] into a [[DecimalValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[DecimalValueV1]].
      */
    private def makeDecimalValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        DecimalValueV1(BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasDecimal).literals.head))
    }

    /**
      * Converts a [[ValueProps]] into a [[BooleanValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[BooleanValueV1]].
      */
    private def makeBooleanValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        BooleanValueV1(predicates(OntologyConstants.KnoraBase.ValueHasBoolean).literals.head.toBoolean)
    }

    /**
      * Converts a [[ValueProps]] into a [[UriValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[UriValueV1]].
      */
    private def makeUriValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        UriValueV1(predicates(OntologyConstants.KnoraBase.ValueHasUri).literals.head)
    }

    /**
      * Converts a [[ValueProps]] into a [[DateValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[DateValueV1]].
      */
    private def makeDateValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        val julianDayCountValueV1 = JulianDayCountValueV1(
            dateval1 = predicates(OntologyConstants.KnoraBase.ValueHasStartJDC).literals.head.toInt,
            dateval2 = predicates(OntologyConstants.KnoraBase.ValueHasEndJDC).literals.head.toInt,
            dateprecision1 = KnoraPrecisionV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasStartPrecision).literals.head),
            dateprecision2 = KnoraPrecisionV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasEndPrecision).literals.head),
            calendar = KnoraCalendarV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasCalendar).literals.head)
        )

        DateUtilV1.julianDayCountValueV1ToDateValueV1(julianDayCountValueV1)
    }

    /**
      * Converts a [[ValueProps]] into an [[IntervalValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[IntervalValueV1]].
      */
    private def makeIntervalValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        IntervalValueV1(
            timeval1 = BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasIntervalStart).literals.head),
            timeval2 = BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasIntervalEnd).literals.head)
        )
    }

    /**
      * Converts a [[ValueProps]] into a [[TextValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[TextValueV1]].
      */
    private def makeTextValue(valueProps: ValueProps): ApiValueV1 = {

        val groupedByAttr: Map[StandoffTagV1.Value, Seq[StandoffPositionV1]] = valueProps.standoff.groupBy(row =>
            // group by the enumeration name of the standoff tag IRI by converting the standoff tag IRI
            // standoff lik and href tags have the same enumeration name, so the groupBy has to combine their standoff positions
            StandoffTagV1.IriToEnumValue(row(OntologyConstants.Rdf.Type))
        ).map {
            case (tagName: StandoffTagV1.Value, standoffInfos: Seq[Map[String, String]]) =>

                // we grouped by the attribute name, return it as the key of that Map
                (tagName, standoffInfos.map {
                    // for each attribute name, we may have several positions that have to be turned into a StandoffPositionV1

                    case standoffInfo =>
                        val maybeResId = standoffInfo.get(OntologyConstants.KnoraBase.StandoffHasLink)

                        // If there's a resid, generate an href from it, because the SALSAH GUI expects this.
                        // Otherwise, use the href returned by the query, if present.
                        val maybeHref = if (maybeResId.nonEmpty) {
                            maybeResId
                        } else {
                            standoffInfo.get(OntologyConstants.KnoraBase.StandoffHasHref)
                        }

                        StandoffPositionV1(
                            start = standoffInfo(OntologyConstants.KnoraBase.StandoffHasStart).toInt,
                            end = standoffInfo(OntologyConstants.KnoraBase.StandoffHasEnd).toInt,
                            href = maybeHref,
                            resid = maybeResId
                        )
                })
        }

        // map over all _link attributes to collect IRIs that are referred to
        val resids: Set[IRI] = groupedByAttr.get(StandoffTagV1.link) match {
            case Some(links: Seq[StandoffPositionV1]) => InputValidation.getResourceIrisFromStandoffLinkTags(links)
            case None => Set.empty[IRI]
        }

        // If there's an empty string in the data (which does sometimes happen), the store package will remove it from
        // the query results. Therefore, if knora-base:valueHasString is missing, we interpret it as an empty string.
        val valueHasString = valueProps.literalData.get(OntologyConstants.KnoraBase.ValueHasString).map(_.literals.head).getOrElse("")

        TextValueV1(
            utf8str = valueHasString,
            textattr = groupedByAttr,
            resource_reference = resids
        )
    }

    /**
      * Converts a [[ValueProps]] into a [[ColorValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[ColorValueV1]].
      */
    private def makeColorValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        ColorValueV1(predicates(OntologyConstants.KnoraBase.ValueHasColor).literals.head)
    }

    /**
      * Converts a [[ValueProps]] into a [[GeomValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[GeomValueV1]].
      */
    private def makeGeomValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        GeomValueV1(predicates(OntologyConstants.KnoraBase.ValueHasGeometry).literals.head)
    }

    /**
      * Converts a [[ValueProps]] into a [[HierarchicalListValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[HierarchicalListValueV1]].
      */
    private def makeListValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        HierarchicalListValueV1(predicates(OntologyConstants.KnoraBase.ValueHasListNode).literals.head)
    }

    /**
      * Converts a [[ValueProps]] into a [[StillImageFileValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[StillImageFileValueV1]].
      */
    private def makeStillImageValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        StillImageFileValueV1(
            internalMimeType = predicates(OntologyConstants.KnoraBase.InternalMimeType).literals.head,
            internalFilename = predicates(OntologyConstants.KnoraBase.InternalFilename).literals.head,
            originalFilename = predicates(OntologyConstants.KnoraBase.OriginalFilename).literals.head,
            dimX = predicates(OntologyConstants.KnoraBase.DimX).literals.head.toInt,
            dimY = predicates(OntologyConstants.KnoraBase.DimY).literals.head.toInt,
            qualityLevel = predicates(OntologyConstants.KnoraBase.QualityLevel).literals.head.toInt,
            isPreview = InputValidation.optionStringToBoolean(predicates.get(OntologyConstants.KnoraBase.IsPreview).flatMap(_.literals.headOption))
        )
    }

    /**
      * Converts a [[ValueProps]] into a [[LinkValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[LinkValueV1]].
      */
    private def makeLinkValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        LinkValueV1(
            subjectIri = predicates(OntologyConstants.Rdf.Subject).literals.head,
            predicateIri = predicates(OntologyConstants.Rdf.Predicate).literals.head,
            objectIri = predicates(OntologyConstants.Rdf.Object).literals.head,
            referenceCount = predicates(OntologyConstants.KnoraBase.ValueHasRefCount).literals.head.toInt
        )
    }

    /**
      * Converts a [[ValueProps]] into a [[GeonameValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[GeonameValueV1]].
      */
    private def makeGeonameValue(valueProps: ValueProps): ApiValueV1 = {
        val predicates = valueProps.literalData

        GeonameValueV1(predicates(OntologyConstants.KnoraBase.ValueHasGeonameCode).literals.head)
    }

    /** Creates an attribute segment for the Salsah GUI from the given resource class.
      * Example: if "http://www.knora.org/ontology/incunabula#book" is given, the function returns "restypeid=http://www.knora.org/ontology/incunabula#book".
      *
      * @param resourceClass the resource class.
      * @return an attribute string to be included in the attributes for the GUI
      */
    def makeAttributeRestype(resourceClass: IRI) = {
        OntologyConstants.SalsahGui.attributeNames.resourceClass + OntologyConstants.SalsahGui.attributeNames.assignmentOperator + resourceClass
    }

    /**
      * Given a set of attribute segments representing assertions about the values of [[OntologyConstants.SalsahGui.GuiAttribute]] for a property,
      * combines the attributes into a string for use in an API v1 response.
      *
      * @param attributes the values of [[OntologyConstants.SalsahGui.GuiAttribute]] for a property.
      * @return a semicolon-delimited string containing the attributes, or [[None]] if no attributes were found.
      */
    def makeAttributeString(attributes: Set[String]): Option[String] = {
        if (attributes.isEmpty) {
            None
        } else {
            Some(attributes.mkString(";"))
        }
    }

}

/**
  * Represents SPARQL results to be converted into [[ApiValueV1]] objects.
  */
object GroupedProps {

    /**
      * Contains the three types of [[GroupedProperties]] returned by a SPARQL query.
      *
      * @param groupedOrdinaryValueProperties properties pointing to ordinary Knora values (i.e. not link values).
      * @param groupedLinkValueProperties     properties pointing to link value objects (reifications of links to resources).
      * @param groupedLinkProperties          properties pointing to resources.
      */
    case class GroupedPropertiesByType(groupedOrdinaryValueProperties: GroupedProperties, groupedLinkValueProperties: GroupedProperties, groupedLinkProperties: GroupedProperties)

    /**
      * Represents the grouped properties of one of the three types.
      *
      * @param groupedProperties The grouped properties: The Map's keys (IRI) consist of resource properties (e.g. http://www.knora.org/ontology/knora-base#hasComment).
      */
    case class GroupedProperties(groupedProperties: Map[IRI, ValueObjects])

    /**
      * Represents the value objects belonging to a resource property
      *
      * @param valueObjects The value objects: The Map's keys consist of value object Iris.
      */
    case class ValueObjects(valueObjects: Map[IRI, ValueProps])

    /**
      * Represents the object properties belonging to a value object
      *
      * @param literalData The value properties: The Map's keys (IRI) consist of value object properties (e.g. http://www.knora.org/ontology/knora-base#valueHasString).
      * @param standoff    Each Map in the List stands for one standoff node, its keys consist of standoff properties (e.g. http://www.knora.org/ontology/knora-base#standoffHasStart
      */
    case class ValueProps(literalData: Map[IRI, ValueLiterals], standoff: Seq[Map[IRI, String]] = Vector.empty[Map[IRI, String]])

    /**
      * Represents the literal values of a property (e.g. a number or a string)
      *
      * @param literals the literal values of a property.
      */
    case class ValueLiterals(literals: Seq[String])

}