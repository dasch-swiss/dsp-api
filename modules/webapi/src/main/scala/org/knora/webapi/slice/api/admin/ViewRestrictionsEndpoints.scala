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
 * Reports, for the three built-in audiences, how many items are hidden or served in restricted view, and
 * lets an admin drill into the affected resources/items. (Spec: DEV-6778, dasch-specs.)
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
      "Per-audience counts of items a project's audiences cannot fully see, grouped by resource class or " +
        "property. Each audience reports `hidden` (permission code 0 — nothing is served) and " +
        "`restrictedView` (code 1 — a degraded version is served) separately; the two are disjoint. " +
        "Both are further split by unit: `resources` counts whole restricted resources, `items` counts " +
        "restricted values inside resources. The two units are never summed — one resource with three " +
        "hidden values is 1 resource and 3 items, not 4 of anything. " +
        "When grouping by resource class, **every** class in the project is reported — including classes " +
        "with no restrictions at all — each with `totalResources`: how many resources the project has in " +
        "that class in total. That count is independent of the restrictions and of `itemType`; it is the " +
        "denominator for `counts.<audience>.resources` (same unit), not for `items`. Summing it over the " +
        "groups gives the project's whole resource count. In property mode only properties that carry a " +
        "restriction are " +
        "reported and `totalResources` is absent, since a property has no resource population of its own. " +
        "Counts are computed by the triplestore and are exact regardless of project size. " +
        "The user must be project admin or system admin.",
    )

  private val resourceClassQuery = query[String]("resourceClass")
    .description("The IRI of the resource class whose value-level restrictions to count.")

  val getViewRestrictionsClasses = baseEndpoints.securedEndpoint.get
    .in(base / "classes")
    .out(jsonBody[ViewRestrictionsClasses].example(ViewRestrictionsClasses.example))
    .description(
      "Step 1 of the view-restrictions report: every resource class in the project with its label, " +
        "ontology, total resource count, and per-audience resource-level restriction counts. Answered by a " +
        "single triplestore query, so the whole table can be rendered before any value counts arrive. " +
        "Each audience reports `hidden` (permission code 0 — nothing is served) and `restrictedView` " +
        "(code 1 — a degraded version is served) separately; the two are disjoint. " +
        "`totalResources` is the class's whole population — restricted or not — and is the denominator " +
        "for the counts here, which are in the same unit. It is derived from the same query rather than " +
        "from a separate count. **Every** class is reported, including classes with nothing restricted " +
        "(all-zero counts, non-zero `totalResources`) and classes holding no resources at all (zero " +
        "population). Takes no item-type filter: resource-level counts are never filtered. " +
        "The user must be project admin or system admin.",
    )

  private val valueItemTypeQuery = query[ValueItemType]("itemType")
    .description("Restrict the value counts to a single value type, or `All` for every value type.")
    .default(ValueItemType.All)

  val getViewRestrictionsValues = baseEndpoints.securedEndpoint.get
    .in(base / "values")
    .in(resourceClassQuery)
    .in(valueItemTypeQuery)
    .out(jsonBody[ViewRestrictionsValues].example(ViewRestrictionsValues.example))
    .description(
      "Step 2 of the view-restrictions report: the per-audience value-level restriction counts for ONE " +
        "resource class, answered by a single triplestore query. Clients call this once per class from " +
        "the step-1 list, which bounds each request and lets a failure affect only that class's row. " +
        "Counts restricted values inside the class's resources — file values, ordinary values, comments — " +
        "which is a different unit from the resource counts in step 1 and must never be summed with them. " +
        "`itemType` narrows to one kind of value; `All` means all value types (it does not include whole " +
        "resources, which step 1 already reports). " +
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
        "Ordered by label then IRI, so paging is stable across requests. " +
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
    case Visible        // full view (permission code >= 2) — not reported as a restriction
    case Hidden         // no access (permission code 0) — counted as `hidden` in the summary
    case RestrictedView // degraded view (code 1) — counted as `restrictedView` in the summary
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

  /**
   * The item-type filter accepted by the stepped `/values` endpoint.
   *
   * Deliberately **not** [[ItemType]], which cannot be narrowed: `ItemType.Resource` is load-bearing for
   * the `/items` drill-down, where it tags whether a row is the resource itself or a value inside it (see
   * `ViewRestrictionsRepo`'s row construction and `ViewRestrictionsService.items`). Reusing `ItemType`
   * here would offer clients a `Resource` value that means nothing on an endpoint that only counts values —
   * step 1 already reports every resource-level count, unfiltered.
   *
   * `All` therefore means "all value types", which is narrower than `ItemType.All`.
   */
  enum ValueItemType {
    case All
    case File
    case Value
    case Comment
  }
  object ValueItemType {
    given JsonCodec[ValueItemType] = DeriveJsonCodec.gen[ValueItemType]
    given Schema[ValueItemType]    = Schema.derivedEnumeration[ValueItemType].defaultStringBased

    given Codec[String, ValueItemType, CodecFormat.TextPlain] =
      Codec.derivedEnumeration[String, ValueItemType].defaultStringBased

    /** The corresponding [[ItemType]], for the query builder that still speaks in those terms. */
    def toItemType(t: ValueItemType): ItemType = t match {
      case ValueItemType.All     => ItemType.All
      case ValueItemType.File    => ItemType.File
      case ValueItemType.Value   => ItemType.Value
      case ValueItemType.Comment => ItemType.Comment
    }
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

  /**
   * How many items one audience cannot fully see, split by the two ways that happens.
   *
   *   - `hidden`         — permission code 0: the audience gets nothing at all.
   *   - `restrictedView` — permission code 1: the audience gets a degraded version (for a still image, the
   *     watermarked / reduced rendering).
   *
   * The two are disjoint, so `hidden + restrictedView` is the number of items that are not fully visible.
   * They are reported separately because they are different facts an admin acts on differently, and because
   * collapsing them would hide code-1 items behind a number labelled "hidden".
   */
  final case class RestrictionCounts(hidden: Int, restrictedView: Int)
  object RestrictionCounts {
    given JsonCodec[RestrictionCounts] = DeriveJsonCodec.gen[RestrictionCounts]
    given Schema[RestrictionCounts]    = Schema.derived[RestrictionCounts]

    val zero: RestrictionCounts = RestrictionCounts(0, 0)

    def plus(a: RestrictionCounts, b: RestrictionCounts): RestrictionCounts =
      RestrictionCounts(a.hidden + b.hidden, a.restrictedView + b.restrictedView)

    /** Items that are not fully visible — the sum of both states. */
    extension (c: RestrictionCounts) def total: Int = c.hidden + c.restrictedView
  }

  /**
   * What one audience cannot fully see in a group, counted in the two units separately.
   *
   *   - `resources` — whole resources whose own permissions restrict them. This is the figure comparable
   *     to [[RestrictionGroup.totalResources]]: "5 of 120 Things are hidden" is a true statement.
   *   - `items` — restricted values inside resources (file values, ordinary values, comments).
   *
   * They are deliberately NOT summed. One resource carrying three hidden values is 1 resource and 3 items;
   * reporting "4" mixes units and can exceed the class's resource population outright — a row reading
   * "3 of 1" is the bug this split fixes.
   *
   * `items` is what conveys restriction *density*: five resources with one hidden field each and five with
   * forty hidden fields each are very different situations, and the paginated drill-down cannot recover
   * that number for a large class.
   */
  final case class UnitCounts(resources: RestrictionCounts, items: RestrictionCounts)
  object UnitCounts {
    given JsonCodec[UnitCounts] = DeriveJsonCodec.gen[UnitCounts]
    given Schema[UnitCounts]    = Schema.derived[UnitCounts]

    val zero: UnitCounts = UnitCounts(RestrictionCounts.zero, RestrictionCounts.zero)

    def plus(a: UnitCounts, b: UnitCounts): UnitCounts =
      UnitCounts(RestrictionCounts.plus(a.resources, b.resources), RestrictionCounts.plus(a.items, b.items))

    /** Everything not fully visible, across both units. Only for "is anything restricted at all?" checks. */
    extension (c: UnitCounts) def anyRestriction: Int = c.resources.total + c.items.total
  }

  /**
   * Per-audience counts. Cumulative in the sense that access widens across the audiences, so each
   * audience's counts are less than or equal to the previous one's, per unit.
   */
  final case class AudienceCounts(
    anonymous: UnitCounts,
    authenticated: UnitCounts,
    projectMember: UnitCounts,
  )
  object AudienceCounts {
    given JsonCodec[AudienceCounts] = DeriveJsonCodec.gen[AudienceCounts]
    given Schema[AudienceCounts]    = Schema.derived[AudienceCounts]

    val zero: AudienceCounts = AudienceCounts(UnitCounts.zero, UnitCounts.zero, UnitCounts.zero)
  }

  // ---------------------------------------------------------------------------------------------------
  // Stepped report (DEV-6778).
  //
  // The stepped endpoints deliberately do NOT reuse [[UnitCounts]]. Each endpoint answers in exactly one
  // unit — `/classes` counts whole resources, `/values` counts values inside them — so a pair-shaped type
  // could only ever be returned half-empty, which reintroduces the very unit confusion UnitCounts exists
  // to prevent. The unit is carried by which endpoint answered, not by a field.
  // ---------------------------------------------------------------------------------------------------

  /**
   * What each audience cannot fully see, in the unit of the answering endpoint.
   *
   * Cumulative in the sense that access widens across the audiences, so each audience's counts are less
   * than or equal to the previous one's.
   */
  final case class AudienceRestrictionCounts(
    anonymous: RestrictionCounts,
    authenticated: RestrictionCounts,
    projectMember: RestrictionCounts,
  )
  object AudienceRestrictionCounts {
    given JsonCodec[AudienceRestrictionCounts] = DeriveJsonCodec.gen[AudienceRestrictionCounts]
    given Schema[AudienceRestrictionCounts]    = Schema.derived[AudienceRestrictionCounts]

    val zero: AudienceRestrictionCounts =
      AudienceRestrictionCounts(RestrictionCounts.zero, RestrictionCounts.zero, RestrictionCounts.zero)

    /**
     * Adds `counts` into one audience's slot, leaving the other two untouched.
     *
     * Written as an update rather than as a three-way `plus` of mostly-zero values because the caller
     * classifies one permission literal against one audience at a time, and this keeps that fold direct.
     */
    def add(
      c: AudienceRestrictionCounts,
      audience: Audience,
      counts: RestrictionCounts,
    ): AudienceRestrictionCounts =
      audience match {
        case Audience.Anonymous     => c.copy(anonymous = RestrictionCounts.plus(c.anonymous, counts))
        case Audience.Authenticated => c.copy(authenticated = RestrictionCounts.plus(c.authenticated, counts))
        case Audience.ProjectMember => c.copy(projectMember = RestrictionCounts.plus(c.projectMember, counts))
      }
  }

  /**
   * One row of the step-1 table: a resource class, its population, and its resource-level restrictions.
   *
   * `totalResources` is the class's whole resource population, derived as the sum of its per-permission
   * counts rather than by a separate query. It is the denominator for every figure in `counts`, which are
   * in the same unit — unlike the value counts from `/values`, which count a different thing entirely.
   *
   * Every class in the project is reported, so a row may be all zeros with a non-zero `totalResources`:
   * that is a class with nothing restricted, not an empty row.
   */
  final case class RestrictedClass(
    id: String,
    label: String,
    ontology: Option[String],
    totalResources: Int,
    counts: AudienceRestrictionCounts,
  )
  object RestrictedClass {
    given JsonCodec[RestrictedClass] = DeriveJsonCodec.gen[RestrictedClass]
    given Schema[RestrictedClass]    = Schema.derived[RestrictedClass]
  }

  /** Step 1: the whole table skeleton plus resource-level counts, from a single query. */
  final case class ViewRestrictionsClasses(projectIri: String, classes: Seq[RestrictedClass])
  object ViewRestrictionsClasses {
    given JsonCodec[ViewRestrictionsClasses] = DeriveJsonCodec.gen[ViewRestrictionsClasses]
    given Schema[ViewRestrictionsClasses]    = Schema.derived[ViewRestrictionsClasses]

    val example: ViewRestrictionsClasses = ViewRestrictionsClasses(
      projectIri = "http://rdfh.ch/projects/0001",
      classes = Seq(
        RestrictedClass(
          id = "http://www.knora.org/ontology/0001/anything#Thing",
          label = "Thing",
          ontology = Some("anything"),
          totalResources = 120,
          counts = AudienceRestrictionCounts(
            anonymous = RestrictionCounts(7, 3),
            authenticated = RestrictionCounts(2, 1),
            projectMember = RestrictionCounts(0, 0),
          ),
        ),
      ),
    )
  }

  /** Step 2: one class's value-level counts, from a single query. */
  final case class ViewRestrictionsValues(
    projectIri: String,
    resourceClass: String,
    itemType: ValueItemType,
    counts: AudienceRestrictionCounts,
  )
  object ViewRestrictionsValues {
    given JsonCodec[ViewRestrictionsValues] = DeriveJsonCodec.gen[ViewRestrictionsValues]
    given Schema[ViewRestrictionsValues]    = Schema.derived[ViewRestrictionsValues]

    val example: ViewRestrictionsValues = ViewRestrictionsValues(
      projectIri = "http://rdfh.ch/projects/0001",
      resourceClass = "http://www.knora.org/ontology/0001/anything#Thing",
      itemType = ValueItemType.All,
      counts = AudienceRestrictionCounts(
        anonymous = RestrictionCounts(12, 4),
        authenticated = RestrictionCounts(5, 2),
        projectMember = RestrictionCounts(0, 0),
      ),
    )
  }

  /**
   * One row of the summary matrix: a resource class or a property.
   *
   * `totalResources` is the size of the group's resource population — how many resources the project has in
   * that class in total, restricted or not. It is the denominator for `counts.<audience>.resources` (the
   * only figure in the same unit); `counts.<audience>.items` counts values and is NOT a share of it. It is
   * deliberately independent of both the restrictions and the `itemType` filter: filtering changes which
   * restrictions are reported, not how many resources a class holds.
   *
   * It is only defined in `groupBy=ResourceClass` mode; a property has no resource population of its own, so
   * it is absent (`None`) when grouping by property.
   *
   * In class mode every class is reported, so a row may have zero counts across the board and still carry a
   * non-zero `totalResources` — that is a class with nothing restricted, not an empty row.
   */
  final case class RestrictionGroup(
    id: String,                   // class IRI, or property IRI in property mode
    label: String,                // human-readable label
    ontology: Option[String],     // short ontology label (class mode)
    propertyName: Option[String], // e.g. "anything:hasPicture" (property mode)
    counts: AudienceCounts,
    totalResources: Option[Int], // resources in this class, project-wide (class mode only)
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
          counts = AudienceCounts(
            // 7 of the class's 120 resources are hidden outright, and 12 values inside other resources are
            // hidden too — two different facts, in two different units.
            anonymous = UnitCounts(RestrictionCounts(7, 3), RestrictionCounts(12, 4)),
            authenticated = UnitCounts(RestrictionCounts(2, 1), RestrictionCounts(5, 2)),
            projectMember = UnitCounts(RestrictionCounts(0, 0), RestrictionCounts(0, 0)),
          ),
          totalResources = Some(120),
        ),
      ),
      totals = AudienceCounts(
        anonymous = UnitCounts(RestrictionCounts(7, 3), RestrictionCounts(12, 4)),
        authenticated = UnitCounts(RestrictionCounts(2, 1), RestrictionCounts(5, 2)),
        projectMember = UnitCounts(RestrictionCounts(0, 0), RestrictionCounts(0, 0)),
      ),
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
