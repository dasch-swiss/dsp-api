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
import org.knora.webapi.slice.common.api.BaseEndpoints

/**
 * Read-only "view restrictions" reporting for a project (design screen 1h).
 *
 * Reports, for the three built-in audiences, how many items are hidden and lets an admin
 * drill into the affected resources/items. (Spec: DEV-6778, dasch-specs.)
 */
final class ViewRestrictionsEndpoints(baseEndpoints: BaseEndpoints) {

  import ViewRestrictionsEndpoints.*

  private val base = "admin" / "projects" / "iri" / projectIri / "view-restrictions"

  private val groupByQuery = query[GroupBy]("groupBy")
    .description("Whether to group the matrix by resource class or by the property that carries the restriction.")
    .default(GroupBy.ResourceClass)

  private val itemTypeQuery = query[ItemType]("itemType")
    .description("Restrict the report to a single item type, or `all` for the combined view.")
    .default(ItemType.All)

  private val groupQuery = query[String]("group")
    .description("The IRI of the class (or property, in property mode) whose affected items to page through.")

  val getViewRestrictionsSummary = baseEndpoints.securedEndpoint.get
    .in(base / "summary")
    .in(groupByQuery)
    .in(itemTypeQuery)
    .out(jsonBody[ViewRestrictionsSummary].example(ViewRestrictionsSummary.example))
    .description(
      "Per-audience counts of hidden items for a project, grouped by resource class or property. " +
        "Counts hidden items only (permission code 0); restricted-view is reported per item in the drill-down. " +
        "The user must be project admin or system admin.",
    )

  val getViewRestrictionsItems = baseEndpoints.securedEndpoint.get
    .in(base / "items")
    .in(groupByQuery)
    .in(groupQuery)
    .in(itemTypeQuery)
    .in(PageAndSize.queryParams())
    .out(jsonBody[PagedResponse[RestrictedResource]].example(RestrictedResource.pagedExample))
    .description(
      "Paginated list of resources affected by a restriction under a given class (or property), each with " +
        "its per-audience visibility and the nested restricted file values / values / comments. " +
        "The user must be project admin or system admin.",
    )
}

object ViewRestrictionsEndpoints {

  val layer = ZLayer.derive[ViewRestrictionsEndpoints]

  /** The three audiences a restriction is reported against. Order is significant (widening access). */
  enum Audience {
    case Anonymous     // logged out — resolved as { UnknownUser }
    case Authenticated // logged in, non-member — resolved as { KnownUser }
    case ProjectMember // member of the project — resolved as { KnownUser, ProjectMember }
  }
  object Audience {
    given JsonCodec[Audience] = DeriveJsonCodec.gen[Audience]
    given Schema[Audience]    = Schema.derivedEnumeration[Audience].defaultStringBased

    /** In summary/reporting order, widest-restriction first. */
    val ordered: List[Audience] = List(Anonymous, Authenticated, ProjectMember)
  }

  /** The effective visibility of one item for one audience. */
  enum Visibility {
    case Visible        // full view (permission code >= 2)
    case Hidden         // no access (permission code 0) — counted in the summary
    case RestrictedView // watermarked / reduced size (permission code 1) — image file values only
  }
  object Visibility {
    given JsonCodec[Visibility] = DeriveJsonCodec.gen[Visibility]
    // defaultStringBased so the OpenAPI schema emits `type: string, enum: [Hidden, RestrictedView, Visible]`
    // (a plain Schema.derived emits a oneOf of empty objects, which codegens as `type Visibility = object`).
    given Schema[Visibility] = Schema.derivedEnumeration[Visibility].defaultStringBased
  }

  /** The kind of restricted item. */
  enum ItemType {
    case All
    case Resource
    case File
    case Value
    case Comment
  }
  object ItemType {
    given JsonCodec[ItemType] = DeriveJsonCodec.gen[ItemType]
    // String-based Schema so the enum emits `type: string, enum: [...]` in BOTH query-param and body
    // positions (a plain Schema.derived makes the body copy codegen as `type ItemType1 = object`).
    given Schema[ItemType] = Schema.derivedEnumeration[ItemType].defaultStringBased

    // Query-param codec, e.g. ?itemType=All
    given Codec[String, ItemType, CodecFormat.TextPlain] =
      Codec.derivedEnumeration[String, ItemType].defaultStringBased
  }

  /** How the summary matrix is grouped. */
  enum GroupBy {
    case ResourceClass
    case Property
  }
  object GroupBy {
    given JsonCodec[GroupBy] = DeriveJsonCodec.gen[GroupBy]
    given Schema[GroupBy]    = Schema.derivedEnumeration[GroupBy].defaultStringBased

    given Codec[String, GroupBy, CodecFormat.TextPlain] =
      Codec.derivedEnumeration[String, GroupBy].defaultStringBased
  }

  /** Per-audience hidden counts. Cumulative: anonymous >= authenticated >= projectMember. */
  final case class AudienceCounts(anonymous: Int, authenticated: Int, projectMember: Int)
  object AudienceCounts {
    given JsonCodec[AudienceCounts] = DeriveJsonCodec.gen[AudienceCounts]
    given Schema[AudienceCounts]    = Schema.derived[AudienceCounts]

    val zero: AudienceCounts = AudienceCounts(0, 0, 0)
  }

  /** One row of the summary matrix: a resource class or a property. */
  final case class RestrictionGroup(
    id: String,                   // class IRI, or property IRI in property mode
    label: String,                // human-readable label
    ontology: Option[String],     // short ontology label (class mode)
    propertyName: Option[String], // e.g. "anything:hasPicture" (property mode)
    counts: AudienceCounts,
  )
  object RestrictionGroup {
    given JsonCodec[RestrictionGroup] = DeriveJsonCodec.gen[RestrictionGroup]
    given Schema[RestrictionGroup]    = Schema.derived[RestrictionGroup]
  }

  final case class ViewRestrictionsSummary(
    projectIri: String,
    groupBy: GroupBy,
    itemType: ItemType,
    groups: Seq[RestrictionGroup],
    totals: AudienceCounts,
    // True if the counts are a lower bound rather than exact. Always false today: counts are computed by
    // SPARQL aggregation over the whole project, so they are exact at any size. Retained so a future
    // sampling/short-circuiting strategy can signal inexactness without a breaking response change.
    approximate: Boolean,
  )
  object ViewRestrictionsSummary {
    given JsonCodec[ViewRestrictionsSummary] = DeriveJsonCodec.gen[ViewRestrictionsSummary]
    given Schema[ViewRestrictionsSummary]    = Schema.derived[ViewRestrictionsSummary]

    val example: ViewRestrictionsSummary = ViewRestrictionsSummary(
      projectIri = "http://rdfh.ch/projects/0001",
      groupBy = GroupBy.ResourceClass,
      itemType = ItemType.All,
      groups = Seq(
        RestrictionGroup(
          id = "http://www.knora.org/ontology/0001/anything#Thing",
          label = "Thing",
          ontology = Some("anything"),
          propertyName = None,
          counts = AudienceCounts(7, 2, 0),
        ),
      ),
      totals = AudienceCounts(7, 2, 0),
      approximate = false,
    )
  }

  /** Per-audience visibility of a single item. */
  final case class ItemVisibility(anonymous: Visibility, authenticated: Visibility, projectMember: Visibility)
  object ItemVisibility {
    given JsonCodec[ItemVisibility] = DeriveJsonCodec.gen[ItemVisibility]
    given Schema[ItemVisibility]    = Schema.derived[ItemVisibility]
  }

  /** A restricted part nested under a resource: a file value, an ordinary value, or a comment. */
  final case class RestrictedItem(
    `type`: ItemType, // File | Value | Comment (never All/Resource here)
    propertyIri: Option[String],
    propertyLabel: Option[String],
    valueIri: Option[String],
    visibility: ItemVisibility,
  )
  object RestrictedItem {
    given JsonCodec[RestrictedItem] = DeriveJsonCodec.gen[RestrictedItem]
    given Schema[RestrictedItem]    = Schema.derived[RestrictedItem]
  }

  /** A resource affected by a restriction, with its own visibility and its restricted parts. */
  final case class RestrictedResource(
    resourceIri: String,
    label: String,
    resourceClassIri: String,
    resourceVisibility: ItemVisibility,
    items: Seq[RestrictedItem],
  )
  object RestrictedResource {
    given JsonCodec[RestrictedResource] = DeriveJsonCodec.gen[RestrictedResource]
    given Schema[RestrictedResource]    = Schema.derived[RestrictedResource]

    val example: RestrictedResource = RestrictedResource(
      resourceIri = "http://rdfh.ch/0001/a-thing",
      label = "A thing",
      resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
      resourceVisibility = ItemVisibility(Visibility.Hidden, Visibility.Visible, Visibility.Visible),
      items = Seq(
        RestrictedItem(
          `type` = ItemType.File,
          // The endpoint emits the internal knora-base IRI (as ?resourceClassIri does), not the external v2 one.
          propertyIri = Some("http://www.knora.org/ontology/knora-base#hasStillImageFileValue"),
          propertyLabel = Some("Still image file"),
          valueIri = Some("http://rdfh.ch/0001/a-thing/values/image"),
          visibility = ItemVisibility(Visibility.RestrictedView, Visibility.Visible, Visibility.Visible),
        ),
      ),
    )

    val pagedExample: PagedResponse[RestrictedResource] =
      PagedResponse.from(Seq(example), 1, PageAndSize.Default)
  }
}
