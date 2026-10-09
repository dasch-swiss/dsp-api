/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.Task

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.common.ResourceIri

trait FairResources {

  /**
   * The resource as the anonymous user sees it, or None when it is missing, deleted, or anonymous may not see it at
   * all. Any other failure fails the effect.
   */
  def readAsAnonymous(iri: ResourceIri): Task[Option[FairResource]]
}

final case class FairResource(
  iri: ResourceIri,
  label: String,
  classIri: String,
  userPermission: Permission.ObjectAccess,
  creationDate: Instant,
  lastModificationDate: Option[Instant],
  authorship: List[String],
  description: Option[String],
  file: Option[FairFileValue],
)

final case class FairFileValue(
  kind: FairFileKind,
  userPermission: Permission.ObjectAccess,
  typeOfData: Option[String],
  assetId: Option[String],
  licenseIri: Option[String],
  authorship: List[String],
)

enum FairFileKind {
  case Stored
  case ExternalIiif
}
