/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.resources

import zio.*

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*

object CheckObjectClassConstraints {

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
  def apply(
    propertyIri: SmartIri,
    valuesToCreate: Seq[CreateValueInNewResourceV2],
    linkTargetClasses: Map[IRI, SmartIri],
    entityInfo: EntityInfoGetResponseV2,
    clientResourceIDs: Map[IRI, String],
    resourceIdForErrorMsg: IRI,
  )(implicit
    stringFormatter: StringFormatter,
  ): Task[Unit] =
    for {
      propertyInfo <- ZIO.succeed(entityInfo.propertyInfoMap(propertyIri))

      // Don't accept link properties.
      _ <- ZIO.when(propertyInfo.isLinkProp)(ZIO.fail(invalidPropUseLinkProp(resourceIdForErrorMsg, propertyIri)))

      // Get the property's object class constraint. If this is a link value property, we want the object
      // class constraint of the corresponding link property instead.
      propertyInfoForObjectClassConstraint = if (propertyInfo.isLinkValueProp)
                                               entityInfo.propertyInfoMap(propertyIri.fromLinkValuePropToLinkProp)
                                             else
                                               propertyInfo

      propertyIriForObjectClassConstraint = propertyInfoForObjectClassConstraint.entityInfoContent.propertyIri

      objectClassConstraint <-
        ZIO
          .fromOption(
            propertyInfoForObjectClassConstraint.entityInfoContent.getIriObject(
              OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
            ),
          )
          .orElseFail(objectTypeMissing(propertyIriForObjectClassConstraint))

      // Check each value.
      _ <-
        ZIO.foreachDiscard(valuesToCreate) { valueToCreate =>
          valueToCreate.valueContent match {
            case linkValueContentV2: LinkValueContentV2 =>
              // It's a link value.
              for {
                _ <-
                  ZIO.when(!propertyInfo.isLinkValueProp) {
                    propertyRequiresValue(resourceIdForErrorMsg, propertyIri, objectClassConstraint)
                  }

                // Does the resource that's the target of the link belongs to a subclass of the
                // link property's object class constraint?
                linkTargetClass     = linkTargetClasses(linkValueContentV2.referredResourceIri)
                linkTargetClassInfo = entityInfo.classInfoMap(linkTargetClass)
                _ <- ZIO.when(!linkTargetClassInfo.allBaseClasses.contains(objectClassConstraint)) {
                       // No. If the target resource already exists, use its IRI in the error message.
                       // Otherwise, use the client's ID for the resource.
                       ZIO.fail(
                         propertyInvalid(
                           resourceIdForErrorMsg,
                           linkValueContentV2,
                           clientResourceIDs,
                           propertyIriForObjectClassConstraint,
                           objectClassConstraint,
                         ),
                       )
                     }
              } yield ()

            case other: ValueContentV2 =>
              // It's not a link value. Check that its type is equal to the property's object class constraint.
              ZIO.when(other.valueType != objectClassConstraint) {
                propertyRequiresValue(resourceIdForErrorMsg, propertyIri, objectClassConstraint)
              }
          }
        }
    } yield ()

  private def invalidPropUseLinkProp(
    resourceIDForErrorMsg: IRI,
    propertyIri: SmartIri,
  ): BadRequestException =
    BadRequestException(
      s"${resourceIDForErrorMsg}Invalid property <${propertyIri.toComplexSchema}>. Use a link value property to submit a link.",
    )

  private def objectTypeMissing(
    property: SmartIri,
  ): InconsistentRepositoryDataException =
    InconsistentRepositoryDataException(s"Property <$property> has no knora-api:objectType")

  private def propertyRequiresValue(
    resourceIdForErrorMsg: IRI,
    propertyIri: SmartIri,
    objectClassConstraint: SmartIri,
  ): Task[OntologyConstraintException] =
    ZIO.fail(
      OntologyConstraintException(
        s"${resourceIdForErrorMsg}Property <${propertyIri.toComplexSchema}> requires a value of type <${objectClassConstraint.toComplexSchema}>",
      ),
    )

  private def propertyInvalid(
    resourceIdForErrorMsg: IRI,
    linkValueContentV2: LinkValueContentV2,
    clientResourceIDs: Map[IRI, String],
    propertyIriForObjectClassConstraint: SmartIri,
    objectClassConstraint: SmartIri,
  ): OntologyConstraintException = {
    val resourceID = if (linkValueContentV2.referredResourceExists) {
      s"<${linkValueContentV2.referredResourceIri}>"
    } else {
      s"'${clientResourceIDs.apply(linkValueContentV2.referredResourceIri)}'" // unsafe apply, present before refactoring
    }

    OntologyConstraintException(
      s"${resourceIdForErrorMsg}Resource $resourceID cannot be the object of property <${propertyIriForObjectClassConstraint.toComplexSchema}>, because it does not belong to class <${objectClassConstraint.toComplexSchema}>",
    )
  }
}
