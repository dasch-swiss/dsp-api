/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import zio.*

import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.api.v3.*

final class OntologyMappingEndpoints(base: V3BaseEndpoint) extends EndpointHelper {

  private val ontologiesBase = ApiV3.basePath / "ontologies"
  private val mappingsTag    = List("Ontology Mappings")

  /** PUT class mapping: add/replace rdfs:subClassOf triples pointing to external IRIs */
  val putClassMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.class_not_found),
        badRequestVariant,
      ),
    )
    .put
    .in(ontologiesBase / path[IriDto].name("ontologyIri") / "classes" / path[IriDto].name("classIri") / "mapping")
    .in(jsonBody[AddClassMappingsRequest])
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[ClassMappingResponse])
    .tags(mappingsTag)
    .description(
      "Adds rdfs:subClassOf triples from the given class to the listed external IRIs. " +
        "Existing external super-class mappings are not removed - use DELETE first to replace them.",
    )

  /** DELETE class mapping: remove a single rdfs:subClassOf triple to an external IRI */
  val deleteClassMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.class_not_found),
        badRequestVariant,
      ),
    )
    .delete
    .in(ontologiesBase / path[IriDto].name("ontologyIri") / "classes" / path[IriDto].name("classIri") / "mapping")
    .in(query[IriDto]("mapping").description("The external IRI to remove from rdfs:subClassOf. Must be URL-encoded."))
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[ClassMappingResponse])
    .tags(mappingsTag)
    .description(
      "Removes a single rdfs:subClassOf triple from the given class to the specified external IRI. " +
        "Idempotent: deleting an absent triple is a no-op.",
    )

  /** PUT property mapping: add/replace rdfs:subPropertyOf triples pointing to external IRIs */
  val putPropertyMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.property_not_found),
        badRequestVariant,
      ),
    )
    .put
    .in(ontologiesBase / path[IriDto].name("ontologyIri") / "properties" / path[IriDto].name("propertyIri") / "mapping")
    .in(jsonBody[AddPropertyMappingsRequest])
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[PropertyMappingResponse])
    .tags(mappingsTag)
    .description(
      "Adds rdfs:subPropertyOf triples from the given property to the listed external IRIs. " +
        "Existing external super-property mappings are not removed - use DELETE first to replace them.",
    )

  /** DELETE property mapping: remove a single rdfs:subPropertyOf triple to an external IRI */
  val deletePropertyMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.property_not_found),
        badRequestVariant,
      ),
    )
    .delete
    .in(ontologiesBase / path[IriDto].name("ontologyIri") / "properties" / path[IriDto].name("propertyIri") / "mapping")
    .in(
      query[IriDto]("mapping").description("The external IRI to remove from rdfs:subPropertyOf. Must be URL-encoded."),
    )
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[PropertyMappingResponse])
    .tags(mappingsTag)
    .description(
      "Removes a single rdfs:subPropertyOf triple from the given property to the specified external IRI. " +
        "Idempotent: deleting an absent triple is a no-op.",
    )
}

object OntologyMappingEndpoints {
  val layer = ZLayer.derive[OntologyMappingEndpoints]
}
