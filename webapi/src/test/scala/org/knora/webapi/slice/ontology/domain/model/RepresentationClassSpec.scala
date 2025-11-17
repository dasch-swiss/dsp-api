/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri

object RepresentationClassSpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  val spec = suite("RepresentationClass")(
    test("RepresentationClass instances should have correct IRI") {
      assertTrue(
        RepresentationClass.ArchiveRepresentation.iri.contains(KnoraBase.ArchiveRepresentation),
        RepresentationClass.AudioRepresentation.iri.contains(KnoraBase.AudioRepresentation),
        RepresentationClass.DDDRepresentation.iri.contains(KnoraBase.DDDRepresentation),
        RepresentationClass.DocumentRepresentation.iri.contains(KnoraBase.DocumentRepresentation),
        RepresentationClass.MovingImageRepresentation.iri.contains(KnoraBase.MovingImageRepresentation),
        RepresentationClass.StillImageRepresentation.iri.contains(KnoraBase.StillImageRepresentation),
        RepresentationClass.TextRepresentation.iri.contains(KnoraBase.TextRepresentation),
        // WithoutRepresentation has no IRI, we could use knora-base:Resource as a placeholder,
        // but it's better to be explicit about the absence of an IRI.
        RepresentationClass.WithoutRepresentation.iri.isEmpty,
      )
    },
    test("from(SmartIri) should correctly map IRIs to RepresentationClass") {
      val tests = Seq(
        (KnoraBase.ArchiveRepresentation, RepresentationClass.ArchiveRepresentation),
        (KnoraBase.AudioRepresentation, RepresentationClass.AudioRepresentation),
        (KnoraBase.DDDRepresentation, RepresentationClass.DDDRepresentation),
        (KnoraBase.DocumentRepresentation, RepresentationClass.DocumentRepresentation),
        (KnoraBase.MovingImageRepresentation, RepresentationClass.MovingImageRepresentation),
        (KnoraBase.StillImageRepresentation, RepresentationClass.StillImageRepresentation),
        (KnoraBase.TextRepresentation, RepresentationClass.TextRepresentation),
      )
      check(Gen.fromIterable(tests))((iriStr, reprClass) =>
        val iri = iriStr.toSmartIri
        assertTrue(RepresentationClass.from(iri.toComplexSchema).contains(reprClass)),
      )
    },
    test("from(SmartIri) should return None for unknown IRI") {
      val iri = ResourceClassIri.unsafeFrom(KnoraBase.StandoffTag.toSmartIri)
      // Given an resource class IRI that does not directly correspond to a representation class
      // the from method should return None and NOT WithoutRepresentation.
      // This is because WithoutRepresentation is a specific case indicating the absence of a representation class.
      // And by only looking at the IRI we cannot determine that, we would have to look at the resource class definition
      // in the ontology. For that we have the OntologyRepo.
      assertTrue(RepresentationClass.from(iri).isEmpty)
    },
  )
}
