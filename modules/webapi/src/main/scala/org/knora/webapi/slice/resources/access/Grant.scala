/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import zio.*

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.OntologyConstraintException
import dsp.errors.RequestRejectedException
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri

/**
 * Proof that [[ObjectAccess]] decided for `caller` and the request's level. `target` is the caller's view: values
 * hidden from the caller are left out. An update's target carries the permissions change the gate decided on; the
 * write takes them from there, never from the request. A grant is valid only inside the `write` closure the gate
 * passed it to; kept beyond it, it outlives the write lock. Plain class, so there is no `copy`, `apply` or `unapply`.
 */
final class Grant[+T] private[access] (
  val caller: User,
  val project: KnoraProject,
  val target: T,
  private[access] val unfiltered: Unfiltered,
) {

  /** Runs an integrity rule of the access package over the unfiltered resource; only its verdict leaves. */
  def verify(rule: Unfiltered => Option[IntegrityViolation]): IO[RequestRejectedException, Unit] =
    ZIO.foreachDiscard(rule(unfiltered))(violation => ZIO.fail(violation.toException))
}

/** The resource as loaded by `SystemUser`, hidden values included; readable only inside the access package. */
final class Unfiltered private[access] (private[access] val resource: Option[ReadResourceV2])

/** The verdict of an integrity rule. Its message is fixed text plus IRIs of the request, never loaded data. */
enum IntegrityViolation {
  case CardinalityViolated(resource: ResourceIri, property: PropertyIri)
  case ValueSetMismatch(resource: ResourceIri, property: PropertyIri)
  case DuplicateLink(resource: ResourceIri, property: PropertyIri, target: ResourceIri)

  private[access] def toException: RequestRejectedException = this match {
    case CardinalityViolated(resource, property) =>
      OntologyConstraintException(
        s"The cardinality of property <$property> does not allow this change to resource <$resource>",
      )
    case ValueSetMismatch(resource, property) =>
      BadRequestException(
        s"The submitted values do not match the values of property <$property> of resource <$resource>",
      )
    case DuplicateLink(resource, property, target) =>
      DuplicateValueException(s"Resource <$resource> already has a link to <$target> via property <$property>")
  }
}

final case class ValueToCreate private[access] (resource: ReadResourceV2)
final case class ValueToUpdate private[access] (
  resource: ReadResourceV2,
  value: ReadValueV2,
  permissions: PermissionsChange,
)
final case class ValueToRescope private[access] (resource: ReadResourceV2, value: ReadValueV2)
final case class ValueToDelete private[access] (resource: ReadResourceV2, value: ReadValueV2)
final case class ValueToErase private[access] (resource: ReadResourceV2, value: ReadValueV2)
final case class ValuesToReorder private[access] (
  resource: ReadResourceV2,
  property: PropertyIri,
  visibleValues: Seq[ReadValueV2],
)
final case class ResourceToUpdate private[access] (resource: ReadResourceV2, permissions: PermissionsChange)
final case class ResourceToDelete private[access] (resource: ReadResourceV2)
final case class ResourceToErase private[access] (resource: ReadResourceV2)
final case class NewResource private[access] (project: KnoraProject, resourceClass: ResourceClassIri)
