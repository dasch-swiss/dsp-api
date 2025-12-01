/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model
import sttp.tapir.Schema
import zio.json.*

import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri

enum RepresentationClass(val iri: Option[String]):
  case ArchiveRepresentation     extends RepresentationClass(Some(KnoraBase.ArchiveRepresentation))
  case AudioRepresentation       extends RepresentationClass(Some(KnoraBase.AudioRepresentation))
  case DDDRepresentation         extends RepresentationClass(Some(KnoraBase.DDDRepresentation))
  case DocumentRepresentation    extends RepresentationClass(Some(KnoraBase.DocumentRepresentation))
  case MovingImageRepresentation extends RepresentationClass(Some(KnoraBase.MovingImageRepresentation))
  case StillImageRepresentation  extends RepresentationClass(Some(KnoraBase.StillImageRepresentation))
  case TextRepresentation        extends RepresentationClass(Some(KnoraBase.TextRepresentation))
  case WithoutRepresentation     extends RepresentationClass(None)

object RepresentationClass {
  given JsonCodec[RepresentationClass] = DeriveJsonCodec.gen[RepresentationClass]
  given Schema[RepresentationClass]    = Schema.derivedEnumeration[RepresentationClass].defaultStringBased

  def from(iri: ResourceClassIri): Option[RepresentationClass] = from(iri.smartIri)
  def from(iri: SmartIri): Option[RepresentationClass]         =
    RepresentationClass.values.find(_.iri.contains(iri.toInternalSchema.toIri))
}
