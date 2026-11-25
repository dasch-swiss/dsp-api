/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

/**
 * What a v2 resource or value write asks [[ObjectAccess]] for. The case fixes the permission level the gate requires,
 * so no caller passes a level, and `T` is the target the write receives in its [[Grant]].
 */
sealed trait AccessRequest[T]

object AccessRequest {

  /** A request on an existing resource, which the gate locks and loads. */
  sealed trait ObjectRequest[T] extends AccessRequest[T] {
    def resource: ResourceIri
  }

  /** Modify on the resource. */
  final case class CreateValue(resource: ResourceIri, property: PropertyIri) extends ObjectRequest[ValueToCreate]

  /**
   * View on the resource; Modify on the value, or ChangeRights when `permissions` changes them; Modify on the
   * resource too for a link value.
   */
  final case class UpdateValue(
    resource: ResourceIri,
    property: PropertyIri,
    value: ValueIri,
    permissions: PermissionsChange,
  ) extends ObjectRequest[ValueToUpdate]

  /** View on the resource; ChangeRights on the value. */
  final case class ChangeValueRights(resource: ResourceIri, property: PropertyIri, value: ValueIri)
      extends ObjectRequest[ValueToRescope]

  /** View on the resource; Delete on the value. */
  final case class DeleteValue(resource: ResourceIri, property: PropertyIri, value: ValueIri)
      extends ObjectRequest[ValueToDelete]

  /** View on the resource; Delete on the value; the resource must be in `project`, the request's shortcode. */
  final case class EraseValue(
    resource: ResourceIri,
    property: PropertyIri,
    value: ValueIri,
    project: KnoraProject,
    onlyHistory: Boolean,
  ) extends ObjectRequest[ValueToErase]

  /** Modify on the resource. */
  final case class ReorderValues(resource: ResourceIri, property: PropertyIri) extends ObjectRequest[ValuesToReorder]

  /** Modify on the resource; ChangeRights when `permissions` changes them. */
  final case class UpdateResource(resource: ResourceIri, permissions: PermissionsChange)
      extends ObjectRequest[ResourceToUpdate]

  /** Delete on the resource. */
  final case class DeleteResource(resource: ResourceIri) extends ObjectRequest[ResourceToDelete]

  /** The project's create permission for the class. */
  final case class CreateResource(project: KnoraProject, resourceClass: ResourceClassIri)
      extends AccessRequest[NewResource]
}

/** The permissions an update submits; a literal's syntax is validated before the gate. */
enum PermissionsChange {
  case Unchanged
  case To(literal: String)
}
