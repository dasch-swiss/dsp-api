/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import dsp.constants.SalsahGui
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.slice.ontology.domain.model.Cardinality

/** Builders for the `ReadOntologyV2` inputs of `ProjectOntologyShapes.generate`. */
object ProjectOntologyFixtures {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  def iri(s: String): SmartIri = sf.toSmartIri(s)

  def ontology(ontologyIri: String, classes: Seq[ReadClassInfoV2], props: Seq[ReadPropertyInfoV2]): ReadOntologyV2 =
    ReadOntologyV2(
      OntologyMetadataV2(iri(ontologyIri.stripSuffix("#"))),
      classes.map(c => c.entityInfoContent.classIri -> c).toMap,
      props.map(p => p.entityInfoContent.propertyIri -> p).toMap,
    )

  /** The cardinalities that every resource class inherits from `knora-base:Resource`. */
  val resourceInheritedCardinalities: Map[String, Cardinality] = {
    import Cardinality.*
    Map(
      "http://www.w3.org/2000/01/rdf-schema#label"          -> ExactlyOne,
      KnoraBase.IsDeleted                                   -> ExactlyOne,
      KnoraBase.AttachedToUser                              -> ExactlyOne,
      KnoraBase.AttachedToProject                           -> ExactlyOne,
      KnoraBase.HasPermissions                              -> ExactlyOne,
      KnoraBase.CreationDate                                -> ExactlyOne,
      KnoraBase.LastModificationDate                        -> ZeroOrOne,
      KnoraBase.DeleteDate                                  -> ZeroOrOne,
      KnoraBase.DeletedBy                                   -> ZeroOrOne,
      KnoraBase.DeleteComment                               -> ZeroOrOne,
      KnoraBase.HasStandoffLinkTo                           -> Unbounded,
      KnoraBase.HasStandoffLinkToValue                      -> Unbounded,
      KnoraBase.HasResourceAuthorship                       -> Unbounded,
      KnoraBase.KnoraBasePrefixExpansion + "hasDescription" -> Unbounded,
    )
  }

  def resourceClass(
    classIri: String,
    direct: Map[String, Cardinality] = Map.empty,
    inherited: Map[String, Cardinality] = Map.empty,
    resourceProps: Set[String] = Set.empty,
  ): ReadClassInfoV2 = {
    def card(m: Map[String, Cardinality]) = m.map((k, v) => iri(k) -> KnoraCardinalityInfo(v))
    ReadClassInfoV2(
      ClassInfoContentV2(iri(classIri), directCardinalities = card(direct), ontologySchema = InternalSchema),
      allBaseClasses = Seq(iri(classIri)),
      isResourceClass = true,
      inheritedCardinalities = card(inherited),
      knoraResourceProperties = resourceProps.map(iri),
    )
  }

  def property(
    propIri: String,
    objectClass: Option[String],
    isLinkValueProp: Boolean = false,
    guiAttributes: Seq[String] = Seq.empty,
  ): ReadPropertyInfoV2 = {
    val constraint = objectClass.map { c =>
      val p = iri(KnoraBase.ObjectClassConstraint)
      p -> PredicateInfoV2(p, Seq(SmartIriLiteralV2(iri(c))))
    }.toMap
    val gui = Option
      .when(guiAttributes.nonEmpty) {
        val p = iri(SalsahGui.GuiAttribute)
        p -> PredicateInfoV2(p, guiAttributes.map(PlainStringLiteralV2.apply))
      }
      .toMap
    val predicates = constraint ++ gui
    ReadPropertyInfoV2(
      PropertyInfoContentV2(iri(propIri), predicates, ontologySchema = InternalSchema),
      isResourceProp = true,
      isLinkValueProp = isLinkValueProp,
    )
  }
}
