/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.domain

import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

/** One planned write of a bulk edit, applied by [[org.knora.webapi.slice.resources.service.BatchEditService]]. */
enum BatchEdit {

  /** Sets the label; with `expected`, only while the label still is that text. */
  case SetLabel(resource: ResourceIri, expected: Option[String], newLabel: String)

  /**
   * Creates a value when `value` is empty, otherwise updates that value; with `expected`, only while the value still
   * holds it (the plain text, or the list node IRI). A create with `expectEmpty` is only written while the property
   * holds no value other than those created earlier in the same batch.
   */
  case SetValue(
    resource: ResourceIri,
    property: PropertyIri,
    value: Option[ValueIri],
    expected: Option[String],
    expectEmpty: Boolean,
    newValue: String,
  )
}

enum BatchEditFailure {
  case Conflict, Forbidden, NotFound, Invalid, Error
}

enum BatchEditOutcome {

  /** `valueIri` is the created value or the new value version; none for a label. */
  case Applied(valueIri: Option[ValueIri])
  case Failed(failure: BatchEditFailure, message: String)
}
