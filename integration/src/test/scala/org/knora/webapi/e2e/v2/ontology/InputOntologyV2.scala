/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2.ontology

import java.time.Instant

import dsp.constants.SalsahGui
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.e2e.v2.ontology.InputOntologyParsingModeV2.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.v2.responder.*
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.OwlCardinalityInfo
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

/**
 * Represents a parsing mode used by [[InputOntologyV2]].
 */
enum InputOntologyParsingModeV2 {

  /**
   * A parsing mode that ignores predicates that are present in Knora responses and absent in client input.
   * In tests, this allows a Knora response containing an entity to be parsed and compared with the client input
   * that was used to create the entity.
   */
  case TestResponseParsingModeV2 extends InputOntologyParsingModeV2

  /**
   * A parsing mode that rejects data not allowed in client input.
   */
  case ClientInputParsingModeV2 extends InputOntologyParsingModeV2

  /**
   * A parsing mode for parsing everything returned in a Knora ontology response.
   */
  case KnoraOutputParsingModeV2 extends InputOntologyParsingModeV2
}

/**
 * Represents information about an ontology received as input, either from the client or from the API server (in
 * the case of a test). This information is necessarily less complete than the information in a [[ReadOntologyV2]],
 * which takes advantage of additional knowledge that is available from the triplestore.
 *
 * @param ontologyMetadata metadata about the ontology.
 * @param classes          information about classes in the ontology.
 * @param properties       information about properties in the ontology.
 * @param individuals      information about OWL named individuals in the ontology.
 */
case class InputOntologyV2(
  ontologyMetadata: OntologyMetadataV2,
  classes: Map[SmartIri, ClassInfoContentV2] = Map.empty[SmartIri, ClassInfoContentV2],
  properties: Map[SmartIri, PropertyInfoContentV2] = Map.empty[SmartIri, PropertyInfoContentV2],
  individuals: Map[SmartIri, IndividualInfoContentV2] = Map.empty[SmartIri, IndividualInfoContentV2],
) {

  /**
   * Converts this [[InputOntologyV2]] to the specified Knora API v2 schema.
   *
   * @param targetSchema the target schema.
   * @return the converted [[InputOntologyV2]].
   */
  def toOntologySchema(targetSchema: ApiV2Schema): InputOntologyV2 =
    InputOntologyV2(
      ontologyMetadata = ontologyMetadata.toOntologySchema(targetSchema),
      classes = classes.map { case (classIri, classInfoContent) =>
        classIri.toOntologySchema(targetSchema) -> classInfoContent.toOntologySchema(targetSchema)
      },
      properties = properties.map { case (propertyIri, propertyInfoContent) =>
        propertyIri.toOntologySchema(targetSchema) -> propertyInfoContent.toOntologySchema(targetSchema)
      },
      individuals = individuals.map { case (individualIri, individualInfoContent) =>
        individualIri.toOntologySchema(targetSchema) -> individualInfoContent.toOntologySchema(targetSchema)
      },
    )

  /**
   * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used in tests after an update, when the
   * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
   * read back from the triplestore (in which predicate objects are not escaped). It is also used in generating
   * client API code.
   *
   * @return a copy of this [[InputOntologyV2]] with all predicate objects unescaped.
   */
  def unescape: InputOntologyV2 =
    InputOntologyV2(
      ontologyMetadata = ontologyMetadata.unescape,
      classes = classes.map { case (classIri, classDef) =>
        classIri -> classDef.unescape
      },
      properties = properties.map { case (propertyIri, propertyDef) =>
        propertyIri -> propertyDef.unescape
      },
      individuals = individuals.map { case (individualIri, individualDef) =>
        individualIri -> individualDef.unescape
      },
    )
}

/**
 * Processes JSON-LD received either from the client or from the API server. This is intended to support
 * two use cases:
 *
 * 1. When an update request is received, an [[InputOntologyV2]] can be used to construct an update request message.
 * 1. In a test, in which the submitted JSON-LD is similar to the server's response, both can be converted to [[InputOntologyV2]] objects for comparison.
 */
object InputOntologyV2 {

  /**
   * Constructs an [[InputOntologyV2]] based on a JSON-LD document.
   *
   * @param jsonLDDocument a JSON-LD document representing information about the ontology.
   * @param parsingMode    the parsing mode to be used.
   * @return an [[InputOntologyV2]] representing the same information.
   */
  def fromJsonLD(
    jsonLDDocument: JsonLDDocument,
    parsingMode: InputOntologyParsingModeV2 = ClientInputParsingModeV2,
  ): InputOntologyV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val ontologyObj: JsonLDObject = jsonLDDocument.body
    val externalOntologyIri: SmartIri =
      ontologyObj.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.toSmartIriWithErr)

    if (!(externalOntologyIri.isKnoraApiV2DefinitionIri && externalOntologyIri.isKnoraOntologyIri)) {
      throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIri")
    }

    val projectIri: Option[ProjectIri] = ontologyObj.maybeIriInObject(
      KnoraApiV2Complex.AttachedToProject,
      (str, errorFun) => ProjectIri.from(str).getOrElse(errorFun),
    )

    val validationFun: (String, => Nothing) => String =
      (s, errorFun) => Iri.toSparqlEncodedString(s).getOrElse(errorFun)

    val ontologyLabel: Option[String] =
      ontologyObj.maybeStringWithValidation(Rdfs.Label, validationFun)

    val ontologyComment: Option[String] =
      ontologyObj.maybeStringWithValidation(Rdfs.Comment, validationFun)

    val lastModificationDate: Option[Instant] = ontologyObj.maybeDatatypeValueInObject(
      key = KnoraApiV2Complex.LastModificationDate,
      expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
      validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
    )

    val ontologyMetadata = OntologyMetadataV2(
      ontologyIri = externalOntologyIri,
      projectIri = projectIri,
      label = ontologyLabel,
      comment = ontologyComment,
      lastModificationDate = lastModificationDate,
    )

    val maybeGraph: Option[JsonLDArray] = ontologyObj.getArray(JsonLDKeywords.GRAPH)

    maybeGraph match {
      case Some(graph) =>
        // Make a list of (entity definition, entity type IRI)
        val entitiesWithTypes: Seq[(JsonLDObject, SmartIri)] = graph.value.map {
          case jsonLDObj: JsonLDObject =>
            val entityType =
              jsonLDObj.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
            (jsonLDObj, entityType)

          case _ => throw BadRequestException("@graph must contain only JSON-LD objects")
        }

        val classes: Map[SmartIri, ClassInfoContentV2] = entitiesWithTypes.collect {
          case (jsonLDObj, entityType) if OntologyConstants.ClassTypes.contains(entityType.toString) =>
            val classInfoContent = ClassInfoContentV2Builder.fromJsonLDObject(jsonLDObj, parsingMode)
            classInfoContent.classIri -> classInfoContent
        }.toMap

        val properties: Map[SmartIri, PropertyInfoContentV2] = entitiesWithTypes.collect {
          case (jsonLDObj, entityType) if OntologyConstants.PropertyTypes.contains(entityType.toString) =>
            val propertyInfoContent = PropertyInfoContentV2Builder.fromJsonLDObject(jsonLDObj, parsingMode)
            propertyInfoContent.propertyIri -> propertyInfoContent
        }.toMap

        val individuals = entitiesWithTypes.collect {
          case (jsonLDObj, entityType) if entityType.toString == OntologyConstants.Owl.NamedIndividual =>
            val individualInfoContent = IndividualInfoContentV2.fromJsonLDObject(jsonLDObj)
            individualInfoContent.individualIri -> individualInfoContent
        }.toMap

        // Check whether any entities are in the wrong ontology.

        val entityIris: Iterable[SmartIri] = classes.values.map(_.classIri) ++ properties.values.map(_.propertyIri) ++
          individuals.values.map(_.individualIri)

        val entityIrisInWrongOntology: Set[SmartIri] = entityIris.filter { entityIri =>
          entityIri.getOntologyFromEntity != externalOntologyIri
        }.toSet

        if (entityIrisInWrongOntology.nonEmpty) {
          throw BadRequestException(
            s"One or more entities are not in ontology $externalOntologyIri: ${entityIrisInWrongOntology.mkString(", ")}",
          )
        }

        InputOntologyV2(
          ontologyMetadata = ontologyMetadata,
          classes = classes,
          properties = properties,
          individuals = individuals,
        )

      case None =>
        // We could get an ontology with no entities in a test.
        InputOntologyV2(ontologyMetadata = ontologyMetadata)
    }
  }

  /**
   * Can read a [[ClassInfoContentV2]] from JSON-LD.
   */
  object ClassInfoContentV2Builder {

    // The predicates that are allowed in a class definition that is read from JSON-LD representing client input.
    private val AllowedJsonLDClassPredicatesInClientInput = Set(
      JsonLDKeywords.ID,
      JsonLDKeywords.TYPE,
      Rdfs.SubClassOf,
      Rdfs.Label,
      Rdfs.Comment,
    )

    // The predicates that are allowed in an owl:Restriction that is read from JSON-LD representing client input.
    private val AllowedJsonLDRestrictionPredicatesInClientInput = Set(
      JsonLDKeywords.TYPE,
      OntologyConstants.Owl.Cardinality,
      OntologyConstants.Owl.MinCardinality,
      OntologyConstants.Owl.MaxCardinality,
      OntologyConstants.Owl.OnProperty,
      SalsahGui.External.GuiOrder,
    )

    /**
     * Converts a JSON-LD class definition into a [[ClassInfoContentV2]].
     *
     * @param jsonLDClassDef a JSON-LD object representing a class definition.
     * @param parsingMode    the parsing mode to be used.
     * @return a [[ClassInfoContentV2]] representing the class definition.
     */
    def fromJsonLDObject(jsonLDClassDef: JsonLDObject, parsingMode: InputOntologyParsingModeV2): ClassInfoContentV2 = {
      implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

      val classIri: SmartIri =
        jsonLDClassDef.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.toSmartIriWithErr)
      val ontologySchema: OntologySchema =
        classIri.getOntologySchema.getOrElse(throw BadRequestException(s"Invalid class IRI: $classIri"))

      // Parse differently depending on parsing mode.
      val filteredClassDef: JsonLDObject = parsingMode match {
        case ClientInputParsingModeV2 =>
          // In client input mode, only certain predicates are allowed.
          val extraClassPredicates = jsonLDClassDef.value.keySet -- AllowedJsonLDClassPredicatesInClientInput

          if (extraClassPredicates.nonEmpty) {
            throw BadRequestException(
              s"The definition of $classIri contains one or more invalid predicates: ${extraClassPredicates.mkString(", ")}",
            )
          } else {
            jsonLDClassDef
          }

        case TestResponseParsingModeV2 =>
          // In test response mode, we ignore predicates that wouldn't be allowed as client input.
          JsonLDObject(jsonLDClassDef.value.view.filterKeys(AllowedJsonLDClassPredicatesInClientInput).toMap)

        case KnoraOutputParsingModeV2 =>
          // In Knora output parsing mode, we accept all predicates.
          jsonLDClassDef
      }

      val (subClassOf: Set[SmartIri], directCardinalities: Map[SmartIri, KnoraCardinalityInfo]) =
        filteredClassDef.getArray(Rdfs.SubClassOf) match {
          case Some(valueArray: JsonLDArray) =>
            val arrayElemsAsObjs: Seq[JsonLDObject] = valueArray.value.map {
              case jsonLDObj: JsonLDObject => jsonLDObj
              case other                   => throw BadRequestException(s"Unexpected value for rdfs:subClassOf: $other")
            }

            // Get the base classes from the objects of rdfs:subClassOf.
            val baseClasses: Set[SmartIri] = arrayElemsAsObjs.filter { jsonLDObj =>
              jsonLDObj.isIri
            }.map { jsonLDObj =>
              jsonLDObj.toIri(stringFormatter.toSmartIriWithErr)
            }.toSet

            // The restrictions are the object of rdfs:subClassOf that have type owl:Restriction.
            val restrictions: Seq[JsonLDObject] = arrayElemsAsObjs.filter { jsonLDObj =>
              if (jsonLDObj.isIri) {
                false
              } else {
                jsonLDObj
                  .requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)
                  .toString == OntologyConstants.Owl.Restriction
              }
            }

            val cardinalities: Map[SmartIri, KnoraCardinalityInfo] =
              restrictions.foldLeft(Map.empty[SmartIri, KnoraCardinalityInfo]) { case (acc, restriction) =>
                val isInherited =
                  restriction.value.get(KnoraApiV2Complex.IsInherited).contains(JsonLDBoolean(true))

                // If we're in client input mode and the client tries to submit an inherited cardinality, return
                // a helpful error message.
                if (isInherited && parsingMode == ClientInputParsingModeV2) {
                  throw BadRequestException("Inherited cardinalities are not allowed in this request")
                } else if (isInherited && parsingMode == TestResponseParsingModeV2) {
                  // In test response parsing mode, ignore inherited cardinalities.
                  acc
                } else {
                  // In client input mode, only certain predicates are allowed on owl:Restriction nodes.
                  if (parsingMode == ClientInputParsingModeV2) {
                    val extraRestrictionPredicates =
                      restriction.value.keySet -- AllowedJsonLDRestrictionPredicatesInClientInput

                    if (extraRestrictionPredicates.nonEmpty) {
                      throw BadRequestException(
                        s"A cardinality in the definition of $classIri contains one or more invalid predicates: ${extraRestrictionPredicates
                            .mkString(", ")}",
                      )
                    }
                  }

                  val (owlCardinalityIri: IRI, owlCardinalityValue: Int) =
                    restriction
                      .getInt(OntologyConstants.Owl.Cardinality)
                      .fold(msg => throw BadRequestException(msg), identity) match {
                      case Some(value) => OntologyConstants.Owl.Cardinality -> value

                      case None =>
                        restriction
                          .getInt(OntologyConstants.Owl.MinCardinality)
                          .fold(msg => throw BadRequestException(msg), identity) match {
                          case Some(value) => OntologyConstants.Owl.MinCardinality -> value

                          case None =>
                            restriction
                              .getInt(OntologyConstants.Owl.MaxCardinality)
                              .fold(msg => throw BadRequestException(msg), identity) match {
                              case Some(value) => OntologyConstants.Owl.MaxCardinality -> value
                              case None =>
                                throw BadRequestException(
                                  s"Missing OWL cardinality predicate in the definition of $classIri",
                                )
                            }
                        }
                    }

                  val onProperty =
                    restriction.requireIriInObject(OntologyConstants.Owl.OnProperty, stringFormatter.toSmartIriWithErr)
                  val guiOrder = restriction
                    .getInt(SalsahGui.External.GuiOrder)
                    .fold(msg => throw BadRequestException(msg), identity)

                  val owlCardinalityInfo = OwlCardinalityInfo(
                    owlCardinalityIri = owlCardinalityIri,
                    owlCardinalityValue = owlCardinalityValue,
                    guiOrder = guiOrder,
                  )

                  val knoraCardinalityInfo = OwlCardinality.owlCardinality2KnoraCardinality(
                    propertyIri = onProperty.toString,
                    owlCardinality = owlCardinalityInfo,
                  )

                  acc + (onProperty -> knoraCardinalityInfo)
                }
              }

            (baseClasses, cardinalities)

          case None => (Set.empty[SmartIri], Map.empty[SmartIri, KnoraCardinalityInfo])
        }

      // If this is a custom datatype, get its definition.
      val datatypeInfo: Option[DatatypeInfoV2] =
        jsonLDClassDef.maybeIriInObject(OntologyConstants.Owl.OnDatatype, stringFormatter.toSmartIriWithErr).map {
          (onDatatype: SmartIri) =>
            val pattern: Option[String] = jsonLDClassDef
              .getObject(OntologyConstants.Owl.WithRestrictions)
              .fold(e => throw BadRequestException(e), identity)
              .map(
                _.getRequiredString(OntologyConstants.Xsd.Pattern).fold(msg => throw BadRequestException(msg), identity),
              )
            DatatypeInfoV2(onDatatype, pattern)
        }

      ClassInfoContentV2(
        classIri = classIri,
        datatypeInfo = datatypeInfo,
        predicates = EntityInfoContentV2.predicatesFromJsonLDObject(filteredClassDef),
        directCardinalities = directCardinalities,
        subClassOf = subClassOf,
        ontologySchema = ontologySchema,
      )
    }
  }

  /**
   * Can read a [[PropertyInfoContentV2]] from JSON-LD, and provides constants used by that class.
   */
  object PropertyInfoContentV2Builder {
    // The predicates allowed in a property definition that is read from JSON-LD representing client input.
    private val AllowedJsonLDPropertyPredicatesInClientInput = Set(
      JsonLDKeywords.ID,
      JsonLDKeywords.TYPE,
      OntologyConstants.KnoraApiV2Simple.SubjectType,
      OntologyConstants.KnoraApiV2Simple.ObjectType,
      KnoraApiV2Complex.SubjectType,
      KnoraApiV2Complex.ObjectType,
      Rdfs.SubPropertyOf,
      Rdfs.Label,
      Rdfs.Comment,
      SalsahGui.External.GuiElementProp,
      SalsahGui.External.GuiAttribute,
    )

    /**
     * Reads a [[PropertyInfoContentV2]] from a JSON-LD object.
     *
     * @param jsonLDPropertyDef the JSON-LD object representing a property definition.
     * @param parsingMode       the parsing mode to be used.
     * @return a [[PropertyInfoContentV2]] representing the property definition.
     */
    def fromJsonLDObject(
      jsonLDPropertyDef: JsonLDObject,
      parsingMode: InputOntologyParsingModeV2,
    ): PropertyInfoContentV2 = {
      implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

      val propertyIri: SmartIri =
        jsonLDPropertyDef.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.toSmartIriWithErr)
      val ontologySchema: OntologySchema =
        propertyIri.getOntologySchema.getOrElse(throw BadRequestException(s"Invalid property IRI: $propertyIri"))

      // Parse differently depending on the parsing mode.
      val filteredPropertyDef: JsonLDObject = parsingMode match {
        case ClientInputParsingModeV2 =>
          // In client input mode, only certain predicates are allowed.
          val extraPropertyPredicates = jsonLDPropertyDef.value.keySet -- AllowedJsonLDPropertyPredicatesInClientInput

          if (extraPropertyPredicates.nonEmpty) {
            throw BadRequestException(
              s"The definition of $propertyIri contains one or more invalid predicates: ${extraPropertyPredicates.mkString(", ")}",
            )
          } else {
            jsonLDPropertyDef
          }

        case TestResponseParsingModeV2 =>
          // In test response mode, we ignore predicates that wouldn't be allowed as client input.
          JsonLDObject(jsonLDPropertyDef.value.view.filterKeys(AllowedJsonLDPropertyPredicatesInClientInput).toMap)

        case KnoraOutputParsingModeV2 =>
          // In Knora output parsing mode, we accept all predicates.
          jsonLDPropertyDef
      }

      val subPropertyOf: Set[SmartIri] = filteredPropertyDef.getArray(Rdfs.SubPropertyOf) match {
        case Some(valueArray: JsonLDArray) =>
          valueArray.value.map {
            case superPropertyIriObj: JsonLDObject => superPropertyIriObj.toIri(stringFormatter.toSmartIriWithErr)
            case other                             => throw BadRequestException(s"Expected a property IRI: $other")
          }.toSet

        case None => Set.empty[SmartIri]
      }

      PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = EntityInfoContentV2.predicatesFromJsonLDObject(filteredPropertyDef),
        subPropertyOf = subPropertyOf,
        ontologySchema = ontologySchema,
      )
    }
  }
}
