/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v2

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.stream.Materializer
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{
  DefaultObjectAccessPermissionsStringForResourceClassGetADM,
  DefaultObjectAccessPermissionsStringResponseADM,
  ResourceCreateOperation
}
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.{SipiGetTextFileRequest, SipiGetTextFileResponse}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.SparqlTemplateResourceToCreate
import org.knora.webapi.messages.util.ConstructResponseUtilV2.MappingAndXSLTransformation
import org.knora.webapi.messages.util.PermissionUtilADM.{
  AGreaterThanB,
  DeletePermission,
  ModifyPermission,
  PermissionComparisonResult
}
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.rdf.{
  JsonLDArray,
  JsonLDDocument,
  JsonLDInt,
  JsonLDKeywords,
  JsonLDObject,
  JsonLDString,
  SparqlSelectResult,
  VariableResultsRow
}
import org.knora.webapi.messages.util.search.ConstructQuery
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.{
  GetMappingRequestV2,
  GetMappingResponseV2,
  GetXSLTransformationRequestV2,
  GetXSLTransformationResponseV2
}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.{SuccessResponseV2, UpdateResultInProject}
import org.knora.webapi.messages.{OntologyConstants, SmartIri}
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.util._

import scala.concurrent.Future
import scala.util.{Failure, Success}

class ResourcesResponderV2(responderData: ResponderData) extends ResponderWithStandoffV2(responderData) {

  /* actor materializer needed for http requests */
  implicit val materializer: Materializer = Materializer.matFromSystem(system)

  /**
    * Represents a resource that is ready to be created and whose contents can be verified afterwards.
    *
    * @param sparqlTemplateResourceToCreate a [[SparqlTemplateResourceToCreate]] describing SPARQL for creating
    *                                       the resource.
    * @param values                         the resource's values for verification.
    * @param hasStandoffLink                `true` if the property `knora-base:hasStandoffLinkToValue` was automatically added.
    */
  private case class ResourceReadyToCreate(sparqlTemplateResourceToCreate: SparqlTemplateResourceToCreate,
                                           values: Map[SmartIri, Seq[UnverifiedValueV2]],
                                           hasStandoffLink: Boolean)

  /**
    * Receives a message of type [[ResourcesResponderRequestV2]], and returns an appropriate response message.
    */
  def receive(msg: ResourcesResponderRequestV2) = msg match {
    case ResourcesGetRequestV2(resIris,
                               propertyIri,
                               valueUuid,
                               versionDate,
                               withDeleted,
                               targetSchema,
                               schemaOptions,
                               featureFactoryConfig,
                               requestingUser) =>
      getResourcesV2(resIris,
                     propertyIri,
                     valueUuid,
                     versionDate,
                     withDeleted,
                     targetSchema,
                     schemaOptions,
                     featureFactoryConfig,
                     requestingUser)
    case ResourcesPreviewGetRequestV2(resIris,
                                      withDeletedResource,
                                      targetSchema,
                                      featureFactoryConfig,
                                      requestingUser) =>
      getResourcePreviewV2(resIris, withDeletedResource, targetSchema, featureFactoryConfig, requestingUser)
    case ResourceTEIGetRequestV2(resIri,
                                 textProperty,
                                 mappingIri,
                                 gravsearchTemplateIri,
                                 headerXSLTIri,
                                 featureFactoryConfig,
                                 requestingUser) =>
      getResourceAsTeiV2(resIri,
                         textProperty,
                         mappingIri,
                         gravsearchTemplateIri,
                         headerXSLTIri,
                         featureFactoryConfig,
                         requestingUser)

    case createResourceRequestV2: CreateResourceRequestV2 => createResourceV2(createResourceRequestV2)

    case updateResourceMetadataRequestV2: UpdateResourceMetadataRequestV2 =>
      updateResourceMetadataV2(updateResourceMetadataRequestV2)

    case deleteOrEraseResourceRequestV2: DeleteOrEraseResourceRequestV2 =>
      deleteOrEraseResourceV2(deleteOrEraseResourceRequestV2)

    case graphDataGetRequest: GraphDataGetRequestV2 => getGraphDataResponseV2(graphDataGetRequest)

    case resourceHistoryRequest: ResourceVersionHistoryGetRequestV2 =>
      getResourceHistoryV2(resourceHistoryRequest)

    case resourceIIIFManifestRequest: ResourceIIIFManifestGetRequestV2 => getIIIFManifestV2(resourceIIIFManifestRequest)

    case resourceHistoryEventsRequest: ResourceHistoryEventsGetRequestV2 =>
      getResourceHistoryEvents(resourceHistoryEventsRequest)

    case projectHistoryEventsRequestV2: ProjectResourcesWithHistoryGetRequestV2 =>
      getProjectResourceHistoryEvents(projectHistoryEventsRequestV2)

    case other =>
      handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
    * Creates a new resource.
    *
    * @param createResourceRequestV2 the request to create the resource.
    * @return a [[ReadResourcesSequenceV2]] containing a preview of the resource.
    */
  private def createResourceV2(createResourceRequestV2: CreateResourceRequestV2): Future[ReadResourcesSequenceV2] = {

    def makeTaskFuture(resourceIri: IRI, resourceUUID: UUID): Future[ReadResourcesSequenceV2] = {
      for {
        //check if resourceIri already exists holding a lock on the IRI
        result <- stringFormatter.checkIriExists(resourceIri, storeManager)

        _ = if (result) {
          throw DuplicateValueException(s"Resource IRI: '${resourceIri}' already exists.")
        }

        // Convert the resource to the internal ontology schema.
        internalCreateResource: CreateResourceV2 <- Future(
          createResourceRequestV2.createResource.toOntologySchema(InternalSchema))

        // Check link targets and list nodes that should exist.

        _ <- checkStandoffLinkTargets(
          values = internalCreateResource.flatValues,
          featureFactoryConfig = createResourceRequestV2.featureFactoryConfig,
          requestingUser = createResourceRequestV2.requestingUser
        )

        _ <- checkListNodes(internalCreateResource.flatValues, createResourceRequestV2.requestingUser)

        // Get the class IRIs of all the link targets in the request.
        linkTargetClasses: Map[IRI, SmartIri] <- getLinkTargetClasses(
          resourceIri: IRI,
          internalCreateResources = Seq(internalCreateResource),
          featureFactoryConfig = createResourceRequestV2.featureFactoryConfig,
          requestingUser = createResourceRequestV2.requestingUser
        )

        // Get the definitions of the resource class and its properties, as well as of the classes of all
        // resources that are link targets.

        resourceClassEntityInfoResponse: EntityInfoGetResponseV2 <- (responderManager ? EntityInfoGetRequestV2(
          classIris = linkTargetClasses.values.toSet + internalCreateResource.resourceClassIri,
          requestingUser = createResourceRequestV2.requestingUser
        )).mapTo[EntityInfoGetResponseV2]

        resourceClassInfo: ReadClassInfoV2 = resourceClassEntityInfoResponse.classInfoMap(
          internalCreateResource.resourceClassIri)

        propertyEntityInfoResponse: EntityInfoGetResponseV2 <- (responderManager ? EntityInfoGetRequestV2(
          propertyIris = resourceClassInfo.knoraResourceProperties,
          requestingUser = createResourceRequestV2.requestingUser
        )).mapTo[EntityInfoGetResponseV2]

        allEntityInfo = EntityInfoGetResponseV2(
          classInfoMap = resourceClassEntityInfoResponse.classInfoMap,
          propertyInfoMap = propertyEntityInfoResponse.propertyInfoMap
        )

        // Get the default permissions of the resource class.

        defaultResourcePermissionsMap <- getResourceClassDefaultPermissions(
          projectIri = createResourceRequestV2.createResource.projectADM.id,
          resourceClassIris = Set(internalCreateResource.resourceClassIri),
          requestingUser = createResourceRequestV2.requestingUser
        )

        defaultResourcePermissions: String = defaultResourcePermissionsMap(internalCreateResource.resourceClassIri)

        // Get the default permissions of each property used.

        defaultPropertyPermissionsMap: Map[SmartIri, Map[SmartIri, String]] <- getDefaultPropertyPermissions(
          projectIri = createResourceRequestV2.createResource.projectADM.id,
          resourceClassProperties = Map(internalCreateResource.resourceClassIri -> internalCreateResource.values.keySet),
          requestingUser = createResourceRequestV2.requestingUser
        )

        defaultPropertyPermissions: Map[SmartIri, String] = defaultPropertyPermissionsMap(
          internalCreateResource.resourceClassIri)

        // Make a versionDate for the resource and its values.
        creationDate: Instant = internalCreateResource.creationDate.getOrElse(Instant.now)

        // Do the remaining pre-update checks and make a ResourceReadyToCreate describing the SPARQL
        // for creating the resource.
        resourceReadyToCreate: ResourceReadyToCreate <- generateResourceReadyToCreate(
          resourceIri = resourceIri,
          resourceUUID = resourceUUID,
          internalCreateResource = internalCreateResource,
          linkTargetClasses = linkTargetClasses,
          entityInfo = allEntityInfo,
          clientResourceIDs = Map.empty[IRI, String],
          defaultResourcePermissions = defaultResourcePermissions,
          defaultPropertyPermissions = defaultPropertyPermissions,
          creationDate = creationDate,
          featureFactoryConfig = createResourceRequestV2.featureFactoryConfig,
          requestingUser = createResourceRequestV2.requestingUser
        )

        // Get the IRI of the named graph in which the resource will be created.
        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(createResourceRequestV2.createResource.projectADM)

        // Generate SPARQL for creating the resource.
        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .createNewResources(
            dataNamedGraph = dataNamedGraph,
            triplestore = settings.triplestoreType,
            resourcesToCreate = Seq(resourceReadyToCreate.sparqlTemplateResourceToCreate),
            projectIri = createResourceRequestV2.createResource.projectADM.id,
            creatorIri = createResourceRequestV2.requestingUser.id,
            formatter = stringFormatter
          )
          .toString()

        // Do the update.
        _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        // Verify that the resource was created correctly.
        previewOfCreatedResource: ReadResourcesSequenceV2 <- verifyResource(
          resourceReadyToCreate = resourceReadyToCreate,
          projectIri = createResourceRequestV2.createResource.projectADM.id,
          featureFactoryConfig = createResourceRequestV2.featureFactoryConfig,
          requestingUser = createResourceRequestV2.requestingUser
        )
      } yield previewOfCreatedResource
    }

    val triplestoreUpdateFuture: Future[ReadResourcesSequenceV2] = for {
      // Don't allow anonymous users to create resources.
      _ <- Future {
        if (createResourceRequestV2.requestingUser.isAnonymousUser) {
          throw ForbiddenException("Anonymous users aren't allowed to create resources")
        } else {
          createResourceRequestV2.requestingUser.id
        }
      }

      // Ensure that the project isn't the system project or the shared ontologies project.

      projectIri = createResourceRequestV2.createResource.projectADM.id

      _ = if (projectIri == OntologyConstants.KnoraAdmin.SystemProject || projectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject) {
        throw BadRequestException(s"Resources cannot be created in project <$projectIri>")
      }

      // Ensure that the resource class isn't from a non-shared ontology in another project.

      resourceClassOntologyIri: SmartIri = createResourceRequestV2.createResource.resourceClassIri.getOntologyFromEntity
      readOntologyMetadataV2: ReadOntologyMetadataV2 <- (responderManager ? OntologyMetadataGetByIriRequestV2(
        Set(resourceClassOntologyIri),
        createResourceRequestV2.requestingUser)).mapTo[ReadOntologyMetadataV2]
      ontologyMetadata: OntologyMetadataV2 = readOntologyMetadataV2.ontologies.headOption
        .getOrElse(throw BadRequestException(s"Ontology $resourceClassOntologyIri not found"))
      ontologyProjectIri: IRI = ontologyMetadata.projectIri
        .getOrElse(throw InconsistentRepositoryDataException(s"Ontology $resourceClassOntologyIri has no project"))
        .toString

      _ = if (projectIri != ontologyProjectIri && !(ontologyMetadata.ontologyIri.isKnoraBuiltInDefinitionIri || ontologyMetadata.ontologyIri.isKnoraSharedDefinitionIri)) {
        throw BadRequestException(
          s"Cannot create a resource in project <$projectIri> with resource class <${createResourceRequestV2.createResource.resourceClassIri}>, which is defined in a non-shared ontology in another project")
      }

      // Check user's PermissionProfile (part of UserADM) to see if the user has the permission to
      // create a new resource in the given project.

      internalResourceClassIri: SmartIri = createResourceRequestV2.createResource.resourceClassIri
        .toOntologySchema(InternalSchema)

      _ = if (!createResourceRequestV2.requestingUser.permissions.hasPermissionFor(
                ResourceCreateOperation(internalResourceClassIri.toString),
                projectIri,
                None)) {
        throw ForbiddenException(
          s"User ${createResourceRequestV2.requestingUser.username} does not have permission to create a resource of class <${createResourceRequestV2.createResource.resourceClassIri}> in project <$projectIri>")
      }

      // Make new UUID.
      resourceUUID: UUID <- Future.successful(
        makeNewUUID(createResourceRequestV2.createResource.resourceIri,
                    createResourceRequestV2.createResource.resourceUUID))

      resourceIri: IRI <- checkOrCreateEntityIri(createResourceRequestV2.createResource.resourceIri,
                                                 stringFormatter.makeResourceIri(Some(resourceUUID)))

      // Do the remaining pre-update checks and the update while holding an update lock on the resource to be created.
      taskResult <- IriLocker.runWithIriLock(
        createResourceRequestV2.apiRequestID,
        resourceIri,
        () => makeTaskFuture(resourceIri, resourceUUID)
      )
    } yield taskResult

    // If the request includes file values, tell Sipi to move the files to permanent storage if the update
    // succeeded, or to delete the temporary files if the update failed.
    doSipiPostUpdateForResources(
      updateFuture = triplestoreUpdateFuture,
      createResources = Seq(createResourceRequestV2.createResource),
      requestingUser = createResourceRequestV2.requestingUser
    )
  }

  /**
    * Updates a resources metadata.
    *
    * @param updateResourceMetadataRequestV2 the update request.
    * @return a [[SuccessResponseV2]].
    */
  private def updateResourceMetadataV2(
      updateResourceMetadataRequestV2: UpdateResourceMetadataRequestV2): Future[SuccessResponseV2] = {
    def makeTaskFuture: Future[SuccessResponseV2] = {
      for {
        // Get the metadata of the resource to be updated.
        resourcesSeq: ReadResourcesSequenceV2 <- getResourcePreviewV2(
          resourceIris = Seq(updateResourceMetadataRequestV2.resourceIri),
          targetSchema = ApiV2Complex,
          featureFactoryConfig = updateResourceMetadataRequestV2.featureFactoryConfig,
          requestingUser = updateResourceMetadataRequestV2.requestingUser
        )

        resource: ReadResourceV2 = resourcesSeq.toResource(updateResourceMetadataRequestV2.resourceIri)
        internalResourceClassIri = updateResourceMetadataRequestV2.resourceClassIri.toOntologySchema(InternalSchema)

        // Make sure that the resource's class is what the client thinks it is.
        _ = if (resource.resourceClassIri != internalResourceClassIri) {
          throw BadRequestException(
            s"Resource <${resource.resourceIri}> is not a member of class <${updateResourceMetadataRequestV2.resourceClassIri}>")
        }

        // If resource has already been modified, make sure that its lastModificationDate is given in the request body.
        _ = if (resource.lastModificationDate.nonEmpty && updateResourceMetadataRequestV2.maybeLastModificationDate.isEmpty) {
          throw EditConflictException(
            s"Resource <${resource.resourceIri}> has been modified in the past. Its lastModificationDate " +
              s"${resource.lastModificationDate.get} must be included in the request body.")
        }

        // Make sure that the resource hasn't been updated since the client got its last modification date.
        _ = if (updateResourceMetadataRequestV2.maybeLastModificationDate.nonEmpty &&
                resource.lastModificationDate != updateResourceMetadataRequestV2.maybeLastModificationDate) {
          throw EditConflictException(s"Resource <${resource.resourceIri}> has been modified since you last read it")
        }

        // Check that the user has permission to modify the resource.
        _ = ResourceUtilV2.checkResourcePermission(
          resourceInfo = resource,
          permissionNeeded = ModifyPermission,
          requestingUser = updateResourceMetadataRequestV2.requestingUser
        )

        // Get the IRI of the named graph in which the resource is stored.
        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(resource.projectADM)

        newModificationDate: Instant = updateResourceMetadataRequestV2.maybeNewModificationDate match {
          case Some(submittedNewModificationDate) =>
            if (resource.lastModificationDate.exists(_.isAfter(submittedNewModificationDate))) {
              throw BadRequestException(
                s"Submitted knora-api:newModificationDate is before the resource's current knora-api:lastModificationDate")
            } else {
              submittedNewModificationDate
            }
          case None => Instant.now
        }

        // Generate SPARQL for updating the resource.
        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .changeResourceMetadata(
            triplestore = settings.triplestoreType,
            dataNamedGraph = dataNamedGraph,
            resourceIri = updateResourceMetadataRequestV2.resourceIri,
            resourceClassIri = internalResourceClassIri,
            maybeLastModificationDate = updateResourceMetadataRequestV2.maybeLastModificationDate,
            newModificationDate = newModificationDate,
            maybeLabel = updateResourceMetadataRequestV2.maybeLabel,
            maybePermissions = updateResourceMetadataRequestV2.maybePermissions
          )
          .toString()

        // Do the update.
        _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        // Verify that the resource was updated correctly.

        updatedResourcesSeq: ReadResourcesSequenceV2 <- getResourcePreviewV2(
          resourceIris = Seq(updateResourceMetadataRequestV2.resourceIri),
          targetSchema = ApiV2Complex,
          featureFactoryConfig = updateResourceMetadataRequestV2.featureFactoryConfig,
          requestingUser = updateResourceMetadataRequestV2.requestingUser
        )

        _ = if (updatedResourcesSeq.resources.size != 1) {
          throw AssertionException(s"Expected one resource, got ${resourcesSeq.resources.size}")
        }

        updatedResource: ReadResourceV2 = updatedResourcesSeq.resources.head

        _ = if (!updatedResource.lastModificationDate.contains(newModificationDate)) {
          throw UpdateNotPerformedException(
            s"Updated resource has last modification date ${updatedResource.lastModificationDate}, expected $newModificationDate")
        }

        _ = updateResourceMetadataRequestV2.maybeLabel match {
          case Some(newLabel) =>
            if (!updatedResource.label.contains(stringFormatter.fromSparqlEncodedString(newLabel))) {
              throw UpdateNotPerformedException()
            }

          case None => ()
        }

        _ = updateResourceMetadataRequestV2.maybePermissions match {
          case Some(newPermissions) =>
            if (PermissionUtilADM.parsePermissions(updatedResource.permissions) != PermissionUtilADM.parsePermissions(
                  newPermissions)) {
              throw UpdateNotPerformedException()
            }

          case None => ()
        }

        // If the resource's label was changed, update the full-text search index.
        _ <- updateResourceMetadataRequestV2.maybeLabel match {
          case Some(_) =>
            for {
              _ <- (storeManager ? SearchIndexUpdateRequest(Some(updateResourceMetadataRequestV2.resourceIri)))
                .mapTo[SparqlUpdateResponse]
            } yield ()

          case None => FastFuture.successful(())
        }
      } yield SuccessResponseV2("Resource metadata updated")
    }

    for {
      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        updateResourceMetadataRequestV2.apiRequestID,
        updateResourceMetadataRequestV2.resourceIri,
        () => makeTaskFuture
      )
    } yield taskResult
  }

  /**
    * Either marks a resource as deleted or erases it from the triplestore, depending on the value of `erase`
    * in the request message.
    *
    * @param deleteOrEraseResourceV2 the request message.
    */
  private def deleteOrEraseResourceV2(
      deleteOrEraseResourceV2: DeleteOrEraseResourceRequestV2): Future[SuccessResponseV2] = {
    if (deleteOrEraseResourceV2.erase) {
      eraseResourceV2(deleteOrEraseResourceV2)
    } else {
      markResourceAsDeletedV2(deleteOrEraseResourceV2)
    }
  }

  /**
    * Marks a resource as deleted.
    *
    * @param deleteResourceV2 the request message.
    */
  private def markResourceAsDeletedV2(deleteResourceV2: DeleteOrEraseResourceRequestV2): Future[SuccessResponseV2] = {
    def makeTaskFuture: Future[SuccessResponseV2] = {
      for {
        // Get the metadata of the resource to be updated.
        resourcesSeq: ReadResourcesSequenceV2 <- getResourcePreviewV2(
          resourceIris = Seq(deleteResourceV2.resourceIri),
          targetSchema = ApiV2Complex,
          featureFactoryConfig = deleteResourceV2.featureFactoryConfig,
          requestingUser = deleteResourceV2.requestingUser
        )

        resource: ReadResourceV2 = resourcesSeq.toResource(deleteResourceV2.resourceIri)
        internalResourceClassIri = deleteResourceV2.resourceClassIri.toOntologySchema(InternalSchema)

        // Make sure that the resource's class is what the client thinks it is.
        _ = if (resource.resourceClassIri != internalResourceClassIri) {
          throw BadRequestException(
            s"Resource <${resource.resourceIri}> is not a member of class <${deleteResourceV2.resourceClassIri}>")
        }

        // Make sure that the resource hasn't been updated since the client got its last modification date.
        _ = if (resource.lastModificationDate != deleteResourceV2.maybeLastModificationDate) {
          throw EditConflictException(s"Resource <${resource.resourceIri}> has been modified since you last read it")
        }

        // If a custom delete date was provided, make sure it's later than the resource's most recent timestamp.
        _ = if (deleteResourceV2.maybeDeleteDate.exists(
                  !_.isAfter(resource.lastModificationDate.getOrElse(resource.creationDate)))) {
          throw BadRequestException(
            s"A custom delete date must be later than the date when the resource was created or last modified")
        }

        // Check that the user has permission to mark the resource as deleted.
        _ = ResourceUtilV2.checkResourcePermission(
          resourceInfo = resource,
          permissionNeeded = DeletePermission,
          requestingUser = deleteResourceV2.requestingUser
        )

        // Get the IRI of the named graph in which the resource is stored.
        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(resource.projectADM)

        // Generate SPARQL for marking the resource as deleted.
        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .deleteResource(
            triplestore = settings.triplestoreType,
            dataNamedGraph = dataNamedGraph,
            resourceIri = deleteResourceV2.resourceIri,
            maybeDeleteComment = deleteResourceV2.maybeDeleteComment,
            currentTime = deleteResourceV2.maybeDeleteDate.getOrElse(Instant.now),
            requestingUser = deleteResourceV2.requestingUser.id
          )
          .toString()

        // Do the update.
        _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        // Verify that the resource was deleted correctly.

        sparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .checkResourceDeletion(
            triplestore = settings.triplestoreType,
            resourceIri = deleteResourceV2.resourceIri
          )
          .toString()

        sparqlSelectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]

        rows = sparqlSelectResponse.results.bindings

        _ = if (rows.isEmpty || !stringFormatter.optionStringToBoolean(
                  rows.head.rowMap.get("isDeleted"),
                  throw InconsistentRepositoryDataException(
                    s"Invalid boolean for isDeleted: ${rows.head.rowMap.get("isDeleted")}"))) {
          throw UpdateNotPerformedException(
            s"Resource <${deleteResourceV2.resourceIri}> was not marked as deleted. Please report this as a possible bug.")
        }
      } yield SuccessResponseV2("Resource marked as deleted")
    }

    if (deleteResourceV2.erase) {
      throw AssertionException(s"Request message has erase == true")
    }

    for {
      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        deleteResourceV2.apiRequestID,
        deleteResourceV2.resourceIri,
        () => makeTaskFuture
      )
    } yield taskResult
  }

  /**
    * Erases a resource from the triplestore.
    *
    * @param eraseResourceV2 the request message.
    */
  private def eraseResourceV2(eraseResourceV2: DeleteOrEraseResourceRequestV2): Future[SuccessResponseV2] = {
    def makeTaskFuture: Future[SuccessResponseV2] = {
      for {
        // Get the metadata of the resource to be updated.
        resourcesSeq: ReadResourcesSequenceV2 <- getResourcePreviewV2(
          resourceIris = Seq(eraseResourceV2.resourceIri),
          targetSchema = ApiV2Complex,
          featureFactoryConfig = eraseResourceV2.featureFactoryConfig,
          requestingUser = eraseResourceV2.requestingUser
        )

        resource: ReadResourceV2 = resourcesSeq.toResource(eraseResourceV2.resourceIri)

        // Ensure that the requesting user is a system admin, or an admin of this project.
        _ = if (!(eraseResourceV2.requestingUser.permissions.isProjectAdmin(resource.projectADM.id) ||
                  eraseResourceV2.requestingUser.permissions.isSystemAdmin)) {
          throw ForbiddenException(s"Only a system admin or project admin can erase a resource")
        }

        internalResourceClassIri = eraseResourceV2.resourceClassIri.toOntologySchema(InternalSchema)

        // Make sure that the resource's class is what the client thinks it is.
        _ = if (resource.resourceClassIri != internalResourceClassIri) {
          throw BadRequestException(
            s"Resource <${resource.resourceIri}> is not a member of class <${eraseResourceV2.resourceClassIri}>")
        }

        // Make sure that the resource hasn't been updated since the client got its last modification date.
        _ = if (resource.lastModificationDate != eraseResourceV2.maybeLastModificationDate) {
          throw EditConflictException(s"Resource <${resource.resourceIri}> has been modified since you last read it")
        }

        // Check that the resource is not referred to by any other resources. We ignore rdf:subject (so we
        // can erase the resource's own links) and rdf:object (in case there is a deleted link value that
        // refers to it). Any such deleted link values will be erased along with the resource. If there
        // is a non-deleted link to the resource, the direct link (rather than the link value) will case
        // isEntityUsed() to throw an exception.

        resourceSmartIri = eraseResourceV2.resourceIri.toSmartIri

        _ <- throwIfEntityIsUsed(
          entityIri = resourceSmartIri,
          errorFun = throw BadRequestException(
            s"Resource ${eraseResourceV2.resourceIri} cannot be erased, because it is referred to by another resource"),
          ignoreRdfSubjectAndObject = true
        )

        // Get the IRI of the named graph from which the resource will be erased.
        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(resource.projectADM)

        // Generate SPARQL for erasing the resource.
        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .eraseResource(
            triplestore = settings.triplestoreType,
            dataNamedGraph = dataNamedGraph,
            resourceIri = eraseResourceV2.resourceIri
          )
          .toString()

        // _ = println(sparqlUpdate)

        // Do the update.
        _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        // Verify that the resource was erased correctly.

        resourceStillExists: Boolean <- stringFormatter.checkIriExists(resourceSmartIri.toString, storeManager)

        _ = if (resourceStillExists) {
          throw UpdateNotPerformedException(
            s"Resource <${eraseResourceV2.resourceIri}> was not erased. Please report this as a possible bug.")
        }
      } yield SuccessResponseV2("Resource erased")
    }

    if (!eraseResourceV2.erase) {
      throw AssertionException(s"Request message has erase == false")
    }

    for {
      // Do the pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        eraseResourceV2.apiRequestID,
        eraseResourceV2.resourceIri,
        () => makeTaskFuture
      )
    } yield taskResult
  }

  /**
    * Generates a [[SparqlTemplateResourceToCreate]] describing SPARQL for creating a resource and its values.
    * This method does pre-update checks that have to be done for each new resource individually, even when
    * multiple resources are being created in a single request.
    *
    * @param internalCreateResource     the resource to be created.
    * @param linkTargetClasses          a map of resources that are link targets to the IRIs of those resources' classes.
    * @param entityInfo                 an [[EntityInfoGetResponseV2]] containing definitions of the class of the resource to
    *                                   be created, as well as the classes that all the link targets
    *                                   belong to.
    * @param clientResourceIDs          a map of IRIs of resources to be created to client IDs for the same resources, if any.
    * @param defaultResourcePermissions the default permissions to be given to the resource, if it does not have custom permissions.
    * @param defaultPropertyPermissions the default permissions to be given to the resource's values, if they do not
    *                                   have custom permissions. This is a map of property IRIs to permission strings.
    * @param creationDate               the versionDate to be attached to the resource and its values.
    * @param featureFactoryConfig       the feature factory configuration.
    * @param requestingUser             the user making the request.
    * @return a [[ResourceReadyToCreate]].
    */
  private def generateResourceReadyToCreate(resourceIri: IRI,
                                            resourceUUID: UUID,
                                            internalCreateResource: CreateResourceV2,
                                            linkTargetClasses: Map[IRI, SmartIri],
                                            entityInfo: EntityInfoGetResponseV2,
                                            clientResourceIDs: Map[IRI, String],
                                            defaultResourcePermissions: String,
                                            defaultPropertyPermissions: Map[SmartIri, String],
                                            creationDate: Instant,
                                            featureFactoryConfig: FeatureFactoryConfig,
                                            requestingUser: UserADM): Future[ResourceReadyToCreate] = {
    val resourceIDForErrorMsg: String =
      clientResourceIDs.get(resourceIri).map(resourceID => s"In resource '$resourceID': ").getOrElse("")

    for {
      // Check that the resource class has a suitable cardinality for each submitted value.
      resourceClassInfo <- Future(entityInfo.classInfoMap(internalCreateResource.resourceClassIri))

      knoraPropertyCardinalities: Map[SmartIri, Cardinality.KnoraCardinalityInfo] = resourceClassInfo.allCardinalities.view
        .filterKeys(resourceClassInfo.knoraResourceProperties)
        .toMap

      _ = internalCreateResource.values.foreach {
        case (propertyIri: SmartIri, valuesForProperty: Seq[CreateValueInNewResourceV2]) =>
          val internalPropertyIri = propertyIri.toOntologySchema(InternalSchema)

          val cardinalityInfo = knoraPropertyCardinalities.getOrElse(
            internalPropertyIri,
            throw OntologyConstraintException(
              s"${resourceIDForErrorMsg}Resource class <${internalCreateResource.resourceClassIri.toOntologySchema(
                ApiV2Complex)}> has no cardinality for property <$propertyIri>")
          )

          if ((cardinalityInfo.cardinality == Cardinality.MayHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveOne) && valuesForProperty.size > 1) {
            throw OntologyConstraintException(
              s"${resourceIDForErrorMsg}Resource class <${internalCreateResource.resourceClassIri.toOntologySchema(
                ApiV2Complex)}> does not allow more than one value for property <$propertyIri>")
          }
      }

      // Check that no required values are missing.

      requiredProps: Set[SmartIri] = knoraPropertyCardinalities.filter {
        case (_, cardinalityInfo) =>
          cardinalityInfo.cardinality == Cardinality.MustHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveSome
      }.keySet -- resourceClassInfo.linkProperties

      internalPropertyIris: Set[SmartIri] = internalCreateResource.values.keySet

      _ = if (!requiredProps.subsetOf(internalPropertyIris)) {
        val missingProps =
          (requiredProps -- internalPropertyIris).map(iri => s"<${iri.toOntologySchema(ApiV2Complex)}>").mkString(", ")
        throw OntologyConstraintException(
          s"${resourceIDForErrorMsg}Values were not submitted for the following property or properties, which are required by resource class <${internalCreateResource.resourceClassIri
            .toOntologySchema(ApiV2Complex)}>: $missingProps")
      }

      // Check that each submitted value is consistent with the knora-base:objectClassConstraint of the property that is supposed to
      // point to it.

      _ = checkObjectClassConstraints(
        values = internalCreateResource.values,
        linkTargetClasses = linkTargetClasses,
        entityInfo = entityInfo,
        clientResourceIDs = clientResourceIDs,
        resourceIDForErrorMsg = resourceIDForErrorMsg
      )

      // Check that the submitted values do not contain duplicates.

      _ = checkForDuplicateValues(
        values = internalCreateResource.values,
        clientResourceIDs = clientResourceIDs,
        resourceIDForErrorMsg = resourceIDForErrorMsg
      )

      // Validate and reformat any custom permissions in the request, and set all permissions to defaults if custom
      // permissions are not provided.

      resourcePermissions: String <- internalCreateResource.permissions match {
        case Some(permissionStr) =>
          for {
            validatedCustomPermissions: String <- PermissionUtilADM.validatePermissions(
              permissionLiteral = permissionStr,
              featureFactoryConfig = featureFactoryConfig,
              responderManager = responderManager
            )

            // Is the requesting user a system admin, or an admin of this project?
            _ = if (!(requestingUser.permissions.isProjectAdmin(internalCreateResource.projectADM.id) || requestingUser.permissions.isSystemAdmin)) {

              // No. Make sure they don't give themselves higher permissions than they would get from the default permissions.

              val permissionComparisonResult: PermissionComparisonResult = PermissionUtilADM.comparePermissionsADM(
                entityCreator = requestingUser.id,
                entityProject = internalCreateResource.projectADM.id,
                permissionLiteralA = validatedCustomPermissions,
                permissionLiteralB = defaultResourcePermissions,
                requestingUser = requestingUser
              )

              if (permissionComparisonResult == AGreaterThanB) {
                throw ForbiddenException(
                  s"${resourceIDForErrorMsg}The specified permissions would give the resource's creator a higher permission on the resource than the default permissions")
              }
            }
          } yield validatedCustomPermissions

        case None => FastFuture.successful(defaultResourcePermissions)
      }

      valuesWithValidatedPermissions: Map[SmartIri, Seq[GenerateSparqlForValueInNewResourceV2]] <- validateAndFormatValuePermissions(
        project = internalCreateResource.projectADM,
        values = internalCreateResource.values,
        defaultPropertyPermissions = defaultPropertyPermissions,
        resourceIDForErrorMsg = resourceIDForErrorMsg,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      // Ask the values responder for SPARQL for generating the values.
      sparqlForValuesResponse: GenerateSparqlToCreateMultipleValuesResponseV2 <- (responderManager ?
        GenerateSparqlToCreateMultipleValuesRequestV2(
          resourceIri = resourceIri,
          values = valuesWithValidatedPermissions,
          creationDate = creationDate,
          requestingUser = requestingUser
        )).mapTo[GenerateSparqlToCreateMultipleValuesResponseV2]

    } yield
      ResourceReadyToCreate(
        sparqlTemplateResourceToCreate = SparqlTemplateResourceToCreate(
          resourceIri = resourceIri,
          resourceUUID = resourceUUID,
          permissions = resourcePermissions,
          sparqlForValues = sparqlForValuesResponse.insertSparql,
          resourceClassIri = internalCreateResource.resourceClassIri.toString,
          resourceLabel = internalCreateResource.label,
          resourceCreationDate = creationDate
        ),
        values = sparqlForValuesResponse.unverifiedValues,
        hasStandoffLink = sparqlForValuesResponse.hasStandoffLink
      )
  }

  /**
    * Given a sequence of resources to be created, gets the class IRIs of all the resources that are the targets of
    * link values in the new resources, whether these already exist in the triplestore or are among the resources
    * to be created.
    *
    * @param internalCreateResources the resources to be created.
    * @param featureFactoryConfig    the feature factory configuration.
    * @param requestingUser          the user making the request.
    * @return a map of resource IRIs to class IRIs.
    */
  private def getLinkTargetClasses(resourceIri: IRI,
                                   internalCreateResources: Seq[CreateResourceV2],
                                   featureFactoryConfig: FeatureFactoryConfig,
                                   requestingUser: UserADM): Future[Map[IRI, SmartIri]] = {
    // Get the IRIs of the new and existing resources that are targets of links.
    val (existingTargetIris: Set[IRI], newTargets: Set[IRI]) =
      internalCreateResources.flatMap(_.flatValues).foldLeft((Set.empty[IRI], Set.empty[IRI])) {
        case ((accExisting: Set[IRI], accNew: Set[IRI]), valueToCreate: CreateValueInNewResourceV2) =>
          valueToCreate.valueContent match {
            case linkValueContentV2: LinkValueContentV2 =>
              if (linkValueContentV2.referredResourceExists) {
                (accExisting + linkValueContentV2.referredResourceIri, accNew)
              } else {
                (accExisting, accNew + linkValueContentV2.referredResourceIri)
              }

            case _ => (accExisting, accNew)
          }
      }

    // Make a map of the IRIs of new target resources to their class IRIs.
    val classesOfNewTargets: Map[IRI, SmartIri] = internalCreateResources
      .map { resourceToCreate =>
        resourceIri -> resourceToCreate.resourceClassIri
      }
      .toMap
      .view
      .filterKeys(newTargets)
      .toMap

    for {
      // Get information about the existing resources that are targets of links.
      existingTargets: ReadResourcesSequenceV2 <- getResourcePreviewV2(
        resourceIris = existingTargetIris.toSeq,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      // Make a map of the IRIs of existing target resources to their class IRIs.
      classesOfExistingTargets: Map[IRI, SmartIri] = existingTargets.resources
        .map(resource => resource.resourceIri -> resource.resourceClassIri)
        .toMap
    } yield classesOfNewTargets ++ classesOfExistingTargets
  }

  /**
    * Checks that values to be created in a new resource do not contain duplicates.
    *
    * @param values                a map of property IRIs to values to be created (in the internal schema).
    * @param clientResourceIDs     a map of IRIs of resources to be created to client IDs for the same resources, if any.
    * @param resourceIDForErrorMsg something that can be prepended to an error message to specify the client's ID for the
    *                              resource to be created, if any.
    */
  private def checkForDuplicateValues(values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
                                      clientResourceIDs: Map[IRI, String] = Map.empty[IRI, String],
                                      resourceIDForErrorMsg: IRI): Unit = {
    values.foreach {
      case (propertyIri: SmartIri, valuesToCreate: Seq[CreateValueInNewResourceV2]) =>
        // Given the values for a property, compute all possible combinations of two of those values.
        val valueCombinations: Iterator[Seq[CreateValueInNewResourceV2]] = valuesToCreate.combinations(2)

        for (valueCombination: Seq[CreateValueInNewResourceV2] <- valueCombinations) {
          // valueCombination must have two elements.
          val firstValue: ValueContentV2 = valueCombination.head.valueContent
          val secondValue: ValueContentV2 = valueCombination(1).valueContent

          if (firstValue.wouldDuplicateOtherValue(secondValue)) {
            throw DuplicateValueException(
              s"${resourceIDForErrorMsg}Duplicate values for property <${propertyIri.toOntologySchema(ApiV2Complex)}>")
          }
        }
    }
  }

  /**
    * Checks that values to be created in a new resource are compatible with the object class constraints
    * of the resource's properties.
    *
    * @param values                a map of property IRIs to values to be created (in the internal schema).
    * @param linkTargetClasses     a map of resources that are link targets to the IRIs of those resource's classes.
    * @param entityInfo            an [[EntityInfoGetResponseV2]] containing definitions of the classes that all the link targets
    *                              belong to.
    * @param clientResourceIDs     a map of IRIs of resources to be created to client IDs for the same resources, if any.
    * @param resourceIDForErrorMsg something that can be prepended to an error message to specify the client's ID for the
    *                              resource to be created, if any.
    */
  private def checkObjectClassConstraints(values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
                                          linkTargetClasses: Map[IRI, SmartIri],
                                          entityInfo: EntityInfoGetResponseV2,
                                          clientResourceIDs: Map[IRI, String] = Map.empty[IRI, String],
                                          resourceIDForErrorMsg: IRI): Unit = {
    values.foreach {
      case (propertyIri: SmartIri, valuesToCreate: Seq[CreateValueInNewResourceV2]) =>
        val propertyInfo: ReadPropertyInfoV2 = entityInfo.propertyInfoMap(propertyIri)

        // Don't accept link properties.
        if (propertyInfo.isLinkProp) {
          throw BadRequestException(
            s"${resourceIDForErrorMsg}Invalid property <${propertyIri.toOntologySchema(ApiV2Complex)}>. Use a link value property to submit a link.")
        }

        // Get the property's object class constraint. If this is a link value property, we want the object
        // class constraint of the corresponding link property instead.

        val propertyInfoForObjectClassConstraint = if (propertyInfo.isLinkValueProp) {
          entityInfo.propertyInfoMap(propertyIri.fromLinkValuePropToLinkProp)
        } else {
          propertyInfo
        }

        val propertyIriForObjectClassConstraint = propertyInfoForObjectClassConstraint.entityInfoContent.propertyIri

        val objectClassConstraint: SmartIri = propertyInfoForObjectClassConstraint.entityInfoContent.requireIriObject(
          OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
          throw InconsistentRepositoryDataException(
            s"Property <$propertyIriForObjectClassConstraint> has no knora-api:objectType")
        )

        // Check each value.
        for (valueToCreate: CreateValueInNewResourceV2 <- valuesToCreate) {
          valueToCreate.valueContent match {
            case linkValueContentV2: LinkValueContentV2 =>
              // It's a link value.

              if (!propertyInfo.isLinkValueProp) {
                throw OntologyConstraintException(s"${resourceIDForErrorMsg}Property <${propertyIri.toOntologySchema(
                  ApiV2Complex)}> requires a value of type <${objectClassConstraint.toOntologySchema(ApiV2Complex)}>")
              }

              // Does the resource that's the target of the link belongs to a subclass of the
              // link property's object class constraint?

              val linkTargetClass = linkTargetClasses(linkValueContentV2.referredResourceIri)
              val linkTargetClassInfo = entityInfo.classInfoMap(linkTargetClass)

              if (!linkTargetClassInfo.allBaseClasses.contains(objectClassConstraint)) {
                // No. If the target resource already exists, use its IRI in the error message.
                // Otherwise, use the client's ID for the resource.
                val resourceID = if (linkValueContentV2.referredResourceExists) {
                  s"<${linkValueContentV2.referredResourceIri}>"
                } else {
                  s"'${clientResourceIDs(linkValueContentV2.referredResourceIri)}'"
                }

                throw OntologyConstraintException(
                  s"${resourceIDForErrorMsg}Resource $resourceID cannot be the object of property <${propertyIriForObjectClassConstraint
                    .toOntologySchema(ApiV2Complex)}>, because it does not belong to class <${objectClassConstraint
                    .toOntologySchema(ApiV2Complex)}>")
              }

            case otherValueContentV2: ValueContentV2 =>
              // It's not a link value. Check that its type is equal to the property's object
              // class constraint.
              if (otherValueContentV2.valueType != objectClassConstraint) {
                throw OntologyConstraintException(s"${resourceIDForErrorMsg}Property <${propertyIri.toOntologySchema(
                  ApiV2Complex)}> requires a value of type <${objectClassConstraint.toOntologySchema(ApiV2Complex)}>")
              }
          }
        }
    }
  }

  /**
    * Given a sequence of values to be created in a new resource, checks the targets of standoff links in text
    * values. For each link, if the target is expected to exist, checks that it exists and that the user has
    * permission to see it.
    *
    * @param values               the values to be checked.
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the user making the request.
    */
  private def checkStandoffLinkTargets(values: Iterable[CreateValueInNewResourceV2],
                                       featureFactoryConfig: FeatureFactoryConfig,
                                       requestingUser: UserADM): Future[Unit] = {
    val standoffLinkTargetsThatShouldExist: Set[IRI] = values.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], valueToCreate: CreateValueInNewResourceV2) =>
        valueToCreate.valueContent match {
          case textValueContentV2: TextValueContentV2 =>
            acc ++ textValueContentV2.standoffLinkTagIriAttributes.filter(_.targetExists).map(_.value)
          case _ => acc
        }
    }

    getResourcePreviewV2(
      resourceIris = standoffLinkTargetsThatShouldExist.toSeq,
      targetSchema = ApiV2Complex,
      featureFactoryConfig = featureFactoryConfig,
      requestingUser = requestingUser
    ).map(_ => ())
  }

  /**
    * Given a sequence of values to be created in a new resource, checks the existence of the list nodes referred to
    * in list values.
    *
    * @param values         the values to be checked.
    * @param requestingUser the user making the request.
    */
  private def checkListNodes(values: Iterable[CreateValueInNewResourceV2], requestingUser: UserADM): Future[Unit] = {
    val listNodesThatShouldExist: Set[IRI] = values.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], valueToCreate: CreateValueInNewResourceV2) =>
        valueToCreate.valueContent match {
          case hierarchicalListValueContentV2: HierarchicalListValueContentV2 =>
            acc + hierarchicalListValueContentV2.valueHasListNode
          case _ => acc
        }
    }

    Future
      .sequence(
        listNodesThatShouldExist
          .map(listNodeIri => ResourceUtilV2.checkListNodeExists(listNodeIri, storeManager))
          .toSeq)
      .map(_ => ())
  }

  /**
    * Given a map of property IRIs to values to be created in a new resource, validates and reformats any custom
    * permissions in the values, and sets all value permissions to defaults if custom permissions are not provided.
    *
    * @param project                    the project in which the resource is to be created.
    * @param values                     the values whose permissions are to be validated.
    * @param defaultPropertyPermissions a map of property IRIs to default permissions.
    * @param resourceIDForErrorMsg      a string that can be prepended to an error message to specify the client's
    *                                   ID for the containing resource, if provided.
    * @param requestingUser             the user making the request.
    * @return a map of property IRIs to sequences of [[GenerateSparqlForValueInNewResourceV2]], in which
    *         all permissions have been validated and defined.
    */
  private def validateAndFormatValuePermissions(
      project: ProjectADM,
      values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
      defaultPropertyPermissions: Map[SmartIri, String],
      resourceIDForErrorMsg: String,
      featureFactoryConfig: FeatureFactoryConfig,
      requestingUser: UserADM): Future[Map[SmartIri, Seq[GenerateSparqlForValueInNewResourceV2]]] = {
    val propertyValuesWithValidatedPermissionsFutures
      : Map[SmartIri, Seq[Future[GenerateSparqlForValueInNewResourceV2]]] = values.map {
      case (propertyIri: SmartIri, valuesToCreate: Seq[CreateValueInNewResourceV2]) =>
        val validatedPermissionFutures: Seq[Future[GenerateSparqlForValueInNewResourceV2]] = valuesToCreate.map {
          valueToCreate =>
            // Does this value have custom permissions?
            valueToCreate.permissions match {
              case Some(permissionStr: String) =>
                // Yes. Validate and reformat them.
                for {
                  validatedCustomPermissions <- PermissionUtilADM.validatePermissions(
                    permissionLiteral = permissionStr,
                    featureFactoryConfig = featureFactoryConfig,
                    responderManager = responderManager
                  )

                  // Is the requesting user a system admin, or an admin of this project?
                  _ = if (!(requestingUser.permissions.isProjectAdmin(project.id) || requestingUser.permissions.isSystemAdmin)) {

                    // No. Make sure they don't give themselves higher permissions than they would get from the default permissions.

                    val permissionComparisonResult: PermissionComparisonResult =
                      PermissionUtilADM.comparePermissionsADM(
                        entityCreator = requestingUser.id,
                        entityProject = project.id,
                        permissionLiteralA = validatedCustomPermissions,
                        permissionLiteralB = defaultPropertyPermissions(propertyIri),
                        requestingUser = requestingUser
                      )

                    if (permissionComparisonResult == AGreaterThanB) {
                      throw ForbiddenException(
                        s"${resourceIDForErrorMsg}The specified value permissions would give a value's creator a higher permission on the value than the default permissions")
                    }
                  }
                } yield
                  GenerateSparqlForValueInNewResourceV2(
                    valueContent = valueToCreate.valueContent,
                    customValueIri = valueToCreate.customValueIri,
                    customValueUUID = valueToCreate.customValueUUID,
                    customValueCreationDate = valueToCreate.customValueCreationDate,
                    permissions = validatedCustomPermissions
                  )

              case None =>
                // No. Use the default permissions.
                FastFuture.successful {
                  GenerateSparqlForValueInNewResourceV2(
                    valueContent = valueToCreate.valueContent,
                    customValueIri = valueToCreate.customValueIri,
                    customValueUUID = valueToCreate.customValueUUID,
                    customValueCreationDate = valueToCreate.customValueCreationDate,
                    permissions = defaultPropertyPermissions(propertyIri)
                  )
                }
            }
        }

        propertyIri -> validatedPermissionFutures
    }

    ActorUtil.sequenceSeqFuturesInMap(propertyValuesWithValidatedPermissionsFutures)
  }

  /**
    * Gets the default permissions for resource classs in a project.
    *
    * @param projectIri        the IRI of the project.
    * @param resourceClassIris the internal IRIs of the resource classes.
    * @param requestingUser    the user making the request.
    * @return a map of resource class IRIs to default permission strings.
    */
  private def getResourceClassDefaultPermissions(projectIri: IRI,
                                                 resourceClassIris: Set[SmartIri],
                                                 requestingUser: UserADM): Future[Map[SmartIri, String]] = {
    val permissionsFutures: Map[SmartIri, Future[String]] = resourceClassIris.toSeq.map { resourceClassIri =>
      val requestMessage = DefaultObjectAccessPermissionsStringForResourceClassGetADM(
        projectIri = projectIri,
        resourceClassIri = resourceClassIri.toString,
        targetUser = requestingUser,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      resourceClassIri -> (responderManager ? requestMessage)
        .mapTo[DefaultObjectAccessPermissionsStringResponseADM]
        .map(_.permissionLiteral)
    }.toMap

    ActorUtil.sequenceFuturesInMap(permissionsFutures)
  }

  /**
    * Gets the default permissions for properties in a resource class in a project.
    *
    * @param projectIri              the IRI of the project.
    * @param resourceClassProperties a map of internal resource class IRIs to sets of internal property IRIs.
    * @param requestingUser          the user making the request.
    * @return a map of internal resource class IRIs to maps of property IRIs to default permission strings.
    */
  private def getDefaultPropertyPermissions(projectIri: IRI,
                                            resourceClassProperties: Map[SmartIri, Set[SmartIri]],
                                            requestingUser: UserADM): Future[Map[SmartIri, Map[SmartIri, String]]] = {
    val permissionsFutures: Map[SmartIri, Future[Map[SmartIri, String]]] = resourceClassProperties.map {
      case (resourceClassIri, propertyIris) =>
        val propertyPermissionsFutures: Map[SmartIri, Future[String]] = propertyIris.toSeq.map { propertyIri =>
          propertyIri -> ResourceUtilV2.getDefaultValuePermissions(
            projectIri = projectIri,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri,
            requestingUser = requestingUser,
            responderManager = responderManager
          )
        }.toMap

        resourceClassIri -> ActorUtil.sequenceFuturesInMap(propertyPermissionsFutures)
    }

    ActorUtil.sequenceFuturesInMap(permissionsFutures)
  }

  /**
    * Checks that a resource was created correctly.
    *
    * @param resourceReadyToCreate the resource that should have been created.
    * @param projectIri            the IRI of the project in which the resource should have been created.
    * @param featureFactoryConfig  the feature factory configuration.
    * @param requestingUser        the user that attempted to create the resource.
    * @return a preview of the resource that was created.
    */
  private def verifyResource(resourceReadyToCreate: ResourceReadyToCreate,
                             projectIri: IRI,
                             featureFactoryConfig: FeatureFactoryConfig,
                             requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {
    val resourceIri = resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceIri

    val resourceFuture: Future[ReadResourcesSequenceV2] = for {
      resourcesResponse: ReadResourcesSequenceV2 <- getResourcesV2(
        resourceIris = Seq(resourceIri),
        requestingUser = requestingUser,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = featureFactoryConfig,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues
      )

      resource: ReadResourceV2 = resourcesResponse.toResource(requestedResourceIri = resourceIri)

      _ = if (resource.resourceClassIri.toString != resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceClassIri) {
        throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong resource class")
      }

      _ = if (resource.resourceUUID != resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceUUID) {
        throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong UUID")
      }

      _ = if (resource.attachedToUser != requestingUser.id) {
        throw AssertionException(s"Resource <$resourceIri> was saved, but it is attached to the wrong user")
      }

      _ = if (resource.projectADM.id != projectIri) {
        throw AssertionException(s"Resource <$resourceIri> was saved, but it is attached to the wrong user")
      }

      _ = if (resource.permissions != resourceReadyToCreate.sparqlTemplateResourceToCreate.permissions) {
        throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong permissions")
      }

      // Undo any escapes in the submitted rdfs:label to compare it with the saved one.
      unescapedLabel: String = stringFormatter.fromSparqlEncodedString(
        resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceLabel)

      _ = if (resource.label != unescapedLabel) {
        throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong label")
      }

      savedPropertyIris: Set[SmartIri] = resource.values.keySet

      // Check that the property knora-base:hasStandoffLinkToValue was automatically added if necessary.
      expectedPropertyIris: Set[SmartIri] = resourceReadyToCreate.values.keySet ++ (if (resourceReadyToCreate.hasStandoffLink) {
                                                                                      Some(
                                                                                        OntologyConstants.KnoraBase.HasStandoffLinkToValue.toSmartIri)
                                                                                    } else {
                                                                                      None
                                                                                    })

      _ = if (savedPropertyIris != expectedPropertyIris) {
        throw AssertionException(
          s"Resource <$resourceIri> was saved, but it has the wrong properties: expected (${expectedPropertyIris
            .map(_.toSparql)
            .mkString(", ")}), but saved (${savedPropertyIris.map(_.toSparql).mkString(", ")})")
      }

      // Ignore knora-base:hasStandoffLinkToValue when checking the expected values.
      _ = (resource.values - OntologyConstants.KnoraBase.HasStandoffLinkToValue.toSmartIri).foreach {
        case (propertyIri: SmartIri, savedValues: Seq[ReadValueV2]) =>
          val expectedValues: Seq[UnverifiedValueV2] = resourceReadyToCreate.values(propertyIri)

          if (expectedValues.size != savedValues.size) {
            throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong values")
          }

          savedValues.zip(expectedValues).foreach {
            case (savedValue, expectedValue) =>
              if (!(expectedValue.valueContent.wouldDuplicateCurrentVersion(savedValue.valueContent) &&
                    savedValue.permissions == expectedValue.permissions &&
                    savedValue.attachedToUser == requestingUser.id)) {
                // println(s"========== Expected ==========\n${MessageUtil.toSource(expectedValue.valueContent)}\n========== Saved ==========\n${MessageUtil.toSource(savedValue.valueContent)}")
                throw AssertionException(
                  s"Resource <$resourceIri> was saved, but one or more of its values are not correct")
              }
          }
      }
    } yield
      ReadResourcesSequenceV2(
        resources = Seq(resource.copy(values = Map.empty))
      )

    resourceFuture.recover {
      case _: NotFoundException =>
        throw UpdateNotPerformedException(
          s"Resource <$resourceIri> was not created. Please report this as a possible bug.")
    }
  }

  /**
    * After the attempted creation of one or more resources, looks for file values among the values that were supposed
    * to be created, and tells Sipi to move those files to permanent storage if the update succeeded, or to delete the
    * temporary files if the update failed.
    *
    * @param updateFuture    the operation that was supposed to create the resources.
    * @param createResources the resources that were supposed to be created.
    * @param requestingUser  the user making the request.
    */
  private def doSipiPostUpdateForResources[T <: UpdateResultInProject](updateFuture: Future[T],
                                                                       createResources: Seq[CreateResourceV2],
                                                                       requestingUser: UserADM): Future[T] = {
    val allValues: Seq[ValueContentV2] = createResources.flatMap(_.flatValues).map(_.valueContent)

    val resultFutures: Seq[Future[T]] = allValues.map { valueContent =>
      ResourceUtilV2.doSipiPostUpdate(
        updateFuture = updateFuture,
        valueContent = valueContent,
        requestingUser = requestingUser,
        responderManager = responderManager,
        storeManager = storeManager,
        log = log
      )
    }

    Future.sequence(resultFutures).transformWith {
      case Success(_) => updateFuture
      case Failure(e) => Future.failed(e)
    }
  }

  /**
    * Gets the requested resources from the triplestore.
    *
    * @param resourceIris         the Iris of the requested resources.
    * @param preview              `true` if a preview of the resource is requested.
    * @param withDeleted          if defined, indicates if the deleted resources and values should be returned or not.
    * @param propertyIri          if defined, requests only the values of the specified explicit property.
    * @param valueUuid            if defined, requests only the value with the specified UUID.
    * @param versionDate          if defined, requests the state of the resources at the specified time in the past.
    *                             Cannot be used in conjunction with `preview`.
    * @param queryStandoff        `true` if standoff should be queried.
    * @param featureFactoryConfig the feature factory configuration.
    * @return a map of resource IRIs to RDF data.
    */
  private def getResourcesFromTriplestore(
      resourceIris: Seq[IRI],
      preview: Boolean,
      withDeleted: Boolean = false,
      propertyIri: Option[SmartIri] = None,
      valueUuid: Option[UUID] = None,
      versionDate: Option[Instant] = None,
      queryStandoff: Boolean,
      featureFactoryConfig: FeatureFactoryConfig,
      requestingUser: UserADM): Future[ConstructResponseUtilV2.MainResourcesAndValueRdfData] = {
    // eliminate duplicate Iris
    val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

    // If we're supposed to query standoff, get the indexes delimiting the first page of standoff. (Subsequent
    // pages, if any, will be queried separately.)
    val (maybeStandoffMinStartIndex: Option[Int], maybeStandoffMaxStartIndex: Option[Int]) =
      StandoffTagUtilV2.getStandoffMinAndMaxStartIndexesForTextValueQuery(
        queryStandoff = queryStandoff,
        settings = settings
      )

    for {
      resourceRequestSparql <- Future(
        org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .getResourcePropertiesAndValues(
            triplestore = settings.triplestoreType,
            resourceIris = resourceIrisDistinct,
            preview = preview,
            withDeleted = withDeleted,
            maybePropertyIri = propertyIri,
            maybeValueUuid = valueUuid,
            maybeVersionDate = versionDate,
            queryAllNonStandoff = true,
            maybeStandoffMinStartIndex = maybeStandoffMinStartIndex,
            maybeStandoffMaxStartIndex = maybeStandoffMaxStartIndex,
            stringFormatter = stringFormatter
          )
          .toString())

      resourceRequestResponse: SparqlExtendedConstructResponse <- (storeManager ? SparqlExtendedConstructRequest(
        sparql = resourceRequestSparql,
        featureFactoryConfig = featureFactoryConfig
      )).mapTo[SparqlExtendedConstructResponse]

      // separate resources and values
      mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData = ConstructResponseUtilV2
        .splitMainResourcesAndValueRdfData(
          constructQueryResults = resourceRequestResponse,
          requestingUser = requestingUser
        )
    } yield mainResourcesAndValueRdfData

  }

  /**
    * Get one or several resources and return them as a sequence.
    *
    * @param resourceIris         the IRIs of the resources to be queried.
    * @param propertyIri          if defined, requests only the values of the specified explicit property.
    * @param valueUuid            if defined, requests only the value with the specified UUID.
    * @param versionDate          if defined, requests the state of the resources at the specified time in the past.
    * @param withDeleted          if defined, indicates if the deleted resource and values should be returned or not.
    * @param targetSchema         the target API schema.
    * @param schemaOptions        the schema options submitted with the request.
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the user making the request.
    * @return a [[ReadResourcesSequenceV2]].
    */
  private def getResourcesV2(resourceIris: Seq[IRI],
                             propertyIri: Option[SmartIri] = None,
                             valueUuid: Option[UUID] = None,
                             versionDate: Option[Instant] = None,
                             withDeleted: Boolean = false,
                             targetSchema: ApiV2Schema,
                             schemaOptions: Set[SchemaOption],
                             featureFactoryConfig: FeatureFactoryConfig,
                             requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {
    // eliminate duplicate Iris
    val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

    // Find out whether to query standoff along with text values. This boolean value will be passed to
    // ConstructResponseUtilV2.makeTextValueContentV2.
    val queryStandoff: Boolean =
      SchemaOptions.queryStandoffWithTextValues(targetSchema = targetSchema, schemaOptions = schemaOptions)

    for {

      mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData <- getResourcesFromTriplestore(
        resourceIris = resourceIris,
        preview = false,
        withDeleted = withDeleted,
        propertyIri = propertyIri,
        valueUuid = valueUuid,
        versionDate = versionDate,
        queryStandoff = queryStandoff,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      // If we're querying standoff, get XML-to standoff mappings.
      mappingsAsMap: Map[IRI, MappingAndXSLTransformation] <- if (queryStandoff) {
        getMappingsFromQueryResultsSeparated(
          queryResultsSeparated = mainResourcesAndValueRdfData.resources,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )
      } else {
        FastFuture.successful(Map.empty[IRI, MappingAndXSLTransformation])
      }

      apiResponse: ReadResourcesSequenceV2 <- ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = resourceIrisDistinct,
        pageSizeBeforeFiltering = resourceIris.size, // doesn't matter because we're not doing paging
        mappings = mappingsAsMap,
        queryStandoff = queryStandoff,
        versionDate = versionDate,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = targetSchema,
        settings = settings,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      _ = apiResponse.checkResourceIris(
        targetResourceIris = resourceIris.toSet,
        resourcesSequence = apiResponse
      )

      _ = valueUuid match {
        case Some(definedValueUuid) =>
          if (!apiResponse.resources.exists(_.values.values.exists(_.exists(_.valueHasUUID == definedValueUuid)))) {
            throw NotFoundException(
              s"Value with UUID ${stringFormatter.base64EncodeUuid(definedValueUuid)} not found (maybe you do not have permission to see it, or it is marked as deleted)")
          }

        case None => ()
      }

    } yield apiResponse

  }

  /**
    * Get the preview of a resource.
    *
    * @param resourceIris         the resource to query for.
    * @param withDeleted          indicates if the deleted resource should be returned or not.
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the the user making the request.
    * @return a [[ReadResourcesSequenceV2]].
    */
  private def getResourcePreviewV2(resourceIris: Seq[IRI],
                                   withDeleted: Boolean = false,
                                   targetSchema: ApiV2Schema,
                                   featureFactoryConfig: FeatureFactoryConfig,
                                   requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

    // eliminate duplicate Iris
    val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

    for {
      mainResourcesAndValueRdfData: ConstructResponseUtilV2.MainResourcesAndValueRdfData <- getResourcesFromTriplestore(
        resourceIris = resourceIris,
        preview = true,
        withDeleted = withDeleted,
        queryStandoff = false, // This has no effect, because we are not querying values.
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      apiResponse: ReadResourcesSequenceV2 <- ConstructResponseUtilV2.createApiResponse(
        mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
        orderByResourceIri = resourceIrisDistinct,
        pageSizeBeforeFiltering = resourceIris.size, // doesn't matter because we're not doing paging
        mappings = Map.empty[IRI, MappingAndXSLTransformation],
        queryStandoff = false,
        versionDate = None,
        calculateMayHaveMoreResults = false,
        responderManager = responderManager,
        targetSchema = targetSchema,
        settings = settings,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      _ = apiResponse.checkResourceIris(
        targetResourceIris = resourceIris.toSet,
        resourcesSequence = apiResponse
      )
    } yield apiResponse
  }

  /**
    * Obtains a Gravsearch template from Sipi.
    *
    * @param gravsearchTemplateIri the Iri of the resource representing the Gravsearch template.
    * @param featureFactoryConfig  the feature factory configuration.
    * @param requestingUser        the user making the request.
    * @return the Gravsearch template.
    */
  private def getGravsearchTemplate(gravsearchTemplateIri: IRI,
                                    featureFactoryConfig: FeatureFactoryConfig,
                                    requestingUser: UserADM): Future[String] = {

    val gravsearchUrlFuture = for {
      resources: ReadResourcesSequenceV2 <- getResourcesV2(
        resourceIris = Vector(gravsearchTemplateIri),
        targetSchema = ApiV2Complex,
        schemaOptions = Set(MarkupAsStandoff),
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      resource: ReadResourceV2 = resources.toResource(gravsearchTemplateIri)

      _ = if (resource.resourceClassIri.toString != OntologyConstants.KnoraBase.TextRepresentation) {
        throw BadRequestException(s"Resource $gravsearchTemplateIri is not a Gravsearch template (text file expected)")
      }

      (fileValueIri: IRI, gravsearchFileValueContent: TextFileValueContentV2) = resource.values.get(
        OntologyConstants.KnoraBase.HasTextFileValue.toSmartIri) match {
        case Some(values: Seq[ReadValueV2]) if values.size == 1 =>
          values.head match {
            case value: ReadValueV2 =>
              value.valueContent match {
                case textRepr: TextFileValueContentV2 => (value.valueIri, textRepr)
                case _ =>
                  throw InconsistentRepositoryDataException(
                    s"Resource $gravsearchTemplateIri is supposed to have exactly one value of type ${OntologyConstants.KnoraBase.TextFileValue}")
              }
          }

        case None =>
          throw InconsistentRepositoryDataException(
            s"Resource $gravsearchTemplateIri has no property ${OntologyConstants.KnoraBase.HasTextFileValue}")
      }

      // check if gravsearchFileValueContent represents a text file
      _ = if (gravsearchFileValueContent.fileValue.internalMimeType != "text/plain") {
        throw BadRequestException(
          s"Expected $fileValueIri to be a text file referring to a Gravsearch template, but it has MIME type ${gravsearchFileValueContent.fileValue.internalMimeType}")
      }

      gravsearchUrl: String = s"${settings.internalSipiBaseUrl}/${resource.projectADM.shortcode}/${gravsearchFileValueContent.fileValue.internalFilename}/file"
    } yield gravsearchUrl

    val recoveredGravsearchUrlFuture = gravsearchUrlFuture.recover {
      case notFound: NotFoundException =>
        throw BadRequestException(s"Gravsearch template $gravsearchTemplateIri not found: ${notFound.message}")
    }

    for {
      gravsearchTemplateUrl <- recoveredGravsearchUrlFuture
      response: SipiGetTextFileResponse <- (storeManager ? SipiGetTextFileRequest(
        fileUrl = gravsearchTemplateUrl,
        requestingUser = KnoraSystemInstances.Users.SystemUser,
        senderName = this.getClass.getName
      )).mapTo[SipiGetTextFileResponse]
      gravsearchTemplate: String = response.content

    } yield gravsearchTemplate

  }

  /**
    * Returns a resource as TEI/XML.
    * This makes only sense for resources that have a text value containing standoff that is to be converted to the TEI body.
    *
    * @param resourceIri           the Iri of the resource to be converted to a TEI document (header and body).
    * @param textProperty          the Iri of the property (text value with standoff) to be converted to the body of the TEI document.
    * @param mappingIri            the Iri of the mapping to be used to convert standoff to XML, if a custom mapping is provided. The mapping is expected to contain an XSL transformation.
    * @param gravsearchTemplateIri the Iri of the Gravsearch template to query for the metadata for the TEI header. The resource Iri is expected to be represented by the placeholder '$resourceIri' in a BIND.
    * @param headerXSLTIri         the Iri of the XSL template to convert the metadata properties to the TEI header.
    * @param featureFactoryConfig  the feature factory configuration.
    * @param requestingUser        the user making the request.
    * @return a [[ResourceTEIGetResponseV2]].
    */
  private def getResourceAsTeiV2(resourceIri: IRI,
                                 textProperty: SmartIri,
                                 mappingIri: Option[IRI],
                                 gravsearchTemplateIri: Option[IRI],
                                 headerXSLTIri: Option[String],
                                 featureFactoryConfig: FeatureFactoryConfig,
                                 requestingUser: UserADM): Future[ResourceTEIGetResponseV2] = {

    /**
      * Extract the text value to be converted to TEI/XML.
      *
      * @param readResource the resource which is expected to hold the text value.
      * @return a [[TextValueContentV2]] representing the text value to be converted to TEI/XML.
      */
    def getTextValueFromReadResource(readResource: ReadResourceV2): TextValueContentV2 = {

      readResource.values.get(textProperty) match {
        case Some(valObjs: Seq[ReadValueV2]) if valObjs.size == 1 =>
          // make sure that the property has one instance and that it is of type TextValue and that is has standoff (markup)
          valObjs.head.valueContent match {
            case textValWithStandoff: TextValueContentV2 if textValWithStandoff.standoff.nonEmpty => textValWithStandoff

            case _ =>
              throw BadRequestException(
                s"$textProperty to be of type ${OntologyConstants.KnoraBase.TextValue} with standoff (markup)")
          }

        case None => throw BadRequestException(s"$textProperty is expected to occur once on $resourceIri")
      }
    }

    /**
      * Given a resource's values, convert the date values to Gregorian.
      *
      * @param values the values to be processed.
      * @return the resource's values with date values converted to Gregorian.
      */
    def convertDateToGregorian(values: Map[SmartIri, Seq[ReadValueV2]]): Map[SmartIri, Seq[ReadValueV2]] = {
      values.map {
        case (propIri: SmartIri, valueObjs: Seq[ReadValueV2]) =>
          propIri -> valueObjs.map {

            // convert all dates to Gregorian calendar dates (standardization)
            valueObj: ReadValueV2 =>
              valueObj match {
                case readNonLinkValueV2: ReadOtherValueV2 =>
                  readNonLinkValueV2.valueContent match {
                    case dateContent: DateValueContentV2 =>
                      // date value

                      readNonLinkValueV2.copy(
                        valueContent = dateContent.copy(
                          // act as if this was a Gregorian date
                          valueHasCalendar = CalendarNameGregorian
                        )
                      )

                    case _ => valueObj
                  }

                case readLinkValueV2: ReadLinkValueV2 if readLinkValueV2.valueContent.nestedResource.nonEmpty =>
                  // recursively process the values of the nested resource

                  val linkContent = readLinkValueV2.valueContent

                  readLinkValueV2.copy(
                    valueContent = linkContent.copy(
                      nestedResource = Some(
                        linkContent.nestedResource.get.copy(
                          // recursive call
                          values = convertDateToGregorian(linkContent.nestedResource.get.values)
                        )
                      )
                    )
                  )

                case _ => valueObj
              }
          }
      }
    }

    for {

      // get the requested resource
      resource: ReadResourceV2 <- if (gravsearchTemplateIri.nonEmpty) {

        // check that there is an XSLT to create the TEI header
        if (headerXSLTIri.isEmpty)
          throw BadRequestException(
            s"When a Gravsearch template Iri is provided, also a header XSLT Iri has to be provided.")

        for {
          // get the template
          template <- getGravsearchTemplate(
            gravsearchTemplateIri = gravsearchTemplateIri.get,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          // insert actual resource Iri, replacing the placeholder
          gravsearchQuery = template.replace("$resourceIri", resourceIri)

          // parse the Gravsearch query
          constructQuery: ConstructQuery = GravsearchParser.parseQuery(gravsearchQuery)

          // do a request to the SearchResponder
          gravSearchResponse: ReadResourcesSequenceV2 <- (responderManager ? GravsearchRequestV2(
            constructQuery = constructQuery,
            targetSchema = ApiV2Complex,
            schemaOptions = SchemaOptions.ForStandoffWithTextValues,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )).mapTo[ReadResourcesSequenceV2]
        } yield gravSearchResponse.toResource(resourceIri)

      } else {
        // no Gravsearch template is provided

        // check that there is no XSLT for the header since there is no Gravsearch template
        if (headerXSLTIri.nonEmpty)
          throw BadRequestException(
            s"When no Gravsearch template Iri is provided, no header XSLT Iri is expected to be provided either.")

        for {
          // get requested resource
          resource <- getResourcesV2(
            resourceIris = Vector(resourceIri),
            targetSchema = ApiV2Complex,
            schemaOptions = SchemaOptions.ForStandoffWithTextValues,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          ).map(_.toResource(resourceIri))
        } yield resource
      }

      // get the value object representing the text value that is to be mapped to the body of the TEI document
      bodyTextValue: TextValueContentV2 = getTextValueFromReadResource(resource)

      // the ext value is expected to have standoff markup
      _ = if (bodyTextValue.standoff.isEmpty)
        throw BadRequestException(s"Property $textProperty of $resourceIri is expected to have standoff markup")

      // get all the metadata but the text property for the TEI header
      headerResource = resource.copy(
        values = convertDateToGregorian(resource.values - textProperty)
      )

      // get the XSL transformation for the TEI header
      headerXSLT: Option[String] <- headerXSLTIri match {
        case Some(headerIri) =>
          val teiHeaderXsltRequest = GetXSLTransformationRequestV2(
            xsltTextRepresentationIri = headerIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          val xslTransformationFuture = for {
            xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? teiHeaderXsltRequest)
              .mapTo[GetXSLTransformationResponseV2]
          } yield Some(xslTransformation.xslt)

          xslTransformationFuture.recover {
            case notFound: NotFoundException =>
              throw SipiException(s"TEI header XSL transformation <$headerIri> not found: ${notFound.message}")

            case other => throw other
          }

        case None => Future(None)
      }

      // get the Iri of the mapping to convert standoff markup to TEI/XML
      mappingToBeApplied = mappingIri match {
        case Some(mapping: IRI) =>
          // a custom mapping is provided
          mapping

        case None =>
          // no mapping is provided, assume the standard case (standard standoff entites only)
          OntologyConstants.KnoraBase.TEIMapping
      }

      // get mapping to convert standoff markup to TEI/XML
      teiMapping: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(
        mappingIri = mappingToBeApplied,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )).mapTo[GetMappingResponseV2]

      // get XSLT from mapping for the TEI body
      bodyXslt: String <- teiMapping.mappingIri match {
        case OntologyConstants.KnoraBase.TEIMapping =>
          // standard standoff to TEI conversion

          // use standard XSLT (built-in)
          val teiXSLTFile: String = FileUtil.readTextResource("standoffToTEI.xsl")

          // return the file's content
          Future(teiXSLTFile)

        case otherMapping =>
          teiMapping.mapping.defaultXSLTransformation match {
            // custom standoff to TEI conversion

            case Some(xslTransformationIri) =>
              // get XSLT for the TEI body.
              val teiBodyXsltRequest = GetXSLTransformationRequestV2(
                xsltTextRepresentationIri = xslTransformationIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser
              )

              val xslTransformationFuture = for {
                xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? teiBodyXsltRequest)
                  .mapTo[GetXSLTransformationResponseV2]
              } yield xslTransformation.xslt

              xslTransformationFuture.recover {
                case notFound: NotFoundException =>
                  throw SipiException(
                    s"Default XSL transformation <${teiMapping.mapping.defaultXSLTransformation.get}> not found for mapping <${teiMapping.mappingIri}>: ${notFound.message}")

                case other => throw other
              }
            case None => throw BadRequestException(s"Default XSL Transformation expected for mapping $otherMapping")
          }
      }

      tei = ResourceTEIGetResponseV2(
        header = TEIHeader(
          headerInfo = headerResource,
          headerXSLT = headerXSLT,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings
        ),
        body = TEIBody(
          bodyInfo = bodyTextValue,
          bodyXSLT = bodyXslt,
          teiMapping = teiMapping.mapping
        )
      )

    } yield tei
  }

  /**
    * Gets a graph of resources that are reachable via links to or from a given resource.
    *
    * @param graphDataGetRequest a [[GraphDataGetRequestV2]] specifying the characteristics of the graph.
    * @return a [[GraphDataGetResponseV2]] representing the requested graph.
    */
  private def getGraphDataResponseV2(graphDataGetRequest: GraphDataGetRequestV2): Future[GraphDataGetResponseV2] = {
    val excludePropertyInternal = graphDataGetRequest.excludeProperty.map(_.toOntologySchema(InternalSchema))

    /**
      * The internal representation of a node returned by a SPARQL query generated by the `getGraphData` template.
      *
      * @param nodeIri         the IRI of the node.
      * @param nodeClass       the IRI of the node's class.
      * @param nodeLabel       the node's label.
      * @param nodeCreator     the node's creator.
      * @param nodeProject     the node's project.
      * @param nodePermissions the node's permissions.
      */
    case class QueryResultNode(nodeIri: IRI,
                               nodeClass: SmartIri,
                               nodeLabel: String,
                               nodeCreator: IRI,
                               nodeProject: IRI,
                               nodePermissions: String)

    /**
      * The internal representation of an edge returned by a SPARQL query generated by the `getGraphData` template.
      *
      * @param linkValueIri         the IRI of the link value.
      * @param sourceNodeIri        the IRI of the source node.
      * @param targetNodeIri        the IRI of the target node.
      * @param linkProp             the IRI of the link property.
      * @param linkValueCreator     the link value's creator.
      * @param sourceNodeProject    the project of the source node.
      * @param linkValuePermissions the link value's permissions.
      */
    case class QueryResultEdge(linkValueIri: IRI,
                               sourceNodeIri: IRI,
                               targetNodeIri: IRI,
                               linkProp: SmartIri,
                               linkValueCreator: IRI,
                               sourceNodeProject: IRI,
                               linkValuePermissions: String)

    /**
      * Represents results returned by a SPARQL query generated by the `getGraphData` template.
      *
      * @param nodes the nodes that were returned by the query.
      * @param edges the edges that were returned by the query.
      */
    case class GraphQueryResults(nodes: Set[QueryResultNode] = Set.empty[QueryResultNode],
                                 edges: Set[QueryResultEdge] = Set.empty[QueryResultEdge])

    /**
      * Recursively queries outbound or inbound links from/to a resource.
      *
      * @param startNode      the node to use as the starting point of the query. The user is assumed to have permission
      *                       to see this node.
      * @param outbound       `true` to get outbound links, `false` to get inbound links.
      * @param depth          the maximum depth of the query.
      * @param traversedEdges edges that have already been traversed.
      * @return a [[GraphQueryResults]].
      */
    def traverseGraph(startNode: QueryResultNode,
                      outbound: Boolean,
                      depth: Int,
                      traversedEdges: Set[QueryResultEdge] = Set.empty[QueryResultEdge]): Future[GraphQueryResults] = {
      if (depth < 1) Future.failed(AssertionException("Depth must be at least 1"))

      for {
        // Get the direct links from/to the start node.
        sparql <- Future(
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .getGraphData(
              triplestore = settings.triplestoreType,
              startNodeIri = startNode.nodeIri,
              startNodeOnly = false,
              maybeExcludeLinkProperty = excludePropertyInternal,
              outbound = outbound, // true to query outbound edges, false to query inbound edges
              limit = settings.maxGraphBreadth
            )
            .toString())

        // _ = println(sparql)

        response: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(sparql)).mapTo[SparqlSelectResult]
        rows: Seq[VariableResultsRow] = response.results.bindings

        // Did we get any results?
        recursiveResults: GraphQueryResults <- if (rows.isEmpty) {
          // No. Return nothing.
          Future(GraphQueryResults())
        } else {
          // Yes. Get the nodes from the query results.
          val otherNodes: Seq[QueryResultNode] = rows
            .map { row: VariableResultsRow =>
              val rowMap: Map[String, String] = row.rowMap

              QueryResultNode(
                nodeIri = rowMap("node"),
                nodeClass = rowMap("nodeClass").toSmartIri,
                nodeLabel = rowMap("nodeLabel"),
                nodeCreator = rowMap("nodeCreator"),
                nodeProject = rowMap("nodeProject"),
                nodePermissions = rowMap("nodePermissions")
              )
            }
            .filter { node: QueryResultNode =>
              // Filter out the nodes that the user doesn't have permission to see.
              PermissionUtilADM
                .getUserPermissionADM(
                  entityCreator = node.nodeCreator,
                  entityProject = node.nodeProject,
                  entityPermissionLiteral = node.nodePermissions,
                  requestingUser = graphDataGetRequest.requestingUser
                )
                .nonEmpty
            }

          // Collect the IRIs of the nodes that the user has permission to see, including the start node.
          val visibleNodeIris: Set[IRI] = otherNodes.map(_.nodeIri).toSet + startNode.nodeIri

          // Get the edges from the query results.
          val edges: Set[QueryResultEdge] = rows
            .map { row: VariableResultsRow =>
              val rowMap: Map[String, String] = row.rowMap
              val nodeIri: IRI = rowMap("node")

              // The SPARQL query takes a start node and returns the other node in the edge.
              //
              // If we're querying outbound edges, the start node is the source node, and the other
              // node is the target node.
              //
              // If we're querying inbound edges, the start node is the target node, and the other
              // node is the source node.

              QueryResultEdge(
                linkValueIri = rowMap("linkValue"),
                sourceNodeIri = if (outbound) startNode.nodeIri else nodeIri,
                targetNodeIri = if (outbound) nodeIri else startNode.nodeIri,
                linkProp = rowMap("linkProp").toSmartIri,
                linkValueCreator = rowMap("linkValueCreator"),
                sourceNodeProject = if (outbound) startNode.nodeProject else rowMap("nodeProject"),
                linkValuePermissions = rowMap("linkValuePermissions")
              )
            }
            .filter { edge: QueryResultEdge =>
              // Filter out the edges that the user doesn't have permission to see. To see an edge,
              // the user must have some permission on the link value and on the source and target
              // nodes.
              val hasPermission: Boolean = visibleNodeIris.contains(edge.sourceNodeIri) && visibleNodeIris.contains(
                edge.targetNodeIri) &&
                PermissionUtilADM
                  .getUserPermissionADM(
                    entityCreator = edge.linkValueCreator,
                    entityProject = edge.sourceNodeProject,
                    entityPermissionLiteral = edge.linkValuePermissions,
                    requestingUser = graphDataGetRequest.requestingUser
                  )
                  .nonEmpty

              // Filter out edges we've already traversed.
              val isRedundant: Boolean = traversedEdges.contains(edge)
              // if (isRedundant) println(s"filtering out edge from ${edge.sourceNodeIri} to ${edge.targetNodeIri}")

              hasPermission && !isRedundant
            }
            .toSet

          // Include only nodes that are reachable via edges that we're going to traverse (i.e. the user
          // has permission to see those edges, and we haven't already traversed them).
          val visibleNodeIrisFromEdges: Set[IRI] = edges.map(_.sourceNodeIri) ++ edges.map(_.targetNodeIri)
          val filteredOtherNodes: Seq[QueryResultNode] =
            otherNodes.filter(node => visibleNodeIrisFromEdges.contains(node.nodeIri))

          // Make a GraphQueryResults containing the resulting nodes and edges, including the start
          // node.
          val results = GraphQueryResults(nodes = filteredOtherNodes.toSet + startNode, edges = edges)

          // Have we reached the maximum depth?
          if (depth == 1) {
            // Yes. Just return the results we have.
            Future(results)
          } else {
            // No. Recursively get results for each of the nodes we found.

            val traversedEdgesForRecursion: Set[QueryResultEdge] = traversedEdges ++ edges

            val lowerResultFutures: Seq[Future[GraphQueryResults]] = filteredOtherNodes.map { node =>
              traverseGraph(
                startNode = node,
                outbound = outbound,
                depth = depth - 1,
                traversedEdges = traversedEdgesForRecursion
              )
            }

            val lowerResultsFuture: Future[Seq[GraphQueryResults]] = Future.sequence(lowerResultFutures)

            // Return those results plus the ones we found.

            lowerResultsFuture.map { lowerResultsSeq: Seq[GraphQueryResults] =>
              lowerResultsSeq.foldLeft(results) {
                case (acc: GraphQueryResults, lowerResults: GraphQueryResults) =>
                  GraphQueryResults(
                    nodes = acc.nodes ++ lowerResults.nodes,
                    edges = acc.edges ++ lowerResults.edges
                  )
              }
            }
          }
        }
      } yield recursiveResults
    }

    for {
      // Get the start node.
      sparql <- Future(
        org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .getGraphData(
            triplestore = settings.triplestoreType,
            startNodeIri = graphDataGetRequest.resourceIri,
            maybeExcludeLinkProperty = excludePropertyInternal,
            startNodeOnly = true,
            outbound = true,
            limit = settings.maxGraphBreadth
          )
          .toString())

      // _ = println(sparql)

      response: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(sparql)).mapTo[SparqlSelectResult]
      rows: Seq[VariableResultsRow] = response.results.bindings

      _ = if (rows.isEmpty) {
        throw NotFoundException(s"Resource <${graphDataGetRequest.resourceIri}> not found (it may have been deleted)")
      }

      firstRowMap: Map[String, String] = rows.head.rowMap

      startNode: QueryResultNode = QueryResultNode(
        nodeIri = firstRowMap("node"),
        nodeClass = firstRowMap("nodeClass").toSmartIri,
        nodeLabel = firstRowMap("nodeLabel"),
        nodeCreator = firstRowMap("nodeCreator"),
        nodeProject = firstRowMap("nodeProject"),
        nodePermissions = firstRowMap("nodePermissions")
      )

      // Make sure the user has permission to see the start node.
      _ = if (PermissionUtilADM
                .getUserPermissionADM(
                  entityCreator = startNode.nodeCreator,
                  entityProject = startNode.nodeProject,
                  entityPermissionLiteral = startNode.nodePermissions,
                  requestingUser = graphDataGetRequest.requestingUser
                )
                .isEmpty) {
        throw ForbiddenException(
          s"User ${graphDataGetRequest.requestingUser.email} does not have permission to view resource <${graphDataGetRequest.resourceIri}>")
      }

      // Recursively get the graph containing outbound links.
      outboundQueryResults: GraphQueryResults <- if (graphDataGetRequest.outbound) {
        traverseGraph(
          startNode = startNode,
          outbound = true,
          depth = graphDataGetRequest.depth
        )
      } else {
        FastFuture.successful(GraphQueryResults())
      }

      // Recursively get the graph containing inbound links.
      inboundQueryResults: GraphQueryResults <- if (graphDataGetRequest.inbound) {
        traverseGraph(
          startNode = startNode,
          outbound = false,
          depth = graphDataGetRequest.depth
        )
      } else {
        FastFuture.successful(GraphQueryResults())
      }

      // Combine the outbound and inbound graphs into a single graph.
      nodes: Set[QueryResultNode] = outboundQueryResults.nodes ++ inboundQueryResults.nodes + startNode
      edges: Set[QueryResultEdge] = outboundQueryResults.edges ++ inboundQueryResults.edges

      // Convert each node to a GraphNodeV2 for the API response message.
      resultNodes: Vector[GraphNodeV2] = nodes.map { node: QueryResultNode =>
        GraphNodeV2(
          resourceIri = node.nodeIri,
          resourceClassIri = node.nodeClass,
          resourceLabel = node.nodeLabel,
        )
      }.toVector

      // Convert each edge to a GraphEdgeV2 for the API response message.
      resultEdges: Vector[GraphEdgeV2] = edges.map { edge: QueryResultEdge =>
        GraphEdgeV2(
          source = edge.sourceNodeIri,
          propertyIri = edge.linkProp,
          target = edge.targetNodeIri,
        )
      }.toVector

    } yield
      GraphDataGetResponseV2(
        nodes = resultNodes,
        edges = resultEdges,
        ontologySchema = InternalSchema
      )
  }

  /**
    * Returns the version history of a resource.
    *
    * @param resourceHistoryRequest the version history request.
    * @return the resource's version history.
    */
  def getResourceHistoryV2(
      resourceHistoryRequest: ResourceVersionHistoryGetRequestV2): Future[ResourceVersionHistoryResponseV2] = {
    for {
      // Get the resource preview, to make sure the user has permission to see the resource, and to get
      // its creation date.
      resourcePreviewResponse: ReadResourcesSequenceV2 <- getResourcePreviewV2(
        resourceIris = Seq(resourceHistoryRequest.resourceIri),
        withDeleted = resourceHistoryRequest.withDeletedResource,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = resourceHistoryRequest.featureFactoryConfig,
        requestingUser = resourceHistoryRequest.requestingUser
      )

      resourcePreview: ReadResourceV2 = resourcePreviewResponse.toResource(resourceHistoryRequest.resourceIri)

      // Get the version history of the resource's values.

      historyRequestSparql = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .getResourceValueVersionHistory(
          triplestore = settings.triplestoreType,
          withDeletedResource = resourceHistoryRequest.withDeletedResource,
          resourceIri = resourceHistoryRequest.resourceIri,
          maybeStartDate = resourceHistoryRequest.startDate,
          maybeEndDate = resourceHistoryRequest.endDate
        )
        .toString()

      valueHistoryResponse: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(historyRequestSparql))
        .mapTo[SparqlSelectResult]

      valueHistoryEntries: Seq[ResourceHistoryEntry] = valueHistoryResponse.results.bindings.map {
        row: VariableResultsRow =>
          val versionDateStr: String = row.rowMap("versionDate")
          val author: IRI = row.rowMap("author")

          ResourceHistoryEntry(
            versionDate = stringFormatter.xsdDateTimeStampToInstant(
              versionDateStr,
              throw InconsistentRepositoryDataException(s"Could not parse version date: $versionDateStr")),
            author = author
          )
      }

      // Figure out whether to add the resource's creation to the history.

      // Is there a requested start date that's after the resource's creation date?
      historyEntriesWithResourceCreation: Seq[ResourceHistoryEntry] = if (resourceHistoryRequest.startDate.exists(
                                                                            _.isAfter(resourcePreview.creationDate))) {
        // Yes. No need to add the resource's creation.
        valueHistoryEntries
      } else {
        // No. Does the value history contain the resource creation date?
        if (valueHistoryEntries.nonEmpty && valueHistoryEntries.last.versionDate == resourcePreview.creationDate) {
          // Yes. No need to add the resource's creation.
          valueHistoryEntries
        } else {
          // No. Add a history entry for it.
          valueHistoryEntries :+ ResourceHistoryEntry(
            versionDate = resourcePreview.creationDate,
            author = resourcePreview.attachedToUser
          )
        }
      }
    } yield
      ResourceVersionHistoryResponseV2(
        historyEntriesWithResourceCreation
      )
  }

  def getIIIFManifestV2(request: ResourceIIIFManifestGetRequestV2): Future[ResourceIIIFManifestGetResponseV2] = {
    // The implementation here is experimental. If we had a way of streaming the canvas URLs to the IIIF viewer,
    // it would be better to write the Gravsearch query differently, so that ?representation was the main resource.
    // Then the Gravsearch query could return pages of results.
    //
    // The manifest generated here also needs to be tested with a IIIF viewer. It's not clear what some of the IRIs
    // in the manifest should be.

    for {
      // Make a Gravsearch query from a template.
      gravsearchQueryForIncomingLinks: String <- Future(
        org.knora.webapi.messages.twirl.queries.gravsearch.txt
          .getIncomingImageLinks(
            resourceIri = request.resourceIri
          )
          .toString())

      // Run the query.

      parsedGravsearchQuery <- FastFuture.successful(GravsearchParser.parseQuery(gravsearchQueryForIncomingLinks))
      searchResponse <- (responderManager ? GravsearchRequestV2(
        constructQuery = parsedGravsearchQuery,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffSeparateFromTextValues,
        featureFactoryConfig = request.featureFactoryConfig,
        requestingUser = request.requestingUser
      )).mapTo[ReadResourcesSequenceV2]

      resource: ReadResourceV2 = searchResponse.toResource(request.resourceIri)

      incomingLinks: Seq[ReadValueV2] = resource.values
        .getOrElse(OntologyConstants.KnoraBase.HasIncomingLinkValue.toSmartIri, Seq.empty)

      representations: Seq[ReadResourceV2] = incomingLinks.collect {
        case readLinkValueV2: ReadLinkValueV2 => readLinkValueV2.valueContent.nestedResource
      }.flatten
    } yield
      ResourceIIIFManifestGetResponseV2(
        JsonLDDocument(
          body = JsonLDObject(
            Map(
              JsonLDKeywords.CONTEXT -> JsonLDString("http://iiif.io/api/presentation/3/context.json"),
              "id" -> JsonLDString(s"${request.resourceIri}/manifest"), // Is this IRI OK?
              "type" -> JsonLDString("Manifest"),
              "label" -> JsonLDObject(
                Map(
                  "en" -> JsonLDArray(
                    Seq(
                      JsonLDString(resource.label)
                    )
                  )
                )
              ),
              "behavior" -> JsonLDArray(
                Seq(
                  JsonLDString("paged")
                )
              ),
              "items" -> JsonLDArray(
                representations.map { representation: ReadResourceV2 =>
                  val imageValue: ReadValueV2 = representation.values
                    .getOrElse(
                      OntologyConstants.KnoraBase.HasStillImageFileValue.toSmartIri,
                      throw InconsistentRepositoryDataException(
                        s"Representation ${representation.resourceIri} has no still image file value")
                    )
                    .head

                  val imageValueContent: StillImageFileValueContentV2 = imageValue.valueContent match {
                    case stillImageFileValueContentV2: StillImageFileValueContentV2 => stillImageFileValueContentV2
                    case _                                                          => throw AssertionException("Expected a StillImageFileValueContentV2")
                  }

                  val fileUrl: String =
                    imageValueContent.makeFileUrl(projectADM = representation.projectADM, settings = settings)

                  JsonLDObject(
                    Map(
                      "id" -> JsonLDString(s"${representation.resourceIri}/canvas"), // Is this IRI OK?
                      "type" -> JsonLDString("Canvas"),
                      "label" -> JsonLDObject(
                        Map(
                          "en" -> JsonLDArray(
                            Seq(
                              JsonLDString(representation.label)
                            )
                          )
                        )
                      ),
                      "height" -> JsonLDInt(imageValueContent.dimY),
                      "width" -> JsonLDInt(imageValueContent.dimX),
                      "items" -> JsonLDArray(
                        Seq(
                          JsonLDObject(
                            Map(
                              "id" -> JsonLDString(s"${imageValue.valueIri}/image"), // Is this IRI OK?
                              "type" -> JsonLDString("AnnotationPage"),
                              "items" -> JsonLDArray(
                                Seq(
                                  JsonLDObject(
                                    Map(
                                      "id" -> JsonLDString(imageValue.valueIri),
                                      "type" -> JsonLDString("Annotation"),
                                      "motivation" -> JsonLDString("painting"),
                                      "body" -> JsonLDObject(
                                        Map(
                                          "id" -> JsonLDString(fileUrl),
                                          "type" -> JsonLDString("Image"),
                                          "format" -> JsonLDString("image/jpeg"),
                                          "height" -> JsonLDInt(imageValueContent.dimY),
                                          "width" -> JsonLDInt(imageValueContent.dimX),
                                          "service" -> JsonLDArray(
                                            Seq(
                                              JsonLDObject(
                                                Map(
                                                  "id" -> JsonLDString(settings.externalSipiIIIFGetUrl),
                                                  "type" -> JsonLDString("ImageService3"),
                                                  "profile" -> JsonLDString("level1")
                                                )
                                              )
                                            )
                                          )
                                        )
                                      )
                                    )
                                  )
                                )
                              )
                            )
                          )
                        )
                      )
                    )
                  )
                }
              )
            )
          ),
          keepStructure = true
        )
      )
  }

  /**
    * Returns all events describing the history of a resource ordered by version date.
    *
    * @param resourceHistoryEventsGetRequest the request for events describing history of a resource.
    * @return the events extracted from full representation of a resource at each time point in its history ordered by version date.
    */
  def getResourceHistoryEvents(resourceHistoryEventsGetRequest: ResourceHistoryEventsGetRequestV2)
    : Future[ResourceAndValueVersionHistoryResponseV2] =
    for {
      resourceHistory: ResourceVersionHistoryResponseV2 <- getResourceHistoryV2(
        ResourceVersionHistoryGetRequestV2(
          resourceIri = resourceHistoryEventsGetRequest.resourceIri,
          withDeletedResource = true,
          featureFactoryConfig = resourceHistoryEventsGetRequest.featureFactoryConfig,
          requestingUser = resourceHistoryEventsGetRequest.requestingUser
        ))
      resourceFullHist: Seq[ResourceAndValueHistoryEvent] <- extractEventsFromHistory(
        resourceIri = resourceHistoryEventsGetRequest.resourceIri,
        resourceHistory = resourceHistory.history,
        featureFactoryConfig = resourceHistoryEventsGetRequest.featureFactoryConfig,
        requestingUser = resourceHistoryEventsGetRequest.requestingUser
      )
      sortedResourceHistory = resourceFullHist.sortBy(_.versionDate)
    } yield ResourceAndValueVersionHistoryResponseV2(historyEvents = sortedResourceHistory)

  /**
    * Returns events representing the history of all resources and values belonging to a project ordered by date.
    *
    * @param projectResourceHistoryEventsGetRequest the request for history events of a project.
    * @return the all history events of resources of a project ordered by version date.
    */
  def getProjectResourceHistoryEvents(projectResourceHistoryEventsGetRequest: ProjectResourcesWithHistoryGetRequestV2)
    : Future[ResourceAndValueVersionHistoryResponseV2] =
    for {
      // Get the project; checks if a project with given IRI exists.
      projectInfoResponse: ProjectGetResponseADM <- (responderManager ? ProjectGetRequestADM(
        identifier = ProjectIdentifierADM(maybeIri = Some(projectResourceHistoryEventsGetRequest.projectIri)),
        featureFactoryConfig = projectResourceHistoryEventsGetRequest.featureFactoryConfig,
        requestingUser = projectResourceHistoryEventsGetRequest.requestingUser
      )).mapTo[ProjectGetResponseADM]

      // Do a SELECT prequery to get the IRIs of the resources that belong to the project.
      prequery = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .getAllResourcesInProjectPrequery(
          triplestore = settings.triplestoreType,
          projectIri = projectInfoResponse.project.id
        )
        .toString

      sparqlSelectResponse <- (storeManager ? SparqlSelectRequest(prequery)).mapTo[SparqlSelectResult]
      mainResourceIris: Seq[IRI] = sparqlSelectResponse.results.bindings.map(_.rowMap("resource"))
      // For each resource IRI return history events
      historyOfResourcesAsSeqOfFutures: Seq[Future[Seq[ResourceAndValueHistoryEvent]]] = mainResourceIris.map {
        resourceIri =>
          for {
            resourceHistory: ResourceVersionHistoryResponseV2 <- getResourceHistoryV2(
              ResourceVersionHistoryGetRequestV2(
                resourceIri = resourceIri,
                withDeletedResource = true,
                featureFactoryConfig = projectResourceHistoryEventsGetRequest.featureFactoryConfig,
                requestingUser = projectResourceHistoryEventsGetRequest.requestingUser
              ))
            resourceFullHist: Seq[ResourceAndValueHistoryEvent] <- extractEventsFromHistory(
              resourceIri = resourceIri,
              resourceHistory = resourceHistory.history,
              featureFactoryConfig = projectResourceHistoryEventsGetRequest.featureFactoryConfig,
              requestingUser = projectResourceHistoryEventsGetRequest.requestingUser
            )
          } yield resourceFullHist
      }

      projectHistory: Seq[Seq[ResourceAndValueHistoryEvent]] <- Future.sequence(historyOfResourcesAsSeqOfFutures)
      sortedProjectHistory: Seq[ResourceAndValueHistoryEvent] = projectHistory.flatten.sortBy(_.versionDate)

    } yield ResourceAndValueVersionHistoryResponseV2(historyEvents = sortedProjectHistory)

  /**
    * Extract events from full representations of resource in each point of its history.
    *
    * @param resourceIri     the IRI of the resource.
    * @param resourceHistory the full representations of the resource in each point in its history.
    * @param featureFactoryConfig       the feature factory configuration.
    * @param requestingUser             the user making the request.
    * @return the full history of resource as sequence of [[ResourceAndValueHistoryEvent]].
    */
  def extractEventsFromHistory(resourceIri: IRI,
                               resourceHistory: Seq[ResourceHistoryEntry],
                               featureFactoryConfig: FeatureFactoryConfig,
                               requestingUser: UserADM): Future[Seq[ResourceAndValueHistoryEvent]] =
    for {
      resourceHist: Seq[ResourceHistoryEntry] <- Future.successful(resourceHistory.reverse)
      // Collect the full representations of the resource for each version date
      histories: Seq[Future[(ResourceHistoryEntry, ReadResourceV2)]] = resourceHist.map { hist =>
        getResourceAtGivenTime(
          resourceIri = resourceIri,
          versionHist = hist,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )
      }

      fullReps: Seq[(ResourceHistoryEntry, ReadResourceV2)] <- Future.sequence(histories)

      // Create an event for the resource at creation time
      (creationTimeHist, resourceAtCreation) = fullReps.head
      resourceCreationEvent: Seq[ResourceAndValueHistoryEvent] = getResourceCreationEvent(resourceAtCreation,
                                                                                          creationTimeHist)

      // If there is a version history for deletion of the event, create a delete resource event for it.
      (deletionRep, resourceAtValueChanges) = fullReps.tail.partition {
        case (resHist, resource) =>
          resource
            .asInstanceOf[ReadResourceV2]
            .deletionInfo
            .exists(deletionInfo => deletionInfo.deleteDate == resHist.versionDate)
      }
      resourceDeleteEvent = getResourceDeletionEvents(deletionRep)

      // For each value version, form an event
      valuesEvents: Seq[ResourceAndValueHistoryEvent] = resourceAtValueChanges.flatMap {
        case (versionHist, readResource) => getValueEvents(readResource, versionHist, fullReps)
      }

      // Get the update resource metadata event, if there is any.
      resourceMetadataUpdateEvent: Seq[ResourceAndValueHistoryEvent] = getResourceMetadataUpdateEvent(
        fullReps.last,
        valuesEvents,
        resourceDeleteEvent)

    } yield resourceCreationEvent ++ resourceDeleteEvent ++ valuesEvents ++ resourceMetadataUpdateEvent

  /**
    * Returns the full representation of a resource at a given date.
    *
    * @param resourceIri                the IRI of the resource.
    * @param versionHist                the history info of the version; i.e. versionDate and author.
    * @param featureFactoryConfig       the feature factory configuration.
    * @param requestingUser             the user making the request.
    * @return the full representation of the resource at the given version date.
    */
  private def getResourceAtGivenTime(resourceIri: IRI,
                                     versionHist: ResourceHistoryEntry,
                                     featureFactoryConfig: FeatureFactoryConfig,
                                     requestingUser: UserADM): Future[(ResourceHistoryEntry, ReadResourceV2)] =
    for {
      resourceFullRepAtCreationTime: ReadResourcesSequenceV2 <- getResourcesV2(
        resourceIris = Seq(resourceIri),
        versionDate = Some(versionHist.versionDate),
        withDeleted = true,
        targetSchema = ApiV2Complex,
        schemaOptions = Set.empty[SchemaOption],
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )
      resourceAtCreationTime: ReadResourceV2 = resourceFullRepAtCreationTime.resources.head
    } yield versionHist -> resourceAtCreationTime

  /**
    * Returns a createResource event as [[ResourceAndValueHistoryEvent]] with request body of the form [[ResourceEventBody]].
    *
    * @param resourceAtTimeOfCreation the full representation of the resource at creation date.
    * @param versionInfoAtCreation the history info of the version; i.e. versionDate and author.
    * @return a createResource event.
    */
  private def getResourceCreationEvent(
      resourceAtTimeOfCreation: ReadResourceV2,
      versionInfoAtCreation: ResourceHistoryEntry): Seq[ResourceAndValueHistoryEvent] = {

    val requestBody: ResourceEventBody = ResourceEventBody(
      resourceIri = resourceAtTimeOfCreation.resourceIri,
      resourceClassIri = resourceAtTimeOfCreation.resourceClassIri,
      label = Some(resourceAtTimeOfCreation.label),
      values = resourceAtTimeOfCreation.values.view
        .mapValues(readValues => readValues.map(readValue => readValue.valueContent))
        .toMap,
      projectADM = resourceAtTimeOfCreation.projectADM,
      permissions = Some(resourceAtTimeOfCreation.permissions),
      creationDate = Some(resourceAtTimeOfCreation.creationDate)
    )

    Seq(
      ResourceAndValueHistoryEvent(
        eventType = ResourceAndValueEventsUtil.CREATE_RESOURCE_EVENT,
        versionDate = versionInfoAtCreation.versionDate,
        author = versionInfoAtCreation.author,
        eventBody = requestBody
      ))
  }

  /**
    * Returns resourceDeletion events as Seq[[ResourceAndValueHistoryEvent]] with request body of the form [[ResourceEventBody]].
    *
    * @param resourceDeletionInfo A sequence of resource deletion info containing version history of deletion and
    *                             the full representation of resource at time of deletion.
    * @return a seq of deleteResource events.
    */
  private def getResourceDeletionEvents(
      resourceDeletionInfo: Seq[(ResourceHistoryEntry, ReadResourceV2)]): Seq[ResourceAndValueHistoryEvent] = {
    resourceDeletionInfo.map {
      case (delHist, fullRepresentation) =>
        val requestBody: ResourceEventBody = ResourceEventBody(
          resourceIri = fullRepresentation.resourceIri,
          resourceClassIri = fullRepresentation.resourceClassIri,
          projectADM = fullRepresentation.projectADM,
          lastModificationDate = fullRepresentation.lastModificationDate,
          deletionInfo = fullRepresentation.deletionInfo
        )
        ResourceAndValueHistoryEvent(
          eventType = ResourceAndValueEventsUtil.DELETE_RESOURCE_EVENT,
          versionDate = delHist.versionDate,
          author = delHist.author,
          eventBody = requestBody
        )
    }
  }

  /**
    * Returns a value event as [[ResourceAndValueHistoryEvent]] with body of the form [[ValueEventBody]].
    *
    * @param resourceAtGivenTime the full representation of the resource at the given time.
    * @param versionHist the history info of the version; i.e. versionDate and author.
    * @param allResourceVersions all full representations of resource for each version date in its history.
    * @return a create/update/delete value event.
    */
  private def getValueEvents(
      resourceAtGivenTime: ReadResourceV2,
      versionHist: ResourceHistoryEntry,
      allResourceVersions: Seq[(ResourceHistoryEntry, ReadResourceV2)]): Seq[ResourceAndValueHistoryEvent] = {
    val resourceIri = resourceAtGivenTime.resourceIri

    /** returns the values of the resource which have the given version date. */
    def findValuesWithGivenVersionDate(values: Map[SmartIri, Seq[ReadValueV2]]): Map[SmartIri, ReadValueV2] = {
      val valuesWithVersionDate: Map[SmartIri, ReadValueV2] = values.foldLeft(Map.empty[SmartIri, ReadValueV2]) {
        case (acc, (propIri, readValue)) =>
          val valuesWithGivenVersion: Seq[ReadValueV2] =
            readValue.filter(readValue =>
              readValue.valueCreationDate == versionHist.versionDate || readValue.deletionInfo.exists(deleteInfo =>
                deleteInfo.deleteDate == versionHist.versionDate))
          if (valuesWithGivenVersion.nonEmpty) {
            acc + (propIri -> valuesWithGivenVersion.head)
          } else { acc }
      }

      valuesWithVersionDate

    }

    val valuesWithAskedVersionDate: Map[SmartIri, ReadValueV2] = findValuesWithGivenVersionDate(
      resourceAtGivenTime.values)
    val valueEvents: Seq[ResourceAndValueHistoryEvent] = valuesWithAskedVersionDate.map {
      case (propIri, readValue) =>
        val event =
          //Is the given date a deletion date?
          if (readValue.deletionInfo.exists(deletionInfo => deletionInfo.deleteDate == versionHist.versionDate)) {
            // Yes. Return a deleteValue event
            val deleteValueRequestBody = ValueEventBody(
              resourceIri = resourceIri,
              resourceClassIri = resourceAtGivenTime.resourceClassIri,
              projectADM = resourceAtGivenTime.projectADM,
              propertyIri = propIri,
              valueIri = readValue.valueIri,
              valueTypeIri = readValue.valueContent.valueType,
              deletionInfo = readValue.deletionInfo,
              previousValueIri = readValue.previousValueIri
            )
            ResourceAndValueHistoryEvent(
              eventType = ResourceAndValueEventsUtil.DELETE_VALUE_EVENT,
              versionDate = versionHist.versionDate,
              author = versionHist.author,
              eventBody = deleteValueRequestBody
            )
          } else {
            // No. Is the given date a creation date, i.e. value does not have a previous version?
            if (readValue.previousValueIri.isEmpty) {
              // Yes. return a createValue event with its request body
              val createValueRequestBody = ValueEventBody(
                resourceIri = resourceIri,
                resourceClassIri = resourceAtGivenTime.resourceClassIri,
                projectADM = resourceAtGivenTime.projectADM,
                propertyIri = propIri,
                valueIri = readValue.valueIri,
                valueTypeIri = readValue.valueContent.valueType,
                valueContent = Some(readValue.valueContent),
                valueUUID = Some(readValue.valueHasUUID),
                valueCreationDate = Some(readValue.valueCreationDate),
                permissions = Some(readValue.permissions),
                valueComment = readValue.valueContent.comment
              )
              ResourceAndValueHistoryEvent(
                eventType = ResourceAndValueEventsUtil.CREATE_VALUE_EVENT,
                versionDate = versionHist.versionDate,
                author = versionHist.author,
                eventBody = createValueRequestBody
              )
            } else {
              // No. return updateValue event
              val (updateEventType: String, updateEventRequestBody: ValueEventBody) =
                getValueUpdateEventType(propIri, readValue, allResourceVersions, resourceAtGivenTime)
              ResourceAndValueHistoryEvent(
                eventType = updateEventType,
                versionDate = versionHist.versionDate,
                author = versionHist.author,
                eventBody = updateEventRequestBody
              )
            }
          }
        event
    }.toSeq

    valueEvents
  }

  /**
    * Since update value operation can be used to update value content or value permissions, using the previous versions
    * of the value, it determines the type of the update and returns eventType: updateValuePermission/updateValueContent
    * together with the request body necessary to do the update.
    *
    * @param propertyIri the IRI of the property.
    * @param currentVersionOfValue the current value version.
    * @param allResourceVersions all versions of resource.
    * @param resourceAtGivenTime the full representation of the resource at time of value update.
    * @return (eventType, update event request body)
    */
  private def getValueUpdateEventType(propertyIri: SmartIri,
                                      currentVersionOfValue: ReadValueV2,
                                      allResourceVersions: Seq[(ResourceHistoryEntry, ReadResourceV2)],
                                      resourceAtGivenTime: ReadResourceV2): (String, ValueEventBody) = {
    val previousValueIri: IRI = currentVersionOfValue.previousValueIri.getOrElse(
      throw BadRequestException("No previous value IRI found for the value, Please report this as a bug."))

    //find the version of resource which has a value with previousValueIri
    val (previousVersionDate, previousVersionOfResource): (ResourceHistoryEntry, ReadResourceV2) = allResourceVersions
      .find(resourceWithHist =>
        resourceWithHist._2.values.exists(item =>
          item._1 == propertyIri && item._2.exists(value => value.valueIri == previousValueIri)))
      .getOrElse(throw NotFoundException(s"Could not find the previous value of ${currentVersionOfValue.valueIri}"))

    // check that the version date of the previousValue is before the version date of the current value.
    if (previousVersionDate.versionDate.isAfter(currentVersionOfValue.valueCreationDate)) {
      throw ForbiddenException(
        s"Previous version of the value ${currentVersionOfValue.valueIri} that has previousValueIRI ${previousValueIri} " +
          s"has a date after the current value.")
    }

    // get the previous value
    val previousValue: ReadValueV2 =
      previousVersionOfResource.values(propertyIri).find(value => value.valueIri == previousValueIri).get

    // Is the content of previous version of value the same as content of the current version?
    val event = if (previousValue.valueContent == currentVersionOfValue.valueContent) {
      //Yes. Permission must have been updated; return a permission update event.
      val updateValuePermissionsRequestBody = ValueEventBody(
        resourceIri = resourceAtGivenTime.resourceIri,
        resourceClassIri = resourceAtGivenTime.resourceClassIri,
        projectADM = resourceAtGivenTime.projectADM,
        propertyIri = propertyIri,
        valueIri = currentVersionOfValue.valueIri,
        valueTypeIri = currentVersionOfValue.valueContent.valueType,
        permissions = Some(currentVersionOfValue.permissions),
        valueComment = currentVersionOfValue.valueContent.comment
      )
      (ResourceAndValueEventsUtil.UPDATE_VALUE_PERMISSION_EVENT, updateValuePermissionsRequestBody)
    } else {
      // No. Content must have been updated; return a content update event.
      val updateValueContentRequestBody = ValueEventBody(
        resourceIri = resourceAtGivenTime.resourceIri,
        resourceClassIri = resourceAtGivenTime.resourceClassIri,
        projectADM = resourceAtGivenTime.projectADM,
        propertyIri = propertyIri,
        valueIri = currentVersionOfValue.valueIri,
        valueTypeIri = currentVersionOfValue.valueContent.valueType,
        valueContent = Some(currentVersionOfValue.valueContent),
        valueUUID = Some(currentVersionOfValue.valueHasUUID),
        valueCreationDate = Some(currentVersionOfValue.valueCreationDate),
        valueComment = currentVersionOfValue.valueContent.comment,
        previousValueIri = currentVersionOfValue.previousValueIri
      )
      (ResourceAndValueEventsUtil.UPDATE_VALUE_CONTENT_EVENT, updateValueContentRequestBody)
    }
    event
  }

  /**
    * Returns an updateResourceMetadata event as [[ResourceAndValueHistoryEvent]] with request body of the form
    * [[ResourceMetadataEventBody]] with information necessary to make update metadata of resource request with a
    * given modification date.
    *
    * @param latestVersionOfResource the full representation of the resource.
    * @param valueEvents             the events describing value operations.
    * @param resourceDeleteEvents    the events describing resource deletion operations.
    * @return an updateResourceMetadata event.
    */
  private def getResourceMetadataUpdateEvent(
      latestVersionOfResource: (ResourceHistoryEntry, ReadResourceV2),
      valueEvents: Seq[ResourceAndValueHistoryEvent],
      resourceDeleteEvents: Seq[ResourceAndValueHistoryEvent]): Seq[ResourceAndValueHistoryEvent] = {
    val readResource: ReadResourceV2 = latestVersionOfResource._2
    val author: IRI = latestVersionOfResource._1.author
    // Is lastModificationDate of resource None
    readResource.lastModificationDate match {
      // Yes. Do nothing.
      case None => Seq.empty[ResourceAndValueHistoryEvent]
      // No. Either a value or the resource metadata must have been modified.
      case Some(modDate) =>
        val deletionEventWithSameDate = resourceDeleteEvents.find(event => event.versionDate == modDate)
        // Is the lastModificationDate of the resource the same as its deletion date?
        val updateMetadataEvent = if (deletionEventWithSameDate.isDefined) {
          // Yes. Do noting.
          Seq.empty[ResourceAndValueHistoryEvent]
          // No. Is there any value event?
        } else if (valueEvents.isEmpty) {
          // No. After creation of the resource its metadata must have been updated, use creation date as the lastModification date of the event.
          val requestBody = ResourceMetadataEventBody(
            resourceIri = readResource.resourceIri,
            resourceClassIri = readResource.resourceClassIri,
            lastModificationDate = readResource.creationDate,
            newModificationDate = modDate
          )
          val event = ResourceAndValueHistoryEvent(
            eventType = ResourceAndValueEventsUtil.UPDATE_RESOURCE_METADATA_EVENT,
            versionDate = modDate,
            author = author,
            eventBody = requestBody
          )
          Seq(event)
        } else {
          // Yes. Sort the value events by version date.
          val sortedEvents = valueEvents.sortBy(_.versionDate)
          // Is there any value event with version date equal to lastModificationDate of the resource?
          val modDateExists = valueEvents.find(event => event.versionDate == modDate)
          modDateExists match {
            // Yes. The last modification date of the resource reflects the modification of a value. Return nothing.
            case Some(_) => Seq.empty[ResourceAndValueHistoryEvent]
            // No. The last modification date of the resource reflects update of a resource's metadata. Return an updateMetadataEvent
            case None =>
              // Find the event with version date before resource's last modification date.
              val eventsBeforeModDate = sortedEvents.filter(event => event.versionDate.isBefore(modDate))
              // Is there any value with versionDate before this date?
              val oldModDate = if (eventsBeforeModDate.nonEmpty) {
                // Yes. assign the versionDate of the last value event as lastModificationDate for request.
                eventsBeforeModDate.last.versionDate

              } else {
                // No. The metadata of the resource must have been updated after the value operations, use the version date
                // of the last value event as the lastModificationDate
                sortedEvents.last.versionDate
              }
              val requestBody = ResourceMetadataEventBody(
                resourceIri = readResource.resourceIri,
                resourceClassIri = readResource.resourceClassIri,
                lastModificationDate = oldModDate,
                newModificationDate = modDate
              )
              val event = ResourceAndValueHistoryEvent(
                eventType = ResourceAndValueEventsUtil.UPDATE_RESOURCE_METADATA_EVENT,
                versionDate = modDate,
                author = author,
                eventBody = requestBody
              )
              Seq(event)
          }
        }
        updateMetadataEvent
    }
  }
}
