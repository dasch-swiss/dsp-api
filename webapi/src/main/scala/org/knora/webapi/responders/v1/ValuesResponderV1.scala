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
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiConstants, SipiResponderConversionPathRequestV1, SipiResponderConversionRequestV1, SipiResponderConversionResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.usermessages.{UserProfileGetRequestV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.twirl.{SparqlTemplateLinkUpdate, StandoffTagV1}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util._

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future

/**
  * Updates Knora values.
  */
class ValuesResponderV1 extends ResponderV1 {
    // Creates IRIs for new Knora value objects.
    val knoraIdUtil = new KnoraIdUtil

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
        case changeFileValueRequest: ChangeFileValueRequestV1 => future2Message(sender(), changeFileValueV1(changeFileValueRequest), log)
        case changeCommentRequest: ChangeCommentRequestV1 => future2Message(sender(), changeCommentV1(changeCommentRequest), log)
        case deleteValueRequest: DeleteValueRequestV1 => future2Message(sender(), deleteValueV1(deleteValueRequest), log)
        case createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV1 => future2Message(sender(), createMultipleValuesV1(createMultipleValuesRequest), log)
        case verifyMultipleValueCreationRequest: VerifyMultipleValueCreationRequestV1 => future2Message(sender(), verifyMultipleValueCreation(verifyMultipleValueCreationRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log)
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

            response <- maybeValueQueryResult match {
                case Some(valueQueryResult) =>
                    for {
                        valueOwnerProfile <- (responderManager ? UserProfileGetRequestV1(valueQueryResult.ownerIri)).mapTo[UserProfileV1]
                    } yield ValueGetResponseV1(
                        valuetype = valueQueryResult.value.valueTypeIri,
                        rights = valueQueryResult.permissionCode,
                        value = valueQueryResult.value,
                        valuecreator = valueOwnerProfile.userData.username.get,
                        valuecreatorname = valueOwnerProfile.userData.fullname.get,
                        valuecreationdate = valueQueryResult.creationDate,
                        comment = valueQueryResult.comment,
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
          * called by [[IriLocker]] once it has acquired an update lock on the resource.
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

            _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = resourcePermissionCode, userNeedsPermission = OntologyConstants.KnoraBase.ModifyPermission)) {
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

            // Everything seems OK, so we can create the value.

            // Construct its permissions from the default permissions of the resource property.
            permissionsFromDefaults: Option[String] = PermissionUtilV1.makePermissionsFromEntityDefaults(propertyInfo)

            // Create the new value.
            unverifiedValue <- createValueV1AfterChecks(
                projectIri = resourceFullResponse.resinfo.get.project_id,
                resourceIri = createValueRequest.resourceIri,
                propertyIri = createValueRequest.propertyIri,
                value = createValueRequest.value,
                comment = createValueRequest.comment,
                valueOwner = userIri, // Make the requesting user the owner.
                valueProject = resourceFullResponse.resinfo.get.project_id, // Give the new value the same project as the containing resource
                valuePermissions = permissionsFromDefaults,
                updateResourceLastModificationDate = true,
                userProfile = createValueRequest.userProfile)

            // Verify that it was created.
            apiResponse <- verifyValueCreation(
                resourceIri = createValueRequest.resourceIri,
                propertyIri = createValueRequest.propertyIri,
                unverifiedValue = unverifiedValue,
                userProfile = createValueRequest.userProfile
            )
        } yield apiResponse

        for {
        // Don't allow anonymous users to create values.
            userIri <- createValueRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to create values"))
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- IriLocker.runWithIriLock(
                createValueRequest.apiRequestID,
                createValueRequest.resourceIri,
                () => makeTaskFuture(userIri)
            )
        } yield taskResult
    }

    /**
      * Generates SPARQL for creating multiple values in a new, empty resource, using an existing transaction.
      * The resource ''must'' be a new, empty resource, i.e. it must have no values. All pre-update checks must already
      * have been performed. Specifically, this method assumes that:
      *
      * - The requesting user has permission to add values to the resource.
      * - Each submitted value is consistent with the `knora-base:objectClassConstraint` of the property that is supposed to point to it.
      * - The resource has a suitable cardinality for each submitted value.
      * - All required values are provided.
      *
      * @param createMultipleValuesRequest the request message.
      * @return a [[GenerateSparqlToCreateMultipleValuesResponseV1]].
      */
    private def createMultipleValuesV1(createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV1): Future[GenerateSparqlToCreateMultipleValuesResponseV1] = {
        /**
          * Creates a [[Future]] that performs the update. This function will be called by [[IriLocker]] once it
          * has acquired an update lock on the resource.
          *
          * @param userIri the IRI of the user making the request.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI): Future[GenerateSparqlToCreateMultipleValuesResponseV1] = {
            /**
              * Assists in the numbering of values to be created.
              *
              * @param createValueV1WithComment the value to be created.
              * @param valueIndex               the index of the value in the sequence of all values to be created. This will be used
              *                                 to generate unique SPARQL variable names.
              * @param valueHasOrder            the index of the value in the sequence of values to be created for a particular property.
              *                                 This will be used to generate `knora-base:valueHasOrder`.
              */
            case class NumberedValueToCreate(createValueV1WithComment: CreateValueV1WithComment,
                                             valueIndex: Int,
                                             valueHasOrder: Int)

            /**
              * Assists in collecting generated SPARQL as well as other information about values to be created for
              * a particular property.
              *
              * @param whereSparql    statements to be included in the SPARQL WHERE clause.
              * @param insertSparql   statements to be included in the SPARQL INSERT clause.
              * @param valuesToVerify information about each value to be created.
              * @param valueIndexes   the value index of each value described by this object (so they can be sorted).
              */
            case class SparqlGenerationResultForProperty(whereSparql: Vector[String] = Vector.empty[String],
                                                         insertSparql: Vector[String] = Vector.empty[String],
                                                         valuesToVerify: Vector[UnverifiedValueV1] = Vector.empty[UnverifiedValueV1],
                                                         valueIndexes: Vector[Int] = Vector.empty[Int])

            for {
            // Get ontology information about the default permissions on the resource's properties.
                entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
                    propertyIris = createMultipleValuesRequest.values.keySet,
                    userProfile = createMultipleValuesRequest.userProfile
                )).mapTo[EntityInfoGetResponseV1]

                ////////////////////////////////////////////////////////////////////////////////////////////////////////////
                // Generate SPARQL to create links and LinkValues for standoff resource references in text values

                // To create LinkValues for the standoff resource references in the values to be created, we need to compute
                // the initial reference count of each LinkValue. This is equal to the number of TextValues in the resource
                // that have standoff references to a particular target resource.

                // First, make a single list of all the values to be created.
                valuesToCreatePerProperty: Map[IRI, Seq[CreateValueV1WithComment]] = createMultipleValuesRequest.values
                valuesToCreateForAllProperties: Iterable[Seq[CreateValueV1WithComment]] = valuesToCreatePerProperty.values
                allValuesToCreate: Iterable[CreateValueV1WithComment] = valuesToCreateForAllProperties.flatten

                // Then, get the standoff resource references from all the text values to be created.
                // The 'collect' method builds a new list by applying a partial function to all elements of the list
                // on which the function is defined.
                resourceReferencesForAllTextValues: Iterable[Set[IRI]] = allValuesToCreate.collect {
                    case CreateValueV1WithComment(textValueV1: TextValueV1WithStandoff, _) =>
                        // check that resource references are consistent in `resource_reference` and linking standoff tags
                        checkTextValueResourceRefs(textValueV1)

                        textValueV1.resource_reference
                }

                // Combine those resource references into a single list, so if there are n text values with a reference to
                // some IRI, the list will contain that IRI n times.
                allResourceReferences: Iterable[IRI] = resourceReferencesForAllTextValues.flatten

                // Now we need to count the number of times each IRI occurs in allResourceReferences. To do this, first
                // use groupBy(identity). The groupBy method takes a function that returns a key for each item in the
                // collection, and makes a Map in which items with the same key are grouped together. The identity
                // function just returns its argument. So groupBy(identity) makes a Map[IRI, Iterable[IRI]] in which each
                // IRI points to a sequence of the same IRI repeated as many times as it occurred in allResourceReferences.
                allResourceReferencesGrouped: Map[IRI, Iterable[IRI]] = allResourceReferences.groupBy(identity)

                // Finally, replace each Iterable[IRI] with its size. That's the number of text values containing
                // standoff references to that IRI.
                targetIris: Map[IRI, Int] = allResourceReferencesGrouped.mapValues(_.size)

                // For each target IRI, construct a SparqlTemplateLinkUpdate to create one link, as well as one LinkValue
                // with associated count as its initial reference count.
                standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] = targetIris.toSeq.map {
                    case (targetIri, initialReferenceCount) =>
                        SparqlTemplateLinkUpdate(
                            linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                            directLinkExists = false,
                            insertDirectLink = true,
                            deleteDirectLink = false,
                            linkValueExists = false,
                            newLinkValueIri = knoraIdUtil.makeRandomValueIri(createMultipleValuesRequest.resourceIri),
                            linkTargetIri = targetIri,
                            currentReferenceCount = 0,
                            newReferenceCount = initialReferenceCount,
                            newLinkValueOwner = OntologyConstants.KnoraBase.SystemUser,
                            newLinkValueProject = None,
                            newLinkValuePermissions = None
                        )
                }

                // Generate INSERT clause statements based on those SparqlTemplateLinkUpdates.
                standoffLinkInsertSparql: String = queries.sparql.v1.txt.generateInsertStatementsForStandoffLinks(linkUpdates = standoffLinkUpdates).toString()

                ////////////////////////////////////////////////////////////////////////////////////////////////////////////
                // Number each value to be created, and give it a valueHasOrder

                // Ungroup the values to be created so we can number them as a single sequence (to create unique SPARQL variable names for each value).
                ungroupedValues: Seq[(IRI, CreateValueV1WithComment)] = createMultipleValuesRequest.values.toSeq.flatMap {
                    case (propertyIri, values) => values.map(value => (propertyIri, value))
                }

                // Number them all as a single sequence. Give each one a knora-base:valueHasOrder of 0 for now; we'll take care of that in a moment.
                numberedValues: Seq[(IRI, NumberedValueToCreate)] = ungroupedValues.zipWithIndex.map {
                    case ((propertyIri: IRI, valueWithComment: CreateValueV1WithComment), valueIndex) => (propertyIri, NumberedValueToCreate(valueWithComment, valueIndex, 0))
                }

                // Group them again by property so we generate knora-base:valueHasOrder for the values of each property.
                groupedNumberedValues: Map[IRI, Seq[NumberedValueToCreate]] = numberedValues.groupBy(_._1).map {
                    case (propertyIri, propertyIriAndValueTuples) => (propertyIri, propertyIriAndValueTuples.map(_._2))
                }

                // Generate knora-base:valueHasOrder for the values of each property.
                groupedNumberedValuesWithValueHasOrder: Map[IRI, Seq[NumberedValueToCreate]] = groupedNumberedValues.map {
                    case (propertyIri, values) =>
                        val valuesWithValueHasOrder = values.zipWithIndex.map {
                            case (numberedValueToCreate, valueHasOrder) => numberedValueToCreate.copy(valueHasOrder = valueHasOrder)
                        }

                        (propertyIri, valuesWithValueHasOrder)
                }

                ////////////////////////////////////////////////////////////////////////////////////////////////////////////
                // Generate SPARQL for each value of each property

                // Make a SparqlGenerationResultForProperty for each property.
                sparqlGenerationResults: Map[IRI, SparqlGenerationResultForProperty] = groupedNumberedValuesWithValueHasOrder.map {
                    case (propertyIri: IRI, valuesToCreate: Seq[NumberedValueToCreate]) =>
                        // Construct a list of permission-relevant assertions about the new values of each property,
                        // i.e. the values' owner and project plus their permissions. Use the property's default
                        // permissions to make permissions for the new values.
                        val propertyInfo = entityInfoResponse.propertyEntityInfoMap(propertyIri)
                        val permissionsFromDefaults: Option[String] = PermissionUtilV1.makePermissionsFromEntityDefaults(propertyInfo)

                        // For each property, construct a SparqlGenerationResultForProperty containing WHERE clause statements, INSERT clause statements, and UnverifiedValueV1s.
                        val sparqlGenerationResultForProperty: SparqlGenerationResultForProperty = valuesToCreate.foldLeft(SparqlGenerationResultForProperty()) {
                            case (propertyAcc: SparqlGenerationResultForProperty, valueToCreate: NumberedValueToCreate) =>
                                val updateValueV1 = valueToCreate.createValueV1WithComment.updateValueV1
                                val newValueIri = knoraIdUtil.makeRandomValueIri(createMultipleValuesRequest.resourceIri)

                                // How we generate the SPARQL depends on whether we're creating a link or an ordinary value.
                                val (whereSparql: String, insertSparql: String) = valueToCreate.createValueV1WithComment.updateValueV1 match {
                                    case linkUpdateV1: LinkUpdateV1 =>
                                        // We're creating a link.

                                        // Construct a SparqlTemplateLinkUpdate to tell the SPARQL templates how to create
                                        // the link and its LinkValue.
                                        val sparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
                                            linkPropertyIri = propertyIri,
                                            directLinkExists = false,
                                            insertDirectLink = true,
                                            deleteDirectLink = false,
                                            linkValueExists = false,
                                            newLinkValueIri = newValueIri,
                                            linkTargetIri = linkUpdateV1.targetResourceIri,
                                            currentReferenceCount = 0,
                                            newReferenceCount = 1,
                                            newLinkValueOwner = userIri,
                                            newLinkValueProject = Some(createMultipleValuesRequest.projectIri),
                                            newLinkValuePermissions = permissionsFromDefaults
                                        )

                                        // Generate WHERE clause statements for the link.
                                        val whereSparql = queries.sparql.v1.txt.generateWhereStatementsForCreateLink(
                                            valueIndex = valueToCreate.valueIndex,
                                            resourceIri = createMultipleValuesRequest.resourceIri,
                                            linkUpdate = sparqlTemplateLinkUpdate,
                                            maybeValueHasOrder = Some(valueToCreate.valueHasOrder)
                                        ).toString()

                                        // Generate INSERT clause statements for the link.
                                        val insertSparql = queries.sparql.v1.txt.generateInsertStatementsForCreateLink(
                                            valueIndex = valueToCreate.valueIndex,
                                            linkUpdate = sparqlTemplateLinkUpdate,
                                            maybeComment = valueToCreate.createValueV1WithComment.comment
                                        ).toString()

                                        (whereSparql, insertSparql)

                                    case ordinaryValue =>
                                        // We're creating an ordinary value.

                                        // Generate WHERE clause statements for the value.
                                        val whereSparql = queries.sparql.v1.txt.generateWhereStatementsForCreateValue(
                                            valueIndex = valueToCreate.valueIndex,
                                            resourceIri = createMultipleValuesRequest.resourceIri,
                                            propertyIri = propertyIri,
                                            newValueIri = newValueIri,
                                            valueTypeIri = updateValueV1.valueTypeIri,
                                            linkUpdates = Seq.empty[SparqlTemplateLinkUpdate], // This is empty because we have to generate SPARQL for standoff links separately.
                                            maybeValueHasOrder = Some(valueToCreate.valueHasOrder)
                                        ).toString()

                                        // Generate INSERT clause statements for the value.
                                        val insertSparql = queries.sparql.v1.txt.generateInsertStatementsForCreateValue(
                                            valueIndex = valueToCreate.valueIndex,
                                            propertyIri = propertyIri,
                                            value = updateValueV1,
                                            newValueIri = newValueIri,
                                            linkUpdates = Seq.empty[SparqlTemplateLinkUpdate], // This is empty because we have to generate SPARQL for standoff links separately.
                                            maybeComment = valueToCreate.createValueV1WithComment.comment,
                                            valueOwner = userIri,
                                            valueProject = createMultipleValuesRequest.projectIri,
                                            maybeValuePermissions = permissionsFromDefaults
                                        ).toString()

                                        //println(insertSparql)

                                        (whereSparql, insertSparql)
                                }

                                // For each value of the property, accumulate the generated SPARQL and an UnverifiedValueV1
                                // in the SparqlGenerationResultForProperty.
                                propertyAcc.copy(
                                    whereSparql = propertyAcc.whereSparql :+ whereSparql,
                                    insertSparql = propertyAcc.insertSparql :+ insertSparql,
                                    valuesToVerify = propertyAcc.valuesToVerify :+ UnverifiedValueV1(newValueIri = newValueIri, value = updateValueV1),
                                    valueIndexes = propertyAcc.valueIndexes :+ valueToCreate.valueIndex
                                )
                        }

                        propertyIri -> sparqlGenerationResultForProperty
                }

                // Concatenate all the generated SPARQL into one string for the WHERE clause and one string for the INSERT clause, sorting
                // the values by their indexes.

                resultsForAllProperties: Iterable[SparqlGenerationResultForProperty] = sparqlGenerationResults.values
                allWhereSparql: String = resultsForAllProperties.flatMap(result => result.whereSparql.zip(result.valueIndexes)).toSeq.sortBy(_._2).map(_._1).mkString("\n\n")

                // The SPARQL for the INSERT clause also contains the SPARQL that was generated to insert standoff links.
                allInsertSparql: String = resultsForAllProperties.flatMap(result => result.insertSparql.zip(result.valueIndexes)).toSeq.sortBy(_._2).map(_._1).mkString("\n\n") + standoffLinkInsertSparql

                // Collect all the UnverifiedValueV1s for each property.
                allUnverifiedValues: Map[IRI, Seq[UnverifiedValueV1]] = sparqlGenerationResults.map {
                    case (propertyIri, results) => propertyIri -> results.valuesToVerify
                }

            } yield GenerateSparqlToCreateMultipleValuesResponseV1(
                whereSparql = allWhereSparql,
                insertSparql = allInsertSparql,
                unverifiedValues = allUnverifiedValues
            )
        }

        for {
        // Don't allow anonymous users to create resources.
            userIri <- createMultipleValuesRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to create resources"))
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- IriLocker.runWithIriLock(
                createMultipleValuesRequest.apiRequestID,
                createMultipleValuesRequest.resourceIri,
                () => makeTaskFuture(userIri)
            )
        } yield taskResult
    }

    /**
      * Verifies the creation of multiple values.
      *
      * @param verifyRequest a [[VerifyMultipleValueCreationRequestV1]].
      * @return a [[VerifyMultipleValueCreationResponseV1]] if all values were created successfully, or a failed
      *         future if any values were not created.
      */
    private def verifyMultipleValueCreation(verifyRequest: VerifyMultipleValueCreationRequestV1): Future[VerifyMultipleValueCreationResponseV1] = {
        // We have a Map of property IRIs to sequences of UnverifiedCreateValueResponseV1s. Query each value and
        // build a Map with the same structure, except that instead of UnverifiedCreateValueResponseV1s, it contains Futures
        // providing the results of querying the values.
        val valueVerificationFutures: Map[IRI, Future[Seq[CreateValueResponseV1]]] = verifyRequest.unverifiedValues.map {
            case (propertyIri: IRI, unverifiedValues: Seq[UnverifiedValueV1]) =>
                val valueVerificationResponsesForProperty = unverifiedValues.map {
                    unverifiedValue =>
                        verifyValueCreation(
                            resourceIri = verifyRequest.resourceIri,
                            propertyIri = propertyIri,
                            unverifiedValue = unverifiedValue,
                            userProfile = verifyRequest.userProfile
                        )
                }

                propertyIri -> Future.sequence(valueVerificationResponsesForProperty)
        }

        // Convert our Map full of Futures into one Future, which will provide a Map of all the results
        // when they're available.
        for {
            valueVerificationResponses: Map[IRI, Seq[CreateValueResponseV1]] <- ActorUtil.sequenceFuturesInMap(valueVerificationFutures)
        } yield VerifyMultipleValueCreationResponseV1(verifiedValues = valueVerificationResponses)
    }

    /**
      * Adds a new version of an existing file value.
      *
      * @param changeFileValueRequest a [[ChangeFileValueRequestV1]] sent by the values route.
      * @return a [[ChangeFileValueResponseV1]] representing all the changed file values.
      */
    private def changeFileValueV1(changeFileValueRequest: ChangeFileValueRequestV1): Future[ChangeFileValueResponseV1] = {

        /**
          * Temporary structure to represent existing file values of a resource.
          *
          * @param property       the property Iri (e.g., hasStillImageFileValueRepresentation)
          * @param valueObjectIri the Iri of the value object.
          * @param quality        the quality of the file value
          */
        case class CurrentFileValue(property: IRI, valueObjectIri: IRI, quality: Option[Int])

        def changeFileValue(oldFileValue: CurrentFileValue, newFileValue: FileValueV1): Future[ChangeValueResponseV1] = {
            changeValueV1(ChangeValueRequestV1(
                valueIri = oldFileValue.valueObjectIri,
                value = newFileValue,
                userProfile = changeFileValueRequest.userProfile,
                apiRequestID = changeFileValueRequest.apiRequestID // re-use the same id
            ))
        }

        /**
          * Preprocesses a file value change request by calling the Sipi responder to create a new file
          * and calls [[changeValueV1]] to actually change the file value in Knora.
          *
          * @param changeFileValueRequest a [[ChangeFileValueRequestV1]] sent by the values route.
          * @return a [[ChangeFileValueResponseV1]] representing all the changed file values.
          */
        def makeTaskFuture(changeFileValueRequest: ChangeFileValueRequestV1): Future[ChangeFileValueResponseV1] = {

            // get the Iris of the current file value(s)
            val resultFuture = for {

                resourceIri <- Future(changeFileValueRequest.resourceIri)

                getFileValuesSparql = queries.sparql.v1.txt.getFileValuesForResource(
                    triplestore = settings.triplestoreType,
                    resourceIri = resourceIri
                ).toString()
                //_ = print(getFileValuesSparql)
                getFileValuesResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(getFileValuesSparql)).mapTo[SparqlSelectResponse]
                // _ <- Future(println(getFileValuesResponse))

                // check that the resource to be updated exists and it is a subclass of knora-base:Representation
                _ = if (getFileValuesResponse.results.bindings.isEmpty) throw NotFoundException(s"Value ${changeFileValueRequest.resourceIri} not found (it may have been deleted) or it is not a knora-base:Representation")

                // get the property Iris, file value Iris and qualities attached to the resource
                fileValues: Seq[CurrentFileValue] = getFileValuesResponse.results.bindings.map {
                    (row: VariableResultsRow) =>

                        CurrentFileValue(
                            property = row.rowMap("p"),
                            valueObjectIri = row.rowMap("fileValueIri"),
                            quality = row.rowMap.get("quality") match {
                                case Some(quality: String) => Some(quality.toInt)
                                case None => None
                            }
                        )
                }

                // the message to be sent to Sipi responder
                sipiConversionRequest: SipiResponderConversionRequestV1 = changeFileValueRequest.file

                sipiResponse: SipiResponderConversionResponseV1 <- (responderManager ? sipiConversionRequest).mapTo[SipiResponderConversionResponseV1]

                // check if the file type returned by Sipi corresponds to the already existing file value type (e.g., hasStillImageRepresentation)
                _ = if (SipiConstants.fileType2FileValueProperty(sipiResponse.file_type) != fileValues.head.property) {
                    // TODO: remove the file from SIPI (delete request)
                    throw BadRequestException(s"Type of submitted file (${sipiResponse.file_type}) does not correspond to expected property type ${fileValues.head.property}")
                }

                //
                // handle file types individually
                //

                // create the apt case class depending on the file type returned by Sipi
                changeValueResponses: Vector[ChangeValueResponseV1] <- sipiResponse.file_type match {
                    case SipiConstants.FileType.IMAGE =>
                        // we deal with hasStillImageFileValue, so there need to be two file values:
                        // one for the full and one for the thumb
                        if (fileValues.size != 2) {
                            throw InconsistentTriplestoreDataException(s"Expected 2 file values for $resourceIri, but ${fileValues.size} given.")
                        }

                        // make sure that we have quality information for the existing file values
                        fileValues.foreach {
                            (fileValue) => fileValue.quality.getOrElse(throw InconsistentTriplestoreDataException(s"No quality level given for ${fileValue.valueObjectIri}"))
                        }

                        // sort file values by quality: the thumbnail file value has to be updated with another thumbnail file value,
                        // the applies for the full quality
                        val oldFileValuesSorted: Seq[CurrentFileValue] = fileValues.sortBy(_.quality)
                        val newFileValuesSorted: Vector[FileValueV1] = sipiResponse.fileValuesV1.sortBy {
                            case imageFileValue: StillImageFileValueV1 => imageFileValue.qualityLevel
                            case otherFileValue: FileValueV1 => throw SipiException(s"Sipi returned a wrong file value type: ${otherFileValue.valueTypeIri}")
                        }

                        val valuesToChange = oldFileValuesSorted.zip(newFileValuesSorted)
                        val (firstOldValue, firstNewValue) = valuesToChange.head
                        val (secondOldValue, secondNewValue) = valuesToChange(1)

                        //
                        // Change the file values sequentially (because concurrent SPARQL updates could interfere with each other).
                        //
                        for {
                            firstResult <- changeFileValue(firstOldValue, firstNewValue)
                            secondResult <- changeFileValue(secondOldValue, secondNewValue)
                        } yield Vector(firstResult, secondResult)


                    case otherFileType => throw NotImplementedException(s"File type $otherFileType not yet supported")
                }

            } yield ChangeFileValueResponseV1(// return the response(s) of the call(s) of changeValueV1
                locations = changeValueResponses.map {
                    changeValueResponse =>
                        val fileValue: FileValueV1 = changeValueResponse.value match {
                            case fileValueV1: FileValueV1 => fileValueV1
                            case other => throw AssertionException(s"Expected Sipi to change a file value, but it changed one of these: ${other.valueTypeIri}")
                        }

                        valueUtilV1.fileValueV12LocationV1(fileValue)
                },
                userdata = changeFileValueRequest.userProfile.userData
            )

            // If a temporary file was created, ensure that it's deleted, regardless of whether the request succeeded or failed.
            resultFuture.andThen {
                case _ => changeFileValueRequest.file match {
                    case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                        // a tmp file has been created by the resources route (non GUI-case), delete it
                        InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source, log)
                    case _ => ()
                }


            }
        }

        for {

        // Do the preparations of a file value change while already holding an update lock on the resource.
        // This is necessary because in `makeTaskFuture` the current file value Iris for the given resource Iri have to been retrieved.
        // Using the lock, we make sure that these are still up to date when `changeValueV1` is being called.
        //
        // The method `changeValueV1` will be called using the same lock.
            taskResult <- IriLocker.runWithIriLock(
                changeFileValueRequest.apiRequestID,
                changeFileValueRequest.resourceIri,
                () => makeTaskFuture(changeFileValueRequest)
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
          * called by [[IriLocker]] once it has acquired an update lock on the resource.
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
                case linkUpdateV1: LinkUpdateV1 => knoraIdUtil.linkValuePropertyIri2LinkPropertyIri(findResourceWithValueResult.propertyIri)
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

                _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = Some(currentValueQueryResult.permissionCode), userNeedsPermission = OntologyConstants.KnoraBase.ModifyPermission)) {
                    throw ForbiddenException(s"User $userIri does not have permission to add a new version to value ${changeValueRequest.valueIri}")
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

                // Make sure the new version would not be redundant, given the current version.
                _ = if (changeValueRequest.value.isRedundant(currentValueQueryResult.value)) {
                    throw DuplicateValueException("The submitted value is the same as the current version")
                }

                // Get details of the resource.  (We do this as late as possible because it's slower than the other checks,
                // and there's no point in doing it if the other checks fail.)
                resourceFullResponse <- (responderManager ? ResourceFullGetRequestV1(
                    iri = findResourceWithValueResult.resourceIri,
                    userProfile = changeValueRequest.userProfile,
                    getIncoming = false
                )).mapTo[ResourceFullResponseV1]

                _ = changeValueRequest.value match {
                    case fileValue: FileValueV1 => () // It is a file value, do not check for duplicates.
                    case _ => // It is not a file value.
                        // Ensure that adding the new value version would not create a duplicate value. This works in API v1 because a
                        // ResourceFullResponseV1 contains only the values that the user is allowed to see, otherwise checking for
                        // duplicate values would be a security risk. We'll have to implement this in a different way in API v2.
                        resourceFullResponse.props.flatMap(_.properties.find(_.pid == propertyIri)) match {
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

                }

                // The rest of the preparation for the update depends on whether we're changing a link or an ordinary value.
                apiResponse <- (changeValueRequest.value, currentValueQueryResult) match {
                    case (linkUpdateV1: LinkUpdateV1, currentLinkValueQueryResult: LinkValueQueryResult) =>
                        // We're updating a link. This means deleting an existing link and creating a new one, so
                        // check that the user has permission to modify the resource.
                        val resourcePermissionCode = resourceFullResponse.resdata.flatMap(resdata => resdata.rights)
                        if (!PermissionUtilV1.impliesV1(userHasPermissionCode = resourcePermissionCode, userNeedsPermission = OntologyConstants.KnoraBase.ModifyPermission)) {
                            throw ForbiddenException(s"User $userIri does not have permission to modify resource ${findResourceWithValueResult.resourceIri}")
                        }

                        // We'll need to create a new LinkValue. Use the property's default permissions to make permissions
                        // for it.
                        val permissionsFromDefaults: Option[String] = PermissionUtilV1.makePermissionsFromEntityDefaults(propertyInfo)

                        changeLinkValueV1AfterChecks(projectIri = currentValueQueryResult.projectIri,
                            resourceIri = findResourceWithValueResult.resourceIri,
                            propertyIri = propertyIri,
                            currentLinkValueV1 = currentLinkValueQueryResult.value,
                            linkUpdateV1 = linkUpdateV1,
                            comment = changeValueRequest.comment,
                            valueOwner = userIri, // Make the requesting user the owner.
                            valueProject = resourceFullResponse.resinfo.get.project_id, // Give it the same project as the containing resource.
                            valuePermissions = permissionsFromDefaults,
                            userProfile = changeValueRequest.userProfile)

                    case _ =>
                        // We're updating an ordinary value. Generate an IRI for the new version of the value.
                        val newValueIri = knoraIdUtil.makeRandomValueIri(findResourceWithValueResult.resourceIri)

                        // Give the new version the same project and permissions as the previous version.

                        val valueProject = currentValueQueryResult.permissionRelevantAssertions.find {
                            case (p, o) => p == OntologyConstants.KnoraBase.AttachedToProject
                        }.map(_._2).getOrElse(resourceFullResponse.resinfo.get.project_id)

                        val valuePermissions = currentValueQueryResult.permissionRelevantAssertions.find {
                            case (p, o) => p == OntologyConstants.KnoraBase.HasPermissions
                        }.map(_._2)

                        changeOrdinaryValueV1AfterChecks(projectIri = currentValueQueryResult.projectIri,
                            resourceIri = findResourceWithValueResult.resourceIri,
                            propertyIri = propertyIri,
                            currentValueIri = changeValueRequest.valueIri,
                            currentValueV1 = currentValueQueryResult.value,
                            newValueIri = newValueIri,
                            updateValueV1 = changeValueRequest.value,
                            comment = changeValueRequest.comment,
                            valueOwner = userIri, // Make the requesting user the owner.
                            valueProject = valueProject,
                            valuePermissions = valuePermissions,
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
            taskResult <- IriLocker.runWithIriLock(
                changeValueRequest.apiRequestID,
                findResourceWithValueResult.resourceIri,
                () => makeTaskFuture(userIri, findResourceWithValueResult)
            )
        } yield taskResult
    }

    private def changeCommentV1(changeCommentRequest: ChangeCommentRequestV1): Future[ChangeValueResponseV1] = {
        /**
          * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
          * called by [[IriLocker]] once it has acquired an update lock on the resource.
          *
          * @param userIri the IRI of the user making the request.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI, findResourceWithValueResult: FindResourceWithValueResult): Future[ChangeValueResponseV1] = {
            for {
            // Ensure that the user has permission to modify the value.

                maybeCurrentValueQueryResult: Option[ValueQueryResult] <- findValue(changeCommentRequest.valueIri, changeCommentRequest.userProfile)

                currentValueQueryResult = maybeCurrentValueQueryResult.getOrElse(throw NotFoundException(s"Value ${changeCommentRequest.valueIri} not found (it may have been deleted)"))

                _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = Some(currentValueQueryResult.permissionCode), userNeedsPermission = OntologyConstants.KnoraBase.ModifyPermission)) {
                    throw ForbiddenException(s"User $userIri does not have permission to add a new version to value ${changeCommentRequest.valueIri}")
                }

                // currentValueQueryResult.comment is an Option[String]
                _ = if (currentValueQueryResult.comment == changeCommentRequest.comment) throw DuplicateValueException("The submitted comment is the same as the current comment")

                // Everything looks OK, so update the comment.

                // Generate an IRI for the new value.
                newValueIri = knoraIdUtil.makeRandomValueIri(findResourceWithValueResult.resourceIri)

                // Generate a SPARQL update.
                sparqlUpdate = queries.sparql.v1.txt.changeComment(
                    dataNamedGraph = settings.projectNamedGraphs(findResourceWithValueResult.projectIri).data,
                    triplestore = settings.triplestoreType,
                    resourceIri = findResourceWithValueResult.resourceIri,
                    propertyIri = findResourceWithValueResult.propertyIri,
                    currentValueIri = changeCommentRequest.valueIri,
                    newValueIri = newValueIri,
                    maybeComment = changeCommentRequest.comment
                ).toString()

                // Do the update.
                sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

                // To find out whether the update succeeded, look for the new value in the triplestore.
                verifyUpdateResult <- verifyOrdinaryValueUpdate(
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
            taskResult <- IriLocker.runWithIriLock(
                changeCommentRequest.apiRequestID,
                findResourceWithValueResult.resourceIri,
                () => makeTaskFuture(userIri, findResourceWithValueResult)
            )
        } yield taskResult

    }

    /**
      * Marks a value as deleted.
      *
      * @param deleteValueRequest the request message.
      * @return a [[DeleteValueResponseV1]].
      */
    private def deleteValueV1(deleteValueRequest: DeleteValueRequestV1): Future[DeleteValueResponseV1] = {
        /**
          * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
          * called by [[IriLocker]] once it has acquired an update lock on the resource.
          *
          * @param userIri                     the IRI of the user making the request.
          * @param findResourceWithValueResult a [[FindResourceWithValueResult]] indicating which resource contains the value
          *                                    to be updated.
          * @return a [[Future]] that does pre-update checks and performs the update.
          */
        def makeTaskFuture(userIri: IRI, findResourceWithValueResult: FindResourceWithValueResult): Future[DeleteValueResponseV1] = for {
        // Ensure that the user has permission to mark the value as deleted.
            maybeCurrentValueQueryResult <- findValue(deleteValueRequest.valueIri, deleteValueRequest.userProfile)
            currentValueQueryResult = maybeCurrentValueQueryResult.getOrElse(throw NotFoundException(s"Value ${deleteValueRequest.valueIri} not found (it may have been deleted)"))

            _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = Some(currentValueQueryResult.permissionCode), userNeedsPermission = OntologyConstants.KnoraBase.DeletePermission)) {
                throw ForbiddenException(s"User $userIri does not have permission to delete value ${deleteValueRequest.valueIri}")
            }

            // The way we delete the value depends on whether it's a link value or an ordinary value.

            (sparqlUpdate, deletedValueIri) <- currentValueQueryResult.value match {
                case linkValue: LinkValueV1 =>
                    // It's a LinkValue. Make a new version of it with a reference count of 0, and mark the new
                    // version as deleted.

                    // Give the new version the same project and permissions as the previous version.

                    val valueProject: IRI = currentValueQueryResult.permissionRelevantAssertions.find {
                        case (p, o) => p == OntologyConstants.KnoraBase.AttachedToProject
                    }.map(_._2).getOrElse(findResourceWithValueResult.projectIri)

                    val valuePermissions: Option[String] = currentValueQueryResult.permissionRelevantAssertions.find {
                        case (p, o) => p == OntologyConstants.KnoraBase.HasPermissions
                    }.map(_._2)

                    val linkPropertyIri = knoraIdUtil.linkValuePropertyIri2LinkPropertyIri(findResourceWithValueResult.propertyIri)

                    for {
                        sparqlTemplateLinkUpdate <- decrementLinkValue(
                            sourceResourceIri = findResourceWithValueResult.resourceIri,
                            linkPropertyIri = linkPropertyIri,
                            targetResourceIri = linkValue.objectIri,
                            valueOwner = userIri, // Make the requesting user the owner.
                            valueProject = Some(valueProject),
                            valuePermissions = valuePermissions,
                            userProfile = deleteValueRequest.userProfile
                        )

                        sparqlUpdate = queries.sparql.v1.txt.deleteLink(
                            dataNamedGraph = settings.projectNamedGraphs(findResourceWithValueResult.projectIri).data,
                            triplestore = settings.triplestoreType,
                            linkSourceIri = findResourceWithValueResult.resourceIri,
                            linkUpdate = sparqlTemplateLinkUpdate,
                            maybeComment = deleteValueRequest.deleteComment
                        ).toString()
                    } yield (sparqlUpdate, sparqlTemplateLinkUpdate.newLinkValueIri)

                case other =>
                    // It's not a LinkValue. Mark the existing version as deleted.

                    // If it's a TextValue, make SparqlTemplateLinkUpdates for updating LinkValues representing
                    // links in standoff markup.
                    val linkUpdatesFuture: Future[Seq[SparqlTemplateLinkUpdate]] = other match {
                        case textValue: TextValueV1WithStandoff =>
                            val linkUpdateFutures = textValue.resource_reference.map {
                                targetResourceIri => decrementLinkValue(
                                    sourceResourceIri = findResourceWithValueResult.resourceIri,
                                    linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                                    targetResourceIri = targetResourceIri,
                                    valueOwner = OntologyConstants.KnoraBase.SystemUser,
                                    valueProject = None,
                                    valuePermissions = None,
                                    userProfile = deleteValueRequest.userProfile
                                )
                            }.toVector

                            Future.sequence(linkUpdateFutures)

                        case _ => Future(Seq.empty[SparqlTemplateLinkUpdate])
                    }

                    for {
                        linkUpdates <- linkUpdatesFuture

                        sparqlUpdate = queries.sparql.v1.txt.deleteValue(
                            dataNamedGraph = settings.projectNamedGraphs(findResourceWithValueResult.projectIri).data,
                            triplestore = settings.triplestoreType,
                            resourceIri = findResourceWithValueResult.resourceIri,
                            propertyIri = findResourceWithValueResult.propertyIri,
                            valueIri = deleteValueRequest.valueIri,
                            maybeDeleteComment = deleteValueRequest.deleteComment,
                            linkUpdates = linkUpdates
                        ).toString()
                    } yield (sparqlUpdate, deleteValueRequest.valueIri)
            }

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

            // Check whether the update succeeded.
            sparqlQuery = queries.sparql.v1.txt.checkValueDeletion(
                triplestore = settings.triplestoreType,
                valueIri = deletedValueIri
            ).toString()
            sparqlSelectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows = sparqlSelectResponse.results.bindings

            _ = if (rows.isEmpty || !InputValidation.optionStringToBoolean(rows.head.rowMap.get("isDeleted"))) {
                throw UpdateNotPerformedException(s"The request to mark value ${deleteValueRequest.valueIri} (or a new version of that value) as deleted did not succeed. Please report this as a possible bug.")
            }
        } yield DeleteValueResponseV1(
            id = deletedValueIri,
            userdata = deleteValueRequest.userProfile.userData
        )

        for {
        // Don't allow anonymous users to update values.
            userIri <- deleteValueRequest.userProfile.userData.user_id match {
                case Some(iri) => Future(iri)
                case None => Future.failed(ForbiddenException("Anonymous users aren't allowed to mark values as deleted"))
            }

            // Find the resource containing the value.
            findResourceWithValueResult <- findResourceWithValue(deleteValueRequest.valueIri)

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- IriLocker.runWithIriLock(
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
                queries.sparql.v1.txt.getVersionHistory(
                    triplestore = settings.triplestoreType,
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
                    val valueIri = rowMap("value")
                    val valueOwner = rowMap("valueOwner")
                    val project = rowMap("project")
                    val valuePermissions = rowMap.get("valuePermissions")

                    // Permission-checking on LinkValues is special, because they can be system-created rather than user-created.
                    val valuePermissionCode = if (InputValidation.optionStringToBoolean(rowMap.get("isLinkValue"))) {
                        // It's a LinkValue.
                        PermissionUtilV1.getUserPermissionOnLinkValueV1(
                            linkValueIri = valueIri,
                            predicateIri = rowMap("linkValuePredicate"),
                            linkValueOwner = valueOwner,
                            linkValueProject = project,
                            linkValuePermissionLiteral = valuePermissions,
                            userProfile = versionHistoryRequest.userProfile
                        )
                    } else {
                        // It's not a LinkValue.
                        PermissionUtilV1.getUserPermissionV1(
                            subjectIri = valueIri,
                            subjectOwner = valueOwner,
                            subjectProject = project,
                            subjectPermissionLiteral = valuePermissions,
                            userProfile = versionHistoryRequest.userProfile
                        )
                    }

                    valuePermissionCode.nonEmpty
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

            linkValueResponse <- maybeValueQueryResult match {
                case Some(valueQueryResult) =>
                    for {
                        valueOwnerProfile <- (responderManager ? UserProfileGetRequestV1(valueQueryResult.ownerIri)).mapTo[UserProfileV1]
                    } yield ValueGetResponseV1(
                        valuetype = valueQueryResult.value.valueTypeIri,
                        rights = valueQueryResult.permissionCode,
                        value = valueQueryResult.value,
                        valuecreator = valueOwnerProfile.userData.username.get,
                        valuecreatorname = valueOwnerProfile.userData.fullname.get,
                        valuecreationdate = valueQueryResult.creationDate,
                        comment = valueQueryResult.comment,
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
        /**
          * The value that was found.
          */
        def value: ApiValueV1

        /**
          * The IRI Of the user that created the value.
          */
        def ownerIri: IRI

        /**
          * The date when the value was created, represented as a string.
          */
        def creationDate: String

        /**
          * The IRI of the project that the value belongs to.
          */
        def projectIri: IRI

        /**
          * An optional comment describing the value.
          */
        def comment: Option[String]

        /**
          * A list of the permission-relevant assertions declared on the value.
          */
        def permissionRelevantAssertions: Seq[(IRI, IRI)]

        /**
          * An integer permission code representing the user's permissions on the value.
          */
        def permissionCode: Int
    }

    /**
      * Represents basic information resulting from querying a value. This is sufficient if the value is an ordinary
      * value (not a link).
      */
    case class BasicValueQueryResult(value: ApiValueV1,
                                     ownerIri: IRI,
                                     creationDate: String,
                                     projectIri: IRI,
                                     comment: Option[String],
                                     permissionRelevantAssertions: Seq[(IRI, IRI)],
                                     permissionCode: Int) extends ValueQueryResult

    /**
      * Represents the result of querying a link.
      *
      * @param directLinkExists             `true` if a direct link exists between the two resources.
      * @param targetResourceClass          if a direct link exists, contains the OWL class of the target resource.
      */
    case class LinkValueQueryResult(value: LinkValueV1,
                                    ownerIri: IRI,
                                    creationDate: String,
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
            sparqlQuery <- Future(queries.sparql.v1.txt.getValue(
                triplestore = settings.triplestoreType,
                valueIri = valueIri
            ).toString())
            response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows: Seq[VariableResultsRow] = response.results.bindings

            // get ontology information about data type standoff classes
            // this information is needed to handle text values with standoff
            standoffDataTypeClassEntities: StandoffClassesWithDataTypeGetResponseV1 <- (responderManager ? StandoffClassesWithDataTypeGetRequestV1(userProfile = userProfile)).mapTo[StandoffClassesWithDataTypeGetResponseV1]
            // gets information about all standoff properties
            standoffAllPropertyEntities: StandoffAllPropertyEntitiesGetResponseV1 <- (responderManager ? StandoffAllPropertyEntitiesGetRequestV1(userProfile = userProfile)).mapTo[StandoffAllPropertyEntitiesGetResponseV1]

        } yield sparqlQueryResults2ValueQueryResult(valueIri, rows, standoffDataTypeClassEntities.standoffClassEntityInfoMap, standoffAllPropertyEntities.standoffAllPropertiesEntityInfoMap, userProfile)
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
                    triplestore = settings.triplestoreType,
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
                    triplestore = settings.triplestoreType,
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
      * Converts SPARQL query results into a [[ValueQueryResult]].
      *
      * @param valueIri    the IRI of the value that was queried.
      * @param rows        the query result rows.
      * @param standoffDataTypeEntityInfoMap a [[Map]] of standoff class Iris to StandoffClassEntityInfoV1 containing all the data type standoff classes.
      * @param standoffAllPropertyEntities a [[Map]] of standoff property Iris to StandoffPropertyEntityInfoV1 containing all the standoff properties.
      * @param userProfile the profile of the user making the request.
      * @return a [[ValueQueryResult]].
      */
    @throws(classOf[ForbiddenException])
    private def sparqlQueryResults2ValueQueryResult(valueIri: IRI, rows: Seq[VariableResultsRow], standoffDataTypeEntityInfoMap: Map[IRI, StandoffClassEntityInfoV1], standoffAllPropertyEntities: Map[IRI, StandoffPropertyEntityInfoV1], userProfile: UserProfileV1): Option[ValueQueryResult] = {
        if (rows.nonEmpty) {
            // Convert the query results to a ApiValueV1.
            val valueProps = valueUtilV1.createValueProps(valueIri, rows)
            // attach the ontology information for data type standoff classes and all standoff properties.
            val value = valueUtilV1.makeValueV1(valueProps.copy(standoffClassesWithDataType = standoffDataTypeEntityInfoMap, standoffAllPropertyEntities = standoffAllPropertyEntities), responderManager, userProfile)

            // Get the IRI of the value's owner.
            val ownerIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToUser, rows = rows).getOrElse(throw InconsistentTriplestoreDataException(s"Value $valueIri has no owner"))

            // Get the value's project IRI.
            val projectIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToProject, rows = rows).getOrElse(throw InconsistentTriplestoreDataException(s"Value $valueIri has no project"))

            // Get the value's creation date.
            val creationDate = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.ValueCreationDate, rows = rows).getOrElse(throw InconsistentTriplestoreDataException(s"Value $valueIri has no valueCreationDate"))

            // Get the optional comment on the value.
            val comment = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.ValueHasComment, rows = rows)

            // Get the value's permission-relevant assertions.
            val assertions = PermissionUtilV1.filterPermissionRelevantAssertionsFromValueProps(valueProps)

            // Get the permission code representing the user's permissions on the value.
            val permissionCode = PermissionUtilV1.getUserPermissionV1FromAssertions(
                subjectIri = valueIri,
                assertions = assertions,
                userProfile = userProfile
            ).getOrElse {
                val userIri = userProfile.userData.user_id.getOrElse(OntologyConstants.KnoraBase.UnknownUser)
                throw ForbiddenException(s"User $userIri does not have permission to see value $valueIri")
            }

            Some(
                BasicValueQueryResult(
                    value = value,
                    ownerIri = ownerIri,
                    creationDate = creationDate,
                    comment = comment,
                    projectIri = projectIri,
                    permissionRelevantAssertions = assertions,
                    permissionCode = permissionCode
                )
            )
        } else {
            None
        }
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
            val linkValueIri = firstRowMap("linkValue")

            // Convert the query results into a LinkValueV1.
            val valueProps = valueUtilV1.createValueProps(linkValueIri, rows)
            val linkValueV1: LinkValueV1 = valueUtilV1.makeValueV1(valueProps, responderManager, userProfile) match {
                case linkValue: LinkValueV1 => linkValue
                case other => throw InconsistentTriplestoreDataException(s"Expected value $linkValueIri to be of type ${OntologyConstants.KnoraBase.LinkValue}, but it was read with type ${other.valueTypeIri}")
            }

            // Get the IRI of the value's owner.
            val ownerIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToUser, rows = rows).getOrElse(throw InconsistentTriplestoreDataException(s"Value $linkValueIri has no owner"))

            // Get the value's project IRI.
            val projectIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToProject, rows = rows).getOrElse(throw InconsistentTriplestoreDataException(s"Value $linkValueIri has no project"))

            // Get the value's creation date.
            val creationDate = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.ValueCreationDate, rows = rows).getOrElse(throw InconsistentTriplestoreDataException(s"Value $linkValueIri has no valueCreationDate"))

            // Get the optional comment on the value.
            val comment = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.ValueHasComment, rows = rows)

            // Get the value's permission-relevant assertions.
            val permissionRelevantAssertions = PermissionUtilV1.filterPermissionRelevantAssertionsFromValueProps(valueProps)

            // Get the permission code representing the user's permissions on the value.
            val permissionCode = PermissionUtilV1.getUserPermissionOnLinkValueV1WithValueProps(
                linkValueIri = linkValueIri,
                predicateIri = linkValueV1.predicateIri,
                valueProps = valueProps,
                userProfile = userProfile
            ).getOrElse {
                val userIri = userProfile.userData.user_id.getOrElse(OntologyConstants.KnoraBase.UnknownUser)
                throw ForbiddenException(s"User $userIri does not have permission to see value $linkValueIri")
            }

            val directLinkExists = firstRowMap.get("directLinkExists").exists(_.toBoolean)
            val targetResourceClass = firstRowMap.get("targetResourceClass")

            Some(
                LinkValueQueryResult(
                    value = linkValueV1,
                    ownerIri = ownerIri,
                    creationDate = creationDate,
                    comment = comment,
                    projectIri = projectIri,
                    directLinkExists = directLinkExists,
                    targetResourceClass = targetResourceClass,
                    permissionRelevantAssertions = permissionRelevantAssertions,
                    permissionCode = permissionCode
                )
            )
        } else {
            None
        }
    }

    /**
      * Verifies that a value was created.
      *
      * @param resourceIri     the IRI of the resource in which the value should have been created.
      * @param propertyIri     the IRI of the property that should point from the resource to the value.
      * @param unverifiedValue the value that should have been created.
      * @param userProfile     the profile of the user making the request.
      * @return a [[CreateValueResponseV1]], or a failed [[Future]] if the value could not be found in
      *         the resource's version history.
      */
    private def verifyValueCreation(resourceIri: IRI,
                                    propertyIri: IRI,
                                    unverifiedValue: UnverifiedValueV1,
                                    userProfile: UserProfileV1): Future[CreateValueResponseV1] = {
        unverifiedValue.value match {
            case linkUpdateV1: LinkUpdateV1 =>
                for {
                    linkValueQueryResult <- verifyLinkUpdate(
                        linkSourceIri = resourceIri,
                        linkPropertyIri = propertyIri,
                        linkTargetIri = linkUpdateV1.targetResourceIri,
                        linkValueIri = unverifiedValue.newValueIri,
                        userProfile = userProfile
                    )

                    apiResponseValue = LinkV1(
                        targetResourceIri = linkUpdateV1.targetResourceIri,
                        valueResourceClass = linkValueQueryResult.targetResourceClass
                    )
                } yield CreateValueResponseV1(
                    value = apiResponseValue,
                    comment = linkValueQueryResult.comment,
                    id = unverifiedValue.newValueIri,
                    rights = linkValueQueryResult.permissionCode,
                    userdata = userProfile.userData
                )

            case ordinaryUpdateValueV1 =>
                for {
                    verifyUpdateResult <- verifyOrdinaryValueUpdate(
                        resourceIri = resourceIri,
                        propertyIri = propertyIri,
                        searchValueIri = unverifiedValue.newValueIri,
                        userProfile = userProfile
                    )
                } yield CreateValueResponseV1(
                    value = verifyUpdateResult.value,
                    comment = verifyUpdateResult.comment,
                    id = unverifiedValue.newValueIri,
                    rights = verifyUpdateResult.permissionCode,
                    userdata = userProfile.userData
                )
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
    private def verifyOrdinaryValueUpdate(resourceIri: IRI, propertyIri: IRI, searchValueIri: IRI, userProfile: UserProfileV1): Future[ValueQueryResult] = {
        for {
        // Do a SPARQL query to look for the value in the resource's version history.
            sparqlQuery <- Future {
                // Run the template function in a Future to handle exceptions (see http://git.iml.unibas.ch/salsah-suite/knora/wikis/futures-with-akka#handling-errors-with-futures)
                queries.sparql.v1.txt.findValueInVersions(
                    triplestore = settings.triplestoreType,
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    searchValueIri = searchValueIri
                ).toString()
            }

            // _ = println(sparqlQuery)

            updateVerificationResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows = updateVerificationResponse.results.bindings

            // get ontology information about data type standoff classes
            // this information is needed to handle text values with standoff
            standoffDataTypeClassEntities: StandoffClassesWithDataTypeGetResponseV1 <- (responderManager ? StandoffClassesWithDataTypeGetRequestV1(userProfile = userProfile)).mapTo[StandoffClassesWithDataTypeGetResponseV1]
            // gets information about all standoff properties
            standoffAllPropertyEntities: StandoffAllPropertyEntitiesGetResponseV1 <- (responderManager ? StandoffAllPropertyEntitiesGetRequestV1(userProfile = userProfile)).mapTo[StandoffAllPropertyEntitiesGetResponseV1]

            result: ValueQueryResult = sparqlQueryResults2ValueQueryResult(valueIri = searchValueIri, rows = rows, standoffDataTypeEntityInfoMap = standoffDataTypeClassEntities.standoffClassEntityInfoMap, standoffAllPropertyEntities = standoffAllPropertyEntities.standoffAllPropertiesEntityInfoMap, userProfile = userProfile).getOrElse(throw UpdateNotPerformedException(s"The update to value $searchValueIri for property $propertyIri in resource $resourceIri was not performed. Please report this as a possible bug."))
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
                    throw UpdateNotPerformedException(s"The update to link value $linkValueIri with source IRI $linkSourceIri, link property $linkPropertyIri, and target $linkTargetIri was not performed. Please report this as a possible bug.")
            }
        } yield result
    }

    /**
      * Finds the object of the specified predicate in SPARQL query results describing a value.
      *
      * @param predicateIri the IRI of the predicate.
      * @param rows the SPARQL query results that describe the value.
      * @return the predicate's object.
      */
    private def getValuePredicateObject(predicateIri: IRI, rows: Seq[VariableResultsRow]): Option[IRI] = {
        rows.find(_.rowMap("objPred") == predicateIri).map(_.rowMap("objObj"))
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
            findResourceSparqlQuery <- Future(queries.sparql.v1.txt.findResourceWithValue(
                triplestore = settings.triplestoreType,
                searchValueIri = valueIri
            ).toString())
            findResourceResponse <- (storeManager ? SparqlSelectRequest(findResourceSparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (findResourceResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"No resource found containing value $valueIri")
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
      * Creates a new value (either an ordinary value or a link), using an existing transaction, assuming that
      * pre-update checks have already been done.
      *
      * @param projectIri                         the IRI of the project in which to create the value.
      * @param resourceIri                        the IRI of the resource in which to create the value.
      * @param propertyIri                        the IRI of the property that will point from the resource to the value.
      * @param value                              the value to create.
      * @param valueOwner                         the IRI of the new value's owner.
      * @param valueProject                       the IRI of the new value's project.
      * @param valuePermissions                   the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
      * @param updateResourceLastModificationDate if true, update the resource's `knora-base:lastModificationDate`.
      * @param userProfile                        the profile of the user making the request.
      * @return an [[UnverifiedValueV1]].
      */
    private def createValueV1AfterChecks(projectIri: IRI,
                                         resourceIri: IRI,
                                         propertyIri: IRI,
                                         value: UpdateValueV1,
                                         comment: Option[String],
                                         valueOwner: IRI,
                                         valueProject: IRI,
                                         valuePermissions: Option[String],
                                         updateResourceLastModificationDate: Boolean,
                                         userProfile: UserProfileV1): Future[UnverifiedValueV1] = {
        value match {
            case linkUpdateV1: LinkUpdateV1 =>
                createLinkValueV1AfterChecks(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    linkUpdateV1 = linkUpdateV1,
                    comment = comment,
                    valueOwner = valueOwner,
                    valueProject = valueProject,
                    valuePermissions = valuePermissions,
                    updateResourceLastModificationDate = updateResourceLastModificationDate,
                    userProfile = userProfile
                )

            case ordinaryUpdateValueV1 =>
                createOrdinaryValueV1AfterChecks(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    value = ordinaryUpdateValueV1,
                    comment = comment,
                    valueOwner = valueOwner,
                    valueProject = valueProject,
                    valuePermissions = valuePermissions,
                    updateResourceLastModificationDate = updateResourceLastModificationDate,
                    userProfile = userProfile
                )
        }
    }

    /**
      * Creates a link, using an existing transaction, assuming that pre-update checks have already been done.
      *
      * @param resourceIri                        the resource in which the link is to be created.
      * @param propertyIri                        the link property.
      * @param linkUpdateV1                       a [[LinkUpdateV1]] specifying the target resource.
      * @param valueOwner                         the IRI of the new link value's owner.
      * @param valueProject                       the IRI of the new link value's project.
      * @param valuePermissions                   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param updateResourceLastModificationDate if true, update the resource's `knora-base:lastModificationDate`.
      * @param userProfile                        the profile of the user making the request.
      * @return an [[UnverifiedValueV1]].
      */
    private def createLinkValueV1AfterChecks(resourceIri: IRI,
                                             propertyIri: IRI,
                                             linkUpdateV1: LinkUpdateV1,
                                             comment: Option[String],
                                             valueOwner: IRI,
                                             valueProject: IRI,
                                             valuePermissions: Option[String],
                                             updateResourceLastModificationDate: Boolean,
                                             userProfile: UserProfileV1): Future[UnverifiedValueV1] = {
        for {
            sparqlTemplateLinkUpdate <- incrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                targetResourceIri = linkUpdateV1.targetResourceIri,
                valueOwner = valueOwner,
                valueProject = Some(valueProject),
                valuePermissions = valuePermissions,
                userProfile = userProfile
            )

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v1.txt.createLink(
                dataNamedGraph = settings.projectNamedGraphs(valueProject).data,
                triplestore = settings.triplestoreType,
                resourceIri = resourceIri,
                linkUpdate = sparqlTemplateLinkUpdate,
                maybeComment = comment
            ).toString()

            /*
            _ = println("================ Create link ===============")
            _ = println(sparqlUpdate)
            _ = println("=============================================")
            */

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
        } yield UnverifiedValueV1(
            newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
            value = linkUpdateV1
        )
    }

    /**
      * Creates an ordinary value (i.e. not a link), using an existing transaction, assuming that pre-update checks have already been done.
      *
      * @param resourceIri                        the resource in which the value is to be created.
      * @param propertyIri                        the property that should point to the value.
      * @param value                              an [[UpdateValueV1]] describing the value.
      * @param valueOwner                         the IRI of the new value's owner.
      * @param valueProject                       the IRI of the new value's project.
      * @param valuePermissions                   the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
      * @param updateResourceLastModificationDate if true, update the resource's `knora-base:lastModificationDate`.
      * @param userProfile                        the profile of the user making the request.
      * @return an [[UnverifiedValueV1]].
      */
    private def createOrdinaryValueV1AfterChecks(resourceIri: IRI,
                                                 propertyIri: IRI,
                                                 value: UpdateValueV1,
                                                 comment: Option[String],
                                                 valueOwner: IRI,
                                                 valueProject: IRI,
                                                 valuePermissions: Option[String],
                                                 updateResourceLastModificationDate: Boolean,
                                                 userProfile: UserProfileV1): Future[UnverifiedValueV1] = {
        // Generate an IRI for the new value.
        val newValueIri = knoraIdUtil.makeRandomValueIri(resourceIri)

        for {
        // If we're creating a text value, update direct links and LinkValues for any resource references in standoff.
            standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- value match {
                case textValueV1: TextValueV1WithStandoff =>
                    // Make sure the text value's list of resource references is correct.
                    checkTextValueResourceRefs(textValueV1)

                    // Construct a SparqlTemplateLinkUpdate for each reference that was added.
                    val standoffLinkUpdatesForAddedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] =
                    textValueV1.resource_reference.map {
                        targetResourceIri => incrementLinkValue(
                            sourceResourceIri = resourceIri,
                            linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                            targetResourceIri = targetResourceIri,
                            valueOwner = OntologyConstants.KnoraBase.SystemUser,
                            valueProject = None,
                            valuePermissions = None,
                            userProfile = userProfile
                        )
                    }.toVector

                    Future.sequence(standoffLinkUpdatesForAddedResourceRefs)

                case _ => Future(Vector.empty[SparqlTemplateLinkUpdate])
            }

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v1.txt.createValue(
                dataNamedGraph = settings.projectNamedGraphs(valueProject).data,
                triplestore = settings.triplestoreType,
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                newValueIri = newValueIri,
                valueTypeIri = value.valueTypeIri,
                value = value,
                linkUpdates = standoffLinkUpdates,
                maybeComment = comment,
                valueOwner = valueOwner,
                valueProject = valueProject,
                maybeValuePermissions = valuePermissions
            ).toString()

            /*
            _ = println("================ Create value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
            */

            // Do the update.
            sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
        } yield UnverifiedValueV1(
            newValueIri = newValueIri,
            value = value
        )
    }

    /**
      * Changes a link, assuming that pre-update checks have already been done.
      *
      * @param projectIri         the IRI of the project containing the link.
      * @param resourceIri        the IRI of the resource containing the link.
      * @param propertyIri        the IRI of the link property.
      * @param currentLinkValueV1 a [[LinkValueV1]] representing the `knora-base:LinkValue` for the existing link.
      * @param linkUpdateV1       a [[LinkUpdateV1]] indicating the new target resource.
      * @param comment            an optional comment on the new link value.
      * @param valueOwner         the IRI of the new link value's owner.
      * @param valueProject       the IRI of the new link value's project.
      * @param valuePermissions   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param userProfile        the profile of the user making the request.
      * @return a [[ChangeValueResponseV1]].
      */
    private def changeLinkValueV1AfterChecks(projectIri: IRI,
                                             resourceIri: IRI,
                                             propertyIri: IRI,
                                             currentLinkValueV1: LinkValueV1,
                                             linkUpdateV1: LinkUpdateV1,
                                             comment: Option[String],
                                             valueOwner: IRI,
                                             valueProject: IRI,
                                             valuePermissions: Option[String],
                                             userProfile: UserProfileV1): Future[ChangeValueResponseV1] = {
        for {
        // Delete the existing link and decrement its LinkValue's reference count.
            sparqlTemplateLinkUpdateForCurrentLink <- decrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                targetResourceIri = currentLinkValueV1.objectIri,
                valueOwner = valueOwner,
                valueProject = Some(valueProject),
                valuePermissions = valuePermissions,
                userProfile = userProfile
            )

            // Create a new link, and create a new LinkValue for it.
            sparqlTemplateLinkUpdateForNewLink <- incrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = propertyIri,
                targetResourceIri = linkUpdateV1.targetResourceIri,
                valueOwner = valueOwner,
                valueProject = Some(valueProject),
                valuePermissions = valuePermissions,
                userProfile = userProfile
            )

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v1.txt.changeLink(
                dataNamedGraph = settings.projectNamedGraphs(projectIri).data,
                triplestore = settings.triplestoreType,
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
      * @param projectIri       the IRI of the project containing the value.
      * @param resourceIri      the IRI of the resource containing the value.
      * @param propertyIri      the IRI of the property that points to the value.
      * @param currentValueIri  the IRI of the existing value.
      * @param currentValueV1   an [[ApiValueV1]] representing the existing value.
      * @param newValueIri      the IRI of the new value.
      * @param updateValueV1    an [[UpdateValueV1]] representing the new value.
      * @param comment          an optional comment on the new value.
      * @param valueOwner       the IRI of the new value's owner.
      * @param valueProject     the IRI of the new value's project.
      * @param valuePermissions the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
      * @param userProfile      the profile of the user making the request.
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
                                                 valueOwner: IRI,
                                                 valueProject: IRI,
                                                 valuePermissions: Option[String],
                                                 userProfile: UserProfileV1): Future[ChangeValueResponseV1] = {
        for {
        // If we're adding a text value, update direct links and LinkValues for any resource references in Standoff.
            standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- (currentValueV1, updateValueV1) match {
                case (currentTextValue: TextValueV1WithStandoff, newTextValue: TextValueV1WithStandoff) =>
                    // Make sure the new text value's list of resource references is correct.
                    checkTextValueResourceRefs(newTextValue)

                    // Identify the resource references that have been added or removed in the new version of
                    // the value.
                    val currentResourceRefs = currentTextValue.resource_reference
                    val newResourceRefs = newTextValue.resource_reference
                    val addedResourceRefs = newResourceRefs -- currentResourceRefs
                    val removedResourceRefs = currentResourceRefs -- newResourceRefs

                    // Construct a SparqlTemplateLinkUpdate for each reference that was added.
                    val standoffLinkUpdatesForAddedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] = addedResourceRefs.toVector.map {
                        targetResourceIri =>
                            incrementLinkValue(
                                sourceResourceIri = resourceIri,
                                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                                targetResourceIri = targetResourceIri,
                                valueOwner = OntologyConstants.KnoraBase.SystemUser,
                                valueProject = None,
                                valuePermissions = None,
                                userProfile = userProfile
                            )
                    }

                    // Construct a SparqlTemplateLinkUpdate for each reference that was removed.
                    val standoffLinkUpdatesForRemovedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] = removedResourceRefs.toVector.map {
                        removedTargetResource =>
                            decrementLinkValue(
                                sourceResourceIri = resourceIri,
                                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                                targetResourceIri = removedTargetResource,
                                valueOwner = OntologyConstants.KnoraBase.SystemUser,
                                valueProject = None,
                                valuePermissions = None,
                                userProfile = userProfile
                            )
                    }

                    Future.sequence(standoffLinkUpdatesForAddedResourceRefs ++ standoffLinkUpdatesForRemovedResourceRefs)

                case _ => Future(Vector.empty[SparqlTemplateLinkUpdate])
            }

            // Generate a SPARQL update.
            sparqlUpdate = queries.sparql.v1.txt.addValueVersion(
                dataNamedGraph = settings.projectNamedGraphs(projectIri).data,
                triplestore = settings.triplestoreType,
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                currentValueIri = currentValueIri,
                newValueIri = newValueIri,
                valueTypeIri = updateValueV1.valueTypeIri,
                value = updateValueV1,
                valueOwner = valueOwner,
                valueProject = valueProject,
                maybeValuePermissions = valuePermissions,
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
            verifyUpdateResult <- verifyOrdinaryValueUpdate(
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
      *    - If there that link and `LinkValue` don't yet exist, they will be created, and the `LinkValue` will be given
      * a reference count of 1.
      *
      * @param sourceResourceIri the IRI of the source resource.
      * @param linkPropertyIri   the IRI of the property that links the source resource to the target resource.
      * @param targetResourceIri the IRI of the target resource.
      * @param valueOwner        the IRI of the new link value's owner.
      * @param valueProject      the IRI of the new link value's project.
      * @param valuePermissions  the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param userProfile       the profile of the user making the request.
      * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
      */
    private def incrementLinkValue(sourceResourceIri: IRI,
                                   linkPropertyIri: IRI,
                                   targetResourceIri: IRI,
                                   valueOwner: IRI,
                                   valueProject: Option[IRI],
                                   valuePermissions: Option[String],
                                   userProfile: UserProfileV1): Future[SparqlTemplateLinkUpdate] = {
        for {
        // Check whether a LinkValue already exists for this link.
            maybeLinkValueQueryResult <- findLinkValueByObject(
                subjectIri = sourceResourceIri,
                predicateIri = linkPropertyIri,
                objectIri = targetResourceIri,
                userProfile = userProfile
            )

            // Generate an IRI for the new LinkValue.
            newLinkValueIri = knoraIdUtil.makeRandomValueIri(sourceResourceIri)

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
                        newLinkValueOwner = valueOwner,
                        newLinkValueProject = valueProject,
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
                        newLinkValueIri = newLinkValueIri,
                        linkTargetIri = targetResourceIri,
                        currentReferenceCount = 0,
                        newReferenceCount = 1,
                        newLinkValueOwner = valueOwner,
                        newLinkValueProject = valueProject,
                        newLinkValuePermissions = valuePermissions
                    )
            }
        } yield linkUpdate
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
      * @param sourceResourceIri the IRI of the source resource.
      * @param linkPropertyIri   the IRI of the property that links the source resource to the target resource.
      * @param targetResourceIri the IRI of the target resource.
      * @param valueOwner        the IRI of the new link value's owner.
      * @param valueProject      the IRI of the new link value's project.
      * @param valuePermissions  the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param userProfile       the profile of the user making the request.
      * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
      */
    private def decrementLinkValue(sourceResourceIri: IRI,
                                   linkPropertyIri: IRI,
                                   targetResourceIri: IRI,
                                   valueOwner: IRI,
                                   valueProject: Option[IRI],
                                   valuePermissions: Option[String],
                                   userProfile: UserProfileV1): Future[SparqlTemplateLinkUpdate] = {
        for {
        // Query the LinkValue to ensure that it exists and to get its contents.
            maybeLinkValueQueryResult <- findLinkValueByObject(
                subjectIri = sourceResourceIri,
                predicateIri = linkPropertyIri,
                objectIri = targetResourceIri,
                userProfile = userProfile
            )

            // Did we find it?
            linkUpdate = maybeLinkValueQueryResult match {
                case Some(linkValueQueryResult) =>
                    // Yes. Make a SparqlTemplateLinkUpdate.

                    // Decrement the LinkValue's reference count.
                    val currentReferenceCount = linkValueQueryResult.value.referenceCount
                    val newReferenceCount = currentReferenceCount - 1

                    // If the new reference count is 0, specify that the direct link between the source and target
                    // resources should be removed.
                    val deleteDirectLink = linkValueQueryResult.directLinkExists && newReferenceCount == 0

                    // Generate an IRI for the new LinkValue.
                    val newLinkValueIri = knoraIdUtil.makeRandomValueIri(sourceResourceIri)

                    SparqlTemplateLinkUpdate(
                        linkPropertyIri = linkPropertyIri,
                        directLinkExists = linkValueQueryResult.directLinkExists,
                        insertDirectLink = false,
                        deleteDirectLink = deleteDirectLink,
                        linkValueExists = true,
                        newLinkValueIri = newLinkValueIri,
                        linkTargetIri = targetResourceIri,
                        currentReferenceCount = currentReferenceCount,
                        newReferenceCount = newReferenceCount,
                        newLinkValueOwner = valueOwner,
                        newLinkValueProject = valueProject,
                        newLinkValuePermissions = valuePermissions
                    )

                case None =>
                    // We didn't find the LinkValue. This shouldn't happen.
                    throw InconsistentTriplestoreDataException(s"There should be a knora-base:LinkValue describing a direct link from resource $sourceResourceIri to resource $targetResourceIri using property $linkPropertyIri, but it seems to be missing")
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
    private def checkTextValueResourceRefs(textValue: TextValueV1WithStandoff): Unit = {

        // please note that the function `InputValidation.getResourceIrisFromStandoffTags` is not used here
        // because we want a double check (the function has already been called in the route or in standoff responder)
        val resourceRefsInStandoff: Set[IRI] = textValue.textattr.foldLeft(Set.empty[IRI]) {
            case (acc: Set[IRI], standoffNode: StandoffTagV1) =>

                standoffNode match {

                    case node: StandoffTagV1 if node.dataType.isDefined && node.dataType.get == StandoffDataTypeClasses.StandoffLinkTag =>
                        acc + node.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.StandoffTagHasLink).getOrElse(throw NotFoundException(s"${OntologyConstants.KnoraBase.StandoffTagHasLink} was not found in $node")).stringValue()

                    case _ => acc
                }
        }

        if (resourceRefsInStandoff != textValue.resource_reference) {
            throw BadRequestException(s"The list of resource references in this text value does not match the resource references in its Standoff markup: $textValue")
        }
    }

    /**
      * Implements a pre-update check to ensure that an [[UpdateValueV1]] has the correct type for the `knora-base:objectClassConstraint` of
      * the property that is supposed to point to it.
      *
      * @param propertyIri                   the IRI of the property.
      * @param propertyObjectClassConstraint the IRI of the `knora-base:objectClassConstraint` of the property.
      * @param updateValueV1                 the value to be updated.
      * @param userProfile                   the profile of the user making the request.
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
