/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateResponse
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.settings.KnoraSettingsImpl

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Contains methods used for dealing with cardinalities on a class
 */
object CardinalityHandler {

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   *
   * @param settings the applications settings.
   * @param storeManager the store manager actor.
   * @param deleteCardinalitiesFromClassRequest the requested cardinalities to be deleted.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[CanDoResponseV2]] indicating whether a class's cardinalities can be deleted.
   */
  def canDeleteCardinalitiesFromClass(
    settings: KnoraSettingsImpl,
    appActor: ActorRef,
    deleteCardinalitiesFromClassRequest: CanDeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  )(implicit ec: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[CanDoResponseV2] = {
    for {
      cacheData: Cache.OntologyCacheData <- Cache.getCacheData

      submittedClassDefinition: ClassInfoContentV2 =
        deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema)

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
             settings,
             appActor,
             internalOntologyIri = internalOntologyIri,
             expectedLastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate
           )

      // Check that the class's rdf:type is owl:Class.

      rdfType: SmartIri = submittedClassDefinition.requireIriObject(
                            OntologyConstants.Rdf.Type.toSmartIri,
                            throw BadRequestException(s"No rdf:type specified")
                          )

      _ = if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
            throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
          }

      // Check that cardinalities were submitted.

      _ = if (submittedClassDefinition.directCardinalities.isEmpty) {
            throw BadRequestException("No cardinalities specified")
          }

      // Check that only one cardinality was submitted.

      _ = if (submittedClassDefinition.directCardinalities.size > 1) {
            throw BadRequestException("Only one cardinality is allowed to be submitted.")
          }

      // Check that the class exists
      currentClassDefinition: ClassInfoContentV2 <-
        classExists(
          cacheData,
          deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema),
          internalClassIri,
          internalOntologyIri
        )

      // FIXME: This seems not to work or is not needed. Check that it is a subclass of knora-base:Resource
      // _ <- isKnoraResourceClass(deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema))

      // Check that the submitted cardinality to delete is defined on this class

      cardinalitiesToDelete: Map[SmartIri, OwlCardinality.KnoraCardinalityInfo] =
        deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema).directCardinalities

      isDefinedOnClassList: List[Boolean] <- Future
                                               .sequence(cardinalitiesToDelete.map { p =>
                                                 for {
                                                   isDefined: Boolean <- isCardinalityDefinedOnClass(
                                                                           cacheData,
                                                                           p._1,
                                                                           p._2,
                                                                           internalClassIri,
                                                                           internalOntologyIri
                                                                         )
                                                 } yield isDefined
                                               }.toList)

      atLeastOneCardinalityNotDefinedOnClass: Boolean = isDefinedOnClassList.contains(false)

      // Check if property is used in resources of this class

      submittedPropertyToDelete: SmartIri = cardinalitiesToDelete.head._1
      propertyIsUsed: Boolean <- isPropertyUsedInResources(
                                   settings,
                                   appActor,
                                   internalClassIri,
                                   submittedPropertyToDelete
                                 )

      // Make an update class definition in which the cardinality to delete is removed

      submittedPropertyToDeleteIsLinkProperty: Boolean = cacheData
                                                           .ontologies(submittedPropertyToDelete.getOntologyFromEntity)
                                                           .properties(submittedPropertyToDelete)
                                                           .isLinkProp

      newClassDefinitionWithRemovedCardinality =
        currentClassDefinition.copy(
          directCardinalities =
            if (submittedPropertyToDeleteIsLinkProperty) {
              // if we want to remove a link property, then we also need to remove the corresponding link property value
              currentClassDefinition.directCardinalities - submittedPropertyToDelete - submittedPropertyToDelete.fromLinkPropToLinkValueProp
            } else
              currentClassDefinition.directCardinalities - submittedPropertyToDelete
        )

      // FIXME: Refactor. From here on is copy-paste from `changeClassCardinalities`, which I don't fully understand

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.

      allBaseClassIrisWithoutInternal: Seq[SmartIri] =
        newClassDefinitionWithRemovedCardinality.subClassOf.toSeq.flatMap { baseClassIri =>
          cacheData.subClassOfRelations.getOrElse(
            baseClassIri,
            Seq.empty[SmartIri]
          )
        }

      allBaseClassIris: Seq[SmartIri] = internalClassIri +: allBaseClassIrisWithoutInternal

      (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) =
        OntologyHelpers
          .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
            internalClassDef = newClassDefinitionWithRemovedCardinality,
            allBaseClassIris = allBaseClassIris.toSet,
            cacheData = cacheData,
            // since we only want to delete (and have already removed what we want), the rest of the link properties
            // need to be marked as wanting to keep.
            existingLinkPropsToKeep =
              newClassDefinitionWithRemovedCardinality.directCardinalities.keySet // gets all keys from the map as a set
                .map(propertyIri =>
                  cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri)
                )                                     // turn the propertyIri into a ReadPropertyInfoV2
                .filter(_.isLinkProp)                 // we are only interested in link properties
                .map(_.entityInfoContent.propertyIri) // turn whatever is left back to a propertyIri
          )
          .fold(e => throw e.head, v => v)

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ = Cache.checkOntologyReferencesInClassDef(
            ontologyCacheData = cacheData,
            classDef = newInternalClassDefWithLinkValueProps,
            errorFun = { msg: String =>
              throw BadRequestException(msg)
            }
          )

      // response is true only when property is not used in data and cardinality is defined directly on that class
    } yield CanDoResponseV2(!propertyIsUsed && !atLeastOneCardinalityNotDefinedOnClass)
  }

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Deletes the supplied cardinalities from a class, if the referenced properties are not used in instances
   * of the class and any subclasses.
   *
   * @param settings the applications settings.
   * @param storeManager the store manager actor.
   * @param deleteCardinalitiesFromClassRequest the requested cardinalities to be deleted.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def deleteCardinalitiesFromClass(
    settings: KnoraSettingsImpl,
    appActor: ActorRef,
    deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  )(implicit ec: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[ReadOntologyV2] =
    for {
      cacheData: Cache.OntologyCacheData <- Cache.getCacheData
      ontology                            = cacheData.ontologies(internalOntologyIri)
      submittedClassDefinition: ClassInfoContentV2 =
        deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema)

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
             settings,
             appActor,
             internalOntologyIri = internalOntologyIri,
             expectedLastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate
           )

      // Check that the class's rdf:type is owl:Class.

      rdfType: SmartIri = submittedClassDefinition.requireIriObject(
                            OntologyConstants.Rdf.Type.toSmartIri,
                            throw BadRequestException(s"No rdf:type specified")
                          )

      _ = if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
            throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
          }

      // Check that cardinalities were submitted.

      _ = if (submittedClassDefinition.directCardinalities.isEmpty) {
            throw BadRequestException("No cardinalities specified")
          }

      // Check that only one cardinality was submitted.

      _ = if (submittedClassDefinition.directCardinalities.size > 1) {
            throw BadRequestException("Only one cardinality is allowed to be submitted.")
          }

      // Check that the class exists
      currentClassDefinition: ClassInfoContentV2 <-
        classExists(
          cacheData,
          deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema),
          internalClassIri,
          internalOntologyIri
        )

      // Check that the submitted cardinality to delete is defined on this class

      cardinalitiesToDelete: Map[SmartIri, OwlCardinality.KnoraCardinalityInfo] =
        deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema).directCardinalities

      isDefinedOnClassList: List[Boolean] <- Future
                                               .sequence(cardinalitiesToDelete.map { p =>
                                                 for {
                                                   isDefined: Boolean <- isCardinalityDefinedOnClass(
                                                                           cacheData,
                                                                           p._1,
                                                                           p._2,
                                                                           internalClassIri,
                                                                           internalOntologyIri
                                                                         )
                                                 } yield isDefined
                                               }.toList)

      _ = if (isDefinedOnClassList.contains(false)) {
            throw BadRequestException(
              "The cardinality is not defined directly on the class and cannot be deleted."
            )
          }

      // Check if property is used in resources of this class

      submittedPropertyToDelete: SmartIri = cardinalitiesToDelete.head._1
      propertyIsUsed: Boolean <- isPropertyUsedInResources(
                                   settings,
                                   appActor,
                                   internalClassIri,
                                   submittedPropertyToDelete
                                 )
      _ = if (propertyIsUsed) {
            throw BadRequestException("Property is used in data. The cardinality cannot be deleted.")
          }

      // Make an update class definition in which the cardinality to delete is removed

      submittedPropertyToDeleteIsLinkProperty: Boolean = cacheData
                                                           .ontologies(submittedPropertyToDelete.getOntologyFromEntity)
                                                           .properties(submittedPropertyToDelete)
                                                           .isLinkProp

      newClassDefinitionWithRemovedCardinality =
        currentClassDefinition.copy(
          directCardinalities =
            if (submittedPropertyToDeleteIsLinkProperty) {
              // if we want to remove a link property, then we also need to remove the corresponding link property value
              currentClassDefinition.directCardinalities - submittedPropertyToDelete - submittedPropertyToDelete.fromLinkPropToLinkValueProp
            } else
              currentClassDefinition.directCardinalities - submittedPropertyToDelete
        )

      // FIXME: Refactor. From here on is copy-paste from `changeClassCardinalities`, which I don't fully understand

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.

      allBaseClassIrisWithoutInternal: Seq[SmartIri] =
        newClassDefinitionWithRemovedCardinality.subClassOf.toSeq.flatMap { baseClassIri =>
          cacheData.subClassOfRelations.getOrElse(
            baseClassIri,
            Seq.empty[SmartIri]
          )
        }

      allBaseClassIris: Seq[SmartIri] = internalClassIri +: allBaseClassIrisWithoutInternal

      (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) =
        OntologyHelpers
          .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
            internalClassDef = newClassDefinitionWithRemovedCardinality,
            allBaseClassIris = allBaseClassIris.toSet,
            cacheData = cacheData,
            // since we only want to delete (and have already removed what we want), the rest of the link properties
            // need to be marked as wanting to keep.
            existingLinkPropsToKeep =
              newClassDefinitionWithRemovedCardinality.directCardinalities.keySet // gets all keys from the map as a set
                .map(propertyIri =>
                  cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri)
                )                                     // turn the propertyIri into a ReadPropertyInfoV2
                .filter(_.isLinkProp)                 // we are only interested in link properties
                .map(_.entityInfoContent.propertyIri) // turn whatever is left back to a propertyIri
          )
          .fold(e => throw e.head, v => v)

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ = Cache.checkOntologyReferencesInClassDef(
            ontologyCacheData = cacheData,
            classDef = newInternalClassDefWithLinkValueProps,
            errorFun = { msg: String =>
              throw BadRequestException(msg)
            }
          )

      // Prepare to update the ontology cache. (No need to deal with SPARQL-escaping here, because there
      // isn't any text to escape in cardinalities.)

      propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet

      inheritedCardinalities: Map[SmartIri, OwlCardinality.KnoraCardinalityInfo] =
        cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
          newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
        }

      readClassInfo = ReadClassInfoV2(
                        entityInfoContent = newInternalClassDefWithLinkValueProps,
                        allBaseClasses = allBaseClassIris,
                        isResourceClass = true,
                        canBeInstantiated = true,
                        inheritedCardinalities = inheritedCardinalities,
                        knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isKnoraResourceProperty(propertyIri, cacheData)
                        ),
                        linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isLinkProp(propertyIri, cacheData)
                        ),
                        linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isLinkValueProp(propertyIri, cacheData)
                        ),
                        fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          OntologyHelpers.isFileValueProp(propertyIri, cacheData)
                        )
                      )

      // Add the cardinalities to the class definition in the triplestore.

      currentTime: Instant = Instant.now

      updateSparql = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                       .replaceClassCardinalities(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         newCardinalities = newInternalClassDefWithLinkValueProps.directCardinalities,
                         lastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate,
                         currentTime = currentTime
                       )
                       .toString()

      _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

      // Check that the ontology's last modification date was updated.

      _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
             settings,
             appActor,
             internalOntologyIri = internalOntologyIri,
             expectedLastModificationDate = currentTime
           )

      // Check that the data that was saved corresponds to the data that was submitted.

      loadedClassDef <- OntologyHelpers.loadClassDefinition(
                          settings,
                          appActor,
                          classIri = internalClassIri
                        )

      _ = if (loadedClassDef != newInternalClassDefWithLinkValueProps) {
            throw InconsistentRepositoryDataException(
              s"Attempted to save class definition $newInternalClassDefWithLinkValueProps, but $loadedClassDef was saved"
            )
          }

      // Update subclasses and write the cache.

      updatedOntology = ontology.copy(
                          ontologyMetadata = ontology.ontologyMetadata.copy(
                            lastModificationDate = Some(currentTime)
                          ),
                          classes = ontology.classes + (internalClassIri -> readClassInfo)
                        )

      _ <- Cache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

      // Read the data back from the cache.

      response: ReadOntologyV2 <- OntologyHelpers.getClassDefinitionsFromOntologyV2(
                                    classIris = Set(internalClassIri),
                                    allLanguages = true,
                                    requestingUser = deleteCardinalitiesFromClassRequest.requestingUser
                                  )

    } yield response

  /**
   * Check if a property entity is used in resource instances. Returns `true` if
   * it is used, and `false` if it is not used.
   *
   * @param settings application settings.
   * @param storeManager store manager actor ref.
   * @param internalPropertyIri the IRI of the entity that is being checked for usage.
   * @param ec the execution context onto with the future will run.
   * @param timeout the timeout for the future.
   * @return a [[Boolean]] denoting if the property entity is used.
   */
  def isPropertyUsedInResources(
    settings: KnoraSettingsImpl,
    appActor: ActorRef,
    internalClassIri: SmartIri,
    internalPropertyIri: SmartIri
  )(implicit ec: ExecutionContext, timeout: Timeout): Future[Boolean] =
    for {
      request <- Future(
                   org.knora.webapi.queries.sparql.v2.txt
                     .isPropertyUsed(
                       internalPropertyIri = internalPropertyIri.toString,
                       internalClassIri = internalClassIri.toString,
                       ignoreKnoraConstraints = true,
                       ignoreRdfSubjectAndObject = true
                     )
                     .toString()
                 )
      response: SparqlAskResponse <-
        appActor.ask(SparqlAskRequest(request)).mapTo[SparqlAskResponse]
    } yield response.result

  /**
   * Checks if the class is defined inside the ontology found in the cache.
   *
   * @param cacheData the cached ontology data
   * @param submittedClassInfoContentV2 the submitted class information
   * @param internalClassIri the internal class IRI
   * @param internalOntologyIri the internal ontology IRI
   * @return `true` if the class is defined inside the ontology found in the cache, otherwise throws an exception.
   */
  private def classExists(
    cacheData: Cache.OntologyCacheData,
    submittedClassInfoContentV2: ClassInfoContentV2,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  )(implicit ec: ExecutionContext): Future[ClassInfoContentV2] = for {
    currentOntologyState: ReadOntologyV2 <- Future(cacheData.ontologies(internalOntologyIri))
    currentClassDefinition = currentOntologyState.classes
                               .getOrElse(
                                 internalClassIri,
                                 throw BadRequestException(
                                   s"Class ${submittedClassInfoContentV2.classIri} does not exist"
                                 )
                               )
                               .entityInfoContent
  } yield currentClassDefinition

  /**
   * Check if the cardinality for a property is defined on a class.
   *
   * @param cacheData the cached ontology data.
   * @param propertyIri the property IRI for which we want to check if the cardinality is defined on the class.
   * @param cardinalityInfo the cardinality that should be defined for the property.
   * @param internalClassIri the class we are checking against.
   * @param internalOntologyIri the ontology containing the class.
   * @return `true` if the cardinality is defined on the class, `false` otherwise
   */
  def isCardinalityDefinedOnClass(
    cacheData: Cache.OntologyCacheData,
    propertyIri: SmartIri,
    cardinalityInfo: OwlCardinality.KnoraCardinalityInfo,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val currentOntologyState: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)

    val readClassInfo: ReadClassInfoV2 = currentOntologyState.classes
      .getOrElse(
        internalClassIri,
        throw BadRequestException(
          s"Class $internalClassIri does not exist"
        )
      )

    // if cardinality is inherited, it's not directly defined on that class
    if (readClassInfo.inheritedCardinalities.keySet.contains(propertyIri)) {
      return FastFuture.successful(false)
    }

    val currentClassState: ClassInfoContentV2 = readClassInfo.entityInfoContent
    val existingCardinality                   = currentClassState.directCardinalities.get(propertyIri)
    existingCardinality match {
      case Some(cardinality) =>
        if (cardinality.cardinality.equals(cardinalityInfo.cardinality)) {
          FastFuture.successful(true)
        } else {
          FastFuture.failed(
            throw BadRequestException(
              s"Submitted cardinality for property $propertyIri does not match existing cardinality."
            )
          )
        }
      case None =>
        throw BadRequestException(
          s"Submitted cardinality for property $propertyIri is not defined for class $internalClassIri."
        )
    }

  }

  /**
   * Checks if the class is a subclass of `knora-base:Resource`.
   *
   * @param submittedClassInfoContentV2 the class to check
   * @return `true` if the class is a subclass of `knora-base:Resource`, otherwise throws an exception.
   */
  def isKnoraResourceClass(
    submittedClassInfoContentV2: ClassInfoContentV2
  )(implicit ec: ExecutionContext, stringFormatter: StringFormatter): Future[Boolean] =
    if (submittedClassInfoContentV2.subClassOf.contains(KnoraBase.Resource.toSmartIri)) {
      FastFuture.successful(true)
    } else {
      FastFuture.failed(
        throw BadRequestException(
          s"Class ${submittedClassInfoContentV2.classIri} is not a subclass of ${KnoraBase.Resource.toSmartIri}. $submittedClassInfoContentV2"
        )
      )
    }

}
