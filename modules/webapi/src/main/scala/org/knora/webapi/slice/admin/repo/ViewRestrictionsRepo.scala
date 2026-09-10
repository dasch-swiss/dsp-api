/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.ehcache.config.builders.ExpiryPolicyBuilder
import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ProjectClasses
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.EhCache
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/**
 * Reads a project's view-restriction data.
 *
 * Two access patterns, deliberately different in shape:
 *
 *   - **Counts** ([[resourceCountsByClass]] + [[valueCountsForClass]]): computed by the triplestore and
 *     grouped by `knora-base:hasPermissions`. Because an object's visibility for the three synthetic
 *     audiences depends only on that literal (see [[ViewRestrictionsService]]), one grouped query answers
 *     every audience and both restriction states at once, and a group's whole population falls out of the
 *     same rows. No per-object data crosses the wire, so counts are exact at any project size.
 *   - **Drill-down** ([[findRestrictedObjects]] + [[countRestrictedResources]]): genuinely paginated. The
 *     query is always narrowed to one resource class, ordered deterministically, and windowed with
 *     `LIMIT`/`OFFSET` in SPARQL, with the page total from a matching `COUNT`.
 *
 * Values carry their own `hasPermissions`/`attachedToUser`, so resources and values are queried separately.
 * File values are distinguished by `rdfs:subClassOf* knora-base:FileValue`; a value carrying
 * `knora-base:valueHasComment` additionally yields a comment item (a comment is a plain literal on the
 * value and is not independently permissioned, so its visibility equals its parent value's).
 */
final case class ViewRestrictionsRepo(
  private val triplestore: TriplestoreService,
  private val projectClassesCache: EhCache[ProjectIri, ProjectClasses],
) {

  /**
   * Resolves the project's asserted resource classes, and whether the most-specific-class filter is
   * needed at all — see [[ViewRestrictionsRepo.ProjectClasses]].
   *
   * Cached per project, briefly. The stepped report calls this once for the class list and then once per
   * class for that class's value counts, so on a 43-class project an uncached resolve is paid 44 times for
   * an answer that is identical every time. The TTL is what keeps a newly added resource class from being
   * invisible until restart: it only has to outlive one report run, not the deployment.
   */
  def projectClasses(projectIri: ProjectIri): Task[ProjectClasses] =
    ZIO.succeed(projectClassesCache.get(projectIri)).flatMap {
      case Some(cached) => ZIO.succeed(cached)
      case None         =>
        resolveProjectClasses(projectIri).tap(resolved => ZIO.succeed(projectClassesCache.put(projectIri, resolved)))
    }

  private def resolveProjectClasses(projectIri: ProjectIri): Task[ProjectClasses] =
    for {
      iris <- triplestore
                .query(ViewRestrictionsRepo.projectClassesQuery(projectIri))
                .map(_.flatMap(_.get("resClass")))
      // Gated: the `subClassOf+` probe costs 27.3s on LHTT when the answer is "no", and this weaker
      // check settles that case in 1.4s. Only a project that actually has a multi-typed resource pays
      // for the traversal. See ViewRestrictionsRepo.anyMultiTypedResourceQuery.
      anyMultiTyped <-
        triplestore
          .query(ViewRestrictionsRepo.anyMultiTypedResourceQuery(projectIri))
          .map(_.nonEmpty)
      multi <- if (anyMultiTyped) triplestore.query(ViewRestrictionsRepo.multiTypedQuery(projectIri)).map(_.nonEmpty)
               else ZIO.succeed(false)
    } yield ProjectClasses(iris, multi)

  /**
   * Step 1 of the stepped report: every class's resource counts, broken down by permission literal, in a
   * single unchunked query.
   *
   * One query rather than a fan-out, because grouping by the literal makes the six former
   * (audience, state) counts and the separate population count all derivable from one row set — see
   * [[ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery]]. A class present in the project but
   * carrying no resources simply yields no rows; the caller reports it with a zero population.
   */
  def resourceCountsByClass(
    projectIri: ProjectIri,
    classes: ProjectClasses,
  ): Task[Seq[PermissionCountRow]] =
    triplestore
      .query(ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery(projectIri, classes))
      .map(_.flatMap(row => permissionCountRow(row, groupCol = Some("resClass"))))

  /**
   * Step 2 of the stepped report: one class's value counts, broken down by permission literal.
   *
   * Scoped to a single class by the route, so there is nothing to chunk and no grouping key beyond the
   * literal itself.
   */
  def valueCountsForClass(
    projectIri: ProjectIri,
    resourceClass: String,
    itemType: ItemType,
    classes: ProjectClasses,
  ): Task[Seq[PermissionCountRow]] =
    triplestore
      .query(ViewRestrictionsRepo.valueCountsByPermissionQuery(projectIri, resourceClass, itemType, classes))
      .map(_.flatMap(row => permissionCountRow(row, groupCol = None)))

  /**
   * Parses one permission-grouped count row. A row missing `permissions` or with an unparseable `cnt` is
   * dropped rather than failing the request: an aggregate row without its grouping key carries no usable
   * information, and a report is more useful slightly incomplete than not at all.
   */
  private def permissionCountRow(
    row: VariableResultsRow,
    groupCol: Option[String],
  ): Option[PermissionCountRow] =
    for {
      permissions <- row.get("permissions")
      count       <- row.get("cnt").flatMap(_.toIntOption)
      // In step 1 the class column must be present, since it is a grouping key; in step 2 there is none.
      groupId <- groupCol.fold[Option[Option[String]]](Some(None))(col => row.get(col).map(Some(_)))
    } yield PermissionCountRow(groupId, permissions, count)

  /**
   * The distinct resource IRIs of one page of the drill-down, ordered by label then IRI so paging is
   * stable, plus every restriction-bearing row belonging to those resources.
   *
   * Paging is over *resources* (the unit the API returns), not raw rows: the page window is applied in
   * SPARQL to the resource list, then the rows for exactly those resources are fetched.
   */
  def findRestrictedObjects(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    offset: Int,
    limit: Int,
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] = {
    val effective = itemType
    for {
      pageIris <-
        triplestore
          .query(ViewRestrictionsRepo.resourcePageQuery(projectIri, effective, group, offset, limit, classes))
          .map(_.flatMap(_.get("resource")))
      rows <- if (pageIris.isEmpty) ZIO.succeed(Seq.empty[RestrictedObjectRow])
              else fetchRowsFor(projectIri, effective, group, pageIris, classes)
    } yield rows
  }

  private def fetchRowsFor(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] = {
    val wantResources = ViewRestrictionsRepo.wantResources(itemType)
    val wantValues    = ViewRestrictionsRepo.wantValues(itemType)
    for {
      resources <- if (wantResources) runResourceQuery(projectIri, group, resourceIris, classes)
                   else ZIO.succeed(Seq.empty)
      values <- if (wantValues) runValueQuery(projectIri, group, itemType, resourceIris, classes)
                else ZIO.succeed(Seq.empty)
    } yield resources ++ values
  }

  /** Total number of distinct resources the drill-down would return — the exact `totalItems` for paging. */
  def countRestrictedResources(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    classes: ProjectClasses,
  ): Task[Int] = {
    val effective = itemType
    triplestore
      .query(ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, effective, group, classes))
      .map(_.getFirst("cnt").flatMap(_.toIntOption).getOrElse(0))
  }

  private def runResourceQuery(
    projectIri: ProjectIri,
    group: String,
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(ViewRestrictionsRepo.resourceQuery(projectIri, Some(group), resourceIris, classes))
      .map(_.map { row =>
        val resource = row.getRequired("resource")
        val resClass = row.getRequired("resClass")
        val label    = row.get("label").getOrElse(resource)
        RestrictedObjectRow(
          groupId = resClass, // resources always group by their class
          groupLabel = localName(resClass),
          ontology = Some(ontologyName(resClass)),
          propertyName = None,
          resourceIri = resource,
          resourceLabel = label,
          resourceClassIri = resClass,
          itemType = ItemType.Resource,
          propertyIri = None,
          propertyLabel = None,
          valueIri = None,
          creator = row.getRequired("creator"),
          permissions = row.getRequired("permissions"),
        )
      })

  private def runValueQuery(
    projectIri: ProjectIri,
    group: String,
    itemType: ItemType,
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(ViewRestrictionsRepo.valueQuery(projectIri, Some(group), resourceIris, classes))
      .map(_.flatMap { row =>
        val resource   = row.getRequired("resource")
        val resClass   = row.getRequired("resClass")
        val label      = row.get("label").getOrElse(resource)
        val prop       = row.getRequired("prop")
        val value      = row.getRequired("value")
        val creator    = row.getRequired("creator")
        val perms      = row.getRequired("permissions")
        val isFile     = row.get("fileClass").isDefined
        val hasComment = row.get("comment").isDefined

        // A value row yields a File-or-Value item, and — if it carries a comment — a Comment item too.
        // The comment shares the value's permissions (a literal on the value, not independently permissioned).
        val baseType        = if (isFile) ItemType.File else ItemType.Value
        def mk(t: ItemType) = RestrictedObjectRow(
          groupId = resClass,
          groupLabel = localName(resClass),
          ontology = Some(ontologyName(resClass)),
          propertyName = Some(localName(prop)),
          resourceIri = resource,
          resourceLabel = label,
          resourceClassIri = resClass,
          itemType = t,
          propertyIri = Some(prop),
          propertyLabel = Some(localName(prop)),
          valueIri = Some(value),
          creator = creator,
          permissions = perms,
        )

        val itemRow    = Option.when(itemType == ItemType.All || itemType == baseType)(mk(baseType))
        val commentRow =
          Option.when(hasComment && (itemType == ItemType.All || itemType == ItemType.Comment))(mk(ItemType.Comment))
        Seq(itemRow, commentRow).flatten
      })

  /** Small IRI helpers for labels until the spike wires proper ontology-label lookup. */
  private def localName(iri: String): String    = iri.split(Array('#', '/')).lastOption.getOrElse(iri)
  private def ontologyName(iri: String): String = {
    val beforeHash = iri.split('#').headOption.getOrElse(iri)
    beforeHash.split('/').lastOption.getOrElse(beforeHash)
  }
}

object ViewRestrictionsRepo {

  /**
   * How long a resolved [[ProjectClasses]] stays cached.
   *
   * Sized to outlive one report run — step 1 plus one request per class, at the frontend's concurrency —
   * and nothing more, so an ontology change shows up on the next report rather than the next restart.
   */
  private val ProjectClassesTtl: java.time.Duration = java.time.Duration.ofMinutes(1)

  val layer: URLayer[TriplestoreService & CacheManager, ViewRestrictionsRepo] = ZLayer.fromZIO(
    for {
      cache <- ZIO.serviceWithZIO[CacheManager](
                 _.createCache[ProjectIri, ProjectClasses](
                   "viewRestrictionsProjectClasses",
                   CacheManager
                     .defaultCacheConfigBuilder[ProjectIri, ProjectClasses]()
                     .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(ProjectClassesTtl))
                     .build(),
                 ),
               )
      triplestore <- ZIO.service[TriplestoreService]
    } yield ViewRestrictionsRepo(triplestore, cache),
  )

  /**
   * One row of a permission-grouped count: how many objects carry `permissions`, optionally within a group.
   *
   * `groupId` is the resource-class IRI for the step-1 query, which groups by class as well as by literal,
   * and `None` for the step-2 query, which is already narrowed to a single class by the route.
   *
   * The literal is carried raw and unclassified on purpose: the caller resolves it against each audience
   * with the real permission model, which is what lets one query answer all three audiences and both
   * restriction states at once. No `CountUnit` here — the unit is decided by which query produced the row,
   * so it cannot be mixed up in the first place.
   */
  final case class PermissionCountRow(groupId: Option[String], permissions: String, count: Int)

  /**
   * The resource classes a project actually asserts, resolved once per request and reused by every query
   * of that request.
   *
   * This replaces two `rdfs:subClassOf` traversals that the triplestore would otherwise re-evaluate for
   * every result row (rows scale as resources × properties × values):
   *
   *   - `?resClass rdfs:subClassOf* knora-base:Resource` — restricting `?resClass` to resource classes.
   *     Superseded by binding `?resClass` to this list, which is *already* the set of resource classes the
   *     project uses.
   *   - `FILTER NOT EXISTS { … subClassOf+ … }` — needed only when a project asserts both a class and one
   *     of its ancestors on the same resource, which `multiTyped` records.
   *
   * The list is deliberately the classes **present in the project's data**, not the `Resource` subclass
   * closure from the ontology: `docs/development/dsp-api-sparql-queries.md` (DEV-6803) measured that
   * inlining a large closure as `VALUES` is catastrophic (2.1s → >60s), because the engine joins the whole
   * table against a large intermediate. A project's asserted classes are a small set that *anchors* the
   * scan instead — the shape that doc endorses.
   *
   * @param iris       the project's asserted resource-class IRIs.
   * @param multiTyped whether any resource asserts a class together with a strict subclass of it, in which
   *                   case the most-specific-class filter is still required for correctness.
   */
  final case class ProjectClasses(iris: Seq[String], multiTyped: Boolean) {

    /**
     * `VALUES ?resClass { <…> }`, or the empty fragment when no class was discovered.
     *
     * Empty does **not** mean "no constraint": [[resClassPatterns]] falls back to the original
     * `subClassOf*` guard in that case, so `?resClass` is never left unconstrained. It is a hole rather
     * than literal template text because both its presence and its length are decided per project;
     * `Fragments.values` throws on an empty collection, hence the explicit branch here.
     *
     * Every template interpolates it as the first thing inside its `WHERE` block, where it binds
     * `?resClass` before the patterns that consume it — the position the previous string splice used.
     */
    def valuesClause: Fragment =
      if (iris.isEmpty) Fragment.empty else Fragments.values(Variable("resClass"), iris.map(Iri.unsafeFrom))

    /**
     * The patterns that pin `?resClass`, beyond the `VALUES` clause of [[valuesClause]]. Also a hole
     * rather than template text: which of the two blocks appear is a per-project decision.
     *
     *   - When no class was discovered, the `VALUES` clause is empty, so the original
     *     `?resClass rdfs:subClassOf* knora-base:Resource` guard is emitted instead. Dropping both would
     *     leave `?resClass` matching every asserted type — including value and non-resource classes —
     *     and inflate every count.
     *   - The most-specific-class filter keeps only the most specific asserted class of a resource, so one
     *     resource yields exactly one `?resClass` binding even when the triplestore asserts or infers its
     *     superclasses too. It is added only when the project's data can actually produce an ambiguous
     *     binding (see [[multiTypedQuery]]) *and* the caller's result depends on how many rows a resource
     *     contributes — see `dedupeRows`. The filter is a `subClassOf+` traversal re-evaluated per result
     *     row — rows scale as resources × properties × values — so on the common case where every resource
     *     carries exactly one class it is pure overhead and is left out entirely (measured on
     *     `incunabula`: 2.46s → 0.78s for that pattern alone).
     *
     * @param dedupeRows whether the caller needs one `?resClass` binding per resource. True for the
     *                   counting and paging queries, whose answer changes if a multi-typed resource is
     *                   counted or listed once per class in its hierarchy. False for
     *                   `SELECT DISTINCT ?permissions`, which projects only the permission literal, so
     *                   duplicate rows collapse in `DISTINCT` and the filter cannot change the result set
     *                   — it is a row *reducer*, and reducing rows cannot add or remove a literal that
     *                   some other row still carries.
     */
    def resClassPatterns(dedupeRows: Boolean): Fragment =
      Seq(
        Option.when(iris.isEmpty)(sparql"?resClass rdfs:subClassOf* knora-base:Resource ."),
        Option.when(multiTyped && dedupeRows)(
          sparql"""|FILTER NOT EXISTS {
                   |  ?resource a ?subClass .
                   |  ?subClass rdfs:subClassOf+ ?resClass .
                   |}""",
        ),
      ).flatten.joinLines
  }

  /**
   * One restriction-bearing object as read from the triplestore, before visibility resolution.
   *
   * @param groupId     the grouping key — resource-class IRI (class mode) or property IRI (property mode).
   * @param itemType    which kind of object this row represents.
   * @param creator     `knora-base:attachedToUser` of the object (for permission resolution).
   * @param permissions the `knora-base:hasPermissions` literal (for permission resolution).
   */
  final case class RestrictedObjectRow(
    groupId: String,
    groupLabel: String,
    ontology: Option[String],
    propertyName: Option[String],
    resourceIri: String,
    resourceLabel: String,
    resourceClassIri: String,
    itemType: ItemType,
    propertyIri: Option[String],
    propertyLabel: Option[String],
    valueIri: Option[String],
    creator: String,
    permissions: String,
  )

  /** Whether the drill-down includes whole-resource rows under the active filter. */
  private[repo] def wantResources(itemType: ItemType): Boolean = {
    val t = itemType
    t == ItemType.All || t == ItemType.Resource
  }

  private[repo] def wantValues(itemType: ItemType): Boolean = {
    val t = itemType
    t == ItemType.All || t == ItemType.File || t == ItemType.Value || t == ItemType.Comment
  }

  /**
   * A permission-literal grants View-or-better to anonymous users when a `V`/`M`/`D`/`CR` clause lists
   * `knora-admin:UnknownUser` (groups within a clause are comma-separated, clauses are `|`-separated).
   * Such an object is fully visible to all three audiences, so it contributes 0 to every count and can be
   * dropped in the query — this keeps every query proportional to the number of *restrictions* rather than
   * the total number of values. The authoritative per-audience decision still happens in Scala via
   * PermissionUtilADM; this only removes provably-open rows, so it is conservative.
   *
   * The drill-down queries use it in `FILTER(!REGEX(?permissions, …))`; the count queries deliberately do
   * not, since keeping every literal is what makes a group's whole population derivable from its rows.
   */
  private val grantsViewToAnonymousRegex = "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser"

  /**
   * Narrows a value pattern to one item type. `File`/`Value` need the file-ness of the value decided in
   * the query (not just reported), and `Comment` needs the comment to exist; `All` and `Resource` add
   * nothing, and their templates then also omit the group that would isolate the skeleton from a
   * constraint — there is nothing to separate.
   */
  private def itemTypeConstraint(itemType: ItemType): Option[Fragment] =
    itemType match {
      case ItemType.File =>
        Some(sparql"""|{
                      |  ?value a ?fileClass .
                      |  ?fileClass rdfs:subClassOf* knora-base:FileValue .
                      |}""")
      case ItemType.Value =>
        Some(sparql"""|FILTER NOT EXISTS {
                      |  ?value a ?fileClass .
                      |  ?fileClass rdfs:subClassOf* knora-base:FileValue .
                      |}""")
      case ItemType.Comment => Some(sparql"?value knora-base:valueHasComment ?comment .")
      case _                => None
    }

  /*
   * The queries below repeat the same two WHERE skeletons — a project's current, non-deleted resources,
   * and its current, non-deleted values reached through a sub-property of `knora-base:hasValue` — rather
   * than sharing them, so that each template can be read as one whole piece of SPARQL. The count queries
   * and the row queries must nevertheless describe the same universe: a count that matched a different row
   * set than the drill-down would be worse than no count, and `ViewRestrictionsQuerySpec` pins that.
   *
   * Two skeleton details are load-bearing and repeated deliberately in every template:
   *
   *   - `?resClass` is pinned to the resource's **most specific asserted** class whenever the caller's
   *     answer depends on how many rows a resource contributes — see `ProjectClasses.resClassPatterns`.
   *     Without that, a resource asserted as (or inferred to be) several classes in one hierarchy binds
   *     `?resClass` once per class, which double-counts it in the aggregated summary and duplicates it in
   *     the drill-down.
   *   - `knora-base:attachedToUser ?creator` is present only where the creator is projected or
   *     constrained. In the permission probes it is an unconstrained join whose only effect is to multiply
   *     intermediate rows, and the creator cannot change the decision for a synthetic audience anyway.
   *
   * NOTE — graph scoping does NOT apply to any of them, and must not be introduced.
   * `CONVENTIONS.md` records that `GRAPH <projectDataGraph>` replaces an `attachedToProject` join
   * (DEV-6827: 5.4×), and that holds for a caller which already knows the single graph it wants — the v2
   * write path writes into one. It does **not** hold for a project-wide read: a project's resources span
   * one data graph per ontology, while `ProjectService.projectDataNamedGraphV2` derives exactly one from
   * shortcode + shortname. Measured on the local `anything` project: 65 resources in
   * `…/data/0001/anything` and 6 more in `…/data/0001/freetest`, so scoping to the derived graph
   * undercounts by those 6 — silently, since a graph with no matches yields no rows rather than an error.
   * `ViewRestrictionsQuerySpec` pins that every query keeps the join.
   *
   * Every query of this report runs on `SparqlTimeout.ViewRestrictions` rather than the standard tier —
   * these are whole-project scans grouped by permission literal, not the bounded lookups 20s is sized for.
   */

  /**
   * `SELECT DISTINCT ?resClass` — the resource classes the project actually asserts.
   *
   * The `subClassOf*` guard is **not** redundant: `attachedToProject` is also carried by list nodes and by
   * the project's own ontologies, so without it `?resClass` would pick up `knora-base:ListNode` and
   * `owl:Ontology` and inflate every downstream count.
   *
   * It is, however, the reason this query timed out on a large project (Fuseki-cancelled at the 20s
   * standard tier). A property path splits the BGP and is never reordered across, so with the guard in the
   * same group the store first materializes one row per (resource, asserted type) for the *whole* project
   * and then probes the closure once per row — the cross-join shape
   * `docs/development/dsp-api-sparql-queries.md` records under "Anchor property paths" (DEV-6803, the same
   * `subClassOf* knora-base:Resource` pattern, >60s).
   *
   * The sub-select fixes the row count rather than the path: Fuseki evaluates a sub-select bottom-up, so
   * the project scan is deduplicated to the handful of classes the project uses *before* the closure walk
   * sees them, and the walk runs once per class instead of once per resource-type pair. The result set is
   * identical — `DISTINCT ?resClass` over a set the outer pattern only filters.
   *
   * The `attachedToProject` join stays inside the sub-select rather than becoming a `GRAPH` scope: a
   * project's resources span one data graph per ontology, so a single derived graph undercounts.
   */
  private[repo] def projectClassesQuery(projectIri: ProjectIri): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT DISTINCT ?resClass
               |WHERE {
               |  {
               |    SELECT DISTINCT ?resClass
               |    WHERE {
               |      ?resource knora-base:attachedToProject $project ;
               |        a ?resClass .
               |    }
               |  }
               |  ?resClass rdfs:subClassOf* knora-base:Resource .
               |}""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /**
   * Does any non-deleted resource of the project carry **two distinct types at all**?
   *
   * A necessary condition for [[multiTypedQuery]]: asserting a class together with a strict subclass of it
   * entails having two distinct types. So `false` here settles `multiTypedQuery` as `false` too, without
   * the `subClassOf+` traversal — and it is the common case, since most resources carry exactly one class.
   *
   * Strictly weaker on purpose, so it can only ever over-approximate: a `true` answer proves nothing and
   * the real traversal still runs. That keeps the correctness argument in [[multiTypedQuery]] intact —
   * notably that `?subClass` must stay unconstrained.
   *
   * Measured on LHTT: 1.4s, against 27.3s for the query it gates.
   */
  private[repo] def anyMultiTypedResourceQuery(projectIri: ProjectIri): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT ?resource
               |WHERE {
               |  ?resource a ?c1 ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:isDeleted false ;
               |    a ?c2 .
               |  FILTER (?c1 != ?c2)
               |}
               |LIMIT 1""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /**
   * Whether the most-specific-class filter can actually change the answer for this project: does any
   * non-deleted resource assert a class together with a **strict subclass** of that class?
   *
   * This mirrors the gated filter exactly, which is what makes omitting the filter sound. In particular
   * `?subClass` is **not** restricted to the project's discovered classes: the filter's own `?subClass` is
   * unconstrained, so a resource typed with a subclass that is not itself a resource class (and so never
   * appears in [[projectClassesQuery]]) still makes the filter load-bearing. Narrowing the probe to the
   * discovered list would answer "no" for exactly that case and silently inflate the counts.
   *
   * The `isDeleted false` guard matches the query skeletons: a deleted resource can never produce a
   * `?resClass` binding there, so it must not drag the expensive filter back on for the request.
   *
   * PERFORMANCE — the `LIMIT 1` does **not** bound this. It stops at the first hit, but when the answer is
   * "no" there is no hit to stop at, so the store must exhaust the search space to prove it: measured 27.3s
   * on LHTT (105,983 resources) returning nothing. Anchoring `?resClass` with a `VALUES` clause makes it
   * worse, not better (36.7s) — the closure-as-VALUES shape `CONVENTIONS.md` warns about.
   *
   * So [[anyMultiTypedResourceQuery]] gates it: that probe is a strictly weaker condition, cheap because it
   * needs no path, and a negative answer settles this one. See [[projectClasses]].
   */
  private[repo] def multiTypedQuery(projectIri: ProjectIri): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT ?resource
               |WHERE {
               |  ?resource a ?resClass ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:isDeleted false ;
               |    a ?subClass .
               |  ?subClass rdfs:subClassOf+ ?resClass .
               |}
               |LIMIT 1""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /**
   * `SELECT ?resClass ?permissions (COUNT(DISTINCT ?resource) AS ?cnt) … GROUP BY ?resClass ?permissions`
   * over all of a project's current resources — the whole of step 1 in one query.
   *
   * One query rather than a fan-out: grouping by the literal makes the six former (audience, state) counts
   * and the separate population count all derivable from one row set. No permission filter at all, which
   * is exactly what makes a class's whole population the sum of its rows.
   */
  private[repo] def resourceCountsByClassAndPermissionQuery(
    projectIri: ProjectIri,
    classes: ProjectClasses,
  ): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT ?resClass ?permissions (COUNT(DISTINCT ?resource) AS ?cnt)
               |WHERE {
               |  ${classes.valuesClause}
               |  ?resource a ?resClass ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:hasPermissions ?permissions ;
               |    knora-base:isDeleted false .
               |  ${classes.resClassPatterns(dedupeRows = true)}
               |}
               |GROUP BY ?resClass ?permissions""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /**
   * `SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt) … GROUP BY ?permissions` over the values of ONE
   * resource class — the whole of step 2 for that class in one query.
   *
   * Narrowed to a single class by a `FILTER (?resClass = <iri>)` rather than by chunking: the route is the
   * unit of work now, so there is no second axis to split on.
   *
   * NOTE: values are reached through `?prop rdfs:subPropertyOf* knora-base:hasValue` with `?prop` unbound.
   * That is a knowingly accepted cost (see the PRD's Constraints): `CONVENTIONS.md` warns an unanchored
   * property path cross-joins against the whole closure, and it is the first thing to revisit if a class
   * turns out to be too slow.
   *
   * Two templates, chosen by whether an item-type constraint applies. The skeleton is wrapped in a group of
   * its own exactly when a constraint follows it — the constraint then cannot be merged into the skeleton's
   * basic graph pattern, nor reordered against its triples — and left bare when there is none.
   */
  private[repo] def valueCountsByPermissionQuery(
    projectIri: ProjectIri,
    resourceClass: String,
    itemType: ItemType,
    classes: ProjectClasses,
  ): Select = {
    val project    = Iri.unsafeFrom(projectIri.value)
    val groupClass = Iri.unsafeFrom(resourceClass)
    itemTypeConstraint(itemType) match {
      case None =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt)
                   |WHERE {
                   |  ${classes.valuesClause}
                   |  ?resource a ?resClass ;
                   |    knora-base:attachedToProject $project ;
                   |    knora-base:isDeleted false .
                   |  ${classes.resClassPatterns(dedupeRows = true)}
                   |  {
                   |    ?resource ?prop ?value .
                   |    ?prop rdfs:subPropertyOf* knora-base:hasValue .
                   |  }
                   |  ?value knora-base:hasPermissions ?permissions ;
                   |    knora-base:isDeleted false .
                   |  FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |  FILTER (?resClass = $groupClass)
                   |}
                   |GROUP BY ?permissions""".render,
          SparqlTimeout.ViewRestrictions,
        )
      case Some(constraint) =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt)
                   |WHERE {
                   |  ${classes.valuesClause}
                   |  {
                   |    ?resource a ?resClass ;
                   |      knora-base:attachedToProject $project ;
                   |      knora-base:isDeleted false .
                   |    ${classes.resClassPatterns(dedupeRows = true)}
                   |    {
                   |      ?resource ?prop ?value .
                   |      ?prop rdfs:subPropertyOf* knora-base:hasValue .
                   |    }
                   |    ?value knora-base:hasPermissions ?permissions ;
                   |      knora-base:isDeleted false .
                   |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |  }
                   |  $constraint
                   |  FILTER (?resClass = $groupClass)
                   |}
                   |GROUP BY ?permissions""".render,
          SparqlTimeout.ViewRestrictions,
        )
    }
  }

  /**
   * One page of distinct resource IRIs for the drill-down, ordered by label then IRI so that paging is
   * reproducible, windowed in SPARQL with `LIMIT`/`OFFSET`.
   *
   * A resource qualifies if it is itself restricted or carries a restricted value under the active filter,
   * mirroring what the row queries return — hence three templates, one per branch shape the filter
   * produces: whole resources only, values only, or a `UNION` of both. Paging is over *resources*, the unit
   * the API returns, so that a resource can never straddle a page boundary.
   *
   * The restriction filter stays inside the branch, i.e. before the label `OPTIONAL` is joined in.
   * [[resourceCountForDrillDownQuery]] repeats these branches verbatim so the page and its total cannot
   * describe different row sets.
   */
  private[repo] def resourcePageQuery(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    offset: Int,
    limit: Int,
    classes: ProjectClasses,
  ): Select = {
    val project    = Iri.unsafeFrom(projectIri.value)
    val groupClass = Iri.unsafeFrom(group)
    val open       = Literal.string(grantsViewToAnonymousRegex)
    val sparqlText =
      if (!wantValues(itemType))
        // Resource-only filter: the resource itself must be restricted.
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |
                 |SELECT DISTINCT ?resource ?labelOrIri
                 |WHERE {
                 |  ${classes.valuesClause}
                 |  {
                 |    ?resource a ?resClass ;
                 |      knora-base:attachedToProject $project ;
                 |      knora-base:attachedToUser ?creator ;
                 |      knora-base:hasPermissions ?permissions ;
                 |      knora-base:isDeleted false .
                 |    ${classes.resClassPatterns(dedupeRows = true)}
                 |    FILTER (!REGEX(?permissions, $open))
                 |    FILTER (?resClass = $groupClass)
                 |  }
                 |  OPTIONAL { ?resource rdfs:label ?label . }
                 |  BIND(COALESCE(?label, STR(?resource)) AS ?labelOrIri)
                 |}
                 |ORDER BY ASC(?labelOrIri) ASC(?resource)
                 |LIMIT ${Literal.int(limit)}
                 |OFFSET ${Literal.int(offset)}"""
      else if (!wantResources(itemType))
        // Value-only filter: the resource must carry a restricted value of the requested kind. The
        // skeleton gets a group of its own so the item-type constraint cannot merge into its BGP.
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |
                 |SELECT DISTINCT ?resource ?labelOrIri
                 |WHERE {
                 |  ${classes.valuesClause}
                 |  {
                 |    {
                 |      ?resource a ?resClass ;
                 |        knora-base:attachedToProject $project ;
                 |        knora-base:isDeleted false .
                 |      ${classes.resClassPatterns(dedupeRows = true)}
                 |      {
                 |        ?resource ?prop ?value .
                 |        ?prop rdfs:subPropertyOf* knora-base:hasValue .
                 |      }
                 |      ?value knora-base:attachedToUser ?creator ;
                 |        knora-base:hasPermissions ?permissions ;
                 |        knora-base:isDeleted false .
                 |      FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                 |    }
                 |    ${itemTypeConstraint(itemType).whenSome(identity)}
                 |    FILTER (!REGEX(?permissions, $open))
                 |    FILTER (?resClass = $groupClass)
                 |  }
                 |  OPTIONAL { ?resource rdfs:label ?label . }
                 |  BIND(COALESCE(?label, STR(?resource)) AS ?labelOrIri)
                 |}
                 |ORDER BY ASC(?labelOrIri) ASC(?resource)
                 |LIMIT ${Literal.int(limit)}
                 |OFFSET ${Literal.int(offset)}"""
      else
        // Both are in scope: a resource qualifies via its own restriction OR via a restricted value.
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |
                 |SELECT DISTINCT ?resource ?labelOrIri
                 |WHERE {
                 |  ${classes.valuesClause}
                 |  {
                 |    ?resource a ?resClass ;
                 |      knora-base:attachedToProject $project ;
                 |      knora-base:attachedToUser ?creator ;
                 |      knora-base:hasPermissions ?permissions ;
                 |      knora-base:isDeleted false .
                 |    ${classes.resClassPatterns(dedupeRows = true)}
                 |    FILTER (!REGEX(?permissions, $open))
                 |    FILTER (?resClass = $groupClass)
                 |  }
                 |  UNION
                 |  {
                 |    ?resource a ?resClass ;
                 |      knora-base:attachedToProject $project ;
                 |      knora-base:isDeleted false .
                 |    ${classes.resClassPatterns(dedupeRows = true)}
                 |    {
                 |      ?resource ?prop ?value .
                 |      ?prop rdfs:subPropertyOf* knora-base:hasValue .
                 |    }
                 |    ?value knora-base:attachedToUser ?creator ;
                 |      knora-base:hasPermissions ?permissions ;
                 |      knora-base:isDeleted false .
                 |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                 |    FILTER (!REGEX(?permissions, $open))
                 |    FILTER (?resClass = $groupClass)
                 |  }
                 |  OPTIONAL { ?resource rdfs:label ?label . }
                 |  BIND(COALESCE(?label, STR(?resource)) AS ?labelOrIri)
                 |}
                 |ORDER BY ASC(?labelOrIri) ASC(?resource)
                 |LIMIT ${Literal.int(limit)}
                 |OFFSET ${Literal.int(offset)}"""
    Select(sparqlText.render, SparqlTimeout.ViewRestrictions)
  }

  /**
   * `SELECT (COUNT(DISTINCT ?resource) AS ?cnt)` matching [[resourcePageQuery]] — the exact page total, in
   * the same unit that query windows in.
   *
   * The three branch shapes are repeated from [[resourcePageQuery]] verbatim, minus the label `OPTIONAL`,
   * the `BIND` and the window; `ViewRestrictionsQuerySpec` pins that the two restrict identically, so the
   * total cannot come to describe a different row set than the pages do.
   */
  private[repo] def resourceCountForDrillDownQuery(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    classes: ProjectClasses,
  ): Select = {
    val project    = Iri.unsafeFrom(projectIri.value)
    val groupClass = Iri.unsafeFrom(group)
    val open       = Literal.string(grantsViewToAnonymousRegex)
    val sparqlText =
      if (!wantValues(itemType))
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |
                 |SELECT (COUNT(DISTINCT ?resource) AS ?cnt)
                 |WHERE {
                 |  ${classes.valuesClause}
                 |  ?resource a ?resClass ;
                 |    knora-base:attachedToProject $project ;
                 |    knora-base:attachedToUser ?creator ;
                 |    knora-base:hasPermissions ?permissions ;
                 |    knora-base:isDeleted false .
                 |  ${classes.resClassPatterns(dedupeRows = true)}
                 |  FILTER (!REGEX(?permissions, $open))
                 |  FILTER (?resClass = $groupClass)
                 |}"""
      else if (!wantResources(itemType))
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |
                 |SELECT (COUNT(DISTINCT ?resource) AS ?cnt)
                 |WHERE {
                 |  ${classes.valuesClause}
                 |  {
                 |    ?resource a ?resClass ;
                 |      knora-base:attachedToProject $project ;
                 |      knora-base:isDeleted false .
                 |    ${classes.resClassPatterns(dedupeRows = true)}
                 |    {
                 |      ?resource ?prop ?value .
                 |      ?prop rdfs:subPropertyOf* knora-base:hasValue .
                 |    }
                 |    ?value knora-base:attachedToUser ?creator ;
                 |      knora-base:hasPermissions ?permissions ;
                 |      knora-base:isDeleted false .
                 |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                 |  }
                 |  ${itemTypeConstraint(itemType).whenSome(identity)}
                 |  FILTER (!REGEX(?permissions, $open))
                 |  FILTER (?resClass = $groupClass)
                 |}"""
      else
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |
                 |SELECT (COUNT(DISTINCT ?resource) AS ?cnt)
                 |WHERE {
                 |  ${classes.valuesClause}
                 |  {
                 |    ?resource a ?resClass ;
                 |      knora-base:attachedToProject $project ;
                 |      knora-base:attachedToUser ?creator ;
                 |      knora-base:hasPermissions ?permissions ;
                 |      knora-base:isDeleted false .
                 |    ${classes.resClassPatterns(dedupeRows = true)}
                 |    FILTER (!REGEX(?permissions, $open))
                 |    FILTER (?resClass = $groupClass)
                 |  }
                 |  UNION
                 |  {
                 |    ?resource a ?resClass ;
                 |      knora-base:attachedToProject $project ;
                 |      knora-base:isDeleted false .
                 |    ${classes.resClassPatterns(dedupeRows = true)}
                 |    {
                 |      ?resource ?prop ?value .
                 |      ?prop rdfs:subPropertyOf* knora-base:hasValue .
                 |    }
                 |    ?value knora-base:attachedToUser ?creator ;
                 |      knora-base:hasPermissions ?permissions ;
                 |      knora-base:isDeleted false .
                 |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                 |    FILTER (!REGEX(?permissions, $open))
                 |    FILTER (?resClass = $groupClass)
                 |  }
                 |}"""
    Select(sparqlText.render, SparqlTimeout.ViewRestrictions)
  }

  /**
   * The restriction-bearing resource rows for an explicit set of resource IRIs (one drill-down page).
   * Unbounded by design: the IRI list is already the page window.
   */
  private[repo] def resourceQuery(
    projectIri: ProjectIri,
    group: Option[String],
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
               |WHERE {
               |  ${classes.valuesClause}
               |  ?resource a ?resClass ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:attachedToUser ?creator ;
               |    knora-base:hasPermissions ?permissions ;
               |    knora-base:isDeleted false .
               |  ${classes.resClassPatterns(dedupeRows = true)}
               |  OPTIONAL { ?resource rdfs:label ?label . }
               |  FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
               |  FILTER (?resource IN (${Fragment
          .join(resourceIris.map(Iri.unsafeFrom(_).toFragment), Fragment.raw(", "))}))
               |  ${group.whenSome(g => sparql"FILTER (?resClass = ${Iri.unsafeFrom(g)})")}
               |}""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /**
   * The restriction-bearing value rows for an explicit set of resource IRIs (one drill-down page).
   * `?fileClass` is bound when the value is a `knora-base:FileValue`, `?comment` when it carries a
   * `knora-base:valueHasComment` — both reported rather than filtered on, since this query returns every
   * kind of value and the caller decides which item rows a value yields.
   */
  private[repo] def valueQuery(
    projectIri: ProjectIri,
    group: Option[String],
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
               |WHERE {
               |  ${classes.valuesClause}
               |  ?resource a ?resClass ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:isDeleted false .
               |  ${classes.resClassPatterns(dedupeRows = true)}
               |  {
               |    ?resource ?prop ?value .
               |    ?prop rdfs:subPropertyOf* knora-base:hasValue .
               |  }
               |  ?value knora-base:attachedToUser ?creator ;
               |    knora-base:hasPermissions ?permissions ;
               |    knora-base:isDeleted false .
               |  FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
               |  OPTIONAL { ?resource rdfs:label ?label . }
               |  OPTIONAL {
               |    ?value a ?fileClass .
               |    ?fileClass rdfs:subClassOf* knora-base:FileValue .
               |  }
               |  OPTIONAL { ?value knora-base:valueHasComment ?comment . }
               |  FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
               |  FILTER (?resource IN (${Fragment
          .join(resourceIris.map(Iri.unsafeFrom(_).toFragment), Fragment.raw(", "))}))
               |  ${group.whenSome(g => sparql"FILTER (?resClass = ${Iri.unsafeFrom(g)})")}
               |}""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }
}
