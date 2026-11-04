/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.ontologies

import sttp.tapir.*
import zio.Clock
import zio.UIO
import zio.ZLayer

import java.time.Instant

import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy
import org.knora.webapi.slice.ontology.domain.model.Cardinality

final case class LastModificationDate private (value: Instant) extends Value[Instant]
object LastModificationDate {

  given TapirCodec.StringCodec[LastModificationDate] =
    TapirCodec.stringCodec(LastModificationDate.from, _.value.toString)

  def from(value: Instant): LastModificationDate                = LastModificationDate(value)
  def from(value: String): Either[String, LastModificationDate] =
    ValuesValidator.parseXsdDateTimeStamp(value).map(LastModificationDate.apply)

  def instant: UIO[LastModificationDate] = Clock.instant.map(LastModificationDate(_))
}

final class OntologiesEndpoints(baseEndpoints: BaseEndpoints) {

  private val publicReason =
    "Ontology metadata (label, comment, project, last modification) is part of the public data model."
  private val publicRequirement = Policy.Public(publicReason).requirement

  private val entitiesReason      = "Ontologies are public."
  private val entitiesRequirement = Policy.Public(entitiesReason).requirement

  private val dereferenceReason      = "An ontology IRI resolves to its public data model."
  private val dereferenceRequirement = Policy.Public(dereferenceReason).requirement

  private val base = "v2" / "ontologies"

  private val requiresOntologyAdmin = "Requires SystemAdmin or ProjectAdmin permissions for the ontology's project."

  private val adminProjectFrom = "ontology's project"
  private val adminRequirement = Policy.ProjectAdmin(adminProjectFrom).requirement

  private val ontologyIriPath      = path[IriDto].name("ontologyIri")
  private val propertyIriPath      = path[IriDto].name("propertyIri")
  private val resourceClassIriPath = path[IriDto].name("resourceClassIri")
  private val classIriPath         = path[IriDto].name("classIri")
  private val lastModificationDate = query[LastModificationDate]("lastModificationDate")
  private val allLanguages         = ApiV2.Inputs.allLanguages

  val getOntologyPathSegments = baseEndpoints
    .publicPolicyEndpointWithUser(dereferenceReason)
    .get
    .in("ontology" / paths)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .in(extractFromRequest(_.uri))
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "This is the route used to dereference an actual ontology IRI. " +
        "If the URL path looks like it belongs to a built-in API ontology (which has to contain \"knora-api\"), prefix it with http://api.knora.org to get the ontology IRI. " +
        "Otherwise, if it looks like it belongs to a project-specific API ontology, prefix it with routeData.appConfig.externalOntologyIriHostAndPort to get the ontology IRI. " +
        dereferenceRequirement,
    )

  val getOntologiesMetadataProject = baseEndpoints
    .publicPolicyEndpoint(publicReason)
    .get
    .in(base / "metadata")
    .in(header[Option[ProjectIri]](ApiV2.Headers.xKnoraAcceptProject))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Get the metadata of an ontology. $publicRequirement")

  val putOntologiesMetadata = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .put
    .in(base / "metadata")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Change the metadata of an ontology. " + adminRequirement,
    )

  val getOntologiesMetadataProjects = baseEndpoints
    .publicPolicyEndpoint(publicReason)
    .get
    .in(base / "metadata" / paths.description("projectIris"))
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Get the metadata of ontologies for specific projects. $publicRequirement")

  val getOntologiesAllentities = baseEndpoints
    .publicPolicyEndpointWithUser(entitiesReason)
    .get
    .in(base / "allentities" / ontologyIriPath)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Get all entities of an ontology. $entitiesRequirement")

  val postOntologiesClasses = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .post
    .in(base / "classes")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Create a new class. " + adminRequirement)

  val putOntologiesClasses = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .put
    .in(base / "classes")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Change the labels or comments of a class. " + adminRequirement,
    )

  val deleteOntologiesClassesComment = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .delete
    .in(base / "classes" / "comment" / resourceClassIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete the comment of a class definition. " + adminRequirement,
    )

  val postOntologiesCardinalities = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .post
    .in(base / "cardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Add cardinalities to a class. " +
        adminRequirement + " " +
        "For more info check out the <a href=\"https://docs.dasch.swiss/DSP-API/03-endpoints/api-v2/ontology-information/#adding-cardinalities-to-a-class\">documentation</a>.",
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

  val putOntologiesCardinalities = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .put
    .in(base / "cardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Update cardinalities of a class. " + adminRequirement,
    )

  val postOntologiesCandeletecardinalities = baseEndpoints.withUserEndpoint.post
    .in(base / "candeletecardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Check if cardinalities can be deleted from a class. " +
        requiresOntologyAdmin,
    )

  val patchOntologiesCardinalities = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .patch
    .in(base / "cardinalities")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete cardinalities from a class. " + adminRequirement,
    )

  val putOntologiesGuiorder = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .put
    .in(base / "guiorder")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Update the GUI order of properties in a class. " + adminRequirement,
    )

  val getOntologiesClassesIris = baseEndpoints
    .publicPolicyEndpointWithUser(entitiesReason)
    .get
    .in(base / "classes" / classIriPath)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Get a class definition by IRI. $entitiesRequirement")

  val getOntologiesCandeleteclass = baseEndpoints.withUserEndpoint.get
    .in(base / "candeleteclass" / resourceClassIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Check if a class can be deleted. Publicly accessible.")

  val deleteOntologiesClasses = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .delete
    .in(base / "classes" / resourceClassIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete a class from an ontology. " + adminRequirement,
    )

  val deleteOntologiesComment = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .delete
    .in(base / "comment" / ontologyIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete the comment of an ontology. " + adminRequirement,
    )

  val postOntologiesProperties = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .post
    .in(base / "properties")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Create a new property. " + adminRequirement)

  val putOntologiesProperties = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .put
    .in(base / "properties")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Update a property's labels or comments. " + adminRequirement,
    )

  val deletePropertiesComment = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .delete
    .in(base / "properties" / "comment" / propertyIriPath)
    .in(lastModificationDate)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete the comment of a property definition. " + adminRequirement,
    )

  val putOntologiesPropertiesGuielement = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .put
    .in(base / "properties" / "guielement")
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Update the GUI element of a property. " + adminRequirement,
    )

  val getOntologiesProperties = baseEndpoints
    .publicPolicyEndpointWithUser(entitiesReason)
    .get
    .in(base / "properties" / propertyIriPath)
    .in(allLanguages)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Get a property definition by IRI. $entitiesRequirement")

  val getOntologiesCandeleteproperty = baseEndpoints.withUserEndpoint.get
    .in(base / "candeleteproperty" / propertyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Check if a property can be deleted. Publicly accessible.")

  val deleteOntologiesProperty = baseEndpoints
    .projectAdminPolicyEndpoint(adminProjectFrom)
    .delete
    .in(base / "properties" / propertyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .in(lastModificationDate)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(
      "Delete a property from an ontology. " + adminRequirement,
    )

  private val bodyAttachedToProject = "body attachedToProject"
  val postOntologies                = baseEndpoints
    .projectAdminPolicyEndpoint(bodyAttachedToProject)
    .post
    .in(base)
    .in(stringJsonBody)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Create a new ontology. ${Policy.ProjectAdmin(bodyAttachedToProject).requirement}")

  val getOntologiesCandeleteontology = baseEndpoints.securedEndpoint.get
    .in(base / "candeleteontology" / ontologyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description("Check if an ontology can be deleted. Requires authentication.")

  private val ontologyProject = "path ontology's project"
  val deleteOntologies        = baseEndpoints
    .projectAdminPolicyEndpoint(ontologyProject)
    .delete
    .in(base / ontologyIriPath)
    .in(ApiV2.Inputs.formatOptions)
    .in(lastModificationDate)
    .out(ApiV2.Outputs.stringBodyFormatted)
    .out(ApiV2.Outputs.contentTypeHeader)
    .description(s"Delete an ontology. ${Policy.ProjectAdmin(ontologyProject).requirement}")
}

object OntologiesEndpoints {
  private[ontologies] val layer = ZLayer.derive[OntologiesEndpoints]
}
