/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo.PropertyRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo.RestrictedPropertyValueRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/**
 * Reads a project's view-restriction data **grouped by property**.
 *
 * A deliberate sibling of [[ViewRestrictionsRepo]] rather than a mode of it: property grouping used to be
 * a `groupBy` flag threaded through a dozen signatures, which made every change to the class report a
 * change to property mode too. The two reports answer different questions and, as the notes below record,
 * want measurably different queries.
 *
 * Three access patterns:
 *
 *   - **The property list** ([[projectValueProperties]]) — read from the ontology cache, issuing no SPARQL
 *     at all. Discovering it from the data instead costs 14.0s on LHTT, which is a blank screen for as long
 *     as the whole report used to take.
 *   - **Counts** ([[valueCountsForProperty]]) — one property at a time, grouped by
 *     `knora-base:hasPermissions`, so one query answers every audience and both restriction states, and the
 *     property's whole value population falls out of the same rows.
 *   - **Drill-down** ([[findRestrictedResources]] + [[countRestrictedResources]]) — paginated in SPARQL,
 *     ordered deterministically. Each row carries its own resource class, because a property spans classes.
 *
 * NOTE — this repo joins **no resource class**, and that is load-bearing rather than an omission.
 * [[ViewRestrictionsRepo]] needs `ProjectClasses`, its `VALUES` clause and the most-specific-class filter
 * because it groups by class and so must pin each resource to exactly one `?resClass`. Counting
 * `DISTINCT ?value` under a single bound property cannot double-count, so none of that applies here.
 * Measured on LHTT for `lhtt:hasTitle`: with the class join 2,380ms, without it 1,128ms, both returning
 * 66,484. It also means this report is never exposed to the 27s multi-typed probe that
 * `ViewRestrictionsRepo.projectClasses` has to gate.
 */
final case class ViewRestrictionsByPropertyRepo(
  private val triplestore: TriplestoreService,
  private val ontologyRepo: OntologyRepo,
) {

  /**
   * The project's value properties, with labels, from the ontology cache.
   *
   * Issues no SPARQL. The alternative — `SELECT DISTINCT ?prop` joined against the project's value
   * population — measured 14.0s on LHTT, which would put a blank screen in front of the step the stepped
   * design exists to make immediate.
   *
   * `knora-base` is fetched separately because it is not among a project's own ontologies, yet project data
   * uses its built-in file-value and comment properties without declaring them locally. Omitting it would
   * silently drop those rows from the report.
   */
  def projectValueProperties(projectIri: ProjectIri): Task[Seq[PropertyRow]] =
    for {
      own     <- ontologyRepo.findByProject(projectIri)
      builtIn <- ontologyRepo.findById(InternalIri(ViewRestrictionsByPropertyRepo.KnoraBaseOntologyIri)).map(_.toList)
      props    = (own ++ builtIn).flatMap(_.properties.values)
    } yield props
      .filter(ViewRestrictionsByPropertyRepo.isValueProperty)
      .map(toPropertyRow)
      .distinctBy(_.id)
      .sortBy(r => (r.label, r.id))

  /**
   * The display label is derived from the property's IRI local name, matching what the class report does
   * for its class labels. The ontology cache does carry `rdfs:label`, but reading it needs a
   * `StringFormatter` to build the predicate key, and taking that as a dependency propagated a new
   * requirement up through `AdminDomainModule` and `AdminModule` for a cosmetic gain. Real labels are worth
   * doing for both reports at once, not for this one alone.
   */
  private def toPropertyRow(info: ReadPropertyInfoV2): PropertyRow = {
    val iri = info.entityInfoContent.propertyIri.toString
    PropertyRow(
      id = iri,
      label = localName(iri),
      ontology = Some(ontologyName(iri)),
    )
  }

  /**
   * One property's value counts, grouped by permission literal.
   *
   * The caller classifies the handful of returned literals with the real permission model, so a single
   * query answers all three audiences and both restriction states — and, because no permission filter is
   * applied, summing the rows gives the property's whole value population.
   */
  def valueCountsForProperty(
    projectIri: ProjectIri,
    propertyIri: String,
    itemType: ValueItemType,
  ): Task[Seq[ViewRestrictionsRepo.PermissionCountRow]] =
    triplestore
      .query(ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, propertyIri, itemType))
      .map(_.flatMap(permissionCountRow))

  /**
   * One page of resources carrying a restricted value of the property, with all of their restricted values.
   *
   * Two queries rather than one: the first windows **resources** in SPARQL, the second fetches every row
   * for exactly those IRIs. Windowing the value rows directly would page a different unit than
   * [[countRestrictedResources]] counts — see [[ViewRestrictionsByPropertyRepo.drillDownResourcePageQuery]].
   */
  def findRestrictedResources(
    projectIri: ProjectIri,
    propertyIri: String,
    itemType: ValueItemType,
    offset: Int,
    limit: Int,
  ): Task[Seq[RestrictedPropertyValueRow]] =
    for {
      pageIris <- triplestore
                    .query(
                      ViewRestrictionsByPropertyRepo
                        .drillDownResourcePageQuery(projectIri, propertyIri, itemType, offset, limit),
                    )
                    .map(_.flatMap(_.get("resource")))
      // An empty window means an empty page: sending `?resource IN ()` would be a pointless round trip.
      rows <- if (pageIris.isEmpty) ZIO.succeed(Seq.empty[RestrictedPropertyValueRow])
              else
                triplestore
                  .query(
                    ViewRestrictionsByPropertyRepo
                      .drillDownRowsQuery(projectIri, propertyIri, itemType, pageIris),
                  )
                  .map(_.flatMap(restrictedRow))
    } yield rows

  /** The exact page total for [[findRestrictedResources]], so `totalItems` is not an estimate. */
  def countRestrictedResources(
    projectIri: ProjectIri,
    propertyIri: String,
    itemType: ValueItemType,
  ): Task[Int] =
    triplestore
      .query(ViewRestrictionsByPropertyRepo.drillDownCountQuery(projectIri, propertyIri, itemType))
      .map(_.flatMap(_.get("cnt").flatMap(_.toIntOption)).headOption.getOrElse(0))

  /**
   * Parses one permission-grouped row. A row missing its literal or with an unparseable count is dropped
   * rather than failing the request: an aggregate row without its grouping key carries no information, and
   * a report is more useful slightly incomplete than not at all.
   */
  private def permissionCountRow(row: VariableResultsRow): Option[ViewRestrictionsRepo.PermissionCountRow] =
    for {
      permissions <- row.get("permissions")
      count       <- row.get("cnt").flatMap(_.toIntOption)
      // No grouping key: the route has already narrowed to one property.
    } yield ViewRestrictionsRepo.PermissionCountRow(None, permissions, count)

  private def restrictedRow(row: VariableResultsRow): Option[RestrictedPropertyValueRow] =
    for {
      resource    <- row.get("resource")
      resClass    <- row.get("resClass")
      value       <- row.get("value")
      creator     <- row.get("creator")
      permissions <- row.get("permissions")
    } yield RestrictedPropertyValueRow(
      resourceIri = resource,
      resourceLabel = row.get("label").getOrElse(resource),
      // Per row, not per table: a property spans classes, so two rows of one property can differ here.
      resourceClassIri = resClass,
      valueIri = value,
      isFile = row.get("fileClass").isDefined,
      hasComment = row.get("comment").isDefined,
      creator = creator,
      permissions = permissions,
    )

  private def localName(iri: String): String    = iri.split(Array('#', '/')).lastOption.getOrElse(iri)
  private def ontologyName(iri: String): String = {
    val beforeHash = iri.split('#').headOption.getOrElse(iri)
    beforeHash.split('/').lastOption.getOrElse(beforeHash)
  }
}

object ViewRestrictionsByPropertyRepo {

  val layer = ZLayer.derive[ViewRestrictionsByPropertyRepo]

  /** One row of the property list: the table skeleton, with no counts. */
  final case class PropertyRow(id: String, label: String, ontology: Option[String])

  /**
   * One restricted value of the property, with the resource carrying it.
   *
   * `resourceClassIri` is per row rather than a property of the whole table: the same property can be used
   * by many classes, which is the entire reason this report exists.
   */
  final case class RestrictedPropertyValueRow(
    resourceIri: String,
    resourceLabel: String,
    resourceClassIri: String,
    valueIri: String,
    isFile: Boolean,
    hasComment: Boolean,
    creator: String,
    permissions: String,
  )

  /**
   * Whether a property carries values, as opposed to links or standoff references.
   *
   * There is no such predicate in the ontology slice to reuse, so it is defined here from the flags
   * `ReadPropertyInfoV2` exposes. A resource property is either a link property (pointing at another
   * resource), the reified link value that accompanies it, or a value property — so excluding the first two
   * from the resource properties leaves exactly the value properties.
   *
   * This single filter decides the report's whole row set — a property it rejects can never be counted and
   * never reaches a screen — so `ViewRestrictionsByPropertyRepoSpec` pins each flag combination directly:
   * a plain value property and a file-value property are accepted, a link property, its reified link value,
   * a standoff internal reference and a non-resource property are not.
   */
  private[repo] def isValueProperty(info: ReadPropertyInfoV2): Boolean =
    info.isResourceProp && !info.isLinkProp && !info.isLinkValueProp && !info.isStandoffInternalReferenceProperty

  /** The knora-base ontology, which is not among a project's own and must be fetched explicitly. */
  private[repo] val KnoraBaseOntologyIri: String = OntologyConstants.KnoraBase.KnoraBaseOntologyIri

  /**
   * `SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt) … GROUP BY ?permissions` for ONE property.
   *
   * The property IRI is **bound in the triple pattern**, not applied as a `FILTER`. Measured on LHTT for
   * `lhtt:hasTitle`: bound 1,053ms, filtered 3,060ms. [[ViewRestrictionsRepo]] narrows its value counts
   * with a `FILTER` because its grouping key is the class and the property varies; here the property is the
   * one fixed thing, so it belongs in the pattern.
   *
   * No permission filter at all — that absence is what lets the caller derive the property's whole value
   * population by summing the rows.
   */
  private[repo] def valueCountsQuery(
    projectIri: ProjectIri,
    propertyIri: String,
    itemType: ValueItemType,
  ): Select = {
    val project  = Iri.unsafeFrom(projectIri.value)
    val property = Iri.unsafeFrom(propertyIri)
    itemTypeConstraint(itemType) match {
      case None =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT ?permissions (COUNT(DISTINCT ?value) AS ?cnt)
                   |WHERE {
                   |  ?resource knora-base:attachedToProject $project ;
                   |    knora-base:isDeleted false ;
                   |    $property ?value .
                   |  ?value knora-base:hasPermissions ?permissions ;
                   |    knora-base:isDeleted false .
                   |  FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
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
                   |  {
                   |    ?resource knora-base:attachedToProject $project ;
                   |      knora-base:isDeleted false ;
                   |      $property ?value .
                   |    ?value knora-base:hasPermissions ?permissions ;
                   |      knora-base:isDeleted false .
                   |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |  }
                   |  $constraint
                   |}
                   |GROUP BY ?permissions""".render,
          SparqlTimeout.ViewRestrictions,
        )
    }
  }

  /**
   * One page of **resources** carrying a restricted value of the property.
   *
   * `DISTINCT ?resource`, with the window applied to resources rather than to value rows. A resource can
   * carry several restricted values of one property — any `maxCardinality > 1` property, such as keywords
   * or several titles — so windowing value rows while [[drillDownCountQuery]] counts distinct resources
   * would page two different units against each other: `totalPages` would be computed from resources while
   * the pages consumed rows, leaving the resources past that point unreachable by any page number the
   * pagination block admits. [[ViewRestrictionsRepo.resourcePageQuery]] has this shape for the same reason.
   * [[drillDownRowsQuery]] then fetches the rows for exactly this page's IRIs.
   *
   * The restriction filter stays inside the inner group, i.e. before the label OPTIONAL is joined in.
   * Ordering by the label when present and the IRI otherwise keeps unlabelled resources paging
   * deterministically.
   */
  private[repo] def drillDownResourcePageQuery(
    projectIri: ProjectIri,
    propertyIri: String,
    itemType: ValueItemType,
    offset: Int,
    limit: Int,
  ): Select = {
    val project  = Iri.unsafeFrom(projectIri.value)
    val property = Iri.unsafeFrom(propertyIri)
    itemTypeConstraint(itemType) match {
      case None =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT DISTINCT ?resource ?labelOrIri
                   |WHERE {
                   |  {
                   |    ?resource knora-base:attachedToProject $project ;
                   |      knora-base:isDeleted false ;
                   |      $property ?value .
                   |    ?value knora-base:hasPermissions ?permissions ;
                   |      knora-base:isDeleted false .
                   |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |    FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
                   |  }
                   |  OPTIONAL { ?resource rdfs:label ?label . }
                   |  BIND(COALESCE(?label, STR(?resource)) AS ?labelOrIri)
                   |}
                   |ORDER BY ASC(?labelOrIri) ASC(?resource)
                   |LIMIT ${Literal.int(limit)}
                   |OFFSET ${Literal.int(offset)}""".render,
          SparqlTimeout.ViewRestrictions,
        )
      case Some(constraint) =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT DISTINCT ?resource ?labelOrIri
                   |WHERE {
                   |  {
                   |    {
                   |      ?resource knora-base:attachedToProject $project ;
                   |        knora-base:isDeleted false ;
                   |        $property ?value .
                   |      ?value knora-base:hasPermissions ?permissions ;
                   |        knora-base:isDeleted false .
                   |      FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |    }
                   |    $constraint
                   |    FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
                   |  }
                   |  OPTIONAL { ?resource rdfs:label ?label . }
                   |  BIND(COALESCE(?label, STR(?resource)) AS ?labelOrIri)
                   |}
                   |ORDER BY ASC(?labelOrIri) ASC(?resource)
                   |LIMIT ${Literal.int(limit)}
                   |OFFSET ${Literal.int(offset)}""".render,
          SparqlTimeout.ViewRestrictions,
        )
    }
  }

  /**
   * Every restricted value of the property carried by an explicit set of resources — one drill-down page.
   *
   * Unbounded by design: the IRI list from [[drillDownResourcePageQuery]] is already the window. Fetching
   * by resource rather than by row is also what keeps a resource whole: its values cannot straddle a page
   * boundary and be returned twice, once partially on each side.
   *
   * `resourceIris` are the IRIs the page query just returned, so they are well-formed by construction; the
   * caller guarantees the list is non-empty, since `?resource IN ()` is not valid SPARQL.
   */
  private[repo] def drillDownRowsQuery(
    projectIri: ProjectIri,
    propertyIri: String,
    itemType: ValueItemType,
    resourceIris: Seq[String],
  ): Select = {
    val project  = Iri.unsafeFrom(projectIri.value)
    val property = Iri.unsafeFrom(propertyIri)
    val pageIris = Fragment.join(resourceIris.map(Iri.unsafeFrom(_).toFragment), Fragment.raw(", "))
    itemTypeConstraint(itemType) match {
      case None =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
                   |WHERE {
                   |  ?resource knora-base:attachedToProject $project ;
                   |    knora-base:isDeleted false ;
                   |    $property ?value .
                   |  ?value knora-base:hasPermissions ?permissions ;
                   |    knora-base:isDeleted false .
                   |  FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |  ?resource a ?resClass .
                   |  ?value knora-base:attachedToUser ?creator .
                   |  OPTIONAL { ?resource rdfs:label ?label . }
                   |  OPTIONAL {
                   |    ?value a ?fileClass .
                   |    ?fileClass rdfs:subClassOf* knora-base:FileValue .
                   |  }
                   |  OPTIONAL { ?value knora-base:valueHasComment ?comment . }
                   |  FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
                   |  FILTER (?resource IN ($pageIris))
                   |}
                   |ORDER BY ASC(?label) ASC(?resource) ASC(?value)""".render,
          SparqlTimeout.ViewRestrictions,
        )
      case Some(constraint) =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
                   |WHERE {
                   |  {
                   |    ?resource knora-base:attachedToProject $project ;
                   |      knora-base:isDeleted false ;
                   |      $property ?value .
                   |    ?value knora-base:hasPermissions ?permissions ;
                   |      knora-base:isDeleted false .
                   |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |    ?resource a ?resClass .
                   |    ?value knora-base:attachedToUser ?creator .
                   |    OPTIONAL { ?resource rdfs:label ?label . }
                   |    OPTIONAL {
                   |      ?value a ?fileClass .
                   |      ?fileClass rdfs:subClassOf* knora-base:FileValue .
                   |    }
                   |    OPTIONAL { ?value knora-base:valueHasComment ?comment . }
                   |  }
                   |  $constraint
                   |  FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
                   |  FILTER (?resource IN ($pageIris))
                   |}
                   |ORDER BY ASC(?label) ASC(?resource) ASC(?value)""".render,
          SparqlTimeout.ViewRestrictions,
        )
    }
  }

  /**
   * `COUNT(DISTINCT ?resource)` — the exact total for [[drillDownResourcePageQuery]], in the **same unit**
   * that query pages in. A page is a page of resources; each one arrives with all of its restricted values.
   */
  private[repo] def drillDownCountQuery(
    projectIri: ProjectIri,
    propertyIri: String,
    itemType: ValueItemType,
  ): Select = {
    val project  = Iri.unsafeFrom(projectIri.value)
    val property = Iri.unsafeFrom(propertyIri)
    itemTypeConstraint(itemType) match {
      case None =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT (COUNT(DISTINCT ?resource) AS ?cnt)
                   |WHERE {
                   |  ?resource knora-base:attachedToProject $project ;
                   |    knora-base:isDeleted false ;
                   |    $property ?value .
                   |  ?value knora-base:hasPermissions ?permissions ;
                   |    knora-base:isDeleted false .
                   |  FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |  FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
                   |}""".render,
          SparqlTimeout.ViewRestrictions,
        )
      case Some(constraint) =>
        Select(
          sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                   |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                   |
                   |SELECT (COUNT(DISTINCT ?resource) AS ?cnt)
                   |WHERE {
                   |  {
                   |    ?resource knora-base:attachedToProject $project ;
                   |      knora-base:isDeleted false ;
                   |      $property ?value .
                   |    ?value knora-base:hasPermissions ?permissions ;
                   |      knora-base:isDeleted false .
                   |    FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
                   |  }
                   |  $constraint
                   |  FILTER (!REGEX(?permissions, ${Literal.string(grantsViewToAnonymousRegex)}))
                   |}""".render,
          SparqlTimeout.ViewRestrictions,
        )
    }
  }

  /*
   * The four queries above repeat the same WHERE skeleton — a project's current, non-deleted values of ONE
   * property — rather than sharing it, so that each template can be read as one whole piece of SPARQL.
   *
   * No `?resClass` and no `ProjectClasses` — see the class doc for why, and for the measurement.
   * `attachedToProject` on the resource is the whole project scope this needs. The property IRI is bound in
   * the pattern, not filtered: 1,053ms against 3,060ms on LHTT.
   *
   * Each query is written as two whole templates chosen by whether an item-type constraint applies. The
   * skeleton is wrapped in a group of its own exactly when a constraint follows it — the constraint then
   * cannot be merged into the skeleton's basic graph pattern, nor reordered against its triples — and left
   * bare when there is no constraint, so no template adds a group that has nothing to separate.
   *
   * The counts deliberately do NOT apply the `!REGEX` restriction filter that the drill-down does: keeping
   * every permission literal is what makes the property's whole population derivable from the same rows.
   */

  /**
   * Narrows to one kind of value. Copied rather than shared with [[ViewRestrictionsRepo]]: reusing it would
   * couple the two repos through the exact seam this split exists to remove, and the fragment is small.
   *
   * `All` has no constraint at all; the group that isolates the skeleton exists only to separate it from a
   * constraint, so the callers omit both.
   */
  private def itemTypeConstraint(itemType: ValueItemType): Option[Fragment] =
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
      case ValueItemType.Comment =>
        Some(sparql"?value knora-base:valueHasComment ?comment .")
      case ValueItemType.All => None
    }

  /**
   * Matches a permission literal that grants full view to anonymous users. Copied from
   * [[ViewRestrictionsRepo]] rather than shared, like `itemTypeConstraint` above.
   *
   * The drill-down queries use it in a `FILTER(!REGEX(?permissions, …))` to keep only rows restricted from
   * someone. The authoritative per-audience decision still happens in Scala via `PermissionUtilADM`; the
   * filter only removes provably-open rows, so it is conservative.
   */
  private val grantsViewToAnonymousRegex = "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser"

}
