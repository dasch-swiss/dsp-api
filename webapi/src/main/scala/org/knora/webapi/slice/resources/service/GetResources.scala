/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import java.util.UUID

import org.knora.webapi.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.resources.api.model.VersionDate
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2

final case class GetResources_(
  private val constructResponseUtilV2: ConstructResponseUtilV2,
  private val standoffTagUtilV2: StandoffTagUtilV2,
  private val triplestore: TriplestoreService,
)(implicit val stringFormatter: StringFormatter) {
  // def getResources
  // def getResourcesWithDeletedResource(
  //   resourceIris: Seq[IRI],
  //   propertyIri: Option[SmartIri] = None,
  //   valueUuid: Option[UUID] = None,
  //   versionDate: Option[VersionDate] = None,
  //   withDeleted: Boolean = true,
  //   showDeletedValues: Boolean = false,
  //   targetSchema: ApiV2Schema,
  //   schemaOptions: Set[Rendering],
  //   requestingUser: User,
  // ): Task[ReadResourcesSequenceV2]

//   // getResourcePreview (sparql)
//   def getResourcePreviewWithDeletedResource(
//     resourceIris: Seq[IRI],
//     withDeleted: Boolean = true,
//     targetSchema: ApiV2Schema,
//     requestingUser: User,
//   ): Task[ReadResourcesSequenceV2]

  def readResourcesSequence(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    versionDate: Option[VersionDate] = None,
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
    queryStandoff: Boolean,
    preview: Boolean,
  ): Task[ReadResourcesSequenceV2] =
    for {
      response <-
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
          constructResponseUtilV2.mappingsFromQueryResults(resourcesWithRdf.resources, requestingUser)
        }

      readSequence <-
        constructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = resourcesWithRdf,
          orderByResourceIri = resourceIris.distinct,
          pageSizeBeforeFiltering = resourceIris.size, // doesn't matter because we're not doing paging
          mappings = mappings.getOrElse(Map.empty),
          queryStandoff = queryStandoff,
          versionDate = versionDate.map(_.value),
          calculateMayHaveMoreResults = false,
          targetSchema = targetSchema,
          requestingUser = requestingUser,
        )

      _ <- readSequence.checkResourceIris(resourceIris.toSet, readSequence)
    } yield readSequence
}

object GetResources_ {
  val layer = ZLayer.derive[GetResources_]
}
