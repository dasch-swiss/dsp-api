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
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.CountUnit
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.GroupCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ProjectClasses
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ProjectProperties
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.GroupBy
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * Reads a project's view-restriction data.
 *
 * Two access patterns, deliberately different in shape:
 *
 *   - **Summary** ([[distinctPermissions]] + [[countByGroup]]): counts are computed by the triplestore.
 *     Because the per-audience visibility of an object depends only on its `knora-base:hasPermissions`
 *     literal (see [[ViewRestrictionsService]]), the service first asks for the project's *distinct*
 *     permission literals — a small set, since literals come from project-level default-permission
 *     templates rather than being authored per object — classifies those few literals in Scala with the
 *     real permission model, and then asks the triplestore to `COUNT` the objects whose literal falls in
 *     each visibility state (hidden / restricted view), once per audience. No per-object rows cross the
 *     wire, so the counts are exact at any project size.
 *   - **Drill-down** ([[findRestrictedObjects]] + [[countRestrictedResources]]): genuinely paginated. The
 *     query is always narrowed to one class or property, ordered deterministically, and windowed with
 *     `LIMIT`/`OFFSET` in SPARQL, with the page total from a matching `COUNT`.
 *
 * Values carry their own `hasPermissions`/`attachedToUser`, so resources and values are queried separately.
 * File values are distinguished by `rdfs:subClassOf* knora-base:FileValue`; a value carrying
 * `knora-base:valueHasComment` additionally yields a comment item (a comment is a plain literal on the
 * value and is not independently permissioned, so its visibility equals its parent value's).
 *
 * Grouping: `GroupBy.ResourceClass` groups by resource class; `GroupBy.Property` groups by the property
 * carrying the value (whole-resource rows are not emitted in property mode).
 */
final case class ViewRestrictionsRepo(
  private val triplestore: TriplestoreService,
) extends QueryBuilderHelper {

  /**
   * Resolves the project's asserted resource classes, and whether the most-specific-class filter is
   * needed at all — see [[ViewRestrictionsRepo.ProjectClasses]].
   *
   * Two small anchored queries per request, in exchange for dropping two `rdfs:subClassOf` traversals
   * that the store would otherwise re-evaluate on every result row of every query in that request.
   */
  def projectClasses(projectIri: ProjectIri): Task[ProjectClasses] =
    for {
      iris <- triplestore
                .query(Select(ViewRestrictionsRepo.projectClassesQuery(projectIri)))
                .map(_.flatMap(_.get("resClass")))
      multi <- triplestore
                 .query(Select(ViewRestrictionsRepo.multiTypedQuery(projectIri)))
                 .map(_.nonEmpty)
    } yield ProjectClasses(iris, multi)

  /**
   * The project's value properties, the second axis [[countByGroup]] chunks its value counts along.
   *
   * One small anchored query per request, in exchange for both a finer chunk granularity than the class
   * list can offer and the removal of a `subPropertyOf*` traversal from every value count in that request.
   */
  def projectProperties(projectIri: ProjectIri): Task[ProjectProperties] =
    triplestore
      .query(Select(ViewRestrictionsRepo.projectPropertiesQuery(projectIri)))
      .map(rows => ProjectProperties(rows.flatMap(_.get("prop"))))

  /**
   * The project's distinct `knora-base:hasPermissions` literals among restriction-bearing objects, across
   * both resources and values. The service classifies these (and only these) with
   * [[org.knora.webapi.messages.util.PermissionUtilADM]] to decide which literals mean hidden and which
   * mean restricted view, per audience, then feeds each subset back into [[countByGroup]].
   *
   * Only the literal is projected — no creator, and no per-object data at all. That is sound because the
   * creator cannot change the decision for a synthetic audience user (it only adds the `knora-admin:Creator`
   * group when the requesting user *is* the creator, which these users never are). `ViewRestrictionsServiceSpec`
   * pins that equivalence.
   */
  def distinctPermissions(
    projectIri: ProjectIri,
    itemType: ItemType,
    groupBy: GroupBy,
    classes: ProjectClasses,
  ): Task[Set[String]] = {
    val wantResources = ViewRestrictionsRepo.wantResources(groupBy, itemType)
    val wantValues    = ViewRestrictionsRepo.wantValues(groupBy, itemType)
    for {
      fromResources <-
        if (wantResources)
          runDistinctPermissions(ViewRestrictionsRepo.distinctResourcePermissionsQuery(projectIri, classes), classes)
        else ZIO.succeed(Set.empty[String])
      fromValues <-
        if (wantValues)
          runDistinctPermissions(ViewRestrictionsRepo.distinctValuePermissionsQuery(projectIri, classes), classes)
        else ZIO.succeed(Set.empty[String])
    } yield fromResources ++ fromValues
  }

  private def runDistinctPermissions(query: SelectQuery, classes: ProjectClasses): Task[Set[String]] =
    triplestore.query(select(query, classes)).map(_.map(_.getRequired("permissions")).toSet)

  /** Renders `query` with the project's `VALUES ?resClass { … }` spliced into its WHERE block. */
  private def select(query: SelectQuery, classes: ProjectClasses): Select =
    Select(ViewRestrictionsRepo.withValues(query, classes.valuesClause(variable("resClass"))))

  /** As above, additionally pinning `?prop` for the value counts' second chunking axis. */
  private def select(query: SelectQuery, classes: ProjectClasses, properties: ProjectProperties): Select =
    Select(
      ViewRestrictionsRepo.withValues(
        query,
        (classes.valuesClause(variable("resClass")) ++ properties.valuesClause(variable("prop"))).toSeq,
      ),
    )

  /**
   * Per-group counts of the objects whose permission literal is in `permissions`, computed by the
   * triplestore with `GROUP BY` + `COUNT`. Exact regardless of project size.
   *
   * The caller supplies the literals that resolve to ONE visibility state for ONE audience (see
   * [[ViewRestrictionsService]]), so this is invoked once per (audience, state) pair — it is not
   * hidden-specific. An empty set means no literal resolves to that state, so the query is skipped rather
   * than sent with an empty `FILTER IN` (which would match nothing anyway).
   */
  def countByGroup(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    permissions: Set[String],
    classes: ProjectClasses,
    properties: ProjectProperties,
  ): Task[Seq[GroupCountRow]] =
    if (permissions.isEmpty) ZIO.succeed(Seq.empty)
    else {
      val wantResources = ViewRestrictionsRepo.wantResources(groupBy, itemType)
      val wantValues    = ViewRestrictionsRepo.wantValues(groupBy, itemType)
      // The two results are kept in their own units (see CountUnit) rather than concatenated into one
      // number: they count different things, and the resource figure is the only one comparable to the
      // class's resource population.
      for {
        resourceCounts <-
          if (wantResources)
            // No property axis: a resource-level count has no `?prop` to chunk on.
            runCountByGroup(
              (cs, _) => ViewRestrictionsRepo.resourceCountQuery(projectIri, permissions, cs),
              CountUnit.Resources,
              classes,
              ProjectProperties(Seq.empty),
            )
          else ZIO.succeed(Seq.empty[GroupCountRow])
        valueCounts <-
          if (wantValues)
            runCountByGroup(
              (cs, ps) =>
                ViewRestrictionsRepo
                  .valueCountQuery(
                    projectIri,
                    groupBy,
                    ViewRestrictionsRepo.effectiveItemType(groupBy, itemType),
                    permissions,
                    cs,
                    ps,
                  ),
              CountUnit.Items,
              classes,
              properties,
            )
          else ZIO.succeed(Seq.empty[GroupCountRow])
      } yield resourceCounts ++ valueCounts
    }

  /**
   * How many resources the project has in each resource class, regardless of any restriction — the
   * denominator the summary's per-class counts are read against ("7 of 120 Things are hidden").
   *
   * Only meaningful when grouping by resource class: a property group has no resource population of its own,
   * so the service asks for this in class mode only.
   */
  def totalResourcesByClass(projectIri: ProjectIri, classes: ProjectClasses): Task[Seq[GroupCountRow]] =
    runCountByGroup(
      (cs, _) => ViewRestrictionsRepo.resourceTotalByClassQuery(projectIri, cs),
      CountUnit.Resources,
      classes,
      ProjectProperties(Seq.empty),
    )

  /**
   * Runs one grouped count as a fan-out over chunks of the project's classes, then merges.
   *
   * The whole-project query is a single scan whose cost grows with the project; Fuseki applies
   * `query-timeout` per request, so past some size that one query is cancelled mid-stream and the request
   * fails, however fast the rest of the pipeline is. Splitting the class list bounds each individual query
   * instead: on a 46k-resource / 265k-value project the single query took 8.1s while the slowest chunk took
   * 1.4s, so the same work survives a budget the whole-project form eventually will not.
   *
   * Chunking runs on **two** axes, the cartesian product of class chunks and property chunks. Classes alone
   * have a floor — a chunk of one class cannot be subdivided — so a project with a single very large class
   * still issued one unbounded query. Binding `?prop` splits below that floor, and it also removes the
   * `subPropertyOf*` traversal from the query (see [[ProjectProperties]]): measured on the perftest project,
   * one class went from 232ms to ~110ms with identical counts. Resource-level counts pass an empty
   * [[ProjectProperties]], which yields a single chunk — they have no `?prop` to split on.
   *
   * Merging depends on the grouping key, so every case is handled by summing per `(groupId, unit)`:
   *
   *   - grouping by resource class, the class chunks partition the groups themselves, so a `groupId` occurs
   *     in one class chunk — but in as many property chunks as it has properties, and those are summed;
   *   - grouping by property, the property chunks partition the groups and the class chunks partition the
   *     *rows*, so a property's total is the sum of its per-class-chunk counts.
   *
   * Summing is only sound because each counted object belongs to exactly one chunk on each axis: a value
   * belongs to one resource, `dedupeRows = true` (the default these count queries use) pins a resource to a
   * single `?resClass` binding, and a value is reached through exactly one property, so the property chunks
   * partition the values rather than overlapping them. Were a resource to bind several classes in the
   * hierarchy, its objects would be counted once per chunk. `ViewRestrictionsQuerySpec` pins that the count
   * queries keep the dedup filter.
   */
  private def runCountByGroup(
    query: (ProjectClasses, ProjectProperties) => SelectQuery,
    unit: CountUnit,
    classes: ProjectClasses,
    properties: ProjectProperties,
  ): Task[Seq[GroupCountRow]] = {
    val chunks = for {
      classChunk <- classes.chunked(ViewRestrictionsRepo.ClassChunkSize)
      propChunk  <- properties.chunked(ViewRestrictionsRepo.PropertyChunkSize)
    } yield (classChunk, propChunk)
    ZIO
      .foreachPar(chunks) { case (classChunk, propChunk) =>
        triplestore
          .query(select(query(classChunk, propChunk), classChunk, propChunk))
          .map(_.flatMap { row =>
            row.get("groupId").flatMap(g => row.get("cnt").flatMap(c => c.toIntOption.map(GroupCountRow(g, _, unit))))
          })
      }
      .withParallelism(ViewRestrictionsRepo.MaxConcurrentChunkQueries)
      .map(mergeCounts)
  }

  /** Sums chunk results per `(groupId, unit)`; see [[runCountByGroup]] for why summing is sound. */
  private def mergeCounts(perChunk: Seq[Seq[GroupCountRow]]): Seq[GroupCountRow] =
    perChunk.flatten
      .groupMapReduce(r => (r.groupId, r.unit))(_.count)(_ + _)
      .map { case ((groupId, unit), count) => GroupCountRow(groupId, count, unit) }
      .toSeq

  /**
   * The distinct resource IRIs of one page of the drill-down, ordered by label then IRI so paging is
   * stable, plus every restriction-bearing row belonging to those resources.
   *
   * Paging is over *resources* (the unit the API returns), not raw rows: the page window is applied in
   * SPARQL to the resource list, then the rows for exactly those resources are fetched.
   */
  def findRestrictedObjects(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    group: String,
    offset: Int,
    limit: Int,
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] = {
    val effective = ViewRestrictionsRepo.effectiveItemType(groupBy, itemType)
    for {
      pageIris <-
        triplestore
          .query(
            select(
              ViewRestrictionsRepo.resourcePageQuery(projectIri, groupBy, effective, group, offset, limit, classes),
              classes,
            ),
          )
          .map(_.flatMap(_.get("resource")))
      rows <- if (pageIris.isEmpty) ZIO.succeed(Seq.empty[RestrictedObjectRow])
              else fetchRowsFor(projectIri, groupBy, effective, group, pageIris, classes)
    } yield rows
  }

  private def fetchRowsFor(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    group: String,
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] = {
    val wantResources = ViewRestrictionsRepo.wantResources(groupBy, itemType)
    val wantValues    = ViewRestrictionsRepo.wantValues(groupBy, itemType)
    for {
      resources <- if (wantResources) runResourceQuery(projectIri, group, resourceIris, classes)
                   else ZIO.succeed(Seq.empty)
      values <- if (wantValues) runValueQuery(projectIri, group, groupBy, itemType, resourceIris, classes)
                else ZIO.succeed(Seq.empty)
    } yield resources ++ values
  }

  /** Total number of distinct resources the drill-down would return — the exact `totalItems` for paging. */
  def countRestrictedResources(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    group: String,
    classes: ProjectClasses,
  ): Task[Int] = {
    val effective = ViewRestrictionsRepo.effectiveItemType(groupBy, itemType)
    triplestore
      .query(
        select(
          ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, groupBy, effective, group, classes),
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
    groupBy: GroupBy,
    itemType: ItemType,
    resourceIris: Seq[String],
    classes: ProjectClasses,
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(
        select(ViewRestrictionsRepo.valueQuery(projectIri, Some(group), groupBy, resourceIris, classes), classes),
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
          groupId = if (groupBy == GroupBy.Property) prop else resClass,
          groupLabel = localName(if (groupBy == GroupBy.Property) prop else resClass),
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

  val layer = ZLayer.derive[ViewRestrictionsRepo]

  /**
   * What a [[GroupCountRow]]'s number counts. The two are different units and must never be added
   * together: one resource carrying three hidden values contributes 1 resource and 3 items, and summing
   * those to "4" is a number with no meaning (and one that can exceed the class's resource population).
   */
  enum CountUnit {
    case Resources // distinct resources whose own permissions restrict them
    case Items     // distinct values (files, ordinary values, comments) that are restricted
  }

  /** One row of the aggregated summary: a grouping key, a count, and the unit that count is in. */
  final case class GroupCountRow(groupId: String, count: Int, unit: CountUnit)

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
    /**
     * Splits into groups of at most `size` classes, each carrying the same `multiTyped` flag, so a grouped
     * count can be issued per chunk instead of once for the whole project — see `runCountByGroup`.
     *
     * An empty class list yields a single empty chunk rather than none: with no discovered class the queries
     * fall back to the `subClassOf*` guard ([[resClassPatterns]]) and must still be run once.
     */
    def chunked(size: Int): Seq[ProjectClasses] =
      if (iris.isEmpty) Seq(this)
      else iris.grouped(math.max(1, size)).map(ProjectClasses(_, multiTyped)).toSeq

    def resClassPatterns(resource: Variable, resClass: Variable, dedupeRows: Boolean): Seq[GraphPattern] = {
      val classGuard =
        Option.when(iris.isEmpty)(resClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))
      val specific = Option.when(multiTyped && dedupeRows)(mostSpecificClass(resource, resClass))
      (classGuard ++ specific).toSeq
    }
  }

  /**
   * The value properties a project's data actually uses — the second axis the grouped value counts are
   * chunked along.
   *
   * Class chunking alone has a floor: once a chunk is a single class it cannot be subdivided, so a project
   * with one very large class still issues one query whose cost grows without bound. Binding `?prop` from a
   * discovered list splits *below* that floor, and it simultaneously replaces the
   * `?prop rdfs:subPropertyOf* knora-base:hasValue` traversal — which the store would otherwise re-evaluate
   * per candidate row — with a table the engine can anchor on. Measured on the perftest project, one class
   * went from 232ms (traversal) to ~110ms (bound), returning identical counts.
   *
   * As with [[ProjectClasses]], these are the properties **present in the data**, not the sub-property
   * closure from the ontology: inlining a large ontology closure as `VALUES` is the shape DEV-6803 measured
   * as catastrophic.
   *
   * @param iris the value-property IRIs asserted in the project, empty when none was discovered.
   */
  final case class ProjectProperties(iris: Seq[String]) {

    /**
     * `VALUES ?prop { <…> }`, or `None` when no property was discovered.
     *
     * `None` does **not** mean "no constraint": [[propPatterns]] falls back to the original
     * `subPropertyOf*` traversal in that case, so `?prop` is never left unconstrained.
     */
    def valuesClause(prop: Variable): Option[String] =
      Option.when(iris.nonEmpty)(ViewRestrictionsRepo.valuesOf(prop, iris))

    /**
     * The pattern that pins `?prop` beyond the `VALUES` clause: the original `subPropertyOf*` traversal,
     * emitted only when nothing was discovered. Dropping both would let `?prop` match every predicate on
     * the resource — including `rdf:type` and the admin properties — and inflate every count.
     */
    def propPatterns(prop: Variable): Seq[GraphPattern] =
      Option.when(iris.isEmpty)(prop.has(zeroOrMore(RDFS.SUBPROPERTYOF), KnoraBase.hasValue)).toSeq

    /**
     * Splits into groups of at most `size` properties. An empty list yields a single empty chunk rather
     * than none: the queries must still run once, falling back to the traversal.
     */
    def chunked(size: Int): Seq[ProjectProperties] =
      if (iris.isEmpty) Seq(this)
      else iris.grouped(math.max(1, size)).map(ProjectProperties(_)).toSeq
  }

  /**
   * How many of the project's classes one grouped-count query covers.
   *
   * Small enough that each query stays well inside the triplestore's `query-timeout`, large enough not to
   * pay the fixed per-request overhead once per class: measured on a 46k-resource project, per-class queries
   * bottomed out at ~25ms of overhead each, so grouping a few classes together costs nothing and cuts the
   * number of round-trips.
   */
  private[repo] val ClassChunkSize = 3

  /**
   * How many value properties one grouped value-count query covers — the second chunking axis.
   *
   * Class chunking bottoms out at one class; this is what bounds a query below that. Sized like
   * [[ClassChunkSize]]: large enough to amortise the fixed per-request overhead (~120ms measured), small
   * enough that a single chunk stays far inside the triplestore's `query-timeout`.
   */
  private[repo] val PropertyChunkSize = 4

  /**
   * Concurrent chunk queries per grouped count. Deliberately small: the service already fans out over
   * (audience, state) with its own cap, and these multiply, so a large value here would put the product of
   * the two on the triplestore for a single request.
   */
  private[repo] val MaxConcurrentChunkQueries = 2

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
  private[repo] def withValues(query: SelectQuery, clause: Option[String]): String =
    withValues(query, clause.toSeq)

  /**
   * As above, for several `VALUES` clauses at once (`?resClass` and `?prop`). They are spliced in the
   * given order at the same insertion point, so both bind before the patterns that consume them.
   */
  private[repo] def withValues(query: SelectQuery, clauses: Seq[String]): String = {
    val sparql = query.getQueryString
    if (clauses.isEmpty) sparql
    else {
      val whereAt = sparql.indexOf("WHERE")
      require(whereAt >= 0, s"cannot splice VALUES: no WHERE clause in rendered query:\n$sparql")
      val idx = sparql.indexOf('{', whereAt)
      require(idx >= 0, s"cannot splice VALUES: no group graph pattern after WHERE:\n$sparql")
      s"${sparql.substring(0, idx + 1)}\n${clauses.mkString("\n")}${sparql.substring(idx + 1)}"
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

  /**
   * In property mode there are no whole-resource rows to group under a property, so `itemType=Resource`
   * would return nothing. AC7 requires it to behave like `all` there (surface the value/file/comment rows
   * instead), so coerce Resource → All when grouping by property.
   */
  private[repo] def effectiveItemType(groupBy: GroupBy, itemType: ItemType): ItemType =
    if (groupBy == GroupBy.Property && itemType == ItemType.Resource) ItemType.All else itemType

  /** Whole-resource restrictions are out of scope in property mode. */
  private[repo] def wantResources(groupBy: GroupBy, itemType: ItemType): Boolean = {
    val t = effectiveItemType(groupBy, itemType)
    groupBy == GroupBy.ResourceClass && (t == ItemType.All || t == ItemType.Resource)
  }

  private[repo] def wantValues(groupBy: GroupBy, itemType: ItemType): Boolean = {
    val t = effectiveItemType(groupBy, itemType)
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

  /** `FILTER(?permissions IN ("…", "…"))` — restrict to the literals the service classified for one state. */
  private def permissionsIn(permissions: Variable, literals: Set[String]) =
    Expressions.in(permissions, literals.toSeq.sorted.map(Rdf.literalOf)*)

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
    properties: ProjectProperties = ProjectProperties(Seq.empty),
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
        // `?prop` is pinned by the spliced `VALUES` clause when the project's properties were discovered;
        // propPatterns emits the `subPropertyOf*` traversal only as the fallback for an empty list.
        GraphPatterns.and((resource.has(prop, value) +: properties.propPatterns(prop))*),
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
   * `SELECT DISTINCT ?prop` — the value properties the project's data actually uses.
   *
   * Anchored on `attachedToProject` for the same reason as [[projectClassesQuery]]: the `subPropertyOf*`
   * check runs over the project's predicates once, here, instead of per result row in every count query.
   * Feeds [[ProjectProperties]], which chunks the grouped value counts along this second axis.
   *
   * Link values are excluded to match [[valueCore]] — a property whose only values are link values
   * contributes nothing to these counts, so binding it would just add an empty chunk.
   */
  private[repo] def projectPropertiesQuery(projectIri: ProjectIri): SelectQuery = {
    val (resource, prop, value) = (variable("resource"), variable("prop"), variable("value"))
    Queries
      .SELECT(prop)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        resource
          .has(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
          .andHas(prop, value)
          .and(prop.has(zeroOrMore(RDFS.SUBPROPERTYOF), KnoraBase.hasValue))
          .and(GraphPatterns.filterNotExists(value.isA(KnoraBase.linkValue))),
      )
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
   * The traversal is unavoidable here, but it runs **once per request** and stops at the first hit
   * (`LIMIT 1`) — as opposed to the gated filter, which the store re-evaluates on every result row.
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

  /** `SELECT DISTINCT ?permissions` over a project's restriction-bearing resources. */
  private[repo] def distinctResourcePermissionsQuery(projectIri: ProjectIri, classes: ProjectClasses): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))

    Queries
      .SELECT(permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns
          .and(
            resourceCore(
              projectIri,
              resource,
              resClass,
              creator,
              permissions,
              classes,
              dedupeRows = false,
              bindCreator = false,
            ),
          )
          .filter(onlyRestricted(permissions)),
      )
  }

  /**
   * `SELECT DISTINCT ?permissions` over a project's restriction-bearing values. Independent of `GroupBy`:
   * grouping changes how counts are bucketed, not which literals exist.
   */
  private[repo] def distinctValuePermissionsQuery(projectIri: ProjectIri, classes: ProjectClasses): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))

    Queries
      .SELECT(permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns
          .and(
            valueCore(
              projectIri,
              resource,
              resClass,
              prop,
              value,
              creator,
              permissions,
              classes,
              dedupeRows = false,
              bindCreator = false,
            ),
          )
          .filter(onlyRestricted(permissions)),
      )
  }

  /**
   * `SELECT ?groupId (COUNT(DISTINCT ?resource) AS ?cnt) … GROUP BY ?groupId` over the resources whose
   * permission literal the service classified into the requested state. Exact at any project size.
   */
  private[repo] def resourceCountQuery(
    projectIri: ProjectIri,
    statePermissions: Set[String],
    classes: ProjectClasses,
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (groupId, cnt)         = (variable("groupId"), variable("cnt"))

    Queries
      .SELECT(groupId, Expressions.count(resource).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns
          .and(resourceCore(projectIri, resource, resClass, creator, permissions, classes))
          .filter(permissionsIn(permissions, statePermissions))
          .and(Expressions.bind(resClass, groupId)),
      )
      .groupBy(groupId)
  }

  /**
   * `SELECT ?groupId (COUNT(DISTINCT ?resource) AS ?cnt) … GROUP BY ?groupId` over **all** of a project's
   * current resources, with no permission filter at all — the per-class population size.
   *
   * Deliberately built on the same [[resourceCore]] skeleton as [[resourceCountQuery]], so the restriction
   * count and this total are counted over the same universe (non-deleted, project-owned, keyed by the
   * resource's most specific asserted class) and the ratio between them is meaningful.
   */
  private[repo] def resourceTotalByClassQuery(projectIri: ProjectIri, classes: ProjectClasses): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (groupId, cnt)         = (variable("groupId"), variable("cnt"))

    Queries
      .SELECT(groupId, Expressions.count(resource).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns
          .and(resourceCore(projectIri, resource, resClass, creator, permissions, classes))
          .and(Expressions.bind(resClass, groupId)),
      )
      .groupBy(groupId)
  }

  /**
   * `SELECT ?groupId (COUNT(DISTINCT ?value) AS ?cnt) … GROUP BY ?groupId` over the values whose permission
   * literal the service classified into the requested state.
   *
   * Counts distinct *values*, matching the summary's semantics that a comment is a facet of its parent
   * value rather than a separately counted item — except under `itemType=Comment`, where the comment is
   * the thing being counted and the constraint restricts to commented values.
   */
  private[repo] def valueCountQuery(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    statePermissions: Set[String],
    classes: ProjectClasses,
    properties: ProjectProperties,
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (fileClass, comment)   = (variable("fileClass"), variable("comment"))
    val (groupId, cnt)         = (variable("groupId"), variable("cnt"))
    val groupCol               = if (groupBy == GroupBy.Property) prop else resClass

    val core =
      valueCore(projectIri, resource, resClass, prop, value, creator, permissions, classes, properties = properties)
    val constrained = itemTypeConstraint(value, fileClass, comment, itemType)
      .fold(GraphPatterns.and(core))(c => GraphPatterns.and(core, c))

    Queries
      .SELECT(groupId, Expressions.count(value).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        constrained
          .filter(permissionsIn(permissions, statePermissions))
          .and(Expressions.bind(groupCol, groupId)),
      )
      .groupBy(groupId)
  }

  // ---------------------------------------------------------------------------------------------------
  // Drill-down: a deterministic page of resources, then the rows belonging to that page.
  // ---------------------------------------------------------------------------------------------------

  /**
   * One page of distinct resource IRIs for the drill-down, ordered by label then IRI so that paging is
   * reproducible, windowed in SPARQL with `LIMIT`/`OFFSET`.
   *
   * A resource qualifies if it is itself restricted or carries a restricted value under the active filter,
   * mirroring what the row queries return.
   */
  private[repo] def resourcePageQuery(
    projectIri: ProjectIri,
    groupBy: GroupBy,
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
      .where(drillDownPattern(projectIri, groupBy, itemType, group, resource, Some(labelOrIri), classes))
      .orderBy(labelOrIri.asc(), resource.asc())
      .offset(offset)
      .limit(limit)
  }

  /** `SELECT (COUNT(DISTINCT ?resource) AS ?cnt)` matching [[resourcePageQuery]] — the exact page total. */
  private[repo] def resourceCountForDrillDownQuery(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    group: String,
    classes: ProjectClasses,
  ): SelectQuery = {
    val resource = variable("resource")
    val cnt      = variable("cnt")

    Queries
      .SELECT(Expressions.count(resource).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(drillDownPattern(projectIri, groupBy, itemType, group, resource, None, classes))
  }

  /**
   * The set of resources the drill-down covers: those restricted themselves (class mode) or carrying a
   * restricted value under the filter. Shared by the page query and its `COUNT` so the two cannot diverge.
   */
  private def drillDownPattern(
    projectIri: ProjectIri,
    groupBy: GroupBy,
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
        if (groupBy == GroupBy.Property) prop else resClass,
        Some(group),
      )
    }

    val branch =
      if (!wantValues(groupBy, itemType)) {
        // Resource-only filter: the resource itself must be restricted.
        withGroupFilter(
          GraphPatterns
            .and(resourceCore(projectIri, resource, resClass, creator, permissions, classes))
            .filter(onlyRestricted(permissions)),
          resClass,
          Some(group),
        )
      } else if (!wantResources(groupBy, itemType)) valueBranch
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
    groupBy: GroupBy,
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
      .where(withGroupFilter(pattern, if (groupBy == GroupBy.Property) prop else resClass, group))
  }
}
