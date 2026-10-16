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
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ValueKind
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
 * change to property mode too. The two reports answer different questions and want different queries.
 *
 * Two access patterns; the counts come from `ViewRestrictionsRowCache`, not from this repo:
 *
 *   - **The property list** ([[projectValueProperties]]) — read from the ontology cache, issuing no SPARQL
 *     at all. It does not come from the data.
 *   - **Drill-down** ([[findRestrictedResources]] + [[countRestrictedResources]]) — paginated in SPARQL,
 *     ordered deterministically. Each row carries its own resource class, because a property spans classes.
 *
 * NOTE — the drill-down queries join **no resource class** to pin a resource, and that is load-bearing.
 * Under a single bound property `DISTINCT ?value` and `DISTINCT ?resource` cannot double-count, so the
 * most-specific-class filter of [[ViewRestrictionsRepo]] does not apply here.
 */
final case class ViewRestrictionsByPropertyRepo(
  private val triplestore: TriplestoreService,
  private val ontologyRepo: OntologyRepo,
) {

  /**
   * The project's value properties, with labels, from the ontology cache.
   *
   * Issues no SPARQL: the list comes from the ontology cache, never from the project's data.
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
      // `ValueKind.of` decides the report's whole row set: a property it rejects is never counted and never
      // reaches a screen. User link values count; standoff link values do not.
      .filter(ValueKind.of(_).isDefined)
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

  /** The knora-base ontology, which is not among a project's own and must be fetched explicitly. */
  private[repo] val KnoraBaseOntologyIri: String = OntologyConstants.KnoraBase.KnoraBaseOntologyIri

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
                   |  FILTER (?resource IN (${Fragment
              .join(resourceIris.map(Iri.unsafeFrom(_).toFragment), Fragment.raw(", "))}))
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
                   |  FILTER (?resource IN (${Fragment
              .join(resourceIris.map(Iri.unsafeFrom(_).toFragment), Fragment.raw(", "))}))
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
   * The queries above repeat the same WHERE skeleton — a project's current, non-deleted values of ONE
   * property — rather than sharing it, so that each template can be read as one whole piece of SPARQL.
   *
   * No `?resClass` and no `ProjectClasses`: see the class doc for why.
   * `attachedToProject` on the resource is the whole project scope this needs. The property IRI is bound in
   * the triple pattern, never in a FILTER, because a FILTER does not narrow the scan.
   *
   * Each query is written as two whole templates chosen by whether an item-type constraint applies. The
   * skeleton is wrapped in a group of its own exactly when a constraint follows it — the constraint then
   * cannot be merged into the skeleton's basic graph pattern, nor reordered against its triples — and left
   * bare when there is no constraint, so no template adds a group that has nothing to separate.
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
