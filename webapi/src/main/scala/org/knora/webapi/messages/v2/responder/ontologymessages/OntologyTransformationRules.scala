/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.ApiV2Simple
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo

/**
 * A trait for objects that provide rules for converting an ontology from the internal schema to an external schema.
 * * See also [[OntologyConstants.CorrespondingIris]].
 */
trait OntologyTransformationRules {

  /**
   * The metadata to be used for the transformed ontology.
   */
  val ontologyMetadata: OntologyMetadataV2

  /**
   * Properties to remove from the ontology before converting it to the target schema.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  val internalPropertiesToRemove: Set[SmartIri]

  /**
   * Classes to remove from the ontology before converting it to the target schema.
   */
  val internalClassesToRemove: Set[SmartIri]

  /**
   * After the ontology has been converted to the target schema, these cardinalities must be
   * added to the specified classes.
   */
  val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]]

  /**
   * Classes that need to be added to the ontology after converting it to the target schema.
   */
  val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2]

  /**
   * Properties that need to be added to the ontology after converting it to the target schema.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2]
}

/**
 * Finds the appropriate [[OntologyTransformationRules]] for an ontology and a target schema.
 */
object OntologyTransformationRules {

  /**
   * Given an ontology IRI and a target ontology schema, returns the [[OntologyTransformationRules]] describing how to
   * convert the ontology to the target schema.
   *
   * @param ontologyIri  the IRI of the ontology being transformed.
   * @param targetSchema the target ontology schema.
   * @return the appropriate [[OntologyTransformationRules]].
   */
  def getTransformationRules(ontologyIri: SmartIri, targetSchema: ApiV2Schema): OntologyTransformationRules =
    targetSchema match {
      case ApiV2Simple  => KnoraBaseToApiV2SimpleTransformationRules
      case ApiV2Complex => KnoraBaseToApiV2ComplexTransformationRules
    }
}
