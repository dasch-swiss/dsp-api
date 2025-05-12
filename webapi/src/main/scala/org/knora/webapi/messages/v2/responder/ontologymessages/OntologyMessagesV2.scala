/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.apache.commons.lang3.builder.HashCodeBuilder
import org.eclipse.rdf4j.model.IRI as Rdf4jIRI
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import zio.*
import zio.prelude.Validation

import java.time.Instant
import java.util.UUID

import dsp.constants.SalsahGui
import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.DataConversionException
import dsp.errors.InconsistentRepositoryDataException
import dsp.valueobjects.Iri
import dsp.valueobjects.Schema
import org.knora.webapi.*
import org.knora.webapi.LanguageCode.EN
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Simple
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.v2.responder.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.domain.model.Cardinality

/**
 * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
 */
sealed trait OntologiesResponderRequestV2 extends KnoraRequestV2 with RelayedMessage

/**
 * Requests the creation of an empty ontology. A successful response will be a [[ReadOntologyV2]].
 *
 * @param ontologyName   the name of the ontology to be created.
 * @param projectIri     the IRI of the project that the ontology will belong to.
 * @param isShared       the flag that shows if an ontology is a shared one.
 * @param label          the label of the ontology.
 * @param comment        the optional comment that described the ontology to be created.
 * @param apiRequestID   the ID of the API request.
 * @param requestingUser the user making the request.
 */
case class CreateOntologyRequestV2(
  ontologyName: String,
  projectIri: ProjectIri,
  isShared: Boolean = false,
  label: String,
  comment: Option[String] = None,
  apiRequestID: UUID,
  requestingUser: User,
)

/**
 * Requests the addition of a property to an ontology. A successful response will be a [[ReadOntologyV2]].
 *
 * @param propertyInfoContent  an [[PropertyInfoContentV2]] containing the property definition.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class CreatePropertyRequestV2(
  propertyInfoContent: PropertyInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * Requests the replacement of a class's cardinalities with new ones. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the new cardinalities.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ReplaceClassCardinalitiesRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * FIXME(DSP-1856): Can only remove one single cardinality at a time.
 * Requests a check if the user can remove class's cardinalities. A successful response will be a [[CanDoResponseV2]].
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the cardinalities to be removed.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
final case class CanDeleteCardinalitiesFromClassRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * FIXME(DSP-1856): Can only remove one single cardinality at a time.
 * Requests the removal of a class's cardinalities. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classInfoContent     a [[ClassInfoContentV2]] containing the cardinalities to be removed.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
final case class DeleteCardinalitiesFromClassRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * Requests the deletion of a class. A successful response will be a [[ReadOntologyMetadataV2]].
 *
 * @param classIri             the IRI of the class to be deleted.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class DeleteClassRequestV2(
  classIri: SmartIri,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * Requests that the `salsah-gui:guiElement` and `salsah-gui:guiAttribute` of a property are changed.
 *
 * @param propertyIri          the IRI of the property to be changed.
 * @param newGuiObject         the GUI object with the new GUI element and/or GUI attributes.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ChangePropertyGuiElementRequest(
  propertyIri: KnoraIris.PropertyIri,
  newGuiObject: Schema.GuiObject,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * Deletes the comment from a property. A successful response will be a [[ReadOntologyV2]].
 *
 * @param propertyIri          the IRI of the property.
 * @param lastModificationDate the ontology's last modification date
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class DeletePropertyCommentRequestV2(
  propertyIri: SmartIri,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * Requests that a class's labels or comments are changed. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classIri             the IRI of the property.
 * @param predicateToUpdate    `rdfs:label` or `rdfs:comment`.
 * @param newObjects           the class's new labels or comments.
 * @param lastModificationDate the ontology's last modification date.
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class ChangeClassLabelsOrCommentsRequestV2(
  classIri: ResourceClassIri,
  predicateToUpdate: LabelOrComment,
  newObjects: Seq[StringLiteralV2],
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

enum LabelOrComment {
  case Label
  case Comment

  override def toString: String = this match {
    case Label   => RDFS.LABEL.toString
    case Comment => RDFS.COMMENT.toString
  }
}
object LabelOrComment {
  def fromString(str: String): Option[LabelOrComment] =
    LabelOrComment.values.find(_.toString == str)
}

/**
 * Deletes the comment from a class. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classIri             the IRI of the class.
 * @param lastModificationDate the ontology's last modification date
 * @param apiRequestID         the ID of the API request.
 * @param requestingUser       the user making the request.
 */
case class DeleteClassCommentRequestV2(
  classIri: SmartIri,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

case class ChangeGuiOrderRequestV2(
  classInfoContent: ClassInfoContentV2,
  lastModificationDate: Instant,
  apiRequestID: UUID,
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * Requests all available information about a list of ontology entities (classes and/or properties). A successful response will be an
 * [[EntityInfoGetResponseV2]].
 *
 * @param classIris      the IRIs of the class entities to be queried.
 * @param propertyIris   the IRIs of the property entities to be queried.
 * @param requestingUser the user making the request.
 */
case class EntityInfoGetRequestV2(
  classIris: Set[SmartIri] = Set.empty[SmartIri],
  propertyIris: Set[SmartIri] = Set.empty[SmartIri],
  requestingUser: User,
) extends OntologiesResponderRequestV2

/**
 * Represents assertions about one or more ontology entities (resource classes and/or properties).
 *
 * @param classInfoMap    a [[Map]] of class entity IRIs to [[ReadClassInfoV2]] objects.
 * @param propertyInfoMap a [[Map]] of property entity IRIs to [[ReadPropertyInfoV2]] objects.
 */
case class EntityInfoGetResponseV2(
  classInfoMap: Map[SmartIri, ReadClassInfoV2],
  propertyInfoMap: Map[SmartIri, ReadPropertyInfoV2],
)

/**
 * Requests all available information about a list of ontology entities (standoff classes and/or properties). A successful response will be an
 * [[StandoffEntityInfoGetResponseV2]].
 *
 * @param standoffClassIris    the IRIs of the resource entities to be queried.
 * @param standoffPropertyIris the IRIs of the property entities to be queried.
 */
case class StandoffEntityInfoGetRequestV2(
  standoffClassIris: Set[SmartIri] = Set.empty,
  standoffPropertyIris: Set[SmartIri] = Set.empty,
) extends OntologiesResponderRequestV2

/**
 * Represents assertions about one or more ontology entities (resource classes and/or properties).
 *
 * @param standoffClassInfoMap    a [[Map]] of standoff class IRIs to [[ReadClassInfoV2]] objects.
 * @param standoffPropertyInfoMap a [[Map]] of standoff property IRIs to [[ReadPropertyInfoV2]] objects.
 */
case class StandoffEntityInfoGetResponseV2(
  standoffClassInfoMap: Map[SmartIri, ReadClassInfoV2],
  standoffPropertyInfoMap: Map[SmartIri, ReadPropertyInfoV2],
)

/**
 * Requests information about all standoff classes that are a subclass of a data type standoff class. A successful response will be an
 * [[StandoffClassesWithDataTypeGetResponseV2]].
 *
 * @param requestingUser the user making the request.
 */
case class StandoffClassesWithDataTypeGetRequestV2(requestingUser: User) extends OntologiesResponderRequestV2

/**
 * Represents assertions about all standoff classes that are a subclass of a data type standoff class.
 *
 * @param standoffClassInfoMap a [[Map]] of standoff class entity IRIs to [[ReadClassInfoV2]] objects.
 */
case class StandoffClassesWithDataTypeGetResponseV2(standoffClassInfoMap: Map[SmartIri, ReadClassInfoV2])

/**
 * Requests information about all standoff property entities. A successful response will be an
 * [[StandoffAllPropertyEntitiesGetResponseV2]].
 *
 * @param requestingUser the user making the request.
 */
case class StandoffAllPropertyEntitiesGetRequestV2(requestingUser: User) extends OntologiesResponderRequestV2

/**
 * Represents assertions about all standoff all standoff property entities.
 *
 * @param standoffAllPropertiesEntityInfoMap a [[Map]] of standoff property IRIs to [[ReadPropertyInfoV2]] objects.
 */
case class StandoffAllPropertyEntitiesGetResponseV2(
  standoffAllPropertiesEntityInfoMap: Map[SmartIri, ReadPropertyInfoV2],
)

/**
 * Checks whether a Knora resource or value class is a subclass of (or identical to) another class.
 * A successful response will be a [[CheckSubClassResponseV2]].
 *
 * @param subClassIri   the IRI of the subclass.
 * @param superClassIri the IRI of the superclass.
 */
case class CheckSubClassRequestV2(subClassIri: SmartIri, superClassIri: SmartIri, requestingUser: User)
    extends OntologiesResponderRequestV2

/**
 * Represents a response to a [[CheckSubClassRequestV2]].
 *
 * @param isSubClass `true` if the requested inheritance relationship exists.
 */
case class CheckSubClassResponseV2(isSubClass: Boolean)

/**
 * Requests information about the subclasses of a Knora resource class. A successful response will be
 * a [[SubClassesGetResponseV2]].
 *
 * @param resourceClassIri the IRI of the given resource class.
 * @param requestingUser   the user making the request.
 */
case class SubClassesGetRequestV2(resourceClassIri: SmartIri, requestingUser: User) extends OntologiesResponderRequestV2

/**
 * Provides information about the subclasses of a Knora resource class.
 *
 * @param subClasses a list of [[SubClassInfoV2]] representing the subclasses of the specified class.
 */
case class SubClassesGetResponseV2(subClasses: Seq[SubClassInfoV2])

/**
 * Requests metadata about ontologies by ontology IRI.
 *
 * @param ontologyIris   the IRIs of the ontologies to be queried. If this set is empty, information
 *                       about all ontologies is returned.
 */
case class OntologyMetadataGetByIriRequestV2(ontologyIris: Set[SmartIri] = Set.empty[SmartIri])
    extends OntologiesResponderRequestV2

/**
 * Requests entity definitions for the given ontology.
 *
 * @param ontologyIri    the ontology to query for.
 * @param allLanguages   true if information in all available languages should be returned.
 * @param requestingUser the user making the request.
 */
case class OntologyEntitiesGetRequestV2(ontologyIri: OntologyIri, allLanguages: Boolean, requestingUser: User)
    extends OntologiesResponderRequestV2

/**
 * Requests the entity definitions for the given class IRIs. A successful response will be a [[ReadOntologyV2]].
 *
 * @param classIris      the IRIs of the classes to be queried.
 * @param allLanguages   true if information in all available languages should be returned.
 * @param requestingUser the user making the request.
 */
case class ClassesGetRequestV2(classIris: Set[SmartIri], allLanguages: Boolean, requestingUser: User)
    extends OntologiesResponderRequestV2

/**
 * Requests the definitions of the specified properties. A successful response will be a [[ReadOntologyV2]].
 *
 * @param propertyIris   the IRIs of the properties to be queried.
 * @param allLanguages   true if information in all available languages should be returned.
 * @param requestingUser the user making the request.
 */
case class PropertiesGetRequestV2(propertyIris: Set[SmartIri], allLanguages: Boolean, requestingUser: User)
    extends OntologiesResponderRequestV2

/**
 * Represents the contents of an ontology to be returned in an API response.
 *
 * @param ontologyMetadata metadata about the ontology.
 * @param classes          information about classes.
 * @param properties       information about properties.
 * @param isWholeOntology  `true` if this is the whole specified ontology, `false` if it's just selected entities.
 * @param userLang         the preferred language in which the information should be returned, or [[None]] if information
 *                         should be returned in all available languages.
 */
case class ReadOntologyV2(
  ontologyMetadata: OntologyMetadataV2,
  classes: Map[SmartIri, ReadClassInfoV2] = Map.empty[SmartIri, ReadClassInfoV2],
  properties: Map[SmartIri, ReadPropertyInfoV2] = Map.empty[SmartIri, ReadPropertyInfoV2],
  individuals: Map[SmartIri, ReadIndividualInfoV2] = Map.empty[SmartIri, ReadIndividualInfoV2],
  isWholeOntology: Boolean = false,
  userLang: Option[String] = None,
) extends KnoraJsonLDResponseV2
    with KnoraReadV2[ReadOntologyV2] {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  def projectIri: Option[ProjectIri] = ontologyMetadata.projectIri
  def ontologyIri: OntologyIri       = OntologyIri.unsafeFrom(ontologyMetadata.ontologyIri)

  /**
   * Converts this [[ReadOntologyV2]] to the specified Knora API v2 schema.
   *
   * @param targetSchema the target schema.
   * @return the converted [[ReadOntologyV2]].
   */
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadOntologyV2 = {
    // Get rules for transforming internal entities to external entities in the target schema.
    val transformationRules =
      OntologyTransformationRules.getTransformationRules(targetSchema)

    // If we're converting to the API v2 simple schema, filter out link value properties.
    val propertiesConsideringLinkValueProps = targetSchema match {
      case ApiV2Simple =>
        properties.filterNot { case (_, propertyInfo) =>
          propertyInfo.isLinkValueProp
        }

      case _ => properties
    }

    // If we're converting from the external schema to an internal one, filter classes and properties that don't
    // exist in the target schema.

    val (classesFilteredForTargetSchema, propsFilteredForTargetSchema) =
      if (
        ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraBase.KnoraBaseOntologyIri ||
        ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraAdmin.KnoraAdminOntologyIri
      ) {
        val filteredClasses = classes.filterNot { case (classIri, classDef) =>
          transformationRules.internalClassesToRemove.contains(
            classIri,
          ) || (targetSchema == ApiV2Simple && classDef.isStandoffClass)
        }

        val filteredProps = propertiesConsideringLinkValueProps.filterNot { case (propertyIri, _) =>
          transformationRules.internalPropertiesToRemove.contains(propertyIri)
        }

        (filteredClasses, filteredProps)
      } else {
        (classes, propertiesConsideringLinkValueProps)
      }

    // Convert everything to the target schema.

    val ontologyMetadataInTargetSchema =
      if (
        ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraBase.KnoraBaseOntologyIri ||
        ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraAdmin.KnoraAdminOntologyIri
      ) {
        transformationRules.ontologyMetadata
      } else {
        ontologyMetadata.toOntologySchema(targetSchema)
      }

    val classesInTargetSchema = classesFilteredForTargetSchema.map { case (classIri, readClassInfo) =>
      classIri.toOntologySchema(targetSchema) -> readClassInfo.toOntologySchema(targetSchema)
    }

    val propertiesInTargetSchema = propsFilteredForTargetSchema.map { case (propertyIri, readPropertyInfo) =>
      propertyIri.toOntologySchema(targetSchema) -> readPropertyInfo.toOntologySchema(targetSchema)
    }

    val individualsInTargetSchema = individuals.map { case (individualIri, readIndividualInfo) =>
      individualIri.toOntologySchema(targetSchema) -> readIndividualInfo.toOntologySchema(targetSchema)
    }

    // If we're converting from the internal schema to an external one, and this is the whole ontology,
    // add classes and properties that exist in the target schema but not in the source schema.

    val (classesWithExtraOnesForSchema, propertiesWithExtraOnesForSchema) =
      if (
        isWholeOntology &&
        (ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraBase.KnoraBaseOntologyIri ||
          ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraAdmin.KnoraAdminOntologyIri)
      ) {
        (
          classesInTargetSchema ++ transformationRules.externalClassesToAdd,
          propertiesInTargetSchema ++ transformationRules.externalPropertiesToAdd,
        )
      } else {
        (classesInTargetSchema, propertiesInTargetSchema)
      }

    copy(
      ontologyMetadata = ontologyMetadataInTargetSchema,
      classes = classesWithExtraOnesForSchema,
      properties = propertiesWithExtraOnesForSchema,
      individuals = individualsInTargetSchema,
    )
  }

  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument =
    toOntologySchema(targetSchema).generateJsonLD(targetSchema, appConfig)

  private def generateJsonLD(targetSchema: ApiV2Schema, appConfig: AppConfig): JsonLDDocument = {
    // Get the ontologies of all Knora entities mentioned in class definitions.

    val knoraOntologiesFromClasses: Set[SmartIri] = classes.values.flatMap { classInfo =>
      val entityIris: Set[SmartIri] = classInfo.allCardinalities.keySet ++ classInfo.entityInfoContent.subClassOf

      entityIris.flatMap { entityIri =>
        if (entityIri.isKnoraEntityIri) {
          Set(entityIri.getOntologyFromEntity)
        } else {
          Set.empty[SmartIri]
        }
      } + classInfo.entityInfoContent.classIri.getOntologyFromEntity
    }.toSet
      .filter(_.isKnoraOntologyIri)

    // Get the ontologies of all Knora entities mentioned in property definitions.

    val knoraOntologiesFromProperties: Set[SmartIri] = properties.values.flatMap { property =>
      val entityIris = property.entityInfoContent.subPropertyOf ++
        property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri) ++
        property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri) ++
        property.entityInfoContent.getPredicateIriObjects(KnoraApiV2Complex.SubjectType.toSmartIri) ++
        property.entityInfoContent.getPredicateIriObjects(KnoraApiV2Complex.ObjectType.toSmartIri)

      entityIris.flatMap { entityIri =>
        if (entityIri.isKnoraEntityIri) {
          Set(entityIri.getOntologyFromEntity)
        } else {
          Set.empty[SmartIri]
        }
      } + property.entityInfoContent.propertyIri.getOntologyFromEntity
    }.toSet
      .filter(_.isKnoraOntologyIri)

    // Determine which ontology to use as the knora-api prefix expansion.
    val knoraApiPrefixExpansion = targetSchema match {
      case ApiV2Simple  => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
      case ApiV2Complex => KnoraApiV2Complex.KnoraApiV2PrefixExpansion
    }

    // Add a salsah-gui prefix only if we're using the complex schema.
    val salsahGuiPrefix: Option[(String, String)] = targetSchema match {
      case ApiV2Complex =>
        Some(
          SalsahGui.SalsahGuiOntologyLabel -> SalsahGui.External.SalsahGuiPrefixExpansion,
        )

      case _ => None
    }

    // Make a set of all other Knora ontologies used.
    val otherKnoraOntologiesUsed: Set[SmartIri] =
      (knoraOntologiesFromClasses ++ knoraOntologiesFromProperties).filterNot { ontology =>
        ontology.getOntologyName.value == OntologyConstants.KnoraApi.KnoraApiOntologyLabel ||
        ontology.getOntologyName.value == SalsahGui.SalsahGuiOntologyLabel
      }

    // Make the JSON-LD context.
    val context = JsonLDUtil.makeContext(
      fixedPrefixes = Map(
        OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion,
        "rdf"                                            -> OntologyConstants.Rdf.RdfPrefixExpansion,
        "rdfs"                                           -> Rdfs.RdfsPrefixExpansion,
        "owl"                                            -> OntologyConstants.Owl.OwlPrefixExpansion,
        "xsd"                                            -> OntologyConstants.Xsd.XsdPrefixExpansion,
      ) ++ salsahGuiPrefix,
      knoraOntologiesNeedingPrefixes = otherKnoraOntologiesUsed,
    )

    // Generate JSON-LD for the classes, properties, and individuals.

    val jsonClasses: Vector[JsonLDObject] = classes.values.map { readClassInfo =>
      userLang match {
        case Some(lang) =>
          readClassInfo.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, appConfig = appConfig)
        case None => readClassInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
      }
    }.toVector

    val jsonProperties: Vector[JsonLDObject] = properties.values.map { readPropertyInfo =>
      userLang match {
        case Some(lang) =>
          readPropertyInfo.toJsonLDWithSingleLanguage(
            targetSchema = targetSchema,
            userLang = lang,
            appConfig = appConfig,
          )
        case None => readPropertyInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
      }
    }.toVector

    val jsonIndividuals: Vector[JsonLDObject] = individuals.values.map { readIndividualInfo =>
      userLang match {
        case Some(lang) =>
          readIndividualInfo.toJsonLDWithSingleLanguage(
            targetSchema = targetSchema,
            userLang = lang,
            appConfig = appConfig,
          )
        case None => readIndividualInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
      }
    }.toVector

    val allEntities       = jsonClasses ++ jsonProperties ++ jsonIndividuals
    val allEntitiesSorted = allEntities.sortBy(_.value(JsonLDKeywords.ID))

    // Assemble the JSON-LD document.

    val body = JsonLDObject(
      ontologyMetadata.toJsonLD(targetSchema) + (JsonLDKeywords.GRAPH -> JsonLDArray(allEntitiesSorted)),
    )

    JsonLDDocument(body = body, context = context, isFlat = true)
  }
}

/**
 * Returns metadata about Knora ontologies.
 *
 * @param ontologies the metadata to be returned.
 */
case class ReadOntologyMetadataV2(ontologies: Set[OntologyMetadataV2])
    extends KnoraJsonLDResponseV2
    with KnoraReadV2[ReadOntologyMetadataV2] {

  override def toOntologySchema(targetSchema: ApiV2Schema): ReadOntologyMetadataV2 = {
    // We may have metadata for knora-api in more than one schema. Just return the one for the target schema.

    val ontologiesAvailableInTargetSchema = ontologies.filterNot { ontology =>
      ontology.ontologyIri.getOntologyName.value == OntologyConstants.KnoraApi.KnoraApiOntologyLabel &&
      !ontology.ontologyIri.getOntologySchema.contains(targetSchema)
    }

    copy(
      ontologies = ontologiesAvailableInTargetSchema.map(_.toOntologySchema(targetSchema)),
    )
  }

  private def generateJsonLD(targetSchema: ApiV2Schema): JsonLDDocument = {
    val knoraApiOntologyPrefixExpansion = targetSchema match {
      case ApiV2Simple  => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
      case ApiV2Complex => KnoraApiV2Complex.KnoraApiV2PrefixExpansion
    }

    val context = JsonLDObject(
      Map(
        OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(knoraApiOntologyPrefixExpansion),
        "xsd"                                            -> JsonLDString(OntologyConstants.Xsd.XsdPrefixExpansion),
        "rdfs"                                           -> JsonLDString(Rdfs.RdfsPrefixExpansion),
        "owl"                                            -> JsonLDString(OntologyConstants.Owl.OwlPrefixExpansion),
      ),
    )

    val ontologiesJson: Vector[JsonLDObject] =
      ontologies.toVector.sortBy(_.ontologyIri).map(ontology => JsonLDObject(ontology.toJsonLD(targetSchema)))

    val body = JsonLDObject(
      Map(
        JsonLDKeywords.GRAPH -> JsonLDArray(ontologiesJson),
      ),
    )

    JsonLDDocument(body = body, context = context, isFlat = true)
  }

  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument =
    toOntologySchema(targetSchema).generateJsonLD(targetSchema)
}

/**
 * Represents a predicate that is asserted about a given ontology entity, and the objects of that predicate.
 *
 * @param predicateIri the IRI of the predicate.
 * @param objects      the objects of the predicate.
 */
case class PredicateInfoV2(predicateIri: SmartIri, objects: Seq[OntologyLiteralV2] = Seq.empty[OntologyLiteralV2]) {

  /**
   * Converts this [[PredicateInfoV2]] to another ontology schema.
   *
   * @param targetSchema the target schema.
   * @return the converted [[PredicateInfoV2]].
   */
  def toOntologySchema(targetSchema: OntologySchema): PredicateInfoV2 =
    copy(
      predicateIri = predicateIri.toOntologySchema(targetSchema),
      objects = objects.map {
        case smartIriLiteral: SmartIriLiteralV2 => smartIriLiteral.toOntologySchema(targetSchema)
        case other                              => other
      },
    )

  /**
   * Requires this predicate to have a single IRI object, and returns that object.
   *
   * @param errorFun a function that throws an error. It will be called if the predicate does not have a single
   *                 IRI object.
   * @return the predicate's IRI object.
   */
  def requireIriObject(errorFun: => Nothing): SmartIri =
    getIriObject().getOrElse(errorFun)

  def getIriObject(): Option[SmartIri] =
    objects match {
      case Seq(SmartIriLiteralV2(iri)) => Some(iri)
      case _                           => None
    }

  /**
   * Requires this predicate to have at least one IRI, and returns those objects.
   *
   * @param errorFun a function that throws an error. It will be called if the predicate has no objects,
   *                 or has non-IRI objects.
   * @return the predicate's IRI objects.
   */
  def requireIriObjects(errorFun: => Nothing): Set[SmartIri] = {
    if (objects.isEmpty) {
      errorFun
    }

    objects.map {
      case SmartIriLiteralV2(iri) => iri
      case _                      => errorFun
    }.toSet
  }

  /**
   * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used after an update, when the
   * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
   * read back from the triplestore (in which predicate objects are not escaped).
   *
   * @return this predicate with its objects unescaped.
   */
  def unescape: PredicateInfoV2 =
    copy(
      objects = objects.map {
        case StringLiteralV2(str, lang) => StringLiteralV2.from(Iri.fromSparqlEncodedString(str), lang)
        case other                      => other
      },
    )

  override def hashCode(): Int =
    // Ignore the order of predicate objects when generating hash codes for this class.
    new HashCodeBuilder(17, 37).append(predicateIri).append(objects.toSet).toHashCode

  override def equals(that: scala.Any): Boolean =
    // Ignore the order of predicate objects when testing equality for this class.
    that match {
      case otherPred: PredicateInfoV2 =>
        predicateIri == otherPred.predicateIri &&
        objects.toSet == otherPred.objects.toSet

      case _ => false
    }
}

object PredicateInfoV2 {

  def checkRequiredStringLiteralWithLanguageTag(
    predicateIri: IRI,
    predicates: Iterable[PredicateInfoV2],
  ): Validation[String, Seq[StringLiteralV2]] =
    atMostOnePredicate(predicateIri, predicates).flatMap {
      case Some(info: PredicateInfoV2) => requireStringLiteralsWithLanguageTag(predicateIri, info)
      case None                        => Validation.fail(s"Missing $predicateIri")
    }

  def checkOptionalStringLiteralWithLanguageTag(
    predicateIri: IRI,
    predicates: Iterable[PredicateInfoV2],
  ): Validation[String, Seq[StringLiteralV2]] =
    atMostOnePredicate(predicateIri, predicates).flatMap {
      case Some(info) => requireStringLiteralsWithLanguageTag(predicateIri, info)
      case None       => Validation.succeed(Seq.empty[StringLiteralV2])
    }

  private def atMostOnePredicate(
    predicateIri: IRI,
    predicates: Iterable[PredicateInfoV2],
  ): Validation[String, Option[PredicateInfoV2]] =
    predicates.filter(_.predicateIri.toIri == predicateIri) match {
      case list if list.size <= 1 => Validation.succeed(list.headOption)
      case _                      => Validation.fail(s"$predicateIri may only be provided once")
    }

  private def requireStringLiteralsWithLanguageTag(
    propertyIri: IRI,
    predicateInfo: PredicateInfoV2,
  ): Validation[String, Seq[StringLiteralV2]] =
    predicateInfo.objects match {
      case Nil => Validation.fail(s"At least one value must be provided for $propertyIri")
      case literals if !literals.forall {
            case StringLiteralV2(_, Some(_)) => true
            case _                           => false
          } =>
        Validation.fail(s"All values of $propertyIri must be string literals with a language code")
      case literals => Validation.succeed(literals.collect { case l: StringLiteralV2 => l })
    }
}

final case class PredicateInfoV2Builder private (
  predicateIri: SmartIri,
  objects: Seq[OntologyLiteralV2] = Seq.empty,
) {
  self =>
  def withObject(obj: OntologyLiteralV2): PredicateInfoV2Builder =
    copy(objects = self.objects :+ obj)
  def withObjects(objs: Seq[OntologyLiteralV2]): PredicateInfoV2Builder =
    copy(objects = self.objects ++ objs)
  def withStringLiteral(lang: LanguageCode, value: String): PredicateInfoV2Builder =
    withObject(StringLiteralV2.from(value, Some(lang.code)))
  def withStringLiteral(value: String): PredicateInfoV2Builder =
    withObject(StringLiteralV2.from(value, None))
  def withStringLiterals(literals: Map[LanguageCode, String]): PredicateInfoV2Builder =
    withObjects(literals.map { case (lang, value) => StringLiteralV2.from(value, lang) }.toSeq)
  def build(): PredicateInfoV2 = PredicateInfoV2(self.predicateIri, self.objects)
}
object PredicateInfoV2Builder {
  def make(predicateIri: Rdf4jIRI)(implicit sf: StringFormatter): PredicateInfoV2Builder =
    make(sf.toSmartIri(predicateIri.toString))
  def make(predicateIri: String)(implicit sf: StringFormatter): PredicateInfoV2Builder =
    make(sf.toSmartIri(predicateIri))
  def make(predicateIri: SmartIri): PredicateInfoV2Builder =
    PredicateInfoV2Builder(predicateIri)

  def makeRdfType(typeIri: SmartIri)(implicit sf: StringFormatter): PredicateInfoV2Builder =
    makeRdfType().withObject(SmartIriLiteralV2(typeIri))
  private def makeRdfType()(implicit sf: StringFormatter): PredicateInfoV2Builder = make(RDF.TYPE)

  def makeRdfsLabel(literals: Map[LanguageCode, String])(implicit sf: StringFormatter): PredicateInfoV2Builder =
    makeRdfsLabel().withStringLiterals(literals)
  def makeRdfsLabelEn(value: String)(implicit sf: StringFormatter): PredicateInfoV2Builder = makeRdfsLabel(EN, value)
  private def makeRdfsLabel(lang: LanguageCode, value: String)(implicit sf: StringFormatter): PredicateInfoV2Builder =
    makeRdfsLabel().withStringLiteral(lang, value)
  private def makeRdfsLabel()(implicit sf: StringFormatter): PredicateInfoV2Builder = make(RDFS.LABEL)

  def makeRdfsCommentEn(value: String)(implicit sf: StringFormatter): PredicateInfoV2Builder =
    makeRdfsComment(EN, value)
  private def makeRdfsComment(lang: LanguageCode, value: String)(implicit sf: StringFormatter): PredicateInfoV2Builder =
    makeRdfsComment().withStringLiteral(lang, value)
  private def makeRdfsComment()(implicit sf: StringFormatter): PredicateInfoV2Builder = make(RDFS.COMMENT)
}

/**
 * Represents the OWL cardinalities that Knora supports.
 */
object OwlCardinality extends Enumeration {

  /**
   * Represents information about an OWL cardinality.
   *
   * @param owlCardinalityIri   the IRI of the OWL cardinality, which must be a member of the set
   *                            [[OntologyConstants.Owl.cardinalityOWLRestrictions]].
   * @param owlCardinalityValue the value of the OWL cardinality, which must be 0 or 1.
   * @param guiOrder            the SALSAH GUI order.
   * @return a [[Value]].
   */
  case class OwlCardinalityInfo(owlCardinalityIri: IRI, owlCardinalityValue: Int, guiOrder: Option[Int] = None) {
    if (!OntologyConstants.Owl.cardinalityOWLRestrictions.contains(owlCardinalityIri)) {
      throw InconsistentRepositoryDataException(s"Invalid OWL cardinality property: $owlCardinalityIri")
    }

    if (!(owlCardinalityValue == 0 || owlCardinalityValue == 1)) {
      throw InconsistentRepositoryDataException(s"Invalid OWL cardinality value: $owlCardinalityValue")
    }

    override def toString: String = s"<$owlCardinalityIri> $owlCardinalityValue"

    def equalsWithoutGuiOrder(that: OwlCardinalityInfo): Boolean =
      owlCardinalityIri == that.owlCardinalityIri && owlCardinalityValue == that.owlCardinalityValue
  }

  /**
   * Represents a Knora cardinality with an optional SALSAH GUI order.
   *
   * @param cardinality the Knora cardinality.
   * @param guiOrder    the SALSAH GUI order.
   */
  case class KnoraCardinalityInfo(cardinality: Cardinality, guiOrder: Option[Int] = None) {
    self =>
    override def toString: String = guiOrder match {
      case Some(definedGuiOrder) => s"${cardinality.toString} (guiOrder $definedGuiOrder)"
      case None                  => cardinality.toString
    }

    def equalsWithoutGuiOrder(that: KnoraCardinalityInfo): Boolean =
      that.cardinality == cardinality

    def isRequired: Boolean = cardinality.isRequired
  }

  /**
   * The valid mappings between Knora cardinalities and OWL cardinalities.
   */
  private val knoraCardinality2OwlCardinalityMap: Map[Cardinality, OwlCardinalityInfo] =
    Cardinality.allCardinalities.map(c => (c, Cardinality.toOwl(c))).toMap

  private val owlCardinality2KnoraCardinalityMap: Map[OwlCardinalityInfo, Cardinality] =
    knoraCardinality2OwlCardinalityMap.map(_.swap)

  /**
   * Converts information about an OWL cardinality restriction to a [[Value]] of this enumeration.
   *
   * @param propertyIri    the IRI of the property that the OWL cardinality applies to.
   * @param owlCardinality information about an OWL cardinality.
   * @return a [[Value]].
   */
  def owlCardinality2KnoraCardinality(propertyIri: IRI, owlCardinality: OwlCardinalityInfo): KnoraCardinalityInfo = {
    val cardinality = owlCardinality2KnoraCardinalityMap.getOrElse(
      owlCardinality.copy(guiOrder = None),
      throw InconsistentRepositoryDataException(s"Invalid OWL cardinality $owlCardinality for $propertyIri"),
    )

    KnoraCardinalityInfo(
      cardinality = cardinality,
      guiOrder = owlCardinality.guiOrder,
    )
  }

  /**
   * Converts a [[Value]] of this enumeration to information about an OWL cardinality restriction.
   *
   * @param knoraCardinality a [[Value]].
   * @return an [[OwlCardinalityInfo]].
   */
  def knoraCardinality2OwlCardinality(knoraCardinality: KnoraCardinalityInfo): OwlCardinalityInfo =
    knoraCardinality2OwlCardinalityMap(knoraCardinality.cardinality).copy(guiOrder = knoraCardinality.guiOrder)

}

/**
 * Represents information about an ontology entity.
 */
sealed trait EntityInfoContentV2 {

  /**
   * The predicates of the entity, and their objects.
   */
  val predicates: Map[SmartIri, PredicateInfoV2]

  protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Checks that a predicate is present in this [[EntityInfoContentV2]] and that it has a single IRI object, and
   * returns the object as a [[SmartIri]].
   *
   * @param predicateIri the IRI of the predicate.
   * @param errorFun     a function that will be called if the predicate is absent or if its object is not an IRI.
   * @return a [[SmartIri]] representing the predicate's object.
   */
  def requireIriObject(predicateIri: SmartIri, errorFun: => Nothing): SmartIri =
    getIriObject(predicateIri).getOrElse(errorFun)
  def getIriObject(predicateIri: SmartIri): Option[SmartIri] = predicates.get(predicateIri).flatMap(_.getIriObject())

  /**
   * Checks that a predicate is present in this [[EntityInfoContentV2]] and that it at least one IRI object, and
   * returns those objects as a set of [[SmartIri]] instances.
   *
   * @param predicateIri the IRI of the predicate.
   * @param errorFun     a function that will be called if the predicate is absent or if its objects are not IRIs.
   * @return a set of [[SmartIri]] instances representing the predicate's objects.
   */
  def requireIriObjects(predicateIri: SmartIri, errorFun: => Nothing): Set[SmartIri] =
    predicates.getOrElse(predicateIri, errorFun).requireIriObjects(errorFun)

  /**
   * A convenience method that returns the canonical `rdf:type` of this entity. Throws [[InconsistentRepositoryDataException]]
   * if the entity's predicates do not include `rdf:type`.
   *
   * @return the entity's `rdf:type`.
   */
  def getRdfType: SmartIri

  /**
   * A convenience method that returns all the objects of this entity's `rdf:type` predicate. Throws [[InconsistentRepositoryDataException]]
   * * if the entity's predicates do not include `rdf:type`.
   *
   * @return all the values of `rdf:type` for this entity, sorted for determinism.
   */
  def getRdfTypes: Seq[SmartIri]

  /**
   * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used after an update, when the
   * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
   * read back from the triplestore (in which predicate objects are not escaped).
   *
   * @return the predicates of this [[EntityInfoContentV2]], with their objects unescaped.
   */
  protected def unescapePredicateObjects: Map[SmartIri, PredicateInfoV2] =
    predicates.map { case (predicateIri, predicateInfo) =>
      predicateIri -> predicateInfo.unescape
    }

  /**
   * Gets a predicate and its object from an entity in a specific language.
   *
   * @param predicateIri the IRI of the predicate.
   * @param userLang     the language in which the object should to be returned.
   * @return the requested predicate and object.
   */
  def getPredicateAndStringLiteralObjectWithLang(
    predicateIri: SmartIri,
    appConfig: AppConfig,
    userLang: String,
  ): Option[(SmartIri, String)] =
    getPredicateStringLiteralObject(
      predicateIri = predicateIri,
      preferredLangs = Some(userLang, appConfig.fallbackLanguage),
    ).map(obj => predicateIri -> obj)

  /**
   * Returns an object for a given predicate. If requested, attempts to return the object in the user's preferred
   * language, in the system's default language, or in any language, in that order.
   *
   * @param predicateIri   the IRI of the predicate.
   * @param preferredLangs the user's preferred language and the system's default language.
   * @return an object for the predicate, or [[None]] if this entity doesn't have the specified predicate, or
   *         if the predicate has no objects.
   */
  def getPredicateStringLiteralObject(
    predicateIri: SmartIri,
    preferredLangs: Option[(String, String)] = None,
  ): Option[String] =
    // Does the predicate exist?
    predicates.get(predicateIri) match {
      case Some(predicateInfo) =>
        // Yes. Make a sequence of its string values.
        val stringLiterals: Vector[StringLiteralV2] = predicateInfo.objects.collect { case strLit: StringLiteralV2 =>
          strLit
        }.toVector

        val stringLiteralSequence = StringLiteralSequenceV2(stringLiterals)

        // Were preferred languages specified?
        preferredLangs match {
          case Some((userLang, defaultLang)) =>
            // Yes.
            stringLiteralSequence.getPreferredLanguage(userLang, defaultLang)
          case None =>
            // Preferred languages were not specified. Take the first object.
            predicateInfo.objects.headOption match {
              case Some(StringLiteralV2(str, _)) => Some(str)
              case _                             => None
            }

        }

      case None => None
    }

  /**
   * Returns all the non-language-specific string objects specified for a given predicate.
   *
   * @param predicateIri the IRI of the predicate.
   * @return the predicate's objects, or an empty set if this entity doesn't have the specified predicate.
   */
  def getPredicateStringLiteralObjectsWithoutLang(predicateIri: SmartIri): Seq[String] =
    predicates.get(predicateIri) match {
      case Some(predicateInfo) =>
        predicateInfo.objects.collect { case StringLiteralV2(str, None) =>
          str
        }

      case None => Seq.empty[String]
    }

  /**
   * Returns all the IRI objects specified for a given predicate.
   *
   * @param predicateIri the IRI of the predicate.
   * @return the predicate's IRI objects, or an empty set if this entity doesn't have the specified predicate.
   */
  def getPredicateIriObjects(predicateIri: SmartIri): Seq[SmartIri] =
    predicates.get(predicateIri) match {
      case Some(predicateInfo) =>
        predicateInfo.objects.collect { case SmartIriLiteralV2(iri) =>
          iri
        }

      case None => Seq.empty[SmartIri]
    }

  /**
   * Returns the first object specified as an IRI for the given predicate.
   *
   * @param predicateIri the IRI of the predicate.
   * @return the predicate's object, if given.
   */
  def getPredicateIriObject(predicateIri: SmartIri): Option[SmartIri] = getPredicateIriObjects(predicateIri).headOption

  /**
   * Returns all the objects specified for a given predicate, along with the language tag of each object.
   *
   * @param predicateIri the IRI of the predicate.
   * @return a map of language tags to objects, or an empty map if this entity doesn't have the specified predicate.
   */
  def getPredicateObjectsWithLangs(predicateIri: SmartIri): Map[String, String] =
    predicates.get(predicateIri) match {
      case Some(predicateInfo) =>
        predicateInfo.objects.collect { case StringLiteralV2(str, Some(lang)) =>
          lang -> str
        }.toMap

      case None => Map.empty[String, String]
    }
}

/**
 * Processes predicates from a JSON-LD class or property definition.
 */
object EntityInfoContentV2 {
  private def stringToLiteral(str: String): StringLiteralV2 = {
    val value =
      Iri.toSparqlEncodedString(str).getOrElse(throw BadRequestException(s"Invalid predicate object: $str"))
    StringLiteralV2.from(value, None)
  }

  /**
   * Processes predicates from a JSON-LD class or property definition. Converts `@type` to `rdf:type`. Ignores
   * `\@id`, `rdfs:subClassOf` and `rdfs:subPropertyOf`.
   *
   * @param jsonLDObject the JSON-LD class or property definition.
   * @return a map of predicate IRIs to [[PredicateInfoV2]] objects.
   */
  def predicatesFromJsonLDObject(jsonLDObject: JsonLDObject): Map[SmartIri, PredicateInfoV2] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val entityType: SmartIri =
      jsonLDObject.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)

    val rdfType: (SmartIri, PredicateInfoV2) = OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
      predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
      objects = Seq(SmartIriLiteralV2(entityType)),
    )

    val predicates =
      jsonLDObject.value - JsonLDKeywords.ID - JsonLDKeywords.TYPE - Rdfs.SubClassOf - Rdfs.SubPropertyOf - OntologyConstants.Owl.WithRestrictions

    predicates.map { case (predicateIriStr: IRI, predicateValue: JsonLDValue) =>
      val predicateIri = predicateIriStr.toSmartIri

      val predicateInfo: PredicateInfoV2 = predicateValue match {
        case JsonLDString(objStr) =>
          PredicateInfoV2(
            predicateIri = predicateIri,
            objects = Seq(stringToLiteral(objStr)),
          )

        case JsonLDBoolean(objBoolean) =>
          PredicateInfoV2(
            predicateIri = predicateIri,
            objects = Seq(BooleanLiteralV2(objBoolean)),
          )

        case objObj: JsonLDObject =>
          if (objObj.isIri) {
            // This is a JSON-LD IRI value.
            PredicateInfoV2(
              predicateIri = predicateIri,
              objects = Seq(SmartIriLiteralV2(objObj.toIri(stringFormatter.toSmartIriWithErr))),
            )
          } else if (objObj.isStringWithLang) {
            // This is a string with a language tag.
            PredicateInfoV2(
              predicateIri = predicateIri,
              objects = JsonLDArray(Seq(objObj)).toObjsWithLang(),
            )
          } else {
            throw BadRequestException(s"Unexpected object value for predicate $predicateIri: $objObj")
          }

        case objArray: JsonLDArray =>
          if (objArray.value.isEmpty) {
            throw BadRequestException(s"No values provided for predicate $predicateIri")
          }

          if (objArray.value.forall(_.isInstanceOf[JsonLDString])) {
            // All the elements of the array are strings.
            PredicateInfoV2(
              predicateIri = predicateIri,
              objects = objArray.value.map {
                case JsonLDString(objStr) => stringToLiteral(objStr)
                case other                => throw AssertionException(s"Invalid object for predicate $predicateIriStr: $other")
              },
            )
          } else if (
            objArray.value.forall {
              case jsonObjElem: JsonLDObject if jsonObjElem.isIri =>
                // All the elements of the array are IRI values.
                true
              case _ => false
            }
          ) {
            PredicateInfoV2(
              predicateIri = predicateIri,
              objects = objArray.value.map {
                case jsonObjElem: JsonLDObject =>
                  SmartIriLiteralV2(jsonObjElem.toIri(stringFormatter.toSmartIriWithErr))
                case other => throw AssertionException(s"Invalid object for predicate $predicateIriStr: $other")
              },
            )
          } else if (
            objArray.value.forall {
              case jsonObjElem: JsonLDObject if jsonObjElem.isStringWithLang =>
                // All the elements of the array are strings with language codes.
                true
              case _ => false
            }
          ) {
            PredicateInfoV2(
              predicateIri = predicateIri,
              objects = objArray.toObjsWithLang(),
            )
          } else {
            throw BadRequestException(s"Invalid object for predicate $predicateIriStr: $predicateValue")
          }

        case other => throw BadRequestException(s"Invalid object for predicate $predicateIriStr: $other")
      }

      predicateIri -> predicateInfo
    } + rdfType
  }
}

/**
 * Represents information about an ontolgoy entity, as returned in an API response.
 */
sealed trait ReadEntityInfoV2 {

  /**
   * Provides basic information about the entity.
   */
  val entityInfoContent: EntityInfoContentV2

  protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Returns the contents of a JSON-LD object containing non-language-specific information about the entity.
   *
   * @param targetSchema the API v2 schema in which the response will be returned.
   */
  protected def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue]

  /**
   * Returns a JSON-LD object representing the entity, with language-specific information provided in a single language.
   *
   * @param targetSchema the API v2 schema in which the response will be returned.
   * @param userLang     the user's preferred language.
   * @param appConfig    the application's configuration.
   * @return a JSON-LD object representing the entity.
   */
  def toJsonLDWithSingleLanguage(
    targetSchema: ApiV2Schema,
    userLang: String,
    appConfig: AppConfig,
  ): JsonLDObject = {
    val label: Option[(IRI, JsonLDString)] = entityInfoContent
      .getPredicateAndStringLiteralObjectWithLang(Rdfs.Label.toSmartIri, appConfig, userLang)
      .map { case (k: SmartIri, v: String) =>
        (k.toString, JsonLDString(v))
      }

    val comment: Option[(IRI, JsonLDString)] = entityInfoContent
      .getPredicateAndStringLiteralObjectWithLang(Rdfs.Comment.toSmartIri, appConfig, userLang)
      .map { case (k: SmartIri, v: String) =>
        (k.toString, JsonLDString(v))
      }

    JsonLDObject(getNonLanguageSpecific(targetSchema) ++ label ++ comment)
  }

  /**
   * Returns a JSON-LD object representing the entity, with language-specific information provided in all
   * available languages.
   *
   * @param targetSchema the API v2 schema in which the response will be returned.
   * @return a JSON-LD object representing the entity.
   */
  def toJsonLDWithAllLanguages(targetSchema: ApiV2Schema): JsonLDObject = {
    val labelObjs: Map[String, String] =
      entityInfoContent.getPredicateObjectsWithLangs(Rdfs.Label.toSmartIri)

    val labels: Option[(IRI, JsonLDArray)] = if (labelObjs.nonEmpty) {
      Some(Rdfs.Label -> JsonLDUtil.objectsWithLangsToJsonLDArray(labelObjs))
    } else {
      None
    }

    val commentObjs: Map[String, String] =
      entityInfoContent.getPredicateObjectsWithLangs(Rdfs.Comment.toSmartIri)

    val comments: Option[(IRI, JsonLDArray)] = if (commentObjs.nonEmpty) {
      Some(Rdfs.Comment -> JsonLDUtil.objectsWithLangsToJsonLDArray(commentObjs))
    } else {
      None
    }

    JsonLDObject(getNonLanguageSpecific(targetSchema) ++ labels ++ comments)
  }
}

/**
 * Represents an OWL class definition as returned in an API response.
 *
 * @param entityInfoContent       a [[ReadClassInfoV2]] providing information about the class.
 * @param allBaseClasses          a seq of the IRIs of all the super-classes of this class.
 * @param isResourceClass         `true` if this is a subclass of `knora-base:Resource`.
 * @param isStandoffClass         `true` if this is a subclass of `knora-base:StandoffTag`.
 * @param isValueClass            `true` if the class is a Knora value class.
 * @param canBeInstantiated       `true` if the class is a Knora resource class that can be instantiated via the API.
 * @param inheritedCardinalities  a [[Map]] of properties to [[Cardinality]] objects representing the class's
 *                                inherited cardinalities on those properties.
 * @param standoffDataType        if this is a standoff tag class, the standoff datatype tag class (if any) that it
 *                                is a subclass of.
 * @param knoraResourceProperties a [[Set]] of IRIs of properties in `allCardinalities` that are subproperties of `knora-base:resourceProperty`.
 * @param linkProperties          a [[Set]] of IRIs of properties in `allCardinalities` that point to resources.
 * @param linkValueProperties     a [[Set]] of IRIs of properties in `allCardinalities` that point to `LinkValue` objects.
 * @param fileValueProperties     a [[Set]] of IRIs of properties in `allCardinalities` that point to `FileValue` objects.
 */
case class ReadClassInfoV2(
  entityInfoContent: ClassInfoContentV2,
  allBaseClasses: Seq[SmartIri],
  isResourceClass: Boolean = false,
  isStandoffClass: Boolean = false,
  isValueClass: Boolean = false,
  canBeInstantiated: Boolean = false,
  inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty[SmartIri, KnoraCardinalityInfo],
  standoffDataType: Option[StandoffDataTypeClasses.Value] = None,
  knoraResourceProperties: Set[SmartIri] = Set.empty[SmartIri],
  linkProperties: Set[SmartIri] = Set.empty[SmartIri],
  linkValueProperties: Set[SmartIri] = Set.empty[SmartIri],
  fileValueProperties: Set[SmartIri] = Set.empty[SmartIri],
) extends ReadEntityInfoV2
    with KnoraReadV2[ReadClassInfoV2] {

  /**
   * All the class's cardinalities, both direct and indirect.
   */
  lazy val allCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
    inheritedCardinalities ++ entityInfoContent.directCardinalities

  /**
   * All the class's cardinalities for subproperties of `knora-base:resourceProperty`.
   */
  lazy val allResourcePropertyCardinalities: Map[SmartIri, KnoraCardinalityInfo] = allCardinalities.filter {
    case (propertyIri, _) => knoraResourceProperties.contains(propertyIri)
  }

  override def toOntologySchema(targetSchema: ApiV2Schema): ReadClassInfoV2 = {
    // Get rules for transforming internal entities to external entities in the target schema.
    val transformationRules =
      OntologyTransformationRules.getTransformationRules(targetSchema)

    // If we're converting to the simplified API v2 schema, remove references to link value properties.

    val linkValuePropsForSchema = if (targetSchema == ApiV2Simple) {
      Set.empty[SmartIri]
    } else {
      linkValueProperties
    }

    val inheritedCardinalitiesConsideringLinkValueProps = if (targetSchema == ApiV2Simple) {
      inheritedCardinalities.filterNot { case (propertyIri, _) =>
        linkValueProperties.contains(propertyIri)
      }
    } else {
      inheritedCardinalities
    }

    val directCardinalitiesConsideringLinkValueProps = if (targetSchema == ApiV2Simple) {
      entityInfoContent.directCardinalities.filterNot { case (propertyIri, _) =>
        linkValueProperties.contains(propertyIri)
      }
    } else {
      entityInfoContent.directCardinalities
    }

    val knoraResourcePropertiesConsideringLinkValueProps = if (targetSchema == ApiV2Simple) {
      knoraResourceProperties -- linkValueProperties
    } else {
      knoraResourceProperties
    }

    // Remove inherited cardinalities for internal properties that don't exist in the target schema.

    val inheritedCardinalitiesFilteredForTargetSchema: Map[SmartIri, KnoraCardinalityInfo] =
      inheritedCardinalitiesConsideringLinkValueProps.filterNot { case (propertyIri, _) =>
        transformationRules.internalPropertiesToRemove.contains(propertyIri)
      }

    // Remove base classes that don't exist in the target schema.

    val allBaseClassesFilteredForTargetSchema = allBaseClasses.diff(transformationRules.internalClassesToRemove.toSeq)

    // Convert all IRIs to the target schema.

    val allBaseClassesInTargetSchema = allBaseClassesFilteredForTargetSchema.map(_.toOntologySchema(targetSchema))

    val entityInfoContentInTargetSchema = entityInfoContent
      .copy(
        directCardinalities = directCardinalitiesConsideringLinkValueProps,
      )
      .toOntologySchema(targetSchema)

    val inheritedCardinalitiesInTargetSchema: Map[SmartIri, KnoraCardinalityInfo] =
      inheritedCardinalitiesFilteredForTargetSchema.map { case (propertyIri, cardinality) =>
        propertyIri.toOntologySchema(targetSchema) -> cardinality
      }

    val knoraResourcePropertiesInTargetSchema =
      knoraResourcePropertiesConsideringLinkValueProps.map(_.toOntologySchema(targetSchema))
    val linkPropertiesInTargetSchema      = linkProperties.map(_.toOntologySchema(targetSchema))
    val linkValuePropertiesInTargetSchema = linkValuePropsForSchema.map(_.toOntologySchema(targetSchema))
    val fileValuePropertiesInTargetSchema = fileValueProperties.map(_.toOntologySchema(targetSchema))

    // Add cardinalities that this class inherits in the target schema but not in the source schema.

    val baseClassesInTargetSchema: Seq[SmartIri] = allBaseClasses.map(_.toOntologySchema(targetSchema))

    val inheritedCardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] = baseClassesInTargetSchema.flatMap {
      baseClassIri =>
        transformationRules.externalCardinalitiesToAdd.getOrElse(
          baseClassIri,
          Map.empty[SmartIri, KnoraCardinalityInfo],
        )
    }.toMap

    val inheritedCardinalitiesWithExtraOnesForSchema: Map[SmartIri, KnoraCardinalityInfo] =
      inheritedCardinalitiesInTargetSchema ++ inheritedCardinalitiesToAdd

    copy(
      entityInfoContent = entityInfoContentInTargetSchema,
      allBaseClasses = allBaseClassesInTargetSchema,
      inheritedCardinalities = inheritedCardinalitiesWithExtraOnesForSchema,
      knoraResourceProperties = knoraResourcePropertiesInTargetSchema,
      linkProperties = linkPropertiesInTargetSchema,
      linkValueProperties = linkValuePropertiesInTargetSchema,
      fileValueProperties = fileValuePropertiesInTargetSchema,
    )
  }

  def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
    if (entityInfoContent.ontologySchema != targetSchema) {
      throw DataConversionException(
        s"ReadClassInfoV2 for class ${entityInfoContent.classIri} is not in schema $targetSchema",
      )
    }

    // Convert OWL cardinalities to JSON-LD.
    val owlCardinalities: Seq[JsonLDObject] = allCardinalities.toArray.sortBy { case (propertyIri, _) =>
      propertyIri
    }.sortBy { case (_, cardinalityInfo: KnoraCardinalityInfo) =>
      cardinalityInfo.guiOrder
    }.toIndexedSeq.map { case (propertyIri: SmartIri, cardinalityInfo: KnoraCardinalityInfo) =>
      val prop2card: (IRI, JsonLDInt) = {
        val owl = Cardinality.toOwl(cardinalityInfo.cardinality)
        (owl.owlCardinalityIri, JsonLDInt(owl.owlCardinalityValue))
      }

      // If we're using the complex schema and the cardinality is inherited, add an annotation to say so.
      val isInheritedStatement =
        if (targetSchema == ApiV2Complex && !entityInfoContent.directCardinalities.contains(propertyIri)) {
          Some(KnoraApiV2Complex.IsInherited -> JsonLDBoolean(true))
        } else {
          None
        }

      val guiOrderStatement = targetSchema match {
        case ApiV2Complex =>
          cardinalityInfo.guiOrder.map { guiOrder =>
            SalsahGui.External.GuiOrder -> JsonLDInt(guiOrder)
          }

        case _ => None
      }

      JsonLDObject(
        Map(
          JsonLDKeywords.TYPE              -> JsonLDString(OntologyConstants.Owl.Restriction),
          OntologyConstants.Owl.OnProperty -> JsonLDUtil.iriToJsonLDObject(propertyIri.toString),
          prop2card,
        ) ++ isInheritedStatement ++ guiOrderStatement,
      )
    }

    val resourceIconPred = targetSchema match {
      case ApiV2Simple  => OntologyConstants.KnoraApiV2Simple.ResourceIcon
      case ApiV2Complex => KnoraApiV2Complex.ResourceIcon
    }

    val resourceIconStatement: Option[(IRI, JsonLDString)] =
      entityInfoContent.getPredicateStringLiteralObjectsWithoutLang(resourceIconPred.toSmartIri).headOption.map {
        resIcon =>
          resourceIconPred -> JsonLDString(resIcon)
      }

    val jsonRestriction: Map[IRI, JsonLDValue] = entityInfoContent.datatypeInfo match {
      case Some(datatypeInfo: DatatypeInfoV2) =>
        Map(
          OntologyConstants.Owl.OnDatatype -> JsonLDUtil.iriToJsonLDObject(datatypeInfo.onDatatype.toString),
        ) ++ datatypeInfo.pattern.map { pattern =>
          OntologyConstants.Owl.WithRestrictions -> JsonLDArray(
            Seq(
              JsonLDObject(Map(OntologyConstants.Xsd.Pattern -> JsonLDString(pattern))),
            ),
          )
        }

      case None => Map.empty
    }

    val jsonSubClassOf = entityInfoContent.subClassOf.toArray.sorted.map { superClass =>
      JsonLDUtil.iriToJsonLDObject(superClass.toString)
    } ++ owlCardinalities

    val jsonSubClassOfStatement: Option[(IRI, JsonLDArray)] = if (jsonSubClassOf.nonEmpty) {
      Some(Rdfs.SubClassOf -> JsonLDArray(jsonSubClassOf.toIndexedSeq))
    } else {
      None
    }

    val isKnoraResourceClassStatement: Option[(IRI, JsonLDBoolean)] =
      if (isResourceClass && targetSchema == ApiV2Complex) {
        Some(KnoraApiV2Complex.IsResourceClass -> JsonLDBoolean(true))
      } else {
        None
      }

    val isStandoffClassStatement: Option[(IRI, JsonLDBoolean)] = if (isStandoffClass && targetSchema == ApiV2Complex) {
      Some(KnoraApiV2Complex.IsStandoffClass -> JsonLDBoolean(true))
    } else {
      None
    }

    val canBeInstantiatedStatement: Option[(IRI, JsonLDBoolean)] =
      if (canBeInstantiated && targetSchema == ApiV2Complex) {
        Some(KnoraApiV2Complex.CanBeInstantiated -> JsonLDBoolean(true))
      } else {
        None
      }

    val isValueClassStatement: Option[(IRI, JsonLDBoolean)] = if (isValueClass && targetSchema == ApiV2Complex) {
      Some(KnoraApiV2Complex.IsValueClass -> JsonLDBoolean(true))
    } else {
      None
    }

    Map(
      JsonLDKeywords.ID   -> JsonLDString(entityInfoContent.classIri.toString),
      JsonLDKeywords.TYPE -> JsonLDArray(entityInfoContent.getRdfTypes.map(typeIri => JsonLDString(typeIri.toString))),
    ) ++ jsonSubClassOfStatement ++ resourceIconStatement ++ isKnoraResourceClassStatement ++
      isStandoffClassStatement ++ canBeInstantiatedStatement ++ isValueClassStatement ++ jsonRestriction
  }
}

final case class ReadClassInfoV2Builder(
  classIri: SmartIri,
  datatypeInfo: Option[DatatypeInfoV2] = None,
  predicates: List[PredicateInfoV2] = List.empty,
  ontologySchema: ApiV2Schema = ApiV2Complex,
  allBaseClasses: List[SmartIri] = List.empty,
) { self =>
  def withApiV2SimpleSchema: ReadClassInfoV2Builder =
    copy(ontologySchema = ApiV2Simple)

  def withRdfType(classIri: Rdf4jIRI)(implicit sf: StringFormatter): ReadClassInfoV2Builder = {
    val rdfType =
      PredicateInfoV2Builder.make(RDF.TYPE).withObject(SmartIriLiteralV2(sf.toSmartIri(classIri.toString))).build()
    copy(predicates = rdfType :: self.predicates)
  }

  def withBaseClass(classIri: SmartIri)(implicit sf: StringFormatter): ReadClassInfoV2Builder =
    copy(allBaseClasses = classIri :: self.allBaseClasses)

  def withRdfsLabelEn(label: String)(implicit sf: StringFormatter): ReadClassInfoV2Builder =
    self.withPredicate(PredicateInfoV2Builder.makeRdfsLabelEn(label))

  def withRdfsCommentEn(comment: String)(implicit sf: StringFormatter): ReadClassInfoV2Builder =
    self.withPredicate(PredicateInfoV2Builder.makeRdfsCommentEn(comment))

  def withPredicate(builder: PredicateInfoV2Builder): ReadClassInfoV2Builder =
    copy(predicates = builder.build() :: self.predicates)

  def build(): ReadClassInfoV2 = {
    val predicatesMap = predicates.map(p => p.predicateIri -> p).toMap
    val classInfo = ClassInfoContentV2(
      classIri = classIri,
      datatypeInfo = datatypeInfo,
      predicates = predicatesMap,
      ontologySchema = ontologySchema,
    )

    ReadClassInfoV2(classInfo, self.allBaseClasses)
  }
}
object ReadClassInfoV2Builder {
  def makeStringDatatypeClassWithPattern(classIri: String, pattern: String)(implicit
    sf: StringFormatter,
  ): ReadClassInfoV2Builder =
    makeDatatypeClass(sf.toSmartIri(classIri), DatatypeInfoV2(sf.toSmartIri(XSD.STRING.toString), Some(pattern)))

  def makeStringDatatypeClass(classIri: String)(implicit sf: StringFormatter): ReadClassInfoV2Builder =
    makeDatatypeClass(sf.toSmartIri(classIri), DatatypeInfoV2(sf.toSmartIri(XSD.STRING.toString), None))

  def makeUriDatatypeClass(classIri: String)(implicit sf: StringFormatter): ReadClassInfoV2Builder =
    makeDatatypeClass(sf.toSmartIri(classIri), DatatypeInfoV2(sf.toSmartIri(XSD.ANYURI.toString), None))

  def makeDatatypeClass(classIri: SmartIri, datatypeInfo: DatatypeInfoV2)(implicit
    sf: StringFormatter,
  ): ReadClassInfoV2Builder =
    ReadClassInfoV2Builder(classIri, Some(datatypeInfo)).withRdfType(RDFS.DATATYPE).withBaseClass(classIri)
}

/**
 * Represents an OWL property definition as returned in an API response.
 *
 * @param entityInfoContent                   a [[PropertyInfoContentV2]] providing information about the property.
 * @param isResourceProp                      `true` if the property is a subproperty of `knora-base:resourceProperty`.
 * @param isEditable                          `true` if the property's value is editable via the Knora API.
 * @param isLinkProp                          `true` if the property is a subproperty of `knora-base:hasLinkTo`.
 * @param isLinkValueProp                     `true` if the property is a subproperty of `knora-base:hasLinkToValue`.
 * @param isFileValueProp                     `true` if the property is a subproperty of `knora-base:hasFileValue`.
 * @param isStandoffInternalReferenceProperty if `true`, this is a subproperty (directly or indirectly) of
 *                                            [[OntologyConstants.KnoraBase.StandoffTagHasInternalReference]].
 */
case class ReadPropertyInfoV2(
  entityInfoContent: PropertyInfoContentV2,
  isResourceProp: Boolean = false,
  isEditable: Boolean = false,
  isLinkProp: Boolean = false,
  isLinkValueProp: Boolean = false,
  isFileValueProp: Boolean = false,
  isStandoffInternalReferenceProperty: Boolean = false,
) extends ReadEntityInfoV2
    with KnoraReadV2[ReadPropertyInfoV2] {

  def propertyIri: PropertyIri = PropertyIri.unsafeFrom(entityInfoContent.propertyIri)

  override def toOntologySchema(targetSchema: ApiV2Schema): ReadPropertyInfoV2 = copy(
    entityInfoContent = entityInfoContent.toOntologySchema(targetSchema),
  )

  def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
    if (entityInfoContent.ontologySchema != targetSchema) {
      throw DataConversionException(
        s"ReadPropertyInfoV2 for property ${entityInfoContent.propertyIri} is not in schema $targetSchema",
      )
    }

    // Get the correct knora-api:subjectType and knora-api:objectType predicates for the target API schema.
    val (subjectTypePred: IRI, objectTypePred: IRI) = targetSchema match {
      case ApiV2Simple =>
        (OntologyConstants.KnoraApiV2Simple.SubjectType, OntologyConstants.KnoraApiV2Simple.ObjectType)
      case ApiV2Complex =>
        (KnoraApiV2Complex.SubjectType, KnoraApiV2Complex.ObjectType)
    }

    // Get the property's knora-api:subjectType and knora-api:objectType, if provided.
    val (maybeSubjectType: Option[SmartIri], maybeObjectType: Option[SmartIri]) =
      entityInfoContent.ontologySchema match {
        case InternalSchema =>
          throw DataConversionException(
            s"ReadPropertyInfoV2 for property ${entityInfoContent.propertyIri} is not in schema $targetSchema",
          )

        case ApiV2Simple =>
          (
            entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri),
            entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri),
          )

        case ApiV2Complex =>
          (
            entityInfoContent.getPredicateIriObject(KnoraApiV2Complex.SubjectType.toSmartIri),
            entityInfoContent.getPredicateIriObject(KnoraApiV2Complex.ObjectType.toSmartIri),
          )
      }

    // Make the property's knora-api:subjectType and knora-api:objectType statements.
    val subjectTypeStatement: Option[(IRI, JsonLDObject)] =
      maybeSubjectType.map(subjectTypeObj => (subjectTypePred, JsonLDUtil.iriToJsonLDObject(subjectTypeObj.toString)))
    val objectTypeStatement: Option[(IRI, JsonLDObject)] =
      maybeObjectType.map(objectTypeObj => (objectTypePred, JsonLDUtil.iriToJsonLDObject(objectTypeObj.toString)))

    val jsonSubPropertyOf: Seq[JsonLDObject] = entityInfoContent.subPropertyOf.toSeq.sorted.map { superProperty =>
      JsonLDUtil.iriToJsonLDObject(superProperty.toString)
    }

    val jsonSubPropertyOfStatement: Option[(IRI, JsonLDArray)] = if (jsonSubPropertyOf.nonEmpty) {
      Some(Rdfs.SubPropertyOf -> JsonLDArray(jsonSubPropertyOf))
    } else {
      None
    }

    val isResourcePropStatement: Option[(IRI, JsonLDBoolean)] = if (isResourceProp && targetSchema == ApiV2Complex) {
      Some(KnoraApiV2Complex.IsResourceProperty -> JsonLDBoolean(true))
    } else {
      None
    }

    val isEditableStatement: Option[(IRI, JsonLDBoolean)] = if (isEditable && targetSchema == ApiV2Complex) {
      Some(KnoraApiV2Complex.IsEditable -> JsonLDBoolean(true))
    } else {
      None
    }

    val isLinkValuePropertyStatement: Option[(IRI, JsonLDBoolean)] =
      if (isLinkValueProp && targetSchema == ApiV2Complex) {
        Some(KnoraApiV2Complex.IsLinkValueProperty -> JsonLDBoolean(true))
      } else {
        None
      }

    val isLinkPropertyStatement: Option[(IRI, JsonLDBoolean)] = if (isLinkProp && targetSchema == ApiV2Complex) {
      Some(KnoraApiV2Complex.IsLinkProperty -> JsonLDBoolean(true))
    } else {
      None
    }

    val guiElementStatement: Option[(IRI, JsonLDObject)] = if (targetSchema == ApiV2Complex) {
      entityInfoContent
        .getPredicateIriObject(SalsahGui.External.GuiElementProp.toSmartIri)
        .map { obj =>
          SalsahGui.External.GuiElementProp -> JsonLDUtil.iriToJsonLDObject(obj.toString)
        }
    } else {
      None
    }

    val guiAttributeStatement = if (targetSchema == ApiV2Complex) {
      entityInfoContent.getPredicateStringLiteralObjectsWithoutLang(
        SalsahGui.External.GuiAttribute.toSmartIri,
      ) match {
        case objs if objs.nonEmpty =>
          Some(
            SalsahGui.External.GuiAttribute -> JsonLDArray(
              objs.toArray.sorted.map(JsonLDString.apply).toIndexedSeq,
            ),
          )

        case _ => None
      }
    } else {
      None
    }

    Map(
      JsonLDKeywords.ID   -> JsonLDString(entityInfoContent.propertyIri.toString),
      JsonLDKeywords.TYPE -> JsonLDArray(entityInfoContent.getRdfTypes.map(typeIri => JsonLDString(typeIri.toString))),
    ) ++ jsonSubPropertyOfStatement ++ subjectTypeStatement ++ objectTypeStatement ++
      isResourcePropStatement ++ isEditableStatement ++ isLinkValuePropertyStatement ++
      isLinkPropertyStatement ++ guiElementStatement ++ guiAttributeStatement
  }
}

final case class ReadPropertyInfoV2Builder private (
  // PropertyInfoContentV2 fields
  propertyIri: SmartIri,
  predicates: Map[SmartIri, PredicateInfoV2] = Map.empty,
  subPropertyOf: Set[SmartIri] = Set.empty,
  ontologySchema: OntologySchema = ApiV2Complex,
  objectType: Option[SmartIri] = None,
  subjectType: Option[SmartIri] = None,
  // ReadPropertyInfoV2 other fields
  isResourceProp: Boolean = false,
  isEditable: Boolean = false,
  isLinkProp: Boolean = false,
  isLinkValueProp: Boolean = false,
  isFileValueProp: Boolean = false,
  isStandoffInternalReferenceProperty: Boolean = false,
) { self =>
  private def withPredicate(v: PredicateInfoV2): ReadPropertyInfoV2Builder =
    copy(predicates = self.predicates + (v.predicateIri -> v))

  private def withRdfType(propertyType: SmartIri)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    withPredicate(PredicateInfoV2Builder.makeRdfType(propertyType).build())

  def withSubjectType(subjectType: Rdf4jIRI)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    withSubjectType(sf.toSmartIri(subjectType.toString))
  def withSubjectType(subjectType: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    withSubjectType(sf.toSmartIri(subjectType))
  def withSubjectType(subjectType: SmartIri)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    self.copy(subjectType = Some(subjectType))

  private def withObjectType(valueType: SmartIri): ReadPropertyInfoV2Builder = self.copy(objectType = Some(valueType))

  def withSubPropertyOf(propertyIri: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    self.copy(subPropertyOf = self.subPropertyOf + sf.toSmartIri(propertyIri))

  def withIsResourceProp(): ReadPropertyInfoV2Builder  = self.copy(isResourceProp = true)
  def withIsLinkValueProp(): ReadPropertyInfoV2Builder = self.copy(isLinkValueProp = true)

  def withRdfLabelEn(label: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    withPredicate(PredicateInfoV2Builder.makeRdfsLabelEn(label).build())
  def withRdfLabel(label: Map[LanguageCode, String])(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    withPredicate(PredicateInfoV2Builder.makeRdfsLabel(label).build())

  def withRdfCommentEn(comment: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    withPredicate(PredicateInfoV2Builder.makeRdfsCommentEn(comment).build())

  def withApiV2SimpleSchema: ReadPropertyInfoV2Builder = self.copy(ontologySchema = ApiV2Simple)

  def build()(implicit sf: StringFormatter): ReadPropertyInfoV2 = {
    def mk(predIri: SmartIri, iriLit: SmartIri) =
      PredicateInfoV2Builder.make(predIri).withObject(SmartIriLiteralV2(iriLit)).build()
    val (objectTypePropIri, subjectTypePropIri) = ontologySchema match {
      case ApiV2Simple  => (sf.toSmartIri(KnoraApiV2Simple.ObjectType), sf.toSmartIri(KnoraApiV2Simple.SubjectType))
      case ApiV2Complex => (sf.toSmartIri(KnoraApiV2Complex.ObjectType), sf.toSmartIri(KnoraApiV2Complex.SubjectType))
      case _            => throw IllegalArgumentException(s"Only V2 is supported, this is unsupported $ontologySchema")
    }
    val predicates = self.predicates ++
      self.objectType.map(objectTypePropIri -> mk(objectTypePropIri, _)) ++
      self.subjectType.map(subjectTypePropIri -> mk(subjectTypePropIri, _))

    ReadPropertyInfoV2(
      PropertyInfoContentV2(propertyIri, predicates, subPropertyOf, ontologySchema),
      isResourceProp,
      isEditable,
      isLinkProp,
      isLinkValueProp,
      isFileValueProp,
      isStandoffInternalReferenceProperty,
    )
  }

}
object ReadPropertyInfoV2Builder {
  def makeOwlAnnotationProperty(iri: String, objectType: Rdf4jIRI)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = makeOwlAnnotationProperty(iri, objectType.toString)

  def makeOwlAnnotationProperty(iri: String, objectType: String)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = makeOwlAnnotationProperty(sf.toSmartIri(iri), sf.toSmartIri(objectType))

  def makeOwlAnnotationProperty(iri: SmartIri, objectType: SmartIri)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = make(iri, OWL.ANNOTATIONPROPERTY).withObjectType(objectType)

  def makeOwlDatatypeProperty(iri: String, objectType: Rdf4jIRI)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = makeOwlDatatypeProperty(iri, objectType.toString)

  def makeOwlDatatypeProperty(iri: Rdf4jIRI, objectType: Rdf4jIRI)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = makeOwlDatatypeProperty(iri.toString, objectType.toString)

  def makeOwlDatatypeProperty(iri: String, objectType: String)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = makeOwlDatatypeProperty(sf.toSmartIri(iri), sf.toSmartIri(objectType))

  def makeOwlDatatypeProperty(iri: SmartIri, objectType: SmartIri)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = makeOwlDatatypeProperty(iri).withObjectType(objectType)

  def makeOwlDatatypeProperty(iri: Rdf4jIRI)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    makeOwlDatatypeProperty(sf.toSmartIri(iri.toString))

  def makeOwlDatatypeProperty(iri: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    makeOwlDatatypeProperty(sf.toSmartIri(iri))

  def makeOwlDatatypeProperty(iri: SmartIri)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    make(iri, OWL.DATATYPEPROPERTY)

  def makeOwlObjectProperty(iri: String, objectType: Rdf4jIRI)(implicit
    sf: StringFormatter,
  ): ReadPropertyInfoV2Builder = makeOwlObjectProperty(iri, objectType.toString)

  def makeOwlObjectProperty(iri: String, objectType: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    makeOwlObjectProperty(sf.toSmartIri(iri)).withObjectType(sf.toSmartIri(objectType))

  def makeOwlObjectProperty(iri: Rdf4jIRI)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    makeOwlObjectProperty(sf.toSmartIri(iri.toString))

  def makeOwlObjectProperty(iri: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    makeOwlObjectProperty(sf.toSmartIri(iri))

  def makeOwlObjectProperty(iri: SmartIri)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    make(iri, OWL.OBJECTPROPERTY)

  def makeRdfProperty(iri: String)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    make(iri, RDF.PROPERTY)

  def make(iri: SmartIri, rdfType: SmartIri)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    ReadPropertyInfoV2Builder(iri).withRdfType(rdfType)

  def make(iri: SmartIri, rdfType: Rdf4jIRI)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    make(iri, sf.toSmartIri(rdfType.toString))

  def make(iri: String, rdfType: Rdf4jIRI)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    make(sf.toSmartIri(iri), sf.toSmartIri(rdfType.toString))

  def make(iri: Rdf4jIRI, rdfType: Rdf4jIRI)(implicit sf: StringFormatter): ReadPropertyInfoV2Builder =
    make(sf.toSmartIri(iri.toString), sf.toSmartIri(rdfType.toString))
}

/**
 * Represents an OWL named individual definition as returned in an API response.
 *
 * @param entityInfoContent an [[IndividualInfoContentV2]] representing information about the named individual.
 */
case class ReadIndividualInfoV2(entityInfoContent: IndividualInfoContentV2)
    extends ReadEntityInfoV2
    with KnoraReadV2[ReadIndividualInfoV2] {
  override def toOntologySchema(targetSchema: ApiV2Schema): ReadIndividualInfoV2 = copy(
    entityInfoContent = entityInfoContent.toOntologySchema(targetSchema),
  )

  override protected def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
    val jsonLDPredicates: Map[IRI, JsonLDValue] = entityInfoContent.predicates.foldLeft(Map.empty[IRI, JsonLDValue]) {
      case (acc, (predicateIri, predicateInfo)) =>
        if (predicateInfo.objects.nonEmpty) {
          val nonLanguageSpecificObjectsAsJson: Seq[JsonLDValue] = predicateInfo.objects.collect {
            case StringLiteralV2(str, None) => (JsonLDString(str), str)
            case SmartIriLiteralV2(iri) =>
              val iriStr = iri.toString
              (JsonLDUtil.iriToJsonLDObject(iri.toString), iriStr)
          }
            .sortBy(_._2)
            .map(_._1) // Sort for determinism in testing.

          acc + (predicateIri.toString -> JsonLDArray(nonLanguageSpecificObjectsAsJson))
        } else {
          acc
        }
    }

    Map(
      JsonLDKeywords.ID -> JsonLDString(entityInfoContent.individualIri.toString),
    ) ++ jsonLDPredicates
  }
}

/**
 * Represents a definition of an `rdfs:Datatype`.
 *
 * @param onDatatype the base datatype to be extended.
 * @param pattern    an optional `xsd:pattern` specifying the regular expression that restricts its values.
 */
case class DatatypeInfoV2(onDatatype: SmartIri, pattern: Option[String] = None)

/**
 * Represents assertions about an OWL class.
 *
 * @param classIri            the IRI of the class.
 * @param predicates          a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
 * @param directCardinalities a [[Map]] of properties to [[Cardinality]] objects representing the cardinalities
 *                            that are directly defined on the class (as opposed to inherited) on those properties.
 * @param datatypeInfo        if the class's `rdf:type` is `rdfs:Datatype`, a [[DatatypeInfoV2]] describing it.
 * @param subClassOf          the classes that this class is a subclass of.
 * @param ontologySchema      indicates whether this ontology entity belongs to an internal ontology (for use in the
 *                            triplestore) or an external one (for use in the Knora API).
 */
case class ClassInfoContentV2(
  classIri: SmartIri,
  predicates: Map[SmartIri, PredicateInfoV2] = Map.empty[SmartIri, PredicateInfoV2],
  directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty[SmartIri, KnoraCardinalityInfo],
  datatypeInfo: Option[DatatypeInfoV2] = None,
  subClassOf: Set[SmartIri] = Set.empty[SmartIri],
  ontologySchema: OntologySchema,
) extends EntityInfoContentV2
    with KnoraContentV2[ClassInfoContentV2] {
  override def toOntologySchema(targetSchema: OntologySchema): ClassInfoContentV2 = {
    val classIriInTargetSchema = classIri.toOntologySchema(targetSchema)

    // Get rules for transforming internal entities to external entities in the target schema, if relevant.
    val maybeTransformationRules: Option[OntologyTransformationRules] = targetSchema match {
      case apiV2Schema: ApiV2Schema =>
        Some(OntologyTransformationRules.getTransformationRules(apiV2Schema))
      case InternalSchema => None
    }

    // Remove cardinalities for internal properties that don't exist in the target schema.

    val knoraBasePropertiesToRemove: Set[SmartIri] = maybeTransformationRules match {
      case Some(transformationRules) => transformationRules.internalPropertiesToRemove
      case _                         => Set.empty[SmartIri]
    }

    val directCardinalitiesFilteredForTargetSchema: Map[SmartIri, KnoraCardinalityInfo] =
      directCardinalities.filterNot { case (propertyIri, _) =>
        knoraBasePropertiesToRemove.contains(propertyIri)
      }

    val subClassOfFilteredForTargetSchema = subClassOf.filterNot { baseClass =>
      maybeTransformationRules match {
        case Some(transformationRules) => transformationRules.internalClassesToRemove.contains(baseClass)
        case None                      => false
      }
    }

    // Convert the property IRIs of the remaining cardinalities to the target schema.

    val directCardinalitiesInTargetSchema: Map[SmartIri, KnoraCardinalityInfo] =
      directCardinalitiesFilteredForTargetSchema.map { case (propertyIri, cardinality) =>
        propertyIri.toOntologySchema(targetSchema) -> cardinality
      }

    // Add any cardinalities that this class has in the external schema but not in the internal schema.

    val cardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] = maybeTransformationRules match {
      case Some(transformationRules) =>
        transformationRules.externalCardinalitiesToAdd.getOrElse(
          classIriInTargetSchema,
          Map.empty[SmartIri, KnoraCardinalityInfo],
        )

      case None => Map.empty[SmartIri, KnoraCardinalityInfo]
    }

    val directCardinalitiesWithExtraOnesForSchema: Map[SmartIri, KnoraCardinalityInfo] =
      directCardinalitiesInTargetSchema ++ cardinalitiesToAdd

    val predicatesInTargetSchema = predicates.map { case (predicateIri, predicate) =>
      predicateIri.toOntologySchema(targetSchema) -> predicate.toOntologySchema(targetSchema)
    }

    val subClassOfInTargetSchema = subClassOfFilteredForTargetSchema.map(_.toOntologySchema(targetSchema))

    copy(
      classIri = classIriInTargetSchema,
      predicates = predicatesInTargetSchema,
      directCardinalities = directCardinalitiesWithExtraOnesForSchema,
      subClassOf = subClassOfInTargetSchema,
      ontologySchema = targetSchema,
    )
  }

  override def getRdfType: SmartIri = {
    val classTypeSet: Seq[SmartIri] = getRdfTypes.filter { classType =>
      OntologyConstants.ClassTypes.contains(classType.toString)
    }

    if (classTypeSet.size == 1) {
      classTypeSet.head
    } else {
      throw InconsistentRepositoryDataException(s"The rdf:type of $classIri is invalid")
    }
  }

  override def getRdfTypes: Seq[SmartIri] =
    requireIriObjects(
      OntologyConstants.Rdf.Type.toSmartIri,
      throw InconsistentRepositoryDataException(s"The rdf:type of $classIri is missing or invalid"),
    ).toVector.sorted

  /**
   * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used after an update, when the
   * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
   * read back from the triplestore (in which predicate objects are not escaped).
   *
   * @return a copy of this object with its predicate objects unescaped.
   */
  def unescape: ClassInfoContentV2 =
    copy(predicates = unescapePredicateObjects)
}

/**
 * Represents assertions about an RDF property.
 *
 * @param propertyIri    the IRI of the queried property.
 * @param predicates     a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
 * @param subPropertyOf  the property's direct superproperties.
 * @param ontologySchema indicates whether this ontology entity belongs to an internal ontology (for use in the
 *                       triplestore) or an external one (for use in the Knora API).
 */
case class PropertyInfoContentV2(
  propertyIri: SmartIri,
  predicates: Map[SmartIri, PredicateInfoV2] = Map.empty[SmartIri, PredicateInfoV2],
  subPropertyOf: Set[SmartIri] = Set.empty[SmartIri],
  ontologySchema: OntologySchema,
) extends EntityInfoContentV2
    with KnoraContentV2[PropertyInfoContentV2] {

  override def toOntologySchema(targetSchema: OntologySchema): PropertyInfoContentV2 = {

    // Are we converting from the internal schema to the API v2 simple schema?
    val predicatesWithAdjustedRdfType: Map[SmartIri, PredicateInfoV2] =
      if (ontologySchema == InternalSchema && targetSchema == ApiV2Simple) {
        // Yes. Is this an object property?
        val rdfTypeIri = OntologyConstants.Rdf.Type.toSmartIri
        val sourcePropertyType: SmartIri = getPredicateIriObject(rdfTypeIri).getOrElse(
          throw InconsistentRepositoryDataException(s"Property $propertyIri has no rdf:type"),
        )

        if (sourcePropertyType.toString == OntologyConstants.Owl.ObjectProperty) {
          // Yes. See if we need to change it to a datatype property. Does it have a knora-base:objectClassConstraint?
          val objectClassConstraintIri          = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri
          val maybeObjectType: Option[SmartIri] = getPredicateIriObject(objectClassConstraintIri)

          maybeObjectType match {
            case Some(objectTypeObj) =>
              // Yes. Is there a corresponding type in the API v2 simple ontology?
              if (
                OntologyConstants
                  .CorrespondingIris((InternalSchema, ApiV2Simple))
                  .contains(objectTypeObj.toString)
              ) {
                // Yes. The corresponding type must be a datatype, so make this a datatype property.
                (predicates - rdfTypeIri) +
                  (rdfTypeIri -> PredicateInfoV2(
                    predicateIri = rdfTypeIri,
                    objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.DatatypeProperty.toSmartIri)),
                  ))
              } else {
                predicates
              }
            case None => predicates
          }
        } else {
          predicates
        }
      } else {
        predicates
      }

    // Remove any references to base properties that don't exist in the target schema.

    val maybeTransformationRules: Option[OntologyTransformationRules] = targetSchema match {
      case apiV2Schema: ApiV2Schema =>
        Some(OntologyTransformationRules.getTransformationRules(apiV2Schema))
      case InternalSchema => None
    }

    val subPropertyOfFilteredForTargetSchema = subPropertyOf.filterNot { baseProperty =>
      maybeTransformationRules match {
        case Some(transformationRules) => transformationRules.internalPropertiesToRemove.contains(baseProperty)
        case None                      => false
      }
    }

    // Convert all IRIs to the target schema.

    val predicatesInTargetSchema = predicatesWithAdjustedRdfType.map { case (predicateIri, predicate) =>
      predicateIri.toOntologySchema(targetSchema) -> predicate.toOntologySchema(targetSchema)
    }

    val subPropertyOfInTargetSchema = subPropertyOfFilteredForTargetSchema.map(_.toOntologySchema(targetSchema))

    copy(
      propertyIri = propertyIri.toOntologySchema(targetSchema),
      predicates = predicatesInTargetSchema,
      subPropertyOf = subPropertyOfInTargetSchema,
      ontologySchema = targetSchema,
    )
  }

  override def getRdfType: SmartIri = {
    val propertyTypeSet: Seq[SmartIri] = getRdfTypes.filter { classType =>
      OntologyConstants.PropertyTypes.contains(classType.toString)
    }

    if (propertyTypeSet.size == 1) {
      propertyTypeSet.head
    } else {
      throw InconsistentRepositoryDataException(s"The rdf:type of $propertyIri is invalid")
    }
  }

  override def getRdfTypes: Seq[SmartIri] =
    requireIriObjects(
      OntologyConstants.Rdf.Type.toSmartIri,
      throw InconsistentRepositoryDataException(s"The rdf:type of $propertyIri is missing or invalid"),
    ).toVector.sorted

  /**
   * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used after an update, when the
   * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
   * read back from the triplestore (in which predicate objects are not escaped).
   *
   * @return a copy of this object with its predicate objects unescaped.
   */
  def unescape: PropertyInfoContentV2 =
    copy(predicates = unescapePredicateObjects)
}

/**
 * Represents assertions about an OWL named individual.
 *
 * @param individualIri  the IRI of the named individual.
 * @param predicates     the predicates of the named individual.
 * @param ontologySchema indicates whether this named individual belongs to an internal ontology (for use in the
 *                       triplestore) or an external one (for use in the Knora API).
 */
case class IndividualInfoContentV2(
  individualIri: SmartIri,
  predicates: Map[SmartIri, PredicateInfoV2],
  ontologySchema: OntologySchema,
) extends EntityInfoContentV2
    with KnoraContentV2[IndividualInfoContentV2] {
  override def getRdfType: SmartIri = {
    val rdfTypePred = predicates.getOrElse(
      OntologyConstants.Rdf.Type.toSmartIri,
      throw InconsistentRepositoryDataException(s"OWL named individual $individualIri has no rdf:type"),
    )

    val nonIndividualTypes: Seq[SmartIri] =
      getRdfTypes.filter(iri => iri.toString != OntologyConstants.Owl.NamedIndividual)

    if (nonIndividualTypes.size != 1) {
      throw InconsistentRepositoryDataException(
        s"OWL named individual $individualIri has too many objects for rdf:type: ${rdfTypePred.objects.mkString(", ")}",
      )
    }

    nonIndividualTypes.head
  }

  override def getRdfTypes: Seq[SmartIri] =
    requireIriObjects(
      OntologyConstants.Rdf.Type.toSmartIri,
      throw InconsistentRepositoryDataException(s"The rdf:type of $individualIri is missing or invalid"),
    ).toVector.sorted

  override def toOntologySchema(targetSchema: OntologySchema): IndividualInfoContentV2 =
    copy(
      individualIri = individualIri.toOntologySchema(targetSchema),
      predicates = predicates.map { case (predicateIri, predicate) =>
        predicateIri.toOntologySchema(targetSchema) -> predicate.toOntologySchema(targetSchema)
      },
      ontologySchema = targetSchema,
    )

  def unescape: IndividualInfoContentV2 =
    copy(predicates = unescapePredicateObjects)
}

/**
 * Can read an [[IndividualInfoContentV2]] from JSON-LD.
 */
object IndividualInfoContentV2 {
  def fromJsonLDObject(jsonLDIndividualDef: JsonLDObject): IndividualInfoContentV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val individualIri: SmartIri =
      jsonLDIndividualDef.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.toSmartIriWithErr)
    val ontologySchema: OntologySchema = individualIri.getOntologySchema.getOrElse(
      throw BadRequestException(s"Invalid named individual IRI: $individualIri"),
    )

    IndividualInfoContentV2(
      individualIri = individualIri,
      predicates = EntityInfoContentV2.predicatesFromJsonLDObject(jsonLDIndividualDef),
      ontologySchema = ontologySchema,
    )

  }
}

/**
 * Represents information about a subclass of a resource class.
 *
 * @param id    the IRI of the subclass.
 * @param label the `rdfs:label` of the subclass.
 */
case class SubClassInfoV2(id: SmartIri, label: String)

/**
 * Returns metadata about an ontology.
 *
 * @param ontologyIri          the IRI of the ontology.
 * @param projectIri           the IRI of the project that the ontology belongs to.
 * @param label                the label of the ontology, if any.
 * @param comment              the comment of the ontology, if any.
 * @param lastModificationDate the ontology's last modification date, if any.
 * @param ontologyVersion      the version string attached to the ontology, if any.
 */
case class OntologyMetadataV2(
  ontologyIri: SmartIri,
  projectIri: Option[ProjectIri] = None,
  label: Option[String] = None,
  comment: Option[String] = None,
  lastModificationDate: Option[Instant] = None,
  ontologyVersion: Option[String] = None,
) extends KnoraContentV2[OntologyMetadataV2] {
  implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  override def toOntologySchema(targetSchema: OntologySchema): OntologyMetadataV2 =
    if (ontologyIri == OntologyConstants.KnoraBase.KnoraBaseOntologyIri.toSmartIri) {
      targetSchema match {
        case InternalSchema => this
        case apiV2Schema: ApiV2Schema =>
          OntologyTransformationRules.getTransformationRules(apiV2Schema).ontologyMetadata
      }
    } else {
      copy(
        ontologyIri = ontologyIri.toOntologySchema(targetSchema),
      )

    }

  /**
   * Undoes the SPARQL-escaping of the `rdfs:label` and `rdfs:comment` of this ontology. This method is meant to be used in tests after an update, when the
   * input (which has been escaped for use in SPARQL) needs to be compared with the updated data
   * read back from the triplestore (which is not escaped).
   *
   * @return a copy of this [[OntologyMetadataV2]] with the `rdfs:label` and `rdfs:comment` unescaped.
   */
  def unescape: OntologyMetadataV2 =
    copy(label = label.map(Iri.fromSparqlEncodedString), comment = comment.map(Iri.fromSparqlEncodedString))

  def toJsonLD(targetSchema: ApiV2Schema): Map[String, JsonLDValue] = {

    val projectIriStatement: Option[(IRI, JsonLDObject)] = if (targetSchema == ApiV2Complex) {
      projectIri.map { definedProjectIri =>
        KnoraApiV2Complex.AttachedToProject -> JsonLDUtil.iriToJsonLDObject(
          definedProjectIri.toString,
        )
      }
    } else {
      None
    }

    val isSharedStatement: Option[(IRI, JsonLDBoolean)] =
      if (ontologyIri.isKnoraSharedDefinitionIri && targetSchema == ApiV2Complex) {
        Some(KnoraApiV2Complex.IsShared -> JsonLDBoolean(true))
      } else {
        None
      }

    val isBuiltInStatement: Option[(IRI, JsonLDBoolean)] =
      if (ontologyIri.isKnoraBuiltInDefinitionIri && targetSchema == ApiV2Complex) {
        Some(KnoraApiV2Complex.IsBuiltIn -> JsonLDBoolean(true))
      } else {
        None
      }

    val labelStatement: Option[(IRI, JsonLDString)] = label.map { labelStr =>
      Rdfs.Label -> JsonLDString(labelStr)
    }

    val commentStatement: Option[(IRI, JsonLDString)] = comment.map { commentStr =>
      Rdfs.Comment -> JsonLDString(commentStr)
    }

    val lastModDateStatement: Option[(IRI, JsonLDObject)] = if (targetSchema == ApiV2Complex) {
      lastModificationDate.map { lastModDate =>
        KnoraApiV2Complex.LastModificationDate -> JsonLDUtil.datatypeValueToJsonLDObject(
          value = lastModDate.toString,
          datatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        )
      }
    } else {
      None
    }

    Map(
      JsonLDKeywords.ID   -> JsonLDString(ontologyIri.toString),
      JsonLDKeywords.TYPE -> JsonLDString(OntologyConstants.Owl.Ontology),
    ) ++ projectIriStatement ++ labelStatement ++ commentStatement ++ lastModDateStatement ++ isSharedStatement ++ isBuiltInStatement
  }
}
