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

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{DefaultObjectAccessPermissionsStringForPropertyGetADM, DefaultObjectAccessPermissionsStringResponseADM, PermissionADM, PermissionType}
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectGetRequestADM, ProjectGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2, ResourcesPreviewGetRequestV2}
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.PermissionUtilADM.{EntityPermission, ModifyPermission}
import org.knora.webapi.util.search.gravsearch.GravsearchParser
import org.knora.webapi.util.{KnoraIdUtil, PermissionUtilADM, SmartIri}

import scala.concurrent.Future

/**
  * Handles requests to read and write Knora values.
  */
class ValuesResponderV2 extends Responder {
    // Creates IRIs for new Knora value objects.
    val knoraIdUtil = new KnoraIdUtil

    override def receive: Receive = {
        case createValueRequest: CreateValueRequestV2 => future2Message(sender(), createValueV2(createValueRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Creates a new value in an existing resource.
      *
      * @param createValueRequest the request to create the value.
      * @return a [[CreateValueResponseV2]].
      */
    def createValueV2(createValueRequest: CreateValueRequestV2): Future[CreateValueResponseV2] = {
        def makeTaskFuture(userIri: IRI): Future[CreateValueResponseV2] = {
            for {
                // Convert the submitted value to the internal schema.
                submittedInternalPropertyIri: SmartIri <- Future(createValueRequest.createValue.propertyIri.toOntologySchema(InternalSchema))
                submittedInternalValueContent: ValueContentV2 = createValueRequest.createValue.valueContent.toOntologySchema(InternalSchema)

                // Get ontology information about the submitted property.

                propertyInfoRequestForSubmittedProperty = PropertiesGetRequestV2(
                    propertyIris = Set(submittedInternalPropertyIri),
                    allLanguages = false,
                    requestingUser = createValueRequest.requestingUser
                )

                propertyInfoResponseForSubmittedProperty: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForSubmittedProperty).mapTo[ReadOntologyV2]
                propertyInfoForSubmittedProperty: ReadPropertyInfoV2 = propertyInfoResponseForSubmittedProperty.properties(submittedInternalPropertyIri)

                // Make an adjusted version of the submitted property: if it's a link value property, substitute the
                // corresponding link property, whose objects we will need to query. Get ontology information about the
                // adjusted property.

                adjustedPropertyInfo: ReadPropertyInfoV2 <- if (propertyInfoForSubmittedProperty.isLinkValueProp) {
                    if (submittedInternalValueContent.valueType.toString == OntologyConstants.KnoraBase.LinkValue) {
                        for {
                            internalLinkPropertyIri <- Future(submittedInternalPropertyIri.fromLinkValuePropToLinkProp)

                            propertyInfoRequestForLinkProperty = PropertiesGetRequestV2(
                                propertyIris = Set(internalLinkPropertyIri),
                                allLanguages = false,
                                requestingUser = createValueRequest.requestingUser
                            )

                            linkPropertyInfoResponse: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForLinkProperty).mapTo[ReadOntologyV2]
                        } yield linkPropertyInfoResponse.properties(internalLinkPropertyIri)
                    } else {
                        FastFuture.failed(BadRequestException(s"A value of type <${submittedInternalValueContent.valueType.toOntologySchema(ApiV2WithValueObjects)}> cannot be an object of property <${createValueRequest.createValue.propertyIri}>"))
                    }
                } else {
                    FastFuture.successful(propertyInfoForSubmittedProperty)
                }

                // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
                // so we can see objects that the user doesn't have permission to see.

                resourceInfo: ReadResourceV2 <- getResourceWithPropertyValues(
                    resourceIri = createValueRequest.createValue.resourceIri,
                    propertyInfo = adjustedPropertyInfo,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )

                // Check that the user has permission to modify the resource.

                _ = checkResourcePermission(
                    resourceInfo = resourceInfo,
                    permissionNeeded = ModifyPermission,
                    requestingUser = createValueRequest.requestingUser
                )

                // Get the definition of the resource class.

                classInfoRequest = ClassesGetRequestV2(
                    classIris = Set(resourceInfo.resourceClass),
                    allLanguages = false,
                    requestingUser = createValueRequest.requestingUser
                )

                classInfoResponse: ReadOntologyV2 <- (responderManager ? classInfoRequest).mapTo[ReadOntologyV2]

                // Check that the resource class has a cardinality for the submitted property.

                classInfo: ReadClassInfoV2 = classInfoResponse.classes(resourceInfo.resourceClass)
                cardinalityInfo: Cardinality.KnoraCardinalityInfo = classInfo.allCardinalities.getOrElse(submittedInternalPropertyIri, throw BadRequestException(s"Resource <${createValueRequest.createValue.resourceIri}> belongs to class <${resourceInfo.resourceClass.toOntologySchema(ApiV2WithValueObjects)}>, which has no cardinality for property <${createValueRequest.createValue.propertyIri}>"))

                // Check that the object of the adjusted property (the value to be created, or the target of the link to be created) will have
                // the correct type for the adjusted property's knora-base:objectClassConstraint.

                _ <- checkPropertyObjectClassConstraint(
                    propertyInfo = adjustedPropertyInfo,
                    valueContent = submittedInternalValueContent,
                    requestingUser = createValueRequest.requestingUser
                )

                // Check that the resource class's cardinality for the submitted property allows another value to be added
                // for that property.

                currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values.getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])

                _ = if ((cardinalityInfo.cardinality == Cardinality.MustHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveSome) && currentValuesForProp.isEmpty) {
                    throw InconsistentTriplestoreDataException(s"Resource class <${resourceInfo.resourceClass.toOntologySchema(ApiV2WithValueObjects)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${createValueRequest.createValue.propertyIri}>, but resource <${createValueRequest.createValue.resourceIri}> has no value for that property")
                }

                _ = if (cardinalityInfo.cardinality == Cardinality.MayHaveOne && currentValuesForProp.nonEmpty) {
                    throw BadRequestException(s"Resource class <${resourceInfo.resourceClass.toOntologySchema(ApiV2WithValueObjects)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${createValueRequest.createValue.propertyIri}>, and this does not allow a value to be added for that property to resource <${createValueRequest.createValue.resourceIri}>")
                }

                // Check that the new value would not duplicate an existing value.

                unescapedSubmittedInternalValueContent = submittedInternalValueContent.unescape

                _ = if (currentValuesForProp.exists(currentVal => unescapedSubmittedInternalValueContent.wouldDuplicateOtherValue(currentVal.valueContent))) {
                    throw DuplicateValueException()
                }

                // If this is a text value, check that the resources pointed to by any standoff link tags exist
                // and that the user has permission to see them.

                _ <- submittedInternalValueContent match {
                    case textValueContent: TextValueContentV2 => checkStandoffLinkTargets(textValueContent, createValueRequest.requestingUser)
                    case _ => FastFuture.successful(())
                }

                // Get the default permissions for the new value. TODO: let the user submit the permissions they want to use.

                defaultObjectAccessPermissionsResponse: DefaultObjectAccessPermissionsStringResponseADM <- {
                    responderManager ? DefaultObjectAccessPermissionsStringForPropertyGetADM(
                        projectIri = resourceInfo.attachedToProject,
                        resourceClassIri = resourceInfo.resourceClass.toString,
                        propertyIri = submittedInternalPropertyIri.toString,
                        targetUser = createValueRequest.requestingUser,
                        requestingUser = KnoraSystemInstances.Users.SystemUser
                    )
                }.mapTo[DefaultObjectAccessPermissionsStringResponseADM]

                newValuePermissionLiteral: String = defaultObjectAccessPermissionsResponse.permissionLiteral

                // Get information about the project that the resource is in, so we know which named graph to put the new value in.
                projectInfo: ProjectGetResponseADM <- {
                    responderManager ? ProjectGetRequestADM(maybeIri = Some(resourceInfo.attachedToProject.toString), requestingUser = createValueRequest.requestingUser)
                }.mapTo[ProjectGetResponseADM]

                newValueNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(projectInfo.project)

                // Create the new value.

                unverifiedValue <- createValueV2AfterChecks(
                    dataNamedGraph = newValueNamedGraph,
                    projectIri = projectInfo.project.id,
                    resourceInfo = resourceInfo,
                    propertyIri = submittedInternalPropertyIri,
                    value = submittedInternalValueContent,
                    valueCreator = createValueRequest.requestingUser.id,
                    valuePermissions = newValuePermissionLiteral,
                    requestingUser = createValueRequest.requestingUser
                )

                // Check that the value was written correctly to the triplestore.

                _ <- verifyValue(
                    resourceIri = createValueRequest.createValue.resourceIri,
                    propertyIri = submittedInternalPropertyIri,
                    unverifiedValue = unverifiedValue,
                    requestingUser = createValueRequest.requestingUser
                )

            } yield CreateValueResponseV2(valueIri = unverifiedValue.newValueIri)
        }

        for {
            // Don't allow anonymous users to create values.
            userIri <- Future {
                if (createValueRequest.requestingUser.isAnonymousUser) {
                    throw ForbiddenException("Anonymous users aren't allowed to create values")
                } else {
                    createValueRequest.requestingUser.id
                }
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- IriLocker.runWithIriLock(
                createValueRequest.apiRequestID,
                createValueRequest.createValue.resourceIri,
                () => makeTaskFuture(userIri)
            )
        } yield taskResult
    }

    /**
      * Given a text value, checks whether the targets of the value's standoff link tags exist and are Knora resources.
      * If not, throws an exception.
      *
      * @param textValueContent the text value.
      * @param requestingUser   the user making the request.
      */
    private def checkStandoffLinkTargets(textValueContent: TextValueContentV2, requestingUser: UserADM): Future[Unit] = {
        val targetResourceIris: Seq[IRI] = textValueContent.standoffLinkTagTargetResourceIris.toSeq

        if (targetResourceIris.isEmpty) {
            FastFuture.successful(())
        } else {
            for {
                resourcePreviewRequest <- FastFuture.successful(
                    ResourcesPreviewGetRequestV2(
                        resourceIris = targetResourceIris,
                        requestingUser = requestingUser
                    )
                )

                // If any of the resources are not found, or the user doesn't have permission to see them, this will throw an exception.

                _ <- (responderManager ? resourcePreviewRequest).mapTo[ReadResourcesSequenceV2]
            } yield ()
        }
    }

    /**
      * Returns a resource's metadata and its values, if any, for the specified property. If the property is a link property, the result
      * will contain any objects of the corresponding link value property (link values), as well as metadata for any resources that the link property points to.
      * If the property's object type is `knora-base:TextValue`, the result will contain any objects of the property (text values), as well metadata
      * for any resources that are objects of `knora-base:hasStandoffLinkTo`.
      *
      * @param resourceIri    the resource IRI.
      * @param propertyInfo   the property definition (in the internal schema). If the caller wants to query a link, this must be the link property,
      *                       not the link value property.
      * @param requestingUser the user making the request.
      * @return a [[ReadResourceV2]] containing only the resource's metadata and its values for the specified property.
      */
    private def getResourceWithPropertyValues(resourceIri: IRI, propertyInfo: ReadPropertyInfoV2, requestingUser: UserADM): Future[ReadResourceV2] = {
        // TODO: when text values in Gravsearch query results are shortened, make a way for this query to get the complete value.

        for {
            // Get the property's object class constraint.
            objectClassConstraint: SmartIri <- Future(propertyInfo.entityInfoContent.requireIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri, throw InconsistentTriplestoreDataException(s"Property ${propertyInfo.entityInfoContent.propertyIri} has no knora-base:objectClassConstraint")))

            // If the property points to a text value, also query the resource's standoff links.
            maybeStandoffLinkToPropertyIri: Option[SmartIri] = if (objectClassConstraint.toString == OntologyConstants.KnoraBase.TextValue) {
                Some(OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri)
            } else {
                None
            }

            // Convert the property IRIs to be queried to the API v2 complex schema for Gravsearch.
            propertyIrisForGravsearchQuery: Seq[SmartIri] = (Seq(propertyInfo.entityInfoContent.propertyIri) ++ maybeStandoffLinkToPropertyIri).map(_.toOntologySchema(ApiV2WithValueObjects))

            // Make a Gravsearch query from a template.
            gravsearchQuery: String = queries.gravsearch.txt.getResourceWithSpecifiedProperties(
                resourceIri = resourceIri,
                propertyIris = propertyIrisForGravsearchQuery
            ).toString()

            // Run the query.

            parsedGravsearchQuery <- FastFuture.successful(GravsearchParser.parseQuery(gravsearchQuery))
            searchResponse <- (responderManager ? GravsearchRequestV2(parsedGravsearchQuery, requestingUser)).mapTo[ReadResourcesSequenceV2]

            // Get the resource from the response.
            resource = resourcesSequenceToResource(
                requestedResourceIri = resourceIri,
                readResourcesSequence = searchResponse,
                requestingUser = requestingUser
            )
        } yield resource
    }

    /**
      * Verifies that a value was written correctly to the triplestore.
      *
      * @param resourceIri     the IRI of the resource that the value belongs to.
      * @param propertyIri     the IRI of the resource property that points to the value (in the internal schema).
      * @param unverifiedValue the value that should have been written to the triplestore.
      * @param requestingUser  the user making the request.
      */
    private def verifyValue(resourceIri: IRI, propertyIri: SmartIri, unverifiedValue: UnverifiedValueV2, requestingUser: UserADM): Future[Unit] = {
        for {
            gravsearchQuery: String <- Future(queries.gravsearch.txt.getResourceWithSpecifiedProperties(
                resourceIri = resourceIri,
                propertyIris = Seq(propertyIri.toOntologySchema(ApiV2WithValueObjects))
            ).toString())

            parsedGravsearchQuery <- FastFuture.successful(GravsearchParser.parseQuery(gravsearchQuery))
            searchResponse <- (responderManager ? GravsearchRequestV2(parsedGravsearchQuery, requestingUser)).mapTo[ReadResourcesSequenceV2]

            resource = resourcesSequenceToResource(
                requestedResourceIri = resourceIri,
                readResourcesSequence = searchResponse,
                requestingUser = requestingUser
            )

            propertyValues = resource.values.getOrElse(propertyIri, throw UpdateNotPerformedException())
            valueInTriplestore: ReadValueV2 = propertyValues.find(_.valueIri == unverifiedValue.newValueIri).getOrElse(throw UpdateNotPerformedException())

            _ = if (!unverifiedValue.value.wouldDuplicateCurrentVersion(valueInTriplestore.valueContent)) {
                /*
                import org.knora.webapi.util.MessageUtil
                println("==============================")
                println("Submitted value:")
                println(MessageUtil.toSource(unverifiedValue.value))
                println
                println("==============================")
                println("Saved value:")
                println(MessageUtil.toSource(valueInTriplestore.valueContent))
                */
                throw AssertionException(s"The value saved as ${unverifiedValue.newValueIri} is not the same as the one that was submitted")
            }
        } yield ()
    }

    /**
      * Checks that a user has the specified permission on a resource.
      *
      * @param resourceInfo   the resource to be modified.
      * @param requestingUser the requesting user.
      */
    private def checkResourcePermission(resourceInfo: ReadResourceV2, permissionNeeded: EntityPermission, requestingUser: UserADM): Unit = {
        val maybeUserPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionADM(
            entityIri = resourceInfo.resourceIri,
            entityCreator = resourceInfo.attachedToUser,
            entityProject = resourceInfo.attachedToProject,
            entityPermissionLiteral = resourceInfo.permissions,
            requestingUser = requestingUser
        )

        val hasRequiredPermission: Boolean = maybeUserPermission match {
            case Some(userPermission: EntityPermission) => userPermission >= permissionNeeded
            case None => false
        }

        if (!hasRequiredPermission) {
            throw ForbiddenException(s"User ${requestingUser.email} does not have permission <$permissionNeeded> on resource <${resourceInfo.resourceIri}>")
        }
    }

    /**
      * Checks that a link value points to a resource with the correct type for the link property's object class constraint.
      *
      * @param linkPropertyIri       the IRI of the link property.
      * @param objectClassConstraint the object class constraint of the link property.
      * @param linkValueContent      the link value.
      * @param requestingUser        the user making the request.
      */
    private def checkLinkPropertyObjectClassConstraint(linkPropertyIri: SmartIri, objectClassConstraint: SmartIri, linkValueContent: LinkValueContentV2, requestingUser: UserADM): Future[Unit] = {
        for {
            // Get a preview of the target resource, because we only need to find out its class and whether the user has permission to view it.

            resourcePreviewRequest <- FastFuture.successful(
                ResourcesPreviewGetRequestV2(
                    resourceIris = Seq(linkValueContent.target),
                    requestingUser = requestingUser
                )
            )

            resourcePreviewResponse <- (responderManager ? resourcePreviewRequest).mapTo[ReadResourcesSequenceV2]

            // If we get a resource, we know the user has permission to view it.

            resource: ReadResourceV2 = resourcesSequenceToResource(
                requestedResourceIri = linkValueContent.target,
                readResourcesSequence = resourcePreviewResponse,
                requestingUser = requestingUser
            )

            // Ask the ontology responder whether the resource's class is a subclass of the link property's object class constraint.

            subClassRequest = CheckSubClassRequestV2(
                subClassIri = resource.resourceClass,
                superClassIri = objectClassConstraint,
                requestingUser = requestingUser
            )

            subClassResponse <- (responderManager ? subClassRequest).mapTo[CheckSubClassResponseV2]

            // If it isn't, throw an exception.
            _ = if (!subClassResponse.isSubClass) {
                throw OntologyConstraintException(s"Resource <${linkValueContent.target}> cannot be the target of property <$linkPropertyIri>, because it is not a member of class <$objectClassConstraint>")
            }
        } yield ()
    }

    /**
      * Checks that a non-link value has the correct type for a property's object class constraint.
      *
      * @param propertyIri           the IRI of the property that should point to the value.
      * @param objectClassConstraint the property's object class constraint.
      * @param valueContent          the value.
      * @param requestingUser        the user making the request.
      */
    private def checkNonLinkPropertyObjectClassConstraint(propertyIri: SmartIri, objectClassConstraint: SmartIri, valueContent: ValueContentV2, requestingUser: UserADM): Future[Unit] = {
        // Is the value type the same as the property's object class constraint?
        if (objectClassConstraint == valueContent.valueType) {
            // Yes. Nothing more to do here.
            Future.successful(())
        } else {
            // No. Ask the ontology responder whether it's a subclass of the property's object class constraint.
            for {
                subClassRequest <- FastFuture.successful(CheckSubClassRequestV2(
                    subClassIri = valueContent.valueType,
                    superClassIri = objectClassConstraint,
                    requestingUser = requestingUser
                ))

                subClassResponse <- (responderManager ? subClassRequest).mapTo[CheckSubClassResponseV2]

                // If it isn't, throw an exception.
                _ = if (!subClassResponse.isSubClass) {
                    throw OntologyConstraintException(s"A value of type <${valueContent.valueType}> cannot be the target of property <$propertyIri>, because it is not a member of class <$objectClassConstraint>")
                }

            } yield ()
        }
    }

    /**
      * Checks that a value to be updated has the correct type for the `knora-base:objectClassConstraint` of
      * the property that is supposed to point to it.
      *
      * @param propertyInfo   the property whose object class constraint is to be checked. If the value is a link value, this is the link property.
      * @param valueContent   the value to be updated.
      * @param requestingUser the user making the request.
      */
    private def checkPropertyObjectClassConstraint(propertyInfo: ReadPropertyInfoV2, valueContent: ValueContentV2, requestingUser: UserADM): Future[Unit] = {
        for {
            objectClassConstraint: SmartIri <- Future(propertyInfo.entityInfoContent.requireIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri, throw InconsistentTriplestoreDataException(s"Property ${propertyInfo.entityInfoContent.propertyIri} has no knora-base:objectClassConstraint")))

            result: Unit <- valueContent match {
                case linkValueContent: LinkValueContentV2 =>
                    // We're creating a link. Check that the user has permission to view the target resource, and that the target resource has the correct type.
                    checkLinkPropertyObjectClassConstraint(
                        linkPropertyIri = propertyInfo.entityInfoContent.propertyIri,
                        objectClassConstraint = objectClassConstraint,
                        linkValueContent = linkValueContent,
                        requestingUser = requestingUser
                    )

                case otherValue =>
                    // We're creating an ordinary value. Check that its type is valid for the property's object class constraint.
                    checkNonLinkPropertyObjectClassConstraint(
                        propertyIri = propertyInfo.entityInfoContent.propertyIri,
                        objectClassConstraint = objectClassConstraint,
                        valueContent = otherValue,
                        requestingUser = requestingUser
                    )

            }
        } yield result
    }

    /**
      * Checks that a [[ReadResourcesSequenceV2]] contains exactly one resource, and returns that resource. If the resource
      * is not present, or if it's `ForbiddenResource`, throws an exception.
      *
      * @param requestedResourceIri  the IRI of the expected resource.
      * @param readResourcesSequence a [[ReadResourcesSequenceV2]] that should contain the resource.
      * @param requestingUser        the user making the request.
      * @return the resource.
      */
    private def resourcesSequenceToResource(requestedResourceIri: IRI, readResourcesSequence: ReadResourcesSequenceV2, requestingUser: UserADM): ReadResourceV2 = {
        if (readResourcesSequence.numberOfResources == 0) {
            throw AssertionException(s"Expected one resource, <$requestedResourceIri>, but no resources were returned")
        }

        if (readResourcesSequence.numberOfResources > 1) {
            throw AssertionException(s"More than one resource returned with IRI <$requestedResourceIri>")
        }

        val resourceInfo = readResourcesSequence.resources.head

        if (resourceInfo.resourceIri == SearchResponderV2Constants.forbiddenResourceIri) {
            throw ForbiddenException(s"User ${requestingUser.email} does not have permission to view resource <${resourceInfo.resourceIri}>")
        }

        resourceInfo
    }

    /**
      * Creates a new value (either an ordinary value or a link), using an existing transaction, assuming that
      * pre-update checks have already been done.
      *
      * @param dataNamedGraph   the named graph in which the value is to be created.
      * @param projectIri       the IRI of the project in which to create the value.
      * @param resourceInfo     information about the the resource in which to create the value.
      * @param propertyIri      the IRI of the property that will point from the resource to the value.
      * @param value            the value to create.
      * @param valueCreator     the IRI of the new value's owner.
      * @param valuePermissions the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
      * @param requestingUser   the user making the request.
      * @return an [[UnverifiedValueV2]].
      */
    private def createValueV2AfterChecks(dataNamedGraph: IRI,
                                         projectIri: IRI,
                                         resourceInfo: ReadResourceV2,
                                         propertyIri: SmartIri,
                                         value: ValueContentV2,
                                         valueCreator: IRI,
                                         valuePermissions: String,
                                         requestingUser: UserADM): Future[UnverifiedValueV2] = {
        value match {
            case linkValueContent: LinkValueContentV2 =>
                createLinkValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = propertyIri,
                    linkValueContent = linkValueContent,
                    valueCreator = valueCreator,
                    valuePermissions = valuePermissions,
                    requestingUser = requestingUser
                )

            case ordinaryValueContent =>
                createOrdinaryValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = propertyIri,
                    value = ordinaryValueContent,
                    valueCreator = valueCreator,
                    valuePermissions = valuePermissions,
                    requestingUser = requestingUser
                )
        }
    }

    /**
      * Creates a link, using an existing transaction, assuming that pre-update checks have already been done.
      *
      * @param dataNamedGraph   the named graph in which the link is to be created.
      * @param resourceInfo     information about the the resource in which to create the value.
      * @param propertyIri      the link property.
      * @param linkValueContent a [[LinkValueContentV2]] specifying the target resource.
      * @param valueCreator     the IRI of the new link value's owner.
      * @param valuePermissions the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param requestingUser   the user making the request.
      * @return an [[UnverifiedValueV2]].
      */
    private def createLinkValueV2AfterChecks(dataNamedGraph: IRI,
                                             resourceInfo: ReadResourceV2,
                                             propertyIri: SmartIri,
                                             linkValueContent: LinkValueContentV2,
                                             valueCreator: IRI,
                                             valuePermissions: String,
                                             requestingUser: UserADM): Future[UnverifiedValueV2] = {
        for {
            sparqlTemplateLinkUpdate <- Future(incrementLinkValue(
                sourceResourceInfo = resourceInfo,
                linkPropertyIri = propertyIri,
                targetResourceIri = linkValueContent.target,
                valueCreator = valueCreator,
                valuePermissions = valuePermissions,
                requestingUser = requestingUser
            ))

            currentTime: String = Instant.now.toString

            // Generate a SPARQL update string.
            //resourceIndex = 0 because this method isn't used when creating multiple resources
            sparqlUpdate = queries.sparql.v2.txt.createLink(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                resourceIri = resourceInfo.resourceIri,
                linkUpdate = sparqlTemplateLinkUpdate,
                currentTime = currentTime,
                maybeComment = linkValueContent.comment
            ).toString()

            /*
            _ = println("================ Create link ===============")
            _ = println(sparqlUpdate)
            _ = println("=============================================")
            */

            // Do the update.
            _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
        } yield UnverifiedValueV2(
            newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
            value = linkValueContent.unescape
        )
    }

    /**
      * Creates an ordinary value (i.e. not a link), using an existing transaction, assuming that pre-update checks have already been done.
      *
      * @param resourceInfo     information about the the resource in which to create the value.
      * @param propertyIri      the property that should point to the value.
      * @param value            an [[ValueContentV2]] describing the value.
      * @param valueCreator     the IRI of the new value's owner.
      * @param valuePermissions the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
      * @param requestingUser   the user making the request.
      * @return an [[UnverifiedValueV2]].
      */
    private def createOrdinaryValueV2AfterChecks(dataNamedGraph: IRI,
                                                 resourceInfo: ReadResourceV2,
                                                 propertyIri: SmartIri,
                                                 value: ValueContentV2,
                                                 valueCreator: IRI,
                                                 valuePermissions: String,
                                                 requestingUser: UserADM): Future[UnverifiedValueV2] = {
        for {
            // Generate an IRI for the new value.
            newValueIri <- FastFuture.successful(knoraIdUtil.makeRandomValueIri(resourceInfo.resourceIri))
            currentTime: String = Instant.now.toString

            // If we're creating a text value, update direct links and LinkValues for any resource references in standoff.
            standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] = value match {
                case textValueContent: TextValueContentV2 =>
                    // Construct a SparqlTemplateLinkUpdate for each reference that was added.
                    textValueContent.standoffLinkTagTargetResourceIris.map {
                        targetResourceIri: IRI =>
                            incrementLinkValue(
                                sourceResourceInfo = resourceInfo,
                                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                                targetResourceIri = targetResourceIri,
                                valueCreator = OntologyConstants.KnoraBase.SystemUser,
                                valuePermissions = standoffLinkValuePermissions,
                                requestingUser = requestingUser
                            )
                    }.toVector

                case _ => Vector.empty[SparqlTemplateLinkUpdate]
            }

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v2.txt.createValue(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                resourceIri = resourceInfo.resourceIri,
                propertyIri = propertyIri,
                newValueIri = newValueIri,
                valueTypeIri = value.valueType,
                value = value,
                linkUpdates = standoffLinkUpdates,
                valueCreator = valueCreator,
                valuePermissions = valuePermissions,
                currentTime = currentTime
            ).toString()

            /*
            _ = println("================ Create value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
            */

            // Do the update.
            _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
        } yield UnverifiedValueV2(
            newValueIri = newValueIri,
            value = value.unescape
        )
    }

    /**
      * Given a [[ReadResourceV2]], finds a link that uses the specified property and points to the specified target
      * resource.
      *
      * @param sourceResourceInfo a [[ReadResourceV2]] describing the source of the link.
      * @param linkPropertyIri    the IRI of the link property.
      * @param targetResourceIri  the IRI of the target resource.
      * @return a [[ReadValueV2]] describing the link value, if found.
      */
    private def findLinkValue(sourceResourceInfo: ReadResourceV2,
                              linkPropertyIri: SmartIri,
                              targetResourceIri: IRI): Option[ReadValueV2] = {
        val linkValueProperty = linkPropertyIri.fromLinkPropToLinkValueProp

        sourceResourceInfo.values.get(linkValueProperty).flatMap {
            linkValueInfos: Seq[ReadValueV2] =>
                linkValueInfos.find {
                    linkValueInfo: ReadValueV2 =>
                        val linkValueContent = linkValueInfo.valueContent match {
                            case linkValue: LinkValueContentV2 => linkValue
                            case _ => throw AssertionException(s"Expected a LinkValueContentV2: $linkValueInfo")
                        }

                        linkValueContent.target == targetResourceIri
                }

                linkValueInfos.headOption
        }
    }

    /**
      * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to create a `LinkValue` or to
      * increment the reference count of an existing `LinkValue`. This happens in two cases:
      *
      *  - When the user creates a link. In this case, neither the link nor the `LinkValue` exist yet. The
      * [[SparqlTemplateLinkUpdate]] will specify that the link should be created, and that the `LinkValue` should be
      * created with a reference count of 1.
      *  - When a text value is updated so that its standoff markup refers to a resource that it did not previously
      * refer to. Here there are two possibilities:
      *    - If there is currently a `knora-base:hasStandoffLinkTo` link between the source and target resources, with a
      * corresponding `LinkValue`, a new version of the `LinkValue` will be made, with an incremented reference count.
      *    - If that link and `LinkValue` don't yet exist, they will be created, and the `LinkValue` will be given
      * a reference count of 1.
      *
      * @param sourceResourceInfo information about the source resource.
      * @param linkPropertyIri    the IRI of the property that links the source resource to the target resource.
      * @param targetResourceIri  the IRI of the target resource.
      * @param valueCreator       the IRI of the new link value's owner.
      * @param valuePermissions   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param requestingUser     the user making the request.
      * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
      */
    private def incrementLinkValue(sourceResourceInfo: ReadResourceV2,
                                   linkPropertyIri: SmartIri,
                                   targetResourceIri: IRI,
                                   valueCreator: IRI,
                                   valuePermissions: String,
                                   requestingUser: UserADM): SparqlTemplateLinkUpdate = {
        // Check whether a LinkValue already exists for this link.
        val maybeLinkValueInfo = findLinkValue(
            sourceResourceInfo = sourceResourceInfo,
            linkPropertyIri = linkPropertyIri,
            targetResourceIri = targetResourceIri
        )

        // Generate an IRI for the new LinkValue.
        val newLinkValueIri = knoraIdUtil.makeRandomValueIri(sourceResourceInfo.resourceIri)

        maybeLinkValueInfo match {
            case Some(linkValueInfo) =>
                // There's already a LinkValue for links between these two resources. Increment
                // its reference count.
                val currentReferenceCount = linkValueInfo.valueHasRefCount.getOrElse(throw AssertionException(s"Link value <${linkValueInfo.valueIri}> has no reference count"))
                val newReferenceCount = currentReferenceCount + 1

                SparqlTemplateLinkUpdate(
                    linkPropertyIri = linkPropertyIri,
                    directLinkExists = true,
                    insertDirectLink = false,
                    deleteDirectLink = false,
                    linkValueExists = true,
                    linkTargetExists = true,
                    newLinkValueIri = newLinkValueIri,
                    linkTargetIri = targetResourceIri,
                    currentReferenceCount = currentReferenceCount,
                    newReferenceCount = newReferenceCount,
                    newLinkValueCreator = valueCreator,
                    newLinkValuePermissions = valuePermissions
                )

            case None =>
                // There's no LinkValue for links between these two resources, so create one, and give it
                // a reference count of 1.
                SparqlTemplateLinkUpdate(
                    linkPropertyIri = linkPropertyIri,
                    directLinkExists = false,
                    insertDirectLink = true,
                    deleteDirectLink = false,
                    linkValueExists = false,
                    linkTargetExists = true,
                    newLinkValueIri = newLinkValueIri,
                    linkTargetIri = targetResourceIri,
                    currentReferenceCount = 0,
                    newReferenceCount = 1,
                    newLinkValueCreator = valueCreator,
                    newLinkValuePermissions = valuePermissions
                )
        }
    }

    /**
      * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to decrement the reference count
      * of a `LinkValue`. This happens in two cases:
      *
      *  - When the user deletes (or changes) a user-created link. In this case, the current reference count will be 1.
      * The existing link will be removed. A new version of the `LinkValue` be made with a reference count of 0, and
      * will be marked as deleted.
      *  - When a resource reference is removed from standoff markup on a text value, so that the text value no longer
      * contains any references to that target resource. In this case, a new version of the `LinkValue` will be
      * made, with a decremented reference count. If the new reference count is 0, the link will be removed and the
      * `LinkValue` will be marked as deleted.
      *
      * @param sourceResourceInfo information about the source resource.
      * @param linkPropertyIri    the IRI of the property that links the source resource to the target resource.
      * @param targetResourceIri  the IRI of the target resource.
      * @param valueCreator       the IRI of the new link value's owner.
      * @param valuePermissions   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param requestingUser     the user making the request.
      * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
      */
    private def decrementLinkValue(sourceResourceInfo: ReadResourceV2,
                                   linkPropertyIri: SmartIri,
                                   targetResourceIri: IRI,
                                   valueCreator: IRI,
                                   valuePermissions: String,
                                   requestingUser: UserADM): SparqlTemplateLinkUpdate = {

        // Check whether a LinkValue already exists for this link.
        val maybeLinkValueInfo = findLinkValue(
            sourceResourceInfo = sourceResourceInfo,
            linkPropertyIri = linkPropertyIri,
            targetResourceIri = targetResourceIri
        )

        // Did we find it?
        maybeLinkValueInfo match {
            case Some(linkValueInfo) =>
                // Yes. Make a SparqlTemplateLinkUpdate.

                // Decrement the LinkValue's reference count.
                val currentReferenceCount = linkValueInfo.valueHasRefCount.getOrElse(throw AssertionException(s"Link value <${linkValueInfo.valueIri}> has no reference count"))
                val newReferenceCount = currentReferenceCount - 1

                // If the new reference count is 0, specify that the direct link between the source and target
                // resources should be removed.
                val deleteDirectLink = newReferenceCount == 0

                // Generate an IRI for the new LinkValue.
                val newLinkValueIri = knoraIdUtil.makeRandomValueIri(sourceResourceInfo.resourceIri)

                SparqlTemplateLinkUpdate(
                    linkPropertyIri = linkPropertyIri,
                    directLinkExists = true,
                    insertDirectLink = false,
                    deleteDirectLink = deleteDirectLink,
                    linkValueExists = true,
                    linkTargetExists = true,
                    newLinkValueIri = newLinkValueIri,
                    linkTargetIri = targetResourceIri,
                    currentReferenceCount = currentReferenceCount,
                    newReferenceCount = newReferenceCount,
                    newLinkValueCreator = valueCreator,
                    newLinkValuePermissions = valuePermissions
                )

            case None =>
                // We didn't find the LinkValue. This shouldn't happen.
                throw InconsistentTriplestoreDataException(s"There should be a knora-base:LinkValue describing a direct link from resource <${sourceResourceInfo.resourceIri}> to resource <$targetResourceIri> using property <$linkPropertyIri>, but it seems to be missing")
        }
    }

    /**
      * The permissions that are granted by every `knora-base:LinkValue` describing a standoff link.
      */
    lazy val standoffLinkValuePermissions: String = {
        val permissions: Set[PermissionADM] = Set(
            PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.SystemUser),
            PermissionADM.viewPermission(OntologyConstants.KnoraBase.UnknownUser)
        )

        PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)
    }
}
