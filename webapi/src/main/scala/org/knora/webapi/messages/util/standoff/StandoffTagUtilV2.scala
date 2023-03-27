/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import com.typesafe.scalalogging.Logger
import zio.Task
import zio.URLayer
import zio.ZLayer

import java.time.Instant
import java.util.UUID

import dsp.errors._
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.util.DateUtilV1
import org.knora.webapi.messages.v1.responder.valuemessages.JulianDayNumberValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraCalendarV1
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraPrecisionV1
import org.knora.webapi.messages.v1.responder.valuemessages.UpdateValueV1
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality._
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.slice.ontology.domain.model.Cardinality.AtLeastOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ExactlyOne
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.messages.ValuesValidator

trait StandoffTagUtilV2 {

  /**
   * Creates a sequence of [[StandoffTagV2]] from the given standoff nodes resulting from a SPARQL CONSTRUCT query.
   *
   * @param standoffAssertions standoff assertions to be converted into [[StandoffTagV2]] objects.
   * @return a sequence of [[StandoffTagV2]] objects.
   */
  def createStandoffTagsV2FromConstructResults(
    standoffAssertions: Map[IRI, Map[SmartIri, LiteralV2]],
    requestingUser: UserADM
  ): Task[Vector[StandoffTagV2]]

  /**
   * Creates a sequence of [[StandoffTagV2]] from the given standoff nodes resulting from a SPARQL SELECT query.
   *
   * @param standoffAssertions standoff assertions to be converted into [[StandoffTagV2]] objects.
   * @return a sequence of [[StandoffTagV2]] objects.
   */
  def createStandoffTagsV2FromSelectResults(
    standoffAssertions: Map[IRI, Map[IRI, String]],
    requestingUser: UserADM
  ): Task[Vector[StandoffTagV2]]
}

final case class StandoffTagUtilV2Live(
  messageRelay: MessageRelay,
  implicit val stringFormatter: StringFormatter
) extends StandoffTagUtilV2 {

  /**
   * Creates a sequence of [[StandoffTagV2]] from the given standoff nodes resulting from a SPARQL CONSTRUCT query.
   *
   * @param standoffAssertions standoff assertions to be converted into [[StandoffTagV2]] objects.
   * @return a sequence of [[StandoffTagV2]] objects.
   */
  override def createStandoffTagsV2FromConstructResults(
    standoffAssertions: Map[IRI, Map[SmartIri, LiteralV2]],
    requestingUser: UserADM
  ): Task[Vector[StandoffTagV2]] = {
    val unwrappedStandoffAssertions: Map[IRI, Map[IRI, String]] = standoffAssertions.map {
      case (standoffTagIri, standoffTagAssertions: Map[SmartIri, LiteralV2]) =>
        standoffTagIri -> standoffTagAssertions.map { case (predicateIri: SmartIri, literal: LiteralV2) =>
          predicateIri.toString -> literal.toString
        }
    }
    createStandoffTagsV2FromSelectResults(unwrappedStandoffAssertions, requestingUser)
  }

  /**
   * Creates a sequence of [[StandoffTagV2]] from the given standoff nodes resulting from a SPARQL SELECT query.
   *
   * @param standoffAssertions standoff assertions to be converted into [[StandoffTagV2]] objects.
   * @return a sequence of [[StandoffTagV2]] objects.
   */
  override def createStandoffTagsV2FromSelectResults(
    standoffAssertions: Map[IRI, Map[IRI, String]],
    requestingUser: UserADM
  ): Task[Vector[StandoffTagV2]] = {

    val standoffClassIris: Set[SmartIri] = standoffAssertions.map { case (_, standoffTagAssertions: Map[IRI, String]) =>
      standoffTagAssertions(OntologyConstants.Rdf.Type).toSmartIri
    }.toSet

    val standoffPropertyIris: Set[SmartIri] = standoffAssertions.flatMap {
      case (_, standoffTagAssertions: Map[IRI, String]) =>
        (standoffTagAssertions.keySet - OntologyConstants.Rdf.Type).map(_.toSmartIri)
    }.toSet

    for {
      standoffEntities <- messageRelay
                            .ask[StandoffEntityInfoGetResponseV2](
                              StandoffEntityInfoGetRequestV2(
                                standoffClassIris = standoffClassIris,
                                standoffPropertyIris = standoffPropertyIris,
                                requestingUser = requestingUser
                              )
                            )

      standoffTags = standoffAssertions.map { case (standoffTagIri: IRI, standoffTagAssertions: Map[IRI, String]) =>
                       val standoffTagSmartIri: SmartIri = standoffTagIri.toSmartIri

                       if (!standoffTagSmartIri.isKnoraStandoffIri) {
                         throw InconsistentRepositoryDataException(s"Invalid standoff tag IRI: $standoffTagIri")
                       }

                       // The start index in the tag's IRI should match the one in its assertions.

                       val startIndexFromIri: Int = standoffTagSmartIri.getStandoffStartIndex.get
                       val startIndexFromAssertions: Int =
                         standoffTagAssertions(OntologyConstants.KnoraBase.StandoffTagHasStartIndex).toInt

                       if (startIndexFromAssertions != startIndexFromIri) {
                         throw InconsistentRepositoryDataException(
                           s"Standoff tag $standoffTagIri has start index $startIndexFromAssertions (expected $startIndexFromIri)"
                         )
                       }

                       // create a sequence of `StandoffTagAttributeV2` from the given attributes
                       val attributes: Seq[StandoffTagAttributeV2] =
                         (standoffTagAssertions -- StandoffProperties.systemProperties - OntologyConstants.Rdf.Type).map {
                           case (propIri: IRI, value) =>
                             val propSmartIri                                   = propIri.toSmartIri
                             val propDef: ReadPropertyInfoV2                    = standoffEntities.standoffPropertyInfoMap(propSmartIri)
                             val propPredicates: Map[SmartIri, PredicateInfoV2] = propDef.entityInfoContent.predicates

                             // check if the given property has an object type constraint (linking property) or an object data type constraint
                             if (
                               propPredicates.contains(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri)
                             ) {

                               // it is a linking property
                               // check if it refers to a resource or a standoff node

                               if (propDef.isStandoffInternalReferenceProperty) {
                                 // it refers to a standoff node, recreate the original id

                                 // value points to another standoff node

                                 // If a v2 SPARQL template was used, the XML ID is in this standoff tag.
                                 val originalId: String =
                                   standoffTagAssertions.get(OntologyConstants.KnoraBase.TargetHasOriginalXMLID) match {
                                     case Some(targetXmlID) => targetXmlID

                                     case None =>
                                       // If a v1 SPARQL template was used, we have to get the target node and to get its XML ID.
                                       standoffAssertions(value).getOrElse(
                                         OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID,
                                         throw InconsistentRepositoryDataException(
                                           s"referred standoff $value node has no original XML id"
                                         )
                                       )
                                   }

                                 // recreate the original id reference
                                 StandoffTagInternalReferenceAttributeV2(
                                   standoffPropertyIri = propSmartIri,
                                   value = originalId
                                 )
                               } else {
                                 // it refers to a knora resource
                                 StandoffTagIriAttributeV2(standoffPropertyIri = propSmartIri, value = value)
                               }
                             } else if (
                               propPredicates.contains(OntologyConstants.KnoraBase.ObjectDatatypeConstraint.toSmartIri)
                             ) {

                               // it is a data type property (literal)
                               val propDataType: PredicateInfoV2 =
                                 propPredicates(OntologyConstants.KnoraBase.ObjectDatatypeConstraint.toSmartIri)

                               propDataType.objects.headOption match {
                                 case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.String))) =>
                                   StandoffTagStringAttributeV2(standoffPropertyIri = propSmartIri, value = value)

                                 case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.Integer))) =>
                                   StandoffTagIntegerAttributeV2(
                                     standoffPropertyIri = propSmartIri,
                                     value = value.toInt
                                   )

                                 case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.Decimal))) =>
                                   StandoffTagDecimalAttributeV2(
                                     standoffPropertyIri = propSmartIri,
                                     value = BigDecimal(value)
                                   )

                                 case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.Boolean))) =>
                                   StandoffTagBooleanAttributeV2(
                                     standoffPropertyIri = propSmartIri,
                                     value = value.toBoolean
                                   )

                                 case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.DateTime))) |
                                     Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.DateTimeStamp))) =>
                                   val timeStamp = stringFormatter.xsdDateTimeStampToInstant(
                                     value,
                                     throw DataConversionException(s"Couldn't parse timestamp: $value")
                                   )
                                   StandoffTagTimeAttributeV2(standoffPropertyIri = propSmartIri, value = timeStamp)

                                 case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.Uri))) =>
                                   StandoffTagUriAttributeV2(standoffPropertyIri = propSmartIri, value = value)

                                 case None =>
                                   throw InconsistentRepositoryDataException(
                                     s"did not find ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} for $propIri"
                                   )

                                 case other =>
                                   throw InconsistentRepositoryDataException(
                                     s"triplestore returned unknown ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} '$other' for $propIri"
                                   )

                               }
                             } else {
                               throw InconsistentRepositoryDataException(
                                 s"no object class or data type constraint found for property '$propIri'"
                               )
                             }

                         }.toVector

                       StandoffTagV2(
                         standoffTagClassIri = standoffTagAssertions(OntologyConstants.Rdf.Type).toSmartIri,
                         startPosition = standoffTagAssertions(OntologyConstants.KnoraBase.StandoffTagHasStart).toInt,
                         endPosition = standoffTagAssertions(OntologyConstants.KnoraBase.StandoffTagHasEnd).toInt,
                         dataType = standoffEntities
                           .standoffClassInfoMap(standoffTagAssertions(OntologyConstants.Rdf.Type).toSmartIri)
                           .standoffDataType,
                         startIndex = startIndexFromAssertions,
                         endIndex =
                           standoffTagAssertions.get(OntologyConstants.KnoraBase.StandoffTagHasEndIndex).map(_.toInt),
                         uuid = stringFormatter.decodeUuid(
                           standoffTagAssertions(OntologyConstants.KnoraBase.StandoffTagHasUUID)
                         ),
                         originalXMLID =
                           standoffTagAssertions.get(OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID),
                         startParentIndex = standoffTagAssertions
                           .get(OntologyConstants.KnoraBase.StandoffTagHasStartParent)
                           .flatMap(_.toSmartIri.getStandoffStartIndex),
                         endParentIndex = standoffTagAssertions
                           .get(OntologyConstants.KnoraBase.StandoffTagHasEndParent)
                           .flatMap(_.toSmartIri.getStandoffStartIndex),
                         attributes = attributes
                       )
                     }.toVector
                       .sortBy(_.startIndex)
    } yield standoffTags
  }
}

object StandoffTagUtilV2Live {
  val layer: URLayer[MessageRelay with StringFormatter, StandoffTagUtilV2] =
    ZLayer.fromFunction(StandoffTagUtilV2Live.apply _)
}

object StandoffTagUtilV2 {

  // string constant used to mark the absence of an XML namespace in the mapping definition of an XML element
  private val noNamespace = "noNamespace"

  // string constant used to mark the absence of a classname in the mapping definition of an XML element
  private val noClass = "noClass"

  // name of class attribute (used to combine elements and classes in mappings)
  private val classAttribute = "class"

  // an internal Link in an XML document begins with this character
  private val internalLinkMarker = '#'

  // A fixed UUID for comparing standoff tags when their UUIDs are not significant.
  private val fixedUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")

  /**
   * Tries to find a data type attribute in the XML attributes of a given standoff node. Throws an appropriate error if information is inconsistent or missing.
   *
   * @param xmlToStandoffMapping the mapping from XML to standoff classes and properties for the given standoff node.
   * @param dataType             the expected data type of the given standoff node.
   * @param standoffNodeFromXML  the given standoff node.
   * @return the value of the attribute.
   */
  private def getDataTypeAttribute(
    xmlToStandoffMapping: XMLTagToStandoffClass,
    dataType: StandoffDataTypeClasses.Value,
    standoffNodeFromXML: StandoffTag
  ): String = {

    if (xmlToStandoffMapping.dataType.isEmpty || xmlToStandoffMapping.dataType.get.standoffDataTypeClass != dataType) {
      throw BadRequestException(
        s"no or wrong data type definition provided in mapping for standoff class ${xmlToStandoffMapping.standoffClassIri}"
      )
    }

    val attrName = xmlToStandoffMapping.dataType.get.dataTypeXMLAttribute

    val attrStringOption: Option[StandoffTagAttribute] =
      standoffNodeFromXML.attributes.find(attr => attr.key == attrName)

    if (attrStringOption.isEmpty) {
      throw BadRequestException(s"required data type attribute '$attrName' could not be found for a $dataType value")
    } else {
      attrStringOption.get.value
    }

  }

  /**
   * Creates a sequence of [[StandoffTagAttributeV2]] for the given standoff node.
   *
   * @param xmlToStandoffMapping     the mapping from XML to standoff classes and properties for the given standoff node.
   * @param classSpecificProps       the properties that may or have to be created (cardinalities) for the given standoff node.
   * @param standoffNodeFromXML      the given standoff node.
   * @param standoffPropertyEntities the ontology information about the standoff properties.
   * @return a sequence of [[StandoffTagAttributeV2]]
   */
  private def createAttributes(
    xmlToStandoffMapping: XMLTagToStandoffClass,
    classSpecificProps: Map[SmartIri, KnoraCardinalityInfo],
    standoffNodeFromXML: StandoffTag,
    standoffPropertyEntities: Map[SmartIri, ReadPropertyInfoV2]
  ): Seq[StandoffTagAttributeV2] = {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (classSpecificProps.nonEmpty) {
      // this standoff class requires additional standoff properties to the standoff data type properties (contained in `StandoffProperties.dataTypeProperties`).

      // map over all non data type attributes, ignore the "class" attribute ("class" is only used in the mapping to allow for the reuse of the same tag name, not to store actual data).
      val attrs: Seq[StandoffTagAttributeV2] = standoffNodeFromXML.attributes
        .filterNot(attr =>
          (xmlToStandoffMapping.dataType.nonEmpty && xmlToStandoffMapping.dataType.get.dataTypeXMLAttribute == attr.key) || attr.key == classAttribute
        )
        .map { attr: StandoffTagAttribute =>
          // get the standoff property IRI for this XML attribute

          val xmlNamespace = attr.xmlNamespace match {
            case None            => noNamespace
            case Some(namespace) => namespace
          }

          val standoffTagPropIri: SmartIri = xmlToStandoffMapping.attributesToProps
            .getOrElse(
              xmlNamespace,
              throw BadRequestException(s"namespace $xmlNamespace unknown for attribute ${attr.key} in mapping")
            )
            .getOrElse(attr.key, throw BadRequestException(s"mapping for attr '${attr.key}' not provided"))
            .toSmartIri
          val propPredicates: Map[SmartIri, PredicateInfoV2] =
            standoffPropertyEntities(standoffTagPropIri).entityInfoContent.predicates

          // check if a cardinality exists for the current attribute
          if (!classSpecificProps.contains(standoffTagPropIri)) {
            throw BadRequestException(s"no cardinality defined for attr '${attr.key}'")
          }

          if (propPredicates.contains(OntologyConstants.KnoraBase.ObjectDatatypeConstraint.toSmartIri)) {
            // property is a data type property

            val propDatatypeConstraint = propPredicates(OntologyConstants.KnoraBase.ObjectDatatypeConstraint.toSmartIri)

            propDatatypeConstraint.objects.headOption match {
              case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.String))) =>
                StandoffTagStringAttributeV2(
                  standoffPropertyIri = standoffTagPropIri,
                  value = stringFormatter.toSparqlEncodedString(
                    attr.value,
                    throw BadRequestException(s"Invalid string attribute: '${attr.value}'")
                  )
                )

              case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.Integer))) =>
                StandoffTagIntegerAttributeV2(
                  standoffPropertyIri = standoffTagPropIri,
                  value = ValuesValidator
                    .validateInt(attr.value)
                    .getOrElse(throw BadRequestException(s"Invalid integer attribute: '${attr.value}'"))
                )

              case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.Decimal))) =>
                StandoffTagDecimalAttributeV2(
                  standoffPropertyIri = standoffTagPropIri,
                  value = ValuesValidator
                    .validateBigDecimal(attr.value)
                    .getOrElse(
                      throw BadRequestException(s"Invalid decimal attribute: '${attr.value}'")
                    )
                )

              case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.Boolean))) =>
                StandoffTagBooleanAttributeV2(
                  standoffPropertyIri = standoffTagPropIri,
                  value = stringFormatter.validateBoolean(
                    attr.value,
                    throw BadRequestException(s"Invalid boolean attribute: '${attr.value}'")
                  )
                )

              case Some(SmartIriLiteralV2(SmartIri(OntologyConstants.Xsd.DateTime))) =>
                StandoffTagTimeAttributeV2(
                  standoffPropertyIri = standoffTagPropIri,
                  value = stringFormatter.xsdDateTimeStampToInstant(
                    attr.value,
                    throw BadRequestException(s"Invalid timestamp attribute: '${attr.value}'")
                  )
                )

              case None =>
                throw InconsistentRepositoryDataException(
                  s"did not find ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} for $standoffTagPropIri"
                )

              case other =>
                throw InconsistentRepositoryDataException(
                  s"triplestore returned unknown ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} '$other' for $standoffTagPropIri"
                )

            }
          } else {
            // only properties with a `ObjectDatatypeConstraint` are allowed here (linking properties have to be created via data type standoff classes)
            throw InconsistentRepositoryDataException(
              s"no ${OntologyConstants.KnoraBase.ObjectDatatypeConstraint} given for property '$standoffTagPropIri'"
            )
          }

        }
        .toList

      val attrsGroupedByPropIri: Map[SmartIri, Seq[StandoffTagAttributeV2]] =
        attrs.groupBy(attr => attr.standoffPropertyIri)

      // filter all the required props
      val mustExistOnce: Set[SmartIri] = classSpecificProps.filter { case (_, card) =>
        card.cardinality == ExactlyOne || card.cardinality == AtLeastOne
      }.keySet

      // check if all the min cardinalities are respected
      mustExistOnce.foreach { propIri =>
        attrsGroupedByPropIri.get(propIri) match {
          case Some(_) => ()
          case None =>
            throw BadRequestException(
              s"the min cardinalities were not respected for the property $propIri (missing attribute for element ${standoffNodeFromXML.tagName})"
            )
        }
      }

      // filter all the props that have a limited occurrence
      val mayExistOnce = classSpecificProps.filter { case (_, card) =>
        card.cardinality == ExactlyOne || card.cardinality == ZeroOrOne
      }.keySet

      // check if all the max cardinalities are respected
      mayExistOnce.foreach { propIri =>
        attrsGroupedByPropIri.get(propIri) match {
          case Some(attrs: Seq[StandoffTagAttributeV2]) =>
            if (attrs.size > 1) {
              throw BadRequestException(
                s"the max cardinalities were not respected for $propIri (for element ${standoffNodeFromXML.tagName})"
              )
            }
          case None => ()
        }
      }

      attrs

    } else {
      // possibly data type properties are required, but no other standoff properties

      // check that there no other attributes than data type attributes and 'class'
      val unsupportedAttributes: Set[String] = standoffNodeFromXML.attributes.filterNot { attr =>
        (xmlToStandoffMapping.dataType.nonEmpty && xmlToStandoffMapping.dataType.get.dataTypeXMLAttribute == attr.key) || attr.key == classAttribute
      }
        .map(attr => attr.key)

      if (unsupportedAttributes.nonEmpty)
        throw BadRequestException(
          s"Attributes found that are not defined in the mapping: ${unsupportedAttributes.mkString(", ")}"
        )

      Seq.empty[StandoffTagAttributeV2]
    }

  }

  /**
   * Represents a text with standoff markup including the mapping.
   *
   * @param text          the text as a mere sequence of characters.
   * @param language      the language of the text, if known.
   * @param standoffTagV2 the text's standoff markup.
   */
  case class TextWithStandoffTagsV2(
    text: String,
    language: Option[String] = None,
    standoffTagV2: Seq[StandoffTagV2],
    mapping: GetMappingResponseV2
  )

  /**
   * Converts XML to a [[TextWithStandoffTagsV2]].
   *
   * @param xml                            the XML representing text with markup.
   * @param mapping                        the mapping used to convert XML to standoff.
   * @param acceptStandoffLinksToClientIDs if `true`, allow standoff link tags to use the client's IDs for target
   *                                       resources. In a bulk import, this allows standoff links to resources
   *                                       that are to be created by the import.
   * @return a [[TextWithStandoffTagsV2]].
   */
  def convertXMLtoStandoffTagV2(
    xml: String,
    mapping: GetMappingResponseV2,
    acceptStandoffLinksToClientIDs: Boolean,
    log: Logger
  ): TextWithStandoffTagsV2 = {

    // collect all the `XMLTag` from the given mapping that require a separator
    // and create a `XMLTagSeparatorRequired` for each of them
    //
    // namespace = Map("myXMLNamespace" -> Map("myXMLTagName" -> Map("myXMLClassname" -> XMLTag(...))))
    val elementsSeparatorRequired: Vector[XMLTagSeparatorRequired] = mapping.mapping.namespace.flatMap {
      case (namespace: String, elesForNamespace: Map[String, Map[String, XMLTag]]) =>
        elesForNamespace.flatMap { case (_, classWithTag: Map[String, XMLTag]) =>
          val tagsWithSeparator: Iterable[XMLTagSeparatorRequired] = classWithTag.filter {
            // filter out all `XMLTag` that require a separator
            case (_, tag: XMLTag) =>
              tag.separatorRequired
          }.map { case (classname: String, tag: XMLTag) =>
            // create a `XMLTagSeparatorRequired` with the current's element
            // namespace and class, if any
            XMLTagSeparatorRequired(
              maybeNamespace = namespace match {
                // check for namespace
                case `noNamespace`  => None
                case nspace: String => Some(nspace)
              },
              tagname = tag.name,
              maybeClassname = classname match {
                // check for classname
                case `noClass`      => None
                case clname: String => Some(clname)
              }
            )
          }
          tagsWithSeparator
        }
    }.toVector

    val xmlStandoffUtil                    = new XMLToStandoffUtil()
    val textWithStandoff: TextWithStandoff = xmlStandoffUtil.xml2TextWithStandoff(xml, elementsSeparatorRequired, log)

    val standoffTagsV2: Seq[StandoffTagV2] = convertXMLStandoffTagToStandoffTagV2(
      textWithStandoff = textWithStandoff,
      mappingXMLtoStandoff = mapping.mapping,
      standoffEntities = mapping.standoffEntities,
      acceptStandoffLinksToClientIDs = acceptStandoffLinksToClientIDs
    )

    TextWithStandoffTagsV2(
      text = textWithStandoff.text,
      standoffTagV2 = standoffTagsV2,
      mapping = mapping
    )
  }

  /**
   * Turns a sequence of [[StandoffTag]] returned by [[XMLToStandoffUtil.xml2TextWithStandoff]] into a sequence of [[StandoffTagV2]].
   * This method handles the creation of data type specific properties (e.g. for a date value) on the basis of the provided mapping.
   *
   * @param textWithStandoff               sequence of [[StandoffTag]] returned by [[XMLToStandoffUtil.xml2TextWithStandoff]].
   * @param mappingXMLtoStandoff           the mapping to be used.
   * @param standoffEntities               the standoff entities (classes and properties) to be used.
   * @param acceptStandoffLinksToClientIDs if `true`, allow standoff link tags to use the client's IDs for target
   *                                       resources. In a bulk import, this allows standoff links to resources
   *                                       that are to be created by the import.
   * @return a sequence of [[StandoffTagV2]].
   */
  private def convertXMLStandoffTagToStandoffTagV2(
    textWithStandoff: TextWithStandoff,
    mappingXMLtoStandoff: MappingXMLtoStandoff,
    standoffEntities: StandoffEntityInfoGetResponseV2,
    acceptStandoffLinksToClientIDs: Boolean
  ): Seq[StandoffTagV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // collect al the existing ids from the standoff tags
    val existingXMLIDs: Seq[String] =
      textWithStandoff.standoff.filter((standoffTag: StandoffTag) => standoffTag.originalID.isDefined).map {
        standoffTagWithID =>
          standoffTagWithID.originalID.get
      }

    // get the id of an XML target element from an internal reference
    // assumes that an internal references starts with a "#"
    def getTargetIDFromInternalReference(internalReference: String): String = {
      // make sure that the internal reference starts with a '#'
      if (internalReference.charAt(0) != internalLinkMarker)
        throw BadRequestException(
          s"invalid internal reference (should start with a $internalLinkMarker): '$internalReference'"
        )

      val refTarget = internalReference.substring(1)

      // make sure that he target of the reference exists in this context
      if (!existingXMLIDs.contains(refTarget))
        throw BadRequestException(s"invalid internal reference: target '$internalReference' unknown")

      refTarget

    }

    textWithStandoff.standoff.map { standoffNodeFromXML: StandoffTag =>
      val xmlNamespace = standoffNodeFromXML.xmlNamespace match {
        case None            => noNamespace
        case Some(namespace) => namespace
      }

      val classname: String = standoffNodeFromXML.attributes.find(_.key == classAttribute) match {
        case None                                       => noClass
        case Some(classAttribute: StandoffTagAttribute) => classAttribute.value
      }

      // get the mapping corresponding to the given namespace and tagname
      val standoffDefFromMapping = mappingXMLtoStandoff.namespace
        .getOrElse(xmlNamespace, throw BadRequestException(s"namespace $xmlNamespace not defined in mapping"))
        .getOrElse(
          standoffNodeFromXML.tagName,
          throw BadRequestException(
            s"the standoff class for the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping"
          )
        )
        .getOrElse(
          classname,
          throw BadRequestException(
            s"the standoff class for the classname $classname in combination with the tag '${standoffNodeFromXML.tagName}' could not be found in the provided mapping"
          )
        )
        .mapping

      val standoffClassIri: SmartIri = standoffDefFromMapping.standoffClassIri.toSmartIri

      // get the cardinalities of the current standoff class
      val cardinalities: Map[SmartIri, KnoraCardinalityInfo] = standoffEntities.standoffClassInfoMap
        .getOrElse(
          standoffClassIri,
          throw NotFoundException(s"information about standoff class $standoffClassIri was not found in ontology")
        )
        .allCardinalities

      // create a standoff base tag with the information available from standoff util
      val standoffBaseTagV2: StandoffTagV2 = standoffNodeFromXML match {
        case hierarchicalStandoffTag: HierarchicalStandoffTag =>
          StandoffTagV2(
            standoffTagClassIri = standoffClassIri,
            startPosition = hierarchicalStandoffTag.startPosition,
            endPosition = hierarchicalStandoffTag.endPosition,
            uuid = hierarchicalStandoffTag.uuid,
            originalXMLID = hierarchicalStandoffTag.originalID match {
              case Some(id: String) =>
                Some(
                  stringFormatter.toSparqlEncodedString(
                    id,
                    throw BadRequestException(s"XML id $id cannot be converted to a Sparql conform string")
                  )
                )
              case None => None
            },
            startIndex = hierarchicalStandoffTag.index,
            endIndex = None,
            startParentIndex = hierarchicalStandoffTag.parentIndex,
            endParentIndex = None,
            attributes = Seq.empty[StandoffTagAttributeV2]
          )
        case freeStandoffTag: FreeStandoffTag =>
          StandoffTagV2(
            standoffTagClassIri = standoffClassIri,
            startPosition = freeStandoffTag.startPosition,
            endPosition = freeStandoffTag.endPosition,
            uuid = freeStandoffTag.uuid,
            originalXMLID = freeStandoffTag.originalID match {
              case Some(id: String) =>
                Some(
                  stringFormatter.toSparqlEncodedString(
                    id,
                    throw BadRequestException(s"XML id $id cannot be converted to a Sparql conform string")
                  )
                )
              case None => None
            },
            startIndex = freeStandoffTag.startIndex,
            endIndex = Some(freeStandoffTag.endIndex),
            startParentIndex = freeStandoffTag.startParentIndex,
            endParentIndex = freeStandoffTag.endParentIndex,
            attributes = Seq.empty[StandoffTagAttributeV2]
          )

        case _ =>
          throw InvalidStandoffException(
            "StandoffUtil did neither return a HierarchicalStandoff tag nor a FreeStandoffTag"
          )
      }

      // check the data type of the given standoff class
      standoffEntities.standoffClassInfoMap(standoffClassIri).standoffDataType match {

        case Some(StandoffDataTypeClasses.StandoffLinkTag) =>
          val linkString: String =
            getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffLinkTag, standoffNodeFromXML)

          val internalLink: StandoffTagAttributeV2 = StandoffTagIriAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
            value = stringFormatter.validateStandoffLinkResourceReference(
              linkString,
              acceptStandoffLinksToClientIDs,
              throw BadRequestException(s"Invalid standoff resource reference: $linkString")
            )
          )

          val classSpecificProps =
            cardinalities -- StandoffProperties.systemProperties.map(_.toSmartIri) -- StandoffProperties.linkProperties
              .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ internalLink
          )

        case Some(StandoffDataTypeClasses.StandoffInternalReferenceTag) =>
          val internalReferenceString: String =
            getDataTypeAttribute(
              standoffDefFromMapping,
              StandoffDataTypeClasses.StandoffInternalReferenceTag,
              standoffNodeFromXML
            )

          val internalReference = StandoffTagInternalReferenceAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasInternalReference.toSmartIri,
            value = getTargetIDFromInternalReference(internalReferenceString)
          )

          val classSpecificProps = cardinalities -- StandoffProperties.systemProperties.map(
            _.toSmartIri
          ) -- StandoffProperties.internalReferenceProperties
            .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffInternalReferenceTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ internalReference
          )

        case Some(StandoffDataTypeClasses.StandoffColorTag) =>
          val colorString: String =
            getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffColorTag, standoffNodeFromXML)

          val colorValue = StandoffTagStringAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasColor.toSmartIri,
            value =
              stringFormatter.validateColor(colorString, throw BadRequestException(s"Color invalid: $colorString"))
          )

          val classSpecificProps =
            cardinalities -- StandoffProperties.systemProperties.map(_.toSmartIri) -- StandoffProperties.colorProperties
              .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffColorTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ colorValue
          )

        case Some(StandoffDataTypeClasses.StandoffUriTag) =>
          val uriString: String =
            getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffUriTag, standoffNodeFromXML)

          val uriValue = StandoffTagUriAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasUri.toSmartIri,
            value =
              stringFormatter.validateAndEscapeIri(uriString, throw BadRequestException(s"URI invalid: $uriString"))
          )

          val classSpecificProps =
            cardinalities -- StandoffProperties.systemProperties.map(_.toSmartIri) -- StandoffProperties.uriProperties
              .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffUriTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ uriValue
          )

        case Some(StandoffDataTypeClasses.StandoffIntegerTag) =>
          val integerString: String = getDataTypeAttribute(
            standoffDefFromMapping,
            StandoffDataTypeClasses.StandoffIntegerTag,
            standoffNodeFromXML
          )

          val integerValue = StandoffTagIntegerAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasInteger.toSmartIri,
            value = ValuesValidator
              .validateInt(integerString)
              .getOrElse(throw BadRequestException(s"Integer value invalid: $integerString"))
          )

          val classSpecificProps = cardinalities -- StandoffProperties.systemProperties.map(
            _.toSmartIri
          ) -- StandoffProperties.integerProperties
            .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffIntegerTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ integerValue
          )

        case Some(StandoffDataTypeClasses.StandoffDecimalTag) =>
          val decimalString: String = getDataTypeAttribute(
            standoffDefFromMapping,
            StandoffDataTypeClasses.StandoffDecimalTag,
            standoffNodeFromXML
          )

          val decimalValue = StandoffTagDecimalAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasDecimal.toSmartIri,
            value = ValuesValidator
              .validateBigDecimal(decimalString)
              .getOrElse(
                throw BadRequestException(s"Decimal value invalid: $decimalString")
              )
          )

          val classSpecificProps = cardinalities -- StandoffProperties.systemProperties.map(
            _.toSmartIri
          ) -- StandoffProperties.decimalProperties
            .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffDecimalTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ decimalValue
          )

        case Some(StandoffDataTypeClasses.StandoffBooleanTag) =>
          val booleanString: String = getDataTypeAttribute(
            standoffDefFromMapping,
            StandoffDataTypeClasses.StandoffBooleanTag,
            standoffNodeFromXML
          )

          val booleanValue = StandoffTagBooleanAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasBoolean.toSmartIri,
            value = stringFormatter.validateBoolean(
              booleanString,
              throw BadRequestException(s"Boolean value invalid: $booleanString")
            )
          )

          val classSpecificProps = cardinalities -- StandoffProperties.systemProperties.map(
            _.toSmartIri
          ) -- StandoffProperties.booleanProperties
            .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffBooleanTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ booleanValue
          )

        case Some(StandoffDataTypeClasses.StandoffIntervalTag) =>
          val intervalString: String = getDataTypeAttribute(
            standoffDefFromMapping,
            StandoffDataTypeClasses.StandoffIntervalTag,
            standoffNodeFromXML
          )

          // interval String contains two decimals separated by a comma
          val interval: Array[String] = intervalString.split(",")
          if (interval.length != 2) {
            throw BadRequestException(
              s"interval string $intervalString is invalid, it should contain two decimals separated by a comma"
            )
          }

          val intervalStart = StandoffTagDecimalAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasIntervalStart.toSmartIri,
            value = ValuesValidator
              .validateBigDecimal(interval(0))
              .getOrElse(
                throw BadRequestException(s"Decimal value invalid: ${interval(0)}")
              )
          )

          val intervalEnd = StandoffTagDecimalAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasIntervalEnd.toSmartIri,
            value = ValuesValidator
              .validateBigDecimal(interval(1))
              .getOrElse(
                throw BadRequestException(s"Decimal value invalid: ${interval(1)}")
              )
          )

          val classSpecificProps = cardinalities -- StandoffProperties.systemProperties.map(
            _.toSmartIri
          ) -- StandoffProperties.intervalProperties
            .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffIntervalTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 ++ List(intervalStart, intervalEnd)
          )

        case Some(StandoffDataTypeClasses.StandoffTimeTag) =>
          val timeStampString: String =
            getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffTimeTag, standoffNodeFromXML)
          val timeStamp: Instant =
            stringFormatter.xsdDateTimeStampToInstant(
              timeStampString,
              throw BadRequestException(s"Invalid timestamp: $timeStampString")
            )
          val timeStampAttr = StandoffTagTimeAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasTimeStamp.toSmartIri,
            value = timeStamp
          )

          val classSpecificProps =
            cardinalities -- StandoffProperties.systemProperties.map(_.toSmartIri) -- StandoffProperties.timeProperties
              .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffTimeTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 :+ timeStampAttr
          )

        case Some(StandoffDataTypeClasses.StandoffDateTag) =>
          val dateString: String =
            getDataTypeAttribute(standoffDefFromMapping, StandoffDataTypeClasses.StandoffDateTag, standoffNodeFromXML)

          val dateValue = DateUtilV1.createJDNValueV1FromDateString(dateString)

          val dateCalendar = StandoffTagStringAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasCalendar.toSmartIri,
            value = dateValue.calendar.toString
          )

          val dateStart = StandoffTagIntegerAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasStartJDN.toSmartIri,
            value = dateValue.dateval1
          )

          val dateEnd = StandoffTagIntegerAttributeV2(
            standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasEndJDN.toSmartIri,
            value = dateValue.dateval2
          )

          val dateStartPrecision =
            StandoffTagStringAttributeV2(
              standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasStartPrecision.toSmartIri,
              value = dateValue.dateprecision1.toString
            )

          val dateEndPrecision =
            StandoffTagStringAttributeV2(
              standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasEndPrecision.toSmartIri,
              value = dateValue.dateprecision2.toString
            )

          val classSpecificProps =
            cardinalities -- StandoffProperties.systemProperties.map(_.toSmartIri) -- StandoffProperties.dateProperties
              .map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          StandoffTagV2(
            dataType = Some(StandoffDataTypeClasses.StandoffDateTag),
            standoffTagClassIri = standoffBaseTagV2.standoffTagClassIri,
            startPosition = standoffBaseTagV2.startPosition,
            endPosition = standoffBaseTagV2.endPosition,
            uuid = standoffBaseTagV2.uuid,
            originalXMLID = standoffBaseTagV2.originalXMLID,
            startIndex = standoffBaseTagV2.startIndex,
            endIndex = standoffBaseTagV2.endIndex,
            startParentIndex = standoffBaseTagV2.startParentIndex,
            endParentIndex = standoffBaseTagV2.endParentIndex,
            attributes = attributesV2 ++ List(dateCalendar, dateStart, dateEnd, dateStartPrecision, dateEndPrecision)
          )

        case None =>
          // ignore the system properties since they are provided by StandoffUtil
          val classSpecificProps: Map[SmartIri, KnoraCardinalityInfo] =
            cardinalities -- StandoffProperties.systemProperties.map(_.toSmartIri)

          val attributesV2: Seq[StandoffTagAttributeV2] = createAttributes(
            standoffDefFromMapping,
            classSpecificProps,
            standoffNodeFromXML,
            standoffEntities.standoffPropertyInfoMap
          )

          standoffBaseTagV2.copy(
            attributes = attributesV2
          )

        case unknownDataType =>
          throw InconsistentRepositoryDataException(
            s"the triplestore returned the data type $unknownDataType for $standoffClassIri that could be handled"
          )

      }
    }
  }

  // maps a standoff class to an XML tag with attributes
  case class XMLTagItem(
    namespace: String,
    tagname: String,
    classname: String,
    tagItem: XMLTag,
    attributes: Map[IRI, XMLAttrItem]
  )

  // maps a standoff property to XML attributes
  case class XMLAttrItem(namespace: String, attrname: String)

  /**
   * Inverts a [[MappingXMLtoStandoff]] and makes standoff class Iris keys.
   * This is makes it easier to map standoff classes back to XML tags (recreating XML from standoff).
   *
   * This method also checks for duplicate usage of standoff classes and properties in the attribute mapping of a tag.
   *
   * @param mappingXMLtoStandoff mapping from XML to standoff.
   * @return a Map standoff class Iris to [[XMLTagItem]].
   */
  def invertXMLToStandoffMapping(mappingXMLtoStandoff: MappingXMLtoStandoff): Map[IRI, XMLTagItem] = {

    // check for duplicate standoff class Iris
    val classIris: Iterable[IRI] = mappingXMLtoStandoff.namespace.values.flatten.flatMap {
      case (_, tagItem: Map[String, XMLTag]) =>
        tagItem.values.map(_.mapping.standoffClassIri)
    }

    // check for duplicate standoff class Iris
    if (classIris.size != classIris.toSet.size) {
      throw BadRequestException("the same standoff class IRI is used more than once in the mapping")
    }

    mappingXMLtoStandoff.namespace.flatMap {
      case (tagNamespace: String, tagMapping: Map[String, Map[String, XMLTag]]) =>
        tagMapping.flatMap { case (tagname: String, classnameMapping: Map[String, XMLTag]) =>
          classnameMapping.map { case (classname: String, tagItem: XMLTag) =>
            // collect all the property Iris defined in the attributes for the current tag
            // over all namespaces
            val propIris: Iterable[IRI] = tagItem.mapping.attributesToProps.values.flatten.map { case (_, propIri) =>
              propIri
            }

            // check for duplicate property Iris
            if (propIris.size != propIris.toSet.size) {
              throw BadRequestException(
                s"the same property IRI is used more than once for the attributes mapping for tag $tagname"
              )
            }

            // inverts the mapping and makes standoff property Iris keys (for attributes)
            // this is makes it easier to map standoff properties back to XML attributes
            val attrItems: Map[IRI, XMLAttrItem] = tagItem.mapping.attributesToProps.flatMap {
              case (attrNamespace: String, attrMappings: Map[String, IRI]) =>
                attrMappings.map { case (attrName, propIri) =>
                  propIri -> XMLAttrItem(attrNamespace, attrName)
                }
            }

            // standoff class IRI -> XMLTagItem(... attributes -> attrItems)
            tagItem.mapping.standoffClassIri -> XMLTagItem(
              namespace = tagNamespace,
              tagname = tagname,
              classname = classname,
              tagItem = tagItem,
              attributes = attrItems
            )
          }

        }

    }

  }

  /**
   * Converts a sequence of [[StandoffTagAttributeV2]] to a sequence of [[StandoffTagAttribute]].
   *
   * @param mapping    the mapping used to convert standoff property IRIs to XML attribute names.
   * @param attributes the standoff properties to be converted to XML attributes.
   * @return a sequence of [[StandoffTagAttribute]].
   */
  private def convertStandoffAttributeTags(
    mapping: Map[IRI, XMLAttrItem],
    attributes: Seq[StandoffTagAttributeV2]
  ): Seq[StandoffTagAttribute] =
    attributes.map { attr =>
      val attrItem: XMLAttrItem = mapping.getOrElse(
        attr.standoffPropertyIri.toString,
        throw NotFoundException(s"property IRI ${attr.standoffPropertyIri} could not be found in mapping")
      )

      StandoffTagAttribute(
        key = attrItem.attrname,
        xmlNamespace = attrItem.namespace match {
          case `noNamespace` => None
          case namespace     => Some(namespace)
        },
        value = attr.stringValue
      )
    }

  /**
   * Converts a text value with standoff to an XML String.
   *
   * @param utf8str              the string representation of the text value (`valueHasString`).
   * @param standoff             the standoff representing the markup.
   * @param mappingXMLtoStandoff the mapping used to convert standoff to XML markup.
   * @return a String representing an XML document.
   */
  def convertStandoffTagV2ToXML(
    utf8str: String,
    standoff: Seq[StandoffTagV2],
    mappingXMLtoStandoff: MappingXMLtoStandoff
  ): String = {

    // inverts the mapping and makes standoff class Iris keys (for tags)
    val mappingStandoffToXML: Map[IRI, XMLTagItem] = invertXMLToStandoffMapping(mappingXMLtoStandoff)

    val standoffUtil = new XMLToStandoffUtil(writeUuidsToXml = false)

    val standoffTags: Seq[StandoffTag] = standoff.map { standoffTagV2: StandoffTagV2 =>
      val xmlItemForStandoffClass: XMLTagItem = mappingStandoffToXML.getOrElse(
        standoffTagV2.standoffTagClassIri.toString,
        throw NotFoundException(s"standoff class IRI ${standoffTagV2.standoffTagClassIri} not found in mapping")
      )

      // recreate data type specific attributes (optional)
      val attributes: Seq[StandoffTagAttribute] = standoffTagV2.dataType match {

        case Some(StandoffDataTypeClasses.StandoffLinkTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val linkIri = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.StandoffTagHasLink)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.linkProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = linkIri, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffInternalReferenceTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val internalRefTarget = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.StandoffTagHasInternalReference)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.internalReferenceProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(
            key = dataTypeAttrName,
            value = StandoffTagUtilV2.internalLinkMarker.toString + internalRefTarget,
            xmlNamespace = None
          )

        case Some(StandoffDataTypeClasses.StandoffColorTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val colorString = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasColor)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.colorProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = colorString, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffUriTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val uriRef = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasUri)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.uriProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = uriRef, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffIntegerTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val integerString = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasInteger)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.integerProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = integerString, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffDecimalTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val decimalString = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasDecimal)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.decimalProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = decimalString, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffBooleanTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val booleanString = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasBoolean)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.booleanProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = booleanString, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffIntervalTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val intervalString = Vector(
            standoffTagV2.attributes
              .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasIntervalStart)
              .get
              .stringValue,
            standoffTagV2.attributes
              .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasIntervalEnd)
              .get
              .stringValue
          ).mkString(",")

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.intervalProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = intervalString, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffTimeTag) =>
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val timeString = standoffTagV2.attributes
            .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasTimeStamp)
            .get
            .stringValue

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.timeProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(key = dataTypeAttrName, value = timeString, xmlNamespace = None)

        case Some(StandoffDataTypeClasses.StandoffDateTag) =>
          // create one attribute from date properties
          val dataTypeAttrName = xmlItemForStandoffClass.tagItem.mapping.dataType
            .getOrElse(
              throw NotFoundException(
                s"data type attribute not found in mapping for ${xmlItemForStandoffClass.tagname}"
              )
            )
            .dataTypeXMLAttribute

          val calendar = KnoraCalendarV1.lookup(
            standoffTagV2.attributes
              .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasCalendar)
              .get
              .stringValue
          )

          val julianDayCountValueV1: UpdateValueV1 = JulianDayNumberValueV1(
            dateval1 = standoffTagV2.attributes
              .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasStartJDN)
              .get
              .stringValue
              .toInt,
            dateval2 = standoffTagV2.attributes
              .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasEndJDN)
              .get
              .stringValue
              .toInt,
            dateprecision1 = KnoraPrecisionV1.lookup(
              standoffTagV2.attributes
                .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasStartPrecision)
                .get
                .stringValue
            ),
            dateprecision2 = KnoraPrecisionV1.lookup(
              standoffTagV2.attributes
                .find(_.standoffPropertyIri.toString == OntologyConstants.KnoraBase.ValueHasEndPrecision)
                .get
                .stringValue
            ),
            calendar = calendar
          )

          val conventionalAttributes = standoffTagV2.attributes.filterNot(attr =>
            StandoffProperties.dateProperties.contains(attr.standoffPropertyIri.toString)
          )

          convertStandoffAttributeTags(
            xmlItemForStandoffClass.attributes,
            conventionalAttributes
          ) :+ StandoffTagAttribute(
            key = dataTypeAttrName,
            value = Vector(calendar.toString, julianDayCountValueV1.toString).mkString(":"),
            xmlNamespace = None
          )

        case None => convertStandoffAttributeTags(xmlItemForStandoffClass.attributes, standoffTagV2.attributes)

        case _ =>
          throw InconsistentRepositoryDataException(
            s"the triplestore returned an unknown data type for ${standoffTagV2.standoffTagClassIri} that could not be handled"
          )
      }

      // check in mapping if the XML element has an attribute class to be recreated
      val classAttributeFromMapping = xmlItemForStandoffClass.classname match {
        case `noClass` => Seq.empty[StandoffTagAttribute]
        case classname =>
          Vector(
            StandoffTagAttribute(
              key = classAttribute,
              value = classname,
              xmlNamespace = None
            )
          )
      }

      val attributesWithClass = attributes ++ classAttributeFromMapping

      if (standoffTagV2.endIndex.isDefined) {
        // it is a free standoff tag
        FreeStandoffTag(
          originalID = standoffTagV2.originalXMLID,
          tagName = xmlItemForStandoffClass.tagname,
          xmlNamespace = xmlItemForStandoffClass.namespace match {
            case `noNamespace` => None
            case namespace     => Some(namespace)
          },
          uuid = standoffTagV2.uuid,
          startPosition = standoffTagV2.startPosition,
          endPosition = standoffTagV2.endPosition,
          startIndex = standoffTagV2.startIndex,
          endIndex = standoffTagV2.endIndex.getOrElse(
            throw InconsistentRepositoryDataException(s"end index is missing for a free standoff tag")
          ),
          startParentIndex = standoffTagV2.startParentIndex,
          endParentIndex = standoffTagV2.endParentIndex,
          attributes = attributesWithClass.toSet
        )
      } else {
        // it is a hierarchical standoff tag
        HierarchicalStandoffTag(
          originalID = standoffTagV2.originalXMLID,
          tagName = xmlItemForStandoffClass.tagname,
          xmlNamespace = xmlItemForStandoffClass.namespace match {
            case `noNamespace` => None
            case namespace     => Some(namespace)
          },
          uuid = standoffTagV2.uuid,
          startPosition = standoffTagV2.startPosition,
          endPosition = standoffTagV2.endPosition,
          index = standoffTagV2.startIndex,
          parentIndex = standoffTagV2.startParentIndex,
          attributes = attributesWithClass.toSet
        )
      }

    }

    val textWithStandoff = TextWithStandoff(text = utf8str, standoff = standoffTags)
    standoffUtil.textWithStandoff2Xml(textWithStandoff)
  }

  /**
   * Given a sequence of standoff tags from a text value, makes a collection that can be compared with standoff from
   * another text value.
   *
   * @param standoff the standoff that needs to be compared.
   * @return a sequence of sets of tags that have the same start index, ordered by start index. All tags have their
   *         UUIDs replaced with empty strings.
   */
  def makeComparableStandoffCollection(standoff: Seq[StandoffTagV2]): Vector[Set[StandoffTagV2]] =
    // Since multiple tags could have the same index (e.g. if the standoff editor that
    // generated them doesn't use indexes), we first group them into sets of tags that have the same index,
    // then make a sequence of sets of tags sorted by index.
    standoff
      .groupBy(_.startIndex)
      .map { case (index: Int, standoffForIndex: Seq[StandoffTagV2]) =>
        // Set each tag's UUID to a fixed UUID (because these should not affect the comparison),
        // and sort its attributes by standoff property IRI.
        val comparableTags = standoffForIndex.map { tag =>
          tag.copy(
            uuid = fixedUuid,
            attributes = tag.attributes.sortBy(_.standoffPropertyIri)
          )
        }

        index -> comparableTags.toSet
      }
      .toVector
      .sortBy { case (index: Int, _) =>
        index
      }
      .map { case (_, standoffForIndex: Set[StandoffTagV2]) =>
        standoffForIndex
      }

  /**
   * If standoff is supposed to be queried with text values, returns the minimum and maximum start indexes
   * for the first page of standoff in a text value. Otherwise, returns `(None, None)`.
   *
   * @param queryStandoff `true` if standoff should be queried.
   * @param appConfig      the application configuration.
   * @return a tuple containing the minimum start index and maximum start index, or `(None, None)` if standoff
   *         is not being queried with text values.
   */
  def getStandoffMinAndMaxStartIndexesForTextValueQuery(
    queryStandoff: Boolean,
    appConfig: AppConfig
  ): (Option[Int], Option[Int]) =
    if (queryStandoff) {
      // Yes. Get the first page of standoff with each text value.
      (Some(0), Some(appConfig.standoffPerPage - 1))
    } else {
      // No.
      (None, None)
    }
}
