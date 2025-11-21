/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import java.util.UUID

import org.knora.webapi.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.resources.api.model.VersionDate

final case class ReadResourcesServiceFake(readResources: Seq[ReadResourceV2]) extends ReadResourcesService {
  def readResourcesSequence(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
  ): Task[ReadResourcesSequenceV2] =
    ZIO.succeed(ReadResourcesSequenceV2(readResources, Set.empty, false))

  def getResources(
    resourceIris: Seq[IRI],
    propertyIri: Option[SmartIri] = None,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = null

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
  ): Task[ReadResourcesSequenceV2] = null

  def getResourcePreviewWithDeletedResource(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = null

  def getResourcePreview(
    resourceIris: Seq[IRI],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = null
}
