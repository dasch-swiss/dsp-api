/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*

import java.time.Instant
import java.util.UUID
import scala.PartialFunction.cond

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraBase.StillImageExternalFileValue
import org.knora.webapi.messages.OntologyConstants.KnoraBase.StillImageFileValue
import org.knora.webapi.messages.OntologyConstants.KnoraBase.StillImageVectorFileValue
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.access.AccessRequest.ChangeValueRights
import org.knora.webapi.slice.resources.access.AccessRequest.CreateValue
import org.knora.webapi.slice.resources.access.AccessRequest.DeleteValue
import org.knora.webapi.slice.resources.access.AccessRequest.EraseValue
import org.knora.webapi.slice.resources.access.AccessRequest.ReorderValues
import org.knora.webapi.slice.resources.access.AccessRequest.UpdateValue
import org.knora.webapi.slice.resources.access.Grant
import org.knora.webapi.slice.resources.access.IntegrityRules
import org.knora.webapi.slice.resources.access.LinkValueWrites
import org.knora.webapi.slice.resources.access.ObjectAccess
import org.knora.webapi.slice.resources.access.PermissionsChange
import org.knora.webapi.slice.resources.access.ValueToCreate
import org.knora.webapi.slice.resources.access.ValueToDelete
import org.knora.webapi.slice.resources.access.ValueToRescope
import org.knora.webapi.slice.resources.access.ValueToUpdate
import org.knora.webapi.slice.resources.repo.ChangeLinkMetadataQuery
import org.knora.webapi.slice.resources.repo.ChangeLinkTargetQuery
import org.knora.webapi.slice.resources.repo.CreateLinkQuery
import org.knora.webapi.slice.resources.repo.DeleteLinkQuery
import org.knora.webapi.slice.resources.repo.DeleteValueQuery
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.resources.repo.service.ValueRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.slice.resources.service.ValueContentValidator
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

final class ValuesResponderV2(
  iriConverter: IriConverter,
  iriService: IriService,
  ontologyCacheHelpers: OntologyCacheHelpers,
  ontologyRepo: OntologyRepo,
  ontologyResponder: OntologyResponderV2,
  permissionUtilADM: PermissionUtilADM,
  permissionsResponder: PermissionsResponder,
  resourceUtilV2: ResourceUtilV2,
  triplestoreService: TriplestoreService,
  valueRepo: ValueRepo,
  valueValidator: ValueContentValidator,
  readResources: ReadResourcesService,
  objectAccess: ObjectAccess,
)(implicit private val stringFormatter: StringFormatter) {

  /**
   * Creates a new value in an existing resource, inside the object-access gate.
   *
   * @param valueToCreate the value to be created.
   * @param requestingUser the user making the request.
   * @param apiRequestID the API request ID.
   * @return a [[CreateValueResponseV2]].
   */
  def createValueV2(
    valueToCreate: CreateValueV2,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[CreateValueResponseV2] =
    for {
      adjustedProperty <- writableProperty(
                            valueToCreate.propertyIri,
                            valueToCreate.valueContent.valueType,
                            "created",
                            requestingUser,
                          )
      property    <- ZIO.fromEither(PropertyIri.from(valueToCreate.propertyIri)).mapError(BadRequestException.apply)
      permissions <- ZIO.foreach(valueToCreate.permissions)(permissionUtilADM.validatePermissions)
      response    <- objectAccess.within(requestingUser, apiRequestID, CreateValue(valueToCreate.resourceIri, property))(
                    createValue(valueToCreate, property, adjustedProperty, permissions, _),
                  )
    } yield response

  /**
   * Creates the value in the grant's resource. The request is validated against the grant's target, after the gate
   * decided.
   */
  private def createValue(
    valueToCreate: CreateValueV2,
    property: PropertyIri,
    adjustedInternalPropertyInfo: ReadPropertyInfoV2,
    customPermissions: Option[String],
    grant: Grant[ValueToCreate],
  ): Task[CreateValueResponseV2] = {
    val resource                      = grant.target.resource
    val submittedInternalPropertyIri  = property.toInternalSchema
    val submittedInternalValueContent = valueToCreate.valueContent.toOntologySchema(InternalSchema)
    for {
      _ <- valueValidator.validate(valueToCreate).mapError(BadRequestException.apply)
      _ <- ZIO
             .fail(
               BadRequestException(
                 s"The rdf:type of resource <${valueToCreate.resourceIri}> is not <${valueToCreate.resourceClassIri}>",
               ),
             )
             .when(resource.resourceClassIri != valueToCreate.resourceClassIri.toOntologySchema(InternalSchema))

      classInfoResponse <-
        ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
          classIris = Set(resource.resourceClassIri),
          allLanguages = false,
          requestingUser = grant.caller,
        )

      // The resource class must have a cardinality for the submitted property.
      cardinalityInfo <-
        ZIO
          .fromOption(
            classInfoResponse.classes
              .get(resource.resourceClassIri)
              .flatMap(_.allCardinalities.get(submittedInternalPropertyIri)),
          )
          .orElseFail(
            BadRequestException(
              s"Resource <${valueToCreate.resourceIri}> belongs to class <${resource.resourceClassIri
                  .toOntologySchema(ApiV2Complex)}>, which has no cardinality for property <${valueToCreate.propertyIri}>",
            ),
          )

      // The object of the adjusted property (the value, or the target of the link) must have the correct type for the
      // adjusted property's knora-base:objectClassConstraint.
      _ <- checkPropertyObjectClassConstraint(adjustedInternalPropertyInfo, submittedInternalValueContent, grant.caller)
      _ <- ifIsListValueThenCheckItPointsToListNodeWhichIsNotARootNode(submittedInternalValueContent)
      _ <- submittedInternalValueContent match {
             case textValueContent: TextValueContentV2 =>
               StandoffLinkTargets
                 .resourceIris(textValueContent.standoffLinkTagTargetResourceIris)
                 .flatMap(checkResourceIris(_, grant.caller))
             case _ => ZIO.unit
           }
      _ <- grant.verify(IntegrityRules.valueAddable(resource.resourceIri, property, cardinalityInfo.cardinality))

      defaultValuePermissions <- permissionsResponder.newValueDefaultObjectAccessPermissions(
                                   resource.projectADM.id,
                                   resource.resourceClassIri,
                                   submittedInternalPropertyIri,
                                   grant.caller,
                                 )
      newValuePermissionLiteral <- customPermissions.fold(ZIO.succeed(defaultValuePermissions.permissionLiteral))(
                                     custom =>
                                       PermissionUtilADM
                                         .ensureWithinDefaultPermissions(
                                           resource.projectADM.id,
                                           custom,
                                           defaultValuePermissions.permissionLiteral,
                                           grant.caller,
                                           "The specified value permissions would give a value's creator a higher permission on the value than the default permissions",
                                         )
                                         .as(custom),
                                   )

      created <-
        submittedInternalValueContent match {
          case linkValueContent: LinkValueContentV2 =>
            createLinkValueV2AfterChecks(
              grant = grant,
              linkPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              linkValueProperty = property,
              linkValueContent = linkValueContent,
              maybeValueIri = valueToCreate.valueIri,
              maybeValueUUID = valueToCreate.valueUUID,
              maybeCreationDate = valueToCreate.valueCreationDate,
              valuePermissions = newValuePermissionLiteral,
              valueHasOrder = valueToCreate.valueHasOrder,
            )

          case ordinaryValueContent =>
            createOrdinaryValueV2AfterChecks(
              grant = grant,
              propertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              value = ordinaryValueContent,
              maybeValueIri = valueToCreate.valueIri,
              maybeValueUUID = valueToCreate.valueUUID,
              maybeValueCreationDate = valueToCreate.valueCreationDate,
              valuePermissions = newValuePermissionLiteral,
              valueHasOrder = valueToCreate.valueHasOrder,
            )
        }
    } yield CreateValueResponseV2(
      valueIri = created.newValueIri,
      valueType = created.valueContent.valueType,
      valueUUID = created.newValueUUID,
      valueCreationDate = created.creationDate,
      projectADM = resource.projectADM,
    )
  }

  private def ifIsListValueThenCheckItPointsToListNodeWhichIsNotARootNode(valueContent: ValueContentV2) =
    valueContent match {
      case listValue: HierarchicalListValueContentV2 =>
        resourceUtilV2.checkListNodeExistsAndIsRootNode(listValue.valueHasListNode).flatMap {
          // it doesn't have isRootNode property - it's a child node
          case Right(false) => ZIO.unit
          // it does have isRootNode property - it's a root node
          case Right(true) =>
            val msg = s"<${listValue.valueHasListNode}> is a root node. Root nodes cannot be set as values."
            ZIO.fail(BadRequestException(msg))
          // it doesn't exists or isn't valid list
          case Left(_) =>
            val msg = s"<${listValue.valueHasListNode}> does not exist or is not a ListNode."
            ZIO.fail(NotFoundException(msg))
        }
      case _ => ZIO.unit
    }

  /**
   * Creates an ordinary value (i.e. not a link) in the grant's resource, after the checks. The caller becomes its
   * creator.
   *
   * @param grant                  the gate's grant; its target holds the resource.
   * @param propertyIri            the property that should point to the value.
   * @param value                  an [[ValueContentV2]] describing the value.
   * @param maybeValueIri          the optional custom IRI supplied for the value.
   * @param maybeValueUUID         the optional custom UUID supplied for the value.
   * @param maybeValueCreationDate the optional custom creation date supplied for the value.
   * @param valuePermissions       the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
   * @return an [[UnverifiedValueV2]].
   */
  private def createOrdinaryValueV2AfterChecks(
    grant: Grant[ValueToCreate],
    propertyIri: SmartIri,
    value: ValueContentV2,
    maybeValueIri: Option[SmartIri],
    maybeValueUUID: Option[UUID],
    maybeValueCreationDate: Option[Instant],
    valuePermissions: IRI,
    valueHasOrder: Option[Int],
  ) = {
    val resourceInfo = grant.target.resource
    for {

      // Make a new value UUID.
      newValueUUID <- ValuesResponderV2.makeNewValueUUID(maybeValueIri, maybeValueUUID)

      // Make an IRI for the new value.
      newValueIriStr <-
        iriService.checkOrCreateEntityIri(
          maybeValueIri,
          ValueIri.from(resourceInfo.resourceIri, newValueUUID).value,
        )
      createdValueIri <- ZIO.fromEither(ValueIri.from(newValueIriStr)).mapError(BadRequestException.apply)

      // Make a creation date for the new value
      creationDate: Instant = maybeValueCreationDate match {
                                case Some(customCreationDate) => customCreationDate
                                case None                     => Instant.now
                              }

      // If we're creating a text value, update direct links and LinkValues for any resource references in standoff.
      standoffLinkUpdates <-
        value match {
          case textValueContent: TextValueContentV2 =>
            // Construct a SparqlTemplateLinkUpdate for each reference that was added.
            StandoffLinkTargets
              .resourceIris(textValueContent.standoffLinkTagTargetResourceIris)
              .flatMap(targets =>
                ZIO.foreach(targets.toVector)(target =>
                  LinkValueWrites.increment(
                    grant,
                    OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                    target,
                    KnoraUserRepo.builtIn.SystemUser.id.value,
                    standoffLinkValuePermissions,
                    newValueIri(None, resourceInfo.resourceIri),
                  ),
                ),
              )

          case _ => ZIO.succeed(Vector.empty[SparqlTemplateLinkUpdate])
        }

      dataNamedGraphInternal <-
        iriConverter.asInternalIri(ProjectService.projectDataNamedGraphV2(resourceInfo.projectADM).value)
      resourceIriInternal  <- iriConverter.asInternalIri(resourceInfo.resourceIri.value)
      newValueIriInternal  <- iriConverter.asInternalIri(createdValueIri.value)
      valueCreatorInternal <- iriConverter.asInternalIri(grant.caller.id)

      // Use repository method which handles dual validation
      _ <- valueRepo.createValue(
             dataNamedGraph = dataNamedGraphInternal,
             resourceIri = resourceIriInternal,
             propertyIri = propertyIri,
             newValueIri = newValueIriInternal,
             newValueUUID = newValueUUID,
             value = value,
             linkUpdates = standoffLinkUpdates,
             valueCreator = valueCreatorInternal,
             valuePermissions = valuePermissions,
             creationDate = creationDate,
             valueHasOrder = valueHasOrder,
           )
    } yield UnverifiedValueV2(
      newValueIri = createdValueIri,
      newValueUUID = newValueUUID,
      valueContent = value.unescape,
      permissions = valuePermissions,
      creationDate = creationDate,
    )
  }

  /**
   * Creates a link from the grant's resource, after the checks. The caller becomes the link value's creator.
   *
   * @param grant             the gate's grant; its target holds the resource.
   * @param linkPropertyIri   the link property.
   * @param linkValueProperty the link value property of the request.
   * @param linkValueContent  a [[LinkValueContentV2]] specifying the target resource.
   * @param maybeValueIri     the optional custom IRI supplied for the value.
   * @param maybeValueUUID    the optional custom UUID supplied for the value.
   * @param valuePermissions  the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
   * @return an [[UnverifiedValueV2]].
   */
  private def createLinkValueV2AfterChecks(
    grant: Grant[ValueToCreate],
    linkPropertyIri: SmartIri,
    linkValueProperty: PropertyIri,
    linkValueContent: LinkValueContentV2,
    maybeValueIri: Option[SmartIri],
    maybeValueUUID: Option[UUID],
    maybeCreationDate: Option[Instant],
    valuePermissions: IRI,
    valueHasOrder: Option[Int],
  ) = {
    val resourceInfo = grant.target.resource
    for {
      _ <- grant.verify(
             IntegrityRules.noDuplicateLink(
               resourceInfo.resourceIri,
               linkValueProperty,
               linkValueContent.referredResourceIri,
             ),
           )
      newValueUUID             <- ValuesResponderV2.makeNewValueUUID(maybeValueIri, maybeValueUUID)
      sparqlTemplateLinkUpdate <-
        LinkValueWrites.increment(
          grant,
          linkPropertyIri,
          linkValueContent.referredResourceIri,
          grant.caller.id,
          valuePermissions,
          newValueIri(maybeValueIri, resourceInfo.resourceIri),
        )

      creationDate: Instant =
        maybeCreationDate match {
          case Some(customValueCreationDate) => customValueCreationDate
          case None                          => Instant.now
        }

      // Duplicate-order check: use the link value property IRI (the property that attaches the LinkValue to the resource).
      linkValuePropertyIri = sparqlTemplateLinkUpdate.linkPropertyIri.toInternalSchema.fromLinkPropToLinkValueProp
      resourceIriInternal <- iriConverter.asInternalIri(resourceInfo.resourceIri.value)
      // The gate's IriLocker lock serialises concurrent writes per resource IRI within a single JVM instance,
      // making the ASK check + INSERT effectively atomic under single-instance deployment.
      // Multi-instance deployments do not share this lock; a race window exists there.
      _ <- ZIO.foreach(valueHasOrder)(order =>
             valueRepo.checkDuplicateOrder(resourceIriInternal, linkValuePropertyIri, order),
           )

      // Generate a SPARQL update.
      sparqlUpdate <- CreateLinkQuery.build(
                        project = resourceInfo.projectADM,
                        resourceIri = resourceInfo.resourceIri,
                        linkUpdate = sparqlTemplateLinkUpdate,
                        newValueUUID = newValueUUID,
                        creationDate = creationDate,
                        maybeComment = linkValueContent.comment,
                        valueHasOrder = valueHasOrder,
                      )

      _ <- triplestoreService.query(sparqlUpdate)
    } yield UnverifiedValueV2(
      newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
      newValueUUID = newValueUUID,
      valueContent = linkValueContent.unescape,
      permissions = valuePermissions,
      creationDate = creationDate,
    )
  }

  /**
   * Creates a new version of an existing value, or changes its permissions, inside the object-access gate.
   *
   * @param updateValue       the value update.
   * @param requestingUser    the user making the request.
   * @param apiRequestId      the ID of the API request.
   * @return a [[UpdateValueResponseV2]].
   */
  def updateValueV2(
    updateValue: UpdateValueV2,
    requestingUser: User,
    apiRequestId: UUID,
  ): Task[UpdateValueResponseV2] =
    for {
      adjustedProperty <- writableProperty(updateValue.propertyIri, updateValue.valueType, "updated", requestingUser)
      property         <- ZIO.fromEither(PropertyIri.from(updateValue.propertyIri)).mapError(BadRequestException.apply)
      response         <- updateValue match {
                    case content: UpdateValueContentV2 =>
                      updateContentInGate(content, property, adjustedProperty, requestingUser, apiRequestId)
                    case rights: UpdateValuePermissionsV2 =>
                      updatePermissionsInGate(rights, property, requestingUser, apiRequestId)
                  }
    } yield response

  private def updateContentInGate(
    content: UpdateValueContentV2,
    property: PropertyIri,
    adjustedProperty: ReadPropertyInfoV2,
    requestingUser: User,
    apiRequestId: UUID,
  ): Task[UpdateValueResponseV2] =
    for {
      permissions <- ZIO.foreach(content.permissions)(permissionUtilADM.validatePermissions)
      change       = permissions.fold(PermissionsChange.Unchanged)(PermissionsChange.To(_))
      request      = UpdateValue(content.resourceIri, property, content.valueIri, change)
      response    <- objectAccess.within(requestingUser, apiRequestId, request)(
                    updateValueContent(content, property, adjustedProperty, _),
                  )
    } yield response

  private def updatePermissionsInGate(
    rights: UpdateValuePermissionsV2,
    property: PropertyIri,
    requestingUser: User,
    apiRequestId: UUID,
  ): Task[UpdateValueResponseV2] =
    for {
      permissions <- permissionUtilADM.validatePermissions(rights.permissions)
      request      = ChangeValueRights(rights.resourceIri, property, rights.valueIri)
      response    <- objectAccess.within(requestingUser, apiRequestId, request)(
                    updateValuePermissions(rights, permissions, _),
                  )
    } yield response

  /**
   * The checks of a value creation or update that use only the request and the ontology; they run before the gate.
   * Returns the adjusted property: for a link value property, its link property.
   *
   * @param verb what the write does to a value, for the message refusing `knora-api:hasStandoffLinkToValue`.
   */
  private def writableProperty(
    propertyIri: SmartIri,
    valueType: SmartIri,
    verb: String,
    requestingUser: User,
  ): Task[ReadPropertyInfoV2] =
    for {
      submittedInternalPropertyIri <- ZIO.attempt(propertyIri.toInternalSchema)
      propertyInfoResponse         <- ontologyResponder.getPropertyDefinitionsFromOntologyV2(
                                propertyIris = Set(submittedInternalPropertyIri),
                                allLanguages = false,
                                requestingUser = requestingUser,
                              )
      submittedPropertyInfo = propertyInfoResponse.properties(submittedInternalPropertyIri)
      _                    <- {
        val msg =
          s"Invalid property <${submittedPropertyInfo.entityInfoContent.propertyIri.toComplexSchema}>." +
            s" Use a link value property to submit a link."
        ZIO.fail(BadRequestException(msg)).when(submittedPropertyInfo.isLinkProp)
      }
      _ <- ZIO.when(propertyIri.toString == OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue)(
             ZIO.fail(BadRequestException(s"Values of <$propertyIri> cannot be $verb directly")),
           )
      adjusted <- getAdjustedInternalPropertyInfo(propertyIri, Some(valueType), submittedPropertyInfo)
    } yield adjusted

  /** Updates the permissions attached to the grant's value. */
  private def updateValuePermissions(
    updateValue: UpdateValuePermissionsV2,
    permissions: String,
    grant: Grant[ValueToRescope],
  ): Task[UpdateValueResponseV2] = {
    val resource = grant.target.resource
    val value    = grant.target.value
    for {
      _ <- checkUpdateTarget(updateValue, resource, value)
      _ <- valueRepo.updateValuePermissions(
             projectDataGraph = ProjectService.projectDataNamedGraphV2(resource.projectADM),
             resourceIri = InternalIri(resource.resourceIri.value),
             valueIri = value.valueIri,
             newPermissions = permissions,
             currentTime = Instant.now,
           )
    } yield UpdateValueResponseV2(value.valueIri, value.valueContent.valueType, value.valueHasUUID, resource.projectADM)
  }

  /**
   * Updates the contents of the grant's value. The request is validated against the grant's target, after the gate
   * decided; the new version takes its permissions from the grant.
   */
  private def updateValueContent(
    updateValue: UpdateValueContentV2,
    property: PropertyIri,
    adjustedInternalPropertyInfo: ReadPropertyInfoV2,
    grant: Grant[ValueToUpdate],
  ): Task[UpdateValueResponseV2] = {
    val resource                      = grant.target.resource
    val currentValue                  = grant.target.value
    val submittedInternalValueContent = updateValue.valueContent.toOntologySchema(InternalSchema)
    val newValueVersionPermissions    = grant.target.permissions match {
      case PermissionsChange.To(literal) => literal
      case PermissionsChange.Unchanged   => currentValue.permissions
    }
    for {
      _ <- valueValidator.validate(updateValue).mapError(BadRequestException.apply)
      _ <- checkUpdateTarget(updateValue, resource, currentValue)

      // The object of the adjusted property (the value, or the target of the link) must have the correct type for the
      // adjusted property's knora-base:objectClassConstraint.
      _ <- checkPropertyObjectClassConstraint(adjustedInternalPropertyInfo, submittedInternalValueContent, grant.caller)
      _ <- ifIsListValueThenCheckItPointsToListNodeWhichIsNotARootNode(submittedInternalValueContent)
      _ <- submittedInternalValueContent match {
             case textValueContent: TextValueContentV2 =>
               StandoffLinkTargets
                 .resourceIris(textValueContent.standoffLinkTagTargetResourceIris)
                 .flatMap(checkResourceIris(_, grant.caller))
             case _ => ZIO.unit
           }

      newValueVersion <-
        (currentValue, submittedInternalValueContent) match {
          case (currentLinkValue: ReadLinkValueV2, newLinkValue: LinkValueContentV2) =>
            updateLinkValueV2AfterChecks(
              grant = grant,
              linkPropertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              linkValueProperty = property,
              currentLinkValue = currentLinkValue,
              newLinkValue = newLinkValue,
              valuePermissions = newValueVersionPermissions,
              valueCreationDate = updateValue.valueCreationDate,
              newValueVersionIri = updateValue.newValueVersionIri,
            )

          case _ =>
            updateOrdinaryValueV2AfterChecks(
              grant = grant,
              propertyIri = adjustedInternalPropertyInfo.entityInfoContent.propertyIri,
              newValueVersion = submittedInternalValueContent,
              valuePermissions = newValueVersionPermissions,
              valueCreationDate = updateValue.valueCreationDate,
              newValueVersionIri = updateValue.newValueVersionIri,
            )
        }
    } yield UpdateValueResponseV2(
      valueIri = newValueVersion.newValueIri,
      valueType = newValueVersion.valueContent.valueType,
      valueUUID = newValueVersion.newValueUUID,
      projectADM = resource.projectADM,
    )
  }

  /** The checks of a value update on the grant's target; they run inside the gate, after its decision. */
  private def checkUpdateTarget(updateValue: UpdateValueV2, resource: ReadResourceV2, currentValue: ReadValueV2) = {
    val submittedInternalValueType = updateValue.valueType.toInternalSchema
    val isSameType                 = currentValue.valueContent.valueType == submittedInternalValueType
    // Cross-type updates are intentionally allowed between all three still-image subtypes
    // (raster, IIIF external, and vector/SVG), since they all represent the same resource
    // and differ only in storage/delivery method.
    val isStillImageTypes =
      Set(
        submittedInternalValueType.toInternalIri.value,
        currentValue.valueContent.valueType.toInternalIri.value,
      ).subsetOf(Set(StillImageExternalFileValue, StillImageFileValue, StillImageVectorFileValue))
    for {
      _ <- {
        val msg =
          s"The rdf:type of resource <${updateValue.resourceIri.value}> is not <${updateValue.resourceClassIri}>"
        ZIO
          .fail(BadRequestException(msg))
          .when(resource.resourceClassIri != updateValue.resourceClassIri.toInternalSchema)
      }
      _ <-
        ZIO.unless(isSameType || isStillImageTypes)(
          ZIO.fail(
            BadRequestException(
              s"Value <${updateValue.valueIri}> has type <${currentValue.valueContent.valueType.toOntologySchema(ApiV2Complex)}>, but the submitted type was <${updateValue.valueType}>",
            ),
          ),
        )

      // If a custom value creation date was submitted, make sure it's later than the date of the current version.
      _ <- ZIO.when(updateValue.valueCreationDate.exists(!_.isAfter(currentValue.valueCreationDate)))(
             ZIO.fail(
               BadRequestException(
                 "A custom value creation date must be later than the date of the current version",
               ),
             ),
           )
    } yield ()
  }

  /**
   * Changes an ordinary value (i.e. not a link) of the grant's target, after the checks.
   *
   * @param grant              the gate's grant; its target holds the resource and the current value version.
   * @param propertyIri        the IRI of the property that points to the value.
   * @param newValueVersion    a [[ValueContentV2]] representing the new value version, in the internal schema.
   * @param valuePermissions   the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
   * @param valueCreationDate  a custom value creation date.
   * @param newValueVersionIri an optional IRI to be used for the new value version.
   * @return an [[UnverifiedValueV2]].
   */
  private def updateOrdinaryValueV2AfterChecks(
    grant: Grant[ValueToUpdate],
    propertyIri: SmartIri,
    newValueVersion: ValueContentV2,
    valuePermissions: String,
    valueCreationDate: Option[Instant],
    newValueVersionIri: Option[SmartIri],
  ): Task[UnverifiedValueV2] = {
    val resourceInfo = grant.target.resource
    val currentValue = grant.target.value
    for {
      newVersionIri <- newValueIri(newValueVersionIri, resourceInfo.resourceIri)

      // If we're updating a text value, update direct links and LinkValues for any resource references in Standoff.
      standoffLinkUpdates <-
        (currentValue.valueContent, newValueVersion) match {
          case (currentTextValue: TextValueContentV2, newTextValue: TextValueContentV2) =>
            for {
              (added, removed) <- ZIO.fromEither(
                                    for {
                                      newRefs     <- newTextValue.standoffLinkTagTargetResourceIris
                                      currentRefs <- StandoffLinkTargets.withValueIri(
                                                       currentValue.valueIri.value,
                                                       currentTextValue.standoffLinkTagTargetResourceIris,
                                                     )
                                    } yield (newRefs -- currentRefs, currentRefs -- newRefs),
                                  )
              addedTargets   <- StandoffLinkTargets.toResourceIris(added)
              removedTargets <- StandoffLinkTargets.toResourceIris(removed)
              increments     <- ZIO.foreach(addedTargets.toVector)(target =>
                              LinkValueWrites.increment(
                                grant,
                                OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                                target,
                                KnoraUserRepo.builtIn.SystemUser.id.value,
                                standoffLinkValuePermissions,
                                newValueIri(None, resourceInfo.resourceIri),
                              ),
                            )
              decrements <- ZIO.foreach(removedTargets.toVector)(target =>
                              LinkValueWrites.decrement(
                                grant,
                                OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                                target,
                                KnoraUserRepo.builtIn.SystemUser.id.value,
                                standoffLinkValuePermissions,
                                makeUnusedValueIri(resourceInfo.resourceIri),
                              ),
                            )
            } yield increments ++ decrements

          case _ => ZIO.succeed(Vector.empty[SparqlTemplateLinkUpdate])
        }

      // If no custom value creation date was provided, make a timestamp to indicate when the value
      // was updated.
      currentTime: Instant    = valueCreationDate.getOrElse(Instant.now)
      dataNamedGraphInternal <-
        iriConverter.asInternalIri(ProjectService.projectDataNamedGraphV2(resourceInfo.projectADM).value)
      resourceIriInternal     <- iriConverter.asInternalIri(resourceInfo.resourceIri.value)
      currentValueIriInternal <- iriConverter.asInternalIri(currentValue.valueIri.value)
      newValueIriInternal     <- iriConverter.asInternalIri(newVersionIri.value)
      valueCreatorInternal    <- iriConverter.asInternalIri(grant.caller.id)
      // Generate a SPARQL update.
      _ <- valueRepo.updateValue(
             dataNamedGraph = dataNamedGraphInternal,
             resourceIri = resourceIriInternal,
             propertyIri = propertyIri,
             currentValueIri = currentValueIriInternal,
             newValueIri = newValueIriInternal,
             value = newValueVersion,
             valueCreator = valueCreatorInternal,
             valuePermissions = valuePermissions,
             linkUpdates = standoffLinkUpdates,
             creationDate = currentTime,
           )
    } yield UnverifiedValueV2(
      newValueIri = newVersionIri,
      newValueUUID = currentValue.valueHasUUID,
      valueContent = newValueVersion.unescape,
      permissions = valuePermissions,
      creationDate = currentTime,
    )
  }

  /**
   * Changes a link of the grant's target, after the checks.
   *
   * @param grant              the gate's grant; its target holds the resource and the current link value.
   * @param linkPropertyIri    the IRI of the link property.
   * @param linkValueProperty  the link value property of the request.
   * @param currentLinkValue   a [[ReadLinkValueV2]] representing the `knora-base:LinkValue` for the existing link.
   * @param newLinkValue       a [[LinkValueContentV2]] indicating the new target resource.
   * @param valuePermissions   the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
   * @param valueCreationDate  a custom value creation date.
   * @param newValueVersionIri an optional IRI to be used for the new value version.
   * @return an [[UnverifiedValueV2]].
   */
  private def updateLinkValueV2AfterChecks(
    grant: Grant[ValueToUpdate],
    linkPropertyIri: SmartIri,
    linkValueProperty: PropertyIri,
    currentLinkValue: ReadLinkValueV2,
    newLinkValue: LinkValueContentV2,
    valuePermissions: String,
    valueCreationDate: Option[Instant],
    newValueVersionIri: Option[SmartIri],
  ): Task[UnverifiedValueV2] = {
    val resourceInfo = grant.target.resource
    val valueCreator = grant.caller.id
    // Are we changing the link target?
    if (currentLinkValue.valueContent.referredResourceIri != newLinkValue.referredResourceIri) {
      for {
        // Yes. The resource must not already link to the new target.
        _ <- grant.verify(
               IntegrityRules.noDuplicateLink(
                 resourceInfo.resourceIri,
                 linkValueProperty,
                 newLinkValue.referredResourceIri,
               ),
             )
        // Delete the existing link and decrement its LinkValue's reference count.
        sparqlTemplateLinkUpdateForCurrentLink <-
          LinkValueWrites.decrement(
            grant,
            linkPropertyIri,
            currentLinkValue.valueContent.referredResourceIri,
            valueCreator,
            valuePermissions,
            makeUnusedValueIri(resourceInfo.resourceIri),
          )

        // Create a new link, and create a new LinkValue for it.
        sparqlTemplateLinkUpdateForNewLink <-
          LinkValueWrites.increment(
            grant,
            linkPropertyIri,
            newLinkValue.referredResourceIri,
            valueCreator,
            valuePermissions,
            newValueIri(newValueVersionIri, resourceInfo.resourceIri),
          )

        // If no custom value creation date was provided, make a timestamp to indicate when the link value
        // was updated.
        currentTime: Instant = valueCreationDate.getOrElse(Instant.now)

        result <- ChangeLinkTargetQuery.build(
                    project = resourceInfo.projectADM,
                    linkSourceIri = resourceInfo.resourceIri,
                    linkUpdateForCurrentLink = sparqlTemplateLinkUpdateForCurrentLink,
                    linkUpdateForNewLink = sparqlTemplateLinkUpdateForNewLink,
                    maybeComment = newLinkValue.comment,
                    currentTime = currentTime,
                    requestingUser = grant.caller.userIri,
                  )
        (newLinkValueUUID, sparqlUpdate) = result

        _ <- triplestoreService.query(sparqlUpdate)
      } yield UnverifiedValueV2(
        newValueIri = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
        newValueUUID = newLinkValueUUID,
        valueContent = newLinkValue.unescape,
        permissions = valuePermissions,
        creationDate = currentTime,
      )
    } else {
      for {
        // We're not changing the link target, just the metadata on the LinkValue.
        sparqlTemplateLinkUpdate <-
          LinkValueWrites.changeMetadata(
            grant,
            linkPropertyIri,
            currentLinkValue.valueContent.referredResourceIri,
            valueCreator,
            valuePermissions,
            newValueIri(newValueVersionIri, resourceInfo.resourceIri),
          )

        result <- ChangeLinkMetadataQuery.build(
                    project = resourceInfo.projectADM,
                    linkSourceIri = resourceInfo.resourceIri,
                    linkUpdate = sparqlTemplateLinkUpdate,
                    maybeComment = newLinkValue.comment,
                  )
        (currentTime, sparqlUpdate) = result

        _ <- triplestoreService.query(sparqlUpdate)
      } yield UnverifiedValueV2(
        newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
        newValueUUID = currentLinkValue.valueHasUUID,
        valueContent = newLinkValue.unescape,
        permissions = valuePermissions,
        creationDate = currentTime,
      )
    }
  }

  /**
   * Reorders the values of a property inside the object-access gate. The caller's visible values are permuted among
   * their own `knora-base:valueHasOrder` slots; values hidden from the caller keep theirs.
   *
   * @param orderedValues each of the caller's visible values of the property once, in their new order.
   * @return the number of values reordered.
   */
  def reorderValues(
    resourceIri: ResourceIri,
    propertyIri: PropertyIri,
    orderedValues: List[ValueIri],
    requestingUser: User,
    apiRequestId: UUID,
  ): Task[Int] =
    existingValuesProperty(propertyIri, "reordered") *>
      objectAccess.within(requestingUser, apiRequestId, ReorderValues(resourceIri, propertyIri)) { grant =>
        for {
          slots <- objectAccess.reorderSlots(grant, orderedValues)
          now   <- Clock.instant
          _     <- valueRepo.reorderValues(
                 ProjectService.projectDataNamedGraphV2(grant.project),
                 InternalIri(grant.target.resource.resourceIri.value),
                 slots,
                 now,
               )
        } yield slots.size
      }

  def eraseValue(
    req: EraseValueV2,
    requestingUser: User,
    project: KnoraProject,
    onlyHistory: Boolean = false,
  ): Task[SuccessResponseV2] = {
    val request = EraseValue(req.resourceIri, req.propertyIri, req.valueIri, project, onlyHistory)
    existingValuesProperty(req.propertyIri, "deleted") *>
      objectAccess
        .within(requestingUser, req.apiRequestId, request) { grant =>
          val value = grant.target.value
          for {
            _           <- checkRemoval(req, grant, grant.target.resource, value, onlyHistory)
            _           <- failBadRequestForStandoffWithLinks(value)
            allPrevious <- valueRepo.findAllPrevious(value.valueIri)
            isLink       = cond(value) { case _: ReadLinkValueV2 => true }
            erased       = if (onlyHistory) List() else List(value.valueIri)
            _           <- ZIO.foreachDiscard(allPrevious.reverse ++ erased) { erasedIri =>
                   ZIO.when(isLink)(valueRepo.eraseValueDirectLink(grant.project)(erasedIri)) *>
                     valueRepo.eraseValue(grant.project)(erasedIri)
                 }
          } yield ()
        }
        .as(SuccessResponseV2(s"Value <${req.propertyIri}> deleted"))
  }

  def eraseValueHistory(
    req: EraseValueHistoryV2,
    requestingUser: User,
    project: KnoraProject,
  ): Task[SuccessResponseV2] =
    eraseValue(req.toEraseValueV2, requestingUser, project, true)

  private def failBadRequestForStandoffWithLinks(value: ReadValueV2) =
    for {
      query <- ZIO.succeed(s"""
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT * WHERE {
          <${value.valueIri}> knora-base:previousValue ?previousValue .
          ?previousValue knora-base:valueHasStandoff ?pvStandoff .
          ?pvStandoff rdf:type knora-base:StandoffLinkTag .
        }
      """)
      historyHasLinks <- triplestoreService.query(Select(query)).map(_.results.bindings.nonEmpty)
      // The history is checked first: a value served from its stored XML with link history answers 400, not 500.
      valueHasLinks <- (historyHasLinks, value) match {
                         case (true, _)                  => ZIO.succeed(true)
                         case (_, text: ReadTextValueV2) =>
                           ZIO
                             .fromEither(
                               StandoffLinkTargets.withValueIri(
                                 value.valueIri.value,
                                 text.valueContent.standoffLinkTagTargetResourceIris,
                               ),
                             )
                             .map(_.nonEmpty)
                         case _ => ZIO.succeed(false)
                       }
      result <- ZIO
                  .fail(BadRequestException("Erasing standoff text values with links is not supported"))
                  .when(valueHasLinks)
    } yield result

  /**
   * The checks of a value removal or reorder that use only the request and the ontology; they run before the gate.
   * Returns the adjusted property: for a link value property, its link property.
   *
   * @param verb what the write does to a value, for the message refusing `knora-api:hasStandoffLinkToValue`.
   */
  private def existingValuesProperty(property: PropertyIri, verb: String): Task[ReadPropertyInfoV2] =
    for {
      _ <- ZIO
             .fail(BadRequestException(s"Values of <${KA.HasStandoffLinkToValue}> cannot be $verb directly"))
             .when(property.toComplexSchema.toIri == KA.HasStandoffLinkToValue)
      submitted <- ontologyRepo
                     .findProperty(property)
                     .someOrFail(NotFoundException(s"Property not found: $property"))
                     .filterOrFail(!_.isLinkProp)(
                       BadRequestException(s"Invalid property <$property>. Use a link value property to submit a link."),
                     )
      adjusted <- linkPropertyIfLinkValue(submitted)
    } yield adjusted

  /** The checks of a value removal on the grant's target, after the gate: class, value type, then cardinality. */
  private def checkRemoval(
    removal: ValueRemoval,
    grant: Grant[?],
    resource: ReadResourceV2,
    value: ReadValueV2,
    onlyHistory: Boolean,
  ): Task[Unit] =
    for {
      _ <- ZIO
             .fail(
               BadRequestException(
                 s"Resource <${removal.resourceIri}> does not belong to class <${removal.resourceClassIri}>",
               ),
             )
             .when(resource.resourceClassIri != removal.resourceClassIri.toInternalSchema)
      _ <-
        ZIO
          .fail(
            BadRequestException(
              s"Value <${removal.valueIri}> in resource <${removal.resourceIri}> is not of type <${removal.valueTypeIri}>",
            ),
          )
          .when(value.valueContent.valueType != removal.valueTypeIri.toOntologySchema(InternalSchema))
      cardinalityInfo <-
        ontologyRepo
          .findClassBy(resource.resourceClassIri.toInternalIri)
          .someOrFail(NotFoundException(s"Resource class not found: ${resource.resourceClassIri}"))
          .flatMap(c =>
            ZIO
              .fromOption(c.allCardinalities.get(removal.propertyIri.toInternalSchema))
              .orElseFail(
                InconsistentRepositoryDataException(
                  s"Resource <${removal.resourceIri}> belongs to class <${resource.resourceClassIri.toComplexSchema}>, which has no cardinality for property <${removal.propertyIri}>",
                ),
              ),
          )
      _ <- grant.verify(
             IntegrityRules.valueRemovable(
               removal.resourceIri,
               removal.propertyIri,
               cardinalityInfo.cardinality,
               onlyHistory,
             ),
           )
    } yield ()

  /**
   * Marks a value as deleted.
   *
   * @param deleteValue the information about value to be deleted.
   * @param requestingUser the user making the request.
   */
  def deleteValueV2(deleteValue: DeleteValueV2, requestingUser: User): Task[SuccessResponseV2] = {
    val request = DeleteValue(deleteValue.resourceIri, deleteValue.propertyIri, deleteValue.valueIri)
    for {
      property        <- existingValuesProperty(deleteValue.propertyIri, "deleted")
      deletedValueIri <-
        objectAccess.within(requestingUser, deleteValue.apiRequestId, request) { grant =>
          checkRemoval(deleteValue, grant, grant.target.resource, grant.target.value, onlyHistory = false) *>
            deleteValueV2AfterChecks(
              grant,
              property.propertyIri.toInternalSchema,
              deleteValue.deleteComment,
              deleteValue.deleteDate,
            )
        }
    } yield SuccessResponseV2(s"Value <$deletedValueIri> marked as deleted")
  }

  /**
   * Deletes a value (either an ordinary value or a link) of the grant's target, after the checks.
   *
   * @param grant          the gate's grant; its target holds the resource and the value to be deleted.
   * @param propertyIri    the IRI of the property that points from the resource to the value; for a link, the link
   *                       property.
   * @param deleteComment  an optional comment explaining why the value is being deleted.
   * @param deleteDate     an optional timestamp indicating when the value was deleted.
   * @return the IRI of the value that was marked as deleted.
   */
  private def deleteValueV2AfterChecks(
    grant: Grant[ValueToDelete],
    propertyIri: SmartIri,
    deleteComment: Option[String],
    deleteDate: Option[Instant],
  ): Task[ValueIri] =
    grant.target.value.valueContent match {
      case link: LinkValueContentV2 => deleteLinkValueV2AfterChecks(grant, link, propertyIri, deleteComment, deleteDate)
      case _                        => deleteOrdinaryValueV2AfterChecks(grant, propertyIri, deleteComment, deleteDate)
    }

  /**
   * Deletes a link: a new version of its `LinkValue` with the reference count decremented, marked as deleted when
   * the count reaches 0, with the permissions of the previous version.
   */
  private def deleteLinkValueV2AfterChecks(
    grant: Grant[ValueToDelete],
    link: LinkValueContentV2,
    propertyIri: SmartIri,
    deleteComment: Option[String],
    deleteDate: Option[Instant],
  ): Task[ValueIri] = {
    val resource = grant.target.resource
    val value    = grant.target.value
    for {
      linkUpdate <- LinkValueWrites.decrement(
                      grant,
                      propertyIri,
                      link.referredResourceIri,
                      value.attachedToUser,
                      value.permissions,
                      makeUnusedValueIri(resource.resourceIri),
                    )
      query <- DeleteLinkQuery
                 .build(
                   ProjectService.projectDataNamedGraphV2(resource.projectADM).value,
                   resource.resourceIri.value,
                   linkUpdate,
                   deleteComment,
                   deleteDate.getOrElse(Instant.now),
                   grant.caller.userIri,
                 )
                 .mapError(e => BadRequestException(e.message))
      _ <- triplestoreService.query(query)
    } yield linkUpdate.newLinkValueIri
  }

  /**
   * Deletes an ordinary value. For a text value, the `LinkValue`s of the links in its standoff markup are
   * decremented in the same update.
   */
  private def deleteOrdinaryValueV2AfterChecks(
    grant: Grant[ValueToDelete],
    propertyIri: SmartIri,
    deleteComment: Option[String],
    deleteDate: Option[Instant],
  ): Task[ValueIri] = {
    val resource                                             = grant.target.resource
    val value                                                = grant.target.value
    val linkUpdatesTask: Task[Seq[SparqlTemplateLinkUpdate]] = value.valueContent match {
      case textValue: TextValueContentV2 =>
        StandoffLinkTargets
          .resourceIris(
            StandoffLinkTargets.withValueIri(value.valueIri.value, textValue.standoffLinkTagTargetResourceIris),
          )
          .flatMap(removedTargets =>
            ZIO.foreach(removedTargets.toVector) { removedTargetResource =>
              LinkValueWrites.decrement(
                grant,
                OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
                removedTargetResource,
                KnoraUserRepo.builtIn.SystemUser.id.value,
                standoffLinkValuePermissions,
                makeUnusedValueIri(resource.resourceIri),
              )
            },
          )
      case _ => ZIO.succeed(Seq.empty)
    }
    for {
      linkUpdates  <- linkUpdatesTask
      sparqlUpdate <- DeleteValueQuery.build(
                        project = resource.projectADM,
                        resourceIri = resource.resourceIri,
                        propertyIri = PropertyIri.unsafeFrom(propertyIri),
                        valueIri = value.valueIri,
                        maybeDeleteComment = deleteComment,
                        linkUpdates = linkUpdates,
                        currentTime = deleteDate.getOrElse(Instant.now),
                        requestingUser = grant.caller.userIri,
                      )
      _ <- triplestoreService.query(sparqlUpdate)
    } yield value.valueIri
  }

  /**
   * When a property IRI is submitted for an update, makes an adjusted version of the submitted property:
   * if it's a link value property, substitutes the corresponding link property, whose objects we will need to query.
   *
   * @param submittedPropertyIri             the submitted property IRI, in the API v2 complex schema.
   * @param maybeSubmittedValueType          the submitted value type, if provided, in the API v2 complex schema.
   * @param propertyInfoForSubmittedProperty ontology information about the submitted property, in the internal schema.
   * @return ontology information about the adjusted property.
   */
  private def getAdjustedInternalPropertyInfo(
    submittedPropertyIri: SmartIri,
    maybeSubmittedValueType: Option[SmartIri],
    propertyInfoForSubmittedProperty: ReadPropertyInfoV2,
  ): Task[ReadPropertyInfoV2] =
    if (propertyInfoForSubmittedProperty.isLinkValueProp) {
      for {
        _ <- (maybeSubmittedValueType map { submittedValueType =>
               ZIO
                 .fail(
                   BadRequestException(
                     s"A value of type <$submittedValueType> cannot be an object of property <$submittedPropertyIri>",
                   ),
                 )
                 .when(submittedValueType.toString != OntologyConstants.KnoraApiV2Complex.LinkValue)
             }).getOrElse(ZIO.unit)
        info <- linkPropertyIfLinkValue(propertyInfoForSubmittedProperty)
      } yield info
    } else if (propertyInfoForSubmittedProperty.isLinkProp) {
      ZIO.fail(
        BadRequestException(
          s"Invalid property for creating a link value (submit a link value property instead): $submittedPropertyIri",
        ),
      )
    } else {
      ZIO.succeed(propertyInfoForSubmittedProperty)
    }

  private def linkPropertyIfLinkValue(p: ReadPropertyInfoV2): Task[ReadPropertyInfoV2] = p match
    case _ if p.isLinkValueProp =>
      val linkProp = p.propertyIri.fromLinkValuePropToLinkProp
      ontologyRepo
        .findProperty(linkProp)
        .someOrFail(
          NotFoundException(s"Link property not found: $linkProp, for link value property ${p.propertyIri} not found."),
        )
    case _ => ZIO.succeed(p)

  /**
   * Given a set of resource IRIs, checks that they point to Knora resources.
   * If not, fails with an exception.
   *
   * @param targetResourceIris   the IRIs to be checked.
   *
   * @param requestingUser       the user making the request.
   */
  private def checkResourceIris(targetResourceIris: Set[ResourceIri], requestingUser: User): Task[Unit] =
    ZIO
      .unless(targetResourceIris.isEmpty) {
        readResources.getResourcePreviewWithDeletedResource(
          resourceIris = targetResourceIris.toSeq,
          targetSchema = ApiV2Complex,
          requestingUser = requestingUser,
        )
      }
      .unit

  /**
   * Checks that a link value points to a resource with the correct type for the link property's object class constraint.
   *
   * @param linkPropertyIri       the IRI of the link property.
   * @param objectClassConstraint the object class constraint of the link property.
   * @param linkValueContent      the link value.
   *
   * @param requestingUser        the user making the request.
   */
  private def checkLinkPropertyObjectClassConstraint(
    linkPropertyIri: SmartIri,
    objectClassConstraint: SmartIri,
    linkValueContent: LinkValueContentV2,
    requestingUser: User,
  ): Task[Unit] =
    for {
      // Get a preview of the target resource, because we only need to find out its class and whether the user has permission to view it.
      resourcePreviewResponse <- readResources.getResourcePreviewWithDeletedResource(
                                   resourceIris = Seq(linkValueContent.referredResourceIri),
                                   targetSchema = ApiV2Complex,
                                   requestingUser = requestingUser,
                                 )

      // If we get a resource, we know the user has permission to view it.
      resource <- resourcePreviewResponse.toResource(linkValueContent.referredResourceIri)

      // Ask the ontology responder whether the resource's class is a subclass of the link property's object class constraint.
      subClassResponse <- ontologyResponder.checkSubClassV2(
                            subClassIri = resource.resourceClassIri,
                            superClassIri = objectClassConstraint,
                          )

      // If it isn't, fail with an exception.
      _ <-
        ZIO.when(!subClassResponse.isSubClass)(
          ZIO.fail(
            OntologyConstraintException(
              s"Resource <${linkValueContent.referredResourceIri}> cannot be the target of property <$linkPropertyIri>, because it is not a member of class <$objectClassConstraint>",
            ),
          ),
        )
    } yield ()

  /**
   * Checks that a non-link value has the correct type for a property's object class constraint.
   *
   * @param propertyIri           the IRI of the property that should point to the value.
   * @param objectClassConstraint the property's object class constraint.
   * @param valueContent          the value.
   */
  private def checkNonLinkPropertyObjectClassConstraint(
    propertyIri: SmartIri,
    objectClassConstraint: SmartIri,
    valueContent: ValueContentV2,
  ): Task[Unit] =
    // Is the value type the same as the property's object class constraint?
    ZIO
      .unless(objectClassConstraint == valueContent.valueType) {
        for {
          subClassResponse <- ontologyResponder.checkSubClassV2(
                                subClassIri = valueContent.valueType,
                                superClassIri = objectClassConstraint,
                              )

          // If it isn't, fail with an exception.
          _ <-
            ZIO.when(!subClassResponse.isSubClass) {
              ZIO.fail(
                OntologyConstraintException(
                  s"A value of type <${valueContent.valueType}> cannot be the target of property <$propertyIri>, because it is not a member of class <$objectClassConstraint>",
                ),
              )
            }

        } yield ()
      }
      .unit

  /**
   * Checks that a value to be updated has the correct type for the `knora-base:objectClassConstraint` of
   * the property that is supposed to point to it.
   *
   * @param propertyInfo         the property whose object class constraint is to be checked. If the value is a link value, this is the link property.
   * @param valueContent         the value to be updated.
   *
   * @param requestingUser       the user making the request.
   */
  private def checkPropertyObjectClassConstraint(
    propertyInfo: ReadPropertyInfoV2,
    valueContent: ValueContentV2,
    requestingUser: User,
  ): Task[Unit] = {
    val propertyIri = propertyInfo.entityInfoContent.propertyIri
    for {
      objectClassConstraint <-
        ZIO
          .fromOption(
            propertyInfo.entityInfoContent.getIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri),
          )
          .orElseFail(
            InconsistentRepositoryDataException(s"Property $propertyIri has no knora-base:objectClassConstraint"),
          )

      result <-
        valueContent match {
          // We're creating a link.
          case linkValueContent: LinkValueContentV2 =>
            ZIO.when(!propertyInfo.isLinkProp)(
              ZIO.fail(
                BadRequestException(s"Property <${propertyIri.toOntologySchema(ApiV2Complex)}> is not a link property"),
              ),
              // Check that the property whose object class constraint is to be checked is actually a link property.
            ) *> checkLinkPropertyObjectClassConstraint(
              propertyIri,
              objectClassConstraint,
              linkValueContent,
              requestingUser,
            )

          // We're referencing a Region through a region preview. The value's own type must satisfy the
          // property's object class constraint (as for any non-link value), AND the referenced resource must
          // exist, be viewable, and be a Region. The generic non-link check covers the former but never looks
          // at the target, so we run both.
          case regionPreview: RegionPreviewValueContentV2 =>
            checkNonLinkPropertyObjectClassConstraint(propertyIri, objectClassConstraint, regionPreview) *>
              checkRegionPreviewTarget(propertyIri, regionPreview, requestingUser)

          // We're creating an ordinary value.
          case otherValue =>
            // Check that its type is valid for the property's object class constraint.
            checkNonLinkPropertyObjectClassConstraint(propertyIri, objectClassConstraint, otherValue)
        }
    } yield result
  }

  /**
   * Checks that a region preview value points to a resource that exists, is viewable by the user, and is a Region.
   * The relevant object class constraint is `isRegionPreviewOf`'s (knora-base:Region), which is what we check
   * here; the region-preview property's own object constraint is the value type RegionPreviewValue, not Region.
   *
   * @param propertyIri    the IRI of the region-preview property.
   * @param regionPreview  the region preview value.
   * @param requestingUser the user making the request.
   */
  private def checkRegionPreviewTarget(
    propertyIri: SmartIri,
    regionPreview: RegionPreviewValueContentV2,
    requestingUser: User,
  ): Task[Unit] =
    for {
      // A preview of the target resource is enough: we only need its class and whether the user may view it.
      resourcePreviewResponse <- readResources.getResourcePreviewWithDeletedResource(
                                   resourceIris = Seq(regionPreview.regionIri),
                                   targetSchema = ApiV2Complex,
                                   requestingUser = requestingUser,
                                 )
      resource         <- resourcePreviewResponse.toResource(regionPreview.regionIri)
      regionClass       = OntologyConstants.KnoraBase.Region.toSmartIri
      subClassResponse <- ontologyResponder.checkSubClassV2(
                            subClassIri = resource.resourceClassIri.toOntologySchema(InternalSchema),
                            superClassIri = regionClass,
                          )
      _ <-
        ZIO.when(!subClassResponse.isSubClass)(
          ZIO.fail(
            OntologyConstraintException(
              s"Resource <${regionPreview.regionIri}> cannot be referenced by property <$propertyIri>, because it is not a member of class <$regionClass>",
            ),
          ),
        )
    } yield ()

  /**
   * The permissions that are granted by every `knora-base:LinkValue` describing a standoff link.
   */
  private lazy val standoffLinkValuePermissions: String = {
    val permissions: Set[PermissionADM] = Set(
      PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraUserRepo.builtIn.SystemUser.id.value),
      PermissionADM.from(Permission.ObjectAccess.View, KnoraGroupRepo.builtIn.UnknownUser.id.value),
    )

    PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)
  }

  /** The IRI of a new value version: the custom IRI, checked to be unused, or a new random one. */
  private def newValueIri(custom: Option[SmartIri], resourceIri: ResourceIri): Task[ValueIri] =
    iriService
      .checkOrCreateEntityIri(custom, ValueIri.makeNew(resourceIri).value)
      .flatMap(s => ZIO.fromEither(ValueIri.from(s)).mapError(BadRequestException.apply))

  /**
   * A convenience method for generating an unused random value IRI.
   *
   * @param resourceIri the IRI of the containing resource.
   * @return the new value IRI.
   */
  private def makeUnusedValueIri(resourceIri: ResourceIri): Task[ValueIri] =
    iriService
      .makeUnusedIri(ValueIri.makeNew(resourceIri).value)
      .flatMap(s => ZIO.fromEither(ValueIri.from(s)).mapError(BadRequestException.apply))
}

object ValuesResponderV2 {
  val layer = ZLayer.derive[ValuesResponderV2]

  /**
   * Make a new value UUID considering optional custom value UUID and custom value IRI.
   * If a custom UUID is given, this method checks that it matches the ending of a given IRI, if there was any.
   * If no custom UUID is given for a value, it checks if a custom value IRI is given or not. If yes, it extracts the
   * UUID from the given IRI. If no custom value IRI was given, it generates a random UUID.
   *
   * @param maybeCustomIri  the optional value IRI.
   * @param maybeCustomUUID the optional value UUID.
   * @return the new value UUID.
   */
  def makeNewValueUUID(
    maybeCustomIri: Option[SmartIri],
    maybeCustomUUID: Option[UUID],
  ): IO[BadRequestException, UUID] =
    (maybeCustomIri, maybeCustomUUID) match {
      case (Some(customIri: SmartIri), Some(customValueUUID)) => combineCustoms(customIri, customValueUUID)
      case (None, Some(customValueUUID))                      => ZIO.succeed(customValueUUID)
      case (Some(customIri), None)                            =>
        ZIO.fromOption(customIri.getUuid).orElseFail(BadRequestException(s"Invalid UUID in IRI: $customIri"))
      case (None, None) => Random.nextUUID

    }

  private def combineCustoms(iri: SmartIri, uuid: UUID): IO[BadRequestException, UUID] =
    if (iri.getUuid.contains(uuid)) ZIO.succeed(uuid)
    else
      ZIO.fail(
        BadRequestException(
          s"Given custom IRI $iri should contain the given custom UUID ${UuidUtil.base64Encode(uuid)}.",
        ),
      )
}
