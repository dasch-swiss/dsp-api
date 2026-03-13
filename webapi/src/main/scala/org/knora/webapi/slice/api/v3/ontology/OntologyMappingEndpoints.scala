/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import zio.*

import org.knora.webapi.slice.api.v3.*

final class OntologyMappingEndpoints(base: V3BaseEndpoint) extends EndpointHelper {

  private val ontologiesBase = ApiV3.basePath / "ontologies"
  private val mappingsTag    = List("Ontology Mappings")

  /** F1 — PUT class mapping: add/replace rdfs:subClassOf triples pointing to external IRIs */
  val putClassMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.class_not_found),
        badRequestVariant,
        // HTTP 500 is declared for OpenAPI documentation completeness.
        // It is NOT produced by application logic (infrastructure failures use .orDie → fiber defect).
        internalServerErrorVariant,
      ),
    )
    .put
    .in(ontologiesBase / path[String]("ontologyIri") / "classes" / path[String]("classIri") / "mapping")
    .in(jsonBody[AddClassMappingsRequest])
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[ClassMappingResponse])
    .tags(mappingsTag)
    .description(
      "Adds rdfs:subClassOf triples from the given class to the listed external IRIs. " +
        "Existing external super-class mappings are not removed — use DELETE first to replace them.",
    )

  /** F2 — DELETE class mapping: remove a single rdfs:subClassOf triple to an external IRI */
  val deleteClassMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.class_not_found),
        badRequestVariant,
        // HTTP 500 is declared for OpenAPI documentation completeness.
        // It is NOT produced by application logic (infrastructure failures use .orDie → fiber defect).
        internalServerErrorVariant,
      ),
    )
    .delete
    .in(ontologiesBase / path[String]("ontologyIri") / "classes" / path[String]("classIri") / "mapping")
    .in(
      query[Option[String]]("mapping").description(
        "Required. The external IRI to remove from rdfs:subClassOf (URL-encoded). " +
          "This parameter is declared as optional in the OpenAPI schema because Tapir uses Option[String] to " +
          "allow a typed 400 response for missing values instead of a generic framework 400. " +
          "Sending a request without this parameter returns HTTP 400 with a v3-shaped error body.",
      ),
    )
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[ClassMappingResponse])
    .tags(mappingsTag)
    .description(
      "Removes a single rdfs:subClassOf triple from the given class to the specified external IRI. " +
        "Idempotent: deleting an absent triple is a no-op.",
    )

  /** F3 — PUT property mapping: add/replace rdfs:subPropertyOf triples pointing to external IRIs */
  val putPropertyMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.property_not_found),
        badRequestVariant,
        // HTTP 500 is declared for OpenAPI documentation completeness.
        // It is NOT produced by application logic (infrastructure failures use .orDie → fiber defect).
        internalServerErrorVariant,
      ),
    )
    .put
    .in(ontologiesBase / path[String]("ontologyIri") / "properties" / path[String]("propertyIri") / "mapping")
    .in(jsonBody[AddPropertyMappingsRequest])
    .out(statusCode(StatusCode.Ok))
    .out(jsonBody[PropertyMappingResponse])
    .tags(mappingsTag)
    .description(
      "Adds rdfs:subPropertyOf triples from the given property to the listed external IRIs. " +
        "Existing external super-property mappings are not removed — use DELETE first to replace them.",
    )

  /** F4 — DELETE property mapping: remove a single rdfs:subPropertyOf triple to an external IRI */
  val deletePropertyMapping = base
    .secured(
      oneOf(
        notFoundVariant(V3ErrorCode.ontology_not_found, V3ErrorCode.property_not_found),
        badRequestVariant,
        // HTTP 500 is declared for OpenAPI documentation completeness.
        // It is NOT produced by application logic (infrastructure failures use .orDie → fiber defect).
        internalServerErrorVariant,
      ),
    )
    .delete
    .in(ontologiesBase / path[String]("ontologyIri") / "properties" / path[String]("propertyIri") / "mapping")
    .in(
      query[Option[String]]("mapping").description(
        "Required. The external IRI to remove from rdfs:subPropertyOf (URL-encoded). " +
          "This parameter is declared as optional in the OpenAPI schema because Tapir uses Option[String] to " +
          "allow a typed 400 response for missing values instead of a generic framework 400. " +
          "Sending a request without this parameter returns HTTP 400 with a v3-shaped error body.",
      ),
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
