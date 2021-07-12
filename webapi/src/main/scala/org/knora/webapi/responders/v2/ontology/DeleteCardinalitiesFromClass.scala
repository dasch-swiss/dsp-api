/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.InternalSchema
import org.knora.webapi.exceptions.{BadRequestException, InconsistentRepositoryDataException}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  Cardinality,
  ClassInfoContentV2,
  DeleteCardinalitiesFromClassRequestV2,
  ReadClassInfoV2,
  ReadOntologyV2
}
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.store.triplestoremessages.{
  SparqlSelectRequest,
  SparqlUpdateRequest,
  SparqlUpdateResponse
}
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/**
 * Contains methods used for deleting cardinalities from a class
 */
object DeleteCardinalitiesFromClass {

  /**
   * Deletes the supplied cardinalities from a class, if the referenced properties are not used in instances
   * of the class and any subclasses.
   *
   * @param settings the applications settings.
   * @param storeManager the store manager actor.
   * @param internalClassIri the Class from which the cardinalities are deleted.
   * @param internalOntologyIri the Ontology of which the Class and Cardinalities are part of.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def deleteCardinalitiesFromClassTaskFuture(
    settings: KnoraSettingsImpl,
    storeManager: ActorRef,
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
             storeManager,
             internalOntologyIri = internalOntologyIri,
             expectedLastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate,
             featureFactoryConfig = deleteCardinalitiesFromClassRequest.featureFactoryConfig
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

      cardinalitiesToDelete: Map[SmartIri, Cardinality.KnoraCardinalityInfo] =
        deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(InternalSchema).directCardinalities

      _ = cardinalitiesToDelete.foreach(p =>
            isCardinalityDefinedOnClass(cacheData, p._1, p._2, internalClassIri, internalOntologyIri)
          )

      // Check if property is used in resources

      submittedPropertyToDelete: SmartIri = cardinalitiesToDelete.head._1
      propertyIsUsed: Boolean            <- isPropertyUsedInResources(settings, storeManager, submittedPropertyToDelete)
      _ = if (propertyIsUsed) {
            throw BadRequestException("Property is used in data. The cardinality cannot be deleted.")
          }

      // Make an update class definition in which the cardinality to delete is removed

      newClassDefinitionWithRemovedCardinality =
        currentClassDefinition.copy(
          directCardinalities = currentClassDefinition.directCardinalities - submittedPropertyToDelete
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
          .checkCardinalitiesBeforeAdding(
            internalClassDef = newClassDefinitionWithRemovedCardinality,
            allBaseClassIris = allBaseClassIris.toSet,
            cacheData = cacheData
          )

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

      inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
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
                         triplestore = settings.triplestoreType,
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         newCardinalities = newInternalClassDefWithLinkValueProps.directCardinalities,
                         lastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate,
                         currentTime = currentTime
                       )
                       .toString()

      _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

      // Check that the ontology's last modification date was updated.

      _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
             settings,
             storeManager,
             internalOntologyIri = internalOntologyIri,
             expectedLastModificationDate = currentTime,
             featureFactoryConfig = deleteCardinalitiesFromClassRequest.featureFactoryConfig
           )

      // Check that the data that was saved corresponds to the data that was submitted.

      loadedClassDef <- OntologyHelpers.loadClassDefinition(
                          settings,
                          storeManager,
                          classIri = internalClassIri,
                          featureFactoryConfig = deleteCardinalitiesFromClassRequest.featureFactoryConfig
                        )

      _ = if (loadedClassDef != newInternalClassDefWithLinkValueProps) {
            throw InconsistentRepositoryDataException(
              s"Attempted to save class definition $newInternalClassDefWithLinkValueProps, but $loadedClassDef was saved"
            )
          }

      // Update the cache.

      updatedOntology = ontology.copy(
                          ontologyMetadata = ontology.ontologyMetadata.copy(
                            lastModificationDate = Some(currentTime)
                          ),
                          classes = ontology.classes + (internalClassIri -> readClassInfo)
                        )

      _ = Cache.storeCacheData(
            cacheData.copy(
              ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology)
            )
          )

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
    storeManager: ActorRef,
    internalPropertyIri: SmartIri
  )(implicit ec: ExecutionContext, timeout: Timeout): Future[Boolean] =
    for {
      request <- Future(
                   org.knora.webapi.queries.sparql.v2.txt
                     .isPropertyUsed(
                       triplestore = settings.triplestoreType,
                       internalPropertyIri = internalPropertyIri.toString,
                       ignoreKnoraConstraints = true,
                       ignoreRdfSubjectAndObject = true
                     )
                     .toString()
                 )
      response: SparqlSelectResult <-
        (storeManager ? SparqlSelectRequest(request)).mapTo[SparqlSelectResult]
    } yield response.results.bindings.nonEmpty

  /**
   * Checks if the class is defined inside the ontology found in the cache.
   *
   * @param cacheData the cached ontology data
   * @param submittedClassInfoContentV2 the submitted class information
   * @param internalClassIri the internal class IRI
   * @param internalOntologyIri the internal ontology IRI
   * @return `true` if the class is defined inside the ontology found in the cache, otherwise throws an exception.
   */
  def classExists(
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

  /**
   * Check if the cardinality for a property is defined on a class.
   *
   * @param cacheData the cached ontology data.
   * @param propertyIri the property IRI for which we want to check if the cardinality is defined on the class.
   * @param cardinalityInfo the cardinality that should be defined for the property.
   * @param internalClassIri the class we are checking against.
   * @param internalOntologyIri the ontology containing the class.
   * @return `true` if the cardinality is defined on the class, otherwise throws an exception.
   */
  def isCardinalityDefinedOnClass(
    cacheData: Cache.OntologyCacheData,
    propertyIri: SmartIri,
    cardinalityInfo: KnoraCardinalityInfo,
    internalClassIri: SmartIri,
    internalOntologyIri: SmartIri
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    val currentOntologyState: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)
    val currentClassState: ClassInfoContentV2 = currentOntologyState.classes
      .getOrElse(
        internalClassIri,
        throw BadRequestException(
          s"Class ${internalClassIri} does not exist"
        )
      )
      .entityInfoContent
    val existingCardinality = currentClassState.directCardinalities
      .getOrElse(
        propertyIri,
        throw BadRequestException(
          s"Cardinality for property ${propertyIri} is not defined."
        )
      )
    if (existingCardinality.cardinality.equals(cardinalityInfo.cardinality)) {
      FastFuture.successful(true)
    } else {
      FastFuture.failed(
        throw BadRequestException(
          s"Submitted cardinality for property ${propertyIri} does not match existing cardinality."
        )
      )
    }
  }

}
