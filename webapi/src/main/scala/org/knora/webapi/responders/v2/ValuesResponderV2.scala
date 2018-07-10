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

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2, ResourcesPreviewGetRequestV2}
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.PermissionUtilADM.{EntityPermission, ModifyPermission}
import org.knora.webapi.util.search.gravsearch.GravsearchParser
import org.knora.webapi.util.{PermissionUtilADM, SmartIri}

import scala.concurrent.Future

/**
  * Handles requests to read and write Knora values.
  */
class ValuesResponderV2 extends Responder {
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
                // Get the resource's metadata and the values, if any, that the resource already has for the property.
                // Do this as the system user, so we can see values that the user doesn't have permission to see.

                resourceInfo: ReadResourceV2 <- getResourceWithPropertyValues(
                    resourceIri = createValueRequest.createValue.resourceIri,
                    propertyIri = createValueRequest.createValue.propertyIri,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )

                internalPropertyIri: SmartIri = createValueRequest.createValue.propertyIri.toOntologySchema(InternalSchema)
                internalValueContent: ValueContentV2 = createValueRequest.createValue.valueContent.toOntologySchema(InternalSchema)

                // Check that the user has permission to modify the resource.

                _ = checkResourcePermission(
                    resourceInfo = resourceInfo,
                    permissionNeeded = ModifyPermission,
                    requestingUser = createValueRequest.requestingUser
                )

                // Get ontology information about the property. If the property is a link value property, get
                // information about the link property rather than about the link value property.

                internalPropertyIriForPropertyInfo: SmartIri = internalValueContent match {
                    case _: LinkValueContentV2 => internalPropertyIri.fromLinkValuePropToLinkProp
                    case _ => internalPropertyIri
                }

                propertyInfoRequest = PropertiesGetRequestV2(
                    propertyIris = Set(internalPropertyIriForPropertyInfo),
                    allLanguages = false,
                    requestingUser = createValueRequest.requestingUser
                )

                propertyInfoResponse: ReadOntologyV2 <- (responderManager ? propertyInfoRequest).mapTo[ReadOntologyV2]

                propertyInfo: ReadPropertyInfoV2 = propertyInfoResponse.properties(internalPropertyIri)

                // Check that the object of the property (the value to be created, or the target of the link to be created) will have
                // the correct type for the property's knora-base:objectClassConstraint.

                _ <- checkPropertyObjectClassConstraint(
                    propertyInfo = propertyInfo,
                    valueContent = internalValueContent,
                    requestingUser = createValueRequest.requestingUser
                )

                // Get the resource class's cardinality for the submitted property.

                classInfoRequest = ClassesGetRequestV2(
                    classIris = Set(resourceInfo.resourceClass),
                    allLanguages = false,
                    requestingUser = createValueRequest.requestingUser
                )

                classInfoResponse: ReadOntologyV2 <- (responderManager ? classInfoRequest).mapTo[ReadOntologyV2]

                // Check that the resource class has a cardinality for the property.

                classInfo: ReadClassInfoV2 = classInfoResponse.classes(resourceInfo.resourceClass)
                cardinalityInfo: Cardinality.KnoraCardinalityInfo = classInfo.allCardinalities.getOrElse(internalPropertyIri, throw BadRequestException(s"Resource <${createValueRequest.createValue.resourceIri}> belongs to class <${resourceInfo.resourceClass.toOntologySchema(ApiV2WithValueObjects)}>, which has no cardinality for property <${createValueRequest.createValue.propertyIri}>"))

                // Check that the resource class's cardinality for the property allows another value to be added
                // for that property.

                currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values.getOrElse(internalPropertyIri, Seq.empty[ReadValueV2])

                _ = if ((cardinalityInfo.cardinality == Cardinality.MayHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveOne) && currentValuesForProp.nonEmpty) {
                    throw BadRequestException(s"Resource class <${resourceInfo.resourceClass.toOntologySchema(ApiV2WithValueObjects)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${createValueRequest.createValue.propertyIri}>, and this does not allow a value to be added for that property to resource <${createValueRequest.createValue.resourceIri}>")
                }

                // Check that the new value would not duplicate an existing value.
                _ = if (currentValuesForProp.exists(currentVal => internalValueContent.wouldDuplicateOtherValue(currentVal.valueContent))) {
                    throw DuplicateValueException()
                }


            } yield CreateValueResponseV2(valueIri = "")
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
            throw NotFoundException(s"Resource <$requestedResourceIri> not found (it may have been deleted, or you might not have permission to see it)")
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
      * Returns a resource's metadata and its values for the specified property.
      *
      * @param resourceIri    the resource IRI.
      * @param propertyIri    the property IRI.
      * @param requestingUser the user making the request.
      * @return a [[ReadResourceV2]] containing only the resource's metadata and its values for the specified property.
      */
    private def getResourceWithPropertyValues(resourceIri: IRI, propertyIri: SmartIri, requestingUser: UserADM): Future[ReadResourceV2] = {
        // TODO: when text values in Gravsearch query results are shortened, make a way for this query to get the complete value.
        val gravsearchQuery =
            s"""
               |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
               |
               |CONSTRUCT {
               |  ?resource knora-api:isMainResource true .
               |  ?resource <$propertyIri> ?propertyValue .
               |} WHERE {
               |  BIND(<$resourceIri> AS ?resource)
               |  ?resource a knora-api:Resource .
               |  ?resource <$propertyIri> ?propertyValue .
               |}
            """.stripMargin

        for {
            parsedGravsearchQuery <- FastFuture.successful(GravsearchParser.parseQuery(gravsearchQuery))
            searchResponse <- (responderManager ? GravsearchRequestV2(parsedGravsearchQuery, requestingUser)).mapTo[ReadResourcesSequenceV2]

            resource = resourcesSequenceToResource(
                requestedResourceIri = resourceIri,
                readResourcesSequence = searchResponse,
                requestingUser = requestingUser
            )
        } yield resource
    }
}
