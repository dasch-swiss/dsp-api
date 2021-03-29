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
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionType}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.messages.util.PermissionUtilADM._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.{KnoraSystemInstances, PermissionUtilADM, ResponderData}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri}
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil

import scala.concurrent.Future

/**
  * Handles requests to read and write Knora values.
  */
class ValuesResponderV2(responderData: ResponderData) extends Responder(responderData) {

  /**
    * The IRI and content of a new value or value version whose existence in the triplestore has been verified.
    *
    * @param newValueIri the IRI that was assigned to the new value.
    * @param value       the content of the new value.
    * @param permissions the permissions attached to the new value.
    */
  private case class VerifiedValueV2(newValueIri: IRI, value: ValueContentV2, permissions: String)

  /**
    * Receives a message of type [[ValuesResponderRequestV2]], and returns an appropriate response message.
    */
  def receive(msg: ValuesResponderRequestV2) = msg match {
    case createValueRequest: CreateValueRequestV2 => createValueV2(createValueRequest)
    case updateValueRequest: UpdateValueRequestV2 => updateValueV2(updateValueRequest)
    case deleteValueRequest: DeleteValueRequestV2 => deleteValueV2(deleteValueRequest)
    case createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV2 =>
      generateSparqlToCreateMultipleValuesV2(createMultipleValuesRequest)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
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
        submittedInternalPropertyIri: SmartIri <- Future(
          createValueRequest.createValue.propertyIri.toOntologySchema(InternalSchema))
        submittedInternalValueContent: ValueContentV2 = createValueRequest.createValue.valueContent
          .toOntologySchema(InternalSchema)

        // Get ontology information about the submitted property.

        propertyInfoRequestForSubmittedProperty = PropertiesGetRequestV2(
          propertyIris = Set(submittedInternalPropertyIri),
          allLanguages = false,
          requestingUser = createValueRequest.requestingUser
        )

        propertyInfoResponseForSubmittedProperty: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForSubmittedProperty)
          .mapTo[ReadOntologyV2]
        propertyInfoForSubmittedProperty: ReadPropertyInfoV2 = propertyInfoResponseForSubmittedProperty.properties(
          submittedInternalPropertyIri)

        // Don't accept link properties.
        _ = if (propertyInfoForSubmittedProperty.isLinkProp) {
          throw BadRequestException(
            s"Invalid property <${createValueRequest.createValue.propertyIri}>. Use a link value property to submit a link.")
        }

        // Don't accept knora-api:hasStandoffLinkToValue.
        _ = if (createValueRequest.createValue.propertyIri.toString == OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue) {
          throw BadRequestException(
            s"Values of <${createValueRequest.createValue.propertyIri}> cannot be created directly")
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
          featureFactoryConfig = createValueRequest.featureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )

        // Check that the user has permission to modify the resource.

        _ = ResourceUtilV2.checkResourcePermission(
          resourceInfo = resourceInfo,
          permissionNeeded = ModifyPermission,
          requestingUser = createValueRequest.requestingUser
        )

        // Check that the resource has the rdf:type that the client thinks it has.

        _ = if (resourceInfo.resourceClassIri != createValueRequest.createValue.resourceClassIri.toOntologySchema(
                  InternalSchema)) {
          throw BadRequestException(
            s"The rdf:type of resource <${createValueRequest.createValue.resourceIri}> is not <${createValueRequest.createValue.resourceClassIri}>")
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
        cardinalityInfo: Cardinality.KnoraCardinalityInfo = classInfo.allCardinalities.getOrElse(
          submittedInternalPropertyIri,
          throw BadRequestException(
            s"Resource <${createValueRequest.createValue.resourceIri}> belongs to class <${resourceInfo.resourceClassIri.toOntologySchema(
              ApiV2Complex)}>, which has no cardinality for property <${createValueRequest.createValue.propertyIri}>")
        )

        // Check that the object of the adjusted property (the value to be created, or the target of the link to be created) will have
        // the correct type for the adjusted property's knora-base:objectClassConstraint.

        _ <- checkPropertyObjectClassConstraint(
          propertyInfo = adjustedInternalPropertyInfo,
          valueContent = submittedInternalValueContent,
          featureFactoryConfig = createValueRequest.featureFactoryConfig,
          requestingUser = createValueRequest.requestingUser
        )

        // If this is a list value, check that it points to a real list node.

        _ <- submittedInternalValueContent match {
          case listValue: HierarchicalListValueContentV2 =>
            ResourceUtilV2.checkListNodeExists(listValue.valueHasListNode, storeManager)
          case _ => FastFuture.successful(())
        }

        // Check that the resource class's cardinality for the submitted property allows another value to be added
        // for that property.

        currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values
          .getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])

        _ = if ((cardinalityInfo.cardinality == Cardinality.MustHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveSome) && currentValuesForProp.isEmpty) {
          throw InconsistentRepositoryDataException(s"Resource class <${resourceInfo.resourceClassIri.toOntologySchema(
            ApiV2Complex)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${createValueRequest.createValue.propertyIri}>, but resource <${createValueRequest.createValue.resourceIri}> has no value for that property")
        }

        _ = if (cardinalityInfo.cardinality == Cardinality.MustHaveOne || (cardinalityInfo.cardinality == Cardinality.MayHaveOne && currentValuesForProp.nonEmpty)) {
          throw OntologyConstraintException(s"Resource class <${resourceInfo.resourceClassIri.toOntologySchema(
            ApiV2Complex)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${createValueRequest.createValue.propertyIri}>, and this does not allow a value to be added for that property to resource <${createValueRequest.createValue.resourceIri}>")
        }

        // Check that the new value would not duplicate an existing value.

        unescapedSubmittedInternalValueContent = submittedInternalValueContent.unescape

        _ = if (currentValuesForProp.exists(currentVal =>
                  unescapedSubmittedInternalValueContent.wouldDuplicateOtherValue(currentVal.valueContent))) {
          throw DuplicateValueException()
        }

        // If this is a text value, check that the resources pointed to by any standoff link tags exist
        // and that the user has permission to see them.

        _ <- submittedInternalValueContent match {
          case textValueContent: TextValueContentV2 =>
            checkResourceIris(
              targetResourceIris = textValueContent.standoffLinkTagTargetResourceIris,
              featureFactoryConfig = createValueRequest.featureFactoryConfig,
              requestingUser = createValueRequest.requestingUser
            )

          case _ => FastFuture.successful(())
        }

        // Get the default permissions for the new value.
        defaultValuePermissions: String <- ResourceUtilV2.getDefaultValuePermissions(
          projectIri = resourceInfo.projectADM.id,
          resourceClassIri = resourceInfo.resourceClassIri,
          propertyIri = submittedInternalPropertyIri,
          requestingUser = createValueRequest.requestingUser,
          responderManager = responderManager
        )
        (parsedValuePermissions, customValuePermissions) = PermissionUtilADM.parseAndReformatPermissions(
          createValueRequest.createValue.permissions,
          defaultValuePermissions)
        // Did the user submit permissions for the new value?
        newValuePermissionLiteral <- createValueRequest.createValue.permissions match {
          case Some(_) =>
            // Yes. Validate them.
            for {
              _ <- PermissionUtilADM.validatePermissions(
                parsedPermissions = parsedValuePermissions,
                featureFactoryConfig = createValueRequest.featureFactoryConfig,
                responderManager = responderManager
              )

              // Is the requesting user a system admin, or an admin of this project?
              _ = if (!(createValueRequest.requestingUser.permissions.isProjectAdmin(
                        createValueRequest.requestingUser.id) || createValueRequest.requestingUser.permissions.isSystemAdmin)) {

                // No. Make sure they don't give themselves higher permissions than they would get from the default permissions.

                val permissionComparisonResult: PermissionComparisonResult = PermissionUtilADM.comparePermissionsADM(
                  entityCreator = createValueRequest.requestingUser.id,
                  entityProject = resourceInfo.projectADM.id,
                  permissionLiteralA = customValuePermissions,
                  permissionLiteralB = defaultValuePermissions,
                  requestingUser = createValueRequest.requestingUser
                )

                if (permissionComparisonResult == AGreaterThanB) {
                  throw ForbiddenException(
                    s"The specified value permissions would give a value's creator a higher permission on the value than the default permissions")
                }
              }
            } yield customValuePermissions

          case None =>
            // No. Use the default permissions.
            FastFuture.successful(defaultValuePermissions)
        }

        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(resourceInfo.projectADM)

        // Create the new value.

        unverifiedValue <- createValueV2AfterChecks(
          dataNamedGraph = dataNamedGraph,
          projectIri = resourceInfo.projectADM.id,
          resourceInfo = resourceInfo,
          propertyIri = adjustedInternalPropertyIri,
          value = submittedInternalValueContent,
          valueIri = createValueRequest.createValue.valueIri,
          valueUUID = createValueRequest.createValue.valueUUID,
          valueCreationDate = createValueRequest.createValue.valueCreationDate,
          valueCreator = createValueRequest.requestingUser.id,
          valuePermissions = newValuePermissionLiteral,
          requestingUser = createValueRequest.requestingUser
        )

        // Check that the value was written correctly to the triplestore.

        verifiedValue: VerifiedValueV2 <- verifyValue(
          resourceIri = createValueRequest.createValue.resourceIri,
          propertyIri = submittedInternalPropertyIri,
          unverifiedValue = unverifiedValue,
          featureFactoryConfig = createValueRequest.featureFactoryConfig,
          requestingUser = createValueRequest.requestingUser
        )
      } yield
        CreateValueResponseV2(
          valueIri = verifiedValue.newValueIri,
          valueType = verifiedValue.value.valueType,
          valueUUID = unverifiedValue.newValueUUID,
          valueCreationDate = unverifiedValue.creationDate,
          projectADM = resourceInfo.projectADM
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

    // Since PR #1230, the cardinalities in knora-base don't allow you to create a file value
    // without creating a new resource, but we leave this line here in case it's needed again
    // someday:
    //
    // If we were creating a file value, have Sipi move the file to permanent storage if the update
    // was successful, or delete the temporary file if the update failed.
    ResourceUtilV2.doSipiPostUpdate(
      updateFuture = triplestoreUpdateFuture,
      valueContent = createValueRequest.createValue.valueContent,
      requestingUser = createValueRequest.requestingUser,
      responderManager = responderManager,
      storeManager = storeManager,
      log = log
    )
  }

  /**
    * Creates a new value (either an ordinary value or a link), using an existing transaction, assuming that
    * pre-update checks have already been done.
    *
    * @param dataNamedGraph    the named graph in which the value is to be created.
    * @param projectIri        the IRI of the project in which to create the value.
    * @param resourceInfo      information about the the resource in which to create the value.
    * @param propertyIri       the IRI of the property that will point from the resource to the value, or, if
    *                          the value is a link value, the IRI of the link property.
    * @param value             the value to create.
    * @param valueIri          the optional custom IRI supplied for the value.
    * @param valueUUID         the optional custom UUID supplied for the value.
    * @param valueCreationDate the optional custom creation date supplied for the value.
    * @param valueCreator      the IRI of the new value's owner.
    * @param valuePermissions  the literal that should be used as the object of the new value's
    *                          `knora-base:hasPermissions` predicate.
    * @param requestingUser    the user making the request.
    * @return an [[UnverifiedValueV2]].
    */
  private def createValueV2AfterChecks(dataNamedGraph: IRI,
                                       projectIri: IRI,
                                       resourceInfo: ReadResourceV2,
                                       propertyIri: SmartIri,
                                       value: ValueContentV2,
                                       valueIri: Option[SmartIri],
                                       valueUUID: Option[UUID],
                                       valueCreationDate: Option[Instant],
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
          maybeValueIri = valueIri,
          maybeValueUUID = valueUUID,
          maybeCreationDate = valueCreationDate,
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
          maybeValueIri = valueIri,
          maybeValueUUID = valueUUID,
          maybeValueCreationDate = valueCreationDate,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          requestingUser = requestingUser
        )
    }
  }

  /**
    * Creates an ordinary value (i.e. not a link), using an existing transaction, assuming that pre-update checks have already been done.
    *
    * @param resourceInfo           information about the the resource in which to create the value.
    * @param propertyIri            the property that should point to the value.
    * @param value                  an [[ValueContentV2]] describing the value.
    * @param maybeValueIri          the optional custom IRI supplied for the value.
    * @param maybeValueUUID         the optional custom UUID supplied for the value.
    * @param maybeValueCreationDate the optional custom creation date supplied for the value.
    * @param valueCreator           the IRI of the new value's owner.
    * @param valuePermissions       the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
    * @param requestingUser         the user making the request.
    * @return an [[UnverifiedValueV2]].
    */
  private def createOrdinaryValueV2AfterChecks(dataNamedGraph: IRI,
                                               resourceInfo: ReadResourceV2,
                                               propertyIri: SmartIri,
                                               value: ValueContentV2,
                                               maybeValueIri: Option[SmartIri],
                                               maybeValueUUID: Option[UUID],
                                               maybeValueCreationDate: Option[Instant],
                                               valueCreator: IRI,
                                               valuePermissions: String,
                                               requestingUser: UserADM): Future[UnverifiedValueV2] = {
    for {

      // Make an IRI for the new value.
      newValueIri: IRI <- checkOrCreateEntityIri(maybeValueIri,
                                                 stringFormatter.makeRandomValueIri(resourceInfo.resourceIri))

      // Make a UUID for the new value
      newValueUUID: UUID = maybeValueUUID match {
        case Some(customValueUUID) => customValueUUID
        case None                  => UUID.randomUUID
      }

      // Make a creation date for the new value
      creationDate: Instant = maybeValueCreationDate match {
        case Some(customCreationDate) => customCreationDate
        case None                     => Instant.now
      }

      // If we're creating a text value, update direct links and LinkValues for any resource references in standoff.
      standoffLinkUpdates <- value match {
        case textValueContent: TextValueContentV2 =>
          // Construct a SparqlTemplateLinkUpdate for each reference that was added.
          val linkUpdateFutures: Seq[Future[SparqlTemplateLinkUpdate]] =
            textValueContent.standoffLinkTagTargetResourceIris.map { targetResourceIri: IRI =>
              incrementLinkValue(
                sourceResourceInfo = resourceInfo,
                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                targetResourceIri = targetResourceIri,
                valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
                valuePermissions = standoffLinkValuePermissions,
                requestingUser = requestingUser
              )
            }.toVector

          Future.sequence(linkUpdateFutures)

        case _ => FastFuture.successful(Vector.empty[SparqlTemplateLinkUpdate])
      }

      // Generate a SPARQL update string.
      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .createValue(
          dataNamedGraph = dataNamedGraph,
          triplestore = settings.triplestoreType,
          resourceIri = resourceInfo.resourceIri,
          propertyIri = propertyIri,
          newValueIri = newValueIri,
          newValueUUID = newValueUUID,
          value = value,
          linkUpdates = standoffLinkUpdates,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          creationDate = creationDate,
          stringFormatter = stringFormatter
        )
        .toString()

      /*
            _ = println("================ Create value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
       */

      // Do the update.
      _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
    } yield
      UnverifiedValueV2(
        newValueIri = newValueIri,
        newValueUUID = newValueUUID,
        valueContent = value.unescape,
        permissions = valuePermissions,
        creationDate = creationDate
      )
  }

  /**
    * Creates a link, using an existing transaction, assuming that pre-update checks have already been done.
    *
    * @param dataNamedGraph   the named graph in which the link is to be created.
    * @param resourceInfo     information about the the resource in which to create the value.
    * @param linkPropertyIri  the link property.
    * @param linkValueContent a [[LinkValueContentV2]] specifying the target resource.
    * @param maybeValueIri    the optional custom IRI supplied for the value.
    * @param maybeValueUUID   the optional custom UUID supplied for the value.
    * @param valueCreator     the IRI of the new link value's owner.
    * @param valuePermissions the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param requestingUser   the user making the request.
    * @return an [[UnverifiedValueV2]].
    */
  private def createLinkValueV2AfterChecks(dataNamedGraph: IRI,
                                           resourceInfo: ReadResourceV2,
                                           linkPropertyIri: SmartIri,
                                           linkValueContent: LinkValueContentV2,
                                           maybeValueIri: Option[SmartIri],
                                           maybeValueUUID: Option[UUID],
                                           maybeCreationDate: Option[Instant],
                                           valueCreator: IRI,
                                           valuePermissions: String,
                                           requestingUser: UserADM): Future[UnverifiedValueV2] = {

    val newValueUUID: UUID = maybeValueUUID match {
      case Some(customValueUUID) => customValueUUID
      case None                  => UUID.randomUUID
    }

    for {
      sparqlTemplateLinkUpdate <- incrementLinkValue(
        sourceResourceInfo = resourceInfo,
        linkPropertyIri = linkPropertyIri,
        targetResourceIri = linkValueContent.referredResourceIri,
        customNewLinkValueIri = maybeValueIri,
        valueCreator = valueCreator,
        valuePermissions = valuePermissions,
        requestingUser = requestingUser
      )

      creationDate: Instant = maybeCreationDate match {
        case Some(customValueCreationDate) => customValueCreationDate
        case None                          => Instant.now
      }

      // Generate a SPARQL update string.
      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .createLink(
          dataNamedGraph = dataNamedGraph,
          triplestore = settings.triplestoreType,
          resourceIri = resourceInfo.resourceIri,
          linkUpdate = sparqlTemplateLinkUpdate,
          newValueUUID = newValueUUID,
          creationDate = creationDate,
          maybeComment = linkValueContent.comment,
          stringFormatter = stringFormatter
        )
        .toString()

      /*
            _ = println("================ Create link ===============")
            _ = println(sparqlUpdate)
            _ = println("=============================================")
       */

      // Do the update.
      _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
    } yield
      UnverifiedValueV2(
        newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
        newValueUUID = newValueUUID,
        valueContent = linkValueContent.unescape,
        permissions = valuePermissions,
        creationDate = creationDate
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
  private def generateSparqlToCreateMultipleValuesV2(
      createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV2)
    : Future[GenerateSparqlToCreateMultipleValuesResponseV2] = {
    for {
      // Generate SPARQL to create links and LinkValues for standoff links in text values.
      sparqlForStandoffLinks: Option[String] <- generateInsertSparqlForStandoffLinksInMultipleValues(
        createMultipleValuesRequest)

      // Generate SPARQL for each value.
      sparqlForPropertyValueFutures: Map[SmartIri, Seq[Future[InsertSparqlWithUnverifiedValue]]] = createMultipleValuesRequest.values
        .map {
          case (propertyIri: SmartIri, valuesToCreate: Seq[GenerateSparqlForValueInNewResourceV2]) =>
            propertyIri -> valuesToCreate.zipWithIndex.map {
              case (valueToCreate: GenerateSparqlForValueInNewResourceV2, valueHasOrder: Int) =>
                generateInsertSparqlWithUnverifiedValue(
                  resourceIri = createMultipleValuesRequest.resourceIri,
                  propertyIri = propertyIri,
                  valueToCreate = valueToCreate,
                  valueHasOrder = valueHasOrder,
                  resourceCreationDate = createMultipleValuesRequest.creationDate,
                  requestingUser = createMultipleValuesRequest.requestingUser
                )
            }
        }

      sparqlForPropertyValues: Map[SmartIri, Seq[InsertSparqlWithUnverifiedValue]] <- ActorUtil.sequenceSeqFuturesInMap(
        sparqlForPropertyValueFutures)

      // Concatenate all the generated SPARQL.
      allInsertSparql: String = sparqlForPropertyValues.values.flatten
        .map(_.insertSparql)
        .mkString("\n\n") + "\n\n" + sparqlForStandoffLinks.getOrElse("")

      // Collect all the unverified values.
      unverifiedValues: Map[SmartIri, Seq[UnverifiedValueV2]] = sparqlForPropertyValues.map {
        case (propertyIri, unverifiedValuesWithSparql) =>
          propertyIri -> unverifiedValuesWithSparql.map(_.unverifiedValue)
      }
    } yield
      GenerateSparqlToCreateMultipleValuesResponseV2(
        insertSparql = allInsertSparql,
        unverifiedValues = unverifiedValues,
        hasStandoffLink = sparqlForStandoffLinks.isDefined
      )
  }

  /**
    * Generates SPARQL to create one of multiple values in a new resource.
    *
    * @param resourceIri          the IRI of the resource.
    * @param propertyIri          the IRI of the property that will point to the value.
    * @param valueToCreate        the value to be created.
    * @param valueHasOrder        the value's `knora-base:valueHasOrder`.
    * @param resourceCreationDate the creation date of the resource.
    * @param requestingUser       the user making the request.
    * @return a [[InsertSparqlWithUnverifiedValue]] containing the generated SPARQL and an [[UnverifiedValueV2]].
    */
  private def generateInsertSparqlWithUnverifiedValue(
      resourceIri: IRI,
      propertyIri: SmartIri,
      valueToCreate: GenerateSparqlForValueInNewResourceV2,
      valueHasOrder: Int,
      resourceCreationDate: Instant,
      requestingUser: UserADM): Future[InsertSparqlWithUnverifiedValue] = {
    for {
      newValueIri: IRI <- checkOrCreateEntityIri(valueToCreate.customValueIri,
                                                 stringFormatter.makeRandomValueIri(resourceIri))

      // Make a UUID for the new value.
      newValueUUID: UUID = valueToCreate.customValueUUID match {
        case Some(customValueUUID) => customValueUUID
        case None                  => UUID.randomUUID
      }

      // Make a creation date for the value. If a custom creation date is given for a value, consider that otherwise
      // use resource creation date for the value.
      valueCreationDate: Instant = valueToCreate.customValueCreationDate match {
        case Some(customValueCreationDate) => customValueCreationDate
        case None                          => resourceCreationDate
      }

      // Generate the SPARQL.
      insertSparql: String = valueToCreate.valueContent match {
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
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .generateInsertStatementsForCreateLink(
              resourceIri = resourceIri,
              linkUpdate = sparqlTemplateLinkUpdate,
              creationDate = valueCreationDate,
              newValueUUID = newValueUUID,
              maybeComment = valueToCreate.valueContent.comment,
              maybeValueHasOrder = Some(valueHasOrder),
              stringFormatter = stringFormatter
            )
            .toString()

        case otherValueContentV2 =>
          // We're creating an ordinary value. Generate SPARQL for it.
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .generateInsertStatementsForCreateValue(
              resourceIri = resourceIri,
              propertyIri = propertyIri,
              value = otherValueContentV2,
              newValueIri = newValueIri,
              newValueUUID = newValueUUID,
              linkUpdates = Seq.empty[SparqlTemplateLinkUpdate], // This is empty because we have to generate SPARQL for standoff links separately.
              valueCreator = requestingUser.id,
              valuePermissions = valueToCreate.permissions,
              creationDate = valueCreationDate,
              maybeValueHasOrder = Some(valueHasOrder),
              stringFormatter = stringFormatter
            )
            .toString()
      }
    } yield
      InsertSparqlWithUnverifiedValue(
        insertSparql = insertSparql,
        unverifiedValue = UnverifiedValueV2(
          newValueIri = newValueIri,
          newValueUUID = newValueUUID,
          valueContent = valueToCreate.valueContent.unescape,
          permissions = valueToCreate.permissions,
          creationDate = valueCreationDate
        )
      )
  }

  /**
    * When processing a request to create multiple values, generates SPARQL for standoff links in text values.
    *
    * @param createMultipleValuesRequest the request to create multiple values.
    * @return SPARQL INSERT statements.
    */
  private def generateInsertSparqlForStandoffLinksInMultipleValues(
      createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV2): Future[Option[String]] = {
    // To create LinkValues for the standoff links in the values to be created, we need to compute
    // the initial reference count of each LinkValue. This is equal to the number of TextValues in the resource
    // that have standoff links to a particular target resource.

    // First, get the standoff link targets from all the text values to be created.
    val standoffLinkTargetsPerTextValue: Vector[Set[IRI]] =
      createMultipleValuesRequest.flatValues.foldLeft(Vector.empty[Set[IRI]]) {
        case (standoffLinkTargetsAcc: Vector[Set[IRI]], createValueV2: GenerateSparqlForValueInNewResourceV2) =>
          createValueV2.valueContent match {
            case textValueContentV2: TextValueContentV2
                if textValueContentV2.standoffLinkTagTargetResourceIris.nonEmpty =>
              standoffLinkTargetsAcc :+ textValueContentV2.standoffLinkTagTargetResourceIris

            case _ => standoffLinkTargetsAcc
          }
      }

    if (standoffLinkTargetsPerTextValue.nonEmpty) {
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
      val standoffLinkUpdatesFutures: Seq[Future[SparqlTemplateLinkUpdate]] = initialReferenceCounts.toSeq.map {
        case (targetIri, initialReferenceCount) =>
          for {
            newValueIri <- makeUnusedValueIri(createMultipleValuesRequest.resourceIri)
          } yield
            SparqlTemplateLinkUpdate(
              linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
              directLinkExists = false,
              insertDirectLink = true,
              deleteDirectLink = false,
              linkValueExists = false,
              linkTargetExists = true, // doesn't matter, the generateInsertStatementsForStandoffLinks template doesn't use it
              newLinkValueIri = newValueIri,
              linkTargetIri = targetIri,
              currentReferenceCount = 0,
              newReferenceCount = initialReferenceCount,
              newLinkValueCreator = OntologyConstants.KnoraAdmin.SystemUser,
              newLinkValuePermissions = standoffLinkValuePermissions
            )
      }
      for {
        standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- Future.sequence(standoffLinkUpdatesFutures)
        // Generate SPARQL INSERT statements based on those SparqlTemplateLinkUpdates.
        sparqlInsert = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .generateInsertStatementsForStandoffLinks(
            resourceIri = createMultipleValuesRequest.resourceIri,
            linkUpdates = standoffLinkUpdates,
            creationDate = createMultipleValuesRequest.creationDate,
            stringFormatter = stringFormatter
          )
          .toString()
      } yield Some(sparqlInsert)
    } else {
      FastFuture.successful(None)
    }
  }

  /**
    * Creates a new version of an existing value.
    *
    * @param updateValueRequest the request to update the value.
    * @return a [[UpdateValueResponseV2]].
    */
  private def updateValueV2(updateValueRequest: UpdateValueRequestV2): Future[UpdateValueResponseV2] = {

    /**
      * Information about a resource, a submitted property, and a value of the property.
      *
      * @param resource                     the contents of the resource.
      * @param submittedInternalPropertyIri the internal IRI of the submitted property.
      * @param adjustedInternalPropertyInfo the internal definition of the submitted property, adjusted
      *                                     as follows: an adjusted version of the submitted property:
      *                                     if it's a link value property, substitute the
      *                                     corresponding link property.
      * @param value                        the requested value.
      */
    case class ResourcePropertyValue(resource: ReadResourceV2,
                                     submittedInternalPropertyIri: SmartIri,
                                     adjustedInternalPropertyInfo: ReadPropertyInfoV2,
                                     value: ReadValueV2)

    /**
      * Gets information about a resource, a submitted property, and a value of the property, and does
      * some checks to see if the submitted information is correct.
      *
      * @param resourceIri                       the IRI of the resource.
      * @param submittedExternalResourceClassIri the submitted external IRI of the resource class.
      * @param submittedExternalPropertyIri      the submitted external IRI of the property.
      * @param valueIri                          the IRI of the value.
      * @param submittedExternalValueType        the submitted external IRI of the value type.
      * @return a [[ResourcePropertyValue]].
      */
    def getResourcePropertyValue(resourceIri: IRI,
                                 submittedExternalResourceClassIri: SmartIri,
                                 submittedExternalPropertyIri: SmartIri,
                                 valueIri: IRI,
                                 submittedExternalValueType: SmartIri): Future[ResourcePropertyValue] = {
      for {
        submittedInternalPropertyIri: SmartIri <- Future(submittedExternalPropertyIri.toOntologySchema(InternalSchema))

        // Get ontology information about the submitted property.

        propertyInfoRequestForSubmittedProperty = PropertiesGetRequestV2(
          propertyIris = Set(submittedInternalPropertyIri),
          allLanguages = false,
          requestingUser = updateValueRequest.requestingUser
        )

        propertyInfoResponseForSubmittedProperty: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForSubmittedProperty)
          .mapTo[ReadOntologyV2]
        propertyInfoForSubmittedProperty: ReadPropertyInfoV2 = propertyInfoResponseForSubmittedProperty.properties(
          submittedInternalPropertyIri)

        // Don't accept link properties.
        _ = if (propertyInfoForSubmittedProperty.isLinkProp) {
          throw BadRequestException(
            s"Invalid property <${propertyInfoForSubmittedProperty.entityInfoContent.propertyIri.toOntologySchema(ApiV2Complex)}>. Use a link value property to submit a link.")
        }

        // Don't accept knora-api:hasStandoffLinkToValue.
        _ = if (submittedExternalPropertyIri.toString == OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue) {
          throw BadRequestException(s"Values of <$submittedExternalPropertyIri> cannot be updated directly")
        }

        // Make an adjusted version of the submitted property: if it's a link value property, substitute the
        // corresponding link property, whose objects we will need to query. Get ontology information about the
        // adjusted property.

        adjustedInternalPropertyInfo: ReadPropertyInfoV2 <- getAdjustedInternalPropertyInfo(
          submittedPropertyIri = submittedExternalPropertyIri,
          maybeSubmittedValueType = Some(submittedExternalValueType),
          propertyInfoForSubmittedProperty = propertyInfoForSubmittedProperty,
          requestingUser = updateValueRequest.requestingUser
        )

        // Get the resource's metadata and relevant property objects, using the adjusted property. Do this as the system user,
        // so we can see objects that the user doesn't have permission to see.

        resourceInfo: ReadResourceV2 <- getResourceWithPropertyValues(
          resourceIri = resourceIri,
          propertyInfo = adjustedInternalPropertyInfo,
          featureFactoryConfig = updateValueRequest.featureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )

        _ = if (resourceInfo.resourceClassIri != submittedExternalResourceClassIri.toOntologySchema(InternalSchema)) {
          throw BadRequestException(
            s"The rdf:type of resource <$resourceIri> is not <$submittedExternalResourceClassIri>")
        }

        // Check that the resource has the value that the user wants to update, as an object of the submitted property.
        currentValue: ReadValueV2 = resourceInfo.values
          .get(submittedInternalPropertyIri)
          .flatMap(_.find(_.valueIri == valueIri))
          .getOrElse {
            throw NotFoundException(
              s"Resource <$resourceIri> does not have value <$valueIri> as an object of property <$submittedExternalPropertyIri>")
          }

        // Check that the current value has the submitted value type.
        _ = if (currentValue.valueContent.valueType != submittedExternalValueType.toOntologySchema(InternalSchema)) {
          throw BadRequestException(
            s"Value <$valueIri> has type <${currentValue.valueContent.valueType.toOntologySchema(ApiV2Complex)}>, but the submitted type was <$submittedExternalValueType>")
        }

        // If a custom value creation date was submitted, make sure it's later than the date of the current version.
        _ = if (updateValueRequest.updateValue.valueCreationDate.exists(!_.isAfter(currentValue.valueCreationDate))) {
          throw BadRequestException("A custom value creation date must be later than the date of the current version")
        }
      } yield
        ResourcePropertyValue(
          resource = resourceInfo,
          submittedInternalPropertyIri = submittedInternalPropertyIri,
          adjustedInternalPropertyInfo = adjustedInternalPropertyInfo,
          value = currentValue
        )
    }

    /**
      * Updates the permissions attached to a value.
      *
      * @param updateValuePermissionsV2 the update request.
      * @return an [[UpdateValueResponseV2]].
      */
    def makeTaskFutureToUpdateValuePermissions(
        updateValuePermissionsV2: UpdateValuePermissionsV2): Future[UpdateValueResponseV2] = {
      for {
        // Do the initial checks, and get information about the resource, the property, and the value.
        resourcePropertyValue: ResourcePropertyValue <- getResourcePropertyValue(
          resourceIri = updateValuePermissionsV2.resourceIri,
          submittedExternalResourceClassIri = updateValuePermissionsV2.resourceClassIri,
          submittedExternalPropertyIri = updateValuePermissionsV2.propertyIri,
          valueIri = updateValuePermissionsV2.valueIri,
          submittedExternalValueType = updateValuePermissionsV2.valueType
        )

        resourceInfo: ReadResourceV2 = resourcePropertyValue.resource
        submittedInternalPropertyIri: SmartIri = resourcePropertyValue.submittedInternalPropertyIri
        currentValue: ReadValueV2 = resourcePropertyValue.value

        // Validate and reformat the submitted permissions.
        newPermissionsParsed = parsePermissions(permissionLiteral = updateValuePermissionsV2.permissions, errorFun = {
          literal =>
            throw BadRequestException(s"Invalid permission literal: $literal")
        })
        _ <- PermissionUtilADM.validatePermissions(
          parsedPermissions = newPermissionsParsed,
          featureFactoryConfig = updateValueRequest.featureFactoryConfig,
          responderManager = responderManager
        )
        newValuePermissionLiteral: String = reformatCustomPermission(newPermissionsParsed)
        // Check that the user has ChangeRightsPermission on the value, and that the new permissions are
        // different from the current ones.

        currentPermissionsParsed: Map[EntityPermission, Set[IRI]] = PermissionUtilADM.parsePermissions(
          currentValue.permissions)

        _ = if (newPermissionsParsed == currentPermissionsParsed) {
          throw BadRequestException(s"The submitted permissions are the same as the current ones")
        }

        _ = ResourceUtilV2.checkValuePermission(
          resourceInfo = resourceInfo,
          valueInfo = currentValue,
          permissionNeeded = ChangeRightsPermission,
          requestingUser = updateValueRequest.requestingUser
        )

        // Do the update.

        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(resourceInfo.projectADM)
        newValueIri: IRI <- checkOrCreateEntityIri(updateValuePermissionsV2.newValueVersionIri,
                                                   stringFormatter.makeRandomValueIri(resourceInfo.resourceIri))

        currentTime: Instant = updateValuePermissionsV2.valueCreationDate.getOrElse(Instant.now)

        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .changeValuePermissions(
            dataNamedGraph = dataNamedGraph,
            triplestore = settings.triplestoreType,
            resourceIri = resourceInfo.resourceIri,
            propertyIri = submittedInternalPropertyIri,
            currentValueIri = currentValue.valueIri,
            valueTypeIri = currentValue.valueContent.valueType,
            newValueIri = newValueIri,
            newPermissions = newValuePermissionLiteral,
            currentTime = currentTime
          )
          .toString()

        _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        // Check that the value was written correctly to the triplestore.

        unverifiedValue = UnverifiedValueV2(
          newValueIri = newValueIri,
          newValueUUID = currentValue.valueHasUUID,
          valueContent = currentValue.valueContent,
          permissions = newValuePermissionLiteral,
          creationDate = currentTime
        )

        verifiedValue: VerifiedValueV2 <- verifyValue(
          resourceIri = resourceInfo.resourceIri,
          propertyIri = submittedInternalPropertyIri,
          unverifiedValue = unverifiedValue,
          featureFactoryConfig = updateValueRequest.featureFactoryConfig,
          requestingUser = updateValueRequest.requestingUser
        )
      } yield
        UpdateValueResponseV2(
          valueIri = verifiedValue.newValueIri,
          valueType = unverifiedValue.valueContent.valueType,
          valueUUID = currentValue.valueHasUUID,
          projectADM = resourceInfo.projectADM
        )
    }

    /**
      * Updates the contents of a value.
      *
      * @param updateValueContentV2 the update request.
      * @return an [[UpdateValueResponseV2]].
      */
    def makeTaskFutureToUpdateValueContent(
        updateValueContentV2: UpdateValueContentV2): Future[UpdateValueResponseV2] = {
      for {
        // Do the initial checks, and get information about the resource, the property, and the value.
        resourcePropertyValue: ResourcePropertyValue <- getResourcePropertyValue(
          resourceIri = updateValueContentV2.resourceIri,
          submittedExternalResourceClassIri = updateValueContentV2.resourceClassIri,
          submittedExternalPropertyIri = updateValueContentV2.propertyIri,
          valueIri = updateValueContentV2.valueIri,
          submittedExternalValueType = updateValueContentV2.valueContent.valueType
        )

        resourceInfo: ReadResourceV2 = resourcePropertyValue.resource
        submittedInternalPropertyIri: SmartIri = resourcePropertyValue.submittedInternalPropertyIri
        adjustedInternalPropertyInfo: ReadPropertyInfoV2 = resourcePropertyValue.adjustedInternalPropertyInfo
        currentValue: ReadValueV2 = resourcePropertyValue.value

        // Did the user submit permissions for the new value?
        newValueVersionPermissionLiteral <- updateValueContentV2.permissions match {
          case Some(permissions) =>
            // Yes. Validate them.
            for {
              parsedPermission <- Future.successful(parsePermissions(permissionLiteral = permissions, errorFun = {
                literal =>
                  throw BadRequestException(s"Invalid permission literal: $literal")
              }))
              _ <- PermissionUtilADM.validatePermissions(
                parsedPermissions = parsedPermission,
                featureFactoryConfig = updateValueRequest.featureFactoryConfig,
                responderManager = responderManager
              )
            } yield reformatCustomPermission(parsedPermission)
          case None =>
            // No. Use the permissions on the current version of the value.
            FastFuture.successful(currentValue.permissions)
        }

        // Check that the user has permission to do the update. If they want to change the permissions
        // on the value, they need ChangeRightsPermission, otherwise they need ModifyPermission.

        currentPermissionsParsed: Map[EntityPermission, Set[IRI]] = PermissionUtilADM.parsePermissions(
          currentValue.permissions)
        newPermissionsParsed: Map[EntityPermission, Set[IRI]] = PermissionUtilADM.parsePermissions(
          newValueVersionPermissionLiteral, { permissionLiteral: String =>
            throw AssertionException(s"Invalid permission literal: $permissionLiteral")
          })

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

        // Convert the submitted value content to the internal schema.
        submittedInternalValueContent: ValueContentV2 = updateValueContentV2.valueContent.toOntologySchema(
          InternalSchema)

        // Check that the object of the adjusted property (the value to be created, or the target of the link to be created) will have
        // the correct type for the adjusted property's knora-base:objectClassConstraint.

        _ <- checkPropertyObjectClassConstraint(
          propertyInfo = adjustedInternalPropertyInfo,
          valueContent = submittedInternalValueContent,
          featureFactoryConfig = updateValueRequest.featureFactoryConfig,
          requestingUser = updateValueRequest.requestingUser
        )

        // If this is a list value, check that it points to a real list node.

        _ <- submittedInternalValueContent match {
          case listValue: HierarchicalListValueContentV2 =>
            ResourceUtilV2.checkListNodeExists(listValue.valueHasListNode, storeManager)
          case _ => FastFuture.successful(())
        }

        // Check that the updated value would not duplicate the current value version.

        unescapedSubmittedInternalValueContent = submittedInternalValueContent.unescape

        _ = if (unescapedSubmittedInternalValueContent.wouldDuplicateCurrentVersion(currentValue.valueContent)) {
          throw DuplicateValueException("The submitted value is the same as the current version")
        }

        // Check that the updated value would not duplicate another existing value of the resource.

        currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values
          .getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])
          .filter(_.valueIri != updateValueContentV2.valueIri)

        _ = if (currentValuesForProp.exists(currentVal =>
                  unescapedSubmittedInternalValueContent.wouldDuplicateOtherValue(currentVal.valueContent))) {
          throw DuplicateValueException()
        }

        _ <- submittedInternalValueContent match {
          case textValueContent: TextValueContentV2 =>
            // This is a text value. Check that the resources pointed to by any standoff link tags exist
            // and that the user has permission to see them.
            checkResourceIris(
              textValueContent.standoffLinkTagTargetResourceIris,
              featureFactoryConfig = updateValueRequest.featureFactoryConfig,
              updateValueRequest.requestingUser
            )

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

        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(resourceInfo.projectADM)

        // Create the new value version.

        unverifiedValue: UnverifiedValueV2 <- (currentValue, submittedInternalValueContent) match {
          case (currentLinkValue: ReadLinkValueV2, newLinkValue: LinkValueContentV2) =>
            updateLinkValueV2AfterChecks(
              dataNamedGraph = dataNamedGraph,
              resourceInfo = resourceInfo,
              linkPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              currentLinkValue = currentLinkValue,
              newLinkValue = newLinkValue,
              valueCreator = updateValueRequest.requestingUser.id,
              valuePermissions = newValueVersionPermissionLiteral,
              valueCreationDate = updateValueContentV2.valueCreationDate,
              newValueVersionIri = updateValueContentV2.newValueVersionIri,
              requestingUser = updateValueRequest.requestingUser
            )

          case _ =>
            updateOrdinaryValueV2AfterChecks(
              dataNamedGraph = dataNamedGraph,
              resourceInfo = resourceInfo,
              propertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              currentValue = currentValue,
              newValueVersion = submittedInternalValueContent,
              valueCreator = updateValueRequest.requestingUser.id,
              valuePermissions = newValueVersionPermissionLiteral,
              valueCreationDate = updateValueContentV2.valueCreationDate,
              newValueVersionIri = updateValueContentV2.newValueVersionIri,
              requestingUser = updateValueRequest.requestingUser
            )
        }

        // Check that the value was written correctly to the triplestore.

        verifiedValue: VerifiedValueV2 <- verifyValue(
          resourceIri = updateValueContentV2.resourceIri,
          propertyIri = submittedInternalPropertyIri,
          unverifiedValue = unverifiedValue,
          featureFactoryConfig = updateValueRequest.featureFactoryConfig,
          requestingUser = updateValueRequest.requestingUser
        )
      } yield
        UpdateValueResponseV2(
          valueIri = verifiedValue.newValueIri,
          valueType = unverifiedValue.valueContent.valueType,
          valueUUID = unverifiedValue.newValueUUID,
          projectADM = resourceInfo.projectADM
        )
    }

    if (updateValueRequest.requestingUser.isAnonymousUser) {
      FastFuture.failed(ForbiddenException("Anonymous users aren't allowed to update values"))
    } else {
      updateValueRequest.updateValue match {
        case updateValueContentV2: UpdateValueContentV2 =>
          // This is a request to update the content of a value.
          val triplestoreUpdateFuture: Future[UpdateValueResponseV2] = IriLocker.runWithIriLock(
            updateValueRequest.apiRequestID,
            updateValueContentV2.resourceIri,
            () => makeTaskFutureToUpdateValueContent(updateValueContentV2)
          )

          ResourceUtilV2.doSipiPostUpdate(
            updateFuture = triplestoreUpdateFuture,
            valueContent = updateValueContentV2.valueContent,
            requestingUser = updateValueRequest.requestingUser,
            responderManager = responderManager,
            storeManager = storeManager,
            log = log
          )

        case updateValuePermissionsV2: UpdateValuePermissionsV2 =>
          // This is a request to update the permissions attached to a value.
          IriLocker.runWithIriLock(
            updateValueRequest.apiRequestID,
            updateValuePermissionsV2.resourceIri,
            () => makeTaskFutureToUpdateValuePermissions(updateValuePermissionsV2)
          )
      }
    }
  }

  /**
    * Changes an ordinary value (i.e. not a link), assuming that pre-update checks have already been done.
    *
    * @param dataNamedGraph     the IRI of the named graph to be updated.
    * @param resourceInfo       information about the resource containing the value.
    * @param propertyIri        the IRI of the property that points to the value.
    * @param currentValue       a [[ReadValueV2]] representing the existing value version.
    * @param newValueVersion    a [[ValueContentV2]] representing the new value version, in the internal schema.
    * @param valueCreator       the IRI of the new value's owner.
    * @param valuePermissions   the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
    * @param valueCreationDate  a custom value creation date.
    * @param newValueVersionIri an optional IRI to be used for the new value version.
    * @param requestingUser     the user making the request.
    * @return an [[UnverifiedValueV2]].
    */
  private def updateOrdinaryValueV2AfterChecks(dataNamedGraph: IRI,
                                               resourceInfo: ReadResourceV2,
                                               propertyIri: SmartIri,
                                               currentValue: ReadValueV2,
                                               newValueVersion: ValueContentV2,
                                               valueCreator: IRI,
                                               valuePermissions: String,
                                               valueCreationDate: Option[Instant],
                                               newValueVersionIri: Option[SmartIri],
                                               requestingUser: UserADM): Future[UnverifiedValueV2] = {
    for {
      newValueIri: IRI <- checkOrCreateEntityIri(newValueVersionIri,
                                                 stringFormatter.makeRandomValueIri(resourceInfo.resourceIri))

      // If we're updating a text value, update direct links and LinkValues for any resource references in Standoff.
      standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- (currentValue.valueContent, newValueVersion) match {
        case (currentTextValue: TextValueContentV2, newTextValue: TextValueContentV2) =>
          // Identify the resource references that have been added or removed in the new version of
          // the value.
          val addedResourceRefs = newTextValue.standoffLinkTagTargetResourceIris -- currentTextValue.standoffLinkTagTargetResourceIris
          val removedResourceRefs = currentTextValue.standoffLinkTagTargetResourceIris -- newTextValue.standoffLinkTagTargetResourceIris

          // Construct a SparqlTemplateLinkUpdate for each reference that was added.
          val standoffLinkUpdatesForAddedResourceRefFutures: Seq[Future[SparqlTemplateLinkUpdate]] =
            addedResourceRefs.toVector.map { targetResourceIri =>
              incrementLinkValue(
                sourceResourceInfo = resourceInfo,
                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                targetResourceIri = targetResourceIri,
                valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
                valuePermissions = standoffLinkValuePermissions,
                requestingUser = requestingUser
              )
            }

          val standoffLinkUpdatesForAddedResourceRefsFuture: Future[Seq[SparqlTemplateLinkUpdate]] =
            Future.sequence(standoffLinkUpdatesForAddedResourceRefFutures)

          // Construct a SparqlTemplateLinkUpdate for each reference that was removed.
          val standoffLinkUpdatesForRemovedResourceRefFutures: Seq[Future[SparqlTemplateLinkUpdate]] =
            removedResourceRefs.toVector.map { removedTargetResource =>
              decrementLinkValue(
                sourceResourceInfo = resourceInfo,
                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                targetResourceIri = removedTargetResource,
                valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
                valuePermissions = standoffLinkValuePermissions,
                requestingUser = requestingUser
              )
            }

          val standoffLinkUpdatesForRemovedResourceRefFuture =
            Future.sequence(standoffLinkUpdatesForRemovedResourceRefFutures)

          for {
            standoffLinkUpdatesForAddedResourceRefs <- standoffLinkUpdatesForAddedResourceRefsFuture
            standoffLinkUpdatesForRemovedResourceRefs <- standoffLinkUpdatesForRemovedResourceRefFuture
          } yield standoffLinkUpdatesForAddedResourceRefs ++ standoffLinkUpdatesForRemovedResourceRefs

        case _ => FastFuture.successful(Vector.empty[SparqlTemplateLinkUpdate])
      }

      // If no custom value creation date was provided, make a timestamp to indicate when the value
      // was updated.
      currentTime: Instant = valueCreationDate.getOrElse(Instant.now)

      // Generate a SPARQL update.
      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .addValueVersion(
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
          currentTime = currentTime,
          requestingUser = requestingUser.id,
          stringFormatter = stringFormatter
        )
        .toString()

      /*
            _ = println("================ Update value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
       */

      // Do the update.
      _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

    } yield
      UnverifiedValueV2(
        newValueIri = newValueIri,
        newValueUUID = currentValue.valueHasUUID,
        valueContent = newValueVersion.unescape,
        permissions = valuePermissions,
        creationDate = currentTime
      )
  }

  /**
    * Changes a link, assuming that pre-update checks have already been done.
    *
    * @param dataNamedGraph     the IRI of the named graph to be updated.
    * @param resourceInfo       information about the resource containing the link.
    * @param linkPropertyIri    the IRI of the link property.
    * @param currentLinkValue   a [[ReadLinkValueV2]] representing the `knora-base:LinkValue` for the existing link.
    * @param newLinkValue       a [[LinkValueContentV2]] indicating the new target resource.
    * @param valueCreator       the IRI of the new link value's owner.
    * @param valuePermissions   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param valueCreationDate  a custom value creation date.
    * @param newValueVersionIri an optional IRI to be used for the new value version.
    * @param requestingUser     the user making the request.
    * @return an [[UnverifiedValueV2]].
    */
  private def updateLinkValueV2AfterChecks(dataNamedGraph: IRI,
                                           resourceInfo: ReadResourceV2,
                                           linkPropertyIri: SmartIri,
                                           currentLinkValue: ReadLinkValueV2,
                                           newLinkValue: LinkValueContentV2,
                                           valueCreator: IRI,
                                           valuePermissions: String,
                                           valueCreationDate: Option[Instant],
                                           newValueVersionIri: Option[SmartIri],
                                           requestingUser: UserADM): Future[UnverifiedValueV2] = {

    // Are we changing the link target?
    if (currentLinkValue.valueContent.referredResourceIri != newLinkValue.referredResourceIri) {
      for {
        // Yes. Delete the existing link and decrement its LinkValue's reference count.
        sparqlTemplateLinkUpdateForCurrentLink: SparqlTemplateLinkUpdate <- decrementLinkValue(
          sourceResourceInfo = resourceInfo,
          linkPropertyIri = linkPropertyIri,
          targetResourceIri = currentLinkValue.valueContent.referredResourceIri,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          requestingUser = requestingUser
        )

        // Create a new link, and create a new LinkValue for it.
        sparqlTemplateLinkUpdateForNewLink: SparqlTemplateLinkUpdate <- incrementLinkValue(
          sourceResourceInfo = resourceInfo,
          linkPropertyIri = linkPropertyIri,
          targetResourceIri = newLinkValue.referredResourceIri,
          customNewLinkValueIri = newValueVersionIri,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          requestingUser = requestingUser
        )

        // If no custom value creation date was provided, make a timestamp to indicate when the link value
        // was updated.
        currentTime: Instant = valueCreationDate.getOrElse(Instant.now)

        // Make a new UUID for the new link value.
        newLinkValueUUID = UUID.randomUUID

        // Generate a SPARQL update string.
        sparqlUpdate <- Future(
          org.knora.webapi.messages.twirl.queries.sparql.v2.txt
            .changeLinkTarget(
              dataNamedGraph = dataNamedGraph,
              triplestore = settings.triplestoreType,
              linkSourceIri = resourceInfo.resourceIri,
              linkUpdateForCurrentLink = sparqlTemplateLinkUpdateForCurrentLink,
              linkUpdateForNewLink = sparqlTemplateLinkUpdateForNewLink,
              newLinkValueUUID = newLinkValueUUID,
              maybeComment = newLinkValue.comment,
              currentTime = currentTime,
              requestingUser = requestingUser.id,
              stringFormatter = stringFormatter
            )
            .toString())

        /*
                _ = println("================ Update link ================")
                _ = println(sparqlUpdate)
                _ = println("==============================================")
         */

        _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
      } yield
        UnverifiedValueV2(
          newValueIri = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
          newValueUUID = newLinkValueUUID,
          valueContent = newLinkValue.unescape,
          permissions = valuePermissions,
          creationDate = currentTime
        )
    } else {
      for {
        // We're not changing the link target, just the metadata on the LinkValue.
        sparqlTemplateLinkUpdate: SparqlTemplateLinkUpdate <- changeLinkValueMetadata(
          sourceResourceInfo = resourceInfo,
          linkPropertyIri = linkPropertyIri,
          targetResourceIri = currentLinkValue.valueContent.referredResourceIri,
          customNewLinkValueIri = newValueVersionIri,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          requestingUser = requestingUser
        )

        // Make a timestamp to indicate when the link value was updated.
        currentTime: Instant = Instant.now

        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .changeLinkMetadata(
            dataNamedGraph = dataNamedGraph,
            triplestore = settings.triplestoreType,
            linkSourceIri = resourceInfo.resourceIri,
            linkUpdate = sparqlTemplateLinkUpdate,
            maybeComment = newLinkValue.comment,
            currentTime = currentTime,
            requestingUser = requestingUser.id
          )
          .toString()

        _ <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
      } yield
        UnverifiedValueV2(
          newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
          newValueUUID = currentLinkValue.valueHasUUID,
          valueContent = newLinkValue.unescape,
          permissions = valuePermissions,
          creationDate = currentTime
        )
    }
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
        submittedInternalPropertyIri: SmartIri <- Future(
          deleteValueRequest.propertyIri.toOntologySchema(InternalSchema))

        // Get ontology information about the submitted property.

        propertyInfoRequestForSubmittedProperty = PropertiesGetRequestV2(
          propertyIris = Set(submittedInternalPropertyIri),
          allLanguages = false,
          requestingUser = deleteValueRequest.requestingUser
        )

        propertyInfoResponseForSubmittedProperty: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForSubmittedProperty)
          .mapTo[ReadOntologyV2]
        propertyInfoForSubmittedProperty: ReadPropertyInfoV2 = propertyInfoResponseForSubmittedProperty.properties(
          submittedInternalPropertyIri)

        // Don't accept link properties.
        _ = if (propertyInfoForSubmittedProperty.isLinkProp) {
          throw BadRequestException(
            s"Invalid property <${propertyInfoForSubmittedProperty.entityInfoContent.propertyIri.toOntologySchema(ApiV2Complex)}>. Use a link value property to submit a link.")
        }

        // Don't accept knora-api:hasStandoffLinkToValue.
        _ = if (deleteValueRequest.propertyIri.toString == OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue) {
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
          featureFactoryConfig = deleteValueRequest.featureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )

        // Check that the resource belongs to the class that the client submitted.

        _ = if (resourceInfo.resourceClassIri != deleteValueRequest.resourceClassIri.toOntologySchema(InternalSchema)) {
          throw BadRequestException(
            s"Resource <${deleteValueRequest.resourceIri}> does not belong to class <${deleteValueRequest.resourceClassIri}>")
        }

        // Check that the resource has the value that the user wants to delete, as an object of the submitted property.

        maybeCurrentValue: Option[ReadValueV2] = resourceInfo.values
          .get(submittedInternalPropertyIri)
          .flatMap(_.find(_.valueIri == deleteValueRequest.valueIri))

        // Check that the user has permission to delete the value.

        currentValue: ReadValueV2 = maybeCurrentValue match {
          case Some(value) => value
          case None =>
            throw NotFoundException(
              s"Resource <${deleteValueRequest.resourceIri}> does not have value <${deleteValueRequest.valueIri}> as an object of property <${deleteValueRequest.propertyIri}>")
        }

        // Check that the value is of the type that the client submitted.

        _ = if (currentValue.valueContent.valueType != deleteValueRequest.valueTypeIri.toOntologySchema(InternalSchema)) {
          throw BadRequestException(
            s"Value <${deleteValueRequest.valueIri}> in resource <${deleteValueRequest.resourceIri}> is not of type <${deleteValueRequest.valueTypeIri}>")
        }

        // Check the user's permissions on the value.

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
        cardinalityInfo: Cardinality.KnoraCardinalityInfo = classInfo.allCardinalities.getOrElse(
          submittedInternalPropertyIri,
          throw InconsistentRepositoryDataException(
            s"Resource <${deleteValueRequest.resourceIri}> belongs to class <${resourceInfo.resourceClassIri.toOntologySchema(
              ApiV2Complex)}>, which has no cardinality for property <${deleteValueRequest.propertyIri}>")
        )

        // Check that the resource class's cardinality for the submitted property allows this value to be deleted.

        currentValuesForProp: Seq[ReadValueV2] = resourceInfo.values
          .getOrElse(submittedInternalPropertyIri, Seq.empty[ReadValueV2])

        _ = if ((cardinalityInfo.cardinality == Cardinality.MustHaveOne || cardinalityInfo.cardinality == Cardinality.MustHaveSome) && currentValuesForProp.size == 1) {
          throw OntologyConstraintException(s"Resource class <${resourceInfo.resourceClassIri.toOntologySchema(
            ApiV2Complex)}> has a cardinality of ${cardinalityInfo.cardinality} on property <${deleteValueRequest.propertyIri}>, and this does not allow a value to be deleted for that property from resource <${deleteValueRequest.resourceIri}>")
        }

        // If a custom delete date was submitted, make sure it's later than the date of the current version.
        _ = if (deleteValueRequest.deleteDate.exists(!_.isAfter(currentValue.valueCreationDate))) {
          throw BadRequestException("A custom delete date must be later than the value's creation date")
        }

        // Get information about the project that the resource is in, so we know which named graph to do the update in.

        dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(resourceInfo.projectADM)

        // Do the update.
        deletedValueIri: IRI <- deleteValueV2AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceInfo = resourceInfo,
          propertyIri = adjustedInternalPropertyIri,
          deleteComment = deleteValueRequest.deleteComment,
          deleteDate = deleteValueRequest.deleteDate,
          currentValue = currentValue,
          requestingUser = deleteValueRequest.requestingUser
        )

        // Check whether the update succeeded.
        sparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .checkValueDeletion(
            triplestore = settings.triplestoreType,
            valueIri = deletedValueIri
          )
          .toString()

        sparqlSelectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
        rows = sparqlSelectResponse.results.bindings

        _ = if (rows.isEmpty || !stringFormatter.optionStringToBoolean(
                  rows.head.rowMap.get("isDeleted"),
                  throw InconsistentRepositoryDataException(
                    s"Invalid boolean for isDeleted: ${rows.head.rowMap.get("isDeleted")}"))) {
          throw UpdateNotPerformedException(
            s"The request to mark value <${deleteValueRequest.valueIri}> (or a new version of that value) as deleted did not succeed. Please report this as a possible bug.")
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
    * @param deleteDate     an optional timestamp indicating when the value was deleted.
    * @param requestingUser the user making the request.
    * @return the IRI of the value that was marked as deleted.
    */
  private def deleteValueV2AfterChecks(dataNamedGraph: IRI,
                                       resourceInfo: ReadResourceV2,
                                       propertyIri: SmartIri,
                                       deleteComment: Option[String],
                                       deleteDate: Option[Instant],
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
          deleteDate = deleteDate,
          requestingUser = requestingUser
        )

      case _ =>
        deleteOrdinaryValueV2AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceInfo = resourceInfo,
          propertyIri = propertyIri,
          currentValue = currentValue,
          deleteComment = deleteComment,
          deleteDate = deleteDate,
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
    * @param deleteDate     an optional timestamp indicating when the value was deleted.
    * @param requestingUser the user making the request.
    * @return the IRI of the value that was marked as deleted.
    */
  private def deleteLinkValueV2AfterChecks(dataNamedGraph: IRI,
                                           resourceInfo: ReadResourceV2,
                                           propertyIri: SmartIri,
                                           currentValue: ReadValueV2,
                                           deleteComment: Option[String],
                                           deleteDate: Option[Instant],
                                           requestingUser: UserADM): Future[IRI] = {
    // Make a new version of of the LinkValue with a reference count of 0, and mark the new
    // version as deleted. Give the new version the same permissions as the previous version.

    val currentLinkValueContent: LinkValueContentV2 = currentValue.valueContent match {
      case linkValueContent: LinkValueContentV2 => linkValueContent
      case _                                    => throw AssertionException("Unreachable code")
    }

    // If no custom delete date was provided, make a timestamp to indicate when the link value was
    // marked as deleted.
    val currentTime: Instant = deleteDate.getOrElse(Instant.now)

    for {
      // Delete the existing link and decrement its LinkValue's reference count.
      sparqlTemplateLinkUpdate <- decrementLinkValue(
        sourceResourceInfo = resourceInfo,
        linkPropertyIri = propertyIri,
        targetResourceIri = currentLinkValueContent.referredResourceIri,
        valueCreator = currentValue.attachedToUser,
        valuePermissions = currentValue.permissions,
        requestingUser = requestingUser
      )

      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .deleteLink(
          dataNamedGraph = dataNamedGraph,
          triplestore = settings.triplestoreType,
          linkSourceIri = resourceInfo.resourceIri,
          linkUpdate = sparqlTemplateLinkUpdate,
          maybeComment = deleteComment,
          currentTime = currentTime,
          requestingUser = requestingUser.id
        )
        .toString()

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
    * @param deleteDate     an optional timestamp indicating when the value was deleted.
    * @param requestingUser the user making the request.
    * @return the IRI of the value that was marked as deleted.
    */
  private def deleteOrdinaryValueV2AfterChecks(dataNamedGraph: IRI,
                                               resourceInfo: ReadResourceV2,
                                               propertyIri: SmartIri,
                                               currentValue: ReadValueV2,
                                               deleteComment: Option[String],
                                               deleteDate: Option[Instant],
                                               requestingUser: UserADM): Future[IRI] = {
    // Mark the existing version of the value as deleted.

    // If it's a TextValue, make SparqlTemplateLinkUpdates for updating LinkValues representing
    // links in standoff markup.
    val linkUpdateFutures: Seq[Future[SparqlTemplateLinkUpdate]] = currentValue.valueContent match {
      case textValue: TextValueContentV2 =>
        textValue.standoffLinkTagTargetResourceIris.toVector.map { removedTargetResource =>
          decrementLinkValue(
            sourceResourceInfo = resourceInfo,
            linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
            targetResourceIri = removedTargetResource,
            valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
            valuePermissions = standoffLinkValuePermissions,
            requestingUser = requestingUser
          )
        }

      case _ => Seq.empty[Future[SparqlTemplateLinkUpdate]]
    }

    val linkUpdateFuture = Future.sequence(linkUpdateFutures)

    // If no custom delete date was provided, make a timestamp to indicate when the value was
    // marked as deleted.
    val currentTime: Instant = deleteDate.getOrElse(Instant.now)

    for {
      linkUpdates: Seq[SparqlTemplateLinkUpdate] <- linkUpdateFuture

      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .deleteValue(
          dataNamedGraph = dataNamedGraph,
          triplestore = settings.triplestoreType,
          resourceIri = resourceInfo.resourceIri,
          propertyIri = propertyIri,
          valueIri = currentValue.valueIri,
          maybeDeleteComment = deleteComment,
          linkUpdates = linkUpdates,
          currentTime = currentTime,
          requestingUser = requestingUser.id,
          stringFormatter = stringFormatter
        )
        .toString()

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
          if (submittedValueType.toString != OntologyConstants.KnoraApiV2Complex.LinkValue) {
            FastFuture.failed(
              BadRequestException(
                s"A value of type <$submittedValueType> cannot be an object of property <$submittedPropertyIri>"))
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

        linkPropertyInfoResponse: ReadOntologyV2 <- (responderManager ? propertyInfoRequestForLinkProperty)
          .mapTo[ReadOntologyV2]
      } yield linkPropertyInfoResponse.properties(internalLinkPropertyIri)
    } else if (propertyInfoForSubmittedProperty.isLinkProp) {
      throw BadRequestException(
        s"Invalid property for creating a link value (submit a link value property instead): $submittedPropertyIri")
    } else {
      FastFuture.successful(propertyInfoForSubmittedProperty)
    }
  }

  /**
    * Given a set of resource IRIs, checks that they point to Knora resources.
    * If not, throws an exception.
    *
    * @param targetResourceIris   the IRIs to be checked.
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the user making the request.
    */
  private def checkResourceIris(targetResourceIris: Set[IRI],
                                featureFactoryConfig: FeatureFactoryConfig,
                                requestingUser: UserADM): Future[Unit] = {
    if (targetResourceIris.isEmpty) {
      FastFuture.successful(())
    } else {
      for {
        resourcePreviewRequest <- FastFuture.successful(
          ResourcesPreviewGetRequestV2(
            resourceIris = targetResourceIris.toSeq,
            targetSchema = ApiV2Complex,
            featureFactoryConfig = featureFactoryConfig,
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
    * @param resourceIri          the resource IRI.
    * @param propertyInfo         the property definition (in the internal schema). If the caller wants to query a link, this must be the link property,
    *                             not the link value property.
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the user making the request.
    * @return a [[ReadResourceV2]] containing only the resource's metadata and its values for the specified property.
    */
  private def getResourceWithPropertyValues(resourceIri: IRI,
                                            propertyInfo: ReadPropertyInfoV2,
                                            featureFactoryConfig: FeatureFactoryConfig,
                                            requestingUser: UserADM): Future[ReadResourceV2] = {
    for {
      // Get the property's object class constraint.
      objectClassConstraint: SmartIri <- Future(
        propertyInfo.entityInfoContent.requireIriObject(
          OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
          throw InconsistentRepositoryDataException(
            s"Property ${propertyInfo.entityInfoContent.propertyIri} has no knora-base:objectClassConstraint")
        ))

      // If the property points to a text value, also query the resource's standoff links.
      maybeStandoffLinkToPropertyIri: Option[SmartIri] = if (objectClassConstraint.toString == OntologyConstants.KnoraBase.TextValue) {
        Some(OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri)
      } else {
        None
      }

      // Convert the property IRIs to be queried to the API v2 complex schema for Gravsearch.
      propertyIrisForGravsearchQuery: Seq[SmartIri] = (Seq(propertyInfo.entityInfoContent.propertyIri) ++ maybeStandoffLinkToPropertyIri)
        .map(_.toOntologySchema(ApiV2Complex))

      // Make a Gravsearch query from a template.
      gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
        .getResourceWithSpecifiedProperties(
          resourceIri = resourceIri,
          propertyIris = propertyIrisForGravsearchQuery
        )
        .toString()

      // Run the query.

      parsedGravsearchQuery <- FastFuture.successful(GravsearchParser.parseQuery(gravsearchQuery))
      searchResponse <- (responderManager ? GravsearchRequestV2(
        constructQuery = parsedGravsearchQuery,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )).mapTo[ReadResourcesSequenceV2]
    } yield searchResponse.toResource(resourceIri)
  }

  /**
    * Verifies that a value was written correctly to the triplestore.
    *
    * @param resourceIri          the IRI of the resource that the value belongs to.
    * @param propertyIri          the internal IRI of the property that points to the value. If the value is a link value,
    *                             this is the link value property.
    * @param unverifiedValue      the value that should have been written to the triplestore.
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the user making the request.
    */
  private def verifyValue(resourceIri: IRI,
                          propertyIri: SmartIri,
                          unverifiedValue: UnverifiedValueV2,
                          featureFactoryConfig: FeatureFactoryConfig,
                          requestingUser: UserADM): Future[VerifiedValueV2] = {
    val verifiedValueFuture: Future[VerifiedValueV2] = for {
      resourcesRequest <- Future {
        ResourcesGetRequestV2(
          resourceIris = Seq(resourceIri),
          propertyIri = Some(propertyIri),
          versionDate = Some(unverifiedValue.creationDate),
          targetSchema = ApiV2Complex,
          schemaOptions = SchemaOptions.ForStandoffWithTextValues,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )
      }

      resourcesResponse <- (responderManager ? resourcesRequest).mapTo[ReadResourcesSequenceV2]
      resource = resourcesResponse.toResource(resourceIri)

      propertyValues = resource.values.getOrElse(propertyIri, throw UpdateNotPerformedException())
      valueInTriplestore: ReadValueV2 = propertyValues
        .find(_.valueIri == unverifiedValue.newValueIri)
        .getOrElse(throw UpdateNotPerformedException())

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
        throw AssertionException(
          s"The value saved as ${unverifiedValue.newValueIri} is not the same as the one that was submitted")
      }
    } yield
      VerifiedValueV2(
        newValueIri = unverifiedValue.newValueIri,
        value = unverifiedValue.valueContent,
        permissions = unverifiedValue.permissions
      )

    verifiedValueFuture.recover {
      case _: NotFoundException =>
        throw UpdateNotPerformedException(
          s"Resource <$resourceIri> was not found. Please report this as a possible bug.")
    }
  }

  /**
    * Checks that a link value points to a resource with the correct type for the link property's object class constraint.
    *
    * @param linkPropertyIri       the IRI of the link property.
    * @param objectClassConstraint the object class constraint of the link property.
    * @param linkValueContent      the link value.
    * @param featureFactoryConfig  the feature factory configuration.
    * @param requestingUser        the user making the request.
    */
  private def checkLinkPropertyObjectClassConstraint(linkPropertyIri: SmartIri,
                                                     objectClassConstraint: SmartIri,
                                                     linkValueContent: LinkValueContentV2,
                                                     featureFactoryConfig: FeatureFactoryConfig,
                                                     requestingUser: UserADM): Future[Unit] = {
    for {
      // Get a preview of the target resource, because we only need to find out its class and whether the user has permission to view it.
      resourcePreviewRequest <- FastFuture.successful(
        ResourcesPreviewGetRequestV2(
          resourceIris = Seq(linkValueContent.referredResourceIri),
          targetSchema = ApiV2Complex,
          featureFactoryConfig = featureFactoryConfig,
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
        throw OntologyConstraintException(
          s"Resource <${linkValueContent.referredResourceIri}> cannot be the target of property <$linkPropertyIri>, because it is not a member of class <$objectClassConstraint>")
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
  private def checkNonLinkPropertyObjectClassConstraint(propertyIri: SmartIri,
                                                        objectClassConstraint: SmartIri,
                                                        valueContent: ValueContentV2,
                                                        requestingUser: UserADM): Future[Unit] = {
    // Is the value type the same as the property's object class constraint?
    if (objectClassConstraint == valueContent.valueType) {
      // Yes. Nothing more to do here.
      Future.successful(())
    } else {
      // No. Ask the ontology responder whether it's a subclass of the property's object class constraint.
      for {
        subClassRequest <- FastFuture.successful(
          CheckSubClassRequestV2(
            subClassIri = valueContent.valueType,
            superClassIri = objectClassConstraint,
            requestingUser = requestingUser
          ))

        subClassResponse <- (responderManager ? subClassRequest).mapTo[CheckSubClassResponseV2]

        // If it isn't, throw an exception.
        _ = if (!subClassResponse.isSubClass) {
          throw OntologyConstraintException(
            s"A value of type <${valueContent.valueType}> cannot be the target of property <$propertyIri>, because it is not a member of class <$objectClassConstraint>")
        }

      } yield ()
    }
  }

  /**
    * Checks that a value to be updated has the correct type for the `knora-base:objectClassConstraint` of
    * the property that is supposed to point to it.
    *
    * @param propertyInfo         the property whose object class constraint is to be checked. If the value is a link value, this is the link property.
    * @param valueContent         the value to be updated.
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the user making the request.
    */
  private def checkPropertyObjectClassConstraint(propertyInfo: ReadPropertyInfoV2,
                                                 valueContent: ValueContentV2,
                                                 featureFactoryConfig: FeatureFactoryConfig,
                                                 requestingUser: UserADM): Future[Unit] = {
    for {
      objectClassConstraint: SmartIri <- Future(
        propertyInfo.entityInfoContent.requireIriObject(
          OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
          throw InconsistentRepositoryDataException(
            s"Property ${propertyInfo.entityInfoContent.propertyIri} has no knora-base:objectClassConstraint")
        ))

      result: Unit <- valueContent match {
        case linkValueContent: LinkValueContentV2 =>
          // We're creating a link.

          // Check that the property whose object class constraint is to be checked is actually a link property.
          if (!propertyInfo.isLinkProp) {
            throw BadRequestException(
              s"Property <${propertyInfo.entityInfoContent.propertyIri.toOntologySchema(ApiV2Complex)}> is not a link property")
          }

          // Check that the user has permission to view the target resource, and that the target resource has the correct type.
          checkLinkPropertyObjectClassConstraint(
            linkPropertyIri = propertyInfo.entityInfoContent.propertyIri,
            objectClassConstraint = objectClassConstraint,
            linkValueContent = linkValueContent,
            featureFactoryConfig = featureFactoryConfig,
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

    sourceResourceInfo.values.get(linkValueProperty).flatMap { linkValueInfos: Seq[ReadValueV2] =>
      linkValueInfos.collectFirst {
        case linkValueInfo: ReadLinkValueV2 if linkValueInfo.valueContent.referredResourceIri == targetResourceIri =>
          linkValueInfo
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
    * @param sourceResourceInfo    information about the source resource.
    * @param linkPropertyIri       the IRI of the property that links the source resource to the target resource.
    * @param targetResourceIri     the IRI of the target resource.
    * @param customNewLinkValueIri the optional custom IRI supplied for the link value.
    * @param valueCreator          the IRI of the new link value's owner.
    * @param valuePermissions      the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param requestingUser        the user making the request.
    * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
    */
  private def incrementLinkValue(sourceResourceInfo: ReadResourceV2,
                                 linkPropertyIri: SmartIri,
                                 targetResourceIri: IRI,
                                 customNewLinkValueIri: Option[SmartIri] = None,
                                 valueCreator: IRI,
                                 valuePermissions: String,
                                 requestingUser: UserADM): Future[SparqlTemplateLinkUpdate] = {
    // Check whether a LinkValue already exists for this link.
    val maybeLinkValueInfo: Option[ReadLinkValueV2] = findLinkValue(
      sourceResourceInfo = sourceResourceInfo,
      linkPropertyIri = linkPropertyIri,
      targetResourceIri = targetResourceIri
    )

    for {
      // Make an IRI for the new LinkValue.
      newLinkValueIri: IRI <- checkOrCreateEntityIri(customNewLinkValueIri,
                                                     stringFormatter.makeRandomValueIri(sourceResourceInfo.resourceIri))

      linkUpdate = maybeLinkValueInfo match {
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
                                 requestingUser: UserADM): Future[SparqlTemplateLinkUpdate] = {

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

        for {
          // Generate an IRI for the new LinkValue.
          newLinkValueIri: IRI <- makeUnusedValueIri(sourceResourceInfo.resourceIri)
        } yield
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
        throw InconsistentRepositoryDataException(
          s"There should be a knora-base:LinkValue describing a direct link from resource <${sourceResourceInfo.resourceIri}> to resource <$targetResourceIri> using property <$linkPropertyIri>, but it seems to be missing")
    }
  }

  /**
    * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to change the metadata
    * on a `LinkValue`.
    *
    * @param sourceResourceInfo    information about the source resource.
    * @param linkPropertyIri       the IRI of the property that links the source resource to the target resource.
    * @param targetResourceIri     the IRI of the target resource.
    * @param customNewLinkValueIri the optional custom IRI supplied for the link value.
    * @param valueCreator          the IRI of the new link value's owner.
    * @param valuePermissions      the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param requestingUser        the user making the request.
    * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
    */
  private def changeLinkValueMetadata(sourceResourceInfo: ReadResourceV2,
                                      linkPropertyIri: SmartIri,
                                      targetResourceIri: IRI,
                                      customNewLinkValueIri: Option[SmartIri] = None,
                                      valueCreator: IRI,
                                      valuePermissions: String,
                                      requestingUser: UserADM): Future[SparqlTemplateLinkUpdate] = {

    // Check whether a LinkValue already exists for this link.
    val maybeLinkValueInfo: Option[ReadLinkValueV2] = findLinkValue(
      sourceResourceInfo = sourceResourceInfo,
      linkPropertyIri = linkPropertyIri,
      targetResourceIri = targetResourceIri
    )

    // Did we find it?
    maybeLinkValueInfo match {
      case Some(linkValueInfo) =>
        // Yes. Make a SparqlTemplateLinkUpdate.

        for {
          // If no custom IRI was provided, generate an IRI for the new LinkValue.
          newLinkValueIri: IRI <- checkOrCreateEntityIri(
            customNewLinkValueIri,
            stringFormatter.makeRandomValueIri(sourceResourceInfo.resourceIri))

        } yield
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
            newReferenceCount = linkValueInfo.valueHasRefCount,
            newLinkValueCreator = valueCreator,
            newLinkValuePermissions = valuePermissions
          )

      case None =>
        // We didn't find the LinkValue. This shouldn't happen.
        throw InconsistentRepositoryDataException(
          s"There should be a knora-base:LinkValue describing a direct link from resource <${sourceResourceInfo.resourceIri}> to resource <$targetResourceIri> using property <$linkPropertyIri>, but it seems to be missing")
    }
  }

  /**
    * The permissions that are granted by every `knora-base:LinkValue` describing a standoff link.
    */
  lazy val standoffLinkValuePermissions: String = {
    val permissions: Set[PermissionADM] = Set(
      PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.SystemUser),
      PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
    )

    PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)
  }

  /**
    * A convenience method for generating an unused random value IRI.
    *
    * @param resourceIri the IRI of the containing resource.
    * @return the new value IRI.
    */
  private def makeUnusedValueIri(resourceIri: IRI): Future[IRI] = {
    stringFormatter.makeUnusedIri(stringFormatter.makeRandomValueIri(resourceIri), storeManager, loggingAdapter)
  }
}
