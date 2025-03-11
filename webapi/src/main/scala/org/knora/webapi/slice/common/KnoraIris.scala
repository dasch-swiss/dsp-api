/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import eu.timepit.refined.types.string.NonEmptyString

import org.knora.webapi.OntologySchema
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.ontology.domain.model.OntologyName
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object KnoraIris {

  opaque type ResourceId = NonEmptyString
  opaque type ValueId    = NonEmptyString
  opaque type EntityName = NonEmptyString

  trait KnoraIri { self =>
    def smartIri: SmartIri
    override def toString: String                           = self.smartIri.toString
    final def toInternalIri: InternalIri                    = self.smartIri.toInternalIri
    final def toComplexSchema: SmartIri                     = self.smartIri.toComplexSchema
    final def toInternalSchema: SmartIri                    = self.smartIri.toInternalSchema
    final def toOntologySchema(s: OntologySchema): SmartIri = self.smartIri.toOntologySchema(s)
  }

  // PropertyIri and ResourceClassIri currently have the same constraint
  // i.e. they only have to be a KnoraEntityIri (SmartIri.isKnoraEntityIri)
  // but they are kept separate for clarity of method signatures

  // Both Iris have an internal and external representation, thus we provide
  // functions which create these from a SmartIri. `from` accepts any SmartIri. `fromApiV2Complex` only accepts
  // SmartIris that are part of the API v2 complex schema.

  final case class PropertyIri private (smartIri: SmartIri) extends KnoraIri {
    def fromLinkValuePropToLinkProp: PropertyIri = PropertyIri.unsafeFrom(smartIri.fromLinkValuePropToLinkProp)
    def fromLinkPropToLinkValueProp: PropertyIri = PropertyIri.unsafeFrom(smartIri.fromLinkPropToLinkValueProp)
  }

  object PropertyIri {

    def unsafeFrom(iri: SmartIri): PropertyIri = from(iri).fold(e => throw IllegalArgumentException(e), identity)

    def fromApiV2Complex(iri: SmartIri): Either[String, PropertyIri] =
      if iri.isApiV2ComplexSchema then from(iri)
      else Left(s"Not an API v2 complex IRI ${iri.toString}")

    def from(iri: SmartIri): Either[String, PropertyIri] = Right(PropertyIri(iri))
  }

  final case class ValueIri private (
    smartIri: SmartIri,
    shortcode: Shortcode,
    resourceId: ResourceId,
    valueId: ValueId,
  ) extends KnoraIri {
    def sameResourceAs(other: ValueIri): Boolean =
      this.shortcode == other.shortcode && this.resourceId == other.resourceId
  }

  final case class ResourceClassIri private (smartIri: SmartIri) extends KnoraIri {
    def ontologyIri: OntologyIri = OntologyIri.unsafeFrom(smartIri.getOntologyFromEntity)
  }

  object ResourceClassIri {
    def unsafeFrom(iri: SmartIri): ResourceClassIri = from(iri).fold(e => throw IllegalArgumentException(e), identity)

    def fromApiV2Complex(iri: SmartIri): Either[String, ResourceClassIri] =
      if iri.isApiV2ComplexSchema then from(iri)
      else Left(s"Not an API v2 complex IRI ${iri.toString}")

    def from(iri: SmartIri): Either[String, ResourceClassIri] = Right(ResourceClassIri(iri))
  }

  // `ValueIri` and `ResourceIri` have no different internal representation.
  // Thus, we only provide functions which create these from a `SmartIri`.
  // The `fromApiV2Complex` is not required as these Iris are not part of the API v2 complex schema.
  object ValueIri {

    def unsafeFrom(iri: SmartIri): ValueIri = from(iri).fold(e => throw IllegalArgumentException(e), identity)

    def from(iri: SmartIri): Either[String, ValueIri] =
      if iri.isKnoraValueIri then
        // the following three calls are safe because we checked that the
        // shortcode, resourceId and valueId are present in isKnoraValueIri
        val shortcode  = iri.getProjectShortcode.getOrElse(throw Exception())
        val resourceId = NonEmptyString.unsafeFrom(iri.getResourceID.getOrElse(throw Exception()))
        val valueId    = NonEmptyString.unsafeFrom(iri.getValueID.getOrElse(throw Exception()))
        Right(ValueIri(iri, shortcode, resourceId, valueId))
      else Left(s"<$iri> is not a Knora value IRI")
  }

  final case class ResourceIri private (smartIri: SmartIri, shortcode: Shortcode, resourceId: ResourceId)
      extends KnoraIri
  object ResourceIri {

    def unsafeFrom(iri: SmartIri): ResourceIri = from(iri).fold(e => throw IllegalArgumentException(e), identity)

    def from(iri: SmartIri): Either[String, ResourceIri] =
      if iri.isKnoraResourceIri then
        // the following two calls are safe because we checked that the
        // shortcode and resourceId are present in isKnoraResourceIri
        val shortcode  = iri.getProjectShortcode.getOrElse(throw Exception())
        val resourceId = NonEmptyString.unsafeFrom(iri.getResourceID.getOrElse(throw Exception()))
        Right(ResourceIri(iri, shortcode, resourceId))
      else Left(s"<$iri> is not a Knora resource IRI")
  }

  final case class OntologyIri private (smartIri: SmartIri) extends KnoraIri { self =>
    def makeEntityIri(name: String): SmartIri = smartIri.makeEntityIri(name)
    def makeClass(name: String): ResourceClassIri =
      ResourceClassIri.unsafeFrom(self.makeEntityIri(name))
    def makeProperty(name: String): PropertyIri =
      PropertyIri.unsafeFrom(self.makeEntityIri(name))
    def ontologyName: OntologyName = smartIri.getOntologyName
    def isInternal: Boolean        = ontologyName.isInternal
    def isExternal: Boolean        = !isInternal
    def isBuiltIn: Boolean         = ontologyName.isBuiltIn
    def isShared: Boolean          = toInternalSchema.toIri.split("/")(4) == "shared"
  }
  object OntologyIri {
    def makeNew(
      ontologyName: OntologyName,
      isShared: Boolean,
      shortcode: Option[Shortcode],
      sf: StringFormatter,
    ): OntologyIri = {
      val sb = new StringBuilder()
      sb.append(OntologyConstants.KnoraInternal.InternalOntologyStart + "/")
      if (isShared) { sb.append("shared/") }
      val sharedOntologiesShortcode = "0000"
      shortcode.map(_.value).filter(v => v != sharedOntologiesShortcode).foreach(v => sb.append(s"$v/"))
      sb.append(ontologyName.value)
      unsafeFrom(sf.toSmartIri(sb.toString()))
    }

    def unsafeFrom(iri: SmartIri): OntologyIri = from(iri).fold(e => throw IllegalArgumentException(e), identity)

    def fromApiV2Complex(iri: SmartIri): Either[String, OntologyIri] =
      from(iri).filterOrElse(_.smartIri.isApiV2ComplexSchema, s"Not an API v2 complex IRI ${iri.toString}")

    def from(iri: SmartIri): Either[String, OntologyIri] =
      if iri.isKnoraOntologyIri then Right(OntologyIri(iri))
      else Left(s"<$iri> is not a Knora ontology IRI")
  }
}
