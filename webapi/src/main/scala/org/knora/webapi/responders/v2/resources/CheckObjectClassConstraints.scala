/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.resources

import zio._

import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages._
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

import OntologyConstants.{KnoraBase => Constants}

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
    ontologyRepo: OntologyRepo,
  )(implicit
    stringFormatter: StringFormatter,
  ): Task[Unit] =
    for {
      propertyInfo      <- ZIO.succeed(entityInfo.propertyInfoMap(propertyIri))
      _                 <- ZIO.when(propertyInfo.isLinkProp)(ZIO.fail(invalidPropUseLinkProp(resourceIdForErrorMsg, propertyIri)))
      objectConstraints <- propertysObjectConstraint(propertyIri, entityInfo, propertyInfo, ontologyRepo)

      // Check each value.
      _ <-
        ZIO.foreachDiscard(valuesToCreate.map(_.valueContent)) {
          case linkValueContentV2: LinkValueContentV2 =>
            // It's a link value.
            for {
              _ <-
                ZIO.when(!propertyInfo.isLinkValueProp) {
                  propertyRequiresValue(resourceIdForErrorMsg, propertyIri, objectConstraints)
                }

              // Does the resource that's the target of the link belongs to a subclass of the
              // link property's object class constraint?
              linkTargetClass     = linkTargetClasses(linkValueContentV2.referredResourceIri)
              linkTargetClassInfo = entityInfo.classInfoMap(linkTargetClass)
              _ <-
                ZIO.when(!linkTargetClassInfo.allBaseClasses.exists(objectConstraints.contains)) {
                  // No. If the target resource already exists, use its IRI in the error message.
                  // Otherwise, use the client's ID for the resource.
                  ZIO.fail(
                    propertyInvalid(
                      resourceIdForErrorMsg,
                      linkValueContentV2,
                      clientResourceIDs,
                      propertyIri,
                      objectConstraints,
                    ),
                  )
                }
            } yield ()

          case other: ValueContentV2 =>
            // It's not a link value. Check that its type is equal to the property's object class constraint.
            ZIO.when(!objectConstraints.contains(other.valueType)) {
              propertyRequiresValue(resourceIdForErrorMsg, propertyIri, objectConstraints)
            }
        }
    } yield ()

  private def propertysObjectConstraint(
    propertyIri: SmartIri,
    entityInfo: EntityInfoGetResponseV2,
    propertyInfo: ReadPropertyInfoV2,
    ontologyRepo: OntologyRepo,
  )(implicit
    stringFormatter: StringFormatter,
  ): Task[List[SmartIri]] = {
    // * Represents an OWL property definition as returned in an API response.
    // Get the property's object class constraint. If this is a link value property, we want the object
    // class constraint of the corresponding link property instead.
    val propertyInfoLinked: ReadPropertyInfoV2 =
      if (propertyInfo.isLinkValueProp)
        entityInfo.propertyInfoMap(propertyIri.fromLinkValuePropToLinkProp)
      else
        propertyInfo

    for {
      objectConstraint <-
        ZIO
          .fromOption(
            propertyInfoLinked.entityInfoContent.getIriObject(Constants.ObjectClassConstraint.toSmartIri),
          )
          .orElseFail(objectTypeMissing(propertyInfoLinked.entityInfoContent.propertyIri))

      objectConstraintInfos <- ontologyRepo.findDirectSubclassesBy(objectConstraint.toInternalIri)
    } yield objectConstraint +: (objectConstraintInfos.map(_.entityInfoContent.classIri))
  }

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
    objectClassConstraints: List[SmartIri],
  ): Task[OntologyConstraintException] = {
    val constraints = objectClassConstraints.map(_.toComplexSchema).mkString(",")
    ZIO.fail(
      OntologyConstraintException(
        s"${resourceIdForErrorMsg}Property <${propertyIri.toComplexSchema}> requires a value of types: <${constraints}>",
      ),
    )
  }

  private def propertyInvalid(
    resourceIdForErrorMsg: IRI,
    linkValueContentV2: LinkValueContentV2,
    clientResourceIDs: Map[IRI, String],
    propertyIriForObjectClassConstraint: SmartIri,
    objectClassConstraints: List[SmartIri],
  ): OntologyConstraintException = {
    val resourceID = if (linkValueContentV2.referredResourceExists) {
      s"<${linkValueContentV2.referredResourceIri}>"
    } else {
      s"'${clientResourceIDs.apply(linkValueContentV2.referredResourceIri)}'" // unsafe apply, present before refactoring
    }

    val constraints = objectClassConstraints.map(_.toComplexSchema).mkString(",")
    OntologyConstraintException(
      s"${resourceIdForErrorMsg}Resource $resourceID cannot be the object of property <${propertyIriForObjectClassConstraint.toComplexSchema}>, because it does not belong to class <$constraints>",
    )
  }
}
