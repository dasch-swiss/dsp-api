package org.knora.webapi.slice.resourceinfo.repo

import org.knora.webapi.IRI

import java.time.Instant

case class ResourceInfo(
  iri: IRI,
  creationDate: Instant,
  lastModificationDate: Instant,
  deleteDate: Option[Instant],
  isDeleted: Boolean
)
