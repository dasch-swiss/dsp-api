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
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.GroupCountRow
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
 *     the "hidden" subset. No per-object rows cross the wire, so the counts are exact at any project size.
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
   * The project's distinct `knora-base:hasPermissions` literals among restriction-bearing objects, split
   * by whether they sit on a resource or on a value. The service classifies these (and only these) with
   * [[org.knora.webapi.messages.util.PermissionUtilADM]] to decide which literals mean "hidden" per
   * audience, then feeds the result back into [[countByGroup]].
   *
   * Only the literal is projected — no creator, and no per-object data at all. That is sound because the
   * creator cannot change the decision for a synthetic audience user (it only adds the `knora-admin:Creator`
   * group when the requesting user *is* the creator, which these users never are). `ViewRestrictionsServiceSpec`
   * pins that equivalence.
   */
  def distinctPermissions(projectIri: ProjectIri, itemType: ItemType, groupBy: GroupBy): Task[Set[String]] = {
    val wantResources = ViewRestrictionsRepo.wantResources(groupBy, itemType)
    val wantValues    = ViewRestrictionsRepo.wantValues(groupBy, itemType)
    for {
      fromResources <-
        if (wantResources) runDistinctPermissions(ViewRestrictionsRepo.distinctResourcePermissionsQuery(projectIri))
        else ZIO.succeed(Set.empty[String])
      fromValues <-
        if (wantValues)
          runDistinctPermissions(ViewRestrictionsRepo.distinctValuePermissionsQuery(projectIri))
        else ZIO.succeed(Set.empty[String])
    } yield fromResources ++ fromValues
  }

  private def runDistinctPermissions(query: SelectQuery): Task[Set[String]] =
    triplestore.query(Select(query)).map(_.map(_.getRequired("permissions")).toSet)

  /**
   * Per-group counts of the objects whose permission literal is in `hiddenPermissions`, computed by the
   * triplestore with `GROUP BY` + `COUNT`. Exact regardless of project size.
   *
   * An empty `hiddenPermissions` means nothing is hidden for that audience, so the query is skipped
   * entirely rather than sent with an empty `FILTER IN` (which matches nothing anyway).
   */
  def countByGroup(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    hiddenPermissions: Set[String],
  ): Task[Seq[GroupCountRow]] =
    if (hiddenPermissions.isEmpty) ZIO.succeed(Seq.empty)
    else {
      val wantResources = ViewRestrictionsRepo.wantResources(groupBy, itemType)
      val wantValues    = ViewRestrictionsRepo.wantValues(groupBy, itemType)
      for {
        resourceCounts <-
          if (wantResources)
            runCountByGroup(ViewRestrictionsRepo.resourceCountQuery(projectIri, hiddenPermissions))
          else ZIO.succeed(Seq.empty[GroupCountRow])
        valueCounts <-
          if (wantValues)
            runCountByGroup(
              ViewRestrictionsRepo
                .valueCountQuery(
                  projectIri,
                  groupBy,
                  ViewRestrictionsRepo.effectiveItemType(groupBy, itemType),
                  hiddenPermissions,
                ),
            )
          else ZIO.succeed(Seq.empty[GroupCountRow])
      } yield resourceCounts ++ valueCounts
    }

  private def runCountByGroup(query: SelectQuery): Task[Seq[GroupCountRow]] =
    triplestore
      .query(Select(query))
      .map(_.flatMap { row =>
        row.get("groupId").flatMap(g => row.get("cnt").flatMap(c => c.toIntOption.map(GroupCountRow(g, _))))
      })

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
  ): Task[Seq[RestrictedObjectRow]] = {
    val effective = ViewRestrictionsRepo.effectiveItemType(groupBy, itemType)
    for {
      pageIris <-
        triplestore
          .query(Select(ViewRestrictionsRepo.resourcePageQuery(projectIri, groupBy, effective, group, offset, limit)))
          .map(_.flatMap(_.get("resource")))
      rows <- if (pageIris.isEmpty) ZIO.succeed(Seq.empty[RestrictedObjectRow])
              else fetchRowsFor(projectIri, groupBy, effective, group, pageIris)
    } yield rows
  }

  private def fetchRowsFor(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    group: String,
    resourceIris: Seq[String],
  ): Task[Seq[RestrictedObjectRow]] = {
    val wantResources = ViewRestrictionsRepo.wantResources(groupBy, itemType)
    val wantValues    = ViewRestrictionsRepo.wantValues(groupBy, itemType)
    for {
      resources <- if (wantResources) runResourceQuery(projectIri, group, resourceIris) else ZIO.succeed(Seq.empty)
      values    <- if (wantValues) runValueQuery(projectIri, group, groupBy, itemType, resourceIris)
                else ZIO.succeed(Seq.empty)
    } yield resources ++ values
  }

  /** Total number of distinct resources the drill-down would return — the exact `totalItems` for paging. */
  def countRestrictedResources(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    group: String,
  ): Task[Int] = {
    val effective = ViewRestrictionsRepo.effectiveItemType(groupBy, itemType)
    triplestore
      .query(Select(ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, groupBy, effective, group)))
      .map(_.getFirst("cnt").flatMap(_.toIntOption).getOrElse(0))
  }

  private def runResourceQuery(
    projectIri: ProjectIri,
    group: String,
    resourceIris: Seq[String],
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(Select(ViewRestrictionsRepo.resourceQuery(projectIri, Some(group), resourceIris)))
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
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(Select(ViewRestrictionsRepo.valueQuery(projectIri, Some(group), groupBy, resourceIris)))
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

  /** One row of the aggregated summary: a grouping key and how many of its objects are hidden. */
  final case class GroupCountRow(groupId: String, count: Int)

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

  /** `FILTER(?permissions IN ("…", "…"))` — restrict to the literals the service classified as hidden. */
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
   */
  private def resourceCore(
    projectIri: ProjectIri,
    resource: Variable,
    resClass: Variable,
    creator: Variable,
    permissions: Variable,
  ) =
    resource
      .isA(resClass)
      .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
      .andHas(KnoraBase.attachedToUser, creator)
      .andHas(KnoraBase.hasPermissions, permissions)
      .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
      .and(resClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))
      .and(mostSpecificClass(resource, resClass))

  /**
   * `FILTER NOT EXISTS { ?resource a ?subClass . ?subClass rdfs:subClassOf+ ?resClass }` — keeps only the
   * most specific asserted class of a resource, so one resource yields exactly one `?resClass` binding even
   * when the triplestore asserts or infers its superclasses too.
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
   */
  private def valueCore(
    projectIri: ProjectIri,
    resource: Variable,
    resClass: Variable,
    prop: Variable,
    value: Variable,
    creator: Variable,
    permissions: Variable,
  ) =
    resource
      .isA(resClass)
      .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
      .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
      .and(resClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))
      .and(mostSpecificClass(resource, resClass))
      .and(
        resource
          .has(prop, value)
          .and(prop.has(zeroOrMore(RDFS.SUBPROPERTYOF), KnoraBase.hasValue)),
      )
      .and(
        value
          .has(KnoraBase.attachedToUser, creator)
          .andHas(KnoraBase.hasPermissions, permissions)
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false)),
      )
      .and(GraphPatterns.filterNotExists(value.isA(KnoraBase.LinkValue)))

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

  /** `SELECT DISTINCT ?permissions` over a project's restriction-bearing resources. */
  private[repo] def distinctResourcePermissionsQuery(projectIri: ProjectIri): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))

    Queries
      .SELECT(permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns
          .and(resourceCore(projectIri, resource, resClass, creator, permissions))
          .filter(onlyRestricted(permissions)),
      )
  }

  /**
   * `SELECT DISTINCT ?permissions` over a project's restriction-bearing values. Independent of `GroupBy`:
   * grouping changes how counts are bucketed, not which literals exist.
   */
  private[repo] def distinctValuePermissionsQuery(projectIri: ProjectIri): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))

    Queries
      .SELECT(permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns
          .and(valueCore(projectIri, resource, resClass, prop, value, creator, permissions))
          .filter(onlyRestricted(permissions)),
      )
  }

  /**
   * `SELECT ?groupId (COUNT(DISTINCT ?resource) AS ?cnt) … GROUP BY ?groupId` over the resources whose
   * permission literal the service classified as hidden. Exact at any project size.
   */
  private[repo] def resourceCountQuery(projectIri: ProjectIri, hiddenPermissions: Set[String]): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (groupId, cnt)         = (variable("groupId"), variable("cnt"))

    Queries
      .SELECT(groupId, Expressions.count(resource).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        GraphPatterns
          .and(resourceCore(projectIri, resource, resClass, creator, permissions))
          .filter(permissionsIn(permissions, hiddenPermissions))
          .and(Expressions.bind(resClass, groupId)),
      )
      .groupBy(groupId)
  }

  /**
   * `SELECT ?groupId (COUNT(DISTINCT ?value) AS ?cnt) … GROUP BY ?groupId` over the values whose permission
   * literal the service classified as hidden.
   *
   * Counts distinct *values*, matching the summary's semantics that a comment is a facet of its parent
   * value rather than a separately counted item — except under `itemType=Comment`, where the comment is
   * the thing being counted and the constraint restricts to commented values.
   */
  private[repo] def valueCountQuery(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    hiddenPermissions: Set[String],
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (fileClass, comment)   = (variable("fileClass"), variable("comment"))
    val (groupId, cnt)         = (variable("groupId"), variable("cnt"))
    val groupCol               = if (groupBy == GroupBy.Property) prop else resClass

    val core        = valueCore(projectIri, resource, resClass, prop, value, creator, permissions)
    val constrained = itemTypeConstraint(value, fileClass, comment, itemType)
      .fold(GraphPatterns.and(core))(c => GraphPatterns.and(core, c))

    Queries
      .SELECT(groupId, Expressions.count(value).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(
        constrained
          .filter(permissionsIn(permissions, hiddenPermissions))
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
  ): SelectQuery = {
    val resource   = variable("resource")
    val labelOrIri = variable("labelOrIri")

    Queries
      .SELECT(resource, labelOrIri)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(drillDownPattern(projectIri, groupBy, itemType, group, resource, Some(labelOrIri)))
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
  ): SelectQuery = {
    val resource = variable("resource")
    val cnt      = variable("cnt")

    Queries
      .SELECT(Expressions.count(resource).distinct().as(cnt))
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(drillDownPattern(projectIri, groupBy, itemType, group, resource, None))
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
  ): GraphPattern = {
    val resClass               = variable("resClass")
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (fileClass, comment)   = (variable("fileClass"), variable("comment"))
    val label                  = variable("label")

    val valueBranch = {
      val core        = valueCore(projectIri, resource, resClass, prop, value, creator, permissions)
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
            .and(resourceCore(projectIri, resource, resClass, creator, permissions))
            .filter(onlyRestricted(permissions)),
          resClass,
          Some(group),
        )
      } else if (!wantResources(groupBy, itemType)) valueBranch
      else {
        // Both are in scope: a resource qualifies via its own restriction OR via a restricted value.
        val resourceBranch = withGroupFilter(
          GraphPatterns
            .and(resourceCore(projectIri, resource, resClass, creator, permissions))
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
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val label                  = variable("label")

    val pattern = GraphPatterns
      .and(resourceCore(projectIri, resource, resClass, creator, permissions))
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
  ): SelectQuery = {
    val (resource, resClass)   = (variable("resource"), variable("resClass"))
    val (prop, value)          = (variable("prop"), variable("value"))
    val (creator, permissions) = (variable("creator"), variable("permissions"))
    val (fileClass, comment)   = (variable("fileClass"), variable("comment"))
    val label                  = variable("label")

    val pattern = GraphPatterns
      .and(valueCore(projectIri, resource, resClass, prop, value, creator, permissions))
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
