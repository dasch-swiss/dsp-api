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

package org.knora.webapi.responders.v2

import java.time.Instant

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{DefaultObjectAccessPermissionsStringForResourceClassGetADM, DefaultObjectAccessPermissionsStringResponseADM, ResourceCreateOperation}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse, SparqlUpdateRequest, SparqlUpdateResponse}
import org.knora.webapi.messages.v1.responder.valuemessages.KnoraCalendarV1
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, GetXSLTransformationRequestV2, GetXSLTransformationResponseV2}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.twirl.SparqlTemplateResourceToCreate
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.ConstructResponseUtilV2.{MappingAndXSLTransformation, ResourceWithValueRdfData}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.search.ConstructQuery
import org.knora.webapi.util.search.gravsearch.GravsearchParser

import scala.concurrent.Future
import scala.concurrent.duration._

class ResourcesResponderV2 extends ResponderWithStandoffV2 {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    /**
      * Represents a resource that is ready to be created and whose contents can be verified afterwards.
      *
      * @param sparqlTemplateResourceToCreate a [[SparqlTemplateResourceToCreate]] describing SPARQL for creating
      *                                       the resource.
      * @param values                         the resource's values for verification.
      */
    case class ResourceReadyToCreate(sparqlTemplateResourceToCreate: SparqlTemplateResourceToCreate,
                                     values: Map[SmartIri, Seq[GenerateSparqlForValueInNewResourceV2]])

    override def receive: Receive = {
        case ResourcesGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResources(resIris, requestingUser), log)
        case ResourcesPreviewGetRequestV2(resIris, requestingUser) => future2Message(sender(), getResourcePreview(resIris, requestingUser), log)
        case ResourceTEIGetRequestV2(resIri, textProperty, mappingIri, gravsearchTemplateIri, headerXSLTIri, requestingUser) => future2Message(sender(), getResourceAsTEI(resIri, textProperty, mappingIri, gravsearchTemplateIri, headerXSLTIri, requestingUser), log)
        case createResourceRequestV2: CreateResourceRequestV2 => future2Message(sender(), createResourceV2(createResourceRequestV2), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Creates a new resource.
      *
      * @param createResourceRequestV2 the request to create the resource.
      * @return a [[ReadResourcesSequenceV2]] containing a preview of the resource.
      */
    private def createResourceV2(createResourceRequestV2: CreateResourceRequestV2): Future[ReadResourcesSequenceV2] = {

        def makeTaskFuture: Future[ReadResourcesSequenceV2] = {
            for {
                // Convert the resource to the internal ontology schema.
                internalCreateResource <- Future(createResourceRequestV2.createResource.toOntologySchema(InternalSchema))

                // Check standoff link targets and list nodes that should exist.

                _ <- checkStandoffLinkTargets(internalCreateResource.flatValues, createResourceRequestV2.requestingUser)
                _ <- checkListNodes(internalCreateResource.flatValues, createResourceRequestV2.requestingUser)

                // Get the class IRIs of all the link targets in the request.
                linkTargetClasses: Map[IRI, SmartIri] <- getLinkTargetClasses(
                    internalCreateResources = Seq(internalCreateResource),
                    requestingUser = createResourceRequestV2.requestingUser
                )

                // Get the definitions of the resource class and its properties, as well as of the classes of all
                // resources that are link targets.

                resourceClassEntityInfoResponse: EntityInfoGetResponseV2 <- (responderManager ? EntityInfoGetRequestV2(
                    classIris = linkTargetClasses.values.toSet + internalCreateResource.resourceClassIri,
                    requestingUser = createResourceRequestV2.requestingUser
                )).mapTo[EntityInfoGetResponseV2]

                resourceClassInfo: ReadClassInfoV2 = resourceClassEntityInfoResponse.classInfoMap(internalCreateResource.resourceClassIri)

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

                defaultPropertyPermissions: Map[SmartIri, String] = defaultPropertyPermissionsMap(internalCreateResource.resourceClassIri)

                // Make a timestamp for the resource and its values.
                currentTime: Instant = Instant.now

                // Do the remaining pre-update checks and make a ResourceReadyToCreate describing the SPARQL
                // for creating the resource.
                resourceReadyToCreate: ResourceReadyToCreate <- generateResourceReadyToCreate(
                    internalCreateResource = internalCreateResource,
                    linkTargetClasses = linkTargetClasses,
                    entityInfo = allEntityInfo,
                    clientResourceIDs = Map.empty[IRI, String],
                    defaultResourcePermissions = defaultResourcePermissions,
                    defaultPropertyPermissions = defaultPropertyPermissions,
                    currentTime = currentTime,
                    requestingUser = createResourceRequestV2.requestingUser
                )

                // Get the IRI of the named graph in which the resource will be created.
                dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(createResourceRequestV2.createResource.projectADM)

                // Generate SPARQL for creating the resource.
                sparqlUpdate = queries.sparql.v2.txt.createNewResources(
                    dataNamedGraph = dataNamedGraph,
                    triplestore = settings.triplestoreType,
                    resourcesToCreate = Seq(resourceReadyToCreate.sparqlTemplateResourceToCreate),
                    projectIri = createResourceRequestV2.createResource.projectADM.id,
                    creatorIri = createResourceRequestV2.requestingUser.id,
                    currentTime = currentTime
                ).toString()

                // Do the update.
                _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

                // Verify that the resource was created correctly.
                previewOfCreatedResource: ReadResourcesSequenceV2 <- verifyResource(
                    resourceReadyToCreate = resourceReadyToCreate,
                    projectIri = createResourceRequestV2.createResource.projectADM.id,
                    requestingUser = createResourceRequestV2.requestingUser
                )
            } yield previewOfCreatedResource
        }

        for {
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

            _ = if (projectIri == OntologyConstants.KnoraBase.SystemProject || projectIri == OntologyConstants.KnoraBase.DefaultSharedOntologiesProject) {
                throw BadRequestException(s"Resources cannot be created in project <$projectIri>")
            }

            // Ensure that the resource class isn't from a non-shared ontology in another project.

            resourceClassOntologyIri: SmartIri = createResourceRequestV2.createResource.resourceClassIri.getOntologyFromEntity
            readOntologyMetadataV2: ReadOntologyMetadataV2 <- (responderManager ? OntologyMetadataGetByIriRequestV2(Set(resourceClassOntologyIri), createResourceRequestV2.requestingUser)).mapTo[ReadOntologyMetadataV2]
            ontologyMetadata: OntologyMetadataV2 = readOntologyMetadataV2.ontologies.headOption.getOrElse(throw BadRequestException(s"Ontology $resourceClassOntologyIri not found"))
            ontologyProjectIri: IRI = ontologyMetadata.projectIri.getOrElse(throw InconsistentTriplestoreDataException(s"Ontology $resourceClassOntologyIri has no project")).toString

            _ = if (projectIri != ontologyProjectIri && !(ontologyMetadata.ontologyIri.isKnoraBuiltInDefinitionIri || ontologyMetadata.ontologyIri.isKnoraSharedDefinitionIri)) {
                throw BadRequestException(s"Cannot create a resource in project <$projectIri> with resource class <${createResourceRequestV2.createResource.resourceClassIri}>, which is defined in a non-shared ontology in another project")
            }

            // Check user's PermissionProfile (part of UserADM) to see if the user has the permission to
            // create a new resource in the given project.
            _ = if (!createResourceRequestV2.requestingUser.permissions.hasPermissionFor(ResourceCreateOperation(createResourceRequestV2.createResource.resourceClassIri.toString), projectIri, None)) {
                throw ForbiddenException(s"User ${createResourceRequestV2.requestingUser.email} does not have permissions to create a resource in project <$projectIri>")
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource to be created.
            taskResult <- IriLocker.runWithIriLock(
                createResourceRequestV2.apiRequestID,
                createResourceRequestV2.createResource.resourceIri,
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
      * @param currentTime                the timestamp to be attached to the resource and its values.
      * @param requestingUser             the user making the request.
      * @return a [[ResourceReadyToCreate]].
      */
    private def generateResourceReadyToCreate(internalCreateResource: CreateResourceV2,
                                              linkTargetClasses: Map[IRI, SmartIri],
                                              entityInfo: EntityInfoGetResponseV2,
                                              clientResourceIDs: Map[IRI, String],
                                              defaultResourcePermissions: String,
                                              defaultPropertyPermissions: Map[SmartIri, String],
                                              currentTime: Instant,
                                              requestingUser: UserADM): Future[ResourceReadyToCreate] = {
        for {
            // Check that the resource class has a suitable cardinality for each submitted value.

            resourceClassInfo <- Future(entityInfo.classInfoMap(internalCreateResource.resourceClassIri))

            knoraPropertyCardinalities: Map[SmartIri, Cardinality.KnoraCardinalityInfo] = resourceClassInfo.allCardinalities.filterKeys(resourceClassInfo.knoraResourceProperties)

            _ = internalCreateResource.values.foreach {
                case (propertyIri: SmartIri, valuesForProperty: Seq[CreateValueInNewResourceV2]) =>
                    val internalPropertyIri = propertyIri.toOntologySchema(InternalSchema)

                    val cardinalityInfo = knoraPropertyCardinalities.getOrElse(internalPropertyIri, throw OntologyConstraintException(s"Resource class <${internalCreateResource.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}> has no cardinality for property <$propertyIri>"))

                    if ((cardinalityInfo.cardinality == Cardinality.MayHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveOne) && valuesForProperty.size > 1) {
                        throw OntologyConstraintException(s"Resource class <${internalCreateResource.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}> does not allow more than one value for property <$propertyIri>")
                    }
            }

            // Check that no required values are missing.

            requiredProps: Set[SmartIri] = knoraPropertyCardinalities.filter {
                case (_, cardinalityInfo) => cardinalityInfo.cardinality == Cardinality.MustHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveSome
            }.keySet -- resourceClassInfo.linkProperties

            internalPropertyIris: Set[SmartIri] = internalCreateResource.values.keySet

            _ = if (!requiredProps.subsetOf(internalPropertyIris)) {
                val missingProps = (requiredProps -- internalPropertyIris).map(iri => s"<${iri.toOntologySchema(ApiV2WithValueObjects)}>").mkString(", ")
                throw OntologyConstraintException(s"Values were not submitted for the following property or properties, which are required by resource class <${internalCreateResource.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}>: $missingProps")
            }

            // Check that each submitted value is consistent with the knora-base:objectClassConstraint of the property that is supposed to
            // point to it.

            _ = checkObjectClassConstraints(
                values = internalCreateResource.values,
                linkTargetClasses = linkTargetClasses,
                entityInfo = entityInfo,
                clientResourceIDs = clientResourceIDs
            )

            // TODO: Check that the submitted values are not redundant.

            // Validate and reformat any custom permissions in the request, and set all permissions to defaults if custom
            // permissions are not provided.

            resourcePermissions: String <- internalCreateResource.permissions match {
                case Some(permissionStr) => PermissionUtilADM.validatePermissions(permissionLiteral = permissionStr, responderManager = responderManager)
                case None => FastFuture.successful(defaultResourcePermissions)
            }

            valuesWithValidatedPermissions: Map[SmartIri, Seq[GenerateSparqlForValueInNewResourceV2]] <- validateAndFormatValuePermissions(internalCreateResource.values, defaultPropertyPermissions)

            // Ask the values responder for SPARQL for generating the values.
            sparqlForValuesResponse: GenerateSparqlToCreateMultipleValuesResponseV2 <- (responderManager ?
                GenerateSparqlToCreateMultipleValuesRequestV2(
                    resourceIri = internalCreateResource.resourceIri,
                    values = valuesWithValidatedPermissions,
                    currentTime = currentTime,
                    requestingUser = requestingUser)
                ).mapTo[GenerateSparqlToCreateMultipleValuesResponseV2]
        } yield ResourceReadyToCreate(
            sparqlTemplateResourceToCreate = SparqlTemplateResourceToCreate(
                resourceIri = internalCreateResource.resourceIri,
                permissions = resourcePermissions,
                sparqlForValues = sparqlForValuesResponse.insertSparql,
                resourceClassIri = internalCreateResource.resourceClassIri.toString,
                resourceLabel = internalCreateResource.label
            ),
            values = valuesWithValidatedPermissions
        )
    }

    /**
      * Given a sequence of resources to be created, gets the class IRIs of all the resources that are the targets of
      * link values in the new resources, whether these already exist in the triplestore or are among the resources
      * to be created.
      *
      * @param internalCreateResources the resources to be created.
      * @param requestingUser          the user making the request.
      * @return a map of resource IRIs to class IRIs.
      */
    private def getLinkTargetClasses(internalCreateResources: Seq[CreateResourceV2], requestingUser: UserADM): Future[Map[IRI, SmartIri]] = {
        // Get the IRIs of the new and existing resources that are targets of links.
        val (existingTargets: Set[IRI], newTargets: Set[IRI]) = internalCreateResources.flatMap(_.flatValues).foldLeft((Set.empty[IRI], Set.empty[IRI])) {
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
        val classesOfNewTargets: Map[IRI, SmartIri] = internalCreateResources.map {
            resourceToCreate => resourceToCreate.resourceIri -> resourceToCreate.resourceClassIri
        }.toMap.filterKeys(newTargets)

        for {
            // Get information about the existing resources that are targets of links.
            existingTargets: ReadResourcesSequenceV2 <- getResourcePreview(existingTargets.toSeq, requestingUser)

            // Make a map of the IRIs of existing target resources to their class IRIs.
            classesOfExistingTargets: Map[IRI, SmartIri] = existingTargets.resources.map(resource => resource.resourceIri -> resource.resourceClassIri).toMap
        } yield classesOfNewTargets ++ classesOfExistingTargets
    }

    /**
      * Checks that values to be created in a new resource are compatible with the object class constraints
      * of the resource's properties.
      *
      * @param values            a map of property IRIs to values to be created (in the internal schema).
      * @param linkTargetClasses a map of resources that are link targets to the IRIs of those resource's classes.
      * @param entityInfo        an [[EntityInfoGetResponseV2]] containing definitions of the classes that all the link targets
      *                          belong to.
      * @param clientResourceIDs a map of IRIs of resources to be created to client IDs for the same resources, if any.
      */
    private def checkObjectClassConstraints(values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
                                            linkTargetClasses: Map[IRI, SmartIri],
                                            entityInfo: EntityInfoGetResponseV2,
                                            clientResourceIDs: Map[IRI, String] = Map.empty[IRI, String]): Unit = {
        values.foreach {
            case (propertyIri: SmartIri, valuesToCreate: Seq[CreateValueInNewResourceV2]) =>
                val propertyInfo: ReadPropertyInfoV2 = entityInfo.propertyInfoMap(propertyIri)

                // Don't accept link properties.
                if (propertyInfo.isLinkProp) {
                    throw BadRequestException(s"Invalid property <${propertyIri.toOntologySchema(ApiV2WithValueObjects)}>. Use a link value property to submit a link.")
                }

                // Get the property's object class constraint. If this is a link value property, we want the object
                // class constraint of the corresponding link property instead.

                val propertyInfoForObjectClassConstraint = if (propertyInfo.isLinkValueProp) {
                    entityInfo.propertyInfoMap(propertyIri.fromLinkValuePropToLinkProp)
                } else {
                    propertyInfo
                }

                val propertyIriForObjectClassConstraint = propertyInfoForObjectClassConstraint.entityInfoContent.propertyIri

                val objectClassConstraint: SmartIri = propertyInfoForObjectClassConstraint.entityInfoContent.requireIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                    throw InconsistentTriplestoreDataException(s"Property <$propertyIriForObjectClassConstraint> has no knora-api:objectType"))

                // Check each value.
                for (valueToCreate: CreateValueInNewResourceV2 <- valuesToCreate) {
                    valueToCreate.valueContent match {
                        case linkValueContentV2: LinkValueContentV2 =>
                            // It's a link value.

                            if (!propertyInfo.isLinkValueProp) {
                                throw OntologyConstraintException(s"Property <${propertyIri.toOntologySchema(ApiV2WithValueObjects)}> requires a value of type knora-api:LinkValue")
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

                                throw OntologyConstraintException(s"Resource $resourceID cannot be the target of property <${propertyIriForObjectClassConstraint.toOntologySchema(ApiV2WithValueObjects)}>, because it does not belong to class <${objectClassConstraint.toOntologySchema(ApiV2WithValueObjects)}>")
                            }

                        case otherValueContentV2: ValueContentV2 =>
                            // It's not a link value. Check that its type is equal to the property's object
                            // class constraint.
                            if (otherValueContentV2.valueType != objectClassConstraint) {
                                throw OntologyConstraintException(s"Property <${propertyIri.toOntologySchema(ApiV2WithValueObjects)}> requires a value of type knora-api:LinkValue")
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
      * @param values         the values to be checked.
      * @param requestingUser the user making the request.
      */
    private def checkStandoffLinkTargets(values: Iterable[CreateValueInNewResourceV2], requestingUser: UserADM): Future[Unit] = {
        val standoffLinkTargetsThatShouldExist: Set[IRI] = values.foldLeft(Set.empty[IRI]) {
            case (acc: Set[IRI], valueToCreate: CreateValueInNewResourceV2) =>
                valueToCreate.valueContent match {
                    case textValueContentV2: TextValueContentV2 => acc ++ textValueContentV2.standoffLinkTagIriAttributes.filter(_.targetExists).map(_.value)
                    case _ => acc
                }
        }

        checkResourceIris(standoffLinkTargetsThatShouldExist, requestingUser)
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
                    case hierarchicalListValueContentV2: HierarchicalListValueContentV2 => acc + hierarchicalListValueContentV2.valueHasListNode
                    case _ => acc
                }
        }

        Future.sequence(listNodesThatShouldExist.map(listNodeIri => ValueUtilV2.checkListNodeExists(listNodeIri, storeManager)).toSeq).map(_ => ())
    }

    /**
      * Given a map of property IRIs to values to be created in a new resource, validates and reformats any custom
      * permissions in the values, and sets all value permissions to defaults if custom permissions are not provided.
      *
      * @param values                     the values whose permissions are to be validated.
      * @param defaultPropertyPermissions a map of property IRIs to default permissions.
      * @return a map of property IRIs to sequences of [[GenerateSparqlForValueInNewResourceV2]], in which
      *         all permissions have been validated and defined.
      */
    private def validateAndFormatValuePermissions(values: Map[SmartIri, Seq[CreateValueInNewResourceV2]],
                                                  defaultPropertyPermissions: Map[SmartIri, String]): Future[Map[SmartIri, Seq[GenerateSparqlForValueInNewResourceV2]]] = {
        val propertyValuesWithValidatedPermissionsFutures: Map[SmartIri, Seq[Future[GenerateSparqlForValueInNewResourceV2]]] = values.map {
            case (propertyIri: SmartIri, valuesToCreate: Seq[CreateValueInNewResourceV2]) =>
                val validatedPermissionFutures: Seq[Future[GenerateSparqlForValueInNewResourceV2]] = valuesToCreate.map {
                    valueToCreate =>
                        // Does this value have custom permissions?
                        valueToCreate.permissions match {
                            case Some(permissionStr: String) =>
                                // Yes. Validate and reformat them.
                                val validatedPermissionFuture: Future[String] = PermissionUtilADM.validatePermissions(permissionLiteral = permissionStr, responderManager = responderManager)

                                // Make a future in which the value has the reformatted permissions.
                                validatedPermissionFuture.map {
                                    validatedPermissions: String =>
                                        GenerateSparqlForValueInNewResourceV2(
                                            valueContent = valueToCreate.valueContent,
                                            permissions = validatedPermissions
                                        )
                                }

                            case None =>
                                // No. Use the default permissions.
                                FastFuture.successful {
                                    GenerateSparqlForValueInNewResourceV2(
                                        valueContent = valueToCreate.valueContent,
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
      * @param resourceClassIris the IRIs of the resource classes.
      * @param requestingUser    the user making the request.
      * @return a map of resource class IRIs to default permission strings.
      */
    private def getResourceClassDefaultPermissions(projectIri: IRI, resourceClassIris: Set[SmartIri], requestingUser: UserADM): Future[Map[SmartIri, String]] = {
        val permissionsFutures: Map[SmartIri, Future[String]] = resourceClassIris.toSeq.map {
            resourceClassIri =>
                val requestMessage = DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = projectIri,
                    resourceClassIri = resourceClassIri.toString,
                    targetUser = requestingUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )

                resourceClassIri -> (responderManager ? requestMessage).mapTo[DefaultObjectAccessPermissionsStringResponseADM].map(_.permissionLiteral)
        }.toMap

        ActorUtil.sequenceFuturesInMap(permissionsFutures)
    }

    /**
      * Gets the default permissions for properties in a resource class in a project.
      *
      * @param projectIri              the IRI of the project.
      * @param resourceClassProperties a map of resource class IRIs to sets of property IRIs.
      * @param requestingUser          the user making the request.
      * @return a map of resource class IRIs to maps of property IRIs to default permission strings.
      */
    private def getDefaultPropertyPermissions(projectIri: IRI, resourceClassProperties: Map[SmartIri, Set[SmartIri]], requestingUser: UserADM): Future[Map[SmartIri, Map[SmartIri, String]]] = {
        val permissionsFutures: Map[SmartIri, Future[Map[SmartIri, String]]] = resourceClassProperties.map {
            case (resourceClassIri, propertyIris) =>
                val propertyPermissionsFutures: Map[SmartIri, Future[String]] = propertyIris.toSeq.map {
                    propertyIri =>
                        propertyIri -> ValueUtilV2.getDefaultValuePermissions(
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
      * @param requestingUser        the user that attempted to create the resource.
      * @return a preview of the resource that was created.
      */
    private def verifyResource(resourceReadyToCreate: ResourceReadyToCreate,
                               projectIri: IRI,
                               requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {
        val resourceIri = resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceIri

        for {
            resourcesResponse: ReadResourcesSequenceV2 <- getResources(
                resourceIris = Seq(resourceIri),
                requestingUser = requestingUser
            )

            resource: ReadResourceV2 = try {
                resourcesResponse.toResource(requestedResourceIri = resourceIri)
            } catch {
                case _: NotFoundException => throw UpdateNotPerformedException(s"Resource <$resourceIri> was not created. Please report this as a possible bug.")
            }

            _ = if (resource.resourceClassIri.toString != resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceClassIri) {
                throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong resource class")
            }

            _ = if (resource.attachedToUser != requestingUser.id) {
                throw AssertionException(s"Resource <$resourceIri> was saved, but it is attached to the wrong user")
            }

            _ = if (resource.attachedToProject != projectIri) {
                throw AssertionException(s"Resource <$resourceIri> was saved, but it is attached to the wrong user")
            }

            _ = if (resource.permissions != resourceReadyToCreate.sparqlTemplateResourceToCreate.permissions) {
                throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong permissions")
            }

            _ = if (resource.label != resourceReadyToCreate.sparqlTemplateResourceToCreate.resourceLabel) {
                throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong label")
            }

            _ = if (resource.values.keySet != resourceReadyToCreate.values.keySet) {
                throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong properties")
            }

            _ = resource.values.foreach {
                case (propertyIri: SmartIri, savedValues: Seq[ReadValueV2]) =>
                    val expectedValues: Seq[GenerateSparqlForValueInNewResourceV2] = resourceReadyToCreate.values(propertyIri)

                    if (expectedValues.size != savedValues.size) {
                        throw AssertionException(s"Resource <$resourceIri> was saved, but it has the wrong values")
                    }

                    savedValues.zip(expectedValues).foreach {
                        case (savedValue, expectedValue) =>
                            if (!(savedValue.valueContent.wouldDuplicateCurrentVersion(expectedValue.valueContent) &&
                                savedValue.permissions == expectedValue.permissions &&
                                savedValue.attachedToUser == requestingUser.id)) {
                                throw AssertionException(s"Resource <$resourceIri> was saved, but one or more of its values are not correct")
                            }
                    }
            }
        } yield ReadResourcesSequenceV2(
            numberOfResources = 1,
            resources = Seq(resource.copy(values = Map.empty))
        )
    }

    /**
      * Gets the requested resources from the triplestore.
      *
      * @param resourceIris the Iris of the requested resources.
      * @return a [[Map[IRI, ResourceWithValueRdfData]]] representing the resources.
      */
    private def getResourcesFromTriplestore(resourceIris: Seq[IRI], preview: Boolean, requestingUser: UserADM): Future[Map[IRI, ResourceWithValueRdfData]] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {
            resourceRequestSparql <- Future(queries.sparql.v2.txt.getResourcePropertiesAndValues(
                triplestore = settings.triplestoreType,
                resourceIris = resourceIrisDistinct,
                preview
            ).toString())

            // _ = println(resourceRequestSparql)

            resourceRequestResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(resourceRequestSparql)).mapTo[SparqlConstructResponse]

            // separate resources and values
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = resourceRequestResponse, requestingUser = requestingUser)

            // check if all the requested resources were returned
            requestedButMissing: Set[IRI] = resourceIrisDistinct.toSet -- queryResultsSeparated.keySet

            _ = if (requestedButMissing.nonEmpty) {
                throw NotFoundException(
                    s"""Not all the requested resources from ${resourceIrisDistinct.mkString(", ")} could not be found:
                        maybe you do not have the right to see all of them or some are marked as deleted.
                        Missing: ${requestedButMissing.map(resourceIri => s"<$resourceIri>").mkString(", ")}""".stripMargin)

            }
        } yield queryResultsSeparated

    }

    /**
      * Get one or several resources and return them as a sequence.
      *
      * @param resourceIris   the resources to query for.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]].
      */
    private def getResources(resourceIris: Seq[IRI], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {

            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = resourceIris, preview = false, requestingUser = requestingUser)

            // get the mappings
            mappingsAsMap <- getMappingsFromQueryResultsSeparated(queryResultsSeparated, requestingUser)

            resourcesResponse: Vector[ReadResourceV2] = resourceIrisDistinct.map {
                resIri: IRI =>
                    ConstructResponseUtilV2.createFullResourceResponse(resIri, queryResultsSeparated(resIri), mappings = mappingsAsMap)
            }.toVector

        } yield ReadResourcesSequenceV2(numberOfResources = resourceIrisDistinct.size, resources = resourcesResponse)

    }

    /**
      * Get the preview of a resource.
      *
      * @param resourceIris   the resource to query for.
      * @param requestingUser the the client making the request.
      * @return a [[ReadResourcesSequenceV2]].
      */
    private def getResourcePreview(resourceIris: Seq[IRI], requestingUser: UserADM): Future[ReadResourcesSequenceV2] = {

        // eliminate duplicate Iris
        val resourceIrisDistinct: Seq[IRI] = resourceIris.distinct

        for {
            queryResultsSeparated: Map[IRI, ResourceWithValueRdfData] <- getResourcesFromTriplestore(resourceIris = resourceIris, preview = true, requestingUser = requestingUser)

            resourcesResponse: Vector[ReadResourceV2] = resourceIrisDistinct.map {
                resIri: IRI =>
                    ConstructResponseUtilV2.createFullResourceResponse(resIri, queryResultsSeparated(resIri), mappings = Map.empty[IRI, MappingAndXSLTransformation])
            }.toVector

        } yield ReadResourcesSequenceV2(numberOfResources = resourceIrisDistinct.size, resources = resourcesResponse)

    }

    /**
      * Obtains a Gravsearch template from Sipi.
      *
      * @param gravsearchTemplateIri the Iri of the resource representing the Gravsearch template.
      * @param requestingUser        the user making the request.
      * @return the Gravsearch template.
      */
    private def getGravsearchTemplate(gravsearchTemplateIri: IRI, requestingUser: UserADM) = {

        val gravsearchUrlFuture = for {
            gravsearchResponseV2: ReadResourcesSequenceV2 <- (responderManager ? ResourcesGetRequestV2(resourceIris = Vector(gravsearchTemplateIri), requestingUser = requestingUser)).mapTo[ReadResourcesSequenceV2]

            gravsearchFileValue: TextFileValueContentV2 = gravsearchResponseV2.resources.headOption match {
                case Some(resource: ReadResourceV2) if resource.resourceClassIri.toString == OntologyConstants.KnoraBase.TextRepresentation =>
                    resource.values.get(OntologyConstants.KnoraBase.HasTextFileValue.toSmartIri) match {
                        case Some(values: Seq[ReadValueV2]) if values.size == 1 => values.head match {
                            case value: ReadValueV2 => value.valueContent match {
                                case textRepr: TextFileValueContentV2 => textRepr
                                case other => throw InconsistentTriplestoreDataException(s"${OntologyConstants.KnoraBase.XSLTransformation} $gravsearchTemplateIri is supposed to have exactly one value of type ${OntologyConstants.KnoraBase.TextFileValue}")
                            }
                        }

                        case None => throw InconsistentTriplestoreDataException(s"${OntologyConstants.KnoraBase.XSLTransformation} has no property ${OntologyConstants.KnoraBase.HasTextFileValue}")
                    }

                case None => throw BadRequestException(s"Resource $gravsearchTemplateIri is not a ${OntologyConstants.KnoraBase.XSLTransformation}")
            }

            // check if `xsltFileValue` represents an XSL transformation
            _ = if (!(gravsearchFileValue.internalMimeType == "text/plain" && gravsearchFileValue.originalFilename.endsWith(".txt"))) {
                throw BadRequestException(s"$gravsearchTemplateIri does not have a file value referring to an XSL transformation")
            }

            gravSearchUrl: String = s"${settings.internalSipiFileServerGetUrl}/${gravsearchFileValue.internalFilename}"

        } yield gravSearchUrl

        val recoveredGravsearchUrlFuture = gravsearchUrlFuture.recover {
            case notFound: NotFoundException => throw BadRequestException(s"XSL transformation $gravsearchTemplateIri not found: ${notFound.message}")
        }

        for {
            gravsearchTemplateUrl <- recoveredGravsearchUrlFuture

            sipiResponseFuture: Future[HttpResponse] = for {

                // ask Sipi to return the XSL transformation file
                response: HttpResponse <- Http().singleRequest(
                    HttpRequest(
                        method = HttpMethods.GET,
                        uri = gravsearchTemplateUrl
                    )
                )

            } yield response

            sipiResponseFutureRecovered: Future[HttpResponse] = sipiResponseFuture.recoverWith {

                case noResponse: akka.stream.scaladsl.TcpIdleTimeoutException =>
                    // this problem is hardly the user's fault. Create a SipiException
                    throw SipiException(message = "Sipi not reachable", e = noResponse, log = log)


                // TODO: what other exceptions have to be handled here?
                // if Exception is used, also previous errors are caught here

            }

            sipiResponseRecovered: HttpResponse <- sipiResponseFutureRecovered

            httpStatusCode: StatusCode = sipiResponseRecovered.status

            messageBody <- sipiResponseRecovered.entity.toStrict(5.seconds)

            _ = if (httpStatusCode != StatusCodes.OK) {
                throw SipiException(s"Sipi returned status code ${httpStatusCode.intValue} with msg '${messageBody.data.decodeString("UTF-8")}'")
            }

            // get the XSL transformation
            gravsearchTemplate: String = messageBody.data.decodeString("UTF-8")

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
      * @param requestingUser        the user making the request.
      * @return a [[ResourceTEIGetResponseV2]].
      */
    private def getResourceAsTEI(resourceIri: IRI, textProperty: SmartIri, mappingIri: Option[IRI], gravsearchTemplateIri: Option[IRI], headerXSLTIri: Option[String], requestingUser: UserADM): Future[ResourceTEIGetResponseV2] = {

        /**
          * Extract the text value to be converted to TEI/XML.
          *
          * @param readResourceSeq the resource which is expected to hold the text value.
          * @return a [[TextValueContentV2]] representing the text value to be converted to TEI/XML.
          */
        def getTextValueFromReadResourceSeq(readResourceSeq: ReadResourcesSequenceV2): TextValueContentV2 = {

            if (readResourceSeq.resources.size != 1) throw BadRequestException(s"Expected exactly one resource, but ${readResourceSeq.resources.size} given")

            readResourceSeq.resources.head.values.get(textProperty) match {
                case Some(valObjs: Seq[ReadValueV2]) if valObjs.size == 1 =>
                    // make sure that the property has one instance and that it is of type TextValue and that is has standoff (markup)
                    valObjs.head.valueContent match {
                        case textValWithStandoff: TextValueContentV2 if textValWithStandoff.standoffAndMapping.nonEmpty =>
                            textValWithStandoff

                        case _ => throw BadRequestException(s"$textProperty to be of type ${OntologyConstants.KnoraBase.TextValue} with standoff (markup)")
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
                            valueObj.valueContent match {
                                case dateContent: DateValueContentV2 =>
                                    // date value

                                    valueObj.copy(
                                        valueContent = dateContent.copy(
                                            // act as if this was a Gregorian date
                                            valueHasCalendar = KnoraCalendarV1.GREGORIAN
                                        )
                                    )

                                case linkContent: LinkValueContentV2 if linkContent.nestedResource.nonEmpty =>
                                    // recursively process the values of the nested resource

                                    valueObj.copy(
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
            resource: ReadResourcesSequenceV2 <- if (gravsearchTemplateIri.nonEmpty) {

                // check that there is an XSLT to create the TEI header
                if (headerXSLTIri.isEmpty) throw BadRequestException(s"When a Gravsearch template Iri is provided, also a header XSLT Iri has to be provided.")

                for {
                    // get the template
                    template <- getGravsearchTemplate(gravsearchTemplateIri.get, requestingUser)

                    // insert actual resource Iri, replacing the placeholder
                    gravsearchQuery = template.replace("$resourceIri", resourceIri)

                    // parse the Gravsearch query
                    constructQuery: ConstructQuery = GravsearchParser.parseQuery(gravsearchQuery)

                    // do a request to the SearchResponder
                    gravSearchResponse: ReadResourcesSequenceV2 <- (responderManager ? GravsearchRequestV2(constructQuery = constructQuery, requestingUser = requestingUser)).mapTo[ReadResourcesSequenceV2]

                    // exactly one resource is expected
                    _ = if (gravSearchResponse.resources.size != 1) throw BadRequestException(s"Gravsearch query for $resourceIri should return one result, but ${gravSearchResponse.resources.size} given.")

                } yield gravSearchResponse

            } else {
                // no Gravsearch template is provided

                // check that there is no XSLT for the header since there is no Gravsearch template
                if (headerXSLTIri.nonEmpty) throw BadRequestException(s"When no Gravsearch template Iri is provided, no header XSLT Iri is expected to be provided either.")

                for {
                    // get requested resource
                    resource <- getResources(Vector(resourceIri), requestingUser)

                } yield resource
            }

            // get the value object representing the text value that is to be mapped to the body of the TEI document
            bodyTextValue: TextValueContentV2 = getTextValueFromReadResourceSeq(resource)

            // the ext value is expected to have standoff markup
            _ = if (bodyTextValue.standoffAndMapping.isEmpty) throw BadRequestException(s"Property $textProperty of $resourceIri is expected to have standoff markup")

            // get all the metadata but the text property for the TEI header
            headerResource = resource.resources.head.copy(
                values = convertDateToGregorian(resource.resources.head.values - textProperty)
            )

            // get the XSL transformation for the TEI header
            headerXSLT: Option[String] <- if (headerXSLTIri.nonEmpty) {
                for {
                    xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? GetXSLTransformationRequestV2(headerXSLTIri.get, requestingUser = requestingUser)).mapTo[GetXSLTransformationResponseV2]
                } yield Some(xslTransformation.xslt)
            } else {
                Future(None)
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
            teiMapping: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = mappingToBeApplied, requestingUser = requestingUser)).mapTo[GetMappingResponseV2]

            // get XSLT from mapping for the TEI body
            bodyXslt: String <- teiMapping.mappingIri match {
                case OntologyConstants.KnoraBase.TEIMapping =>
                    // standard standoff to TEI conversion

                    // use standard XSLT (built-in)
                    val teiXSLTFile: String = FileUtil.readTextResource("standoffToTEI.xsl")

                    // return the file's content
                    Future(teiXSLTFile)

                case otherMapping => teiMapping.mapping.defaultXSLTransformation match {
                    // custom standoff to TEI conversion

                    case Some(xslTransformationIri) =>
                        // get XSLT for the TEI body.
                        for {
                            xslTransformation: GetXSLTransformationResponseV2 <- (responderManager ? GetXSLTransformationRequestV2(xslTransformationIri, requestingUser = requestingUser)).mapTo[GetXSLTransformationResponseV2]
                        } yield xslTransformation.xslt


                    case None => throw BadRequestException(s"Default XSL Transformation expected for mapping $otherMapping")
                }
            }

            tei = ResourceTEIGetResponseV2(
                header = TEIHeader(
                    headerInfo = headerResource,
                    headerXSLT = headerXSLT,
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
      * Given a set of resource IRIs, checks that they point to Knora resources.
      * If not, throws an exception.
      *
      * @param targetResourceIris the IRIs to be checked.
      * @param requestingUser     the user making the request.
      */
    private def checkResourceIris(targetResourceIris: Set[IRI], requestingUser: UserADM): Future[Unit] = {
        if (targetResourceIris.isEmpty) {
            FastFuture.successful(())
        } else {
            getResourcePreview(targetResourceIris.toSeq, requestingUser).map(_ => ())
        }
    }
}

