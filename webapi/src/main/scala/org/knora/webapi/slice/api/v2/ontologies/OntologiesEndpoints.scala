/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.ontologies

import sttp.tapir.*
import zio.ZLayer

import java.time.Instant

import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.ontology.domain.model.Cardinality

final case class LastModificationDate private (value: Instant) extends Value[Instant]
object LastModificationDate {

  given TapirCodec.StringCodec[LastModificationDate] =
    TapirCodec.stringCodec(LastModificationDate.from, _.value.toString)

  def from(value: Instant): LastModificationDate                = LastModificationDate(value)
  def from(value: String): Either[String, LastModificationDate] =
    ValuesValidator.parseXsdDateTimeStamp(value).map(LastModificationDate.apply)
}

final class OntologiesEndpoints(baseEndpoints: BaseEndpoints) {
  private val base = "v2" / "ontologies"

  private val ontologyIriPath      = path[IriDto].name("ontologyIri")
  private val propertyIriPath      = path[IriDto].name("propertyIri")
  private val resourceClassIriPath = path[IriDto].name("resourceClassIri")
  private val classIriPath         = path[IriDto].name("classIri")
  private val lastModificationDate = query[LastModificationDate]("lastModificationDate")
  private val allLanguages         = query[Boolean]("allLanguages").default(false)

  val getOntologyPathSegments = baseEndpoints.withUserEndpoint.get
    .in("ontology" / paths)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .in(extractFromRequest(_.uri))
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "This is the route used to dereference an actual ontology IRI. " +
        "If the URL path looks like it belongs to a built-in API ontology (which has to contain \"knora-api\"), prefix it with http://api.knora.org to get the ontology IRI. " +
        "Otherwise, if it looks like it belongs to a project-specific API ontology, prefix it with routeData.appConfig.externalOntologyIriHostAndPort to get the ontology IRI.",
    )

  val getOntologiesMetadataProject = baseEndpoints.publicEndpoint.get
    .in(base / "metadata")
    .in(header[Option[ProjectIri]](ApiV2.Headers.xKnoraAcceptProject))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Get the metadata of an ontology. Publicly accessible.")

  val putOntologiesMetadata = baseEndpoints.securedEndpoint.put
    .in(base / "metadata")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Change the metadata of an ontology. Requires ProjectAdmin permissions for the ontology's project.")

  val getOntologiesMetadataProjects = baseEndpoints.publicEndpoint.get
    .in(base / "metadata" / paths.description("projectIris"))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Get the metadata of ontologies for specific projects. Publicly accessible.")

  val getOntologiesAllentities = baseEndpoints.withUserEndpoint.get
    .in(base / "allentities" / ontologyIriPath)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Get all entities of an ontology. Publicly accessible.")

  val postOntologiesClasses = baseEndpoints.withUserEndpoint.post
    .in(base / "classes")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Create a new class. Requires ProjectAdmin permissions for the ontology's project.")

  val putOntologiesClasses = baseEndpoints.withUserEndpoint.put
    .in(base / "classes")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Change the labels or comments of a class. Requires ProjectAdmin permissions for the ontology's project.",
    )

  val deleteOntologiesClassesComment = baseEndpoints.withUserEndpoint.delete
    .in(base / "classes" / "comment" / resourceClassIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete the comment of a class definition. Requires ProjectAdmin permissions for the ontology's project.",
    )

  val postOntologiesCardinalities = baseEndpoints.withUserEndpoint.post
    .in(base / "cardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Add cardinalities to a class. " +
        "Requires ProjectAdmin permissions for the ontology's project. " +
        "For more info check out the <a href=\"https://docs.dasch.swiss/knora-api-v2/ontologies.html#add-cardinalities-to-a-class\">documentation</a>.",
    )

  val getOntologiesCanreplacecardinalities = baseEndpoints.withUserEndpoint.get
    .in(base / "canreplacecardinalities" / resourceClassIriPath)
    .in(query[Option[IriDto]]("propertyIri"))
    .in(
      query[Option[String]]("newCardinality")
        .description(
          "The new cardinality to be set for the property, must be provided when propertyIri is given. " +
            "Valid values are: " + Cardinality.allCardinalities.mkString(", "),
        )
        .example(Some(Cardinality.AtLeastOne.toString)),
    )
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "If only a class IRI is provided, this endpoint checks if any cardinality of any of the class properties can " +
        "be replaced. " +
        "If a property IRI and a new cardinality are provided, it checks if the cardinality can be set for the property " +
        "on the specific class. " +
        "Fails if not both a property IRI and a new cardinality is provided. " +
        "Fails if the user does not have write access to the ontology of the class.",
    )

  val putOntologiesCardinalities = baseEndpoints.withUserEndpoint.put
    .in(base / "cardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Update cardinalities of a class. Requires ProjectAdmin permissions for the ontology's project.")

  val postOntologiesCandeletecardinalities = baseEndpoints.withUserEndpoint.post
    .in(base / "candeletecardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Check if cardinalities can be deleted from a class. Publicly accessible.")

  val patchOntologiesCardinalities = baseEndpoints.withUserEndpoint.patch
    .in(base / "cardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Delete cardinalities from a class. Requires ProjectAdmin permissions for the ontology's project.")

  val putOntologiesGuiorder = baseEndpoints.withUserEndpoint.put
    .in(base / "guiorder")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Update the GUI order of properties in a class. Requires ProjectAdmin permissions for the ontology's project.",
    )

  val getOntologiesClassesIris = baseEndpoints.withUserEndpoint.get
    .in(base / "classes" / classIriPath)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Get a class definition by IRI. Publicly accessible.")

  val getOntologiesCandeleteclass = baseEndpoints.withUserEndpoint.get
    .in(base / "candeleteclass" / resourceClassIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Check if a class can be deleted. Publicly accessible.")

  val deleteOntologiesClasses = baseEndpoints.withUserEndpoint.delete
    .in(base / "classes" / resourceClassIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Delete a class from an ontology. Requires ProjectAdmin permissions for the ontology's project.")

  val deleteOntologiesComment = baseEndpoints.withUserEndpoint.delete
    .in(base / "comment" / ontologyIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Delete the comment of an ontology. Requires ProjectAdmin permissions for the ontology's project.")

  val postOntologiesProperties = baseEndpoints.withUserEndpoint.post
    .in(base / "properties")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Create a new property. Requires ProjectAdmin permissions for the ontology's project.")

  val putOntologiesProperties = baseEndpoints.withUserEndpoint.put
    .in(base / "properties")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Update a property's labels or comments. Requires ProjectAdmin permissions for the ontology's project.",
    )

  val deletePropertiesComment = baseEndpoints.withUserEndpoint.delete
    .in(base / "properties" / "comment" / propertyIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete the comment of a property definition. Requires ProjectAdmin permissions for the ontology's project.",
    )

  val putOntologiesPropertiesGuielement = baseEndpoints.withUserEndpoint.put
    .in(base / "properties" / "guielement")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Update the GUI element of a property. Requires ProjectAdmin permissions for the ontology's project.")

  val getOntologiesProperties = baseEndpoints.withUserEndpoint.get
    .in(base / "properties" / propertyIriPath)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Get a property definition by IRI. Publicly accessible.")

  val getOntologiesCandeleteproperty = baseEndpoints.withUserEndpoint.get
    .in(base / "candeleteproperty" / propertyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Check if a property can be deleted. Publicly accessible.")

  val deleteOntologiesProperty = baseEndpoints.securedEndpoint.delete
    .in(base / "properties" / propertyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .in(lastModificationDate)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Delete a property from an ontology. Requires ProjectAdmin permissions for the ontology's project.")

  val postOntologies = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Create a new ontology. Requires ProjectAdmin permissions for the project.")

  val getOntologiesCandeleteontology = baseEndpoints.securedEndpoint
    .in(base / "candeleteontology" / ontologyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Check if an ontology can be deleted. Requires authentication.")

  val deleteOntologies = baseEndpoints.securedEndpoint.delete
    .in(base / ontologyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .in(lastModificationDate)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Delete an ontology. Requires ProjectAdmin permissions for the ontology's project.")
}

object OntologiesEndpoints {
  private[ontologies] val layer = ZLayer.derive[OntologiesEndpoints]
}
