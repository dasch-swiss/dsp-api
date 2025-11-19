/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.apache.jena.rdf.model.Resource
import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.From
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.Variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.*
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOfType
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.predicateObjectList
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicate
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicateObjectList
import zio.*

import java.time.Instant
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Try

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn.ProjectAdmin
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn.UnknownUser
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.resources.repo.SparqlPermissionFilter
import org.knora.webapi.slice.resources.repo.model.FileValueTypeSpecificInfo
import org.knora.webapi.slice.resources.repo.model.FormattedTextValueType
import org.knora.webapi.slice.resources.repo.model.ResourceReadyToCreate
import org.knora.webapi.slice.resources.repo.model.StandoffAttribute
import org.knora.webapi.slice.resources.repo.model.StandoffAttributeValue
import org.knora.webapi.slice.resources.repo.model.StandoffLinkValueInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo.*
import org.knora.webapi.slice.resources.repo.model.ValueInfo
import org.knora.webapi.slice.resources.repo.service.ResourceModel.ActiveResource
import org.knora.webapi.slice.resources.repo.service.ResourceModel.DeletedResource
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class ResourceIriAndLabel(iri: ResourceIri, label: String)

trait ResourcesRepo {
  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: InternalIri,
    projectIri: InternalIri,
  ): Task[Unit]

  def findValues(id: ResourceIri): Task[Map[PropertyIri, Seq[ValueIri]]]
  def findLinks(id: ResourceIri): Task[Map[PropertyIri, Seq[ResourceIri]]]

  def findById(id: ResourceIri): Task[Option[ResourceModel]]
  final def findActiveById(id: ResourceIri): Task[Option[ActiveResource]] =
    findById(id).map(_.collect { case r: ActiveResource => r })
  final def findDeletedById(id: ResourceIri): Task[Option[DeletedResource]] =
    findById(id).map(_.collect { case r: DeletedResource => r })

  def countByResourceClass(resourceClassIri: ResourceClassIri, project: KnoraProject): Task[Int]

  def findResourcesByResourceClassIri(
    resourceClassIri: ResourceClassIri,
    project: KnoraProject,
    user: User,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[ResourceIriAndLabel]]
}

sealed trait ResourceModel {
  def iri: ResourceIri
  def label: String
  def resourceClassIri: ResourceClassIri
  def hasStandoffLinkTo: Option[ResourceIri]
  def hasStandoffLinkToValue: Option[ValueIri]
  def attachedToUser: UserIri
  def attachedToProject: ProjectIri
  def creationDate: Instant
  def lastModificationDate: Option[Instant]
  def hasPermissions: Map[ObjectAccess, Set[InternalIri]]
  final def shortcode: KnoraProject.Shortcode = iri.shortcode
  final def ontologyIri: OntologyIri          = resourceClassIri.ontologyIri
}
object ResourceModel {

  final case class ActiveResource(
    iri: ResourceIri,
    label: String,
    resourceClassIri: ResourceClassIri,
    hasStandoffLinkTo: Option[ResourceIri],
    hasStandoffLinkToValue: Option[ValueIri],
    attachedToUser: UserIri,
    attachedToProject: ProjectIri,
    creationDate: Instant,
    lastModificationDate: Option[Instant],
    hasPermissions: Map[ObjectAccess, Set[InternalIri]],
  ) extends ResourceModel

  final case class DeletedResource(
    iri: ResourceIri,
    label: String,
    resourceClassIri: ResourceClassIri,
    hasStandoffLinkTo: Option[ResourceIri],
    hasStandoffLinkToValue: Option[ValueIri],
    attachedToUser: UserIri,
    attachedToProject: ProjectIri,
    creationDate: Instant,
    lastModificationDate: Option[Instant],
    hasPermissions: Map[ObjectAccess, Set[InternalIri]],
    deleteDate: Option[Instant],
    deleteComment: Option[String],
    deletedBy: Option[UserIri],
  ) extends ResourceModel
}

final case class ResourcesRepoLive(triplestore: TriplestoreService)(implicit val sf: StringFormatter)
    extends ResourcesRepo
    with QueryBuilderHelper {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: InternalIri,
    projectIri: InternalIri,
  ): Task[Unit] =
    triplestore.query(ResourcesRepoLive.createNewResourceQuery(dataGraphIri, resource, projectIri, userIri))

  def findValues(id: ResourceIri): Task[Map[PropertyIri, Seq[ValueIri]]] = {
    val resource = iri(id.toString)

    val (resourceClass, valueProp, value, valueClass) =
      (variable("resourceClass"), variable("valueProperty"), variable("value"), variable("valueClass"))

    val resourceSubclass = resource
      .isA(resourceClass)
      .and(resourceClass.has(RDFS.SUBCLASSOF, KB.Resource))

    val valueAValueClass = value
      .isA(valueClass)
      .and(valueClass.has(RDFS.SUBCLASSOF, KB.Value))

    val queryP = resource.has(valueProp, value)

    val query = Queries.CONSTRUCT(queryP).where(queryP, resourceSubclass, valueAValueClass)
    for {
      rdfModel <- triplestore.queryRdfModel(Construct(query))
      resource <- rdfModel.getResource(id.toString)
      result    = resource.map(_.res).map(mapPropertyValues).getOrElse(Map.empty)
    } yield result
  }

  def findLinks(id: ResourceIri): Task[Map[PropertyIri, Seq[ResourceIri]]] = {
    val resource = iri(id.toString)

    val (resourceClass, valueProp, value, valueClass) =
      (variable("resourceClass"), variable("valueProperty"), variable("value"), variable("valueClass"))

    val resourceSubclass = resource
      .isA(resourceClass)
      .and(resourceClass.has(RDFS.SUBCLASSOF, KB.Resource))

    val valueAValueClass = value
      .isA(valueClass)
      .and(valueClass.has(RDFS.SUBCLASSOF, KB.Resource))

    val queryP = resource.has(valueProp, value)

    val query = Queries.CONSTRUCT(queryP).where(queryP, resourceSubclass, valueAValueClass)
    for {
      rdfModel <- triplestore.queryRdfModel(Construct(query))
      resource <- rdfModel.getResource(id.toString)
      result    = resource.map(_.res).map(mapPropertyResources).getOrElse(Map.empty)
    } yield result
  }

  private def mapPropertyResources(res: Resource): Map[PropertyIri, Seq[ResourceIri]] = res
    .listProperties()
    .asScala
    .map { stmt =>
      val p = PropertyIri.unsafeFrom(stmt.getPredicate.toString.toSmartIri)
      val v = ResourceIri.unsafeFrom(stmt.getObject.toString.toSmartIri)
      (p, v)
    }
    .toList
    .groupMap((p, _) => p)((_, v) => v)

  private def mapPropertyValues(res: Resource): Map[PropertyIri, Seq[ValueIri]] = res
    .listProperties()
    .asScala
    .map { stmt =>
      val p = PropertyIri.unsafeFrom(stmt.getPredicate.toString.toSmartIri)
      val v = ValueIri.unsafeFrom(stmt.getObject.toString.toSmartIri)
      (p, v)
    }
    .toList
    .groupMap((p, _) => p)((_, v) => v)

  def findById(id: ResourceIri): Task[Option[ResourceModel]] = {
    val s                = iri(id.toString)
    val clazz            = variable("clazz")
    val resourceSubclass = clazz.has(RDFS.SUBCLASSOF, KB.Resource)
    val whereClause = s
      .isA(clazz)
      .andHas(RDFS.LABEL, variable("label"))
      .andHas(KB.isDeleted, variable("isDeleted"))
      .andHas(KB.attachedToUser, variable("attachedToUser"))
      .andHas(KB.attachedToProject, variable("attachedToProject"))
      .andHas(KB.creationDate, variable("creationDate"))
      .andHas(KB.hasPermissions, variable("hasPermissions"))
      .and(s.has(KB.lastModificationDate, variable("lastModificationDate")).optional)
      .and(s.has(KB.hasStandoffLinkTo, variable("hasStandoffLinkTo")).optional)
      .and(s.has(KB.hasStandoffLinkToValue, variable("hasStandoffLinkToValue")).optional)
      .and(s.has(KB.deleteDate, variable("deleteDate")).optional)
      .and(s.has(KB.deleteComment, variable("deleteComment")).optional)
      .and(s.has(KB.deletedBy, variable("deletedBy")).optional)

    val query = Queries.SELECT().where(whereClause, resourceSubclass).prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS)
    triplestore.query(Select(query)).map(result => if result.nonEmpty then Some(mapToResource(id, result)) else None)
  }

  private def mapToResource(iri: ResourceIri, result: SparqlSelectResult): ResourceModel = {
    val row                    = result.getFirstRowOrThrow
    val isDeleted              = row.getRequired("isDeleted", s => Right(s.toBoolean))
    val label                  = row.getRequired("label")
    val resourceClassIri       = row.getRequired("clazz", s => ResourceClassIri.from(s.toSmartIri))
    val hasStandoffLinkTo      = row.get("hasStandoffLinkTo", s => ResourceIri.from(s.toSmartIri))
    val hasStandoffLinkToValue = row.get("hasStandoffLinkToValue", s => ValueIri.from(s.toSmartIri))
    val attachedToUser         = row.getRequired("attachedToUser", UserIri.from)
    val creationDate           = row.getRequired("creationDate", s => Try(Instant.parse(s)).toEither.left.map(_.getMessage))
    val attachedToProject      = row.getRequired("attachedToProject", ProjectIri.from)
    val lastModificationDate =
      row.get("lastModificationDate", s => Try(Instant.parse(s)).toEither.left.map(_.getMessage))
    val hasPermissions = PermissionUtilADM
      .parsePermissions(row.getRequired("hasPermissions"))
      .map { case (k, v) => k -> v.map(InternalIri.apply) }
    if isDeleted then
      val deleteDate    = row.get("deleteDate", s => Try(Instant.parse(s)).toEither.left.map(_.getMessage))
      val deleteComment = row.get("deleteComment")
      val deletedBy     = row.get("deletedBy", UserIri.from)
      DeletedResource(
        iri,
        label,
        resourceClassIri,
        hasStandoffLinkTo,
        hasStandoffLinkToValue,
        attachedToUser,
        attachedToProject,
        creationDate,
        lastModificationDate,
        hasPermissions,
        deleteDate,
        deleteComment,
        deletedBy,
      )
    else
      ActiveResource(
        iri,
        label,
        resourceClassIri,
        hasStandoffLinkTo,
        hasStandoffLinkToValue,
        attachedToUser,
        attachedToProject,
        creationDate,
        lastModificationDate,
        hasPermissions,
      )
  }

  override def countByResourceClass(iri: ResourceClassIri, project: KnoraProject): Task[Int] = {
    val s      = variable("s")
    val select = SparqlBuilder.select(Expressions.count(s).as(variable("count")))
    val from   = SparqlBuilder.from(toRdfIri(ProjectService.projectDataNamedGraphV2(project)))
    val where  = List(s.isA(toRdfIri(iri)), GraphPatterns.filterNotExists(toRdfIri(iri).has(KB.isDeleted, true)))
    val query  = Queries.SELECT(select).from(from).where(where: _*)
    triplestore.select(query).map(_.getFirst("count").map(_.toInt).getOrElse(0))
  }

  override def findResourcesByResourceClassIri(
    resourceClassIri: ResourceClassIri,
    project: KnoraProject,
    user: User,
    pageAndSize: PageAndSize,
  ): Task[PagedResponse[ResourceIriAndLabel]] = {

    val resVar    = variable("resourceIri")
    val resLabel  = variable("resourceLabel")
    val permVar   = variable("permissions")
    val graphName = SparqlBuilder.from(toRdfIri(ProjectService.projectDataNamedGraphV2(project)))

    val filterDeleted = GraphPatterns.filterNotExists(resVar.has(KB.isDeleted, true))

    val resourcePatternCount = resVar
      .isA(toRdfIri(resourceClassIri))
      .andHas(KB.hasPermissions, permVar)
    val wherePatternCount =
      buildPermissionPattern(user, permVar, project).map(_.and(resourcePatternCount)).getOrElse(resourcePatternCount)
    val countQuery = Queries
      .SELECT(Expressions.count(resVar).as(variable("totalCount")))
      .prefix(RDFS.NS, KB.NS)
      .from(graphName)
      .where(wherePatternCount, filterDeleted)

    val resourcePatternSelect = resVar
      .isA(toRdfIri(resourceClassIri))
      .andHas(RDFS.LABEL, resLabel)
      .andHas(KB.hasPermissions, permVar)
    val wherePatternSelect =
      buildPermissionPattern(user, permVar, project)
        .map(_.and(resourcePatternSelect))
        .getOrElse(resourcePatternSelect)

    val selectQuery = Queries
      .SELECT(resVar, resLabel)
      .prefix(RDFS.NS, KB.NS)
      .from(graphName)
      .where(wherePatternSelect, filterDeleted)
      .orderBy(resLabel.asc())
      .limit(pageAndSize.size)
      .offset((pageAndSize.page - 1) * pageAndSize.size)

    for {
      _               <- ZIO.logError("COUNT:\n" + countQuery.getQueryString)
      _               <- ZIO.logError("SELECT:\n" + selectQuery.getQueryString)
      totalCountFork  <- triplestore.select(countQuery).fork
      resourcesResult <- triplestore.select(selectQuery)
      totalCount      <- totalCountFork.join.map(_.getFirstInt("totalCount").getOrElse(0))
      result = resourcesResult.map(row =>
                 ResourceIriAndLabel(
                   ResourceIri.unsafeFrom(row.getRequired("resourceIri").toSmartIri),
                   row.getRequired("resourceLabel"),
                 ),
               )
    } yield PagedResponse.from(result, totalCount, pageAndSize)
  }

  private def buildPermissionPattern(
    user: User,
    variable: Variable,
    prj: KnoraProject,
  ): Option[GraphPattern] =
    if user.isSystemUser || user.isSystemAdmin then None
    else
      val builtInGroups: Set[KnoraGroup] =
        if user.isAnonymousUser then Set(UnknownUser)
        else if user.isProjectAdmin(prj.id) then Set(ProjectAdmin)
        else Set.empty

      val groupsForPermissions: Set[KnoraGroup] = builtInGroups ++ user.groups.map(toKnoraGroup)

      val expressions = ObjectAccess.all.flatMap(oa =>
        groupsForPermissions.map(SparqlPermissionFilter.buildSparqlRegex(variable, oa, _)),
      )
      val pattern = expressions.reduce(Expressions.or(_, _))

      Some(GraphPatterns.and().filter(pattern))

  private def toKnoraGroup(group: Group): KnoraGroup = KnoraGroup(
    GroupIri.unsafeFrom(group.id),
    GroupName.unsafeFrom(group.name),
    GroupDescriptions.unsafeFrom(group.descriptions),
    GroupStatus.from(group.status),
    group.project.map(_.id),
    GroupSelfJoin.from(group.selfjoin),
  )
}

object ResourcesRepoLive {
  val layer = ZLayer.derive[ResourcesRepoLive]

  private[service] def createNewResourceQuery(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: InternalIri,
    creatorIri: InternalIri,
  ) = {
    val query: InsertDataQuery =
      Queries
        .INSERT_DATA()
        .into(iri(dataGraphIri.value))
        .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS)

    val resourcePattern = CreateResourceQueryBuilder.buildResourcePattern(resourceToCreate, projectIri, creatorIri)
    query.insertData(resourcePattern)

    val valuePatterns = resourceToCreate.valueInfos.flatMap(
      CreateResourceQueryBuilder.buildValuePattern(_, resourceToCreate.resourceIri),
    )
    query.insertData(valuePatterns: _*)

    val standoffLinkPatterns = resourceToCreate.standoffLinks.flatMap { standoffLink =>
      CreateResourceQueryBuilder.buildStandoffLinkPattern(
        standoffLink,
        resourceToCreate.resourceIri,
        resourceToCreate.creationDate,
      )
    }
    query.insertData(standoffLinkPatterns: _*)

    Update(query.getQueryString())
  }

  private object CreateResourceQueryBuilder {
    def buildResourcePattern(
      resource: ResourceReadyToCreate,
      projectIri: InternalIri,
      creatorIri: InternalIri,
    ): TriplePattern =
      iri(resource.resourceIri.value)
        .isA(iri(resource.resourceClassIri.value))
        .andHas(RDFS.LABEL, literalOf(resource.resourceLabel))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.attachedToUser, iri(creatorIri.value))
        .andHas(KB.attachedToProject, iri(projectIri.value))
        .andHas(KB.hasPermissions, literalOf(resource.permissions))
        .andHas(KB.creationDate, literalOfType(resource.creationDate.toString(), XSD.DATETIME))

    def buildStandoffLinkPattern(
      standoffLink: StandoffLinkValueInfo,
      resourceIri: InternalIri,
      resourceCreationDate: Instant,
    ): List[TriplePattern] =
      List(
        iri(resourceIri.value)
          .has(iri(standoffLink.linkPropertyIri.value), iri(standoffLink.linkTargetIri.value))
          .andHas(iri(standoffLink.linkPropertyIri.value + "Value"), iri(standoffLink.newLinkValueIri.value)),
        buildStandoffLinkPatternContent(standoffLink, resourceIri, resourceCreationDate),
      )

    private def buildStandoffLinkPatternContent(
      standoffLink: StandoffLinkValueInfo,
      resourceIri: InternalIri,
      resourceCreationDate: Instant,
    ): TriplePattern =
      iri(standoffLink.newLinkValueIri.value)
        .isA(KB.linkValue)
        .andHas(RDF.SUBJECT, iri(resourceIri.value))
        .andHas(RDF.PREDICATE, iri(standoffLink.linkPropertyIri.value))
        .andHas(RDF.OBJECT, iri(standoffLink.linkTargetIri.value))
        .andHas(KB.valueHasString, literalOf(standoffLink.linkTargetIri.value))
        .andHas(KB.valueHasRefCount, literalOf(standoffLink.newReferenceCount))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.valueCreationDate, literalOfType(resourceCreationDate.toString(), XSD.DATETIME))
        .andHas(KB.attachedToUser, iri(standoffLink.newLinkValueCreator.value))
        .andHas(KB.hasPermissions, literalOf(standoffLink.newLinkValuePermissions))
        .andHas(KB.valueHasUUID, literalOf(standoffLink.valueUuid))

    def buildValuePattern(valueInfo: ValueInfo, resourceIri: InternalIri): List[TriplePattern] =
      List(
        iri(resourceIri.value).has(iri(valueInfo.propertyIri.value), iri(valueInfo.valueIri.value)),
        buildGeneralValuePattern(valueInfo),
      ) :::
        buildTypeSpecificValuePattern(
          valueInfo.value,
          valueInfo.valueIri.value,
          valueInfo.propertyIri.value,
          resourceIri.value,
        )

    private def buildGeneralValuePattern(valueInfo: ValueInfo): TriplePattern =
      iri(valueInfo.valueIri.value)
        .isA(iri(valueInfo.valueTypeIri.value))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.valueHasString, literalOf(valueInfo.valueHasString))
        .andHas(KB.valueHasUUID, literalOf(UuidUtil.base64Encode(valueInfo.valueUUID)))
        .andHas(KB.attachedToUser, iri(valueInfo.creator.value))
        .andHas(KB.hasPermissions, literalOf(valueInfo.permissions))
        .andHas(KB.valueHasOrder, literalOf(valueInfo.valueHasOrder))
        .andHas(KB.valueCreationDate, literalOfType(valueInfo.creationDate.toString(), XSD.DATETIME))
        .andHasOptional(KB.valueHasComment, valueInfo.comment.map(literalOf))

    private def buildTypeSpecificValuePattern(
      value: TypeSpecificValueInfo,
      valueIri: String,
      propertyIri: String,
      resourceIri: String,
    ): List[TriplePattern] =
      value match
        case v: LinkValueInfo =>
          buildLinkValuePatterns(v, valueIri, propertyIri, resourceIri)
        case UnformattedTextValueInfo(valueHasLanguage) =>
          List(
            iri(valueIri)
              .has(KB.hasTextValueType, KB.UnformattedText)
              .andHasOptional(KB.valueHasLanguage, valueHasLanguage.map(literalOf)),
          )
        case v: FormattedTextValueInfo =>
          buildFormattedTextValuePatterns(v, valueIri)
        case IntegerValueInfo(valueHasInteger) =>
          List(iri(valueIri).has(KB.valueHasInteger, literalOf(valueHasInteger)))
        case DecimalValueInfo(valueHasDecimal) =>
          List(iri(valueIri).has(KB.valueHasDecimal, literalOfType(valueHasDecimal.toString(), XSD.DECIMAL)))
        case BooleanValueInfo(valueHasBoolean) =>
          List(iri(valueIri).has(KB.valueHasBoolean, literalOf(valueHasBoolean)))
        case UriValueInfo(valueHasUri) =>
          List(iri(valueIri).has(KB.valueHasUri, literalOfType(valueHasUri, XSD.ANYURI)))
        case v: DateValueInfo =>
          buildDateValuePattern(v, valueIri)
        case ColorValueInfo(valueHasColor) =>
          List(iri(valueIri).has(KB.valueHasColor, literalOf(valueHasColor)))
        case GeomValueInfo(valueHasGeometry) =>
          List(iri(valueIri).has(KB.valueHasGeometry, literalOf(valueHasGeometry)))
        case v: FileValueTypeSpecificInfo =>
          List(buildFileValuePattern(v, valueIri))
        case HierarchicalListValueInfo(valueHasListNode) =>
          List(iri(valueIri).has(KB.valueHasListNode, iri(valueHasListNode.value)))
        case IntervalValueInfo(valueHasIntervalStart, valueHasIntervalEnd) =>
          List(
            iri(valueIri)
              .has(KB.valueHasIntervalStart, literalOfType(valueHasIntervalStart.toString(), XSD.DECIMAL))
              .andHas(KB.valueHasIntervalEnd, literalOfType(valueHasIntervalEnd.toString(), XSD.DECIMAL)),
          )
        case TimeValueInfo(valueHasTimeStamp) =>
          List(iri(valueIri).has(KB.valueHasTimeStamp, literalOfType(valueHasTimeStamp.toString(), XSD.DATETIME)))
        case GeonameValueInfo(valueHasGeonameCode) =>
          List(iri(valueIri).has(KB.valueHasGeonameCode, literalOf(valueHasGeonameCode)))

    private def buildLinkValuePatterns(
      v: LinkValueInfo,
      valueIri: String,
      propertyIri: String,
      resourceIri: String,
    ): List[TriplePattern] =
      List(
        iri(resourceIri).has(iri(propertyIri.stripSuffix("Value")), iri(v.referredResourceIri.value)),
        iri(valueIri)
          .has(RDF.SUBJECT, iri(resourceIri))
          .andHas(RDF.PREDICATE, iri(propertyIri.stripSuffix("Value")))
          .andHas(RDF.OBJECT, iri(v.referredResourceIri.value))
          .andHas(KB.valueHasRefCount, literalOf(1)),
      )

    private def buildFormattedTextValuePatterns(v: FormattedTextValueInfo, valueIri: String): List[TriplePattern] =
      val txtTypeIri = v.textValueType match
        case FormattedTextValueType.StandardMapping  => KB.FormattedText
        case FormattedTextValueType.CustomMapping(_) => KB.CustomFormattedText
      val valuePattern =
        iri(valueIri)
          .has(KB.valueHasMapping, iri(v.mappingIri.value))
          .andHas(KB.hasTextValueType, txtTypeIri)
          .andHas(KB.valueHasMaxStandoffStartIndex, literalOf(v.maxStandoffStartIndex))
          .andHasOptional(KB.valueHasLanguage, v.valueHasLanguage.map(literalOf))
      List(valuePattern) ::: v.standoff.map { standoffTagInfo =>
        valuePattern.andHas(KB.valueHasStandoff, iri(standoffTagInfo.standoffTagInstanceIri.value))
        iri(standoffTagInfo.standoffTagInstanceIri.value)
          .isA(iri(standoffTagInfo.standoffTagClassIri.value))
          .andHasOptional(KB.standoffTagHasEndIndex, standoffTagInfo.endIndex.map(i => literalOf(i)))
          .andHasOptional(KB.standoffTagHasStartParent, standoffTagInfo.startParentIri.map(v => iri(v.value)))
          .andHasOptional(KB.standoffTagHasEndParent, standoffTagInfo.endParentIri.map(v => iri(v.value)))
          .andHasOptional(KB.standoffTagHasOriginalXMLID, standoffTagInfo.originalXMLID.map(literalOf))
          .andHas(standoffAttributeLiterals(standoffTagInfo.attributes): _*)
          .andHas(KB.standoffTagHasStartIndex, literalOf(standoffTagInfo.startIndex))
          .andHas(KB.standoffTagHasUUID, literalOf(UuidUtil.base64Encode(standoffTagInfo.uuid)))
          .andHas(KB.standoffTagHasStart, literalOf(standoffTagInfo.startPosition))
          .andHas(KB.standoffTagHasEnd, literalOf(standoffTagInfo.endPosition))
      }.toList

    private def standoffAttributeLiterals(attributes: Seq[StandoffAttribute]): List[RdfPredicateObjectList] =
      attributes.map { attribute =>
        val v = attribute.value match
          case StandoffAttributeValue.IriAttribute(value)               => iri(value.value)
          case StandoffAttributeValue.UriAttribute(value)               => literalOfType(value, XSD.ANYURI)
          case StandoffAttributeValue.InternalReferenceAttribute(value) => iri(value.value)
          case StandoffAttributeValue.StringAttribute(value)            => literalOf(value)
          case StandoffAttributeValue.IntegerAttribute(value)           => literalOf(value)
          case StandoffAttributeValue.DecimalAttribute(value)           => literalOfType(value.toString(), XSD.DECIMAL)
          case StandoffAttributeValue.BooleanAttribute(value)           => literalOf(value)
          case StandoffAttributeValue.TimeAttribute(value)              => literalOfType(value.toString(), XSD.DATETIME)
        val p = iri(attribute.propertyIri.value)
        predicateObjectList(p, v)
      }.toList

    private def buildDateValuePattern(v: DateValueInfo, valueIri: String): List[TriplePattern] =
      List(
        iri(valueIri)
          .has(KB.valueHasStartJDN, literalOf(v.valueHasStartJDN))
          .andHas(KB.valueHasEndJDN, literalOf(v.valueHasEndJDN))
          .andHas(KB.valueHasStartPrecision, literalOf(v.valueHasStartPrecision.toString()))
          .andHas(KB.valueHasEndPrecision, literalOf(v.valueHasEndPrecision.toString()))
          .andHas(KB.valueHasCalendar, literalOf(v.valueHasCalendar.toString())),
      )

    private def buildFileValuePattern(v: FileValueTypeSpecificInfo, valueIri: String): TriplePattern = {
      val result = iri(valueIri)
        .has(KB.internalFilename, literalOf(v.fileValue.internalFilename))
        .andHas(KB.internalMimeType, literalOf(v.fileValue.internalMimeType))
        .andHasOptional(KB.originalFilename, v.fileValue.originalFilename.map(literalOf))
        .andHasOptional(KB.originalMimeType, v.fileValue.originalMimeType.map(literalOf))
        .andHasOptional(KB.hasCopyrightHolder, v.fileValue.copyrightHolder.map(_.value).map(literalOf))
        .andHasOptional(KB.hasLicense, v.fileValue.licenseIri.map(_.value).map(iri))
      v.fileValue.authorship.foreach(_.map(_.value).foreach(result.andHas(KB.hasAuthorship, _)))

      v match {
        case _: OtherFileValueInfo              => result
        case v: StillImageFileValueInfo         => result.andHas(KB.dimX, literalOf(v.dimX)).andHas(KB.dimY, literalOf(v.dimY))
        case v: StillImageExternalFileValueInfo => result.andHas(KB.externalUrl, literalOf(v.externalUrl))
        case v: DocumentFileValueInfo =>
          result
            .andHasOptional(KB.dimX, v.dimX.map(i => literalOf(i)))
            .andHasOptional(KB.dimY, v.dimY.map(i => literalOf(i)))
            .andHasOptional(KB.pageCount, v.pageCount.map(i => literalOf(i)))
      }
    }
  }
}

/**
 * Extends the `TriplePattern` class to add the `andHasOptional` method.
 *
 * This method allows adding an optional triple pattern to the existing triple pattern.
 *
 * @param p The RDF predicate of the optional triple pattern.
 * @param o The RDF object of the optional triple pattern.
 * @return A new `TriplePattern` object that represents the combined triple pattern.
 */
extension (tp: TriplePattern)
  def andHasOptional(p: RdfPredicate, o: Option[RdfObject]): TriplePattern =
    o.fold(tp)(o => tp.andHas(p, o))

/**
 * Extends the `Iri` class to add the `hasOptional` method.
 *
 * This method allows optionally creating a triple if the object is defined.
 *
 * @param p The RDF predicate of the optional triple pattern.
 * @param o The RDF object of the optional triple pattern.
 * @return An optional `TriplePattern` object that represents the combined triple pattern.
 */
extension (iri: Iri)
  def hasOptional(p: RdfPredicate, o: Option[RdfObject]): Option[TriplePattern] =
    o.map(o => iri.has(p, o))
