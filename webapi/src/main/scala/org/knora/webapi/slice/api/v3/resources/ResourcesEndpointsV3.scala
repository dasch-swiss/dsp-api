/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.zio.jsonBody
import zio.Chunk
import zio.ZLayer

import org.knora.webapi.slice.api.v3.ApiV3
import org.knora.webapi.slice.api.v3.ErrorDetail
import org.knora.webapi.slice.api.v3.LanguageStringDto
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.OntologyAndResourceClasses
import org.knora.webapi.slice.api.v3.OntologyDto
import org.knora.webapi.slice.api.v3.ResourceClassAndCountDto
import org.knora.webapi.slice.api.v3.ResourceClassDto
import org.knora.webapi.slice.api.v3.V3BaseEndpoint
import org.knora.webapi.slice.api.v3.V3ErrorCode
import org.knora.webapi.slice.api.v3.V3ErrorCode.project_not_found
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.slice.ontology.domain.model.RepresentationClass

trait EndpointHelper:
  import sttp.tapir.generic.auto._
  def oneOfNotFoundsErrorCode(codes: V3ErrorCode.NotFounds*): EndpointOutput.OneOfVariant[NotFound] =
    oneOfVariant(
      statusCode(StatusCode.NotFound).and(
        jsonBody[NotFound].examples(codes.toList.map(mkExampleNotFound)),
      ),
    )

  private def mkExampleNotFound(code: V3ErrorCode.NotFounds) = Example
    .of(NotFound(code, "Not found example message", Map("id" -> "example_id")))
    .name(code.toString)
    .description(show(code))

  private def show(code: V3ErrorCode) =
    s"""Example template string for code `$code`:
       |```
       |${code.template}
       |```""".stripMargin

class ResourcesEndpointsV3(baseEndpoint: V3BaseEndpoint) extends EndpointHelper {

  val getResourcesResourcesPerOntology = baseEndpoint
    .publicWithErrorOut(oneOfNotFoundsErrorCode(project_not_found))
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
        "Note that the `itemCount` includes only non-deleted resources and it includes resources even if the current user may not be permitted to see some of them, for performance reasons.",
    )

}

object ResourcesEndpointsV3 {
  val layer = ZLayer.derive[ResourcesEndpointsV3]
}
