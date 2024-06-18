/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.InsertDataQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import zio.*

import java.time.Instant
import java.util.UUID

import dsp.constants.SalsahGui.IRI
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.twirl.NewLinkValueInfo
import org.knora.webapi.messages.twirl.NewValueInfo
import org.knora.webapi.messages.twirl.TypeSpecificValueInfo
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionDay
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

case class ResourceReadyToCreate(
  resourceIri: IRI,
  resourceClassIri: IRI,
  resourceLabel: String,
  creationDate: Instant,
  permissions: String,
  newValueInfos: Seq[NewValueInfo],
  linkUpdates: Seq[NewLinkValueInfo],
)

trait ResourcesRepo {
  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit]
}

final case class ResourcesRepoLive(triplestore: TriplestoreService) extends ResourcesRepo {

  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit] =
    triplestore.query(
      ResourcesRepoLive.createNewResourceQuery(
        dataGraphIri,
        resource,
        projectIri,
        userIri,
      ),
    )

}

object ResourcesRepoLive {
  val layer = ZLayer.derive[ResourcesRepoLive]

  private[service] def createNewResourceQuery(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ): Update =
    Update(
      sparql.v2.txt.createNewResource(
        dataNamedGraph = dataGraphIri.value,
        projectIri = projectIri,
        creatorIri = creatorIri,
        creationDate = resourceToCreate.creationDate,
        resourceIri = resourceToCreate.resourceIri,
        resourceClassIri = resourceToCreate.resourceClassIri,
        resourceLabel = resourceToCreate.resourceLabel,
        permissions = resourceToCreate.permissions,
        linkUpdates = resourceToCreate.linkUpdates,
        newValueInfos = resourceToCreate.newValueInfos,
      ),
    )

  private[service] def createNewResourceQueryWithBuilder(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ) = {
    val graph = iri(dataGraphIri.value)

    val query: InsertDataQuery =
      Queries
        .INSERT_DATA()
        .into(graph)
        .prefix(KnoraBaseVocab.NS, RDF.NS, RDFS.NS, XSD.NS)

    val resourcePattern =
      Rdf
        .iri(resourceToCreate.resourceIri)
        .isA(iri(resourceToCreate.resourceClassIri))
        .andHas(RDFS.LABEL, Rdf.literalOf(resourceToCreate.resourceLabel))
        .andHas(KnoraBaseVocab.isDeleted, Rdf.literalOf(false))
        .andHas(KnoraBaseVocab.attachedToUser, iri(creatorIri))
        .andHas(KnoraBaseVocab.attachedToProject, iri(projectIri))
        .andHas(KnoraBaseVocab.hasPermissions, Rdf.literalOf(resourceToCreate.permissions))
        .andHas(KnoraBaseVocab.creationDate, Rdf.literalOfType(resourceToCreate.creationDate.toString(), XSD.DATETIME))

    query.insertData(resourcePattern)

    for (newValueInfo <- resourceToCreate.newValueInfos) {
      val valuePattern =
        Rdf
          .iri(newValueInfo.valueIri)
          .isA(iri(newValueInfo.valueTypeIri))
          .andHas(KnoraBaseVocab.isDeleted, Rdf.literalOf(false))
          .andHas(KnoraBaseVocab.valueHasString, Rdf.literalOf(newValueInfo.valueHasString))
          .andHas(KnoraBaseVocab.valueHasUUID, Rdf.literalOf(UuidUtil.base64Encode(newValueInfo.valueUUID)))
          .andHas(KnoraBaseVocab.attachedToUser, iri(newValueInfo.valueCreator))
          .andHas(KnoraBaseVocab.hasPermissions, Rdf.literalOf(newValueInfo.valuePermissions))
          .andHas(KnoraBaseVocab.valueHasOrder, Rdf.literalOf(newValueInfo.valueHasOrder))
          .andHas(
            KnoraBaseVocab.valueCreationDate,
            Rdf.literalOfType(newValueInfo.creationDate.toString(), XSD.DATETIME),
          )

      newValueInfo.comment.foreach(comment =>
        valuePattern.andHas(KnoraBaseVocab.valueHasComment, Rdf.literalOf(comment)),
      )

      newValueInfo.value match
        case TypeSpecificValueInfo.LinkValueInfo(referredResourceIri)         => ???
        case TypeSpecificValueInfo.UnformattedTextValueInfo(valueHasLanguage) => ???
        case TypeSpecificValueInfo.FormattedTextValueInfo(
              valueHasLanguage,
              mappingIri,
              maxStandoffStartIndex,
              standoff,
            ) =>
          ???
        case TypeSpecificValueInfo.IntegerValueInfo(valueHasInteger) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasInteger, Rdf.literalOf(valueHasInteger))
        case TypeSpecificValueInfo.DecimalValueInfo(valueHasDecimal) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasDecimal, Rdf.literalOf(valueHasDecimal))
        case TypeSpecificValueInfo.BooleanValueInfo(valueHasBoolean) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasBoolean, Rdf.literalOf(valueHasBoolean))
        case TypeSpecificValueInfo.UriValueInfo(valueHasUri) =>
          valuePattern.andHas(KnoraBaseVocab.valueHasUri, Rdf.literalOf(valueHasUri))
        case TypeSpecificValueInfo.DateValueInfo(startJDN, endJDN, startPrecision, endPrecision, calendar) =>
          valuePattern
            .andHas(KnoraBaseVocab.valueHasStartJDN, Rdf.literalOf(startJDN))
            .andHas(KnoraBaseVocab.valueHasEndJDN, Rdf.literalOf(endJDN))
            .andHas(KnoraBaseVocab.valueHasStartPrecision, Rdf.literalOf(startPrecision.toString()))
            .andHas(KnoraBaseVocab.valueHasEndPrecision, Rdf.literalOf(endPrecision.toString()))
            .andHas(KnoraBaseVocab.valueHasCalendar, Rdf.literalOf(calendar.toString()))
        case TypeSpecificValueInfo.ColorValueInfo(valueHasColor)   => ???
        case TypeSpecificValueInfo.GeomValueInfo(valueHasGeometry) => ???
        case TypeSpecificValueInfo.StillImageFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              dimX,
              dimY,
            ) =>
          ???
        case TypeSpecificValueInfo.StillImageExternalFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              externalUrl,
            ) =>
          ???
        case TypeSpecificValueInfo.DocumentFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
              dimX,
              dimY,
              pageCount,
            ) =>
          ???
        case TypeSpecificValueInfo.OtherFileValueInfo(
              internalFilename,
              internalMimeType,
              originalFilename,
              originalMimeType,
            ) =>
          ???
        case TypeSpecificValueInfo.HierarchicalListValueInfo(valueHasListNode)                   => ???
        case TypeSpecificValueInfo.IntervalValueInfo(valueHasIntervalStart, valueHasIntervalEnd) => ???
        case TypeSpecificValueInfo.TimeValueInfo(valueHasTimeStamp)                              => ???
        case TypeSpecificValueInfo.GeonameValueInfo(valueHasGeonameCode)                         => ???

      resourcePattern.andHas(iri(newValueInfo.propertyIri), Rdf.iri(newValueInfo.valueIri))

      query.insertData(valuePattern)
    }

    Update(query.getQueryString())
  }
}

object KnoraBaseVocab {
  private val kb = "http://www.knora.org/ontology/knora-base#"

  val NS: Namespace = new SimpleNamespace("knora-base", kb)

  val isDeleted         = iri(kb + "isDeleted")
  val attachedToUser    = iri(kb + "attachedToUser")
  val attachedToProject = iri(kb + "attachedToProject")
  val hasPermissions    = iri(kb + "hasPermissions")
  val creationDate      = iri(kb + "creationDate")

  val valueHasString    = iri(kb + "valueHasString")
  val valueHasUUID      = iri(kb + "valueHasUUID")
  val valueHasComment   = iri(kb + "valueHasComment")
  val valueHasOrder     = iri(kb + "valueHasOrder")
  val valueCreationDate = iri(kb + "valueCreationDate")

  val valueHasInteger        = iri(kb + "valueHasInteger")
  val valueHasBoolean        = iri(kb + "valueHasBoolean")
  val valueHasDecimal        = iri(kb + "valueHasDecimal")
  val valueHasUri            = iri(kb + "valueHasUri")
  val valueHasStartJDN       = iri(kb + "valueHasStartJDN")
  val valueHasEndJDN         = iri(kb + "valueHasEndJDN")
  val valueHasStartPrecision = iri(kb + "valueHasStartPrecision")
  val valueHasEndPrecision   = iri(kb + "valueHasEndPrecision")
  val valueHasCalendar       = iri(kb + "valueHasCalendar")

}

object Run extends ZIOAppDefault {

  override def run = Console.printLine(prettyRes)

  val graphIri         = InternalIri("fooGraph")
  val projectIri       = "fooProjectIri"
  val userIri          = "fooUserIri"
  val resourceIri      = "fooResourceIri"
  val resourceClassIri = "fooClass"
  val label            = "fooLabel"
  val creationDate     = Instant.parse("2024-01-01T10:00:00.673298Z")
  val permissions      = "fooPermissions"

  val values = List(
    // decimal value
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "fooDecimalProp",
      valueIri = "fooDecimalValueIri",
      valueTypeIri = "DecimalValue",
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.DecimalValueInfo(42.42),
      valuePermissions = permissions,
      valueCreator = userIri,
      creationDate = creationDate,
      valueHasOrder = 3,
      valueHasString = "42.42",
      comment = None,
    ),
    // uri value
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "fooUriProp",
      valueIri = "fooUriValueIri",
      valueTypeIri = "UriValue",
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.UriValueInfo("http://example.com"),
      valuePermissions = permissions,
      valueCreator = userIri,
      creationDate = creationDate,
      valueHasOrder = 4,
      valueHasString = "http://example.com",
      comment = None,
    ),
    // date value
    NewValueInfo(
      resourceIri = resourceIri,
      propertyIri = "fooDateProp",
      valueIri = "fooDateValueIri",
      valueTypeIri = "DateValue",
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.DateValueInfo(
        0,
        0,
        DatePrecisionDay,
        DatePrecisionDay,
        CalendarNameGregorian,
      ),
      valuePermissions = permissions,
      valueCreator = userIri,
      creationDate = creationDate,
      valueHasOrder = 5,
      valueHasString = "2024-01-01T10:00:00.673298Z",
      comment = None,
    ),
  )

  val resourceDefinition = ResourceReadyToCreate(
    resourceIri = resourceIri,
    resourceClassIri = resourceClassIri,
    resourceLabel = label,
    creationDate = creationDate,
    permissions = permissions,
    newValueInfos = values,
    linkUpdates = Seq.empty,
  )

  val res = ResourcesRepoLive
    .createNewResourceQueryWithBuilder(
      dataGraphIri = graphIri,
      resourceToCreate = resourceDefinition,
      projectIri = projectIri,
      creatorIri = userIri,
    )
    .sparql

  val prettyRes = res.replace("{", "{\n").replace("}", "\n}")
}
