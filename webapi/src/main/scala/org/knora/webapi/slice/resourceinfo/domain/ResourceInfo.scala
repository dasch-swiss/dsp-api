/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

import java.time.Instant

import org.knora.webapi.IRI

case class ResourceInfo(
  iri: IRI,
  creationDate: Instant,
  lastModificationDate: Option[Instant],
  deleteDate: Option[Instant],
  isDeleted: Boolean
)
object ResourceInfo {
  def apply(iri: IRI, creationDate: Instant, lastModificationDate: Option[Instant]): ResourceInfo =
    ResourceInfo(iri, creationDate, lastModificationDate, deleteDate = None, isDeleted = false)
  def apply(iri: IRI, creationDate: Instant, lastModificationDate: Option[Instant], deleteDate: Instant): ResourceInfo =
    ResourceInfo(iri, creationDate, lastModificationDate, Some(deleteDate), isDeleted = true)
}
