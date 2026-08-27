/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.AdminPathVariables.projectIri
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.AudienceRestrictionCounts
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemVisibility
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.RestrictionCounts
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility
import org.knora.webapi.slice.common.api.BaseEndpoints

/**
 * Read-only "view restrictions" reporting for a project, **grouped by property**.
 *
 * A separate set of routes from the class-grouped report rather than a `groupBy` parameter on it. A
 * property can be used by many resource classes, so a misconfigured property is scattered across the class
 * report's rows and its pattern is invisible; this answers "what is restricted on this property, across
 * every class that uses it".
 *
 * Stepped like the class report, and for the same reason: a single whole-project query grouped by property
 * measured 24.8s on LHTT. Step 1 comes from the ontology cache and issues no SPARQL, so the table paints at
 * once; step 2 is one bounded request per property.
 *
 * `ValueItemType` is imported from [[ViewRestrictionsEndpoints]] rather than redefined. It carries no
 * grouping discriminator, both reports genuinely mean the same four value types, and a second identical
 * enum would surface in the generated client as a confusing near-duplicate.
 */
final class ViewRestrictionsByPropertyEndpoints(baseEndpoints: BaseEndpoints) {

  import ViewRestrictionsByPropertyEndpoints.*

  private val base = "admin" / "projects" / "iri" / projectIri / "view-restrictions"

  private val propertyQuery = query[String]("property")
    .description("The IRI of the value property whose restrictions to report.")

  private val itemTypeQuery = query[ValueItemType]("itemType")
    .description("Restrict the counts to a single value type, or `All` for every value type.")
    .default(ValueItemType.All)

  val getProperties = baseEndpoints.securedEndpoint.get
    .in(base / "properties")
    .out(jsonBody[ViewRestrictionsProperties].example(ViewRestrictionsProperties.example))
    .description(
      "Step 1 of the property-grouped report: every value property of the project's own ontologies and of " +
        "`knora-base`, with its IRI, label and ontology name. Read from the ontology cache, so it issues no " +
        "triplestore query and the table can be rendered immediately. " +
        "Carries **no counts**: unlike a resource class, which has both a resource population and the values " +
        "inside it, a property only ever has values, so there is nothing step 1 could count that step 2 does " +
        "not. Every value property is listed, including ones no resource uses — such a row settles at zero, " +
        "which is a fact worth showing. Takes no filter. " +
        "The user must be project admin or system admin.",
    )

  val getPropertyValues = baseEndpoints.securedEndpoint.get
    .in(base / "property-values")
    .in(propertyQuery)
    .in(itemTypeQuery)
    .out(jsonBody[ViewRestrictionsPropertyValues].example(ViewRestrictionsPropertyValues.example))
    .description(
      "Step 2 of the property-grouped report: the per-audience restriction counts for ONE value property, " +
        "across every resource class that uses it, answered by a single triplestore query. " +
        "Each audience reports `hidden` (permission code 0 — nothing is served) and `restrictedView` " +
        "(code 1 — a degraded version is served) separately; the two are disjoint. " +
        "`totalValues` is how many values of this property the project holds, restricted or not. It is a " +
        "true denominator: the counts beside it are in the same unit, so \"94 of 66,484 are hidden\" is a " +
        "sound statement. It is derived from the same query rather than from a separate count. " +
        "The user must be project admin or system admin.",
    )

  val getPropertyItems = baseEndpoints.securedEndpoint.get
    .in(base / "property-items")
    .in(propertyQuery)
    .in(itemTypeQuery)
    .in(PageAndSize.queryParams())
    .out(jsonBody[PagedResponse[RestrictedPropertyResource]].example(RestrictedPropertyResource.pagedExample))
    .description(
      "Paginated list of the resources carrying a restricted value of the given property, each with its " +
        "restricted values and their per-audience visibility. " +
        "Each resource reports its **own** resource class: a property spans classes, which is the whole " +
        "reason this report exists, so the class belongs to the row rather than to the table. " +
        "Ordered by label then IRI, so paging is stable across requests. " +
        "The user must be project admin or system admin.",
    )
}

object ViewRestrictionsByPropertyEndpoints {

  val layer = ZLayer.derive[ViewRestrictionsByPropertyEndpoints]

  /** One row of the step-1 table: a value property, with no counts. */
  final case class RestrictedProperty(id: String, label: String, ontology: Option[String])
  object RestrictedProperty {
    given JsonCodec[RestrictedProperty] = DeriveJsonCodec.gen[RestrictedProperty]
    given Schema[RestrictedProperty]    = Schema.derived[RestrictedProperty]
  }

  /** Step 1: the table skeleton, from the ontology cache. */
  final case class ViewRestrictionsProperties(projectIri: String, properties: Seq[RestrictedProperty])
  object ViewRestrictionsProperties {
    given JsonCodec[ViewRestrictionsProperties] = DeriveJsonCodec.gen[ViewRestrictionsProperties]
    given Schema[ViewRestrictionsProperties]    = Schema.derived[ViewRestrictionsProperties]

    val example: ViewRestrictionsProperties = ViewRestrictionsProperties(
      projectIri = "http://rdfh.ch/projects/0001",
      properties = Seq(
        RestrictedProperty(
          id = "http://www.knora.org/ontology/0001/anything#hasText",
          label = "Text",
          ontology = Some("anything"),
        ),
      ),
    )
  }

  /**
   * Step 2: one property's counts, with the population they are a share of.
   *
   * One unit throughout — values of this property. A property has no resource population of its own, so
   * there is no second unit here to keep apart, unlike the class report.
   */
  final case class ViewRestrictionsPropertyValues(
    projectIri: String,
    property: String,
    itemType: ValueItemType,
    totalValues: Int,
    counts: AudienceRestrictionCounts,
  )
  object ViewRestrictionsPropertyValues {
    given JsonCodec[ViewRestrictionsPropertyValues] = DeriveJsonCodec.gen[ViewRestrictionsPropertyValues]
    given Schema[ViewRestrictionsPropertyValues]    = Schema.derived[ViewRestrictionsPropertyValues]

    val example: ViewRestrictionsPropertyValues = ViewRestrictionsPropertyValues(
      projectIri = "http://rdfh.ch/projects/0001",
      property = "http://www.knora.org/ontology/0001/anything#hasText",
      itemType = ValueItemType.All,
      totalValues = 120,
      counts = AudienceRestrictionCounts(
        anonymous = RestrictionCounts(12, 4),
        authenticated = RestrictionCounts(5, 2),
        projectMember = RestrictionCounts(0, 0),
      ),
    )
  }

  /** One restricted value of the property in the drill-down. */
  final case class RestrictedPropertyValue(
    valueIri: String,
    isFile: Boolean,
    hasComment: Boolean,
    visibility: ItemVisibility,
  )
  object RestrictedPropertyValue {
    given JsonCodec[RestrictedPropertyValue] = DeriveJsonCodec.gen[RestrictedPropertyValue]
    given Schema[RestrictedPropertyValue]    = Schema.derived[RestrictedPropertyValue]
  }

  /**
   * A resource carrying restricted values of the property.
   *
   * `resourceClassIri` is reported per resource because a property spans classes — two rows of the same
   * property can legitimately differ here, and that difference is often the finding.
   */
  final case class RestrictedPropertyResource(
    resourceIri: String,
    label: String,
    resourceClassIri: String,
    values: Seq[RestrictedPropertyValue],
  )
  object RestrictedPropertyResource {
    given JsonCodec[RestrictedPropertyResource] = DeriveJsonCodec.gen[RestrictedPropertyResource]
    given Schema[RestrictedPropertyResource]    = Schema.derived[RestrictedPropertyResource]

    val example: RestrictedPropertyResource = RestrictedPropertyResource(
      resourceIri = "http://rdfh.ch/0001/a-thing",
      label = "A thing",
      resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
      values = Seq(
        RestrictedPropertyValue(
          valueIri = "http://rdfh.ch/0001/a-thing/values/text",
          isFile = false,
          hasComment = true,
          visibility = ItemVisibility(Visibility.Hidden, Visibility.Visible, Visibility.Visible),
        ),
      ),
    )

    val pagedExample: PagedResponse[RestrictedPropertyResource] =
      PagedResponse.from(Seq(example), 1, PageAndSize.Default)
  }
}
