/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer

import org.knora.webapi.slice.api.v3.ApiV3
import org.knora.webapi.slice.api.v3.EndpointHelper
import org.knora.webapi.slice.api.v3.LanguageStringDto
import org.knora.webapi.slice.api.v3.OntologyAndResourceClasses
import org.knora.webapi.slice.api.v3.OntologyDto
import org.knora.webapi.slice.api.v3.ResourceClassAndCountDto
import org.knora.webapi.slice.api.v3.ResourceClassDto
import org.knora.webapi.slice.api.v3.V3BaseEndpoint
import org.knora.webapi.slice.api.v3.V3ErrorCode.project_not_found
import org.knora.webapi.slice.common.api.Policy
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.slice.ontology.domain.model.RepresentationClass

class ResourcesEndpointsV3(baseEndpoint: V3BaseEndpoint) extends EndpointHelper {

  val getResourcesResourcesPerOntology = baseEndpoint
    .publicPolicy(
      oneOf(
        notFoundVariant(project_not_found),
      ),
      "aggregate counts per class, no resource data",
    )
    .get
    .in(ApiV3.V3ProjectsProjectIri / "resourcesPerOntology")
    .out(
      jsonBody[List[OntologyAndResourceClasses]].example(
        List(
          OntologyAndResourceClasses(
            ontology = OntologyDto(
              iri = "http://0.0.0.0:3333/ontology/0001/anything/v2",
              label = "The anything ontology",
              comment = "A sample ontology for anything",
            ),
            classesAndCount = List(
              ResourceClassAndCountDto(
                resourceClass = ResourceClassDto(
                  iri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ImageThing",
                  representationClass = RepresentationClass.StillImageRepresentation,
                  label = List(
                    LanguageStringDto(
                      value = "Thing with image representation",
                      language = EN,
                    ),
                  ),
                  comment = List(
                    LanguageStringDto(
                      value = "A thing which is has an image",
                      language = EN,
                    ),
                  ),
                ),
                itemCount = 42,
              ),
            ),
          ),
        ),
      ),
    )
    .description(
      "This endpoint returns all ontologies in a project along with their resource classes and the count of resource instances for each class. " +
        "Note that the `itemCount` includes only non-deleted resources and it includes resources even if the current user may not be permitted to see some of them, for performance reasons. Does not require authentication.",
    )

  val postValuesBatch = baseEndpoint
    .authenticatedObjectAccessPolicy(
      oneOf(badRequestVariant),
      "BatchChangesService, on each resource and value",
    )
    .post
    .in(ApiV3.basePath / "resources" / "values" / "batch")
    .in(
      jsonBody[BatchChangesRequest].example(
        BatchChangesRequest(
          List(
            BatchChange(
              resource = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw",
              property = Some("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText"),
              value = Some("http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ"),
              expected = Some("Basel"),
              newValue = Some("Basle"),
            ),
            BatchChange(
              resource = "http://rdfh.ch/0001/a-thing",
              property = Some("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText"),
              newValue = Some("Basel"),
              expectEmpty = Some(true),
            ),
            BatchChange(
              resource = "http://rdfh.ch/0001/a-thing",
              label = Some(BatchLabelChange(expected = Some("A thing"), newLabel = "Another thing")),
            ),
          ),
        ),
      ),
    )
    .out(
      jsonBody[BatchChangesResponse].example(
        BatchChangesResponse(
          List(
            BatchChangeResult(0, BatchChangeStatus.ok, Some("http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/new")),
            BatchChangeResult(1, BatchChangeStatus.conflict, message = Some("The value is 'Bâle', not 'Basel'.")),
            BatchChangeResult(2, BatchChangeStatus.forbidden, message = Some("No permission to modify the resource.")),
          ),
        ),
      ),
    )
    .description(
      s"Applies up to ${BatchChangesRequest.MaxChanges} planned changes of a bulk edit: create or update plain text " +
        "and list values, or update resource labels. A change without `value` creates a value; with `value` it " +
        "updates that value. A change with `expected` is rejected as a `conflict` if the stored content differs (the " +
        "text, the list node IRI, the label). A create with `expectEmpty` is a `conflict` if the property holds a " +
        "value that an earlier change of the same request did not create. Formatted text is refused as `invalid`. " +
        "Changes are applied in order, each on its own, and each gets a result at its index: one failure does not " +
        "cancel the others. The comment of an updated value, and the language of a text, are kept. " +
        Policy.objectWriteRequirement,
    )

}

object ResourcesEndpointsV3 {
  val layer = ZLayer.derive[ResourcesEndpointsV3]
}
