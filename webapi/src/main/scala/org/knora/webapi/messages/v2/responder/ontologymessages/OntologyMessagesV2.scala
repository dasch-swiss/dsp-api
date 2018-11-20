/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v2.responder.ontologymessages


import java.time.Instant
import java.util.UUID

import akka.actor.ActorSelection
import akka.event.LoggingAdapter
import akka.util.Timeout
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.{KnoraCardinalityInfo, OwlCardinalityInfo}
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.{SmartIri, StringFormatter}

import scala.concurrent.{ExecutionContext, Future}

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait OntologiesResponderRequestV2 extends KnoraRequestV2 {

    def requestingUser: UserADM
}

/**
  * Requests that all ontologies in the repository are loaded. This message must be sent only once, when the application
  * starts, before it accepts any API requests. A successful response will be a [[SuccessResponseV2]].
  *
  * @param requestingUser the user making the request.
  */
case class LoadOntologiesRequestV2(requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Requests the creation of an empty ontology. A successful response will be a [[ReadOntologyV2]].
  *
  * @param ontologyName   the name of the ontology to be created.
  * @param projectIri     the IRI of the project that the ontology will belong to.
  * @param apiRequestID   the ID of the API request.
  * @param requestingUser the user making the request.
  */
case class CreateOntologyRequestV2(ontologyName: String,
                                   projectIri: SmartIri,
                                   isShared: Boolean = false,
                                   label: String,
                                   apiRequestID: UUID,
                                   requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[CreateOntologyRequestV2]] based on JSON-LD requests.
  */
object CreateOntologyRequestV2 extends KnoraJsonLDRequestReaderV2[CreateOntologyRequestV2] {
    /**
      * Converts JSON-LD input into a [[CreateOntologyRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[CreateOntologyRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            storeManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[CreateOntologyRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument,
                               apiRequestID: UUID,
                               requestingUser: UserADM): CreateOntologyRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val ontologyName: String = jsonLDDocument.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.OntologyName, stringFormatter.validateProjectSpecificOntologyName)
        val label: String = jsonLDDocument.requireStringWithValidation(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)
        val projectIri: SmartIri = jsonLDDocument.requireIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject, stringFormatter.toSmartIriWithErr)
        val isShared: Boolean = jsonLDDocument.maybeBoolean(OntologyConstants.KnoraApiV2WithValueObjects.IsShared).exists(identity)

        CreateOntologyRequestV2(
            ontologyName = ontologyName,
            projectIri = projectIri,
            isShared = isShared,
            label = label,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Requests that an ontology is deleted. All the entities in the ontology must be unused in data.
  *
  * @param ontologyIri          the IRI of the ontology to delete.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class DeleteOntologyRequestV2(ontologyIri: SmartIri,
                                   lastModificationDate: Instant,
                                   apiRequestID: UUID,
                                   requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Represents information taken from an [[InputOntologyV2]], representing a request to update a property
  * definition.
  *
  * @param propertyInfoContent  information to be updated in the property definition.
  * @param lastModificationDate the ontology's last modification date.
  */
case class PropertyUpdateInfo(propertyInfoContent: PropertyInfoContentV2,
                              lastModificationDate: Instant)

/**
  * Represents information taken from an [[InputOntologyV2]], representing a request to update a class
  * definition.
  *
  * @param classInfoContent     information to be updated in the class definition.
  * @param lastModificationDate the ontology's last modification date.
  */
case class ClassUpdateInfo(classInfoContent: ClassInfoContentV2,
                           lastModificationDate: Instant)

/**
  * Assists in the processing of JSON-LD in ontology entity update requests.
  */
object OntologyUpdateHelper {
    /**
      * Gets the ontology's last modification date from the request.
      *
      * @param inputOntologyV2 an [[InputOntologyV2]] representing the ontology to be updated.
      * @return the ontology's last modification date.
      */
    def getOntologyLastModificationDate(inputOntologyV2: InputOntologyV2): Instant = {
        inputOntologyV2.ontologyMetadata.lastModificationDate.getOrElse(throw BadRequestException(s"An ontology update request must include the ontology's knora-api:lastModificationDate"))
    }

    /**
      * Gets a class definition from the request.
      *
      * @param inputOntologyV2 an [[InputOntologyV2]] that must contain a single class definition.
      * @return a [[ClassUpdateInfo]] containing the class definition and the ontology's last modification date.
      */
    def getClassDef(inputOntologyV2: InputOntologyV2): ClassUpdateInfo = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val externalOntologyIri = inputOntologyV2.ontologyMetadata.ontologyIri

        // The ontology's lastModificationDate must be provided.

        val lastModificationDate: Instant = getOntologyLastModificationDate(inputOntologyV2)

        // The request must contain exactly one class definition, and no property definitions.

        if (inputOntologyV2.properties.nonEmpty || inputOntologyV2.individuals.nonEmpty) {
            throw BadRequestException(s"A property or individual definition cannot be submitted when creating or modifying a class")
        }

        if (inputOntologyV2.classes.size != 1) {
            throw BadRequestException(s"Only one class can be created or modified per request")
        }

        val classInfoContent = inputOntologyV2.classes.values.head

        // Check that the class's IRI is valid.

        val classIri = classInfoContent.classIri

        if (!(classIri.isKnoraApiV2EntityIri &&
            classIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
            classIri.getOntologyFromEntity == externalOntologyIri)) {
            throw BadRequestException(s"Invalid class IRI: $classIri")
        }

        // Check that the class's rdf:type is valid.

        val classType: SmartIri = classInfoContent.getRdfType

        if (classType != OntologyConstants.Owl.Class.toSmartIri) {
            throw BadRequestException(s"Property $classIri must be an owl:Class")
        }

        // Check that the IRIs of the class's predicates are valid.

        classInfoContent.predicates.keySet.foreach {
            predIri =>
                if (predIri.isKnoraIri && !predIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
                    throw BadRequestException(s"Invalid predicate for request: $predIri")
                }
        }

        ClassUpdateInfo(
            classInfoContent = classInfoContent,
            lastModificationDate = lastModificationDate
        )
    }

    /**
      * Gets a property definition from the request.
      *
      * @param inputOntologyV2 an [[InputOntologyV2]] that must contain a single property definition.
      * @return a [[PropertyUpdateInfo]] containing the property definition and the ontology's last modification date.
      */
    def getPropertyDef(inputOntologyV2: InputOntologyV2): PropertyUpdateInfo = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val externalOntologyIri = inputOntologyV2.ontologyMetadata.ontologyIri

        // The ontology's lastModificationDate must be provided.

        val lastModificationDate: Instant = getOntologyLastModificationDate(inputOntologyV2)

        // The request must contain exactly one property definition, and no class definitions.

        if (inputOntologyV2.classes.nonEmpty || inputOntologyV2.individuals.nonEmpty) {
            throw BadRequestException(s"A class or individual definition cannot be submitted when creating or modifying a property")
        }

        if (inputOntologyV2.properties.size != 1) {
            throw BadRequestException(s"Only one property can be created or modified per request")
        }

        val propertyInfoContent = inputOntologyV2.properties.values.head

        // Check that the property IRI is valid.

        val propertyIri = propertyInfoContent.propertyIri

        if (!(propertyIri.isKnoraApiV2EntityIri &&
            propertyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
            propertyIri.getOntologyFromEntity == externalOntologyIri)) {
            throw BadRequestException(s"Invalid property IRI: $propertyIri")
        }

        // Check that the property type is valid.

        val propertyType: SmartIri = propertyInfoContent.getRdfType

        if (propertyType != OntologyConstants.Owl.ObjectProperty.toSmartIri) {
            throw BadRequestException(s"Property $propertyIri must be an owl:ObjectProperty")
        }

        // Check that the IRIs of the property's predicates are valid.

        propertyInfoContent.predicates.keySet.foreach {
            predIri =>
                if (predIri.isKnoraIri && !predIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
                    throw BadRequestException(s"Invalid predicate for request: $predIri")
                }
        }

        PropertyUpdateInfo(
            propertyInfoContent = propertyInfoContent,
            lastModificationDate = lastModificationDate
        )
    }

    private val LabelAndCommentPredicates = Set(
        OntologyConstants.Rdfs.Label,
        OntologyConstants.Rdfs.Comment
    )

    /**
      * Gets the values of `rdfs:label` or `rdfs:comment` from a request to update them.
      *
      * @param entityInfoContent the data submitted about the entity to be updated.
      * @return the values of that predicate.
      */
    def getLabelsOrComments(entityInfoContent: EntityInfoContentV2): PredicateInfoV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val predicatesWithNewData = entityInfoContent.predicates - OntologyConstants.Rdf.Type.toSmartIri

        if (predicatesWithNewData.size != 1) {
            throw BadRequestException(s"Either rdfs:label or rdfs:comment must be provided")
        }

        val predicateInfoToUpdate = predicatesWithNewData.values.head
        val predicateToUpdate = predicateInfoToUpdate.predicateIri

        if (!LabelAndCommentPredicates.contains(predicateToUpdate.toString)) {
            throw BadRequestException(s"Invalid predicate: $predicateToUpdate")
        }

        if (!predicateInfoToUpdate.objects.forall {
            case StringLiteralV2(_, Some(_)) => true
            case _ => false
        }) {
            throw BadRequestException(s"Missing language code in rdfs:label or rdfs:comment")
        }

        predicateInfoToUpdate
    }
}

/**
  * Requests the addition of a property to an ontology. A successful response will be a [[ReadOntologyV2]].
  *
  * @param propertyInfoContent  an [[PropertyInfoContentV2]] containing the property definition.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class CreatePropertyRequestV2(propertyInfoContent: PropertyInfoContentV2,
                                   lastModificationDate: Instant,
                                   apiRequestID: UUID,
                                   requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[CreatePropertyRequestV2]] based on JSON-LD requests.
  */
object CreatePropertyRequestV2 extends KnoraJsonLDRequestReaderV2[CreatePropertyRequestV2] {
    /**
      * Converts a JSON-LD request to a [[CreatePropertyRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[CreatePropertyRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            storeManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[CreatePropertyRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument,
                               apiRequestID: UUID,
                               requestingUser: UserADM): CreatePropertyRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Get the property definition and the ontology's last modification date from the JSON-LD.

        val inputOntologiesV2 = InputOntologyV2.fromJsonLD(jsonLDDocument)
        val propertyUpdateInfo = OntologyUpdateHelper.getPropertyDef(inputOntologiesV2)
        val propertyInfoContent = propertyUpdateInfo.propertyInfoContent
        val lastModificationDate = propertyUpdateInfo.lastModificationDate

        // Check that the knora-api:subjectType (if provided) and the knora-api:objectType point to valid entity IRIs.

        propertyInfoContent.predicates.get(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri).foreach {
            subjectTypePred =>
                val subjectType = subjectTypePred.requireIriObject(throw BadRequestException(s"Missing or invalid object for predicate knora-api:subjectType"))

                if (!(subjectType.isKnoraApiV2EntityIri && subjectType.getOntologySchema.contains(ApiV2WithValueObjects))) {
                    throw BadRequestException(s"Invalid knora-api:subjectType: $subjectType")
                }
        }

        val objectType = propertyInfoContent.requireIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri, throw BadRequestException(s"Missing knora-api:objectType"))

        if (!(objectType.isKnoraApiV2EntityIri && objectType.getOntologySchema.contains(ApiV2WithValueObjects))) {
            throw BadRequestException(s"Invalid knora-api:objectType: $objectType")
        }

        // The request must provide an rdfs:label and an rdfs:comment.

        if (!propertyInfoContent.predicates.contains(OntologyConstants.Rdfs.Label.toSmartIri)) {
            throw BadRequestException("Missing rdfs:label")
        }

        if (!propertyInfoContent.predicates.contains(OntologyConstants.Rdfs.Comment.toSmartIri)) {
            throw BadRequestException("Missing rdfs:comment")
        }

        CreatePropertyRequestV2(
            propertyInfoContent = propertyInfoContent,
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Requests the addition of a class to an ontology. A successful response will be a [[ReadOntologyV2]].
  *
  * @param classInfoContent     a [[ClassInfoContentV2]] containing the class definition.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class CreateClassRequestV2(classInfoContent: ClassInfoContentV2,
                                lastModificationDate: Instant,
                                apiRequestID: UUID,
                                requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[CreateClassRequestV2]] based on JSON-LD requests.
  */
object CreateClassRequestV2 extends KnoraJsonLDRequestReaderV2[CreateClassRequestV2] {
    /**
      * Converts a JSON-LD request to a [[CreateClassRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[CreateClassRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            storeManager: ActorSelection,
                            responderManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[CreateClassRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument, apiRequestID: UUID, requestingUser: UserADM): CreateClassRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Get the class definition and the ontology's last modification date from the JSON-LD.

        val inputOntologiesV2 = InputOntologyV2.fromJsonLD(jsonLDDocument)
        val classUpdateInfo = OntologyUpdateHelper.getClassDef(inputOntologiesV2)
        val classInfoContent = classUpdateInfo.classInfoContent
        val lastModificationDate = classUpdateInfo.lastModificationDate

        // The request must provide an rdfs:label and an rdfs:comment.

        if (!classInfoContent.predicates.contains(OntologyConstants.Rdfs.Label.toSmartIri)) {
            throw BadRequestException("Missing rdfs:label")
        }

        if (!classInfoContent.predicates.contains(OntologyConstants.Rdfs.Comment.toSmartIri)) {
            throw BadRequestException("Missing rdfs:comment")
        }

        CreateClassRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Requests the addition of cardinalities to a class. A successful response will be a [[ReadOntologyV2]].
  *
  * @param classInfoContent     a [[ClassInfoContentV2]] containing the class definition.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class AddCardinalitiesToClassRequestV2(classInfoContent: ClassInfoContentV2,
                                            lastModificationDate: Instant,
                                            apiRequestID: UUID,
                                            requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[AddCardinalitiesToClassRequestV2]] based on JSON-LD input.
  */
object AddCardinalitiesToClassRequestV2 extends KnoraJsonLDRequestReaderV2[AddCardinalitiesToClassRequestV2] {
    /**
      * Converts JSON-LD input into an [[AddCardinalitiesToClassRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return an [[AddCardinalitiesToClassRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            storeManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[AddCardinalitiesToClassRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument, apiRequestID: UUID, requestingUser: UserADM): AddCardinalitiesToClassRequestV2 = {
        // Get the class definition and the ontology's last modification date from the JSON-LD.

        val inputOntologiesV2 = InputOntologyV2.fromJsonLD(jsonLDDocument)
        val classUpdateInfo = OntologyUpdateHelper.getClassDef(inputOntologiesV2)
        val classInfoContent = classUpdateInfo.classInfoContent
        val lastModificationDate = classUpdateInfo.lastModificationDate

        // The request must provide cardinalities.

        if (classInfoContent.directCardinalities.isEmpty) {
            throw BadRequestException("No cardinalities specified")
        }

        AddCardinalitiesToClassRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Requests the replacement of a class's cardinalities with new ones. A successful response will be a [[ReadOntologyV2]].
  *
  * @param classInfoContent     a [[ClassInfoContentV2]] containing the new cardinalities.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class ChangeCardinalitiesRequestV2(classInfoContent: ClassInfoContentV2,
                                        lastModificationDate: Instant,
                                        apiRequestID: UUID,
                                        requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[ChangeCardinalitiesRequestV2]] based on JSON-LD input.
  */
object ChangeCardinalitiesRequestV2 extends KnoraJsonLDRequestReaderV2[ChangeCardinalitiesRequestV2] {
    /**
      * Converts JSON-LD input into a [[ChangeCardinalitiesRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[ChangeCardinalitiesRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            storeManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ChangeCardinalitiesRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument, apiRequestID: UUID, requestingUser: UserADM): ChangeCardinalitiesRequestV2 = {
        val inputOntologiesV2 = InputOntologyV2.fromJsonLD(jsonLDDocument)
        val classUpdateInfo = OntologyUpdateHelper.getClassDef(inputOntologiesV2)
        val classInfoContent = classUpdateInfo.classInfoContent
        val lastModificationDate = classUpdateInfo.lastModificationDate

        ChangeCardinalitiesRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Requests the deletion of a class. A successful response will be a [[ReadOntologyMetadataV2]].
  *
  * @param classIri             the IRI of the class to be deleted.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class DeleteClassRequestV2(classIri: SmartIri,
                                lastModificationDate: Instant,
                                apiRequestID: UUID,
                                requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Requests the deletion of a property. A successful response will be a [[ReadOntologyMetadataV2]].
  *
  * @param propertyIri          the IRI of the property to be deleted.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class DeletePropertyRequestV2(propertyIri: SmartIri,
                                   lastModificationDate: Instant,
                                   apiRequestID: UUID,
                                   requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * A trait for requests to change entity labels or comments.
  */
sealed trait ChangeLabelsOrCommentsRequest {
    /**
      * The predicate to update: `rdfs:label` or `rdfs:comment`.
      */
    val predicateToUpdate: SmartIri

    /**
      * The new objects of the predicate.
      */
    val newObjects: Seq[StringLiteralV2]
}

/**
  * Requests that a property's labels or comments are changed. A successful response will be a [[ReadOntologyV2]].
  *
  * @param propertyIri          the IRI of the property.
  * @param predicateToUpdate    `rdfs:label` or `rdfs:comment`.
  * @param newObjects           the property's new labels or comments.
  * @param lastModificationDate the ontology's last modification date.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class ChangePropertyLabelsOrCommentsRequestV2(propertyIri: SmartIri,
                                                   predicateToUpdate: SmartIri,
                                                   newObjects: Seq[StringLiteralV2],
                                                   lastModificationDate: Instant,
                                                   apiRequestID: UUID,
                                                   requestingUser: UserADM) extends OntologiesResponderRequestV2 with ChangeLabelsOrCommentsRequest

/**
  * Constructs instances of [[ChangePropertyLabelsOrCommentsRequestV2]] based on JSON-LD input.
  */
object ChangePropertyLabelsOrCommentsRequestV2 extends KnoraJsonLDRequestReaderV2[ChangePropertyLabelsOrCommentsRequestV2] {


    /**
      * Converts a JSON-LD request to a [[ChangePropertyLabelsOrCommentsRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[ChangePropertyLabelsOrCommentsRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            storeManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ChangePropertyLabelsOrCommentsRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument,
                               apiRequestID: UUID,
                               requestingUser: UserADM): ChangePropertyLabelsOrCommentsRequestV2 = {
        val inputOntologiesV2 = InputOntologyV2.fromJsonLD(jsonLDDocument)
        val propertyUpdateInfo = OntologyUpdateHelper.getPropertyDef(inputOntologiesV2)
        val propertyInfoContent = propertyUpdateInfo.propertyInfoContent
        val lastModificationDate = propertyUpdateInfo.lastModificationDate
        val predicateInfoToUpdate = OntologyUpdateHelper.getLabelsOrComments(propertyInfoContent)

        ChangePropertyLabelsOrCommentsRequestV2(
            propertyIri = propertyInfoContent.propertyIri,
            predicateToUpdate = predicateInfoToUpdate.predicateIri,
            newObjects = predicateInfoToUpdate.objects.collect {
                case strLiteral: StringLiteralV2 => strLiteral
            },
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

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
case class ChangeClassLabelsOrCommentsRequestV2(classIri: SmartIri,
                                                predicateToUpdate: SmartIri,
                                                newObjects: Seq[StringLiteralV2],
                                                lastModificationDate: Instant,
                                                apiRequestID: UUID,
                                                requestingUser: UserADM) extends OntologiesResponderRequestV2 with ChangeLabelsOrCommentsRequest

/**
  * Constructs instances of [[ChangeClassLabelsOrCommentsRequestV2]] based on JSON-LD input.
  */
object ChangeClassLabelsOrCommentsRequestV2 extends KnoraJsonLDRequestReaderV2[ChangeClassLabelsOrCommentsRequestV2] {
    /**
      * Converts a JSON-LD request to a [[ChangeClassLabelsOrCommentsRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[ChangeClassLabelsOrCommentsRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            storeManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ChangeClassLabelsOrCommentsRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument,
                               apiRequestID: UUID,
                               requestingUser: UserADM): ChangeClassLabelsOrCommentsRequestV2 = {
        val inputOntologiesV2 = InputOntologyV2.fromJsonLD(jsonLDDocument)
        val classUpdateInfo = OntologyUpdateHelper.getClassDef(inputOntologiesV2)
        val classInfoContent = classUpdateInfo.classInfoContent
        val lastModificationDate = classUpdateInfo.lastModificationDate
        val predicateInfoToUpdate = OntologyUpdateHelper.getLabelsOrComments(classInfoContent)

        ChangeClassLabelsOrCommentsRequestV2(
            classIri = classInfoContent.classIri,
            predicateToUpdate = predicateInfoToUpdate.predicateIri,
            newObjects = predicateInfoToUpdate.objects.collect {
                case strLiteral: StringLiteralV2 => strLiteral
            },
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Requests a change in the metadata of an ontology. A successful response will be a [[ReadOntologyMetadataV2]].
  *
  * @param ontologyIri          the external ontology IRI.
  * @param label                the ontology's new label.
  * @param lastModificationDate the ontology's last modification date, returned in a previous operation.
  * @param apiRequestID         the ID of the API request.
  * @param requestingUser       the user making the request.
  */
case class ChangeOntologyMetadataRequestV2(ontologyIri: SmartIri,
                                           label: String,
                                           lastModificationDate: Instant,
                                           apiRequestID: UUID,
                                           requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[ChangeOntologyMetadataRequestV2]] based on JSON-LD requests.
  */
object ChangeOntologyMetadataRequestV2 extends KnoraJsonLDRequestReaderV2[ChangeOntologyMetadataRequestV2] {
    /**
      * Converts a JSON-LD request to a [[ChangeOntologyMetadataRequestV2]].
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[ChangeClassLabelsOrCommentsRequestV2]] representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            storeManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ChangeOntologyMetadataRequestV2] = {
        Future {
            fromJsonLDSync(
                jsonLDDocument = jsonLDDocument,
                apiRequestID = apiRequestID,
                requestingUser = requestingUser
            )
        }
    }

    private def fromJsonLDSync(jsonLDDocument: JsonLDDocument,
                               apiRequestID: UUID,
                               requestingUser: UserADM): ChangeOntologyMetadataRequestV2 = {
        val inputOntologyV2 = InputOntologyV2.fromJsonLD(jsonLDDocument)
        val inputMetadata = inputOntologyV2.ontologyMetadata
        val ontologyIri = inputMetadata.ontologyIri
        val label = inputMetadata.label.getOrElse(throw BadRequestException(s"No rdfs:label submitted"))
        val lastModificationDate = inputMetadata.lastModificationDate.getOrElse(throw BadRequestException("No knora-api:lastModificationDate submitted"))

        ChangeOntologyMetadataRequestV2(
            ontologyIri = ontologyIri,
            label = label,
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Requests all available information about a list of ontology entities (classes and/or properties). A successful response will be an
  * [[EntityInfoGetResponseV2]].
  *
  * @param classIris      the IRIs of the class entities to be queried.
  * @param propertyIris   the IRIs of the property entities to be queried.
  * @param requestingUser the user making the request.
  */
case class EntityInfoGetRequestV2(classIris: Set[SmartIri] = Set.empty[SmartIri], propertyIris: Set[SmartIri] = Set.empty[SmartIri], requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param classInfoMap    a [[Map]] of class entity IRIs to [[ReadClassInfoV2]] objects.
  * @param propertyInfoMap a [[Map]] of property entity IRIs to [[ReadPropertyInfoV2]] objects.
  */
case class EntityInfoGetResponseV2(classInfoMap: Map[SmartIri, ReadClassInfoV2],
                                   propertyInfoMap: Map[SmartIri, ReadPropertyInfoV2])

/**
  * Requests all available information about a list of ontology entities (standoff classes and/or properties). A successful response will be an
  * [[StandoffEntityInfoGetResponseV2]].
  *
  * @param standoffClassIris    the IRIs of the resource entities to be queried.
  * @param standoffPropertyIris the IRIs of the property entities to be queried.
  * @param requestingUser       the user making the request.
  */
case class StandoffEntityInfoGetRequestV2(standoffClassIris: Set[SmartIri] = Set.empty[SmartIri], standoffPropertyIris: Set[SmartIri] = Set.empty[SmartIri], requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param standoffClassInfoMap    a [[Map]] of standoff class IRIs to [[ReadClassInfoV2]] objects.
  * @param standoffPropertyInfoMap a [[Map]] of standoff property IRIs to [[ReadPropertyInfoV2]] objects.
  */
case class StandoffEntityInfoGetResponseV2(standoffClassInfoMap: Map[SmartIri, ReadClassInfoV2],
                                           standoffPropertyInfoMap: Map[SmartIri, ReadPropertyInfoV2])

/**
  * Requests information about all standoff classes that are a subclass of a data type standoff class. A successful response will be an
  * [[StandoffClassesWithDataTypeGetResponseV2]].
  *
  * @param requestingUser the user making the request.
  */
case class StandoffClassesWithDataTypeGetRequestV2(requestingUser: UserADM) extends OntologiesResponderRequestV2

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
case class StandoffAllPropertyEntitiesGetRequestV2(requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Represents assertions about all standoff all standoff property entities.
  *
  * @param standoffAllPropertiesEntityInfoMap a [[Map]] of standoff property IRIs to [[ReadPropertyInfoV2]] objects.
  */
case class StandoffAllPropertyEntitiesGetResponseV2(standoffAllPropertiesEntityInfoMap: Map[SmartIri, ReadPropertyInfoV2])

/**
  * Checks whether a Knora resource or value class is a subclass of (or identical to) another class.
  * A successful response will be a [[CheckSubClassResponseV2]].
  *
  * @param subClassIri   the IRI of the subclass.
  * @param superClassIri the IRI of the superclass.
  */
case class CheckSubClassRequestV2(subClassIri: SmartIri, superClassIri: SmartIri, requestingUser: UserADM) extends OntologiesResponderRequestV2

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
case class SubClassesGetRequestV2(resourceClassIri: SmartIri, requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Provides information about the subclasses of a Knora resource class.
  *
  * @param subClasses a list of [[SubClassInfoV2]] representing the subclasses of the specified class.
  */
case class SubClassesGetResponseV2(subClasses: Seq[SubClassInfoV2])

/**
  *
  * Request information about the Knora entities (Knora resource classes, standoff class, resource properties, and standoff properties) of a named graph.
  * A successful response will be a [[OntologyKnoraEntitiesIriInfoV2]].
  *
  * @param ontologyIri    the IRI of the named graph.
  * @param requestingUser the user making the request.
  */
case class OntologyKnoraEntityIrisGetRequestV2(ontologyIri: SmartIri, requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Requests metadata about ontologies by project.
  *
  * @param projectIris    the IRIs of the projects for which ontologies should be returned. If this set is empty, information
  *                       about all ontologies is returned.
  * @param requestingUser the user making the request.
  */
case class OntologyMetadataGetByProjectRequestV2(projectIris: Set[SmartIri] = Set.empty[SmartIri], requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Requests metadata about ontologies by ontology IRI.
  *
  * @param ontologyIris   the IRIs of the ontologies to be queried. If this set is empty, information
  *                       about all ontologies is returned.
  * @param requestingUser the user making the request.
  */
case class OntologyMetadataGetByIriRequestV2(ontologyIris: Set[SmartIri] = Set.empty[SmartIri], requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Requests entity definitions for the given ontology.
  *
  * @param ontologyIri    the ontology to query for.
  * @param allLanguages   true if information in all available languages should be returned.
  * @param requestingUser the user making the request.
  */
case class OntologyEntitiesGetRequestV2(ontologyIri: SmartIri, allLanguages: Boolean, requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Requests the entity definitions for the given class IRIs. A successful response will be a [[ReadOntologyV2]].
  *
  * @param classIris      the IRIs of the classes to be queried.
  * @param allLanguages   true if information in all available languages should be returned.
  * @param requestingUser the user making the request.
  */
case class ClassesGetRequestV2(classIris: Set[SmartIri], allLanguages: Boolean, requestingUser: UserADM) extends OntologiesResponderRequestV2

/**
  * Requests the definitions of the specified properties. A successful response will be a [[ReadOntologyV2]].
  *
  * @param propertyIris   the IRIs of the properties to be queried.
  * @param allLanguages   true if information in all available languages should be returned.
  * @param requestingUser the user making the request.
  */
case class PropertiesGetRequestV2(propertyIris: Set[SmartIri], allLanguages: Boolean, requestingUser: UserADM) extends OntologiesResponderRequestV2

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
case class ReadOntologyV2(ontologyMetadata: OntologyMetadataV2,
                          classes: Map[SmartIri, ReadClassInfoV2] = Map.empty[SmartIri, ReadClassInfoV2],
                          properties: Map[SmartIri, ReadPropertyInfoV2] = Map.empty[SmartIri, ReadPropertyInfoV2],
                          individuals: Map[SmartIri, ReadIndividualInfoV2] = Map.empty[SmartIri, ReadIndividualInfoV2],
                          isWholeOntology: Boolean = false,
                          userLang: Option[String] = None) extends KnoraResponseV2 with KnoraReadV2[ReadOntologyV2] {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Converts this [[ReadOntologyV2]] to the specified Knora API v2 schema.
      *
      * @param targetSchema the target schema.
      * @return the converted [[ReadOntologyV2]].
      */
    override def toOntologySchema(targetSchema: ApiV2Schema): ReadOntologyV2 = {
        // Get rules for transforming knora-base entities to knora-api entities in the target schema.
        val transformationRules = KnoraBaseTransformationRules.getTransformationRules(targetSchema)

        // If we're converting to the API v2 simple schema, filter out link value properties.
        val propertiesConsideringLinkValueProps = targetSchema match {
            case ApiV2Simple =>
                properties.filterNot {
                    case (_, propertyInfo) => propertyInfo.isLinkValueProp
                }

            case _ => properties
        }

        // If we're converting knora-base to knora-api, filter classes and properties that don't exist in the target
        // schema.

        val (classesFilteredForTargetSchema, propsFilteredForTargetSchema) = if (ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraBase.KnoraBaseOntologyIri) {
            val filteredClasses = classes.filterNot {
                case (classIri, classDef) => transformationRules.knoraBaseClassesToRemove.contains(classIri) || (targetSchema == ApiV2Simple && classDef.isStandoffClass)
            }

            val filteredProps = propertiesConsideringLinkValueProps.filterNot {
                case (propertyIri, _) => transformationRules.knoraBasePropertiesToRemove.contains(propertyIri)
            }

            (filteredClasses, filteredProps)
        } else {
            (classes, propertiesConsideringLinkValueProps)
        }

        // Convert everything to the target schema.

        val ontologyMetadataInTargetSchema = if (ontologyMetadata.ontologyIri == OntologyConstants.KnoraBase.KnoraBaseOntologyIri.toSmartIri) {
            transformationRules.ontologyMetadata
        } else {
            ontologyMetadata.toOntologySchema(targetSchema)
        }

        val classesInTargetSchema = classesFilteredForTargetSchema.map {
            case (classIri, readClassInfo) => classIri.toOntologySchema(targetSchema) -> readClassInfo.toOntologySchema(targetSchema)
        }

        val propertiesInTargetSchema = propsFilteredForTargetSchema.map {
            case (propertyIri, readPropertyInfo) => propertyIri.toOntologySchema(targetSchema) -> readPropertyInfo.toOntologySchema(targetSchema)
        }

        val individualsInTargetSchema = individuals.map {
            case (individualIri, readIndividualInfo) => individualIri.toOntologySchema(targetSchema) -> readIndividualInfo.toOntologySchema(targetSchema)
        }

        // If we're converting knora-base to knora-api, and this is the whole ontology, add classes and properties that exist in the target schema but
        // not in the source schema.

        val (classesWithExtraOnesForSchema, propertiesWithExtraOnesForSchema) = if (isWholeOntology && ontologyMetadata.ontologyIri.toString == OntologyConstants.KnoraBase.KnoraBaseOntologyIri) {
            (classesInTargetSchema ++ transformationRules.knoraApiClassesToAdd, propertiesInTargetSchema ++ transformationRules.knoraApiPropertiesToAdd)
        } else {
            (classesInTargetSchema, propertiesInTargetSchema)
        }

        copy(
            ontologyMetadata = ontologyMetadataInTargetSchema,
            classes = classesWithExtraOnesForSchema,
            properties = propertiesWithExtraOnesForSchema,
            individuals = individualsInTargetSchema
        )
    }

    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(targetSchema, settings)
    }

    private def generateJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        // Get the ontologies of all Knora entities mentioned in class definitions.

        val knoraOntologiesFromClasses: Set[SmartIri] = classes.values.flatMap {
            classInfo =>
                val entityIris: Set[SmartIri] = classInfo.allCardinalities.keySet ++ classInfo.entityInfoContent.subClassOf

                entityIris.flatMap {
                    entityIri =>
                        if (entityIri.isKnoraEntityIri) {
                            Set(entityIri.getOntologyFromEntity)
                        } else {
                            Set.empty[SmartIri]
                        }
                } + classInfo.entityInfoContent.classIri.getOntologyFromEntity
        }.toSet

        // Get the ontologies of all Knora entities mentioned in property definitions.

        val knoraOntologiesFromProperties: Set[SmartIri] = properties.values.flatMap {
            property =>
                val entityIris = property.entityInfoContent.subPropertyOf ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri) ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri) ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri) ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri)

                entityIris.flatMap {
                    entityIri =>
                        if (entityIri.isKnoraEntityIri) {
                            Set(entityIri.getOntologyFromEntity)
                        } else {
                            Set.empty[SmartIri]
                        }
                } + property.entityInfoContent.propertyIri.getOntologyFromEntity
        }.toSet


        // Determine which ontology to use as the knora-api prefix expansion.
        val knoraApiPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        // Add a salsah-gui prefix only if we're using the complex schema.
        val salsahGuiPrefix: Option[(String, String)] = targetSchema match {
            case ApiV2WithValueObjects =>
                Some(OntologyConstants.SalsahGui.SalsahGuiOntologyLabel -> OntologyConstants.SalsahGuiApiV2WithValueObjects.SalsahGuiPrefixExpansion)

            case _ => None
        }

        // Make a set of all other Knora ontologies used.
        val otherKnoraOntologiesUsed: Set[SmartIri] = (knoraOntologiesFromClasses ++ knoraOntologiesFromProperties).filterNot {
            ontology => ontology.getOntologyName == OntologyConstants.KnoraApi.KnoraApiOntologyLabel || ontology.getOntologyName == OntologyConstants.SalsahGui.SalsahGuiOntologyLabel
        }

        // Make the JSON-LD context.
        val context = JsonLDUtil.makeContext(
            fixedPrefixes = Map(
                OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> knoraApiPrefixExpansion,
                "rdf" -> OntologyConstants.Rdf.RdfPrefixExpansion,
                "rdfs" -> OntologyConstants.Rdfs.RdfsPrefixExpansion,
                "owl" -> OntologyConstants.Owl.OwlPrefixExpansion,
                "xsd" -> OntologyConstants.Xsd.XsdPrefixExpansion
            ) ++ salsahGuiPrefix,
            knoraOntologiesNeedingPrefixes = otherKnoraOntologiesUsed
        )

        // Generate JSON-LD for the classes, properties, and individuals.

        val jsonClasses: Vector[JsonLDObject] = classes.values.map {
            readClassInfo =>
                userLang match {
                    case Some(lang) => readClassInfo.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, settings = settings)
                    case None => readClassInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
                }
        }.toVector

        val jsonProperties: Vector[JsonLDObject] = properties.values.map {
            readPropertyInfo =>
                userLang match {
                    case Some(lang) => readPropertyInfo.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, settings = settings)
                    case None => readPropertyInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
                }
        }.toVector

        val jsonIndividuals: Vector[JsonLDObject] = individuals.values.map {
            readIndividualInfo =>
                userLang match {
                    case Some(lang) => readIndividualInfo.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, settings = settings)
                    case None => readIndividualInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
                }
        }.toVector

        val allEntities = jsonClasses ++ jsonProperties ++ jsonIndividuals
        val allEntitiesSorted = allEntities.sortBy(_.value(JsonLDConstants.ID))

        // Assemble the JSON-LD document.

        val body = JsonLDObject(
            ontologyMetadata.toJsonLD(targetSchema) + (JsonLDConstants.GRAPH -> JsonLDArray(allEntitiesSorted))
        )

        JsonLDDocument(body = body, context = context)
    }
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
case class InputOntologyV2(ontologyMetadata: OntologyMetadataV2,
                           classes: Map[SmartIri, ClassInfoContentV2] = Map.empty[SmartIri, ClassInfoContentV2],
                           properties: Map[SmartIri, PropertyInfoContentV2] = Map.empty[SmartIri, PropertyInfoContentV2],
                           individuals: Map[SmartIri, IndividualInfoContentV2] = Map.empty[SmartIri, IndividualInfoContentV2]) {

    /**
      * Converts this [[InputOntologyV2]] to the specified Knora API v2 schema.
      *
      * @param targetSchema the target schema.
      * @return the converted [[InputOntologyV2]].
      */
    def toOntologySchema(targetSchema: ApiV2Schema): InputOntologyV2 = {
        InputOntologyV2(
            ontologyMetadata = ontologyMetadata.toOntologySchema(targetSchema),
            classes = classes.map {
                case (classIri, classInfoContent) => classIri.toOntologySchema(targetSchema) -> classInfoContent.toOntologySchema(targetSchema)
            },
            properties = properties.map {
                case (propertyIri, propertyInfoContent) => propertyIri.toOntologySchema(targetSchema) -> propertyInfoContent.toOntologySchema(targetSchema)
            },
            individuals = individuals.map {
                case (individualIri, individualInfoContent) => individualIri.toOntologySchema(targetSchema) -> individualInfoContent.toOntologySchema(targetSchema)
            }
        )
    }

    /**
      * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used in tests after an update, when the
      * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
      * read back from the triplestore (in which predicate objects are not escaped).
      *
      * @return a copy of this [[InputOntologyV2]] with all predicate objects unescaped.
      */
    def unescape: InputOntologyV2 = {
        InputOntologyV2(
            ontologyMetadata = ontologyMetadata.unescape,
            classes = classes.map {
                case (classIri, classDef) => classIri -> classDef.unescape
            },
            properties = properties.map {
                case (propertyIri, propertyDef) => propertyIri -> propertyDef.unescape
            },
            individuals = individuals.map {
                case (individualIri, individualDef) => individualIri -> individualDef.unescape
            }
        )
    }
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
      * @param jsonLDDocument  a JSON-LD document representing information about the ontology.
      * @param ignoreExtraData if `true`, extra data in the JSON-LD will be ignored. This is used only in testing.
      *                        Otherwise, extra data will cause an exception to be thrown.
      * @return an [[InputOntologyV2]] representing the same information.
      */
    def fromJsonLD(jsonLDDocument: JsonLDDocument, ignoreExtraData: Boolean = false): InputOntologyV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val ontologyObj = jsonLDDocument.body
        val externalOntologyIri: SmartIri = ontologyObj.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr)

        if (!(externalOntologyIri.isKnoraApiV2DefinitionIri && externalOntologyIri.isKnoraOntologyIri)) {
            throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIri")
        }

        val projectIri: Option[SmartIri] = ontologyObj.maybeIriInObject(OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject, stringFormatter.toSmartIriWithErr)

        val ontologyLabel: Option[String] = ontologyObj.maybeStringWithValidation(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)

        val lastModificationDate: Option[Instant] =
            ontologyObj.maybeStringWithValidation(OntologyConstants.KnoraApiV2Simple.LastModificationDate, stringFormatter.toInstant).
                orElse(ontologyObj.maybeStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant))

        val ontologyMetadata = OntologyMetadataV2(
            ontologyIri = externalOntologyIri,
            projectIri = projectIri,
            label = ontologyLabel,
            lastModificationDate = lastModificationDate
        )

        val maybeGraph: Option[JsonLDArray] = ontologyObj.maybeArray(JsonLDConstants.GRAPH)

        maybeGraph match {
            case Some(graph) =>
                // Make a list of (entity definition, entity type IRI)
                val entitiesWithTypes: Seq[(JsonLDObject, SmartIri)] = graph.value.map {
                    case jsonLDObj: JsonLDObject =>
                        val entityType = jsonLDObj.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                        (jsonLDObj, entityType)

                    case _ => throw BadRequestException("@graph must contain only JSON-LD objects")
                }

                val classes: Map[SmartIri, ClassInfoContentV2] = entitiesWithTypes.collect {
                    case (jsonLDObj, entityType) if OntologyConstants.ClassTypes.contains(entityType.toString) =>
                        val classInfoContent = ClassInfoContentV2.fromJsonLDObject(jsonLDObj, ignoreExtraData)
                        classInfoContent.classIri -> classInfoContent
                }.toMap

                val properties: Map[SmartIri, PropertyInfoContentV2] = entitiesWithTypes.collect {
                    case (jsonLDObj, entityType) if OntologyConstants.PropertyTypes.contains(entityType.toString) =>
                        val propertyInfoContent = PropertyInfoContentV2.fromJsonLDObject(jsonLDObj, ignoreExtraData)
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

                val entityIrisInWrongOntology = entityIris.filter(_.getOntologyFromEntity != externalOntologyIri)

                if (entityIrisInWrongOntology.nonEmpty) {
                    throw BadRequestException(s"One or more entities are not in ontology $externalOntologyIri: ${entityIrisInWrongOntology.mkString(", ")}")
                }

                InputOntologyV2(
                    ontologyMetadata = ontologyMetadata,
                    classes = classes,
                    properties = properties,
                    individuals = individuals
                )

            case None =>
                // We could get an ontology with no entities in a test.
                InputOntologyV2(ontologyMetadata = ontologyMetadata)
        }
    }
}

/**
  * Returns metadata about Knora ontologies.
  *
  * @param ontologies the metadata to be returned.
  */
case class ReadOntologyMetadataV2(ontologies: Set[OntologyMetadataV2]) extends KnoraResponseV2 with KnoraReadV2[ReadOntologyMetadataV2] {

    override def toOntologySchema(targetSchema: ApiV2Schema): ReadOntologyMetadataV2 = {
        // We may have metadata for knora-api in more than one schema. Just return the one for the target schema.

        val ontologiesAvailableInTargetSchema = ontologies.filterNot {
            ontology =>
                ontology.ontologyIri.getOntologyName == OntologyConstants.KnoraApi.KnoraApiOntologyLabel &&
                    !ontology.ontologyIri.getOntologySchema.contains(targetSchema)
        }

        copy(
            ontologies = ontologiesAvailableInTargetSchema.map(_.toOntologySchema(targetSchema))
        )
    }

    private def generateJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        val knoraApiOntologyPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        val context = JsonLDObject(Map(
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(knoraApiOntologyPrefixExpansion),
            "rdfs" -> JsonLDString(OntologyConstants.Rdfs.RdfsPrefixExpansion),
            "owl" -> JsonLDString(OntologyConstants.Owl.OwlPrefixExpansion)
        ))

        val ontologiesJson: Vector[JsonLDObject] = ontologies.toVector.sortBy(_.ontologyIri).map(ontology => JsonLDObject(ontology.toJsonLD(targetSchema)))

        val body = JsonLDObject(Map(
            JsonLDConstants.GRAPH -> JsonLDArray(ontologiesJson)
        ))

        JsonLDDocument(body = body, context = context)
    }

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(targetSchema, settings)
    }
}

/**
  * Represents a predicate that is asserted about a given ontology entity, and the objects of that predicate.
  *
  * @param predicateIri the IRI of the predicate.
  * @param objects      the objects of the predicate.
  */
case class PredicateInfoV2(predicateIri: SmartIri,
                           objects: Seq[OntologyLiteralV2] = Seq.empty[OntologyLiteralV2]) {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Converts this [[PredicateInfoV2]] to another ontology schema.
      *
      * @param targetSchema the target schema.
      * @return the converted [[PredicateInfoV2]].
      */
    def toOntologySchema(targetSchema: OntologySchema): PredicateInfoV2 = {
        copy(
            predicateIri = predicateIri.toOntologySchema(targetSchema),
            objects = objects.map {
                case smartIriLiteral: SmartIriLiteralV2 => smartIriLiteral.toOntologySchema(targetSchema)
                case other => other
            }
        )
    }

    /**
      * Requires this predicate to have a single IRI object, and returns that object.
      *
      * @param errorFun a function that throws an error. It will be called if the predicate does not have a single
      *                 IRI object.
      * @return the predicate's IRI object.
      */
    def requireIriObject(errorFun: => Nothing): SmartIri = {
        objects match {
            case Seq(SmartIriLiteralV2(iri)) => iri
            case _ => errorFun
        }
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
            case _ => errorFun
        }.toSet
    }

    /**
      * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used after an update, when the
      * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
      * read back from the triplestore (in which predicate objects are not escaped).
      *
      * @return this predicate with its objects unescaped.
      */
    def unescape: PredicateInfoV2 = {
        copy(
            objects = objects.map {
                case StringLiteralV2(str, lang) => StringLiteralV2(stringFormatter.fromSparqlEncodedString(str), lang)
                case other => other
            }
        )
    }

    override def hashCode(): Int = {
        // Ignore the order of predicate objects when generating hash codes for this class.
        new HashCodeBuilder(17, 37).append(predicateIri).append(objects.toSet).hashCode()
    }

    override def equals(that: scala.Any): Boolean = {
        // Ignore the order of predicate objects when testing equality for this class.
        that match {
            case otherPred: PredicateInfoV2 =>
                predicateIri == otherPred.predicateIri &&
                    objects.toSet == otherPred.objects.toSet

            case _ => false
        }
    }
}

/**
  * Represents the OWL cardinalities that Knora supports.
  */
object Cardinality extends Enumeration {

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
            throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality property: $owlCardinalityIri")
        }

        if (!(owlCardinalityValue == 0 || owlCardinalityValue == 1)) {
            throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality value: $owlCardinalityValue")
        }

        override def toString: String = s"<$owlCardinalityIri> $owlCardinalityValue"

        def equalsWithoutGuiOrder(that: OwlCardinalityInfo): Boolean = {
            owlCardinalityIri == that.owlCardinalityIri && owlCardinalityValue == that.owlCardinalityValue
        }
    }

    /**
      * Represents a Knora cardinality with an optional SALSAH GUI order.
      *
      * @param cardinality the Knora cardinality.
      * @param guiOrder    the SALSAH GUI order.
      */
    case class KnoraCardinalityInfo(cardinality: Value, guiOrder: Option[Int] = None) {
        override def toString: String = cardinality.toString
    }

    type Cardinality = Value

    val MayHaveOne: Value = Value(0, "0-1")
    val MayHaveMany: Value = Value(1, "0-n")
    val MustHaveOne: Value = Value(2, "1")
    val MustHaveSome: Value = Value(3, "1-n")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * The valid mappings between Knora cardinalities and OWL cardinalities.
      */
    private val knoraCardinality2OwlCardinalityMap: Map[Value, OwlCardinalityInfo] = Map(
        MayHaveOne -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.MaxCardinality, owlCardinalityValue = 1),
        MayHaveMany -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.MinCardinality, owlCardinalityValue = 0),
        MustHaveOne -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.Cardinality, owlCardinalityValue = 1),
        MustHaveSome -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.MinCardinality, owlCardinalityValue = 1)
    )

    private val owlCardinality2KnoraCardinalityMap: Map[OwlCardinalityInfo, Value] = knoraCardinality2OwlCardinalityMap.map {
        case (knoraC, owlC) => (owlC, knoraC)
    }

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Cardinality not found: $name")
        }
    }

    /**
      * Converts information about an OWL cardinality restriction to a [[Value]] of this enumeration.
      *
      * @param propertyIri    the IRI of the property that the OWL cardinality applies to.
      * @param owlCardinality information about an OWL cardinality.
      * @return a [[Value]].
      */
    def owlCardinality2KnoraCardinality(propertyIri: IRI, owlCardinality: OwlCardinalityInfo): KnoraCardinalityInfo = {
        val cardinality = owlCardinality2KnoraCardinalityMap.getOrElse(owlCardinality.copy(guiOrder = None), throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality $owlCardinality for $propertyIri"))

        KnoraCardinalityInfo(
            cardinality = cardinality,
            guiOrder = owlCardinality.guiOrder
        )
    }

    /**
      * Converts a [[Value]] of this enumeration to information about an OWL cardinality restriction.
      *
      * @param knoraCardinality a [[Value]].
      * @return an [[OwlCardinalityInfo]].
      */
    def knoraCardinality2OwlCardinality(knoraCardinality: KnoraCardinalityInfo): OwlCardinalityInfo = {
        knoraCardinality2OwlCardinalityMap(knoraCardinality.cardinality).copy(guiOrder = knoraCardinality.guiOrder)
    }

    /**
      * Checks whether a cardinality that is directly defined on a class is compatible with an inherited cardinality on the
      * same property. This will be true only if the directly defined cardinality is at least as restrictive as the
      * inherited one.
      *
      * @param directCardinality      the directly defined cardinality.
      * @param inheritableCardinality the inherited cardinality.
      * @return `true` if the directly defined cardinality is compatible with the inherited one.
      */
    def isCompatible(directCardinality: Value, inheritableCardinality: Value): Boolean = {
        if (directCardinality == inheritableCardinality) {
            true
        } else {
            inheritableCardinality match {
                case MayHaveOne => directCardinality == MustHaveOne
                case MayHaveMany => true
                case MustHaveOne => false
                case MustHaveSome => directCardinality == MustHaveOne
            }
        }
    }
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
    def requireIriObject(predicateIri: SmartIri, errorFun: => Nothing): SmartIri = {
        predicates.getOrElse(predicateIri, errorFun).requireIriObject(errorFun)
    }

    /**
      * Checks that a predicate is present in this [[EntityInfoContentV2]] and that it at least one IRI object, and
      * returns those objects as a set of [[SmartIri]] instances.
      *
      * @param predicateIri the IRI of the predicate.
      * @param errorFun     a function that will be called if the predicate is absent or if its objects are not IRIs.
      * @return a set of [[SmartIri]] instances representing the predicate's objects.
      */
    def requireIriObjects(predicateIri: SmartIri, errorFun: => Nothing): Set[SmartIri] = {
        predicates.getOrElse(predicateIri, errorFun).requireIriObjects(errorFun)
    }

    /**
      * A convenience method that returns the canonical `rdf:type` of this entity. Throws [[InconsistentTriplestoreDataException]]
      * if the entity's predicates do not include `rdf:type`.
      *
      * @return the entity's `rdf:type`.
      */
    def getRdfType: SmartIri

    /**
      * A convenience method that returns all the objects of this entity's `rdf:type` predicate. Throws [[InconsistentTriplestoreDataException]]
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
    protected def unescapePredicateObjects: Map[SmartIri, PredicateInfoV2] = {
        predicates.map {
            case (predicateIri, predicateInfo) => predicateIri -> predicateInfo.unescape
        }
    }

    /**
      * Gets a predicate and its object from an entity in a specific language.
      *
      * @param predicateIri the IRI of the predicate.
      * @param userLang     the language in which the object should to be returned.
      * @return the requested predicate and object.
      */
    def getPredicateAndStringLiteralObjectWithLang(predicateIri: SmartIri, settings: SettingsImpl, userLang: String): Option[(SmartIri, String)] = {
        getPredicateStringLiteralObject(
            predicateIri = predicateIri,
            preferredLangs = Some(userLang, settings.fallbackLanguage)
        ).map(obj => predicateIri -> obj)
    }

    /**
      * Returns an object for a given predicate. If requested, attempts to return the object in the user's preferred
      * language, in the system's default language, or in any language, in that order.
      *
      * @param predicateIri   the IRI of the predicate.
      * @param preferredLangs the user's preferred language and the system's default language.
      * @return an object for the predicate, or [[None]] if this entity doesn't have the specified predicate, or
      *         if the predicate has no objects.
      */
    def getPredicateStringLiteralObject(predicateIri: SmartIri, preferredLangs: Option[(String, String)] = None): Option[String] = {
        // Does the predicate exist?
        predicates.get(predicateIri) match {
            case Some(predicateInfo) =>
                // Yes. Make a sequence of its string values.
                val stringLiterals: Vector[StringLiteralV2] = predicateInfo.objects.collect {
                    case strLit: StringLiteralV2 => strLit
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
                            case _ => None
                        }

                }

            case None => None
        }
    }

    /**
      * Returns all the non-language-specific string objects specified for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's objects, or an empty set if this entity doesn't have the specified predicate.
      */
    def getPredicateStringLiteralObjectsWithoutLang(predicateIri: SmartIri): Seq[String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) => predicateInfo.objects.collect {
                case StringLiteralV2(str, None) => str
            }

            case None => Seq.empty[String]
        }
    }

    /**
      * Returns all the IRI objects specified for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's IRI objects, or an empty set if this entity doesn't have the specified predicate.
      */
    def getPredicateIriObjects(predicateIri: SmartIri): Seq[SmartIri] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) => predicateInfo.objects.collect {
                case SmartIriLiteralV2(iri) => iri
            }

            case None => Seq.empty[SmartIri]
        }
    }

    /**
      * Returns the first object specified as a string without a language tag for the given predicate.
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
    def getPredicateObjectsWithLangs(predicateIri: SmartIri): Map[String, String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) => predicateInfo.objects.collect {
                case StringLiteralV2(str, Some(lang)) => lang -> str
            }.toMap

            case None => Map.empty[String, String]
        }
    }
}

/**
  * Processes predicates from a JSON-LD class or property definition.
  */
object EntityInfoContentV2 {
    private def stringToLiteral(str: String): StringLiteralV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        StringLiteralV2(stringFormatter.toSparqlEncodedString(str, throw BadRequestException(s"Invalid predicate object: $str")))
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

        val entityType: SmartIri = jsonLDObject.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)

        val rdfType: (SmartIri, PredicateInfoV2) = OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(entityType))
        )

        val predicates = jsonLDObject.value - JsonLDConstants.ID - JsonLDConstants.TYPE - OntologyConstants.Rdfs.SubClassOf - OntologyConstants.Rdfs.SubPropertyOf

        predicates.map {
            case (predicateIriStr: IRI, predicateValue: JsonLDValue) =>
                val predicateIri = predicateIriStr.toSmartIri

                val predicateInfo: PredicateInfoV2 = predicateValue match {
                    case JsonLDString(objStr) =>
                        PredicateInfoV2(
                            predicateIri = predicateIri,
                            objects = Seq(stringToLiteral(objStr))
                        )

                    case objObj: JsonLDObject =>
                        if (objObj.isIri) {
                            // This is a JSON-LD IRI value.
                            PredicateInfoV2(
                                predicateIri = predicateIri,
                                objects = Seq(SmartIriLiteralV2(objObj.toIri(stringFormatter.toSmartIriWithErr)))
                            )
                        } else if (objObj.isStringWithLang) {
                            // This is a string with a language tag.
                            PredicateInfoV2(
                                predicateIri = predicateIri,
                                objects = JsonLDArray(Seq(objObj)).toObjsWithLang
                            )
                        } else {
                            throw BadRequestException(s"Unexpected object value: $objObj")
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
                                    case other => throw AssertionException(s"Invalid object for predicate $predicateIriStr: $other")
                                }
                            )
                        } else if (objArray.value.forall {
                            case jsonObjElem: JsonLDObject if jsonObjElem.isIri =>
                                // All the elements of the array are IRI values.
                                true
                            case _ => false
                        }) {
                            PredicateInfoV2(
                                predicateIri = predicateIri,
                                objects = objArray.value.map {
                                    case jsonObjElem: JsonLDObject => SmartIriLiteralV2(jsonObjElem.toIri(stringFormatter.toSmartIriWithErr))
                                    case other => throw AssertionException(s"Invalid object for predicate $predicateIriStr: $other")
                                }
                            )
                        } else if (objArray.value.forall {
                            case jsonObjElem: JsonLDObject if jsonObjElem.isStringWithLang =>
                                // All the elements of the array are strings with language codes.
                                true
                            case _ => false
                        }) {
                            PredicateInfoV2(
                                predicateIri = predicateIri,
                                objects = objArray.toObjsWithLang
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
      * @param settings     the application settings.
      * @return a JSON-LD object representing the entity.
      */
    def toJsonLDWithSingleLanguage(targetSchema: ApiV2Schema, userLang: String, settings: SettingsImpl): JsonLDObject = {
        val label: Option[(IRI, JsonLDString)] = entityInfoContent.getPredicateAndStringLiteralObjectWithLang(OntologyConstants.Rdfs.Label.toSmartIri, settings, userLang).map {
            case (k: SmartIri, v: String) => (k.toString, JsonLDString(v))
        }

        val comment: Option[(IRI, JsonLDString)] = entityInfoContent.getPredicateAndStringLiteralObjectWithLang(OntologyConstants.Rdfs.Comment.toSmartIri, settings, userLang).map {
            case (k: SmartIri, v: String) => (k.toString, JsonLDString(v))
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
        val labelObjs: Map[String, String] = entityInfoContent.getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Label.toSmartIri)

        val labels: Option[(IRI, JsonLDArray)] = if (labelObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Label -> JsonLDUtil.objectsWithLangsToJsonLDArray(labelObjs))
        } else {
            None
        }

        val commentObjs: Map[String, String] = entityInfoContent.getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Comment.toSmartIri)

        val comments: Option[(IRI, JsonLDArray)] = if (commentObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Comment -> JsonLDUtil.objectsWithLangsToJsonLDArray(commentObjs))
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
  * @param allBaseClasses          a set of the IRIs of all the base classes of the class.
  * @param isResourceClass         `true` if this is a subclass of `knora-base:Resource`.
  * @param isStandoffClass         `true` if this is a subclass of `knora-base:StandoffTag`.
  * @param isValueClass            `true` if the class is a Knora value class.
  * @param canBeInstantiated       `true` if the class is a Knora resource class that can be instantiated via the API.
  * @param inheritedCardinalities  a [[Map]] of properties to [[Cardinality.Value]] objects representing the class's
  *                                inherited cardinalities on those properties.
  * @param standoffDataType        if this is a standoff tag class, the standoff datatype tag class (if any) that it
  *                                is a subclass of.
  * @param knoraResourceProperties a [[Set]] of IRIs of properties in `allCardinalities` that are subproperties of `knora-base:resourceProperty`.
  * @param linkProperties          a [[Set]] of IRIs of properties in `allCardinalities` that point to resources.
  * @param linkValueProperties     a [[Set]] of IRIs of properties in `allCardinalities` that point to `LinkValue` objects.
  * @param fileValueProperties     a [[Set]] of IRIs of properties in `allCardinalities` that point to `FileValue` objects.
  */
case class ReadClassInfoV2(entityInfoContent: ClassInfoContentV2,
                           allBaseClasses: Set[SmartIri] = Set.empty[SmartIri],
                           isResourceClass: Boolean = false,
                           isStandoffClass: Boolean = false,
                           isValueClass: Boolean = false,
                           canBeInstantiated: Boolean = false,
                           inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty[SmartIri, KnoraCardinalityInfo],
                           standoffDataType: Option[StandoffDataTypeClasses.Value] = None,
                           knoraResourceProperties: Set[SmartIri] = Set.empty[SmartIri],
                           linkProperties: Set[SmartIri] = Set.empty[SmartIri],
                           linkValueProperties: Set[SmartIri] = Set.empty[SmartIri],
                           fileValueProperties: Set[SmartIri] = Set.empty[SmartIri]) extends ReadEntityInfoV2 with KnoraReadV2[ReadClassInfoV2] {
    /**
      * All the class's cardinalities, both direct and indirect.
      */
    lazy val allCardinalities: Map[SmartIri, KnoraCardinalityInfo] = inheritedCardinalities ++ entityInfoContent.directCardinalities

    /**
      * All the class's cardinalities for subproperties of `knora-base:resourceProperty`.
      */
    lazy val allResourcePropertyCardinalities: Map[SmartIri, KnoraCardinalityInfo] = allCardinalities.filter {
        case (propertyIri, _) => knoraResourceProperties.contains(propertyIri)
    }

    override def toOntologySchema(targetSchema: ApiV2Schema): ReadClassInfoV2 = {
        // Get rules for transforming knora-base entities to knora-api entities in the target schema.
        val transformationRules = KnoraBaseTransformationRules.getTransformationRules(targetSchema)

        // If we're converting to the simplified API v2 schema, remove references to link value properties.

        val linkValuePropsForSchema = if (targetSchema == ApiV2Simple) {
            Set.empty[SmartIri]
        } else {
            linkValueProperties
        }

        val inheritedCardinalitiesConsideringLinkValueProps = if (targetSchema == ApiV2Simple) {
            inheritedCardinalities.filterNot {
                case (propertyIri, _) => linkValueProperties.contains(propertyIri)
            }
        } else {
            inheritedCardinalities
        }

        val directCardinalitiesConsideringLinkValueProps = if (targetSchema == ApiV2Simple) {
            entityInfoContent.directCardinalities.filterNot {
                case (propertyIri, _) => linkValueProperties.contains(propertyIri)
            }
        } else {
            entityInfoContent.directCardinalities
        }

        val knoraResourcePropertiesConsideringLinkValueProps = if (targetSchema == ApiV2Simple) {
            knoraResourceProperties -- linkValueProperties
        } else {
            knoraResourceProperties
        }

        // Remove inherited cardinalities for knora-base properties that don't exist in the target schema.

        val inheritedCardinalitiesFilteredForTargetSchema: Map[SmartIri, KnoraCardinalityInfo] = inheritedCardinalitiesConsideringLinkValueProps.filterNot {
            case (propertyIri, _) => transformationRules.knoraBasePropertiesToRemove.contains(propertyIri)
        }

        // Remove base classes that don't exist in the target schema.

        val allBaseClassesFilteredForTargetSchema = allBaseClasses.diff(transformationRules.knoraBaseClassesToRemove)

        // Convert all IRIs to the target schema.

        val allBaseClassesInTargetSchema = allBaseClassesFilteredForTargetSchema.map(_.toOntologySchema(targetSchema))

        val entityInfoContentInTargetSchema = entityInfoContent.copy(
            directCardinalities = directCardinalitiesConsideringLinkValueProps
        ).toOntologySchema(targetSchema)

        val inheritedCardinalitiesInTargetSchema: Map[SmartIri, KnoraCardinalityInfo] = inheritedCardinalitiesFilteredForTargetSchema.map {
            case (propertyIri, cardinality) => propertyIri.toOntologySchema(targetSchema) -> cardinality
        }

        val knoraResourcePropertiesInTargetSchema = knoraResourcePropertiesConsideringLinkValueProps.map(_.toOntologySchema(targetSchema))
        val linkPropertiesInTargetSchema = linkProperties.map(_.toOntologySchema(targetSchema))
        val linkValuePropertiesInTargetSchema = linkValuePropsForSchema.map(_.toOntologySchema(targetSchema))
        val fileValuePropertiesInTargetSchema = fileValueProperties.map(_.toOntologySchema(targetSchema))

        // Add cardinalities that this class inherits in the target schema but not in the source schema.

        val baseClassesInTargetSchema = allBaseClasses.map(_.toOntologySchema(targetSchema))

        val inheritedCardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] = baseClassesInTargetSchema.flatMap {
            baseClassIri => transformationRules.knoraApiCardinalitiesToAdd.getOrElse(baseClassIri, Map.empty[SmartIri, KnoraCardinalityInfo])
        }.toMap

        val inheritedCardinalitiesWithExtraOnesForSchema: Map[SmartIri, KnoraCardinalityInfo] = inheritedCardinalitiesInTargetSchema ++ inheritedCardinalitiesToAdd

        copy(
            entityInfoContent = entityInfoContentInTargetSchema,
            allBaseClasses = allBaseClassesInTargetSchema,
            inheritedCardinalities = inheritedCardinalitiesWithExtraOnesForSchema,
            knoraResourceProperties = knoraResourcePropertiesInTargetSchema,
            linkProperties = linkPropertiesInTargetSchema,
            linkValueProperties = linkValuePropertiesInTargetSchema,
            fileValueProperties = fileValuePropertiesInTargetSchema
        )
    }

    def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
        if (entityInfoContent.ontologySchema != targetSchema) {
            throw DataConversionException(s"ReadClassInfoV2 for class ${entityInfoContent.classIri} is not in schema $targetSchema")
        }

        // Convert OWL cardinalities to JSON-LD.
        val owlCardinalities: Seq[JsonLDObject] = allCardinalities.toArray.sortBy {
            case (propertyIri, _) => propertyIri
        }.sortBy {
            case (_, cardinalityInfo: KnoraCardinalityInfo) => cardinalityInfo.guiOrder
        }.map {
            case (propertyIri: SmartIri, cardinalityInfo: KnoraCardinalityInfo) =>

                val prop2card: (IRI, JsonLDInt) = cardinalityInfo.cardinality match {
                    case Cardinality.MayHaveMany => OntologyConstants.Owl.MinCardinality -> JsonLDInt(0)
                    case Cardinality.MayHaveOne => OntologyConstants.Owl.MaxCardinality -> JsonLDInt(1)
                    case Cardinality.MustHaveOne => OntologyConstants.Owl.Cardinality -> JsonLDInt(1)
                    case Cardinality.MustHaveSome => OntologyConstants.Owl.MinCardinality -> JsonLDInt(1)
                }

                // If we're using the complex schema and the cardinality is inherited, add an annotation to say so.
                val isInheritedStatement = if (targetSchema == ApiV2WithValueObjects && !entityInfoContent.directCardinalities.contains(propertyIri)) {
                    Some(OntologyConstants.KnoraApiV2WithValueObjects.IsInherited -> JsonLDBoolean(true))
                } else {
                    None
                }

                val guiOrderStatement = targetSchema match {
                    case ApiV2WithValueObjects =>
                        cardinalityInfo.guiOrder.map {
                            guiOrder => OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiOrder -> JsonLDInt(guiOrder)
                        }

                    case _ => None
                }

                JsonLDObject(Map(
                    JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.Owl.Restriction),
                    OntologyConstants.Owl.OnProperty -> JsonLDUtil.iriToJsonLDObject(propertyIri.toString),
                    prop2card
                ) ++ isInheritedStatement ++ guiOrderStatement)
        }

        val resourceIconPred = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.ResourceIcon
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.ResourceIcon
        }

        val resourceIconStatement: Option[(IRI, JsonLDString)] = entityInfoContent.getPredicateStringLiteralObjectsWithoutLang(resourceIconPred.toSmartIri).headOption.map {
            resIcon => resourceIconPred -> JsonLDString(resIcon)
        }

        val jsonRestriction: Option[JsonLDObject] = entityInfoContent.xsdStringRestrictionPattern.map {
            pattern: String =>
                JsonLDObject(Map(
                    JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.Rdfs.Datatype),
                    OntologyConstants.Owl.OnDatatype -> JsonLDUtil.iriToJsonLDObject(OntologyConstants.Xsd.String),
                    OntologyConstants.Owl.WithRestrictions -> JsonLDArray(Seq(
                        JsonLDObject(Map(OntologyConstants.Xsd.Pattern -> JsonLDString(pattern))
                        ))
                    )))
        }

        val jsonSubClassOf = entityInfoContent.subClassOf.toArray.sorted.map {
            superClass => JsonLDUtil.iriToJsonLDObject(superClass.toString)
        } ++ owlCardinalities ++ jsonRestriction

        val jsonSubClassOfStatement: Option[(IRI, JsonLDArray)] = if (jsonSubClassOf.nonEmpty) {
            Some(OntologyConstants.Rdfs.SubClassOf -> JsonLDArray(jsonSubClassOf))
        } else {
            None
        }

        val isKnoraResourceClassStatement: Option[(IRI, JsonLDBoolean)] = if (isResourceClass && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsResourceClass -> JsonLDBoolean(true))
        } else {
            None
        }

        val isStandoffClassStatement: Option[(IRI, JsonLDBoolean)] = if (isStandoffClass && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsStandoffClass -> JsonLDBoolean(true))
        } else {
            None
        }

        val canBeInstantiatedStatement: Option[(IRI, JsonLDBoolean)] = if (canBeInstantiated && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.CanBeInstantiated -> JsonLDBoolean(true))
        } else {
            None
        }

        val isValueClassStatement: Option[(IRI, JsonLDBoolean)] = if (isValueClass && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsValueClass -> JsonLDBoolean(true))
        } else {
            None
        }

        Map(
            JsonLDConstants.ID -> JsonLDString(entityInfoContent.classIri.toString),
            JsonLDConstants.TYPE -> JsonLDArray(entityInfoContent.getRdfTypes.map(typeIri => JsonLDString(typeIri.toString)))
        ) ++ jsonSubClassOfStatement ++ resourceIconStatement ++ isKnoraResourceClassStatement ++
            isStandoffClassStatement ++ canBeInstantiatedStatement ++ isValueClassStatement
    }
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
case class ReadPropertyInfoV2(entityInfoContent: PropertyInfoContentV2,
                              isResourceProp: Boolean = false,
                              isEditable: Boolean = false,
                              isLinkProp: Boolean = false,
                              isLinkValueProp: Boolean = false,
                              isFileValueProp: Boolean = false,
                              isStandoffInternalReferenceProperty: Boolean = false) extends ReadEntityInfoV2 with KnoraReadV2[ReadPropertyInfoV2] {
    override def toOntologySchema(targetSchema: ApiV2Schema): ReadPropertyInfoV2 = copy(
        entityInfoContent = entityInfoContent.toOntologySchema(targetSchema)
    )

    def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
        if (entityInfoContent.ontologySchema != targetSchema) {
            throw DataConversionException(s"ReadPropertyInfoV2 for property ${entityInfoContent.propertyIri} is not in schema $targetSchema")
        }

        // Get the correct knora-api:subjectType and knora-api:objectType predicates for the target API schema.
        val (subjectTypePred: IRI, objectTypePred: IRI) = targetSchema match {
            case ApiV2Simple => (OntologyConstants.KnoraApiV2Simple.SubjectType, OntologyConstants.KnoraApiV2Simple.ObjectType)
            case ApiV2WithValueObjects => (OntologyConstants.KnoraApiV2WithValueObjects.SubjectType, OntologyConstants.KnoraApiV2WithValueObjects.ObjectType)
        }

        // Get the property's knora-api:subjectType and knora-api:objectType, if provided.
        val (maybeSubjectType: Option[SmartIri], maybeObjectType: Option[SmartIri]) = entityInfoContent.ontologySchema match {
            case InternalSchema => throw DataConversionException(s"ReadPropertyInfoV2 for property ${entityInfoContent.propertyIri} is not in schema $targetSchema")

            case ApiV2Simple =>
                (entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri),
                    entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri))

            case ApiV2WithValueObjects =>
                (entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri),
                    entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri))
        }

        // Make the property's knora-api:subjectType and knora-api:objectType statements.
        val subjectTypeStatement: Option[(IRI, JsonLDObject)] = maybeSubjectType.map(subjectTypeObj => (subjectTypePred, JsonLDUtil.iriToJsonLDObject(subjectTypeObj.toString)))
        val objectTypeStatement: Option[(IRI, JsonLDObject)] = maybeObjectType.map(objectTypeObj => (objectTypePred, JsonLDUtil.iriToJsonLDObject(objectTypeObj.toString)))

        val jsonSubPropertyOf: Seq[JsonLDObject] = entityInfoContent.subPropertyOf.toSeq.sorted.map {
            superProperty => JsonLDUtil.iriToJsonLDObject(superProperty.toString)
        }

        val jsonSubPropertyOfStatement: Option[(IRI, JsonLDArray)] = if (jsonSubPropertyOf.nonEmpty) {
            Some(OntologyConstants.Rdfs.SubPropertyOf -> JsonLDArray(jsonSubPropertyOf))
        } else {
            None
        }

        val isResourcePropStatement: Option[(IRI, JsonLDBoolean)] = if (isResourceProp && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsResourceProperty -> JsonLDBoolean(true))
        } else {
            None
        }

        val isEditableStatement: Option[(IRI, JsonLDBoolean)] = if (isEditable && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsEditable -> JsonLDBoolean(true))
        } else {
            None
        }

        val isLinkValuePropertyStatement: Option[(IRI, JsonLDBoolean)] = if (isLinkValueProp && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsLinkValueProperty -> JsonLDBoolean(true))
        } else {
            None
        }

        val isLinkPropertyStatement: Option[(IRI, JsonLDBoolean)] = if (isLinkProp && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsLinkProperty -> JsonLDBoolean(true))
        } else {
            None
        }

        val guiElementStatement: Option[(IRI, JsonLDObject)] = if (targetSchema == ApiV2WithValueObjects) {
            entityInfoContent.getPredicateIriObject(OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri).map {
                obj => OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp -> JsonLDUtil.iriToJsonLDObject(obj.toString)
            }
        } else {
            None
        }

        val guiAttributeStatement = if (targetSchema == ApiV2WithValueObjects) {
            entityInfoContent.getPredicateStringLiteralObjectsWithoutLang(OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri) match {
                case objs if objs.nonEmpty =>
                    Some(OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute -> JsonLDArray(objs.toArray.sorted.map(JsonLDString)))

                case _ => None
            }
        } else {
            None
        }

        Map(
            JsonLDConstants.ID -> JsonLDString(entityInfoContent.propertyIri.toString),
            JsonLDConstants.TYPE -> JsonLDArray(entityInfoContent.getRdfTypes.map(typeIri => JsonLDString(typeIri.toString)))
        ) ++ jsonSubPropertyOfStatement ++ subjectTypeStatement ++ objectTypeStatement ++
            isResourcePropStatement ++ isEditableStatement ++ isLinkValuePropertyStatement ++
            isLinkPropertyStatement ++ guiElementStatement ++ guiAttributeStatement
    }
}

/**
  * Represents an OWL named individual definition as returned in an API response.
  *
  * @param entityInfoContent an [[IndividualInfoContentV2]] representing information about the named individual.
  */
case class ReadIndividualInfoV2(entityInfoContent: IndividualInfoContentV2) extends ReadEntityInfoV2 with KnoraReadV2[ReadIndividualInfoV2] {
    override def toOntologySchema(targetSchema: ApiV2Schema): ReadIndividualInfoV2 = copy(
        entityInfoContent = entityInfoContent.toOntologySchema(targetSchema)
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
                    }.sortBy(_._2).map(_._1) // Sort for determinism in testing.

                    acc + (predicateIri.toString -> JsonLDArray(nonLanguageSpecificObjectsAsJson))
                } else {
                    acc
                }
        }

        Map(
            JsonLDConstants.ID -> JsonLDString(entityInfoContent.individualIri.toString)
        ) ++ jsonLDPredicates
    }
}

/**
  * Represents assertions about an OWL class.
  *
  * @param classIri                    the IRI of the class.
  * @param predicates                  a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param directCardinalities         a [[Map]] of properties to [[Cardinality.Value]] objects representing the cardinalities
  *                                    that are directly defined on the class (as opposed to inherited) on those properties.
  * @param xsdStringRestrictionPattern if the class's rdf:type is rdfs:Datatype, an optional xsd:pattern specifying
  *                                    the regular expression that restricts its values. This has the effect of making the
  *                                    class a subclass of a blank node with owl:onDatatype xsd:string.
  * @param subClassOf                  the classes that this class is a subclass of.
  * @param ontologySchema              indicates whether this ontology entity belongs to an internal ontology (for use in the
  *                                    triplestore) or an external one (for use in the Knora API).
  */
case class ClassInfoContentV2(classIri: SmartIri,
                              predicates: Map[SmartIri, PredicateInfoV2] = Map.empty[SmartIri, PredicateInfoV2],
                              directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty[SmartIri, KnoraCardinalityInfo],
                              xsdStringRestrictionPattern: Option[String] = None,
                              subClassOf: Set[SmartIri] = Set.empty[SmartIri],
                              ontologySchema: OntologySchema) extends EntityInfoContentV2 with KnoraContentV2[ClassInfoContentV2] {
    override def toOntologySchema(targetSchema: OntologySchema): ClassInfoContentV2 = {
        val classIriInTargetSchema = classIri.toOntologySchema(targetSchema)

        // Get rules for transforming knora-base entities to knora-api entities in the target schema, if relevant.
        val maybeTransformationRules: Option[KnoraBaseTransformationRules] = targetSchema match {
            case apiV2Schema: ApiV2Schema => Some(KnoraBaseTransformationRules.getTransformationRules(apiV2Schema))
            case InternalSchema => None
        }

        // Remove cardinalities for knora-base properties that don't exist in the target schema.

        val knoraBasePropertiesToRemove: Set[SmartIri] = maybeTransformationRules match {
            case Some(transformationRules) => transformationRules.knoraBasePropertiesToRemove
            case _ => Set.empty[SmartIri]
        }

        val directCardinalitiesFilteredForTargetSchema: Map[SmartIri, KnoraCardinalityInfo] = directCardinalities.filterNot {
            case (propertyIri, _) => knoraBasePropertiesToRemove.contains(propertyIri)
        }

        val subClassOfFilteredForTargetSchema = subClassOf.filterNot {
            baseClass =>
                maybeTransformationRules match {
                    case Some(transformationRules) => transformationRules.knoraBaseClassesToRemove.contains(baseClass)
                    case None => false
                }
        }

        // Convert the property IRIs of the remaining cardinalities to the target schema.

        val directCardinalitiesInTargetSchema: Map[SmartIri, KnoraCardinalityInfo] = directCardinalitiesFilteredForTargetSchema.map {
            case (propertyIri, cardinality) => propertyIri.toOntologySchema(targetSchema) -> cardinality
        }

        // Add any cardinalities that this class has in knora-api but not in knora-base.

        val cardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] = maybeTransformationRules match {
            case Some(transformationRules) =>
                transformationRules.knoraApiCardinalitiesToAdd.getOrElse(
                    classIriInTargetSchema,
                    Map.empty[SmartIri, KnoraCardinalityInfo]
                )

            case None => Map.empty[SmartIri, KnoraCardinalityInfo]
        }

        val directCardinalitiesWithExtraOnesForSchema: Map[SmartIri, KnoraCardinalityInfo] = directCardinalitiesInTargetSchema ++ cardinalitiesToAdd

        val predicatesInTargetSchema = predicates.map {
            case (predicateIri, predicate) => predicateIri.toOntologySchema(targetSchema) -> predicate.toOntologySchema(targetSchema)
        }

        val subClassOfInTargetSchema = subClassOfFilteredForTargetSchema.map(_.toOntologySchema(targetSchema))

        copy(
            classIri = classIriInTargetSchema,
            predicates = predicatesInTargetSchema,
            directCardinalities = directCardinalitiesWithExtraOnesForSchema,
            subClassOf = subClassOfInTargetSchema,
            ontologySchema = targetSchema
        )
    }

    override def getRdfType: SmartIri = {
        val classTypeSet: Seq[SmartIri] = getRdfTypes.filter {
            classType => OntologyConstants.ClassTypes.contains(classType.toString)
        }

        if (classTypeSet.size == 1) {
            classTypeSet.head
        } else {
            throw InconsistentTriplestoreDataException(s"The rdf:type of $classIri is invalid")
        }
    }

    override def getRdfTypes: Seq[SmartIri] = requireIriObjects(OntologyConstants.Rdf.Type.toSmartIri, throw InconsistentTriplestoreDataException(s"The rdf:type of $classIri is missing or invalid")).toVector.sorted

    /**
      * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used after an update, when the
      * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
      * read back from the triplestore (in which predicate objects are not escaped).
      *
      * @return a copy of this object with its predicate objects unescaped.
      */
    def unescape: ClassInfoContentV2 = {
        copy(predicates = unescapePredicateObjects)
    }
}

/**
  * Can read a [[ClassInfoContentV2]] from JSON-LD.
  */
object ClassInfoContentV2 {

    // The predicates that are allowed in a class definition that is read from JSON-LD.
    private val AllowedJsonLDClassPredicates = Set(
        JsonLDConstants.ID,
        JsonLDConstants.TYPE,
        OntologyConstants.Rdfs.SubClassOf,
        OntologyConstants.Rdfs.Label,
        OntologyConstants.Rdfs.Comment
    )

    // The predicates that are allowed in an owl:Restriction that is read from JSON-LD.
    private val AllowedJsonLDRestrictionPredicates = Set(
        JsonLDConstants.TYPE,
        OntologyConstants.Owl.Cardinality,
        OntologyConstants.Owl.MinCardinality,
        OntologyConstants.Owl.MaxCardinality,
        OntologyConstants.Owl.OnProperty,
        OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiOrder
    )

    /**
      * Converts a JSON-LD class definition into a [[ClassInfoContentV2]].
      *
      * @param jsonLDClassDef  a JSON-LD object representing a class definition.
      * @param ignoreExtraData if `true`, extra data in the class definition will be ignored. This is used only in testing.
      *                        Otherwise, extra data will cause an exception to be thrown.
      * @return a [[ClassInfoContentV2]] representing the class definition.
      */
    def fromJsonLDObject(jsonLDClassDef: JsonLDObject, ignoreExtraData: Boolean): ClassInfoContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val classIri: SmartIri = jsonLDClassDef.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr)
        val ontologySchema: OntologySchema = classIri.getOntologySchema.getOrElse(throw BadRequestException(s"Invalid class IRI: $classIri"))

        // TODO: handle custom datatypes.

        if (!ignoreExtraData) {
            val extraClassPredicates = jsonLDClassDef.value.keySet -- AllowedJsonLDClassPredicates

            if (extraClassPredicates.nonEmpty) {
                throw BadRequestException(s"The definition of $classIri contains one or more invalid predicates: ${extraClassPredicates.mkString(", ")}")
            }
        }

        val filteredClassDef = JsonLDObject(jsonLDClassDef.value.filterKeys(AllowedJsonLDClassPredicates))

        val (subClassOf: Set[SmartIri], directCardinalities: Map[SmartIri, KnoraCardinalityInfo]) = filteredClassDef.maybeArray(OntologyConstants.Rdfs.SubClassOf) match {
            case Some(valueArray: JsonLDArray) =>
                val arrayElemsAsObjs: Seq[JsonLDObject] = valueArray.value.map {
                    case jsonLDObj: JsonLDObject => jsonLDObj
                    case other => throw BadRequestException(s"Unexpected value for rdfs:subClassOf: $other")
                }

                // Get the base classes from the objects of rdfs:subClassOf.
                val baseClasses: Set[SmartIri] = arrayElemsAsObjs.filter {
                    jsonLDObj => jsonLDObj.isIri
                }.map {
                    jsonLDObj => jsonLDObj.toIri(stringFormatter.toSmartIriWithErr)
                }.toSet

                // Any object of rdfs:subClassOf that isn't a base class should be an owl:Restriction.
                val restrictions: Seq[JsonLDObject] = arrayElemsAsObjs.filter {
                    jsonLDObj => !jsonLDObj.isIri
                }

                val directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = restrictions.foldLeft(Map.empty[SmartIri, KnoraCardinalityInfo]) {
                    case (acc, restriction) =>
                        if (restriction.value.get(OntologyConstants.KnoraApiV2WithValueObjects.IsInherited).contains(JsonLDBoolean(true))) {
                            // If ignoreExtraData is true and we encounter knora-api:isInherited in a cardinality, ignore the whole cardinality.
                            if (ignoreExtraData) {
                                acc
                            } else {
                                throw BadRequestException("Inherited cardinalities are not allowed in this request")
                            }
                        } else {
                            val extraRestrictionPredicates = restriction.value.keySet -- AllowedJsonLDRestrictionPredicates

                            if (!ignoreExtraData && extraRestrictionPredicates.nonEmpty) {
                                throw BadRequestException(s"A cardinality in the definition of $classIri contains one or more invalid predicates: ${extraRestrictionPredicates.mkString(", ")}")
                            }

                            val cardinalityType = restriction.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)

                            if (cardinalityType != OntologyConstants.Owl.Restriction.toSmartIri) {
                                throw BadRequestException(s"A cardinality must be expressed as an owl:Restriction, but this type was found: $cardinalityType")
                            }

                            val (owlCardinalityIri: IRI, owlCardinalityValue: Int) = restriction.maybeInt(OntologyConstants.Owl.Cardinality) match {
                                case Some(value) => OntologyConstants.Owl.Cardinality -> value

                                case None =>
                                    restriction.maybeInt(OntologyConstants.Owl.MinCardinality) match {
                                        case Some(value) => OntologyConstants.Owl.MinCardinality -> value

                                        case None =>
                                            restriction.maybeInt(OntologyConstants.Owl.MaxCardinality) match {
                                                case Some(value) => OntologyConstants.Owl.MaxCardinality -> value
                                                case None => throw BadRequestException(s"Missing OWL cardinality predicate in the definition of $classIri")
                                            }
                                    }
                            }

                            val onProperty = restriction.requireIriInObject(OntologyConstants.Owl.OnProperty, stringFormatter.toSmartIriWithErr)
                            val guiOrder = restriction.maybeInt(OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiOrder)

                            val owlCardinalityInfo = OwlCardinalityInfo(
                                owlCardinalityIri = owlCardinalityIri,
                                owlCardinalityValue = owlCardinalityValue,
                                guiOrder = guiOrder
                            )

                            acc + (onProperty -> Cardinality.owlCardinality2KnoraCardinality(
                                propertyIri = onProperty.toString,
                                owlCardinality = owlCardinalityInfo
                            ))
                        }
                }

                (baseClasses, directCardinalities)

            case None => (Set.empty[SmartIri], Map.empty[SmartIri, KnoraCardinalityInfo])
        }

        ClassInfoContentV2(
            classIri = classIri,
            predicates = EntityInfoContentV2.predicatesFromJsonLDObject(filteredClassDef),
            directCardinalities = directCardinalities,
            subClassOf = subClassOf,
            ontologySchema = ontologySchema
        )
    }

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
case class PropertyInfoContentV2(propertyIri: SmartIri,
                                 predicates: Map[SmartIri, PredicateInfoV2] = Map.empty[SmartIri, PredicateInfoV2],
                                 subPropertyOf: Set[SmartIri] = Set.empty[SmartIri],
                                 ontologySchema: OntologySchema) extends EntityInfoContentV2 with KnoraContentV2[PropertyInfoContentV2] {

    override def toOntologySchema(targetSchema: OntologySchema): PropertyInfoContentV2 = {

        // Are we converting from the internal schema to the API v2 simple schema?
        val predicatesWithAdjustedRdfType: Map[SmartIri, PredicateInfoV2] = if (ontologySchema == InternalSchema && targetSchema == ApiV2Simple) {
            // Yes. Is this an object property?
            val rdfTypeIri = OntologyConstants.Rdf.Type.toSmartIri
            val sourcePropertyType: SmartIri = getPredicateIriObject(rdfTypeIri).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdf:type"))

            if (sourcePropertyType.toString == OntologyConstants.Owl.ObjectProperty) {
                // Yes. See if we need to change it to a datatype property. Does it have a knora-base:objectClassConstraint?
                val objectClassConstraintIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri
                val maybeObjectType: Option[SmartIri] = getPredicateIriObject(objectClassConstraintIri)

                maybeObjectType match {
                    case Some(objectTypeObj) =>
                        // Yes. Is there a corresponding type in the API v2 simple ontology?
                        if (OntologyConstants.CorrespondingIris((InternalSchema, ApiV2Simple)).get(objectTypeObj.toString).nonEmpty) {
                            // Yes. The corresponding type must be a datatype, so make this a datatype property.
                            (predicates - rdfTypeIri) +
                                (rdfTypeIri -> PredicateInfoV2(
                                    predicateIri = rdfTypeIri,
                                    objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.DatatypeProperty.toSmartIri))
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

        val maybeTransformationRules: Option[KnoraBaseTransformationRules] = targetSchema match {
            case apiV2Schema: ApiV2Schema => Some(KnoraBaseTransformationRules.getTransformationRules(apiV2Schema))
            case InternalSchema => None
        }

        val subPropertyOfFilteredForTargetSchema = subPropertyOf.filterNot {
            baseProperty =>
                maybeTransformationRules match {
                    case Some(transformationRules) => transformationRules.knoraBasePropertiesToRemove.contains(baseProperty)
                    case None => false
                }
        }

        // Convert all IRIs to the target schema.

        val predicatesInTargetSchema = predicatesWithAdjustedRdfType.map {
            case (predicateIri, predicate) => predicateIri.toOntologySchema(targetSchema) -> predicate.toOntologySchema(targetSchema)
        }

        val subPropertyOfInTargetSchema = subPropertyOfFilteredForTargetSchema.map(_.toOntologySchema(targetSchema))

        copy(
            propertyIri = propertyIri.toOntologySchema(targetSchema),
            predicates = predicatesInTargetSchema,
            subPropertyOf = subPropertyOfInTargetSchema,
            ontologySchema = targetSchema
        )
    }

    override def getRdfType: SmartIri = {
        val propertyTypeSet: Seq[SmartIri] = getRdfTypes.filter {
            classType => OntologyConstants.PropertyTypes.contains(classType.toString)
        }

        if (propertyTypeSet.size == 1) {
            propertyTypeSet.head
        } else {
            throw InconsistentTriplestoreDataException(s"The rdf:type of $propertyIri is invalid")
        }
    }

    override def getRdfTypes: Seq[SmartIri] = requireIriObjects(OntologyConstants.Rdf.Type.toSmartIri, throw InconsistentTriplestoreDataException(s"The rdf:type of $propertyIri is missing or invalid")).toVector.sorted

    /**
      * Undoes the SPARQL-escaping of predicate objects. This method is meant to be used after an update, when the
      * input (whose predicate objects have been escaped for use in SPARQL) needs to be compared with the updated data
      * read back from the triplestore (in which predicate objects are not escaped).
      *
      * @return a copy of this object with its predicate objects unescaped.
      */
    def unescape: PropertyInfoContentV2 = {
        copy(predicates = unescapePredicateObjects)
    }
}

/**
  * Can read a [[PropertyInfoContentV2]] from JSON-LD, and provides constants used by that class.
  */
object PropertyInfoContentV2 {
    // The predicates allowed in a property definition that is read from JSON-LD.
    private val AllowedJsonLDPropertyPredicates = Set(
        JsonLDConstants.ID,
        JsonLDConstants.TYPE,
        OntologyConstants.KnoraApiV2Simple.SubjectType,
        OntologyConstants.KnoraApiV2Simple.ObjectType,
        OntologyConstants.KnoraApiV2WithValueObjects.SubjectType,
        OntologyConstants.KnoraApiV2WithValueObjects.ObjectType,
        OntologyConstants.Rdfs.SubPropertyOf,
        OntologyConstants.Rdfs.Label,
        OntologyConstants.Rdfs.Comment,
        OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp,
        OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute
    )

    /**
      * Reads a [[PropertyInfoContentV2]] from a JSON-LD object.
      *
      * @param jsonLDPropertyDef the JSON-LD object representing a property definition.
      * @param ignoreExtraData   if `true`, extra data in the property definition will be ignored. This is used only in testing.
      *                          Otherwise, extra data will cause an exception to be thrown.
      * @return a [[PropertyInfoContentV2]] representing the property definition.
      */
    def fromJsonLDObject(jsonLDPropertyDef: JsonLDObject, ignoreExtraData: Boolean): PropertyInfoContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val propertyIri: SmartIri = jsonLDPropertyDef.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr)
        val ontologySchema: OntologySchema = propertyIri.getOntologySchema.getOrElse(throw BadRequestException(s"Invalid property IRI: $propertyIri"))

        if (!ignoreExtraData) {
            val extraPropertyPredicates = jsonLDPropertyDef.value.keySet -- AllowedJsonLDPropertyPredicates

            if (extraPropertyPredicates.nonEmpty) {
                throw BadRequestException(s"The definition of $propertyIri contains one or more invalid predicates: ${extraPropertyPredicates.mkString(", ")}")
            }
        }

        val filteredPropertyDef = JsonLDObject(jsonLDPropertyDef.value.filterKeys(AllowedJsonLDPropertyPredicates))

        val subPropertyOf: Set[SmartIri] = filteredPropertyDef.maybeArray(OntologyConstants.Rdfs.SubPropertyOf) match {
            case Some(valueArray: JsonLDArray) =>
                valueArray.value.map {
                    case superPropertyIriObj: JsonLDObject => superPropertyIriObj.toIri(stringFormatter.toSmartIriWithErr)
                    case other => throw BadRequestException(s"Expected a property IRI: $other")
                }.toSet

            case None => Set.empty[SmartIri]
        }

        PropertyInfoContentV2(
            propertyIri = propertyIri,
            predicates = EntityInfoContentV2.predicatesFromJsonLDObject(filteredPropertyDef),
            subPropertyOf = subPropertyOf,
            ontologySchema = ontologySchema
        )
    }
}

/**
  * Represents assertions about an OWL named individual.
  *
  * @param individualIri  the IRI of the named individual.
  * @param predicates     the predicates of the named individual.
  * @param ontologySchema indicates whether this named individual belongs to an internal ontology (for use in the
  *                       triplestore) or an external one (for use in the Knora API).
  */
case class IndividualInfoContentV2(individualIri: SmartIri,
                                   predicates: Map[SmartIri, PredicateInfoV2],
                                   ontologySchema: OntologySchema) extends EntityInfoContentV2 with KnoraContentV2[IndividualInfoContentV2] {
    override def getRdfType: SmartIri = {
        val rdfTypePred = predicates.getOrElse(OntologyConstants.Rdf.Type.toSmartIri, throw InconsistentTriplestoreDataException(s"OWL named individual $individualIri has no rdf:type"))

        val nonIndividualTypes: Seq[SmartIri] = getRdfTypes.filter(iri => iri.toString != OntologyConstants.Owl.NamedIndividual)

        if (nonIndividualTypes.size != 1) {
            throw InconsistentTriplestoreDataException(s"OWL named individual $individualIri has too many objects for rdf:type: ${rdfTypePred.objects.mkString(", ")}")
        }

        nonIndividualTypes.head
    }

    override def getRdfTypes: Seq[SmartIri] = requireIriObjects(OntologyConstants.Rdf.Type.toSmartIri, throw InconsistentTriplestoreDataException(s"The rdf:type of $individualIri is missing or invalid")).toVector.sorted

    override def toOntologySchema(targetSchema: OntologySchema): IndividualInfoContentV2 = {
        copy(
            individualIri = individualIri.toOntologySchema(targetSchema),
            predicates = predicates.map {
                case (predicateIri, predicate) => predicateIri.toOntologySchema(targetSchema) -> predicate.toOntologySchema(targetSchema)
            },
            ontologySchema = targetSchema
        )
    }

    def unescape: IndividualInfoContentV2 = {
        copy(predicates = unescapePredicateObjects)
    }
}

/**
  * Can read an [[IndividualInfoContentV2]] from JSON-LD.
  */
object IndividualInfoContentV2 {
    def fromJsonLDObject(jsonLDIndividualDef: JsonLDObject): IndividualInfoContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val individualIri: SmartIri = jsonLDIndividualDef.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr)
        val ontologySchema: OntologySchema = individualIri.getOntologySchema.getOrElse(throw BadRequestException(s"Invalid named individual IRI: $individualIri"))

        IndividualInfoContentV2(
            individualIri = individualIri,
            predicates = EntityInfoContentV2.predicatesFromJsonLDObject(jsonLDIndividualDef),
            ontologySchema = ontologySchema
        )

    }
}

/**
  * Represents the IRIs of Knora entities (Knora resource classes, standoff class, resource properties, and standoff properties) defined in a particular ontology.
  *
  * @param ontologyIri          the IRI of the ontology.
  * @param classIris            the classes defined in the ontology.
  * @param propertyIris         the properties defined in the ontology.
  * @param standoffClassIris    the standoff classes defined in the ontology.
  * @param standoffPropertyIris the standoff properties defined in the ontology.
  */
case class OntologyKnoraEntitiesIriInfoV2(ontologyIri: SmartIri,
                                          classIris: Set[SmartIri],
                                          propertyIris: Set[SmartIri],
                                          standoffClassIris: Set[SmartIri],
                                          standoffPropertyIris: Set[SmartIri])

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
  * @param lastModificationDate the ontology's last modification date, if any.
  */
case class OntologyMetadataV2(ontologyIri: SmartIri,
                              projectIri: Option[SmartIri] = None,
                              label: Option[String] = None,
                              lastModificationDate: Option[Instant] = None) extends KnoraContentV2[OntologyMetadataV2] {
    override def toOntologySchema(targetSchema: OntologySchema): OntologyMetadataV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        if (ontologyIri == OntologyConstants.KnoraBase.KnoraBaseOntologyIri.toSmartIri) {
            targetSchema match {
                case InternalSchema => this
                case apiV2Schema: ApiV2Schema => KnoraBaseTransformationRules.getTransformationRules(apiV2Schema).ontologyMetadata
            }
        } else {
            copy(
                ontologyIri = ontologyIri.toOntologySchema(targetSchema)
            )

        }
    }

    /**
      * Undoes the SPARQL-escaping of the `rdfs:label` of this ontology. This method is meant to be used in tests after an update, when the
      * input (which has been escaped for use in SPARQL) needs to be compared with the updated data
      * read back from the triplestore (which is not escaped).
      *
      * @return a copy of this [[OntologyMetadataV2]] with the `rdfs:label` unescaped.
      */
    def unescape: OntologyMetadataV2 = {
        val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        copy(label = label.map(stringFormatter.fromSparqlEncodedString))
    }

    def toJsonLD(targetSchema: ApiV2Schema): Map[String, JsonLDValue] = {

        val projectIriStatement: Option[(IRI, JsonLDObject)] = if (targetSchema == ApiV2WithValueObjects) {
            projectIri.map {
                definedProjectIri => OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject -> JsonLDUtil.iriToJsonLDObject(definedProjectIri.toString)
            }
        } else {
            None
        }

        val isSharedStatement: Option[(IRI, JsonLDBoolean)] = if (ontologyIri.isKnoraSharedDefinitionIri && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsShared -> JsonLDBoolean(true))
        } else {
            None
        }

        val isBuiltInStatement: Option[(IRI, JsonLDBoolean)] = if (ontologyIri.isKnoraBuiltInDefinitionIri && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsBuiltIn -> JsonLDBoolean(true))
        } else {
            None
        }

        val labelStatement: Option[(IRI, JsonLDString)] = label.map {
            labelStr => OntologyConstants.Rdfs.Label -> JsonLDString(labelStr)
        }

        val lastModDateStatement: Option[(IRI, JsonLDString)] = lastModificationDate.map {
            lastModDate =>
                val lastModDateProp = targetSchema match {
                    case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.LastModificationDate
                    case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate
                }

                lastModDateProp -> JsonLDString(lastModDate.toString)
        }

        Map(JsonLDConstants.ID -> JsonLDString(ontologyIri.toString),
            JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.Owl.Ontology)
        ) ++ projectIriStatement ++ labelStatement ++ lastModDateStatement ++ isSharedStatement ++ isBuiltInStatement
    }
}
