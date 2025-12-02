/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import monocle.syntax.all.*
import zio.*

import java.util.UUID

import dsp.errors.NotFoundException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.resources.api.model.VersionDate
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

trait ReadResourcesService {
  def readResourcesSequence(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
  ): Task[ReadResourcesSequenceV2]

  def readResourcesSequencePar(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
    skipRetrievalChecks: Boolean = false,
  ): Task[ReadResourcesSequenceV2]

  def getResources(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  def getResourcesWithDeletedResource(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    versionDate: Option[VersionDate] = None,
    withDeleted: Boolean = true,
    showDeletedValues: Boolean = false,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  def getResourcePreviewWithDeletedResource(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  def getResourcePreview(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]
}

// Raitis TODO: add scaladoc
final case class ReadResourcesServiceLive(
  private val constructResponseUtilV2: ConstructResponseUtilV2,
  private val standoffTagUtilV2: StandoffTagUtilV2,
  private val triplestore: TriplestoreService,
)(implicit val stringFormatter: StringFormatter)
    extends ReadResourcesService {
  private def readResourcesSequence_(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    versionDate: Option[VersionDate] = None,
    withDeleted: Boolean = true,
    queryStandoff: Boolean = false,
    failOnMissingValueUuid: Boolean = false,
    markDeletions: Boolean = false,
    showDeletedValues: Boolean = false,
    skipRetrievalChecks: Boolean = false,
  ): Task[ReadResourcesSequenceV2] =
    for {
      resourcesWithValues <-
        triplestore
          .query(
            Construct(
              sparql.v2.txt
                .getResourcePropertiesAndValues(
                  resourceIris = resourceIris.distinct,
                  preview = preview,
                  withDeleted = withDeleted,
                  maybePropertyIri = propertyIri,
                  maybeValueUuid = valueUuid,
                  maybeVersionDate = versionDate.map(_.value),
                  queryAllNonStandoff = true,
                  queryStandoff = queryStandoff,
                ),
            ),
          )
          .flatMap(_.asExtended)
          .map(constructResponseUtilV2.splitMainResourcesAndValueRdfData(_, requestingUser))

      mappings <-
        ZIO.when(queryStandoff) {
          constructResponseUtilV2.mappingsFromQueryResults(resourcesWithValues.resources, requestingUser)
        }

      readSequence <-
        constructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = resourcesWithValues,
          orderByResourceIri = resourceIris.distinct,
          pageSizeBeforeFiltering = resourceIris.size, // doesn't matter because we're not doing paging
          mappings = mappings.getOrElse(Map.empty),
          queryStandoff = queryStandoff,
          versionDate = versionDate.map(_.value),
          calculateMayHaveMoreResults = false,
          targetSchema = targetSchema,
          requestingUser = requestingUser,
        )

      _ <-
        ZIO.foreach(readSequence.checkResourceIris(resourceIris.toSet, readSequence)) { throwable =>
          if (skipRetrievalChecks)
            ZIO.logError(throwable.toString)
          else
            ZIO.fail(throwable)
        }

      _ <-
        ZIO.when(failOnMissingValueUuid) {
          ZIO.foreach(valueUuid) { valueUuid =>
            val msg      = (u: String) => s"Value with UUID ${u} not found (maybe you do not have permission to see it)"
            val matching = readSequence.resources.exists(_.values.values.exists(_.exists(_.valueHasUUID == valueUuid)))
            ZIO.unless(matching) {
              ZIO.fail(NotFoundException(msg(UuidUtil.base64Encode(valueUuid))))
            }
          }
        }
    } yield readSequence
      .focus(_.resources)
      .modify(_.map(r => if (markDeletions) r.markDeleted(versionDate, showDeletedValues) else r))

  def readResourcesSequencePar(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
    skipRetrievalChecks: Boolean = false,
  ): Task[ReadResourcesSequenceV2] =
    ZIO
      .foreachPar(Chunk.fromIterable(resourceIris).grouped(500).map(_.toSeq).toArray) { resourceIris =>
        readResourcesSequence_(
          resourceIris,
          propertyIri,
          valueUuid = valueUuid,
          preview = preview,
          targetSchema,
          requestingUser,
          withDeleted = withDeleted,
          skipRetrievalChecks = skipRetrievalChecks,
        )
      }
      .withParallelism(50)
      .map(_.fold(ReadResourcesSequenceV2(Seq.empty))(_ ++ _))

  def readResourcesSequence(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
  ): Task[ReadResourcesSequenceV2] =
    readResourcesSequence_(
      resourceIris,
      propertyIri,
      valueUuid = valueUuid,
      preview = preview,
      targetSchema,
      requestingUser,
      withDeleted = withDeleted,
    )

  def getResources(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] =
    readResourcesSequence_(
      resourceIris = resourceIris,
      propertyIri = propertyIri,
      targetSchema = targetSchema,
      requestingUser = requestingUser,
      // Passed down to ConstructResponseUtilV2.makeTextValueContentV2.
      queryStandoff = SchemaOptions.queryStandoffWithTextValues(targetSchema, schemaOptions),
      failOnMissingValueUuid = true,
    )

  def getResourcesWithDeletedResource(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    versionDate: Option[VersionDate] = None,
    withDeleted: Boolean = true,
    showDeletedValues: Boolean = false,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] =
    readResourcesSequence_(
      resourceIris = resourceIris,
      propertyIri = propertyIri,
      valueUuid = valueUuid,
      versionDate = versionDate,
      withDeleted = withDeleted,
      targetSchema = targetSchema,
      requestingUser = requestingUser,
      queryStandoff = SchemaOptions.queryStandoffWithTextValues(targetSchema, schemaOptions),
      failOnMissingValueUuid = true,
      markDeletions = true,
      showDeletedValues = showDeletedValues,
    )

  def getResourcePreviewWithDeletedResource(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] =
    readResourcesSequence_(
      resourceIris = resourceIris,
      versionDate = None,
      withDeleted = withDeleted,
      targetSchema = targetSchema,
      requestingUser = requestingUser,
      preview = true,
      markDeletions = true,
    )

  def getResourcePreview(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] =
    readResourcesSequence_(
      resourceIris = resourceIris,
      versionDate = None,
      withDeleted = withDeleted,
      targetSchema = targetSchema,
      requestingUser = requestingUser,
      preview = true,
    )
}

object ReadResourcesServiceLive {
  val layer = ZLayer.derive[ReadResourcesServiceLive]
}
