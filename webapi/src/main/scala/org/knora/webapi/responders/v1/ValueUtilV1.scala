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
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.LocationV1
import org.knora.webapi.messages.v1.responder.standoffmessages.{GetMappingRequestV1, GetMappingResponseV1, StandoffProperties}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.VariableResultsRow
import org.knora.webapi.responders.v1.GroupedProps._
import org.knora.webapi.twirl._
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
    def makeValueV1(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val valueTypeIri = valueProps.literalData(OntologyConstants.Rdf.Type).literals.head

        valueTypeIri match {
            case OntologyConstants.KnoraBase.TextValue => makeTextValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.IntValue => makeIntValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.DecimalValue => makeDecimalValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.BooleanValue => makeBooleanValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.UriValue => makeUriValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.DateValue => makeDateValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.ColorValue => makeColorValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.GeomValue => makeGeomValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.GeonameValue => makeGeonameValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.ListValue => makeListValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.IntervalValue => makeIntervalValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.StillImageFileValue => makeStillImageValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.TextFileValue => makeTextFileValue(valueProps, responderManager, userProfile)
            case OntologyConstants.KnoraBase.LinkValue => makeLinkValue(valueProps, responderManager, userProfile)
        }
    }

    def makeSipiImagePreviewGetUrlFromFilename(filename: String): String = {
        s"${settings.sipiIIIFGetUrl}/$filename/full/full/0/default.jpg"
    }

    /**
      * Creates a IIIF URL for accessing an image file via Sipi.
      *
      * @param imageFileValueV1 the image file value representing the image.
      * @return a Sipi IIIF URL.
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

    /**
      * Creates a URL for accessing a text file via Sipi.
      *
      * @param textFileValue the text file value representing the text file.
      * @return a Sipi URL.
      */
    def makeSipiTextFileGetUrlFromFilename(textFileValue: TextFileValueV1): String = {
        s"${settings.sipieFileServerGetUrl}/${textFileValue.internalFilename}"
    }

    // A Map of MIME types to Knora API v1 binary format name.
    private val mimeType2V1Format = new ErrorHandlingMap(Map(// TODO: add mime types for text files that are supported by Sipi
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
            case textFileValue: TextFileValueV1 =>
                LocationV1(
                    format_name = mimeType2V1Format(textFileValue.internalMimeType),
                    origname = textFileValue.originalFilename,
                    path = makeSipiTextFileGetUrlFromFilename(textFileValue)
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
        settings.salsahBaseUrl + settings.salsahProjectIconsBasePath + ontologyName + '/' + iconsSrc
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

        val groupedValueObject = groupKnoraValueObjectPredicateRows(objRows.map(_.rowMap))

        ValueProps(valueIri, new ErrorHandlingMap(groupedValueObject.valuesLiterals, { key: IRI => s"Predicate $key not found in value $valueIri" }), groupedValueObject.standoff)
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
      * @param objRows a value object's predicates.
      * @return a [[GroupedValueObject]] containing the values (literal or linking) and standoff nodes if given.
      */
    private def groupKnoraValueObjectPredicateRows(objRows: Seq[Map[String, String]]): GroupedValueObject = {


        // get rid of the value object Iri `obj` and group by predicate Iri `objPred` (e.g. `valueHasString`)
        val valuesGroupedByPredicate = objRows.map(_ - "obj").groupBy(_ ("objPred"))

        valuesGroupedByPredicate.foldLeft(GroupedValueObject(valuesLiterals = Map.empty[String, ValueLiterals], standoff = Map.empty[IRI, Map[IRI, String]])) {
            case (acc: GroupedValueObject, (objPredIri: IRI, values: Seq[Map[String, String]])) =>

                if (objPredIri == OntologyConstants.KnoraBase.ValueHasStandoff) {
                    // standoff information

                    val groupedByStandoffNodeIri: Map[IRI, Seq[Map[String, String]]] = values.groupBy(_ ("objObj"))

                    val standoffNodeAssertions: Map[IRI, Map[String, String]] = groupedByStandoffNodeIri.map {
                        case (standoffNodeIri: IRI, values: Seq[Map[String, String]]) =>

                            val valuesMap: Map[String, String] = values.map {
                                // make a Map with the standoffPred as the key and the objStandoff as the value
                                value: Map[String, String] => Map(value("predStandoff") -> value("objStandoff"))
                            }.foldLeft(Map.empty[String, String]) {
                                // for each standoff node, we want to have just one Map
                                // this foldLeft turns a Sequence of Maps into one Map (a predicate can only occur once)
                                case (nodeValues: Map[String, String], (value: Map[String, String])) =>
                                    nodeValues ++ value
                            }

                            standoffNodeIri -> valuesMap

                    }

                    acc.copy(
                        standoff = acc.standoff ++ standoffNodeAssertions
                    )

                } else {
                    // non standoff value

                    val value: (String, ValueLiterals) = (objPredIri, ValueLiterals(values.map {
                        value: Map[String, String] => value("objObj")
                    }))

                    acc.copy(
                        valuesLiterals = acc.valuesLiterals + value
                    )

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
                        val groupedValueObject = groupKnoraValueObjectPredicateRows(objRows)

                        val vp: ValueProps = ValueProps(
                            valueIri = objIri,
                            new ErrorHandlingMap(groupedValueObject.valuesLiterals, { key: IRI => s"Predicate $key not found for property object $objIri" }),
                            groupedValueObject.standoff
                        )

                        (objIri, vp)
                })

                (resProp, GroupedProps.ValueObjects(vo._2))
        }

        GroupedProps.GroupedProperties(gp)
    }

    /**
      * Converts a [[ValueProps]] into an [[IntegerValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return an [[IntegerValueV1]].
      */
    private def makeIntValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(IntegerValueV1(predicates(OntologyConstants.KnoraBase.ValueHasInteger).literals.head.toInt))
    }

    /**
      * Converts a [[ValueProps]] into a [[DecimalValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[DecimalValueV1]].
      */
    private def makeDecimalValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(DecimalValueV1(BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasDecimal).literals.head)))
    }

    /**
      * Converts a [[ValueProps]] into a [[BooleanValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[BooleanValueV1]].
      */
    private def makeBooleanValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(BooleanValueV1(predicates(OntologyConstants.KnoraBase.ValueHasBoolean).literals.head.toBoolean))
    }

    /**
      * Converts a [[ValueProps]] into a [[UriValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[UriValueV1]].
      */
    private def makeUriValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(UriValueV1(predicates(OntologyConstants.KnoraBase.ValueHasUri).literals.head))
    }

    /**
      * Converts a [[ValueProps]] into a [[DateValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[DateValueV1]].
      */
    private def makeDateValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        val julianDayNumberValueV1 = JulianDayNumberValueV1(
            dateval1 = predicates(OntologyConstants.KnoraBase.ValueHasStartJDN).literals.head.toInt,
            dateval2 = predicates(OntologyConstants.KnoraBase.ValueHasEndJDN).literals.head.toInt,
            dateprecision1 = KnoraPrecisionV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasStartPrecision).literals.head),
            dateprecision2 = KnoraPrecisionV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasEndPrecision).literals.head),
            calendar = KnoraCalendarV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasCalendar).literals.head)
        )

        Future(DateUtilV1.julianDayNumberValueV1ToDateValueV1(julianDayNumberValueV1))
    }

    /**
      * Converts a [[ValueProps]] into an [[IntervalValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[IntervalValueV1]].
      */
    private def makeIntervalValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(IntervalValueV1(
            timeval1 = BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasIntervalStart).literals.head),
            timeval2 = BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasIntervalEnd).literals.head)
        ))
    }

    /**
      * Creates a [[TextValueWithStandoffV1]] from the given string and the standoff nodes.
      *
      * @param utf8str          the string representation.
      * @param valueProps       the properties of the TextValue with standoff.
      * @param responderManager the responder manager.
      * @param userProfile      the client that is making the request.
      * @return a [[TextValueWithStandoffV1]].
      */
    private def makeTextValueWithStandoff(utf8str: String, valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TextValueWithStandoffV1] = {

        // get the Iri of the mapping
        val mappingIri = valueProps.literalData.getOrElse(OntologyConstants.KnoraBase.ValueHasMapping, throw InconsistentTriplestoreDataException(s"no mapping Iri associated with standoff belonging to textValue ${valueProps.valueIri}")).literals.head

        for {

        // get the mapping and the related standoff entities
            mappingResponse: GetMappingResponseV1 <- (responderManager ? GetMappingRequestV1(mappingIri = mappingIri, userProfile = userProfile)).mapTo[GetMappingResponseV1]

            standoffTags: Seq[StandoffTagV1] = valueProps.standoff.values.map {

                (standoffInfo: Map[IRI, String]) =>

                    // create a sequence of `StandoffTagAttributeV1` from the given attributes
                    val attributes: Seq[StandoffTagAttributeV1] = (standoffInfo -- StandoffProperties.systemProperties - OntologyConstants.Rdf.Type).map {
                        case (propIri, value) =>

                            // check if the given property has an object type constraint (linking property) or an object data type constraint
                            if (mappingResponse.standoffEntities.standoffPropertyEntityInfoMap(propIri).predicates.get(OntologyConstants.KnoraBase.ObjectClassConstraint).isDefined) {

                                // it is a linking property
                                // check if it refers to a resource or a standoff node

                                if (mappingResponse.standoffEntities.standoffPropertyEntityInfoMap(propIri).isSubPropertyOf.contains(OntologyConstants.KnoraBase.StandoffTagHasInternalReference)) {
                                    // it refers to a standoff node, recreate the original id

                                    // value points to another standoff node
                                    // get this standoff node and access its original id
                                    val originalId: String = valueProps.standoff(value).getOrElse(OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID, throw InconsistentTriplestoreDataException(s"referred standoff $value node has no original XML id"))

                                    // recreate the original id reference
                                    StandoffTagStringAttributeV1(standoffPropertyIri = propIri, value = "#" + originalId)
                                } else {
                                    // it refers to a knora resource
                                    StandoffTagIriAttributeV1(standoffPropertyIri = propIri, value = value)
                                }
                            } else if (mappingResponse.standoffEntities.standoffPropertyEntityInfoMap(propIri).predicates.get(OntologyConstants.KnoraBase.ObjectDatatypeConstraint).isDefined) {

                                // it is a data type property (literal)
                                val propDataType = mappingResponse.standoffEntities.standoffPropertyEntityInfoMap(propIri).predicates(OntologyConstants.KnoraBase.ObjectDatatypeConstraint)

                                propDataType.objects.headOption match {
                                    case Some(OntologyConstants.Xsd.String) =>
                                        StandoffTagStringAttributeV1(standoffPropertyIri = propIri, value = value)

                                    case Some(OntologyConstants.Xsd.Integer) =>
                                        StandoffTagIntegerAttributeV1(standoffPropertyIri = propIri, value = value.toInt)

                                    case Some(OntologyConstants.Xsd.Decimal) =>
                                        StandoffTagDecimalAttributeV1(standoffPropertyIri = propIri, value = BigDecimal(value))

                                    case Some(OntologyConstants.Xsd.Boolean) =>
                                        StandoffTagBooleanAttributeV1(standoffPropertyIri = propIri, value = value.toBoolean)

                                    case Some(OntologyConstants.Xsd.Uri) => StandoffTagIriAttributeV1(standoffPropertyIri = propIri, value = value)

                                    case None => throw InconsistentTriplestoreDataException(s"did not find ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} for $propIri")

                                    case other => throw InconsistentTriplestoreDataException(s"triplestore returned unknown ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} '$other' for $propIri")

                                }
                            } else {
                                throw InconsistentTriplestoreDataException(s"no object class or data type constraint found for property '$propIri'")
                            }

                    }.toVector

                    StandoffTagV1(
                        standoffTagClassIri = standoffInfo(OntologyConstants.Rdf.Type),
                        startPosition = standoffInfo(OntologyConstants.KnoraBase.StandoffTagHasStart).toInt,
                        endPosition = standoffInfo(OntologyConstants.KnoraBase.StandoffTagHasEnd).toInt,
                        dataType = mappingResponse.standoffEntities.standoffClassEntityInfoMap(standoffInfo(OntologyConstants.Rdf.Type)).dataType,
                        startIndex = standoffInfo(OntologyConstants.KnoraBase.StandoffTagHasStartIndex).toInt,
                        endIndex = standoffInfo.get(OntologyConstants.KnoraBase.StandoffTagHasEndIndex) match {
                            case Some(endIndex: String) => Some(endIndex.toInt)
                            case None => None
                        },
                        uuid = standoffInfo(OntologyConstants.KnoraBase.StandoffTagHasUUID),
                        originalXMLID = standoffInfo.get(OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID),
                        startParentIndex = standoffInfo.get(OntologyConstants.KnoraBase.StandoffTagHasStartParentIndex) match {
                            case Some(startParentIndex: String) => Some(startParentIndex.toInt)
                            case None => None
                        },
                        endParentIndex = standoffInfo.get(OntologyConstants.KnoraBase.StandoffTagHasEndParentIndex) match {
                            case Some(endParentIndex: String) => Some(endParentIndex.toInt)
                            case None => None
                        },
                        attributes = attributes
                    )

            }.toVector

        } yield TextValueWithStandoffV1(
            utf8str = utf8str,
            standoff = standoffTags,
            mappingIri = mappingIri,
            mapping = mappingResponse.mapping,
            resource_reference = InputValidation.getResourceIrisFromStandoffTags(standoffTags)
        )

    }

    /**
      * Creates a [[TextValueSimpleV1]] from the given string.
      *
      * @param utf8str the string representation of the TextValue.
      * @return a [[TextValueSimpleV1]].
      */
    private def makeTextValueSimple(utf8str: String)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TextValueSimpleV1] = {
        Future(TextValueSimpleV1(
            utf8str = utf8str
        ))
    }

    /**
      * Converts a [[ValueProps]] into a [[TextValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[TextValueV1]].
      */
    private def makeTextValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {


        val valueHasString = valueProps.literalData.get(OntologyConstants.KnoraBase.ValueHasString).map(_.literals.head).getOrElse(throw InconsistentTriplestoreDataException(s"Value ${valueProps.valueIri} has no knora-base:valueHasString"))

        if (valueProps.standoff.nonEmpty) {
            // there is standoff markup
            makeTextValueWithStandoff(valueHasString, valueProps, responderManager, userProfile)

        } else {
            // there is no standoff markup
            makeTextValueSimple(valueHasString)

        }
    }

    /**
      * Converts a [[ValueProps]] into a [[ColorValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[ColorValueV1]].
      */
    private def makeColorValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(ColorValueV1(predicates(OntologyConstants.KnoraBase.ValueHasColor).literals.head))
    }

    /**
      * Converts a [[ValueProps]] into a [[GeomValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[GeomValueV1]].
      */
    private def makeGeomValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(GeomValueV1(predicates(OntologyConstants.KnoraBase.ValueHasGeometry).literals.head))
    }

    /**
      * Converts a [[ValueProps]] into a [[HierarchicalListValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[HierarchicalListValueV1]].
      */
    private def makeListValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(HierarchicalListValueV1(predicates(OntologyConstants.KnoraBase.ValueHasListNode).literals.head))
    }

    /**
      * Converts a [[ValueProps]] into a [[StillImageFileValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[StillImageFileValueV1]].
      */
    private def makeStillImageValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(StillImageFileValueV1(
            internalMimeType = predicates(OntologyConstants.KnoraBase.InternalMimeType).literals.head,
            internalFilename = predicates(OntologyConstants.KnoraBase.InternalFilename).literals.head,
            originalFilename = predicates(OntologyConstants.KnoraBase.OriginalFilename).literals.head,
            dimX = predicates(OntologyConstants.KnoraBase.DimX).literals.head.toInt,
            dimY = predicates(OntologyConstants.KnoraBase.DimY).literals.head.toInt,
            qualityLevel = predicates(OntologyConstants.KnoraBase.QualityLevel).literals.head.toInt,
            isPreview = InputValidation.optionStringToBoolean(predicates.get(OntologyConstants.KnoraBase.IsPreview).flatMap(_.literals.headOption))
        ))
    }

    /**
      * Converts a [[ValueProps]] into a [[TextFileValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[TextFileValueV1]].
      */
    private def makeTextFileValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(TextFileValueV1(
            internalMimeType = predicates(OntologyConstants.KnoraBase.InternalMimeType).literals.head,
            internalFilename = predicates(OntologyConstants.KnoraBase.InternalFilename).literals.head,
            originalFilename = predicates(OntologyConstants.KnoraBase.OriginalFilename).literals.head
        ))
    }

    /**
      * Converts a [[ValueProps]] into a [[LinkValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[LinkValueV1]].
      */
    private def makeLinkValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(LinkValueV1(
            subjectIri = predicates(OntologyConstants.Rdf.Subject).literals.head,
            predicateIri = predicates(OntologyConstants.Rdf.Predicate).literals.head,
            objectIri = predicates(OntologyConstants.Rdf.Object).literals.head,
            referenceCount = predicates(OntologyConstants.KnoraBase.ValueHasRefCount).literals.head.toInt
        ))
    }

    /**
      * Converts a [[ValueProps]] into a [[GeonameValueV1]].
      *
      * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
      * @return a [[GeonameValueV1]].
      */
    private def makeGeonameValue(valueProps: ValueProps, responderManager: ActorSelection, userProfile: UserProfileV1)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
        val predicates = valueProps.literalData

        Future(GeonameValueV1(predicates(OntologyConstants.KnoraBase.ValueHasGeonameCode).literals.head))
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
            Some(attributes.toVector.sorted.mkString(";"))
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
      * Represents the grouped values of a value object.
      *
      * @param valuesLiterals the values (literal or linking).
      * @param standoff       standoff nodes, if any.
      */
    case class GroupedValueObject(valuesLiterals: Map[String, ValueLiterals], standoff: Map[IRI, Map[IRI, String]])

    /**
      * Represents the object properties belonging to a value object
      *
      * @param valueIri    the IRI of the value object.
      * @param literalData the value properties: The Map's keys (IRI) consist of value object properties (e.g. http://www.knora.org/ontology/knora-base#String).
      * @param standoff    the keys of the first Map are the standoff node Iris, the second Map contains all the predicates and objects related to one standoff node.
      */
    case class ValueProps(valueIri: IRI, literalData: Map[IRI, ValueLiterals], standoff: Map[IRI, Map[IRI, String]] = Map.empty[IRI, Map[IRI, String]])

    /**
      * Represents the literal values of a property (e.g. a number or a string)
      *
      * @param literals the literal values of a property.
      */
    case class ValueLiterals(literals: Seq[String])

}