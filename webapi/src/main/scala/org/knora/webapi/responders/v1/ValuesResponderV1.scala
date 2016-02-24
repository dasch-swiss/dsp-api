/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.ontologymessages._
import org.knora.webapi.messages.v1respondermessages.resourcemessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages._
import org.knora.webapi.responders.ResourceLocker
import org.knora.webapi.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIriUtil

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future

/**
  * Updates Knora values.
  */
class ValuesResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora value objects.
    val knoraIriUtil = new KnoraIriUtil

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[ValuesResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case ValueGetRequestV1(valueIri, userProfile) => future2Message(sender(), getValueResponseV1(valueIri, userProfile), log)
        case LinkValueGetRequestV1(subjectIri, predicateIri, objectIri, userProfile) => future2Message(sender(), getLinkValue(subjectIri, predicateIri, objectIri, userProfile), log)
        case versionHistoryRequest: ValueVersionHistoryGetRequestV1 => future2Message(sender(), getValueVersionHistoryResponseV1(versionHistoryRequest), log)
        case createValueRequest: CreateValueRequestV1 => future2Message(sender(), createValueV1(createValueRequest), log)
        case changeValueRequest: ChangeValueRequestV1 => future2Message(sender(), changeValueV1(changeValueRequest), log)
        case changeCommentRequest: ChangeCommentRequestV1 => future2Message(sender(), changeCommentV1(changeCommentRequest), log)
        case deleteValueRequest: DeleteValueRequestV1 => future2Message(sender(), deleteValueV1(deleteValueRequest), log)
        case createMultipleValuesRequest: CreateMultipleValuesRequestV1 => future2Message(sender(), createMultipleValuesV1(createMultipleValuesRequest), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete API responses.

    /**
      * Queries a `knora-base:Value` and returns a [[ValueGetResponseV1]] describing it.
      *
      * @param valueIri    the IRI of the value to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[ValueGetResponseV1]].
      */
    private def getValueResponseV1(valueIri: IRI, userProfile: UserProfileV1): Future[ValueGetResponseV1] = {
        for {
            maybeValueQueryResult <- findValue(valueIri, userProfile)

            response = maybeValueQueryResult match {
                case Some(valueQueryResult) =>
                    ValueGetResponseV1(
                        valuetype = valueQueryResult.value.valueTypeIri,
                        rights = valueQueryResult.permissionCode,
                        value = valueQueryResult.value,
                        userdata = userProfile.userData
                    )

                case None =>
                    throw NotFoundException(s"Value $valueIri not found (it may have been deleted)")
            }
        } yield response
    }

    /**
      * Creates a new value of a resource property (as opposed to a new version of an existing value).
      *
      * @param createValueRequest the request message.
      * @return a [[CreateValueResponseV1]] if the update was successful.
      */
    private def createValueV1(createValueRequest: CreateValueRequestV1): Future[CreateValueResponseV1] = {
        /**
          * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
          * called by [[ResourceLocker]] once it has acquired an update lock on the resource.
          *
          * @param userIri the IRI of the user making the request.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI): Future[CreateValueResponseV1] = for {
        // Check that the submitted value has the correct type for the property.

            entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
                propertyIris = Set(createValueRequest.propertyIri),
                userProfile = createValueRequest.userProfile
            )).mapTo[EntityInfoGetResponseV1]

            propertyInfo = entityInfoResponse.propertyEntityInfoMap(createValueRequest.propertyIri)
            propertyObjectClassConstraint = propertyInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse {
                throw InconsistentTriplestoreDataException(s"Property ${createValueRequest.propertyIri} has no knora-base:objectClassConstraint")
            }

            // Check that the object of the property (the value to be created, or the target of the link to be created) will have
            // the correct type for the property's knora-base:objectClassConstraint.
            _ <- checkPropertyObjectClassConstraintForValue(
                propertyIri = createValueRequest.propertyIri,
                propertyObjectClassConstraint = propertyObjectClassConstraint,
                updateValueV1 = createValueRequest.value,
                userProfile = createValueRequest.userProfile
            )

            // Check that the user has permission to modify the resource. (We do this as late as possible because it's
            // slower than the other checks, and there's no point in doing it if the other checks fail.)

            resourceFullResponse <- (responderManager ? ResourceFullGetRequestV1(
                iri = createValueRequest.resourceIri,
                userProfile = createValueRequest.userProfile,
                getIncoming = false
            )).mapTo[ResourceFullResponseV1]

            resourcePermissionCode = resourceFullResponse.resdata.flatMap(resdata => resdata.rights)

            _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = resourcePermissionCode, userNeedsPermissionIri = OntologyConstants.KnoraBase.HasModifyPermission)) {
                throw ForbiddenException(s"User $userIri does not have permission to modify resource ${createValueRequest.resourceIri}")
            }

            // Ensure that creating the value would not violate the resource's cardinality restrictions or create a duplicate value.
            // This works in API v1 because a ResourceFullResponseV1 contains the resource's current property values (but only the
            // ones that the user is allowed to see, otherwise checking for duplicate values would be a security risk), plus empty
            // properties for which the resource's class has cardinalities. If the resources responder returns no information about
            // the property, this could be because the property isn't allowed for the resource, or because it's allowed, has a
            // cardinality of MustHaveOne or MayHaveOne, and already has a value that the user isn't allowed to see. We'll have to
            // implement this in a different way in API v2.
            cardinalityOK = resourceFullResponse.props.flatMap(_.properties.find(_.pid == createValueRequest.propertyIri)) match {
                case Some(prop: PropertyV1) =>
                    if (prop.values.exists(apiValueV1 => createValueRequest.value.isDuplicateOfOtherValue(apiValueV1))) {
                        throw DuplicateValueException()
                    }

                    val propCardinality = Cardinality.lookup(prop.occurrence.get)
                    !((propCardinality == Cardinality.MayHaveOne || propCardinality == Cardinality.MustHaveOne) && prop.values.nonEmpty)

                case None =>
                    false
            }

            _ = if (!cardinalityOK) {
                throw OntologyConstraintException(s"Cardinality restrictions do not allow a value to be added for property ${createValueRequest.propertyIri} of resource ${createValueRequest.resourceIri}")
            }

            // Everything seems OK, so create the value.

            // Construct a list of permission-relevant assertions about the new value, i.e. its owner and project
            // plus its permissions. Use the property's default permissions to make permissions for the new value.
            // Give it the same project as the containing resource, and make the requesting user the owner.
            ownerTuple: (IRI, IRI) = OntologyConstants.KnoraBase.AttachedToUser -> userIri
            projectTuple: (IRI, IRI) = OntologyConstants.KnoraBase.AttachedToProject -> resourceFullResponse.resinfo.get.project_id
            permissionsFromDefaults: Seq[(IRI, IRI)] = PermissionUtilV1.makePermissionsFromEntityDefaults(propertyInfo)
            permissionRelevantAssertionsForNewValue: Seq[(IRI, IRI)] = ownerTuple +: projectTuple +: permissionsFromDefaults

            apiResponse <- createValueV1AfterChecks(
                projectIri = resourceFullResponse.resinfo.get.project_id,
                resourceIri = createValueRequest.resourceIri,
                propertyIri = createValueRequest.propertyIri,
                value = createValueRequest.value,
                comment = createValueRequest.comment,
                permissionRelevantAssertions = permissionRelevantAssertionsForNewValue,
                updateResourceLastModificationDate = true,
                userProfile = createValueRequest.userProfile
            )
        } yield apiResponse

        for {
        // Don't allow anonymous users to update values.
            userIri <- createValueRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to update values"))
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- ResourceLocker.runWithResourceLock(
                createValueRequest.apiRequestID,
                createValueRequest.resourceIri,
                () => makeTaskFuture(userIri)
            )
        } yield taskResult
    }

    /**
      * Creates multiple values in a new, empty resource. The resource ''must'' be a new, empty resource, i.e. it must
      * have no values. All pre-update checks must already have been performed. Specifically, this method assumes that:
      *
      * - The requesting user has permission to add values to the resource.
      * - Each submitted value is consistent with the `knora-base:objectClassConstraint` of the property that is supposed to point to it.
      * - The resource has a suitable cardinality for each submitted value.
      * - All required values are provided.
      *
      * @param createMultipleValuesRequest the request message.
      * @return a [[CreateMultipleValuesResponseV1]].
      */
    private def createMultipleValuesV1(createMultipleValuesRequest: CreateMultipleValuesRequestV1): Future[CreateMultipleValuesResponseV1] = {
        /**
          * Creates a [[Future]] that performs the update. This function will be called by [[ResourceLocker]] once it
          * has acquired an update lock on the resource.
          *
          * @param userIri the IRI of the user making the request.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI): Future[CreateMultipleValuesResponseV1] = {
            // Make owner and project assertions for the new values. Give them the same project as the
            // containing resource, and make the requesting user the owner.
            val ownerTuple: (IRI, IRI) = OntologyConstants.KnoraBase.AttachedToUser -> userIri
            val projectTuple: (IRI, IRI) = OntologyConstants.KnoraBase.AttachedToProject -> createMultipleValuesRequest.projectIri

            for {
            // Get ontology information about the default permissions on the resource's properties.
                entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
                    propertyIris = createMultipleValuesRequest.values.keySet,
                    userProfile = createMultipleValuesRequest.userProfile
                )).mapTo[EntityInfoGetResponseV1]

                // We have a Map of property IRIs to lists of UpdateValueV1s. Create each value in the triplestore, and convert
                // the results into a Map of property IRIs to lists of Futures, each of which contains the results of creating
                // one value.
                valueCreationFutures: Map[IRI, Future[Seq[CreateValueResponseV1]]] = createMultipleValuesRequest.values.map {
                    case (propertyIri: IRI, valuesWithComments: Seq[CreateValueV1WithComment]) =>
                        // Construct a list of permission-relevant assertions about the new values of the property,
                        // i.e. the values' owner and project plus their permissions. Use the property's default
                        // permissions to make permissions for the new values.
                        val propertyInfo = entityInfoResponse.propertyEntityInfoMap(propertyIri)
                        val permissionsFromDefaults: Seq[(IRI, IRI)] = PermissionUtilV1.makePermissionsFromEntityDefaults(propertyInfo)
                        val permissionRelevantAssertionsForNewValue: Seq[(IRI, IRI)] = ownerTuple +: projectTuple +: permissionsFromDefaults

                        // For each value submitted for the property, make a Future that creates the value. Don't update
                        // the resource's lastModificationDate in these Futures, because it is not safe to update
                        // a resource in multiple concurrent transactions. (Multiple transactions could simultaneously
                        // find that the resource has no lastModificationDate, and they could all add it, giving
                        // the resource multiple lastModificationDate triples.)
                        val valueCreationResponsesForProperty: Seq[Future[CreateValueResponseV1]] = valuesWithComments.map {
                            valueV1WithComment: CreateValueV1WithComment => createValueV1AfterChecks(
                                projectIri = createMultipleValuesRequest.projectIri,
                                resourceIri = createMultipleValuesRequest.resourceIri,
                                propertyIri = propertyIri,
                                value = valueV1WithComment.updateValueV1,
                                comment = valueV1WithComment.comment,
                                permissionRelevantAssertions = permissionRelevantAssertionsForNewValue,
                                updateResourceLastModificationDate = false,
                                userProfile = createMultipleValuesRequest.userProfile
                            )
                        }

                        propertyIri -> Future.sequence(valueCreationResponsesForProperty)
                }

                // Convert a Map containing futures into a Future containing a Map: http://stackoverflow.com/a/17479415
                valueCreationResponses: Map[IRI, Seq[CreateValueResponseV1]] <- Future.sequence {
                    valueCreationFutures.map {
                        case (propertyIri: IRI, responseFutures: Future[Seq[CreateValueResponseV1]]) =>
                            responseFutures.map {
                                responses: Seq[CreateValueResponseV1] =>
                                    (propertyIri, responses)
                            }
                    }
                }.map(_.toMap)

            } yield CreateMultipleValuesResponseV1(values = valueCreationResponses)
        }

        for {
        // Don't allow anonymous users to update values.
            userIri <- createMultipleValuesRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to update values"))
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- ResourceLocker.runWithResourceLock(
                createMultipleValuesRequest.apiRequestID,
                createMultipleValuesRequest.resourceIri,
                () => makeTaskFuture(userIri)
            )
        } yield taskResult
    }

    /**
      * Adds a new version of an existing value.
      *
      * @param changeValueRequest the request message.
      * @return an [[ChangeValueResponseV1]] if the update was successful.
      */
    private def changeValueV1(changeValueRequest: ChangeValueRequestV1): Future[ChangeValueResponseV1] = {
        /**
          * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
          * called by [[ResourceLocker]] once it has acquired an update lock on the resource.
          *
          * @param userIri                     the IRI of the user making the request.
          * @param findResourceWithValueResult a [[FindResourceWithValueResult]] indicating which resource contains the value
          *                                    to be updated.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI, findResourceWithValueResult: FindResourceWithValueResult): Future[ChangeValueResponseV1] = {
            // If we're updating a link, findResourceWithValueResult will contain the IRI of the property that points to the
            // knora-base:LinkValue, but we'll need the IRI of the corresponding link property.
            val propertyIri = changeValueRequest.value match {
                case linkUpdateV1: LinkUpdateV1 => knoraIriUtil.linkValuePropertyIri2LinkPropertyIri(findResourceWithValueResult.propertyIri)
                case _ => findResourceWithValueResult.propertyIri
            }

            if (propertyIri == OntologyConstants.KnoraBase.HasStandoffLinkTo) {
                throw BadRequestException("Standoff links can be changed only by submitting a new text value")
            }

            for {
            // Ensure that the user has permission to modify the value.

                maybeCurrentValueQueryResult: Option[ValueQueryResult] <- changeValueRequest.value match {
                    case linkUpdateV1: LinkUpdateV1 =>
                        // We're being asked to update a link. We expect the current value version IRI to point to a
                        // knora-base:LinkValue. Get all necessary information about the LinkValue and the corresponding
                        // direct link.
                        findLinkValueByIri(
                            subjectIri = findResourceWithValueResult.resourceIri,
                            predicateIri = propertyIri,
                            objectIri = None,
                            linkValueIri = changeValueRequest.valueIri,
                            userProfile = changeValueRequest.userProfile
                        )

                    case otherValueV1 =>
                        // We're being asked to update an ordinary value.
                        findValue(changeValueRequest.valueIri, changeValueRequest.userProfile)
                }

                currentValueQueryResult = maybeCurrentValueQueryResult.getOrElse(throw NotFoundException(s"Value ${changeValueRequest.valueIri} not found (it may have been deleted)"))

                _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = Some(currentValueQueryResult.permissionCode), userNeedsPermissionIri = OntologyConstants.KnoraBase.HasModifyPermission)) {
                    throw ForbiddenException(s"User $userIri does not have permission to add a new version to value ${changeValueRequest.valueIri}")
                }

                // Make sure the new version would not be redundant, given the current version.
                _ = if (changeValueRequest.value.isRedundant(currentValueQueryResult.value)) {
                    throw DuplicateValueException("The submitted value is the same as the current version")
                }

                // Check that the submitted value has the correct type for the property.

                entityInfoResponse <- (responderManager ? EntityInfoGetRequestV1(
                    propertyIris = Set(propertyIri),
                    userProfile = changeValueRequest.userProfile
                )).mapTo[EntityInfoGetResponseV1]

                propertyInfo = entityInfoResponse.propertyEntityInfoMap(propertyIri)
                propertyObjectClassConstraint = propertyInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse {
                    throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")
                }

                // Check that the object of the property (the value to be updated, or the target of the link to be updated) will have
                // the correct type for the property's knora-base:objectClassConstraint.
                _ <- checkPropertyObjectClassConstraintForValue(
                    propertyIri = propertyIri,
                    propertyObjectClassConstraint = propertyObjectClassConstraint,
                    updateValueV1 = changeValueRequest.value,
                    userProfile = changeValueRequest.userProfile
                )

                // Check that the current value and the submitted value have the same type.
                _ = if (currentValueQueryResult.value.valueTypeIri != changeValueRequest.value.valueTypeIri) {
                    throw BadRequestException(s"Value ${changeValueRequest.valueIri} has type ${currentValueQueryResult.value.valueTypeIri}, but the submitted new version has type ${changeValueRequest.value.valueTypeIri}")
                }

                // Get details of the resource.  (We do this as late as possible because it's slower than the other checks,
                // and there's no point in doing it if the other checks fail.)
                resourceFullResponse <- (responderManager ? ResourceFullGetRequestV1(
                    iri = findResourceWithValueResult.resourceIri,
                    userProfile = changeValueRequest.userProfile,
                    getIncoming = false
                )).mapTo[ResourceFullResponseV1]

                // Ensure that adding the new value version would not create a duplicate value. This works in API v1 because a
                // ResourceFullResponseV1 contains only the values that the user is allowed to see, otherwise checking for
                // duplicate values would be a security risk. We'll have to implement this in a different way in API v2.
                _ = resourceFullResponse.props.flatMap(_.properties.find(_.pid == propertyIri)) match {
                    case Some(prop: PropertyV1) =>
                        // Don't consider the current value version when looking for duplicates.
                        val filteredValues = prop.value_ids.zip(prop.values).filter(_._1 != changeValueRequest.valueIri).unzip._2

                        if (filteredValues.exists(apiValueV1 => changeValueRequest.value.isDuplicateOfOtherValue(apiValueV1))) {
                            throw DuplicateValueException()
                        }

                    case None =>
                        // This shouldn't happen unless someone just changed the ontology.
                        throw NotFoundException(s"No information found about property $propertyIri for resource ${findResourceWithValueResult.resourceIri}")
                }

                // The rest of the preparation for the update depends on whether we're changing a link or an ordinary value.
                apiResponse <- (changeValueRequest.value, currentValueQueryResult) match {
                    case (linkUpdateV1: LinkUpdateV1, currentLinkValueQueryResult: LinkValueQueryResult) =>
                        // We're updating a link. This means deleting an existing link and creating a new one, so
                        // check that the user has permission to modify the resource.
                        val resourcePermissionCode = resourceFullResponse.resdata.flatMap(resdata => resdata.rights)
                        if (!PermissionUtilV1.impliesV1(userHasPermissionCode = resourcePermissionCode, userNeedsPermissionIri = OntologyConstants.KnoraBase.HasModifyPermission)) {
                            throw ForbiddenException(s"User $userIri does not have permission to modify resource ${findResourceWithValueResult.resourceIri}")
                        }

                        // We'll need to create a new LinkValue. Construct a list of permission-relevant assertions
                        // about the new LinkValue, i.e. its owner and project plus its permissions. Use the property's
                        // default permissions to make permissions for the new LinkValue. Give it the same project as the
                        // containing resource, and make the requesting user the owner.
                        val ownerTuple: (IRI, IRI) = OntologyConstants.KnoraBase.AttachedToUser -> userIri
                        val projectTuple: (IRI, IRI) = OntologyConstants.KnoraBase.AttachedToProject -> resourceFullResponse.resinfo.get.project_id
                        val permissionsFromDefaults: Seq[(IRI, IRI)] = PermissionUtilV1.makePermissionsFromEntityDefaults(propertyInfo)
                        val permissionRelevantAssertionsForNewValue: Seq[(IRI, IRI)] = ownerTuple +: projectTuple +: permissionsFromDefaults

                        changeLinkValueV1AfterChecks(projectIri = currentValueQueryResult.projectIri,
                            resourceIri = findResourceWithValueResult.resourceIri,
                            propertyIri = propertyIri,
                            currentLinkValueV1 = currentLinkValueQueryResult.value,
                            linkUpdateV1 = linkUpdateV1,
                            comment = changeValueRequest.comment,
                            permissionRelevantAssertions = permissionRelevantAssertionsForNewValue,
                            userProfile = changeValueRequest.userProfile)

                    case _ =>
                        // We're updating an ordinary value. Generate an IRI for the new version of the value.
                        val newValueIri = knoraIriUtil.makeRandomValueIri(findResourceWithValueResult.resourceIri)

                        // Give the new version the same permissions and project as the previous version, but make the requesting user
                        // the owner.
                        val permissionRelevantAssertionsForNewValue: Seq[(IRI, IRI)] = {
                            (OntologyConstants.KnoraBase.AttachedToUser, userIri) +:
                                currentValueQueryResult.permissionRelevantAssertions.filterNot {
                                    case (p, o) => p == OntologyConstants.KnoraBase.AttachedToUser
                                }
                        }

                        changeOrdinaryValueV1AfterChecks(projectIri = currentValueQueryResult.projectIri,
                            resourceIri = findResourceWithValueResult.resourceIri,
                            propertyIri = propertyIri,
                            currentValueIri = changeValueRequest.valueIri,
                            currentValueV1 = currentValueQueryResult.value,
                            newValueIri = newValueIri,
                            updateValueV1 = changeValueRequest.value,
                            comment = changeValueRequest.comment,
                            permissionRelevantAssertions = permissionRelevantAssertionsForNewValue,
                            userProfile = changeValueRequest.userProfile)
                }
            } yield apiResponse
        }


        for {
        // Don't allow anonymous users to update values.
            userIri <- changeValueRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to update values"))
            }

            // Find the resource containing the value.
            findResourceWithValueResult <- findResourceWithValue(changeValueRequest.valueIri)

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- ResourceLocker.runWithResourceLock(
                changeValueRequest.apiRequestID,
                findResourceWithValueResult.resourceIri,
                () => makeTaskFuture(userIri, findResourceWithValueResult)
            )
        } yield taskResult
    }

    private def changeCommentV1(changeCommentRequest: ChangeCommentRequestV1): Future[ChangeValueResponseV1] = {
        /**
          * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
          * called by [[ResourceLocker]] once it has acquired an update lock on the resource.
          *
          * @param userIri the IRI of the user making the request.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI, findResourceWithValueResult: FindResourceWithValueResult): Future[ChangeValueResponseV1] = {
            for {
            // Ensure that the user has permission to modify the value.

                maybeCurrentValueQueryResult: Option[ValueQueryResult] <- findValue(changeCommentRequest.valueIri, changeCommentRequest.userProfile)

                currentValueQueryResult = maybeCurrentValueQueryResult.getOrElse(throw NotFoundException(s"Value ${changeCommentRequest.valueIri} not found (it may have been deleted)"))

                _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = Some(currentValueQueryResult.permissionCode), userNeedsPermissionIri = OntologyConstants.KnoraBase.HasModifyPermission)) {
                    throw ForbiddenException(s"User $userIri does not have permission to add a new version to value ${changeCommentRequest.valueIri}")
                }

                // currentValueQueryResult.comment is an Option[String]
                _ = currentValueQueryResult.comment.foreach {
                    commentStr => if (commentStr == changeCommentRequest.comment) throw DuplicateValueException("The submitted comment is the same as the current comment")
                }

                // Everything looks OK, so update the comment.

                // Generate an IRI for the new value.
                newValueIri = knoraIriUtil.makeRandomValueIri(findResourceWithValueResult.resourceIri)

                // Generate a SPARQL update.
                sparqlUpdate = queries.sparql.v1.txt.changeComment(
                    dataNamedGraph = settings.projectNamedGraphs(findResourceWithValueResult.projectIri).data,
                    resourceIri = findResourceWithValueResult.resourceIri,
                    propertyIri = findResourceWithValueResult.propertyIri,
                    currentValueIri = changeCommentRequest.valueIri,
                    newValueIri = newValueIri,
                    comment = changeCommentRequest.comment
                ).toString()

                // Do the update.
                sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

                // To find out whether the update succeeded, look for the new value in the triplestore.
                verifyUpdateResult <- verifyValueUpdate(
                    resourceIri = findResourceWithValueResult.resourceIri,
                    propertyIri = findResourceWithValueResult.propertyIri,
                    searchValueIri = newValueIri,
                    userProfile = changeCommentRequest.userProfile
                )
            } yield ChangeValueResponseV1(
                value = verifyUpdateResult.value,
                comment = verifyUpdateResult.comment,
                id = newValueIri,
                rights = verifyUpdateResult.permissionCode,
                userdata = changeCommentRequest.userProfile.userData
            )
        }

        for {
        // Don't allow anonymous users to update values.
            userIri <- changeCommentRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to update values"))
            }

            // Find the resource containing the value.
            findResourceWithValueResult <- findResourceWithValue(changeCommentRequest.valueIri)

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- ResourceLocker.runWithResourceLock(
                changeCommentRequest.apiRequestID,
                findResourceWithValueResult.resourceIri,
                () => makeTaskFuture(userIri, findResourceWithValueResult)
            )
        } yield taskResult

    }

    /**
      * Creates a new version of a value and marks it as deleted.
      *
      * @param deleteValueRequest the request message.
      * @return a [[DeleteValueResponseV1]].
      */
    private def deleteValueV1(deleteValueRequest: DeleteValueRequestV1): Future[DeleteValueResponseV1] = {
        // TODO: handle deleting links.

        /**
          * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
          * called by [[ResourceLocker]] once it has acquired an update lock on the resource.
          *

          * @param userIri                     the IRI of the user making the request.
          * @param findResourceWithValueResult a [[FindResourceWithValueResult]] indicating which resource contains the value
          *                                    to be updated.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI, findResourceWithValueResult: FindResourceWithValueResult): Future[DeleteValueResponseV1] = for {
        // Ensure that the user has permission to modify the value.
            currentValueResponse <- getValueResponseV1(deleteValueRequest.valueIri, deleteValueRequest.userProfile)

            _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = Some(currentValueResponse.rights), userNeedsPermissionIri = OntologyConstants.KnoraBase.HasDeletePermission)) {
                throw ForbiddenException(s"User $userIri does not have permission to delete value ${deleteValueRequest.valueIri}")
            }

            // The way we delete the value depends on whether it's a link value or an ordinary value.

            (newValueIri, sparqlUpdate) <- currentValueResponse.value match {
                case linkValue: LinkValueV1 =>
                    // It's a link value. Make a SparqlTemplateLinkUpdate describing how to delete it.

                    val linkPropertyIri = knoraIriUtil.linkValuePropertyIri2LinkPropertyIri(findResourceWithValueResult.propertyIri)

                    for {
                        sparqlTemplateLinkUpdate <- decrementLinkValue(
                            sourceResourceIri = findResourceWithValueResult.resourceIri,
                            linkPropertyIri = linkPropertyIri,
                            removedTargetResourceIri = linkValue.objectIri,
                            userProfile = deleteValueRequest.userProfile
                        )

                        sparqlUpdate = queries.sparql.v1.txt.deleteLink(
                            dataNamedGraph = settings.projectNamedGraphs(findResourceWithValueResult.projectIri).data,
                            linkSourceIri = findResourceWithValueResult.resourceIri,
                            linkUpdate = sparqlTemplateLinkUpdate,
                            maybeComment = deleteValueRequest.comment
                        ).toString()
                    } yield (sparqlTemplateLinkUpdate.newLinkValueIri, sparqlUpdate)

                case _ =>
                    // Generate an IRI for the new value.
                    val newValueIri = knoraIriUtil.makeRandomValueIri(findResourceWithValueResult.resourceIri)
                    val sparqlUpdate = queries.sparql.v1.txt.deleteValue(
                        dataNamedGraph = settings.projectNamedGraphs(findResourceWithValueResult.projectIri).data,
                        resourceIri = findResourceWithValueResult.resourceIri,
                        propertyIri = findResourceWithValueResult.propertyIri,
                        currentValueIri = deleteValueRequest.valueIri,
                        newValueIri = newValueIri,
                        maybeComment = deleteValueRequest.comment
                    ).toString()

                    Future(newValueIri, sparqlUpdate)
            }

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

            // Check whether the update succeeded.
            sparqlQuery = queries.sparql.v1.txt.checkDeletion(newValueIri).toString()
            sparqlSelectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows = sparqlSelectResponse.results.bindings

            _ = if (rows.isEmpty || !rows.head.rowMap.get("isDeleted").exists(_.toBoolean)) {
                throw UpdateNotPerformedException(s"Value ${deleteValueRequest.valueIri} was not deleted, perhaps because the request was based on outdated information")
            }
        } yield DeleteValueResponseV1(
                id = newValueIri,
                userdata = deleteValueRequest.userProfile.userData
            )

        for {
        // Don't allow anonymous users to update values.
            userIri <- deleteValueRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to update values"))
            }

            // Find the resource containing the value.
            findResourceWithValueResult <- findResourceWithValue(deleteValueRequest.valueIri)

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- ResourceLocker.runWithResourceLock(
                deleteValueRequest.apiRequestID,
                findResourceWithValueResult.resourceIri,
                () => makeTaskFuture(userIri, findResourceWithValueResult)
            )
        } yield taskResult
    }

    /**
      * Gets the version history of a value.
      *
      * @param versionHistoryRequest a [[ValueVersionHistoryGetRequestV1]].
      * @return a [[ValueVersionHistoryGetResponseV1]].
      */
    private def getValueVersionHistoryResponseV1(versionHistoryRequest: ValueVersionHistoryGetRequestV1): Future[ValueVersionHistoryGetResponseV1] = {
        /**
          * Recursively converts a [[Map]] of value version SPARQL query result rows into a [[Vector]] representing the value's version history,
          * ordered from most recent to oldest.
          *
          * @param versionMap        a [[Map]] of value version IRIs to the contents of SPARQL query result rows.
          * @param startAtVersion    the IRI of the version to start at.
          * @param versionRowsVector a [[Vector]] containing the results of the previous recursive call, or an empty vector if this is the first call.
          * @return a [[Vector]] in which the elements are SPARQL query result rows representing versions, ordered from most recent to oldest.
          */
        @tailrec
        def versionMap2Vector(versionMap: Map[IRI, Map[String, String]], startAtVersion: IRI, versionRowsVector: Vector[Map[String, String]]): Vector[Map[String, String]] = {
            val startValue = versionMap(startAtVersion)
            val newVersionVector = versionRowsVector :+ startValue

            startValue.get("previousValue") match {
                case Some(previousValue) => versionMap2Vector(versionMap, previousValue, newVersionVector)
                case None => newVersionVector
            }
        }


        for {
        // Do a SPARQL query to get the versions of the value.
            sparqlQuery <- Future {
                // Run the template function in a Future to handle exceptions (see http://git.iml.unibas.ch/salsah-suite/knora/wikis/futures-with-akka#handling-errors-with-futures)
                queries.sparql.v1.txt.getVersionHistory(
                    resourceIri = versionHistoryRequest.resourceIri,
                    propertyIri = versionHistoryRequest.propertyIri,
                    currentValueIri = versionHistoryRequest.currentValueIri
                ).toString()
            }
            selectResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows = selectResponse.results.bindings

            _ = if (rows.isEmpty) {
                throw NotFoundException(s"Value ${versionHistoryRequest.currentValueIri} is not the most recent version of an object of property ${versionHistoryRequest.propertyIri} for resource ${versionHistoryRequest.resourceIri}")
            }

            // Convert the result rows to a map of value IRIs to result rows.
            versionMap: Map[IRI, Map[String, String]] = rows.map {
                row =>
                    val valueIri = row.rowMap("value")
                    valueIri -> row.rowMap
            }(breakOut)

            // Order the result rows from most recent to oldest.
            versionRowsVector = versionMap2Vector(versionMap, versionHistoryRequest.currentValueIri, Vector.empty[Map[String, String]])

            // Filter out the versions that the user doesn't have permission to see.
            filteredVersionRowsVector = versionRowsVector.filter {
                rowMap =>
                    val valueOwner = rowMap("valueOwner")
                    val project = rowMap("project")
                    val permissionAssertions = rowMap.getOrElse("permissionAssertions", "")

                    val permissionRelevantAssertions = PermissionUtilV1.parsePermissions(
                        assertionsString = permissionAssertions,
                        owner = valueOwner,
                        project = project
                    )

                    val valuePermissions = PermissionUtilV1.getUserPermissionV1(
                        subjectIri = rowMap("value"),
                        assertions = permissionRelevantAssertions,
                        userProfile = versionHistoryRequest.userProfile
                    )

                    valuePermissions.nonEmpty
            }

            // Make a set of the IRIs of the versions that the user has permission to see.
            visibleVersionIris = filteredVersionRowsVector.map(_ ("value")).toSet

            versionV1Vector = filteredVersionRowsVector.map {
                rowMap =>
                    ValueVersionV1(
                        valueObjectIri = rowMap("value"),
                        valueCreationDate = rowMap.get("valueCreationDate"),
                        previousValue = rowMap.get("previousValue") match {
                            // Don't refer to a previous value that the user doesn't have permission to see.
                            case Some(previousValueIri) if visibleVersionIris.contains(previousValueIri) => Some(previousValueIri)
                            case _ => None
                        }
                    )
            }
        } yield ValueVersionHistoryGetResponseV1(
            valueVersions = versionV1Vector,
            versionHistoryRequest.userProfile.userData
        )
    }

    /**
      * Looks for a direct link connecting two resources, finds the corresponding `knora-base:LinkValue`, and returns
      * a [[ValueGetResponseV1]] containing a [[LinkValueV1]] describing the `LinkValue`. Throws [[NotFoundException]]
      * if no such `LinkValue` is found.
      *
      * @param subjectIri   the IRI of the resource that is the source of the link.
      * @param predicateIri the IRI of the property that links the two resources.
      * @param objectIri    the IRI of the resource that is the target of the link.
      * @param userProfile  the profile of the user making the request.
      * @return a [[ValueGetResponseV1]] containing a [[LinkValueV1]].
      */
    @throws(classOf[NotFoundException])
    private def getLinkValue(subjectIri: IRI, predicateIri: IRI, objectIri: IRI, userProfile: UserProfileV1): Future[ValueGetResponseV1] = {
        for {
            maybeValueQueryResult <- findLinkValueByObject(
                subjectIri = subjectIri,
                predicateIri = predicateIri,
                objectIri = objectIri,
                userProfile = userProfile
            )

            linkValueResponse = maybeValueQueryResult match {
                case Some(valueQueryResult) =>
                    ValueGetResponseV1(
                        valuetype = valueQueryResult.value.valueTypeIri,
                        rights = valueQueryResult.permissionCode,
                        value = valueQueryResult.value,
                        userdata = userProfile.userData
                    )

                case None =>
                    throw NotFoundException(s"No knora-base:LinkValue found describing a link from resource $subjectIri with predicate $predicateIri to resource $objectIri (it may have been deleted)")
            }
        } yield linkValueResponse
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Helper methods and types.

    /**
      * Represents the result of querying a value.
      */
    trait ValueQueryResult {
        def value: ApiValueV1

        def projectIri: IRI

        def comment: Option[String]

        def permissionRelevantAssertions: Seq[(IRI, IRI)]

        def permissionCode: Int
    }

    /**
      * Represents basic information resulting from querying a value. This is sufficient if the value is an ordinary
      * value (not a link).
      *
      * @param value                        the value that was found.
      * @param permissionRelevantAssertions a list of the permission-relevant assertions declared on the value.
      * @param permissionCode               an integer permission code representing the user's permissions on the value.
      */
    case class BasicValueQueryResult(value: ApiValueV1,
                                     projectIri: IRI,
                                     comment: Option[String],
                                     permissionRelevantAssertions: Seq[(IRI, IRI)],
                                     permissionCode: Int) extends ValueQueryResult

    /**
      * Represents the result of querying a link.
      *
      * @param value                        a [[LinkValueV1]] representing the `knora-base:LinkValue` that was found.
      * @param directLinkExists             `true` if a direct link exists between the two resources.
      * @param targetResourceClass          if a direct link exists, contains the OWL class of the target resource.
      * @param permissionRelevantAssertions a list of the permission-relevant assertions declared on the value.
      * @param permissionCode               an integer permission code representing the user's permissions on the value.
      */
    case class LinkValueQueryResult(value: LinkValueV1,
                                    projectIri: IRI,
                                    comment: Option[String],
                                    directLinkExists: Boolean,
                                    targetResourceClass: Option[IRI],
                                    permissionRelevantAssertions: Seq[(IRI, IRI)],
                                    permissionCode: Int) extends ValueQueryResult

    /**
      * Queries a `knora-base:Value` and returns a [[ValueQueryResult]] describing it.
      *
      * @param valueIri    the IRI of the value to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[ValueQueryResult]], or `None` if the value is not found.
      */
    private def findValue(valueIri: IRI, userProfile: UserProfileV1): Future[Option[ValueQueryResult]] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getValue(valueIri).toString())
            response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows: Seq[VariableResultsRow] = response.results.bindings

            result = if (rows.nonEmpty) {
                Some(sparqlQueryResults2ValueQueryResult(valueIri, rows, userProfile))
            } else {
                None
            }
        } yield result
    }

    /**
      * Looks for `knora-base:LinkValue` given its IRI, and returns a [[ValueGetResponseV1]] containing a
      * [[LinkValueV1]] describing the `LinkValue`, or `None` if no such `LinkValue` is found.
      *
      * @param subjectIri   the IRI of the resource that is the source of the link.
      * @param predicateIri the IRI of the property that links the two resources.
      * @param objectIri    if provided, the IRI of the target resource.
      * @param linkValueIri the IRI of the `LinkValue`.
      * @param userProfile  the profile of the user making the request.
      * @return an optional [[ValueGetResponseV1]] containing a [[LinkValueV1]].
      */
    private def findLinkValueByIri(subjectIri: IRI, predicateIri: IRI, objectIri: Option[IRI], linkValueIri: IRI, userProfile: UserProfileV1): Future[Option[LinkValueQueryResult]] = {
        for {
            sparqlQuery <- Future {
                queries.sparql.v1.txt.findLinkValueByIri(
                    subjectIri = subjectIri,
                    predicateIri = predicateIri,
                    maybeObjectIri = objectIri,
                    linkValueIri = linkValueIri
                ).toString()
            }

            response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows: Seq[VariableResultsRow] = response.results.bindings
        } yield sparqlQueryResults2LinkValueQueryResult(rows, userProfile)
    }

    /**
      * Looks for `knora-base:LinkValue` describing a link between two resources, and returns
      * a [[ValueGetResponseV1]] containing a [[LinkValueV1]] describing the `LinkValue`, or `None` if no such
      * `LinkValue` is found.
      *
      * @param subjectIri   the IRI of the resource that is the source of the link.
      * @param predicateIri the IRI of the property that links the two resources.
      * @param objectIri    the IRI of the target resource.
      * @param userProfile  the profile of the user making the request.
      * @return an optional [[ValueGetResponseV1]] containing a [[LinkValueV1]].
      */
    private def findLinkValueByObject(subjectIri: IRI, predicateIri: IRI, objectIri: IRI, userProfile: UserProfileV1): Future[Option[LinkValueQueryResult]] = {
        for {
            sparqlQuery <- Future {
                queries.sparql.v1.txt.findLinkValueByObject(
                    subjectIri = subjectIri,
                    predicateIri = predicateIri,
                    objectIri = objectIri
                ).toString()
            }

            response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows: Seq[VariableResultsRow] = response.results.bindings
        } yield sparqlQueryResults2LinkValueQueryResult(rows, userProfile)
    }

    /**
      * Converts SPARQL query results about a `knora-base:LinkValue` into a [[LinkValueQueryResult]].
      *
      * @param rows        SPARQL query results about a `knora-base:LinkValue`.
      * @param userProfile the profile of the user making the request.
      * @return a [[LinkValueQueryResult]].
      */
    private def sparqlQueryResults2LinkValueQueryResult(rows: Seq[VariableResultsRow], userProfile: UserProfileV1): Option[LinkValueQueryResult] = {
        if (rows.nonEmpty) {
            val firstRowMap = rows.head.rowMap
            val valueIri = firstRowMap("linkValue")
            val comment = getValueComment(rows)
            val projectIri = getValueProjectIri(valueIri, rows)
            val directLinkExists = firstRowMap.get("directLinkExists").exists(_.toBoolean)
            val targetResourceClass = firstRowMap.get("targetResourceClass")
            val valueQueryResult = sparqlQueryResults2ValueQueryResult(valueIri, rows, userProfile)
            val linkValueQueryResult = LinkValueQueryResult(
                value = valueQueryResult.value match {
                    case linkValue: LinkValueV1 => linkValue
                    case other => throw InconsistentTriplestoreDataException(s"Expected value $valueIri to be of type ${OntologyConstants.KnoraBase.LinkValue}, but it was read with type ${other.valueTypeIri}")
                },
                comment = comment,
                projectIri = projectIri,
                directLinkExists = directLinkExists,
                targetResourceClass = targetResourceClass,
                permissionRelevantAssertions = valueQueryResult.permissionRelevantAssertions,
                permissionCode = valueQueryResult.permissionCode
            )
            Some(linkValueQueryResult)
        } else {
            None
        }
    }

    /**
      * Given the IRI of a value that should have been created, looks for the value in the resource's version history,
      * and returns details about it. If the value is not found, throws [[UpdateNotPerformedException]].
      *
      * @param resourceIri    the IRI of the resource that may have the value.
      * @param propertyIri    the IRI of the property that may have have the value.
      * @param searchValueIri the IRI of the value.
      * @param userProfile    the profile of the user making the request.
      * @return a [[ValueQueryResult]].
      */
    @throws(classOf[UpdateNotPerformedException])
    @throws(classOf[ForbiddenException])
    private def verifyValueUpdate(resourceIri: IRI, propertyIri: IRI, searchValueIri: IRI, userProfile: UserProfileV1): Future[ValueQueryResult] = {
        for {
        // Do a SPARQL query to look for the value in the resource's version history.
            sparqlQuery <- Future {
                // Run the template function in a Future to handle exceptions (see http://git.iml.unibas.ch/salsah-suite/knora/wikis/futures-with-akka#handling-errors-with-futures)
                queries.sparql.v1.txt.findValueInVersions(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    searchValueIri = searchValueIri
                ).toString()
            }

            // _ = println(sparqlQuery)

            updateVerificationResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows = updateVerificationResponse.results.bindings

            result = if (rows.nonEmpty) {
                sparqlQueryResults2ValueQueryResult(valueIri = searchValueIri, rows = rows, userProfile = userProfile)
            } else {
                throw UpdateNotPerformedException(s"The update to value $searchValueIri for property $propertyIri in resource $resourceIri was not performed, perhaps because it was based on outdated information")
            }
        } yield result
    }

    /**
      * Given information about a link that should have been created, verifies that the link exists, and returns
      * details about it. If the link has not been created, throws [[UpdateNotPerformedException]].
      *
      * @param linkSourceIri   the IRI of the resource that should be the source of the link.
      * @param linkPropertyIri the IRI of the link property.
      * @param linkTargetIri   the IRI of the resource that should be the target of the link.
      * @param linkValueIri    the IRI of the `knora-base:LinkValue` that should have been created.
      * @param userProfile     the profile of the user making the request.
      * @return a [[LinkValueQueryResult]].
      */
    @throws(classOf[UpdateNotPerformedException])
    @throws(classOf[ForbiddenException])
    private def verifyLinkUpdate(linkSourceIri: IRI, linkPropertyIri: IRI, linkTargetIri: IRI, linkValueIri: IRI, userProfile: UserProfileV1): Future[LinkValueQueryResult] = {
        for {
            maybeLinkValueQueryResult <- findLinkValueByIri(
                subjectIri = linkSourceIri,
                predicateIri = linkPropertyIri,
                objectIri = Some(linkTargetIri),
                linkValueIri = linkValueIri,
                userProfile = userProfile
            )

            result = maybeLinkValueQueryResult match {
                case Some(linkValueQueryResult) =>
                    if (!linkValueQueryResult.directLinkExists || linkValueQueryResult.targetResourceClass.isEmpty) {
                        throw UpdateNotPerformedException()
                    } else {
                        linkValueQueryResult
                    }

                case None =>
                    throw UpdateNotPerformedException(s"The update to link value $linkValueIri with source IRI $linkSourceIri, link property $linkPropertyIri, and target $linkTargetIri was not performed, perhaps because it was based on outdated information")
            }
        } yield result
    }

    /**
      * Converts SPARQL query results into a [[ApiValueV1]] plus a permission code.
      *
      * @param valueIri    the IRI of the value that was queried.
      * @param rows        the query result rows.
      * @param userProfile the profile of the user making the request.
      * @return a tuple containing a [[ApiValueV1]], the value's permission-relevant assertions, and a Knora API v1 permission
      *         code representing the user's permissions on the value.
      */
    @throws(classOf[ForbiddenException])
    private def sparqlQueryResults2ValueQueryResult(valueIri: IRI, rows: Seq[VariableResultsRow], userProfile: UserProfileV1): ValueQueryResult = {
        // Convert the query results to a ApiValueV1.
        val valueProps = valueUtilV1.createValueProps(valueIri, rows)
        val value = valueUtilV1.makeValueV1(valueProps)

        // Get the value's project IRI.
        val projectIri = getValueProjectIri(valueIri, rows)

        // Get the optional comment on the value.
        val comment = getValueComment(rows)

        // Get the value's permission-relevant assertions.
        val assertions = PermissionUtilV1.filterPermissionRelevantAssertionsFromValueProps(valueProps)

        // Get the permission code representing the user's permissions on the value.
        val permissionCode = PermissionUtilV1.getUserPermissionV1(valueIri, assertions, userProfile) match {
            case Some(code) => code
            case None =>
                val userIri = userProfile.userData.user_id.getOrElse(OntologyConstants.KnoraBase.UnknownUser)
                throw ForbiddenException(s"User $userIri does not have permission to see value $valueIri")
        }

        BasicValueQueryResult(
            value = value,
            comment = comment,
            projectIri = projectIri,
            permissionRelevantAssertions = assertions,
            permissionCode = permissionCode
        )
    }

    /**
      * Finds the IRI of a value's project in SPARQL query results describing the value.
      *
      * @param valueIri the IRI of the value.
      * @param rows     the SPARQL query results that describe the value.
      * @return the IRI of the value's project.
      */
    private def getValueProjectIri(valueIri: IRI, rows: Seq[VariableResultsRow]): IRI = {
        rows.find(_.rowMap("objPred") == OntologyConstants.KnoraBase.AttachedToProject).getOrElse(throw InconsistentTriplestoreDataException(s"Value $valueIri has no project")).rowMap("objObj")
    }

    /**
      * Gets the optional comment on a value from the SPARQL query results describing the value.
      *
      * @param rows the SPARQL query results that describe the value.
      * @return the optional comment on the value.
      */
    private def getValueComment(rows: Seq[VariableResultsRow]): Option[String] = {
        rows.find(_.rowMap("objPred") == OntologyConstants.KnoraBase.ValueHasComment).map(_.rowMap("objObj"))
    }

    /**
      * The result of calling the `findResourceWithValue` method.
      *
      * @param resourceIri the IRI of the resource containing the value.
      * @param projectIri  the IRI of the resource's project.
      * @param propertyIri the IRI of the property pointing to the value.
      */
    case class FindResourceWithValueResult(resourceIri: IRI, projectIri: IRI, propertyIri: IRI)

    /**
      * Given a value IRI, finds the value's resource and property.
      *
      * @param valueIri the IRI of the value.
      * @return a [[FindResourceWithValueResult]].
      */
    private def findResourceWithValue(valueIri: IRI): Future[FindResourceWithValueResult] = {
        for {
            findResourceSparqlQuery <- Future(queries.sparql.v1.txt.findResourceWithValue(valueIri).toString())
            findResourceResponse <- (storeManager ? SparqlSelectRequest(findResourceSparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (findResourceResponse.results.bindings.isEmpty) {
                throw new NotFoundException(s"No resource found containing value $valueIri")
            }

            resultRowMap = findResourceResponse.getFirstRow.rowMap

            resourceIri = resultRowMap("resource")
            projectIri = resultRowMap("project")
            propertyIri = resultRowMap("property")
        } yield FindResourceWithValueResult(
            resourceIri = resourceIri,
            projectIri = projectIri,
            propertyIri = propertyIri
        )
    }

    /**
      * Creates a new value (either an ordinary value or a link), assuming that pre-update checks have already been
      * done.
      *
      * @param projectIri                         the IRI of the project in which to create the value.
      * @param resourceIri                        the IRI of the resource in which to create the value.
      * @param propertyIri                        the IRI of the property that will point from the resource to the value.
      * @param value                              the value to create.
      * @param permissionRelevantAssertions       the permission-relevant assertions to assign to the value, i.e. its owner
      *                                           and project plus its permissions.
      * @param updateResourceLastModificationDate if true, update the resource's `knora-base:lastModificationDate`.
      * @param userProfile                        the profile of the user making the request.
      * @return a [[CreateValueResponseV1]].
      */
    private def createValueV1AfterChecks(projectIri: IRI,
                                         resourceIri: IRI,
                                         propertyIri: IRI,
                                         value: UpdateValueV1,
                                         comment: Option[String],
                                         permissionRelevantAssertions: Seq[(IRI, IRI)],
                                         updateResourceLastModificationDate: Boolean,
                                         userProfile: UserProfileV1): Future[CreateValueResponseV1] = {
        value match {
            case linkUpdateV1: LinkUpdateV1 =>
                // We're creating a link.
                createLinkValueV1AfterChecks(
                    projectIri = projectIri,
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    linkUpdateV1 = linkUpdateV1,
                    comment = comment,
                    permissionRelevantAssertions = permissionRelevantAssertions,
                    updateResourceLastModificationDate = updateResourceLastModificationDate,
                    userProfile = userProfile
                )

            case ordinaryUpdateValueV1 =>
                // We're creating an ordinary value. Generate an IRI for it.
                val newValueIri = knoraIriUtil.makeRandomValueIri(resourceIri)

                createOrdinaryValueV1AfterChecks(
                    projectIri = projectIri,
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    newValueIri = newValueIri,
                    value = ordinaryUpdateValueV1,
                    comment = comment,
                    permissionRelevantAssertions = permissionRelevantAssertions,
                    updateResourceLastModificationDate = updateResourceLastModificationDate,
                    userProfile = userProfile
                )
        }
    }

    /**
      * Creates a link, assuming that pre-update checks have already been done.
      *
      * @param projectIri                         the IRI of the project in which the link is to be created.
      * @param resourceIri                        the resource in which the link is to be created.
      * @param propertyIri                        the link property.
      * @param linkUpdateV1                       a [[LinkUpdateV1]] specifying the target resource.
      * @param permissionRelevantAssertions       permission-relevant assertions for the new `knora-base:LinkValue`, i.e.
      *                                           its owner and project plus permissions.
      * @param updateResourceLastModificationDate if true, update the resource's `knora-base:lastModificationDate`.
      * @param userProfile                        the profile of the user making the request.
      * @return a [[CreateValueResponseV1]].
      */
    private def createLinkValueV1AfterChecks(projectIri: IRI,
                                             resourceIri: IRI,
                                             propertyIri: IRI,
                                             linkUpdateV1: LinkUpdateV1,
                                             comment: Option[String],
                                             permissionRelevantAssertions: Seq[(IRI, IRI)],
                                             updateResourceLastModificationDate: Boolean,
                                             userProfile: UserProfileV1): Future[CreateValueResponseV1] = {
        for {
            sparqlTemplateLinkUpdate <- incrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                targetResourceIri = linkUpdateV1.targetResourceIri,
                permissionRelevantAssertions = permissionRelevantAssertions,
                userProfile = userProfile
            )

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v1.txt.createLink(
                dataNamedGraph = settings.projectNamedGraphs(projectIri).data,
                linkSourceIri = resourceIri,
                linkUpdate = sparqlTemplateLinkUpdate,
                maybeComment = comment,
                updateResourceLastModificationDate = updateResourceLastModificationDate
            ).toString()

            /*
            _ = println("================ Create link ===============")
            _ = println(sparqlUpdate)
            _ = println("=============================================")
            */

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

            // To find out whether the update succeeded, check that the link is in the triplestore.
            linkValueQueryResult <- verifyLinkUpdate(
                linkSourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                linkTargetIri = linkUpdateV1.targetResourceIri,
                linkValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
                userProfile = userProfile
            )

            apiResponseValue = LinkV1(
                targetResourceIri = linkUpdateV1.targetResourceIri,
                valueResourceClass = linkValueQueryResult.targetResourceClass
            )
        } yield CreateValueResponseV1(
            value = apiResponseValue,
            comment = linkValueQueryResult.comment,
            id = sparqlTemplateLinkUpdate.newLinkValueIri,
            rights = linkValueQueryResult.permissionCode,
            userdata = userProfile.userData
        )
    }

    /**
      * Creates an ordinary value (i.e. not a link), assuming that pre-update checks have already been done.
      *
      * @param projectIri                         the project in which the value is to be created.
      * @param resourceIri                        the resource in which the value is to be created.
      * @param propertyIri                        the property that should point to the value.
      * @param newValueIri                        the IRI of the new value.
      * @param value                              an [[UpdateValueV1]] describing the value.
      * @param permissionRelevantAssertions       permission-relevant assertions for the new value, i.e.
      *                                           its owner and project plus permissions.
      * @param updateResourceLastModificationDate if true, update the resource's `knora-base:lastModificationDate`.
      * @param userProfile                        the profile of the user making the request.
      * @return a [[CreateValueResponseV1]].
      */
    private def createOrdinaryValueV1AfterChecks(projectIri: IRI,
                                                 resourceIri: IRI,
                                                 propertyIri: IRI,
                                                 newValueIri: IRI,
                                                 value: UpdateValueV1,
                                                 comment: Option[String],
                                                 permissionRelevantAssertions: Seq[(IRI, IRI)],
                                                 updateResourceLastModificationDate: Boolean,
                                                 userProfile: UserProfileV1): Future[CreateValueResponseV1] = {
        for {
        // If we're creating a text value, update direct links and LinkValues for any resource references in Standoff.
            standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- value match {
                case textValueV1: TextValueV1 =>
                    // Make sure the text value's list of resource references is correct.
                    checkTextValueResourceRefs(textValueV1)

                    // Construct a SparqlTemplateLinkUpdate for each reference that was added.
                    val standoffLinkUpdatesForAddedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] =
                        textValueV1.resource_reference.map {
                            targetResourceIri => incrementLinkValue(
                                sourceResourceIri = resourceIri,
                                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                                targetResourceIri = targetResourceIri,
                                permissionRelevantAssertions = permissionRelevantAssertions,
                                userProfile = userProfile
                            )
                        }

                    Future.sequence(standoffLinkUpdatesForAddedResourceRefs)

                case _ => Future(Vector.empty[SparqlTemplateLinkUpdate])
            }

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v1.txt.createValue(
                dataNamedGraph = settings.projectNamedGraphs(projectIri).data,
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                newValueIri = newValueIri,
                valueTypeIri = value.valueTypeIri,
                value = value,
                linkUpdates = standoffLinkUpdates,
                maybeComment = comment,
                permissionRelevantAssertions = permissionRelevantAssertions,
                updateResourceLastModificationDate = updateResourceLastModificationDate
            ).toString()

            /*
            _ = println("================ Create value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
            */

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

            // To find out whether the update succeeded, check that the new value is in the triplestore.
            verifyUpdateResult <- verifyValueUpdate(
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                searchValueIri = newValueIri,
                userProfile = userProfile
            )
        } yield CreateValueResponseV1(
            value = verifyUpdateResult.value,
            comment = verifyUpdateResult.comment,
            id = newValueIri,
            rights = verifyUpdateResult.permissionCode,
            userdata = userProfile.userData
        )
    }

    /**
      * Changes a link, assuming that pre-update checks have already been done.
      *
      * @param projectIri                   the IRI of the project containing the link.
      * @param resourceIri                  the IRI of the resource containing the link.
      * @param propertyIri                  the IRI of the link property.
      * @param currentLinkValueV1           a [[LinkValueV1]] representing the `knora-base:LinkValue` for the existing link.
      * @param linkUpdateV1                 a [[LinkUpdateV1]] indicating the new target resource.
      * @param comment                      an optional comment on the new link value.
      * @param permissionRelevantAssertions permission-relevant assertions for the new `knora-base:LinkValue`, i.e.
      *                                     its owner and project plus permissions.
      * @param userProfile                  the profile of the user making the request.
      * @return a [[ChangeValueResponseV1]].
      */
    private def changeLinkValueV1AfterChecks(projectIri: IRI,
                                             resourceIri: IRI,
                                             propertyIri: IRI,
                                             currentLinkValueV1: LinkValueV1,
                                             linkUpdateV1: LinkUpdateV1,
                                             comment: Option[String],
                                             permissionRelevantAssertions: Seq[(IRI, IRI)],
                                             userProfile: UserProfileV1): Future[ChangeValueResponseV1] = {
        for {
        // Delete the existing link and decrement its LinkValue's reference count.
            sparqlTemplateLinkUpdateForCurrentLink <- decrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                removedTargetResourceIri = currentLinkValueV1.objectIri,
                userProfile = userProfile
            )

            // Create a new link, and create a new LinkValue for it.
            sparqlTemplateLinkUpdateForNewLink <- incrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                targetResourceIri = linkUpdateV1.targetResourceIri,
                permissionRelevantAssertions = permissionRelevantAssertions,
                userProfile = userProfile
            )

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v1.txt.changeLink(
                dataNamedGraph = settings.projectNamedGraphs(projectIri).data,
                linkSourceIri = resourceIri,
                linkUpdateForCurrentLink = sparqlTemplateLinkUpdateForCurrentLink,
                linkUpdateForNewLink = sparqlTemplateLinkUpdateForNewLink,
                maybeComment = comment
            ).toString()

            /*
            _ = println("================ Update link ================")
            _ = println(sparqlUpdate)
            _ = println("=============================================")
            */

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

            // To find out whether the update succeeded, check that the new link is in the triplestore.
            linkValueQueryResult <- verifyLinkUpdate(
                linkSourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                linkTargetIri = linkUpdateV1.targetResourceIri,
                linkValueIri = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
                userProfile = userProfile
            )

            apiResponseValue = LinkV1(
                targetResourceIri = linkUpdateV1.targetResourceIri,
                valueResourceClass = linkValueQueryResult.targetResourceClass
            )

        } yield ChangeValueResponseV1(
            value = apiResponseValue,
            comment = linkValueQueryResult.comment,
            id = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
            rights = linkValueQueryResult.permissionCode,
            userdata = userProfile.userData
        )
    }

    /**
      * Changes an ordinary value (i.e. not a link), assuming that pre-update checks have already been done.
      *
      * @param projectIri                   the IRI of the project containing the value.
      * @param resourceIri                  the IRI of the resource containing the value.
      * @param propertyIri                  the IRI of the property that points to the value.
      * @param currentValueIri              the IRI of the existing value.
      * @param currentValueV1               an [[ApiValueV1]] representing the existing value.
      * @param newValueIri                  the IRI of the new value.
      * @param updateValueV1                an [[UpdateValueV1]] representing the new value.
      * @param comment                      an optional comment on the new value.
      * @param permissionRelevantAssertions permission-relevant assertions for the new `knora-base:LinkValue`, i.e.
      *                                     its owner and project plus permissions.
      * @param userProfile                  the profile of the user making the request.
      * @return a [[ChangeValueResponseV1]].
      */
    private def changeOrdinaryValueV1AfterChecks(projectIri: IRI,
                                                 resourceIri: IRI,
                                                 propertyIri: IRI,
                                                 currentValueIri: IRI,
                                                 currentValueV1: ApiValueV1,
                                                 newValueIri: IRI,
                                                 updateValueV1: UpdateValueV1,
                                                 comment: Option[String],
                                                 permissionRelevantAssertions: Seq[(IRI, IRI)],
                                                 userProfile: UserProfileV1): Future[ChangeValueResponseV1] = {
        for {
        // If we're adding a text value, update direct links and LinkValues for any resource references in Standoff.
            standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- (currentValueV1, updateValueV1) match {
                case (currentTextValue: TextValueV1, newTextValue: TextValueV1) =>
                    // Make sure the new text value's list of resource references is correct.
                    checkTextValueResourceRefs(newTextValue)

                    // Identify the resource references that have been added or removed in the new version of
                    // the value.
                    val currentResourceRefs = currentTextValue.resource_reference.toSet
                    val newResourceRefs = newTextValue.resource_reference.toSet
                    val addedResourceRefs = newResourceRefs -- currentResourceRefs
                    val removedResourceRefs = currentResourceRefs -- newResourceRefs

                    // Construct a SparqlTemplateLinkUpdate for each reference that was added.
                    val standoffLinkUpdatesForAddedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] =
                        addedResourceRefs.toVector.map {
                            targetResourceIri =>
                                incrementLinkValue(
                                    sourceResourceIri = resourceIri,
                                    linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                                    targetResourceIri = targetResourceIri,
                                    permissionRelevantAssertions = permissionRelevantAssertions,
                                    userProfile = userProfile
                                )
                        }

                    // Construct a SparqlTemplateLinkUpdate for each reference that was removed.
                    val standoffLinkUpdatesForRemovedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] =
                        removedResourceRefs.toVector.map {
                            removedTargetResource =>
                                decrementLinkValue(
                                    sourceResourceIri = resourceIri,
                                    linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                                    removedTargetResourceIri = removedTargetResource,
                                    userProfile = userProfile
                                )
                        }

                    Future.sequence(standoffLinkUpdatesForAddedResourceRefs ++ standoffLinkUpdatesForRemovedResourceRefs)

                case _ => Future(Vector.empty[SparqlTemplateLinkUpdate])
            }

            // Generate a SPARQL update.
            sparqlUpdate = queries.sparql.v1.txt.addValueVersion(
                dataNamedGraph = settings.projectNamedGraphs(projectIri).data,
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                currentValueIri = currentValueIri,
                newValueIri = newValueIri,
                valueTypeIri = updateValueV1.valueTypeIri,
                permissionRelevantAssertions = permissionRelevantAssertions,
                value = updateValueV1,
                maybeComment = comment,
                linkUpdates = standoffLinkUpdates
            ).toString()

            /*
            _ = println("================ Update value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
            */

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

            // To find out whether the update succeeded, look for the new value in the triplestore.
            verifyUpdateResult <- verifyValueUpdate(
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                searchValueIri = newValueIri,
                userProfile = userProfile
            )
        } yield ChangeValueResponseV1(
            value = verifyUpdateResult.value,
            comment = verifyUpdateResult.comment,
            id = newValueIri,
            rights = verifyUpdateResult.permissionCode,
            userdata = userProfile.userData
        )
    }

    /**
      * Generates a [[SparqlTemplateLinkUpdate]] object for a resource reference that has been added to a resource.
      *
      * @param sourceResourceIri            the resource containing the resource reference.
      * @param linkPropertyIri              the IRI of the property that links the source resource to the target resource.
      * @param targetResourceIri            the target resource for which a reference has been added.
      * @param permissionRelevantAssertions the permission-relevant assertions to be used for a new `knora-base:LinkValue`
      *                                     (as opposed to a new version of an existing `LinkValue`).
      * @param userProfile                  the profile of the user making the request.
      * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
      */
    private def incrementLinkValue(sourceResourceIri: IRI, linkPropertyIri: IRI, targetResourceIri: IRI, permissionRelevantAssertions: Seq[(IRI, IRI)], userProfile: UserProfileV1): Future[SparqlTemplateLinkUpdate] = {
        for {
            maybeLinkValueQueryResult <- findLinkValueByObject(
                subjectIri = sourceResourceIri,
                predicateIri = linkPropertyIri,
                objectIri = targetResourceIri,
                userProfile = userProfile
            )

            // Generate an IRI for the new LinkValue.
            newLinkValueIri = knoraIriUtil.makeRandomValueIri(sourceResourceIri)

            linkUpdate = maybeLinkValueQueryResult match {
                case Some(linkValueQueryResult) =>
                    // There's already a LinkValue for links between these two resources. Increment
                    // its reference count.
                    val currentReferenceCount = linkValueQueryResult.value.referenceCount
                    val newReferenceCount = currentReferenceCount + 1
                    val insertDirectLink = !linkValueQueryResult.directLinkExists

                    SparqlTemplateLinkUpdate(
                        linkPropertyIri = linkPropertyIri,
                        directLinkExists = linkValueQueryResult.directLinkExists,
                        insertDirectLink = insertDirectLink,
                        deleteDirectLink = false,
                        linkValueExists = true,
                        newLinkValueIri = newLinkValueIri,
                        linkTargetIri = targetResourceIri,
                        currentReferenceCount = currentReferenceCount,
                        newReferenceCount = newReferenceCount,
                        permissionRelevantAssertions = linkValueQueryResult.permissionRelevantAssertions
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
                        newLinkValueIri = newLinkValueIri,
                        linkTargetIri = targetResourceIri,
                        currentReferenceCount = 0,
                        newReferenceCount = 1,
                        permissionRelevantAssertions = permissionRelevantAssertions
                    )
            }
        } yield linkUpdate
    }

    /**
      * Generates a [[SparqlTemplateLinkUpdate]] for a resource reference that has been removed from a resource.
      *
      * @param sourceResourceIri        the resource containing the resource reference.
      * @param linkPropertyIri          the IRI of the property that links the source resource to the target resource.
      * @param removedTargetResourceIri the target resources for which a reference has been removed.
      * @param userProfile              the profile of the user making the request.
      * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
      */
    private def decrementLinkValue(sourceResourceIri: IRI, linkPropertyIri: IRI, removedTargetResourceIri: IRI, userProfile: UserProfileV1): Future[SparqlTemplateLinkUpdate] = {
        for {
            maybeLinkValueQueryResult <- findLinkValueByObject(
                subjectIri = sourceResourceIri,
                predicateIri = linkPropertyIri,
                objectIri = removedTargetResourceIri,
                userProfile = userProfile
            )

            linkUpdate = maybeLinkValueQueryResult match {
                case Some(linkValueQueryResult) =>
                    // There's already a LinkValue for links between these two resources. Decrement its
                    // reference count.
                    val currentReferenceCount = linkValueQueryResult.value.referenceCount
                    val newReferenceCount = currentReferenceCount - 1
                    val deleteDirectLink = linkValueQueryResult.directLinkExists && newReferenceCount == 0

                    // Generate an IRI for the new LinkValue.
                    val newLinkValueIri = knoraIriUtil.makeRandomValueIri(sourceResourceIri)

                    SparqlTemplateLinkUpdate(
                        linkPropertyIri = linkPropertyIri,
                        directLinkExists = linkValueQueryResult.directLinkExists,
                        insertDirectLink = false,
                        deleteDirectLink = deleteDirectLink,
                        linkValueExists = true,
                        newLinkValueIri = newLinkValueIri,
                        linkTargetIri = removedTargetResourceIri,
                        currentReferenceCount = currentReferenceCount,
                        newReferenceCount = newReferenceCount,
                        permissionRelevantAssertions = linkValueQueryResult.permissionRelevantAssertions
                    )

                case None =>
                    // This shouldn't happen.
                    throw InconsistentTriplestoreDataException(s"There should be a knora-base:LinkValue describing a direct link from resource $sourceResourceIri to resource $removedTargetResourceIri using property $linkPropertyIri, but it seems to be missing")
            }
        } yield linkUpdate
    }

    /**
      * Checks a [[TextValueV1]] to make sure that the resource references in its [[StandoffPositionV1]] objects match
      * the list of resource IRIs in its `resource_reference` member variable.
      *
      * @param textValue the [[TextValueV1]] to be checked.
      */
    @throws(classOf[BadRequestException])
    private def checkTextValueResourceRefs(textValue: TextValueV1): Unit = {
        val resourceRefsInStandoff: Set[IRI] = textValue.textattr.get(StandoffConstantsV1.LINK_ATTR) match {
            case Some(positions) => positions.flatMap(_.resid).toSet
            case None => Set.empty[IRI]
        }

        if (resourceRefsInStandoff != textValue.resource_reference.toSet) {
            throw BadRequestException(s"The list of resource references in this text value does not match the resource references in its Standoff markup: $textValue")
        }
    }

    /**
      * Implements a pre-update check to ensure that an [[UpdateValueV1]] has the correct type for the `knora-base:objectClassConstraint` of
      * the property that is supposed to point to it.
      *
      * @param propertyIri the IRI of the property.
      * @param propertyObjectClassConstraint the IRI of the `knora-base:objectClassConstraint` of the property.
      * @param updateValueV1 the value to be updated.
      * @param userProfile   the profile of the user making the request.
      * @return an empty [[Future]] on success, or a failed [[Future]] if the value has the wrong type.
      */
    private def checkPropertyObjectClassConstraintForValue(propertyIri: IRI, propertyObjectClassConstraint: IRI, updateValueV1: UpdateValueV1, userProfile: UserProfileV1): Future[Unit] = {
        for {
            result <- updateValueV1 match {
                case linkUpdate: LinkUpdateV1 =>
                    // We're creating a link. Ask the resources responder to check the OWL class of the target resource.
                    for {
                        checkTargetClassResponse <- (responderManager ? ResourceCheckClassRequestV1(
                            resourceIri = linkUpdate.targetResourceIri,
                            owlClass = propertyObjectClassConstraint,
                            userProfile = userProfile
                        )).mapTo[ResourceCheckClassResponseV1]

                        _ = if (!checkTargetClassResponse.isInClass) {
                            throw OntologyConstraintException(s"Resource ${linkUpdate.targetResourceIri} cannot be the target of property $propertyIri, because it is not a member of OWL class $propertyObjectClassConstraint")
                        }
                    } yield ()

                case otherValue =>
                    // We're creating an ordinary value. Check that its type is valid for the property's knora-base:objectClassConstraint.
                    valueUtilV1.checkValueTypeForPropertyObjectClassConstraint(
                        propertyIri = propertyIri,
                        propertyObjectClassConstraint = propertyObjectClassConstraint,
                        valueType = otherValue.valueTypeIri,
                        responderManager = responderManager)
            }
        } yield result
    }
}
