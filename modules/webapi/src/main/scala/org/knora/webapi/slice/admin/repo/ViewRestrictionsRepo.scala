/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsIriNames.localName
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsIriNames.ontologyName
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.DrillDownBranches
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/**
 * Reads a project's view-restriction data.
 *
 * Two access patterns, deliberately different in shape:
 *
 *   - **Counts** ([[resourceCounts]] and [[valueCounts]], read through `ViewRestrictionsRowCache`): computed
 *     by the triplestore and grouped by `knora-base:hasPermissions`. Visibility depends only on that literal (see
 *     [[org.knora.webapi.slice.admin.domain.service.ViewRestrictionsAudience]]), so one grouped query
 *     answers every audience and both restriction states at once, and a group's whole population falls out
 *     of the same rows. No per-object data crosses the wire, so counts are exact at any project size.
 *   - **Drill-down** ([[findRestrictedObjects]] + [[countRestrictedResources]]): genuinely paginated. The
 *     query is always narrowed to one resource class, ordered deterministically, and windowed with
 *     `LIMIT`/`OFFSET` in SPARQL, with the page total from a matching `COUNT`. The objects are selected by
 *     the permission literals the service classifies as restricted, so the drill-down lists what the counts
 *     report.
 *
 * Values carry their own `hasPermissions`/`attachedToUser`, so resources and values are queried separately.
 * File values are distinguished by `rdfs:subClassOf* knora-base:FileValue`; a value carrying
 * `knora-base:valueHasComment` additionally yields a comment item (a comment is a plain literal on the
 * value and is not independently permissioned, so its visibility equals its parent value's).
 */
final case class ViewRestrictionsRepo(private val triplestore: TriplestoreService) {

  /**
   * The project's current resources counted per class and permission literal. A row that cannot be
   * parsed fails the request.
   *
   * Callers read through `ViewRestrictionsRowCache`; a direct call bypasses its refresh rule.
   */
  def resourceCounts(projectIri: ProjectIri): Task[Seq[ResourceCountRow]] =
    triplestore
      .query(ViewRestrictionsRepo.resourceCountsQuery(projectIri))
      .flatMap(rows => ZIO.foreach(rows.results.bindings)(parseResourceCountRow))

  /**
   * The named graphs that hold something attached to the project: its data graphs and its ontology graphs, in IRI
   * order. A row that is not an IRI fails the request.
   *
   * Callers read through `ViewRestrictionsRowCache`; a direct call bypasses its refresh rule.
   */
  def dataGraphs(projectIri: ProjectIri): Task[Seq[String]] =
    triplestore
      .query(ViewRestrictionsRepo.projectDataGraphsQuery(projectIri))
      .flatMap(rows => ZIO.foreach(rows.results.bindings)(parseGraphIri))

  /**
   * The value counts of the project whose data lies in `graphs`, per resource class, property, permission literal
   * and file flag. Only current values of current resources are counted, and only of a sub-property of
   * `knora-base:hasValue` other than `knora-base:hasStandoffLinkToValue`. No graph means no rows and no query. Row
   * order is unspecified. A row that cannot be parsed fails the request.
   *
   * Callers read through `ViewRestrictionsRowCache`; a direct call bypasses its refresh rule.
   */
  def valueCounts(graphs: Seq[String]): Task[Seq[ValueCountRow]] =
    if (graphs.isEmpty) ZIO.succeed(Seq.empty)
    else
      triplestore
        .query(ViewRestrictionsRepo.valueCountsQuery(graphs))
        .flatMap(rows => ZIO.foreach(rows.results.bindings)(parseValueCountRow))

  private def parseGraphIri(row: VariableResultsRow): Task[String] =
    for {
      iri <- ZIO.attempt(row.getRequired("g"))
      _   <- ZIO
             .fromEither(Iri.from(iri))
             .mapError(msg => InconsistentRepositoryDataException(s"Invalid graph IRI: $msg"))
    } yield iri

  private def parseResourceCountRow(row: VariableResultsRow): Task[ResourceCountRow] =
    ZIO.attempt(
      ResourceCountRow(row.getRequired("resClass"), row.getRequired("permissions"), requiredInt(row, "cnt")),
    )

  private def parseValueCountRow(row: VariableResultsRow): Task[ValueCountRow] =
    ZIO.attempt(
      ValueCountRow(
        classIri = row.getRequired("resClass"),
        propertyIri = row.getRequired("prop"),
        permissions = row.getRequired("permissions"),
        isFile = row.getRequired("isFile") == "true",
        count = requiredInt(row, "cnt"),
        commented = requiredInt(row, "commented"),
      ),
    )

  private def requiredInt(row: VariableResultsRow, column: String): Int =
    row.getRequired(column, value => value.toIntOption.toRight(s"not an integer: $value"))

  /**
   * The distinct resource IRIs of one page of the drill-down, ordered by label then IRI so paging is
   * stable, plus every restriction-bearing row belonging to those resources.
   *
   * Paging is over *resources* (the unit the API returns), not raw rows: the page window is applied in
   * SPARQL to the resource list, then the rows for exactly those resources are fetched.
   *
   * `branches` holds the permission literals that select the restricted resources and values, `graphs` the
   * project's graphs the page is read from; see [[ViewRestrictionsRepo.resourcePageQuery]]. `graphs` must not be
   * empty.
   */
  def findRestrictedObjects(
    projectIri: ProjectIri,
    graphs: Seq[String],
    itemType: ItemType,
    group: String,
    offset: Int,
    limit: Int,
    branches: DrillDownBranches,
  ): Task[Seq[RestrictedObjectRow]] =
    for {
      pageIris <-
        triplestore
          .query(ViewRestrictionsRepo.resourcePageQuery(graphs, itemType, group, offset, limit, branches))
          .map(_.flatMap(_.get("resource")))
      rows <- if (pageIris.isEmpty) ZIO.succeed(Seq.empty[RestrictedObjectRow])
              else fetchRowsFor(projectIri, itemType, group, pageIris, branches)
    } yield rows

  private def fetchRowsFor(
    projectIri: ProjectIri,
    itemType: ItemType,
    group: String,
    resourceIris: Seq[String],
    branches: DrillDownBranches,
  ): Task[Seq[RestrictedObjectRow]] =
    for {
      resources <- branches.resources.fold(ZIO.succeed(Seq.empty))(runResourceQuery(projectIri, group, resourceIris, _))
      values    <-
        branches.values.fold(ZIO.succeed(Seq.empty))(runValueQuery(projectIri, group, itemType, resourceIris, _))
    } yield resources ++ values

  /**
   * Total number of distinct resources the drill-down would return — the exact `totalItems` for paging. `graphs`
   * must not be empty.
   */
  def countRestrictedResources(
    graphs: Seq[String],
    itemType: ItemType,
    group: String,
    branches: DrillDownBranches,
  ): Task[Int] =
    triplestore
      .query(ViewRestrictionsRepo.resourceCountForDrillDownQuery(graphs, itemType, group, branches))
      .map(_.getFirst("cnt").flatMap(_.toIntOption).getOrElse(0))

  private def runResourceQuery(
    projectIri: ProjectIri,
    group: String,
    resourceIris: Seq[String],
    resourceRestricted: Set[String],
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(ViewRestrictionsRepo.resourceQuery(projectIri, group, resourceIris, resourceRestricted))
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
    valueRestricted: Set[String],
  ): Task[Seq[RestrictedObjectRow]] =
    triplestore
      .query(ViewRestrictionsRepo.valueQuery(projectIri, group, resourceIris, valueRestricted))
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
}

object ViewRestrictionsRepo {

  val layer: URLayer[TriplestoreService, ViewRestrictionsRepo] = ZLayer.derive[ViewRestrictionsRepo]

  /**
   * One row of a permission-grouped count: how many objects carry `permissions`.
   *
   * The literal is carried raw and unclassified on purpose: the caller resolves it against each audience
   * with the real permission model, which is what lets one query answer all three audiences and both
   * restriction states at once.
   */
  final case class PermissionCountRow(permissions: String, count: Int)

  /** One row of the resource-count query: the resources of one class that carry one permission literal. */
  final case class ResourceCountRow(classIri: String, permissions: String, count: Int)

  /**
   * One row of the value-count query: the current values of one property of the current resources of one class
   * that carry one permission literal.
   *
   * `isFile` is set when the property is a sub-property of `knora-base:hasFileValue`; `commented` counts the
   * values with a `knora-base:valueHasComment`.
   */
  final case class ValueCountRow(
    classIri: String,
    propertyIri: String,
    permissions: String,
    isFile: Boolean,
    count: Int,
    commented: Int,
  )

  /**
   * One restriction-bearing object as read from the triplestore, before visibility resolution.
   *
   * @param groupId     the grouping key, the resource-class IRI.
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
   * The parts of a class drill-down that have something to run, each with the permission literals that mark
   * its objects as restricted: whole resources, values, or both.
   */
  sealed trait DrillDownBranches {

    /** The literals selecting whole restricted resources, if that branch runs. */
    def resources: Option[Set[String]] = this match {
      case DrillDownBranches.ResourcesOnly(literals) => Some(literals)
      case DrillDownBranches.Both(literals, _)       => Some(literals)
      case DrillDownBranches.ValuesOnly(_)           => None
    }

    /** The literals selecting restricted values, if that branch runs. */
    def values: Option[Set[String]] = this match {
      case DrillDownBranches.ValuesOnly(literals) => Some(literals)
      case DrillDownBranches.Both(_, literals)    => Some(literals)
      case DrillDownBranches.ResourcesOnly(_)     => None
    }
  }

  object DrillDownBranches {
    final case class ResourcesOnly(literals: Set[String])                            extends DrillDownBranches
    final case class ValuesOnly(literals: Set[String])                               extends DrillDownBranches
    final case class Both(resourceLiterals: Set[String], valueLiterals: Set[String]) extends DrillDownBranches

    /**
     * The branches `itemType` asks for whose literal set is non-empty; `None` when nothing can qualify and no
     * query has to run.
     */
    def of(
      itemType: ItemType,
      resourceRestricted: Set[String],
      valueRestricted: Set[String],
    ): Option[DrillDownBranches] = {
      val resources = Option.when(wantsResources(itemType) && resourceRestricted.nonEmpty)(resourceRestricted)
      val values    = Option.when(wantsValues(itemType) && valueRestricted.nonEmpty)(valueRestricted)
      (resources, values) match {
        case (Some(r), Some(v)) => Some(Both(r, v))
        case (Some(r), None)    => Some(ResourcesOnly(r))
        case (None, Some(v))    => Some(ValuesOnly(v))
        case (None, None)       => None
      }
    }

    private def wantsResources(itemType: ItemType): Boolean =
      itemType == ItemType.All || itemType == ItemType.Resource

    private def wantsValues(itemType: ItemType): Boolean =
      itemType == ItemType.All || itemType == ItemType.File || itemType == ItemType.Value ||
        itemType == ItemType.Comment
  }

  /**
   * Narrows a value pattern to one item type. `File`/`Value` need the file-ness of the value decided in
   * the query (not just reported), and `Comment` needs the comment to exist; `All` adds nothing, and its
   * templates then also omit the group that would isolate the skeleton from a constraint.
   */
  private[repo] def itemTypeConstraint(itemType: ValueItemType): Option[Fragment] =
    itemType match {
      case ValueItemType.File =>
        Some(sparql"""|{
                      |  ?value a ?fileClass .
                      |  ?fileClass rdfs:subClassOf* knora-base:FileValue .
                      |}""")
      case ValueItemType.Value =>
        Some(sparql"""|FILTER NOT EXISTS {
                      |  ?value a ?fileClass .
                      |  ?fileClass rdfs:subClassOf* knora-base:FileValue .
                      |}""")
      case ValueItemType.Comment => Some(sparql"?value knora-base:valueHasComment ?comment .")
      case ValueItemType.All     => None
    }

  /** The constraint of a class drill-down item type; whole resources and `All` add none. */
  private def classItemTypeConstraint(itemType: ItemType): Option[Fragment] =
    itemType match {
      case ItemType.File    => itemTypeConstraint(ValueItemType.File)
      case ItemType.Value   => itemTypeConstraint(ValueItemType.Value)
      case ItemType.Comment => itemTypeConstraint(ValueItemType.Comment)
      case _                => None
    }

  /*
   * The queries below describe the same universe — a project's current, non-deleted resources, and its current,
   * non-deleted values reached through a sub-property of `knora-base:hasValue` — each in its own template, so
   * that each can be read as one whole piece of SPARQL; only the drill-down page and its count share their
   * selection. A count that matched a different row set than the drill-down would be worse than no count, and
   * `ViewRestrictionsServiceSpec` pins that they agree.
   * The count queries count a resource typed with two classes once for each class.
   *
   * `knora-base:attachedToUser ?creator` is joined only where the creator is projected, in `resourceQuery`
   * and `valueQuery`. The page and count templates leave it out: it is not constrained there, and an
   * unconstrained join only multiplies intermediate rows.
   *
   * Graph scoping uses the project's graphs as [[projectDataGraphsQuery]] enumerates them, never the one graph
   * `ProjectService.projectDataNamedGraphV2` derives: a project can have one data graph per ontology (the local
   * `anything` project has `anything` and `freetest`), and scoping to the derived graph would silently undercount.
   * Inside the enumerated graphs a constant-object pattern such as `?value knora-base:isDeleted false` scans the
   * project, not the store, and replaces the `attachedToProject` join.
   *
   * Every query of this report runs on `SparqlTimeout.ViewRestrictions` rather than the standard tier —
   * these are whole-project scans grouped by permission literal, not the bounded lookups 20s is sized for. The
   * project-wide value count runs on `SparqlTimeout.ViewRestrictionsCounts`.
   */

  /**
   * Counts a project's current resources per class and permission literal.
   */
  private[repo] def resourceCountsQuery(projectIri: ProjectIri): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT ?resClass ?permissions (COUNT(*) AS ?cnt)
               |WHERE {
               |  ?resource knora-base:attachedToProject $project .
               |  ?resource a ?resClass ;
               |    knora-base:hasPermissions ?permissions ;
               |    knora-base:isDeleted false .
               |}
               |GROUP BY ?resClass ?permissions""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /** The named graphs that hold a triple with `knora-base:attachedToProject` of the project, in IRI order. */
  private[repo] def projectDataGraphsQuery(projectIri: ProjectIri): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT ?g
               |WHERE {
               |  GRAPH ?g { ?resource knora-base:attachedToProject $project . }
               |}
               |ORDER BY ?g""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /**
   * Counts the current values of the current resources in `graphs`, per resource class, property, permission
   * literal and file flag. `graphs` must not be empty.
   *
   * The pattern order is load-bearing. Fuseki TDB2 without statistics orders the patterns of one group by fixed
   * weights: a pattern with a constant object (`?x <P> <O>`, and `?x a <C>`) runs before a pattern with an unbound
   * object. So the group has exactly one such pattern, `?value knora-base:isDeleted false`, which inside the graph
   * scans the project's values; every other pattern is a lookup from it. The resource's deleted flag stays a
   * variable and is filtered after grouping: `?resource knora-base:isDeleted false` would run first and join every
   * resource with every value.
   *
   * Comments are counted in a `UNION` branch of their own, driven by `knora-base:valueHasComment`: an `OPTIONAL` is
   * evaluated once per value row, which on the largest project cost about as much as the rest of the query.
   *
   * The properties are classified after grouping, on a few hundred rows: the property path drops what is not a
   * value (link targets, value versions, `rdf:object`), and `isFile` marks file values.
   */
  private[repo] def valueCountsQuery(graphs: Seq[String]): Select = {
    require(graphs.nonEmpty, "a value count needs at least one graph")
    val graphValues = Fragments.values(Variable("g"), graphs.map(Iri.unsafeFrom))
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT ?resClass ?prop ?permissions ?isFile (SUM(?valueCount) AS ?cnt) (SUM(?commentCount) AS ?commented)
               |WHERE {
               |  {
               |    SELECT ?resClass ?prop ?permissions ?resDeleted (COUNT(*) AS ?valueCount) (0 AS ?commentCount)
               |    WHERE {
               |      $graphValues
               |      GRAPH ?g {
               |        ?value knora-base:isDeleted false .
               |        ?value knora-base:hasPermissions ?permissions .
               |        ?resource ?prop ?value .
               |        ?resource a ?resClass .
               |        ?resource knora-base:isDeleted ?resDeleted .
               |      }
               |    }
               |    GROUP BY ?resClass ?prop ?permissions ?resDeleted
               |  }
               |  UNION
               |  {
               |    SELECT ?resClass ?prop ?permissions ?resDeleted (0 AS ?valueCount) (COUNT(*) AS ?commentCount)
               |    WHERE {
               |      $graphValues
               |      GRAPH ?g {
               |        ?value knora-base:valueHasComment ?comment .
               |        ?value knora-base:isDeleted ?valueDeleted .
               |        ?value knora-base:hasPermissions ?permissions .
               |        ?resource ?prop ?value .
               |        ?resource a ?resClass .
               |        ?resource knora-base:isDeleted ?resDeleted .
               |      }
               |      FILTER (?valueDeleted = false)
               |    }
               |    GROUP BY ?resClass ?prop ?permissions ?resDeleted
               |  }
               |  FILTER (?resDeleted = false)
               |  ?prop rdfs:subPropertyOf* knora-base:hasValue .
               |  FILTER (?prop != knora-base:hasStandoffLinkToValue)
               |  BIND (EXISTS { ?prop rdfs:subPropertyOf* knora-base:hasFileValue } AS ?isFile)
               |}
               |GROUP BY ?resClass ?prop ?permissions ?isFile""".render,
      SparqlTimeout.ViewRestrictionsCounts,
    )
  }

  /**
   * `FILTER (STR(?permissions) IN ("a", "b", ...))`: keeps the rows whose permission literal is one of
   * `literals`. For the row queries, whose rows are already bound by `VALUES ?resource`. `literals` must not be
   * empty.
   *
   * `STR()` is deliberate. Jena rewrites `?permissions IN ("a", ...)` over plain string literals into a
   * `UNION` with each literal placed in the triple pattern, which makes
   * `?value knora-base:hasPermissions "a"` scan every object in the store that carries that literal.
   * `STR(?permissions)` is not a variable, so the rewrite does not apply and the permission is matched
   * on the rows already bound by the page.
   */
  private[repo] def permissionFilter(literals: Set[String]): Fragment = {
    require(literals.nonEmpty, "a permission filter needs at least one literal")
    val list = Fragment.join(literals.toSeq.sorted.map(Literal.string(_).toFragment), Fragment.raw(", "))
    sparql"FILTER (STR(?permissions) IN ($list))"
  }

  /**
   * `VALUES ?permissions { "a" "b" ... }`: the permission literals that select the drill-down's objects, the
   * restricted literals of the classification the counts use (`ViewRestrictionsAudience.restrictedLiterals`).
   * `literals` must not be empty.
   *
   * Inside the project's graphs, `?x knora-base:hasPermissions "a"` scans only the project's objects that carry
   * the literal, so the literal is meant to be the driver here.
   */
  private[repo] def permissionValues(literals: Set[String]): Fragment = {
    require(literals.nonEmpty, "a permission list needs at least one literal")
    Fragments.values(Variable("permissions"), literals.toSeq.sorted.map(Literal.string))
  }

  /** `VALUES ?g { ... }`: the graphs a query reads from. `graphs` must not be empty. */
  private[repo] def graphValues(graphs: Seq[String]): Fragment = {
    require(graphs.nonEmpty, "a graph-scoped query needs at least one graph")
    Fragments.values(Variable("g"), graphs.map(Iri.unsafeFrom))
  }

  /**
   * `{ SELECT DISTINCT ?resource ... }`: the resources of class `group` that are themselves restricted or carry a
   * restricted value under the active filter, mirroring what the row queries return. A branch the
   * [[DrillDownBranches]] lack is left out; both together are a `UNION`. [[resourcePageQuery]] and
   * [[resourceCountForDrillDownQuery]] share it, so a page and its total describe the same row set.
   *
   * Each branch is driven by its permission literals: an inner sub-select reads the objects that carry one of
   * them from the project's graphs, and only then are the class and the deleted flags checked, on bound terms.
   * The sub-select is load-bearing: written in one group, `?resource a <group>` would run before
   * `?resource ?prop ?value` and join the whole class with every restricted value. A value counts unless its
   * property is `knora-base:hasStandoffLinkToValue`: a user link value is listed, a standoff link value is not.
   */
  private def restrictedResourcesOfClass(
    graphs: Seq[String],
    itemType: ItemType,
    group: String,
    branches: DrillDownBranches,
  ): Fragment = {
    val groupClass     = Iri.unsafeFrom(group)
    val resourceBranch = branches.resources.map(literals => sparql"""|{
               |  {
               |    SELECT ?resource
               |    WHERE {
               |      ${permissionValues(literals)}
               |      ${graphValues(graphs)}
               |      GRAPH ?g { ?resource knora-base:hasPermissions ?permissions . }
               |    }
               |  }
               |  ?resource a $groupClass ;
               |    knora-base:isDeleted false .
               |}""")
    val valueBranch = branches.values.map(literals => sparql"""|{
               |  {
               |    SELECT ?resource ?prop ?value
               |    WHERE {
               |      ${permissionValues(literals)}
               |      ${graphValues(graphs)}
               |      GRAPH ?g {
               |        ?value knora-base:hasPermissions ?permissions .
               |        ?resource ?prop ?value .
               |      }
               |    }
               |  }
               |  ?resource a $groupClass ;
               |    knora-base:isDeleted false .
               |  ?value knora-base:isDeleted false .
               |  ?prop rdfs:subPropertyOf* knora-base:hasValue .
               |  FILTER (?prop != knora-base:hasStandoffLinkToValue)
               |  ${classItemTypeConstraint(itemType).whenSome(identity)}
               |}""")
    val union = Fragment.join(resourceBranch.toSeq ++ valueBranch.toSeq, Fragment.raw("\nUNION\n"))
    sparql"""|{
             |  SELECT DISTINCT ?resource
             |  WHERE {
             |    $union
             |  }
             |}"""
  }

  /**
   * One page of distinct resource IRIs for the drill-down, ordered by label then IRI so that paging is
   * reproducible, windowed in SPARQL with `LIMIT`/`OFFSET`. Paging is over *resources*, the unit the API
   * returns, so that a resource can never straddle a page boundary. The resources are those of
   * [[restrictedResourcesOfClass]]; the label is joined only after them.
   */
  private[repo] def resourcePageQuery(
    graphs: Seq[String],
    itemType: ItemType,
    group: String,
    offset: Int,
    limit: Int,
    branches: DrillDownBranches,
  ): Select =
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT DISTINCT ?resource ?labelOrIri
               |WHERE {
               |  ${restrictedResourcesOfClass(graphs, itemType, group, branches)}
               |  OPTIONAL { ?resource rdfs:label ?label . }
               |  BIND(COALESCE(?label, STR(?resource)) AS ?labelOrIri)
               |}
               |ORDER BY ASC(?labelOrIri) ASC(?resource)
               |LIMIT ${Literal.int(limit)}
               |OFFSET ${Literal.int(offset)}""".render,
      SparqlTimeout.ViewRestrictions,
    )

  /**
   * The number of resources [[resourcePageQuery]] pages through — the exact page total, in the same unit that
   * query windows in, unwindowed.
   */
  private[repo] def resourceCountForDrillDownQuery(
    graphs: Seq[String],
    itemType: ItemType,
    group: String,
    branches: DrillDownBranches,
  ): Select =
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT (COUNT(*) AS ?cnt)
               |WHERE {
               |  ${restrictedResourcesOfClass(graphs, itemType, group, branches)}
               |}""".render,
      SparqlTimeout.ViewRestrictions,
    )

  /**
   * The restriction-bearing resource rows for an explicit set of resource IRIs (one drill-down page).
   * Unbounded by design: the IRI list is already the page window, and `VALUES` binds `?resource` to it
   * first. `resourceIris` must not be empty.
   */
  private[repo] def resourceQuery(
    projectIri: ProjectIri,
    group: String,
    resourceIris: Seq[String],
    resourceRestricted: Set[String],
  ): Select = {
    val project    = Iri.unsafeFrom(projectIri.value)
    val groupClass = Iri.unsafeFrom(group)
    val page       = Fragments.values(Variable("resource"), resourceIris.map(Iri.unsafeFrom))
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
               |WHERE {
               |  $page
               |  ?resource a $groupClass ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:attachedToUser ?creator ;
               |    knora-base:hasPermissions ?permissions ;
               |    knora-base:isDeleted false .
               |  BIND($groupClass AS ?resClass)
               |  OPTIONAL { ?resource rdfs:label ?label . }
               |  ${permissionFilter(resourceRestricted)}
               |}""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }

  /**
   * The restriction-bearing value rows for an explicit set of resource IRIs (one drill-down page); `VALUES`
   * binds `?resource` to them first. `?fileClass` is bound when the value is a `knora-base:FileValue`,
   * `?comment` when it carries a `knora-base:valueHasComment` — both reported rather than filtered on, since
   * this query returns every kind of value and the caller decides which item rows a value yields.
   * `resourceIris` must not be empty.
   */
  private[repo] def valueQuery(
    projectIri: ProjectIri,
    group: String,
    resourceIris: Seq[String],
    valueRestricted: Set[String],
  ): Select = {
    val project    = Iri.unsafeFrom(projectIri.value)
    val groupClass = Iri.unsafeFrom(group)
    val page       = Fragments.values(Variable("resource"), resourceIris.map(Iri.unsafeFrom))
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
               |WHERE {
               |  $page
               |  ?resource a $groupClass ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:isDeleted false .
               |  BIND($groupClass AS ?resClass)
               |  {
               |    ?resource ?prop ?value .
               |    ?prop rdfs:subPropertyOf* knora-base:hasValue .
               |  }
               |  ?value knora-base:attachedToUser ?creator ;
               |    knora-base:hasPermissions ?permissions ;
               |    knora-base:isDeleted false .
               |  FILTER (?prop != knora-base:hasStandoffLinkToValue)
               |  OPTIONAL { ?resource rdfs:label ?label . }
               |  OPTIONAL {
               |    ?value a ?fileClass .
               |    ?fileClass rdfs:subClassOf* knora-base:FileValue .
               |  }
               |  OPTIONAL { ?value knora-base:valueHasComment ?comment . }
               |  ${permissionFilter(valueRestricted)}
               |}""".render,
      SparqlTimeout.ViewRestrictions,
    )
  }
}
