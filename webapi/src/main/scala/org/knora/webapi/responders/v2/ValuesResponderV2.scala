/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionType}
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectGetRequestADM, ProjectGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2, ResourcesPreviewGetRequestV2}
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.v2.search.gravsearch.GravsearchParser
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.PermissionUtilADM.{ChangeRightsPermission, DeletePermission, EntityPermission, ModifyPermission}
import org.knora.webapi.util.{KnoraIdUtil, PermissionUtilADM, SmartIri}

import scala.concurrent.Future

/**
  * Handles requests to read and write Knora values.
  */
class ValuesResponderV2 extends Responder {
    // Creates IRIs for new Knora value objects.
    val knoraIdUtil = new KnoraIdUtil

    /**
      * The IRI and content of a new value or value version whose existence in the triplestore has been verified.
      *
      * @param newValueIri the IRI that was assigned to the new value.
      * @param value       the content of the new value.
      */
    case class VerifiedValueV2(newValueIri: IRI, value: ValueContentV2, permissions: String)

    override def receive: Receive = {
        case createValueRequest: CreateValueRequestV2 => future2Message(sender(), createValueV2(createValueRequest), log)
        case updateValueRequest: UpdateValueRequestV2 => future2Message(sender(), updateValueV2(updateValueRequest), log)
        case deleteValueRequest: DeleteValueRequestV2 => future2Message(sender(), deleteValueV2(deleteValueRequest), log)
        case createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV2 => future2Message(sender(), generateSparqToCreateMultipleValuesV2(createMultipleValuesRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Creates a new value in an existing resource.
      *
      * @param createValueRequest the request to create the value.
      * @return a [[CreateValueResponseV2]].
      */
    private def createValueV2(createValueRequest: CreateValueRequestV2): Future[CreateValueResponseV2] = {
        def makeTaskFuture: Future[CreateValueResponseV2] = {
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

                // Don't accept link properties.
                _ = if (propertyInfoForSubmittedProperty.isLinkProp) {
                    throw BadRequestException(s"Invalid property <${createValueRequest.createValue.propertyIri}>. Use a link value property to submit a link.")
                }

                // Don't accept knora-api:hasStandoffLinkToValue.
                _ = if (createValueRequest.createValue.propertyIri.toString == OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue) {
                    throw BadRequestException(s"Values of <${createValueRequest.createValue.propertyIri}> cannot be created directly")
                }

                // Make an adjusted version of the submitted property: if it's a link value property, substitute the
                // corresponding link property, whose objects we will need to query. Get ontology information about the
                // adjusted property.

                adjustedInternalPropertyInfo: ReadPropertyInfoV2 <- getAdjustedInternalPropertyInfo(
                    submittedPropertyIri = createValueRequest.createValue.propertyIri,
                    maybeSubmittedValueType = Some(createValueRequest.createValue.valueContent.valueType),
                    propertyInfoForSubmittedProperty = propertyInfoForSubmittedProperty,
                    requestingUser = createValueRequest.requestingUser
                )

                adjustedInternalPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri

                // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
                // so we can see objects that the user doesn't have permission to see.

                resourceInfo: ReadResourceV2 <- getResourceWithPropertyValues(
                    resourceIri = createValueRequest.createValue.resourceIri,
                    propertyInfo = adjustedInternalPropertyInfo,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )

                // Check that the user has permission to modify the resource.

                _ = ResourceUtilV2.checkResourcePermission(
                    resourceInfo = resourceInfo,
                    permissionNeeded = ModifyPermission,
                    requestingUser = createValueRequest.requestingUser
                )

                // Check that the resource has the rdf:type that the client thinks it has.

                _ = if (resourceInfo.resourceClassIri != createValueRequest.createValue.resourceClassIri.toOntologySchema(InternalSchema)) {
                    throw BadRequestException(s"The rdf:type of resource <${createValueRequest.createValue.resourceIri}> is not <${createValueRequest.createValue.resourceClassIri}>")
                }

                // Get the definition of the resource class.

                classInfoRequest = ClassesGetRequestV2(
                    classIris = Set(resourceInfo.resourceClassIri),
                    allLanguages = false,
                    requestingUser = createValueRequest.requestingUser
                )

                classInfoResponse: ReadOntologyV2 <- (responderManager ? classInfoRequest).mapTo[ReadOntologyV2]

                // Check that the resource class has a cardinality for the submitted property.

                classInfo: ReadClassInfoV2 = classInfoResponse.classes(resourceInfo.resourceClassIri)
                cardinalityInfo: Cardinality.KnoraCardinalityInfo = classInfo.allCardinalities.getOrElse(submittedInternalPropertyIri, throw BadRequestException(s"Resource <${createValueRequest.createValue.resourceIri}> belongs to class <${resourceInfo.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}>, which has no cardinality for property <${createValueRequest.createValue.propertyIri}>"))

                // Check that the object of the adjusted property (the value to be created, or the target of the link to be created) will have
                // the correct type for the adjusted property's knora-base:objectClassConstraint.

                _ <- checkPropertyObjectClassConstraint(
                    propertyInfo = adjustedInternalPropertyInfo,
                    valueContent = submittedInternalValueContent,
                    requestingUser = createValueRequest.requestingUser
                )

                // If this is a list value, check that it points to a real list node.

                _ <- submittedInternalValueContent match {
                    case listValue: HierarchicalListValueContentV2 => ResourceUtilV2.checkListNodeExists(listValue.valueHasListNode, storeManager)
                    case _ => FastFuture.successful(())
                }

                // Check that the resource class's cardinality for the submitted property allows another value to be added
                // for that property.

                currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values.getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])

                _ = if ((cardinalityInfo.cardinality == Cardinality.MustHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveSome) && currentValuesForProp.isEmpty) {
                    throw InconsistentTriplestoreDataException(s"Resource class <${resourceInfo.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${createValueRequest.createValue.propertyIri}>, but resource <${createValueRequest.createValue.resourceIri}> has no value for that property")
                }

                _ = if (cardinalityInfo.cardinality == Cardinality.MustHaveOne || (cardinalityInfo.cardinality == Cardinality.MayHaveOne && currentValuesForProp.nonEmpty)) {
                    throw OntologyConstraintException(s"Resource class <${resourceInfo.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${createValueRequest.createValue.propertyIri}>, and this does not allow a value to be added for that property to resource <${createValueRequest.createValue.resourceIri}>")
                }

                // Check that the new value would not duplicate an existing value.

                unescapedSubmittedInternalValueContent = submittedInternalValueContent.unescape

                _ = if (currentValuesForProp.exists(currentVal => unescapedSubmittedInternalValueContent.wouldDuplicateOtherValue(currentVal.valueContent))) {
                    throw DuplicateValueException()
                }

                // If this is a text value, check that the resources pointed to by any standoff link tags exist
                // and that the user has permission to see them.

                _ <- submittedInternalValueContent match {
                    case textValueContent: TextValueContentV2 => checkResourceIris(textValueContent.standoffLinkTagTargetResourceIris, createValueRequest.requestingUser)
                    case _ => FastFuture.successful(())
                }

                // Did the user submit permissions for the new value?
                newValuePermissionLiteral <- createValueRequest.createValue.permissions match {
                    case Some(permissions) =>
                        // Yes. Validate them.
                        PermissionUtilADM.validatePermissions(permissionLiteral = permissions, responderManager = responderManager)

                    case None =>
                        // No. Get default permissions for the new value.
                        ResourceUtilV2.getDefaultValuePermissions(
                            projectIri = resourceInfo.projectADM.id,
                            resourceClassIri = resourceInfo.resourceClassIri,
                            propertyIri = submittedInternalPropertyIri,
                            requestingUser = createValueRequest.requestingUser,
                            responderManager = responderManager
                        )
                }

                // Get information about the project that the resource is in, so we know which named graph to put the new value in.
                projectInfo: ProjectGetResponseADM <- {
                    responderManager ? ProjectGetRequestADM(maybeIri = Some(resourceInfo.projectADM.id), requestingUser = createValueRequest.requestingUser)
                }.mapTo[ProjectGetResponseADM]

                dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(projectInfo.project)

                // Create the new value.

                unverifiedValue <- createValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    projectIri = projectInfo.project.id,
                    resourceInfo = resourceInfo,
                    propertyIri = adjustedInternalPropertyIri,
                    value = submittedInternalValueContent,
                    valueCreator = createValueRequest.requestingUser.id,
                    valuePermissions = newValuePermissionLiteral,
                    requestingUser = createValueRequest.requestingUser
                )

                // Check that the value was written correctly to the triplestore.

                verifiedValue: VerifiedValueV2 <- verifyValue(
                    resourceIri = createValueRequest.createValue.resourceIri,
                    propertyIriForGravsearch = adjustedInternalPropertyIri.toOntologySchema(ApiV2WithValueObjects),
                    propertyIriInResult = submittedInternalPropertyIri,
                    unverifiedValue = unverifiedValue,
                    requestingUser = createValueRequest.requestingUser
                )
            } yield CreateValueResponseV2(
                valueIri = verifiedValue.newValueIri,
                valueType = verifiedValue.value.valueType
            )
        }

        val triplestoreUpdateFuture: Future[CreateValueResponseV2] = for {
            // Don't allow anonymous users to create values.
            _ <- Future {
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
                () => makeTaskFuture
            )
        } yield taskResult

        // If we were creating a file value, have Sipi move the file to permanent storage if the update
        // was successful, or delete the temporary file if the update failed.
        ResourceUtilV2.doSipiPostUpdate(
            updateFuture = triplestoreUpdateFuture,
            valueContent = createValueRequest.createValue.valueContent,
            requestingUser = createValueRequest.requestingUser,
            responderManager = responderManager,
            log = log
        )
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
                    linkPropertyIri = propertyIri,
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
            currentTime: Instant = Instant.now

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
                creationDate = currentTime
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
            valueContent = value.unescape,
            permissions = valuePermissions
        )
    }

    /**
      * Creates a link, using an existing transaction, assuming that pre-update checks have already been done.
      *
      * @param dataNamedGraph   the named graph in which the link is to be created.
      * @param resourceInfo     information about the the resource in which to create the value.
      * @param linkPropertyIri  the link property.
      * @param linkValueContent a [[LinkValueContentV2]] specifying the target resource.
      * @param valueCreator     the IRI of the new link value's owner.
      * @param valuePermissions the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param requestingUser   the user making the request.
      * @return an [[UnverifiedValueV2]].
      */
    private def createLinkValueV2AfterChecks(dataNamedGraph: IRI,
                                             resourceInfo: ReadResourceV2,
                                             linkPropertyIri: SmartIri,
                                             linkValueContent: LinkValueContentV2,
                                             valueCreator: IRI,
                                             valuePermissions: String,
                                             requestingUser: UserADM): Future[UnverifiedValueV2] = {
        for {
            sparqlTemplateLinkUpdate <- Future(incrementLinkValue(
                sourceResourceInfo = resourceInfo,
                linkPropertyIri = linkPropertyIri,
                targetResourceIri = linkValueContent.referredResourceIri,
                valueCreator = valueCreator,
                valuePermissions = valuePermissions,
                requestingUser = requestingUser
            ))

            currentTime: Instant = Instant.now

            // Generate a SPARQL update string.
            sparqlUpdate = queries.sparql.v2.txt.createLink(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                resourceIri = resourceInfo.resourceIri,
                linkUpdate = sparqlTemplateLinkUpdate,
                creationDate = currentTime,
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
            valueContent = linkValueContent.unescape,
            permissions = valuePermissions
        )
    }

    /**
      * Represents SPARQL generated to create one of multiple values in a new resource.
      *
      * @param insertSparql    the generated SPARQL.
      * @param unverifiedValue an [[UnverifiedValueV2]] representing the value that is to be created.
      */
    private case class InsertSparqlWithUnverifiedValue(insertSparql: String, unverifiedValue: UnverifiedValueV2)

    /**
      * Generates SPARQL for creating multiple values.
      *
      * @param createMultipleValuesRequest the request to create multiple values.
      * @return a [[GenerateSparqlToCreateMultipleValuesResponseV2]] containing the generated SPARQL and information
      *         about the values to be created.
      */
    private def generateSparqToCreateMultipleValuesV2(createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV2): Future[GenerateSparqlToCreateMultipleValuesResponseV2] = {
        for {
            // Generate SPARQL to create links and LinkValues for standoff links in text values.
            sparqlForStandoffLinks: String <- Future(generateInsertSparqlForStandoffLinksInMultipleValues(createMultipleValuesRequest))

            // Generate SPARQL for each value.
            sparqlForPropertyValues: Map[SmartIri, Seq[InsertSparqlWithUnverifiedValue]] = createMultipleValuesRequest.values.map {
                case (propertyIri: SmartIri, valuesToCreate: Seq[GenerateSparqlForValueInNewResourceV2]) =>
                    propertyIri -> valuesToCreate.zipWithIndex.map {
                        case (valueToCreate: GenerateSparqlForValueInNewResourceV2, valueHasOrder: Int) =>
                            generateInsertSparqlWithUnverifiedValue(
                                resourceIri = createMultipleValuesRequest.resourceIri,
                                propertyIri = propertyIri,
                                valueToCreate = valueToCreate,
                                valueHasOrder = valueHasOrder,
                                creationDate = createMultipleValuesRequest.creationDate,
                                requestingUser = createMultipleValuesRequest.requestingUser
                            )
                    }
            }

            // Concatenate all the generated SPARQL.
            allInsertSparql: String = sparqlForPropertyValues.values.flatten.map(_.insertSparql).mkString("\n\n") + "\n\n" + sparqlForStandoffLinks

            // Collect all the unverified values.
            unverifiedValues: Map[SmartIri, Seq[UnverifiedValueV2]] = sparqlForPropertyValues.map {
                case (propertyIri, unverifiedValuesWithSparql) => propertyIri -> unverifiedValuesWithSparql.map(_.unverifiedValue)
            }
        } yield GenerateSparqlToCreateMultipleValuesResponseV2(
            insertSparql = allInsertSparql,
            unverifiedValues = unverifiedValues
        )
    }

    /**
      * Generates SPARQL to create one of multiple values in a new resource.
      *
      * @param resourceIri    the IRI of the resource.
      * @param propertyIri    the IRI of the property that will point to the value.
      * @param valueToCreate  the value to be created.
      * @param valueHasOrder  the value's `knora-base:valueHasOrder`.
      * @param creationDate    the timestamp to be used as the value creation time.
      * @param requestingUser the user making the request.
      * @return a [[InsertSparqlWithUnverifiedValue]] containing the generated SPARQL and an [[UnverifiedValueV2]].
      */
    private def generateInsertSparqlWithUnverifiedValue(resourceIri: IRI,
                                                        propertyIri: SmartIri,
                                                        valueToCreate: GenerateSparqlForValueInNewResourceV2,
                                                        valueHasOrder: Int,
                                                        creationDate: Instant,
                                                        requestingUser: UserADM): InsertSparqlWithUnverifiedValue = {
        // Make an IRI for the new value.
        val newValueIri = knoraIdUtil.makeRandomValueIri(resourceIri)

        // Generate the SPARQL.
        val insertSparql: String = valueToCreate.valueContent match {
            case linkValueContentV2: LinkValueContentV2 =>
                // We're creating a link.

                // Construct a SparqlTemplateLinkUpdate to tell the SPARQL template how to create
                // the link and its LinkValue.
                val sparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
                    linkPropertyIri = propertyIri.fromLinkValuePropToLinkProp,
                    directLinkExists = false,
                    insertDirectLink = true,
                    deleteDirectLink = false,
                    linkValueExists = false,
                    linkTargetExists = linkValueContentV2.referredResourceExists,
                    newLinkValueIri = newValueIri,
                    linkTargetIri = linkValueContentV2.referredResourceIri,
                    currentReferenceCount = 0,
                    newReferenceCount = 1,
                    newLinkValueCreator = requestingUser.id,
                    newLinkValuePermissions = valueToCreate.permissions
                )

                // Generate SPARQL for the link.
                queries.sparql.v2.txt.generateInsertStatementsForCreateLink(
                    resourceIri = resourceIri,
                    linkUpdate = sparqlTemplateLinkUpdate,
                    creationDate = creationDate,
                    maybeComment = valueToCreate.valueContent.comment,
                    maybeValueHasOrder = Some(valueHasOrder)
                ).toString()

            case otherValueContentV2 =>
                // We're creating an ordinary value. Generate SPARQL for it.
                queries.sparql.v2.txt.generateInsertStatementsForCreateValue(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    value = otherValueContentV2,
                    newValueIri = newValueIri,
                    linkUpdates = Seq.empty[SparqlTemplateLinkUpdate], // This is empty because we have to generate SPARQL for standoff links separately.
                    valueCreator = requestingUser.id,
                    valuePermissions = valueToCreate.permissions,
                    creationDate = creationDate,
                    maybeValueHasOrder = Some(valueHasOrder)
                ).toString()
        }

        InsertSparqlWithUnverifiedValue(
            insertSparql = insertSparql,
            unverifiedValue = UnverifiedValueV2(
                newValueIri = newValueIri,
                valueContent = valueToCreate.valueContent.unescape,
                permissions = valueToCreate.permissions
            )
        )
    }

    /**
      * When processing a request to create multiple values, generates SPARQL for standoff links in text values.
      *
      * @param createMultipleValuesRequest the request to create multiple values.
      * @return SPARQL INSERT statements.
      */
    private def generateInsertSparqlForStandoffLinksInMultipleValues(createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV2): String = {
        // To create LinkValues for the standoff links in the values to be created, we need to compute
        // the initial reference count of each LinkValue. This is equal to the number of TextValues in the resource
        // that have standoff links to a particular target resource.

        // First, get the standoff link targets from all the text values to be created.
        val standoffLinkTargetsPerTextValue: Vector[Set[IRI]] = createMultipleValuesRequest.flatValues.foldLeft(Vector.empty[Set[IRI]]) {
            case (acc: Vector[Set[IRI]], createValueV2: GenerateSparqlForValueInNewResourceV2) =>
                createValueV2.valueContent match {
                    case textValueContentV2: TextValueContentV2 => acc :+ textValueContentV2.standoffLinkTagTargetResourceIris
                    case _ => acc
                }
        }

        // Combine those resource references into a single list, so if there are n text values with a link to
        // some IRI, the list will contain that IRI n times.
        val allStandoffLinkTargets: Vector[IRI] = standoffLinkTargetsPerTextValue.flatten

        // Now we need to count the number of times each IRI occurs in allStandoffLinkTargets. To do this, first
        // use groupBy(identity). The groupBy method takes a function that returns a key for each item in the
        // collection, and makes a Map in which items with the same key are grouped together. The identity
        // function just returns its argument. So groupBy(identity) makes a Map[IRI, Vector[IRI]] in which each
        // IRI points to a sequence of the same IRI repeated as many times as it occurred in allStandoffLinkTargets.
        val allStandoffLinkTargetsGrouped: Map[IRI, Vector[IRI]] = allStandoffLinkTargets.groupBy(identity)

        // Replace each Vector[IRI] with its size. That's the number of text values containing
        // standoff links to that IRI.
        val initialReferenceCounts: Map[IRI, Int] = allStandoffLinkTargetsGrouped.mapValues(_.size)

        // For each standoff link target IRI, construct a SparqlTemplateLinkUpdate to create a hasStandoffLinkTo property
        // and one LinkValue with its initial reference count.
        val standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] = initialReferenceCounts.toSeq.map {
            case (targetIri, initialReferenceCount) =>
                SparqlTemplateLinkUpdate(
                    linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                    directLinkExists = false,
                    insertDirectLink = true,
                    deleteDirectLink = false,
                    linkValueExists = false,
                    linkTargetExists = true, // doesn't matter, the generateInsertStatementsForStandoffLinks template doesn't use it
                    newLinkValueIri = knoraIdUtil.makeRandomValueIri(createMultipleValuesRequest.resourceIri),
                    linkTargetIri = targetIri,
                    currentReferenceCount = 0,
                    newReferenceCount = initialReferenceCount,
                    newLinkValueCreator = OntologyConstants.KnoraBase.SystemUser,
                    newLinkValuePermissions = standoffLinkValuePermissions
                )
        }

        // Generate SPARQL INSERT statements based on those SparqlTemplateLinkUpdates.
        queries.sparql.v2.txt.generateInsertStatementsForStandoffLinks(
            resourceIri = createMultipleValuesRequest.resourceIri,
            linkUpdates = standoffLinkUpdates,
            creationDate = createMultipleValuesRequest.creationDate
        ).toString()
    }

    /**
      * Creates a new version of an existing value.
      *
      * @param updateValueRequest the request to update the value.
      * @return a [[UpdateValueResponseV2]].
      */
    private def updateValueV2(updateValueRequest: UpdateValueRequestV2): Future[UpdateValueResponseV2] = {
        def makeTaskFuture: Future[UpdateValueResponseV2] = {
            for {
                // Convert the submitted value to the internal schema.
                submittedInternalPropertyIri: SmartIri <- Future(updateValueRequest.updateValue.propertyIri.toOntologySchema(InternalSchema))
                submittedInternalValueContent: ValueContentV2 = updateValueRequest.updateValue.valueContent.toOntologySchema(InternalSchema)

                // Get ontology information about the submitted property.

                propertyInfoRequestForSubmittedProperty = PropertiesGetRequestV2(
                    propertyIris = Set(submittedInternalPropertyIri),
                    allLanguages = false,
                    requestingUser = updateValueRequest.requestingUser
                )

                propertyInfoResponseForSubmittedProperty: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForSubmittedProperty).mapTo[ReadOntologyV2]
                propertyInfoForSubmittedProperty: ReadPropertyInfoV2 = propertyInfoResponseForSubmittedProperty.properties(submittedInternalPropertyIri)

                // Don't accept link properties.
                _ = if (propertyInfoForSubmittedProperty.isLinkProp) {
                    throw BadRequestException(s"Invalid property <${propertyInfoForSubmittedProperty.entityInfoContent.propertyIri.toOntologySchema(ApiV2WithValueObjects)}>. Use a link value property to submit a link.")
                }

                // Don't accept knora-api:hasStandoffLinkToValue.
                _ = if (updateValueRequest.updateValue.propertyIri.toString == OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue) {
                    throw BadRequestException(s"Values of <${updateValueRequest.updateValue.propertyIri}> cannot be updated directly")
                }

                // Make an adjusted version of the submitted property: if it's a link value property, substitute the
                // corresponding link property, whose objects we will need to query. Get ontology information about the
                // adjusted property.

                adjustedInternalPropertyInfo: ReadPropertyInfoV2 <- getAdjustedInternalPropertyInfo(
                    submittedPropertyIri = updateValueRequest.updateValue.propertyIri,
                    maybeSubmittedValueType = Some(updateValueRequest.updateValue.valueContent.valueType),
                    propertyInfoForSubmittedProperty = propertyInfoForSubmittedProperty,
                    requestingUser = updateValueRequest.requestingUser
                )

                adjustedInternalPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri

                // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
                // so we can see objects that the user doesn't have permission to see.

                resourceInfo: ReadResourceV2 <- getResourceWithPropertyValues(
                    resourceIri = updateValueRequest.updateValue.resourceIri,
                    propertyInfo = adjustedInternalPropertyInfo,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )

                // Check that the resource has the rdf:type that the client thinks it has.

                _ = if (resourceInfo.resourceClassIri != updateValueRequest.updateValue.resourceClassIri.toOntologySchema(InternalSchema)) {
                    throw BadRequestException(s"The rdf:type of resource <${updateValueRequest.updateValue.resourceIri}> is not <${updateValueRequest.updateValue.resourceClassIri}>")
                }

                // Check that the resource has the value that the user wants to update, as an object of the submitted property.

                maybeCurrentValue: Option[ReadValueV2] = resourceInfo.values.get(submittedInternalPropertyIri).flatMap(_.find(_.valueIri == updateValueRequest.updateValue.valueIri))

                currentValue: ReadValueV2 = maybeCurrentValue match {
                    case Some(value) => value
                    case None => throw NotFoundException(s"Resource <${updateValueRequest.updateValue.resourceIri}> does not have value <${updateValueRequest.updateValue.valueIri}> as an object of property <${updateValueRequest.updateValue.propertyIri}>")
                }

                // Did the user submit permissions for the new value?
                newValueVersionPermissionLiteral <- updateValueRequest.updateValue.permissions match {
                    case Some(permissions) =>
                        // Yes. Validate them.
                        PermissionUtilADM.validatePermissions(permissionLiteral = permissions, responderManager = responderManager)

                    case None =>
                        // No. Use the permissions on the current version of the value.
                        FastFuture.successful(currentValue.permissions)
                }

                // Check that the user has permission to do the update. If they want to change the permissions
                // on the value, they need ChangeRightsPermission, otherwise they need ModifyPermission.

                currentPermissionsParsed: Map[EntityPermission, Set[IRI]] = PermissionUtilADM.parsePermissions(currentValue.permissions)
                newPermissionsParsed: Map[EntityPermission, Set[IRI]] = PermissionUtilADM.parsePermissions(newValueVersionPermissionLiteral, { permissionLiteral: String => throw BadRequestException(s"Invalid permission literal: $permissionLiteral") })

                permissionNeeded = if (newPermissionsParsed != currentPermissionsParsed) {
                    ChangeRightsPermission
                } else {
                    ModifyPermission
                }

                _ = ResourceUtilV2.checkValuePermission(
                    resourceInfo = resourceInfo,
                    valueInfo = currentValue,
                    permissionNeeded = permissionNeeded,
                    requestingUser = updateValueRequest.requestingUser
                )

                // Check that the current value and the submitted value have the same type.
                _ = if (currentValue.valueContent.valueType != submittedInternalValueContent.valueType) {
                    throw BadRequestException(s"Value <${updateValueRequest.updateValue.valueIri}> has type <${currentValue.valueContent.valueType.toOntologySchema(ApiV2WithValueObjects)}>, but the submitted new version has type <${updateValueRequest.updateValue.valueContent.valueType}>")
                }

                // Check that the object of the adjusted property (the value to be created, or the target of the link to be created) will have
                // the correct type for the adjusted property's knora-base:objectClassConstraint.

                _ <- checkPropertyObjectClassConstraint(
                    propertyInfo = adjustedInternalPropertyInfo,
                    valueContent = submittedInternalValueContent,
                    requestingUser = updateValueRequest.requestingUser
                )

                // If this is a list value, check that it points to a real list node.

                _ <- submittedInternalValueContent match {
                    case listValue: HierarchicalListValueContentV2 => ResourceUtilV2.checkListNodeExists(listValue.valueHasListNode, storeManager)
                    case _ => FastFuture.successful(())
                }

                // Check that the updated value would not duplicate the current value version.

                unescapedSubmittedInternalValueContent = submittedInternalValueContent.unescape

                _ = if (unescapedSubmittedInternalValueContent.wouldDuplicateCurrentVersion(currentValue.valueContent)) {
                    throw DuplicateValueException("The submitted value is the same as the current version")
                }

                // Check that the updated value would not duplicate another existing value of the resource.

                currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values.getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2]).filter(_.valueIri != updateValueRequest.updateValue.valueIri)

                _ = if (currentValuesForProp.exists(currentVal => unescapedSubmittedInternalValueContent.wouldDuplicateOtherValue(currentVal.valueContent))) {
                    throw DuplicateValueException()
                }

                _ <- submittedInternalValueContent match {
                    case textValueContent: TextValueContentV2 =>
                        // This is a text value. Check that the resources pointed to by any standoff link tags exist
                        // and that the user has permission to see them.
                        checkResourceIris(textValueContent.standoffLinkTagTargetResourceIris, updateValueRequest.requestingUser)

                    case _: LinkValueContentV2 =>
                        // We're updating a link. This means deleting an existing link and creating a new one, so
                        // check that the user has permission to modify the resource.
                        Future {
                            ResourceUtilV2.checkResourcePermission(
                                resourceInfo = resourceInfo,
                                permissionNeeded = ModifyPermission,
                                requestingUser = updateValueRequest.requestingUser
                            )
                        }

                    case _ => FastFuture.successful(())
                }

                // Get information about the project that the resource is in, so we know which named graph to put the new value in.
                projectInfo: ProjectGetResponseADM <- {
                    responderManager ? ProjectGetRequestADM(maybeIri = Some(resourceInfo.projectADM.id), requestingUser = updateValueRequest.requestingUser)
                }.mapTo[ProjectGetResponseADM]

                dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(projectInfo.project)

                // Create the new value version.

                unverifiedValue <- updateValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = adjustedInternalPropertyIri,
                    currentValue = currentValue,
                    newValueVersion = submittedInternalValueContent,
                    valueCreator = updateValueRequest.requestingUser.id,
                    valuePermissions = newValueVersionPermissionLiteral,
                    requestingUser = updateValueRequest.requestingUser
                )

                // Check that the value was written correctly to the triplestore.

                verifiedValue <- verifyValue(
                    resourceIri = updateValueRequest.updateValue.resourceIri,
                    propertyIriForGravsearch = adjustedInternalPropertyIri.toOntologySchema(ApiV2WithValueObjects),
                    propertyIriInResult = submittedInternalPropertyIri,
                    unverifiedValue = unverifiedValue,
                    requestingUser = updateValueRequest.requestingUser
                )
            } yield UpdateValueResponseV2(
                valueIri = unverifiedValue.newValueIri,
                valueType = unverifiedValue.valueContent.valueType
            )
        }

        val triplestoreUpdateFuture: Future[UpdateValueResponseV2] = for {
            // Don't allow anonymous users to create values.
            _ <- Future {
                if (updateValueRequest.requestingUser.isAnonymousUser) {
                    throw ForbiddenException("Anonymous users aren't allowed to update values")
                } else {
                    updateValueRequest.requestingUser.id
                }
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- IriLocker.runWithIriLock(
                updateValueRequest.apiRequestID,
                updateValueRequest.updateValue.resourceIri,
                () => makeTaskFuture
            )
        } yield taskResult

        ResourceUtilV2.doSipiPostUpdate(
            updateFuture = triplestoreUpdateFuture,
            valueContent = updateValueRequest.updateValue.valueContent,
            requestingUser = updateValueRequest.requestingUser,
            responderManager = responderManager,
            log = log
        )
    }

    /**
      * Updates an existing value (either an ordinary value or a link), using an existing transaction, assuming that
      * pre-update checks have already been done.
      *
      * @param dataNamedGraph   the named graph in which the value is to be created.
      * @param resourceInfo     information about the the resource in which to create the value.
      * @param propertyIri      the IRI of the property that will point from the resource to the value.
      * @param currentValue     the value to be updated.
      * @param newValueVersion  the new version of the value.
      * @param valueCreator     the IRI of the new value's owner.
      * @param valuePermissions the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
      * @param requestingUser   the user making the request.
      * @return an [[UnverifiedValueV2]].
      */
    private def updateValueV2AfterChecks(dataNamedGraph: IRI,
                                         resourceInfo: ReadResourceV2,
                                         propertyIri: SmartIri,
                                         currentValue: ReadValueV2,
                                         newValueVersion: ValueContentV2,
                                         valueCreator: IRI,
                                         valuePermissions: String,
                                         requestingUser: UserADM): Future[UnverifiedValueV2] = {
        (currentValue.valueContent, newValueVersion) match {
            case (currentLinkValue: LinkValueContentV2, newLinkValue: LinkValueContentV2) =>
                updateLinkValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = propertyIri,
                    currentLinkValue = currentLinkValue,
                    newLinkValue = newLinkValue,
                    valueCreator = valueCreator,
                    valuePermissions = valuePermissions,
                    requestingUser = requestingUser
                )

            case _ =>
                val newValueIri = knoraIdUtil.makeRandomValueIri(resourceInfo.resourceIri)

                updateOrdinaryValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = propertyIri,
                    currentValue = currentValue,
                    newValueIri = newValueIri,
                    newValueVersion = newValueVersion,
                    valueCreator = valueCreator,
                    valuePermissions = valuePermissions,
                    requestingUser = requestingUser
                )
        }
    }

    /**
      * Changes an ordinary value (i.e. not a link), assuming that pre-update checks have already been done.
      *
      * @param dataNamedGraph   the IRI of the named graph to be updated.
      * @param resourceInfo     information about the resource containing the value.
      * @param propertyIri      the IRI of the property that points to the value.
      * @param currentValue     a [[ReadValueV2]] representing the existing value version.
      * @param newValueIri      the IRI of the new value.
      * @param newValueVersion  a [[ValueContentV2]] representing the new value version, in the internal schema.
      * @param valueCreator     the IRI of the new value's owner.
      * @param valuePermissions the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
      * @param requestingUser   the user making the request.
      * @return an [[UnverifiedValueV2]].
      */
    private def updateOrdinaryValueV2AfterChecks(dataNamedGraph: IRI,
                                                 resourceInfo: ReadResourceV2,
                                                 propertyIri: SmartIri,
                                                 currentValue: ReadValueV2,
                                                 newValueIri: IRI,
                                                 newValueVersion: ValueContentV2,
                                                 valueCreator: IRI,
                                                 valuePermissions: String,
                                                 requestingUser: UserADM): Future[UnverifiedValueV2] = {

        // If we're updating a text value, update direct links and LinkValues for any resource references in Standoff.
        val standoffLinkUpdates = (currentValue.valueContent, newValueVersion) match {
            case (currentTextValue: TextValueContentV2, newTextValue: TextValueContentV2) =>
                // Identify the resource references that have been added or removed in the new version of
                // the value.
                val addedResourceRefs = newTextValue.standoffLinkTagTargetResourceIris -- currentTextValue.standoffLinkTagTargetResourceIris
                val removedResourceRefs = currentTextValue.standoffLinkTagTargetResourceIris -- newTextValue.standoffLinkTagTargetResourceIris

                // Construct a SparqlTemplateLinkUpdate for each reference that was added.
                val standoffLinkUpdatesForAddedResourceRefs: Seq[SparqlTemplateLinkUpdate] = addedResourceRefs.toVector.map {
                    targetResourceIri =>
                        incrementLinkValue(
                            sourceResourceInfo = resourceInfo,
                            linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                            targetResourceIri = targetResourceIri,
                            valueCreator = OntologyConstants.KnoraBase.SystemUser,
                            valuePermissions = standoffLinkValuePermissions,
                            requestingUser = requestingUser
                        )
                }

                // Construct a SparqlTemplateLinkUpdate for each reference that was removed.
                val standoffLinkUpdatesForRemovedResourceRefs: Seq[SparqlTemplateLinkUpdate] = removedResourceRefs.toVector.map {
                    removedTargetResource =>
                        decrementLinkValue(
                            sourceResourceInfo = resourceInfo,
                            linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                            targetResourceIri = removedTargetResource,
                            valueCreator = OntologyConstants.KnoraBase.SystemUser,
                            valuePermissions = standoffLinkValuePermissions,
                            requestingUser = requestingUser
                        )
                }

                standoffLinkUpdatesForAddedResourceRefs ++ standoffLinkUpdatesForRemovedResourceRefs

            case _ => Vector.empty[SparqlTemplateLinkUpdate]
        }

        // Make a timestamp to indicate when the value was updated.
        val currentTime: Instant = Instant.now

        for {
            // Generate a SPARQL update.
            sparqlUpdate <- Future(queries.sparql.v2.txt.addValueVersion(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                resourceIri = resourceInfo.resourceIri,
                propertyIri = propertyIri,
                currentValueIri = currentValue.valueIri,
                newValueIri = newValueIri,
                valueTypeIri = newValueVersion.valueType,
                value = newValueVersion,
                valueCreator = valueCreator,
                valuePermissions = valuePermissions,
                maybeComment = newValueVersion.comment,
                linkUpdates = standoffLinkUpdates,
                currentTime = currentTime
            ).toString())

            /*
            _ = println("================ Update value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
            */

            // Do the update.
            _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        } yield UnverifiedValueV2(
            newValueIri = newValueIri,
            valueContent = newValueVersion.unescape,
            permissions = valuePermissions
        )
    }

    /**
      * Changes a link, assuming that pre-update checks have already been done.
      *
      * @param dataNamedGraph   the IRI of the named graph to be updated.
      * @param resourceInfo     information about the resource containing the link.
      * @param propertyIri      the IRI of the link property.
      * @param currentLinkValue a [[LinkValueContentV2]] representing the `knora-base:LinkValue` for the existing link.
      * @param newLinkValue     a [[LinkValueContentV2]] indicating the new target resource.
      * @param valueCreator     the IRI of the new link value's owner.
      * @param valuePermissions the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
      * @param requestingUser   the user making the request.
      * @return an [[UnverifiedValueV2]].
      */
    private def updateLinkValueV2AfterChecks(dataNamedGraph: IRI,
                                             resourceInfo: ReadResourceV2,
                                             propertyIri: SmartIri,
                                             currentLinkValue: LinkValueContentV2,
                                             newLinkValue: LinkValueContentV2,
                                             valueCreator: IRI,
                                             valuePermissions: String,
                                             requestingUser: UserADM): Future[UnverifiedValueV2] = {
        // Delete the existing link and decrement its LinkValue's reference count.
        val sparqlTemplateLinkUpdateForCurrentLink: SparqlTemplateLinkUpdate = decrementLinkValue(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = propertyIri,
            targetResourceIri = currentLinkValue.referredResourceIri,
            valueCreator = valueCreator,
            valuePermissions = valuePermissions,
            requestingUser = requestingUser
        )

        // Create a new link, and create a new LinkValue for it.
        val sparqlTemplateLinkUpdateForNewLink: SparqlTemplateLinkUpdate = incrementLinkValue(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = propertyIri,
            targetResourceIri = newLinkValue.referredResourceIri,
            valueCreator = valueCreator,
            valuePermissions = valuePermissions,
            requestingUser = requestingUser
        )

        // Make a timestamp to indicate when the link value was updated.
        val currentTime: Instant = Instant.now

        for {
            // Generate a SPARQL update string.
            sparqlUpdate <- Future(queries.sparql.v2.txt.changeLink(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                linkSourceIri = resourceInfo.resourceIri,
                linkUpdateForCurrentLink = sparqlTemplateLinkUpdateForCurrentLink,
                linkUpdateForNewLink = sparqlTemplateLinkUpdateForNewLink,
                maybeComment = newLinkValue.comment,
                currentTime = currentTime
            ).toString())

            /*
            _ = println("================ Update link ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
            */

            _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
        } yield UnverifiedValueV2(
            newValueIri = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
            valueContent = newLinkValue.unescape,
            permissions = valuePermissions
        )
    }

    /**
      * Marks a value as deleted.
      *
      * @param deleteValueRequest the request to mark the value as deleted.
      */
    private def deleteValueV2(deleteValueRequest: DeleteValueRequestV2): Future[SuccessResponseV2] = {
        def makeTaskFuture: Future[SuccessResponseV2] = {
            for {
                // Convert the submitted property IRI to the internal schema.
                submittedInternalPropertyIri: SmartIri <- Future(deleteValueRequest.propertyIri.toOntologySchema(InternalSchema))

                // Get ontology information about the submitted property.

                propertyInfoRequestForSubmittedProperty = PropertiesGetRequestV2(
                    propertyIris = Set(submittedInternalPropertyIri),
                    allLanguages = false,
                    requestingUser = deleteValueRequest.requestingUser
                )

                propertyInfoResponseForSubmittedProperty: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForSubmittedProperty).mapTo[ReadOntologyV2]
                propertyInfoForSubmittedProperty: ReadPropertyInfoV2 = propertyInfoResponseForSubmittedProperty.properties(submittedInternalPropertyIri)

                // Don't accept link properties.
                _ = if (propertyInfoForSubmittedProperty.isLinkProp) {
                    throw BadRequestException(s"Invalid property <${propertyInfoForSubmittedProperty.entityInfoContent.propertyIri.toOntologySchema(ApiV2WithValueObjects)}>. Use a link value property to submit a link.")
                }

                // Don't accept knora-api:hasStandoffLinkToValue.
                _ = if (deleteValueRequest.propertyIri.toString == OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue) {
                    throw BadRequestException(s"Values of <${deleteValueRequest.propertyIri}> cannot be deleted directly")
                }

                // Make an adjusted version of the submitted property: if it's a link value property, substitute the
                // corresponding link property, whose objects we will need to query. Get ontology information about the
                // adjusted property.

                adjustedInternalPropertyInfo: ReadPropertyInfoV2 <- getAdjustedInternalPropertyInfo(
                    submittedPropertyIri = deleteValueRequest.propertyIri,
                    maybeSubmittedValueType = None,
                    propertyInfoForSubmittedProperty = propertyInfoForSubmittedProperty,
                    requestingUser = deleteValueRequest.requestingUser
                )

                adjustedInternalPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri

                // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
                // so we can see objects that the user doesn't have permission to see.

                resourceInfo: ReadResourceV2 <- getResourceWithPropertyValues(
                    resourceIri = deleteValueRequest.resourceIri,
                    propertyInfo = adjustedInternalPropertyInfo,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )

                // Check that the resource has the value that the user wants to delete, as an object of the submitted property.

                maybeCurrentValue: Option[ReadValueV2] = resourceInfo.values.get(submittedInternalPropertyIri).flatMap(_.find(_.valueIri == deleteValueRequest.valueIri))

                // Check that the user has permission to delete the value.

                currentValue: ReadValueV2 = maybeCurrentValue match {
                    case Some(value) => value
                    case None => throw NotFoundException(s"Resource <${deleteValueRequest.resourceIri}> does not have value <${deleteValueRequest.valueIri}> as an object of property <${deleteValueRequest.propertyIri}>")
                }

                _ = ResourceUtilV2.checkValuePermission(
                    resourceInfo = resourceInfo,
                    valueInfo = currentValue,
                    permissionNeeded = DeletePermission,
                    requestingUser = deleteValueRequest.requestingUser
                )

                // Get the definition of the resource class.

                classInfoRequest = ClassesGetRequestV2(
                    classIris = Set(resourceInfo.resourceClassIri),
                    allLanguages = false,
                    requestingUser = deleteValueRequest.requestingUser
                )

                classInfoResponse: ReadOntologyV2 <- (responderManager ? classInfoRequest).mapTo[ReadOntologyV2]
                classInfo: ReadClassInfoV2 = classInfoResponse.classes(resourceInfo.resourceClassIri)
                cardinalityInfo: Cardinality.KnoraCardinalityInfo = classInfo.allCardinalities.getOrElse(submittedInternalPropertyIri, throw InconsistentTriplestoreDataException(s"Resource <${deleteValueRequest.resourceIri}> belongs to class <${resourceInfo.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}>, which has no cardinality for property <${deleteValueRequest.propertyIri}>"))

                // Check that the resource class's cardinality for the submitted property allows this value to be deleted.

                currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values.getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])

                _ = if ((cardinalityInfo.cardinality == Cardinality.MustHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveSome) && currentValuesForProp.size == 1) {
                    throw OntologyConstraintException(s"Resource class <${resourceInfo.resourceClassIri.toOntologySchema(ApiV2WithValueObjects)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${deleteValueRequest.propertyIri}>, and this does not allow a value to be deleted for that property from resource <${deleteValueRequest.resourceIri}>")
                }

                // Get information about the project that the resource is in, so we know which named graph to do the update in.

                projectInfo: ProjectGetResponseADM <- {
                    responderManager ? ProjectGetRequestADM(maybeIri = Some(resourceInfo.projectADM.id), requestingUser = deleteValueRequest.requestingUser)
                }.mapTo[ProjectGetResponseADM]

                dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(projectInfo.project)

                // Do the update.
                deletedValueIri: IRI <- deleteValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = adjustedInternalPropertyIri,
                    deleteComment = deleteValueRequest.deleteComment,
                    currentValue = currentValue,
                    requestingUser = deleteValueRequest.requestingUser
                )

                // Check whether the update succeeded.
                sparqlQuery = queries.sparql.v2.txt.checkValueDeletion(
                    triplestore = settings.triplestoreType,
                    valueIri = deletedValueIri
                ).toString()

                sparqlSelectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
                rows = sparqlSelectResponse.results.bindings

                _ = if (rows.isEmpty || !stringFormatter.optionStringToBoolean(rows.head.rowMap.get("isDeleted"), throw InconsistentTriplestoreDataException(s"Invalid boolean for isDeleted: ${rows.head.rowMap.get("isDeleted")}"))) {
                    throw UpdateNotPerformedException(s"The request to mark value <${deleteValueRequest.valueIri}> (or a new version of that value) as deleted did not succeed. Please report this as a possible bug.")
                }
            } yield SuccessResponseV2(s"Value <$deletedValueIri> marked as deleted")
        }

        for {
            // Don't allow anonymous users to create values.
            _ <- Future {
                if (deleteValueRequest.requestingUser.isAnonymousUser) {
                    throw ForbiddenException("Anonymous users aren't allowed to update values")
                } else {
                    deleteValueRequest.requestingUser.id
                }
            }

            // Do the remaining pre-update checks and the update while holding an update lock on the resource.
            taskResult <- IriLocker.runWithIriLock(
                deleteValueRequest.apiRequestID,
                deleteValueRequest.resourceIri,
                () => makeTaskFuture
            )
        } yield taskResult
    }


    /**
      * Deletes a value (either an ordinary value or a link), using an existing transaction, assuming that
      * pre-update checks have already been done.
      *
      * @param dataNamedGraph the named graph in which the value is to be deleted.
      * @param resourceInfo   information about the the resource in which to create the value.
      * @param propertyIri    the IRI of the property that points from the resource to the value.
      * @param currentValue   the value to be deleted.
      * @param deleteComment  an optional comment explaining why the value is being deleted.
      * @param requestingUser the user making the request.
      * @return the IRI of the value that was marked as deleted.
      */
    private def deleteValueV2AfterChecks(dataNamedGraph: IRI,
                                         resourceInfo: ReadResourceV2,
                                         propertyIri: SmartIri,
                                         deleteComment: Option[String],
                                         currentValue: ReadValueV2,
                                         requestingUser: UserADM): Future[IRI] = {
        currentValue.valueContent match {
            case _: LinkValueContentV2 =>
                deleteLinkValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = propertyIri,
                    currentValue = currentValue,
                    deleteComment = deleteComment,
                    requestingUser = requestingUser
                )

            case _ =>
                deleteOrdinaryValueV2AfterChecks(
                    dataNamedGraph = dataNamedGraph,
                    resourceInfo = resourceInfo,
                    propertyIri = propertyIri,
                    currentValue = currentValue,
                    deleteComment = deleteComment,
                    requestingUser = requestingUser
                )
        }
    }

    /**
      * Deletes a link after checks.
      *
      * @param dataNamedGraph the named graph in which the value is to be deleted.
      * @param resourceInfo   information about the the resource in which to create the value.
      * @param propertyIri    the IRI of the property that points from the resource to the value.
      * @param currentValue   the value to be deleted.
      * @param deleteComment  an optional comment explaining why the value is being deleted.
      * @param requestingUser the user making the request.
      * @return the IRI of the value that was marked as deleted.
      */
    private def deleteLinkValueV2AfterChecks(dataNamedGraph: IRI,
                                             resourceInfo: ReadResourceV2,
                                             propertyIri: SmartIri,
                                             currentValue: ReadValueV2,
                                             deleteComment: Option[String],
                                             requestingUser: UserADM): Future[IRI] = {
        // Make a new version of of the LinkValue with a reference count of 0, and mark the new
        // version as deleted. Give the new version the same permissions as the previous version.

        val currentLinkValueContent: LinkValueContentV2 = currentValue.valueContent match {
            case linkValueContent: LinkValueContentV2 => linkValueContent
            case _ => throw AssertionException("Unreachable code")
        }

        // Delete the existing link and decrement its LinkValue's reference count.
        val sparqlTemplateLinkUpdate = decrementLinkValue(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = propertyIri,
            targetResourceIri = currentLinkValueContent.referredResourceIri,
            valueCreator = currentValue.attachedToUser,
            valuePermissions = currentValue.permissions,
            requestingUser = requestingUser
        )

        // Make a timestamp to indicate when the link value was updated.
        val currentTime: Instant = Instant.now

        for {
            sparqlUpdate <- Future(queries.sparql.v2.txt.deleteLink(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                linkSourceIri = resourceInfo.resourceIri,
                linkUpdate = sparqlTemplateLinkUpdate,
                maybeComment = deleteComment,
                currentTime = currentTime
            ).toString())

            _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
        } yield sparqlTemplateLinkUpdate.newLinkValueIri
    }

    /**
      * Deletes an ordinary value after checks.
      *
      * @param dataNamedGraph the named graph in which the value is to be deleted.
      * @param resourceInfo   information about the the resource in which to create the value.
      * @param propertyIri    the IRI of the property that points from the resource to the value.
      * @param currentValue   the value to be deleted.
      * @param deleteComment  an optional comment explaining why the value is being deleted.
      * @param requestingUser the user making the request.
      * @return the IRI of the value that was marked as deleted.
      */
    private def deleteOrdinaryValueV2AfterChecks(dataNamedGraph: IRI,
                                                 resourceInfo: ReadResourceV2,
                                                 propertyIri: SmartIri,
                                                 currentValue: ReadValueV2,
                                                 deleteComment: Option[String],
                                                 requestingUser: UserADM): Future[IRI] = {
        // Mark the existing version of the value as deleted.

        // If it's a TextValue, make SparqlTemplateLinkUpdates for updating LinkValues representing
        // links in standoff markup.
        val linkUpdates: Seq[SparqlTemplateLinkUpdate] = currentValue.valueContent match {
            case textValue: TextValueContentV2 =>
                textValue.standoffLinkTagTargetResourceIris.toVector.map {
                    removedTargetResource =>
                        decrementLinkValue(
                            sourceResourceInfo = resourceInfo,
                            linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                            targetResourceIri = removedTargetResource,
                            valueCreator = OntologyConstants.KnoraBase.SystemUser,
                            valuePermissions = standoffLinkValuePermissions,
                            requestingUser = requestingUser
                        )
                }

            case _ => Seq.empty[SparqlTemplateLinkUpdate]
        }

        // Make a timestamp to indicate when the value was marked as deleted.
        val currentTime: Instant = Instant.now

        for {
            sparqlUpdate <- Future(queries.sparql.v2.txt.deleteValue(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                resourceIri = resourceInfo.resourceIri,
                propertyIri = propertyIri,
                valueIri = currentValue.valueIri,
                maybeDeleteComment = deleteComment,
                linkUpdates = linkUpdates,
                currentTime = currentTime
            ).toString())

            _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
        } yield currentValue.valueIri
    }

    /**
      * When a property IRI is submitted for an update, makes an adjusted version of the submitted property:
      * if it's a link value property, substitutes the corresponding link property, whose objects we will need to query.
      *
      * @param submittedPropertyIri             the submitted property IRI, in the API v2 complex schema.
      * @param maybeSubmittedValueType          the submitted value type, if provided, in the API v2 complex schema.
      * @param propertyInfoForSubmittedProperty ontology information about the submitted property, in the internal schema.
      * @param requestingUser                   the requesting user.
      * @return ontology information about the adjusted property.
      */
    private def getAdjustedInternalPropertyInfo(submittedPropertyIri: SmartIri,
                                                maybeSubmittedValueType: Option[SmartIri],
                                                propertyInfoForSubmittedProperty: ReadPropertyInfoV2,
                                                requestingUser: UserADM): Future[ReadPropertyInfoV2] = {
        val submittedInternalPropertyIri: SmartIri = submittedPropertyIri.toOntologySchema(InternalSchema)

        if (propertyInfoForSubmittedProperty.isLinkValueProp) {
            maybeSubmittedValueType match {
                case Some(submittedValueType) =>
                    if (submittedValueType.toString != OntologyConstants.KnoraApiV2WithValueObjects.LinkValue) {
                        FastFuture.failed(BadRequestException(s"A value of type <$submittedValueType> cannot be an object of property <$submittedPropertyIri>"))
                    }

                case None => ()
            }

            for {
                internalLinkPropertyIri <- Future(submittedInternalPropertyIri.fromLinkValuePropToLinkProp)

                propertyInfoRequestForLinkProperty = PropertiesGetRequestV2(
                    propertyIris = Set(internalLinkPropertyIri),
                    allLanguages = false,
                    requestingUser = requestingUser
                )

                linkPropertyInfoResponse: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForLinkProperty).mapTo[ReadOntologyV2]
            } yield linkPropertyInfoResponse.properties(internalLinkPropertyIri)
        } else if (propertyInfoForSubmittedProperty.isLinkProp) {
            throw BadRequestException(s"Invalid property for creating a link value (submit a link value property instead): $submittedPropertyIri")
        } else {
            FastFuture.successful(propertyInfoForSubmittedProperty)
        }
    }

    /**
      * Given a set of resource IRIs, checks that they point to Knora resources.
      * If not, throws an exception.
      *
      * @param targeResourceIris the IRIs to be checked.
      * @param requestingUser    the user making the request.
      */
    private def checkResourceIris(targeResourceIris: Set[IRI], requestingUser: UserADM): Future[Unit] = {
        if (targeResourceIris.isEmpty) {
            FastFuture.successful(())
        } else {
            for {
                resourcePreviewRequest <- FastFuture.successful(
                    ResourcesPreviewGetRequestV2(
                        resourceIris = targeResourceIris.toSeq,
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
        } yield searchResponse.toResource(resourceIri)
    }

    /*
    private def findResourceWithValue(valueIri: IRI, requestingUser: UserADM): Future[ReadResourceV2] = {

    }
    */

    /**
      * Verifies that a value was written correctly to the triplestore.
      *
      * @param resourceIri              the IRI of the resource that the value belongs to.
      * @param propertyIriForGravsearch the external IRI the property to be used in Gravsearch to find the value. If the value is a link value, this is the link property.
      * @param propertyIriInResult      the internal IRI of the property that is expected to be returned. If the value is a link value, this is the link value property.
      * @param unverifiedValue          the value that should have been written to the triplestore.
      * @param requestingUser           the user making the request.
      */
    private def verifyValue(resourceIri: IRI,
                            propertyIriForGravsearch: SmartIri,
                            propertyIriInResult: SmartIri,
                            unverifiedValue: UnverifiedValueV2,
                            requestingUser: UserADM): Future[VerifiedValueV2] = {
        for {
            gravsearchQuery: String <- Future(queries.gravsearch.txt.getResourceWithSpecifiedProperties(
                resourceIri = resourceIri,
                propertyIris = Seq(propertyIriForGravsearch)
            ).toString())

            parsedGravsearchQuery <- FastFuture.successful(GravsearchParser.parseQuery(gravsearchQuery))
            searchResponse <- (responderManager ? GravsearchRequestV2(parsedGravsearchQuery, requestingUser)).mapTo[ReadResourcesSequenceV2]

            resource = try {
                searchResponse.toResource(resourceIri)
            } catch {
                case _: NotFoundException => throw UpdateNotPerformedException(s"Resource <$resourceIri> was not created. Please report this as a possible bug.")
            }

            propertyValues = resource.values.getOrElse(propertyIriInResult, throw UpdateNotPerformedException())
            valueInTriplestore: ReadValueV2 = propertyValues.find(_.valueIri == unverifiedValue.newValueIri).getOrElse(throw UpdateNotPerformedException())

            _ = if (!(unverifiedValue.valueContent.wouldDuplicateCurrentVersion(valueInTriplestore.valueContent) &&
                valueInTriplestore.permissions == unverifiedValue.permissions &&
                valueInTriplestore.attachedToUser == requestingUser.id)) {
                /*
                import org.knora.webapi.util.MessageUtil
                println("==============================")
                println("Submitted value:")
                println(MessageUtil.toSource(unverifiedValue.valueContent))
                println
                println("==============================")
                println("Saved value:")
                println(MessageUtil.toSource(valueInTriplestore.valueContent))
                */
                throw AssertionException(s"The value saved as ${unverifiedValue.newValueIri} is not the same as the one that was submitted")
            }
        } yield VerifiedValueV2(
            newValueIri = unverifiedValue.newValueIri,
            value = unverifiedValue.valueContent,
            permissions = unverifiedValue.permissions
        )
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
                    resourceIris = Seq(linkValueContent.referredResourceIri),
                    requestingUser = requestingUser
                )
            )

            resourcePreviewResponse <- (responderManager ? resourcePreviewRequest).mapTo[ReadResourcesSequenceV2]

            // If we get a resource, we know the user has permission to view it.
            resource: ReadResourceV2 = resourcePreviewResponse.toResource(linkValueContent.referredResourceIri)

            // Ask the ontology responder whether the resource's class is a subclass of the link property's object class constraint.

            subClassRequest = CheckSubClassRequestV2(
                subClassIri = resource.resourceClassIri,
                superClassIri = objectClassConstraint,
                requestingUser = requestingUser
            )

            subClassResponse <- (responderManager ? subClassRequest).mapTo[CheckSubClassResponseV2]

            // If it isn't, throw an exception.
            _ = if (!subClassResponse.isSubClass) {
                throw OntologyConstraintException(s"Resource <${linkValueContent.referredResourceIri}> cannot be the target of property <$linkPropertyIri>, because it is not a member of class <$objectClassConstraint>")
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
                    // We're creating a link.

                    // Check that the property whose object class constraint is to be checked is actually a link property.
                    if (!propertyInfo.isLinkProp) {
                        throw BadRequestException(s"Property <${propertyInfo.entityInfoContent.propertyIri.toOntologySchema(ApiV2WithValueObjects)}> is not a link property")
                    }

                    // Check that the user has permission to view the target resource, and that the target resource has the correct type.
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
      * Given a [[ReadResourceV2]], finds a link that uses the specified property and points to the specified target
      * resource.
      *
      * @param sourceResourceInfo a [[ReadResourceV2]] describing the source of the link.
      * @param linkPropertyIri    the IRI of the link property.
      * @param targetResourceIri  the IRI of the target resource.
      * @return a [[ReadLinkValueV2]] describing the link value, if found.
      */
    private def findLinkValue(sourceResourceInfo: ReadResourceV2,
                              linkPropertyIri: SmartIri,
                              targetResourceIri: IRI): Option[ReadLinkValueV2] = {
        val linkValueProperty = linkPropertyIri.fromLinkPropToLinkValueProp

        sourceResourceInfo.values.get(linkValueProperty).flatMap {
            linkValueInfos: Seq[ReadValueV2] =>
                linkValueInfos.collectFirst {
                    case linkValueInfo: ReadLinkValueV2 if linkValueInfo.valueContent.referredResourceIri == targetResourceIri => linkValueInfo
                }
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
        val maybeLinkValueInfo: Option[ReadLinkValueV2] = findLinkValue(
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
                SparqlTemplateLinkUpdate(
                    linkPropertyIri = linkPropertyIri,
                    directLinkExists = true,
                    insertDirectLink = false,
                    deleteDirectLink = false,
                    linkValueExists = true,
                    linkTargetExists = true,
                    newLinkValueIri = newLinkValueIri,
                    linkTargetIri = targetResourceIri,
                    currentReferenceCount = linkValueInfo.valueHasRefCount,
                    newReferenceCount = linkValueInfo.valueHasRefCount + 1,
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
                val newReferenceCount = linkValueInfo.valueHasRefCount - 1

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
                    currentReferenceCount = linkValueInfo.valueHasRefCount,
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
