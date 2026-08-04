/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.Variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.RestrictedObjectRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ScanCap
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.GroupBy
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * Reads the raw per-object restriction data a project's view-restrictions report needs: for each
 * restriction-bearing object (resource / value / file value / comment) the creator, project and
 * `knora-base:hasPermissions` literal — enough for [[org.knora.webapi.messages.util.PermissionUtilADM]]
 * to resolve per-audience visibility in [[ViewRestrictionsService]].
 *
 * Two queries: one for whole-resource permissions, one for value permissions (values carry their own
 * `hasPermissions`/`attachedToUser`). File values are distinguished by `rdfs:subClassOf* knora-base:FileValue`;
 * a value carrying `knora-base:valueHasComment` additionally yields a comment row (a comment is a plain
 * literal on the value and shares the value's permissions — it is not independently permissioned in the
 * RDF model, so its visibility equals its parent value's).
 *
 * Grouping: `GroupBy.ResourceClass` groups by the resource class; `GroupBy.Property` groups by the
 * property that carries the value (whole-resource rows are not emitted in property mode).
 *
 * GUARDRAIL: each query is bounded by [[ScanCap]] rows (the large-project guardrail). When a
 * query returns exactly the cap, the result is a lower bound; the service flags the summary `approximate`.
 */
final case class ViewRestrictionsRepo(
  private val triplestore: TriplestoreService,
) extends QueryBuilderHelper {

  /**
   * All restriction-bearing rows for a project under the given filter, with a `capped` flag that is true
   * when any underlying query hit [[ScanCap]] raw rows (so the counts are a lower bound).
   */
  def findRestrictedObjects(
    projectIri: ProjectIri,
    groupBy: GroupBy,
    itemType: ItemType,
    group: Option[String] = None,
  ): Task[ViewRestrictionsRepo.Result] = {
    // In property mode there are no whole-resource rows to group under a property, so `itemType=Resource`
    // would return nothing. AC7 requires it to behave like `all` there (surface the value/file/comment
    // rows instead), so coerce Resource → All when grouping by property.
    val effectiveItemType =
      if (groupBy == GroupBy.Property && itemType == ItemType.Resource) ItemType.All else itemType

    // Whole-resource restrictions are out of scope in property mode.
    val wantResources =
      groupBy == GroupBy.ResourceClass &&
        (effectiveItemType == ItemType.All || effectiveItemType == ItemType.Resource)
    val wantValues =
      effectiveItemType == ItemType.All || effectiveItemType == ItemType.File ||
        effectiveItemType == ItemType.Value || effectiveItemType == ItemType.Comment
    val empty = (Seq.empty[RestrictedObjectRow], false)
    for {
      resources <- if (wantResources) runResourceQuery(projectIri, group) else ZIO.succeed(empty)
      values    <- if (wantValues) runValueQuery(projectIri, group, groupBy, effectiveItemType) else ZIO.succeed(empty)
    } yield ViewRestrictionsRepo.Result(resources._1 ++ values._1, resources._2 || values._2)
  }

  /** Runs the resource query; returns rows plus whether the raw result reached the scan cap. */
  private def runResourceQuery(
    projectIri: ProjectIri,
    group: Option[String],
  ): Task[(Seq[RestrictedObjectRow], Boolean)] =
    triplestore
      .query(Select(ViewRestrictionsRepo.resourceQuery(projectIri, group)))
      .map { result =>
        val capped = result.size >= ScanCap
        val rows   = result.map { row =>
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
        }
        (rows, capped)
      }

  /** Runs the value query; returns rows (file/value + optional comment) plus whether the raw result hit the cap. */
  private def runValueQuery(
    projectIri: ProjectIri,
    group: Option[String],
    groupBy: GroupBy,
    itemType: ItemType,
  ): Task[(Seq[RestrictedObjectRow], Boolean)] =
    triplestore
      .query(Select(ViewRestrictionsRepo.valueQuery(projectIri, group, groupBy)))
      .map { result =>
        val capped = result.size >= ScanCap
        val rows   = result.flatMap { row =>
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
        }
        (rows, capped)
      }

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
   * Max rows scanned per query — the large-project guardrail. A blunt cap in v1; a
   * configurable value / cache is the follow-up. Kept generous so real projects are not truncated.
   */
  val ScanCap: Int = 10000

  /**
   * Query result: the restriction-bearing rows and whether any underlying query hit the [[ScanCap]]
   * (so the counts are a lower bound and the summary should be flagged `approximate`).
   */
  final case class Result(rows: Seq[RestrictedObjectRow], capped: Boolean)

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
   * A permission-literal grants View-or-better to anonymous users when a `V`/`M`/`D`/`CR` clause lists
   * `knora-admin:UnknownUser` (groups within a clause are comma-separated, clauses are `|`-separated).
   * Such an object is fully visible to all three audiences, so it contributes 0 to every count and can be
   * dropped in the query — this keeps the scan (and the `ScanCap` guardrail) proportional to the number of
   * *restrictions*, not the total number of values. The authoritative per-audience decision still happens
   * in Scala via PermissionUtilADM; this only removes provably-open rows, so it is conservative.
   */
  private val grantsViewToAnonymousRegex = "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser"

  /** `FILTER(!REGEX(?permissions, <grantsViewToAnonymous>))` — keep only rows restricted from someone. */
  private def onlyRestricted(permissions: Variable) =
    Expressions.not(Expressions.regex(permissions, Rdf.literalOf(grantsViewToAnonymousRegex)))

  /**
   * SELECT the current, non-deleted resources of a project with the data to resolve each resource's
   * per-audience visibility: class, label, creator and permission literal. Optionally constrained to one
   * resource class (`group`, only meaningful in class mode).
   */
  private[repo] def resourceQuery(projectIri: ProjectIri, group: Option[String]): SelectQuery = {
    val resource    = variable("resource")
    val resClass    = variable("resClass")
    val label       = variable("label")
    val creator     = variable("creator")
    val permissions = variable("permissions")

    var wherePattern = resource
      .isA(resClass)
      .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
      .andHas(KnoraBase.attachedToUser, creator)
      .andHas(KnoraBase.hasPermissions, permissions)
      .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
      .and(resClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))
      .and(resource.has(RDFS.LABEL, label).optional())
      .filter(onlyRestricted(permissions))

    group.foreach(g => wherePattern = wherePattern.filter(Expressions.equals(resClass, Rdf.iri(g))))

    Queries
      .SELECT(resource, resClass, label, creator, permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(wherePattern)
      .limit(ScanCap)
  }

  /**
   * SELECT the current, non-deleted values of a project's resources with the data to resolve each
   * value's per-audience visibility. Values are matched via the `knora-base:hasValue` super-property so
   * the carrying property IRI is captured; link values are excluded. `?fileClass` is bound when the value
   * is a `knora-base:FileValue`, and `?comment` when it carries a `knora-base:valueHasComment`.
   *
   * `group` filters by resource class (class mode) or by the carrying property (property mode).
   */
  private[repo] def valueQuery(projectIri: ProjectIri, group: Option[String], groupBy: GroupBy): SelectQuery = {
    val resource    = variable("resource")
    val resClass    = variable("resClass")
    val label       = variable("label")
    val prop        = variable("prop")
    val value       = variable("value")
    val fileClass   = variable("fileClass") // bound iff the value is a knora-base:FileValue
    val comment     = variable("comment")   // bound iff the value carries a valueHasComment literal
    val creator     = variable("creator")
    val permissions = variable("permissions")

    var wherePattern = resource
      .isA(resClass)
      .andHas(KnoraBase.attachedToProject, Rdf.iri(projectIri.value))
      .andHas(KnoraBase.isDeleted, Rdf.literalOf(false))
      .and(resClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))
      .and(resource.has(RDFS.LABEL, label).optional())
      // resource -> (some sub-property of hasValue) -> value, capturing the carrying property
      .and(
        resource
          .has(prop, value)
          .and(prop.has(zeroOrMore(RDFS.SUBPROPERTYOF), KnoraBase.hasValue)),
      )
      // the value carries its own creator + permissions and is not deleted
      .and(
        value
          .has(KnoraBase.attachedToUser, creator)
          .andHas(KnoraBase.hasPermissions, permissions)
          .andHas(KnoraBase.isDeleted, Rdf.literalOf(false)),
      )
      // keep only values that are actually restricted from some audience (see onlyRestricted)
      .filter(onlyRestricted(permissions))
      // exclude link values (reification of links, not user-facing values here)
      .and(GraphPatterns.filterNotExists(value.isA(KnoraBase.LinkValue)))
      // OPTIONAL: bind ?fileClass when the value's type is (a subclass of) knora-base:FileValue.
      // ?fileClass being bound in a result row means the value is a file value.
      .and(
        value
          .isA(fileClass)
          .and(fileClass.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.FileValue))
          .optional(),
      )
      // OPTIONAL: the comment literal, if present
      .and(value.has(KnoraBase.valueHasComment, comment).optional())

    group.foreach { g =>
      val col = if (groupBy == GroupBy.Property) prop else resClass
      wherePattern = wherePattern.filter(Expressions.equals(col, Rdf.iri(g)))
    }

    Queries
      .SELECT(resource, resClass, label, prop, value, fileClass, comment, creator, permissions)
      .distinct()
      .prefix(prefix(KnoraBase.NS), prefix(RDFS.NS))
      .where(wherePattern)
      .limit(ScanCap)
  }
}
