/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.Variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.ehcache.config.builders.ExpiryPolicyBuilder
import zio.*

import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ProjectClasses
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.EhCache
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

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
) extends QueryBuilderHelper {

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
                .query(Select(ViewRestrictionsRepo.projectClassesQuery(projectIri)))
                .map(_.flatMap(_.get("resClass")))
      // Gated: the `subClassOf+` probe costs 27.3s on LHTT when the answer is "no", and this weaker
      // check settles that case in 1.4s. Only a project that actually has a multi-typed resource pays
      // for the traversal. See ViewRestrictionsRepo.anyMultiTypedResourceQuery.
      anyMultiTyped <- triplestore
                         .query(Select(ViewRestrictionsRepo.anyMultiTypedResourceQuery(projectIri)))
                         .map(_.nonEmpty)
      multi <- if (anyMultiTyped)
                 triplestore.query(Select(ViewRestrictionsRepo.multiTypedQuery(projectIri))).map(_.nonEmpty)
               else ZIO.succeed(false)
    } yield ProjectClasses(iris, multi)

  /** Renders `query` with the project's `VALUES ?resClass { … }` spliced into its WHERE block. */
  private def select(query: SelectQuery, classes: ProjectClasses): Select =
    Select(ViewRestrictionsRepo.withValues(query, classes.valuesClause(variable("resClass"))))

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
      .query(select(ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery(projectIri, classes), classes))
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
      .query(
        select(
          ViewRestrictionsRepo.valueCountsByPermissionQuery(projectIri, resourceClass, itemType, classes),
          classes,
        ),
      )
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
          .query(
            select(
              ViewRestrictionsRepo.resourcePageQuery(projectIri, effective, group, offset, limit, classes),
              classes,
            ),
          )
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
      .query(
        select(
          ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, effective, group, classes),
          classes,
        ),
      )
      .map(_.getFirst("cnt").flatMap(_.toIntOption).getOrElse(0))
  }

  private def runResourceQuery(
    projectIri: ProjectIri,
    group: String,
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(select(ViewRestrictionsRepo.resourceQuery(projectIri, Some(group), resourceIris, classes), classes))
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
      .query(
        select(ViewRestrictionsRepo.valueQuery(projectIri, Some(group), resourceIris, classes), classes),
      )
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

object ViewRestrictionsRepo extends QueryBuilderHelper {

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
   *   - `FILTER NOT EXISTS { … subClassOf+ … }` ([[mostSpecificClass]]) — needed only when a project
   *     asserts both a class and one of its ancestors on the same resource, which `multiTyped` records.
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
     * `VALUES ?resClass { <…> }`, or `None` when no class was discovered.
     *
     * `None` does **not** mean "no constraint": [[resClassPatterns]] falls back to the original
     * `subClassOf*` guard in that case, so `?resClass` is never left unconstrained.
     */
    def valuesClause(resClass: Variable): Option[String] =
      Option.when(iris.nonEmpty)(ViewRestrictionsRepo.valuesOf(resClass, iris))

    /**
     * The patterns that pin `?resClass`, beyond the `VALUES` clause spliced in by [[withValues]].
     *
     *   - When no class was discovered, the `VALUES` clause is omitted, so the original
     *     `?resClass rdfs:subClassOf* knora-base:Resource` guard is emitted instead. Dropping both would
     *     leave `?resClass` matching every asserted type — including value and non-resource classes —
     *     and inflate every count.
     *   - The most-specific-class filter is added only when the project's data can actually produce an
     *     ambiguous binding (see [[multiTypedQuery]]) *and* the caller's result depends on how many rows
     *     a resource contributes — see `dedupeRows`.
     *
     * @param dedupeRows whether the caller needs one `?resClass` binding per resource. True for the
     *                   counting and paging queries, whose answer changes if a multi-typed resource is
     *                   counted or listed once per class in its hierarchy. False for
     *                   `SELECT DISTINCT ?permissions`, which projects only the permission literal, so
     *                   duplicate rows collapse in `DISTINCT` and the filter cannot change the result set
     *                   — it is a row *reducer*, and reducing rows cannot add or remove a literal that
     *                   some other row still carries.
     */
    def resClassPatterns(resource: Variable, resClass: Variable, dedupeRows: Boolean): Seq[GraphPattern] = {
      val classGuard =
        Option.when(iris.isEmpty)(resClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))
      val specific = Option.when(multiTyped && dedupeRows)(mostSpecificClass(resource, resClass))
      (classGuard ++ specific).toSeq
    }
  }

  /** `VALUES ?v { <a> <b> … }` for a set of IRIs. */
  private[repo] def valuesOf(v: Variable, iris: Seq[String]): String =
    s"VALUES ${v.getQueryString} { ${iris.map(i => s"<$i>").mkString(" ")} }"

  /**
   * Splices a `VALUES` clause into a built query's WHERE block.
   *
   * The rdf4j SparqlBuilder cannot express `VALUES` (see the SparqlBuilder limitations in
   * `docs/development/dsp-api-sparql-queries.md`), so the documented string fallback is used. The clause
   * is inserted immediately after the opening brace of the WHERE block, where it binds `?resClass` before
   * the patterns that consume it.
   *
   * The insertion point is located from the `WHERE` keyword rather than the first brace in the query, and
   * a serialization without it is a programming error rather than something to paper over: splicing into
   * an arbitrary brace would silently produce a wrong query.
   */
  private[repo] def withValues(query: SelectQuery, clause: Option[String]): String = {
    val sparql = query.getQueryString
    clause.fold(sparql) { v =>
      val whereAt = sparql.indexOf("WHERE")
      require(whereAt >= 0, s"cannot splice VALUES: no WHERE clause in rendered query:\n$sparql")
      val idx = sparql.indexOf('{', whereAt)
      require(idx >= 0, s"cannot splice VALUES: no group graph pattern after WHERE:\n$sparql")
      s"${sparql.substring(0, idx + 1)}\n$v${sparql.substring(idx + 1)}"
    }
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
   */
  private val grantsViewToAnonymousRegex = "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser"

  /** `FILTER(!REGEX(?permissions, <grantsViewToAnonymous>))` — keep only rows restricted from someone. */
  private def onlyRestricted(permissions: Variable) =
    Expressions.not(Expressions.regex(permissions, Rdf.literalOf(grantsViewToAnonymousRegex)))

  /** `FILTER(?resource IN (<…>, <…>))` — restrict to the resources on the current page. */
  private def resourceIn(resource: Variable, iris: Seq[String]) =
    Expressions.in(resource, iris.map(Rdf.iri)*)

  // ---------------------------------------------------------------------------------------------------
  // Shared WHERE fragments. Kept in one place so the count queries and the row queries can never drift
  // apart — a count that matched a different row set than the drill-down would be worse than no count.
  // ---------------------------------------------------------------------------------------------------

  /**
   * The resource skeleton: a project's current, non-deleted resources with class, creator, permissions.
   *
   * `?resClass` is pinned to the resource's **most specific asserted** class — see [[mostSpecificClass]].
   * Without that, a resource asserted as (or inferred to be) several classes in one hierarchy would bind
   * `?resClass` once per class, which double-counts it in the aggregated summary and duplicates it in the
   * drill-down.
   *
   * `bindCreator = false` drops the `attachedToUser ?creator` pattern — see [[valueCore]].
   *
   * NOTE — graph scoping does NOT apply here, and must not be reintroduced.
   * `CONVENTIONS.md` records that `GRAPH <projectDataGraph>` replaces an `attachedToProject` join
   * (DEV-6827: 5.4×), and that holds for a caller which already knows the single graph it wants — the v2
   * write path writes into one. It does **not** hold for a project-wide read: a project's resources span
   * one data graph per ontology, while `ProjectService.projectDataNamedGraphV2` derives exactly one from
   * shortcode + shortname. Measured on the local `anything` project: 65 resources in
   * `…/data/0001/anything` and 6 more in `…/data/0001/freetest`, so scoping to the derived graph
   * undercounts by those 6 — silently, since a graph with no matches yields no rows rather than an error.
   * `ViewRestrictionsQuerySpec` pins that every query keeps the join.
   */
  private def resourceCore(
    projectIri: ProjectIri,
    resource: Variable,
    resClass: Variable,
    creator: Variable,
    permissions: Variable,
    classes: ProjectClasses,
    dedupeRows: Boolean = true,
    bindCreator: Boolean = true,
  ) = {
    val withProject = resource.isA(resClass).andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
    // The creator keeps its position in the chain rather than being appended, so enabling/disabling it
    // cannot reorder the other patterns.
    val withCreator = if (bindCreator) withProject.andHas(KnoraBase.attachedToUser, creator) else withProject
    withCreator
      .andHas(KnoraBase.hasPermissions, permissions)
      .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
      .and(classes.resClassPatterns(resource, resClass, dedupeRows)*)
  }

  /**
   * `FILTER NOT EXISTS { ?resource a ?subClass . ?subClass rdfs:subClassOf+ ?resClass }` — keeps only the
   * most specific asserted class of a resource, so one resource yields exactly one `?resClass` binding even
   * when the triplestore asserts or infers its superclasses too.
   *
   * Only emitted when the project actually asserts a class together with one of its ancestors
   * ([[ViewRestrictionsRepo.needsMostSpecificClassFilter]]). The filter is a `subClassOf+` traversal
   * re-evaluated per result row — rows scale as resources × properties × values — so on the common case
   * where every resource carries exactly one class it is pure overhead and is left out entirely
   * (measured on `incunabula`: 2.46s → 0.78s for that pattern alone).
   */
  private def mostSpecificClass(resource: Variable, resClass: Variable): GraphPattern = {
    val subClass = variable("subClass")
    GraphPatterns.filterNotExists(
      resource
        .isA(subClass)
        .and(subClass.has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).oneOrMore().build(), resClass)),
    )
  }

  /**
   * The value skeleton: a project's current, non-deleted values reached through a sub-property of
   * `knora-base:hasValue`, with the carrying property captured and link values excluded. Values carry their
   * own creator and permission literal.
   *
   * `?resClass` is pinned to the most specific asserted class for the same reason as in [[resourceCore]]:
   * in class mode it is the grouping key, so a multi-typed resource would otherwise count its values once
   * per class in the hierarchy.
   *
   * `bindCreator = false` drops the `attachedToUser ?creator` pattern for callers that neither project nor
   * constrain the creator — the permission probes. It is an unconstrained join whose only effect there is to
   * multiply intermediate rows.
   *
   * Graph scoping does not apply here either — see [[resourceCore]] for why a project-wide read cannot
   * substitute a single derived data graph for the `attachedToProject` join.
   */
  private def valueCore(
    projectIri: ProjectIri,
    resource: Variable,
    resClass: Variable,
    prop: Variable,
    value: Variable,
    creator: Variable,
    permissions: Variable,
    classes: ProjectClasses,
    dedupeRows: Boolean = true,
    bindCreator: Boolean = true,
  ) = {
    // As in resourceCore, the creator keeps its leading position in the value block so toggling it cannot
    // reorder the remaining patterns.
    val valuePatterns =
      if (bindCreator)
        value
          .has(KnoraBase.attachedToUser, creator)
          .andHas(KnoraBase.hasPermissions, permissions)
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
      else value.has(KnoraBase.hasPermissions, permissions).andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
    resource
      .isA(resClass)
      .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
      .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
      .and(classes.resClassPatterns(resource, resClass, dedupeRows)*)
      .and(
        resource
          .has(prop, value)
          .and(prop.has(zeroOrMore(RDFS.SUBPROPERTYOF), KnoraBase.hasValue)),
      )
      .and(valuePatterns)
      .and(GraphPatterns.filterNotExists(value.isA(KnoraBase.linkValue)))
  }

  /** OPTIONAL `?fileClass`, bound iff the value is (a subclass of) `knora-base:FileValue`. */
  private def optionalFileClass(value: Variable, fileClass: Variable): GraphPattern =
    value
      .isA(fileClass)
      .and(fileClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.FileValue))
      .optional()

  /**
   * Restricts a value pattern to one item type. `File`/`Value` need the file-ness of the value decided in
   * the query (not just reported), and `Comment` needs the comment to exist; `All` adds nothing.
   */
  private def itemTypeConstraint(value: Variable, fileClass: Variable, comment: Variable, itemType: ItemType) = {
    val isFile    = value.isA(fileClass).and(fileClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.FileValue))
    val hasCommnt = value.has(KnoraBase.valueHasComment, comment)
    itemType match {
      case ItemType.File    => Some(isFile)
      case ItemType.Value   => Some(GraphPatterns.filterNotExists(isFile))
      case ItemType.Comment => Some(hasCommnt)
      case _                => None
    }
  }

  private def withGroupFilter(pattern: GraphPattern, col: Variable, group: Option[String]): GraphPattern =
    group.fold(pattern)(g => GraphPatterns.and(pattern).filter(Expressions.equals(col, Rdf.iri(g))))

  // ---------------------------------------------------------------------------------------------------
  // Summary: distinct permission literals, then aggregated counts.
  // ---------------------------------------------------------------------------------------------------

  /**
   * `SELECT DISTINCT ?resClass` — the resource classes the project actually asserts.
   *
   * Anchored on `attachedToProject`, so the `subClassOf*` check runs over the handful of classes the
   * project uses rather than per result row. Feeds [[ProjectClasses]].
   */
  private[repo] def projectClassesQuery(projectIri: ProjectIri): SelectQuery = {
    val (resource, resClass) = (variable("resource"), variable("resClass"))
    Queries
      .SELECT(resClass)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        resource
          .isA(resClass)
          .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
          .and(resClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource)),
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
  private[repo] def anyMultiTypedResourceQuery(projectIri: ProjectIri): SelectQuery = {
    val (resource, c1, c2) = (variable("resource"), variable("c1"), variable("c2"))
    Queries
      .SELECT(resource)
      .prefix(prefix(KnoraBase.NS))
      .where(
        resource
          .isA(c1)
          .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
          .andIsA(c2)
          .filter(Expressions.notEquals(c1, c2)),
      )
      .limit(1)
  }

  /**
   * Whether [[mostSpecificClass]] can actually change the answer for this project: does any non-deleted
   * resource assert a class together with a **strict subclass** of that class?
   *
   * This mirrors the gated filter exactly, which is what makes omitting the filter sound. In particular
   * `?subClass` is **not** restricted to the project's discovered classes: the filter's own `?subClass` is
   * unconstrained, so a resource typed with a subclass that is not itself a resource class (and so never
   * appears in [[projectClassesQuery]]) still makes the filter load-bearing. Narrowing the probe to the
   * discovered list would answer "no" for exactly that case and silently inflate the counts.
   *
   * The `isDeleted false` guard matches [[resourceCore]]/[[valueCore]]: a deleted resource can never
   * produce a `?resClass` binding there, so it must not drag the expensive filter back on for the request.
   *
   * PERFORMANCE — the `LIMIT 1` does **not** bound this. It stops at the first hit, but when the answer is
   * "no" there is no hit to stop at, so the store must exhaust the search space to prove it: measured 27.3s
   * on LHTT (105,983 resources) returning nothing. Anchoring `?resClass` with a `VALUES` clause makes it
   * worse, not better (36.7s) — the closure-as-VALUES shape `CONVENTIONS.md` warns about.
   *
   * So [[anyMultiTypedResourceQuery]] gates it: that probe is a strictly weaker condition, cheap because it
   * needs no path, and a negative answer settles this one. See [[projectClasses]].
   */
  private[repo] def multiTypedQuery(projectIri: ProjectIri): SelectQuery = {
    val (resource, resClass, subClass) = (variable("resource"), variable("resClass"), variable("subClass"))
    Queries
      .SELECT(resource)
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        resource
          .isA(resClass)
          .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
          .andIsA(subClass)
          .and(subClass.has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).oneOrMore().build(), resClass)),
      )
      .limit(1)
  }

  /**
   * `SELECT ?resClass ?permissions (COUNT(DISTINCT ?resource) AS ?cnt) … GROUP BY ?resClass ?permissions`
   * over all of a project's current resources — the whole of step 1 in one query.
   *
   * Built on the same [[resourceCore]] skeleton as the queries it replaces, so it counts the same universe
   * (non-deleted, project-owned, keyed by the resource's most specific asserted class). `bindCreator =
   * false` because the creator cannot change the decision for a synthetic audience, making
   * `attachedToUser ?creator` an unconstrained join that only multiplies intermediate rows — the same
   * reasoning [[valueCore]] already records for the permission probes.
   */
  private[repo] def resourceCountsByClassAndPermissionQuery(
    projectIri: ProjectIri,
    classes: ProjectClasses,
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val cnt                    = variable("cnt")

    Queries
      .SELECT(resClass, permissions, Expressions.count(resource).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns.and(
          resourceCore(
            projectIri,
            resource,
            resClass,
            creator,
            permissions,
            classes,
            bindCreator = false,
          ),
        ),
      )
      .groupBy(resClass, permissions)
  }

  /**
   * `SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt) … GROUP BY ?permissions` over the values of ONE
   * resource class — the whole of step 2 for that class in one query.
   *
   * Narrowed to a single class by a `FILTER (?resClass = <iri>)` rather than by chunking: the route is the
   * unit of work now, so there is no second axis to split on.
   *
   * NOTE: `valueCore` reaches values through `?prop rdfs:subPropertyOf* knora-base:hasValue` with `?prop`
   * unbound. That is a knowingly accepted cost (see the PRD's Constraints): `CONVENTIONS.md` warns an
   * unanchored property path cross-joins against the whole closure, and it is the first thing to revisit
   * if a class turns out to be too slow.
   */
  private[repo] def valueCountsByPermissionQuery(
    projectIri: ProjectIri,
    resourceClass: String,
    itemType: ItemType,
    classes: ProjectClasses,
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (fileClass, comment)   = (variable("fileClass"), variable("comment"))
    val cnt                    = variable("cnt")

    val core = valueCore(
      projectIri,
      resource,
      resClass,
      prop,
      value,
      creator,
      permissions,
      classes,
      bindCreator = false,
    )
    val constrained = itemTypeConstraint(value, fileClass, comment, itemType)
      .fold(GraphPatterns.and(core))(c => GraphPatterns.and(core, c))

    Queries
      .SELECT(permissions, Expressions.count(value).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(constrained.filter(Expressions.equals(resClass, Rdf.iri(resourceClass))))
      .groupBy(permissions)
  }

  /**
   * One page of distinct resource IRIs for the drill-down, ordered by label then IRI so that paging is
   * reproducible, windowed in SPARQL with `LIMIT`/`OFFSET`.
   *
   * A resource qualifies if it is itself restricted or carries a restricted value under the active filter,
   * mirroring what the row queries return.
   */
  private[repo] def resourcePageQuery(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    offset: Int,
    limit: Int,
    classes: ProjectClasses,
  ): SelectQuery = {
    val resource   = variable("resource")
    val labelOrIri = variable("labelOrIri")

    Queries
      .SELECT(resource, labelOrIri)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(drillDownPattern(projectIri, itemType, group, resource, Some(labelOrIri), classes))
      .orderBy(labelOrIri.asc(), resource.asc())
      .offset(offset)
      .limit(limit)
  }

  /** `SELECT (COUNT(DISTINCT ?resource) AS ?cnt)` matching [[resourcePageQuery]] — the exact page total. */
  private[repo] def resourceCountForDrillDownQuery(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    classes: ProjectClasses,
  ): SelectQuery = {
    val resource = variable("resource")
    val cnt      = variable("cnt")

    Queries
      .SELECT(Expressions.count(resource).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(drillDownPattern(projectIri, itemType, group, resource, None, classes))
  }

  /**
   * The set of resources the drill-down covers: those restricted themselves (class mode) or carrying a
   * restricted value under the filter. Shared by the page query and its `COUNT` so the two cannot diverge.
   */
  private def drillDownPattern(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    resource: Variable,
    orderKey: Option[Variable],
    classes: ProjectClasses,
  ): GraphPattern = {
    val resClass               = variable("resClass")
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (fileClass, comment)   = (variable("fileClass"), variable("comment"))
    val label                  = variable("label")

    val valueBranch = {
      val core        = valueCore(projectIri, resource, resClass, prop, value, creator, permissions, classes)
      val constrained = itemTypeConstraint(value, fileClass, comment, itemType)
        .fold(GraphPatterns.and(core))(c => GraphPatterns.and(core, c))
      withGroupFilter(
        constrained.filter(onlyRestricted(permissions)),
        resClass,
        Some(group),
      )
    }

    val branch =
      if (!wantValues(itemType)) {
        // Resource-only filter: the resource itself must be restricted.
        withGroupFilter(
          GraphPatterns
            .and(resourceCore(projectIri, resource, resClass, creator, permissions, classes))
            .filter(onlyRestricted(permissions)),
          resClass,
          Some(group),
        )
      } else if (!wantResources(itemType)) valueBranch
      else {
        // Both are in scope: a resource qualifies via its own restriction OR via a restricted value.
        val resourceBranch = withGroupFilter(
          GraphPatterns
            .and(resourceCore(projectIri, resource, resClass, creator, permissions, classes))
            .filter(onlyRestricted(permissions)),
          resClass,
          Some(group),
        )
        GraphPatterns.union(resourceBranch, valueBranch)
      }

    orderKey.fold(GraphPatterns.and(branch)) { key =>
      // Order by label when present, else the IRI, so unlabelled resources still sort deterministically.
      GraphPatterns
        .and(branch, resource.has(RDFS.LABEL, label).optional())
        .and(Expressions.bind(Expressions.coalesce(label, Expressions.str(resource)), key))
    }
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
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val label                  = variable("label")

    val pattern = GraphPatterns
      .and(resourceCore(projectIri, resource, resClass, creator, permissions, classes))
      .and(resource.has(RDFS.LABEL, label).optional())
      .filter(onlyRestricted(permissions))
      .filter(resourceIn(resource, resourceIris))

    Queries
      .SELECT(resource, resClass, label, creator, permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(withGroupFilter(pattern, resClass, group))
  }

  /**
   * The restriction-bearing value rows for an explicit set of resource IRIs (one drill-down page).
   * `?fileClass` is bound when the value is a `knora-base:FileValue`, `?comment` when it carries a
   * `knora-base:valueHasComment`.
   */
  private[repo] def valueQuery(
    projectIri: ProjectIri,
    group: Option[String],
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (fileClass, comment)   = (variable("fileClass"), variable("comment"))
    val label                  = variable("label")

    val pattern = GraphPatterns
      .and(valueCore(projectIri, resource, resClass, prop, value, creator, permissions, classes))
      .and(resource.has(RDFS.LABEL, label).optional())
      .and(optionalFileClass(value, fileClass))
      .and(value.has(KnoraBase.valueHasComment, comment).optional())
      .filter(onlyRestricted(permissions))
      .filter(resourceIn(resource, resourceIris))

    Queries
      .SELECT(resource, resClass, label, prop, value, fileClass, comment, creator, permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(withGroupFilter(pattern, resClass, group))
  }
}
