/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2

import zio.Task
import zio.UIO
import zio.ZIO
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.util.search.ConstructQuery
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.v2.ResourceCountV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.resources.service.ReadResourcesService

/**
 * Records what the read dependencies of a client REST service receive and what its renderer is asked to render.
 * Every recording read returns an empty sequence that records the [[FormatOptions]] it is rendered with.
 */
final class ServingProbe {
  private val readLog     = new AtomicReference(Vector.empty[SchemaRendering])
  private val renderedLog = new AtomicReference(Vector.empty[FormatOptions])

  def reads: Vector[SchemaRendering]  = readLog.get
  def rendered: Vector[FormatOptions] = renderedLog.get

  val renderer: KnoraResponseRenderer = KnoraResponseRenderer(null, null)

  val readResources: ReadResourcesService = new UnusedReadResourcesService {
    override def getResourcesWithDeletedResource(
      resourceIris: Seq[ResourceIri],
      propertyIri: Option[SmartIri],
      valueUuid: Option[UUID],
      versionDate: Option[VersionDate],
      withDeleted: Boolean,
      showDeletedValues: Boolean,
      targetSchema: ApiV2Schema,
      schemaOptions: Set[Rendering],
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = read(SchemaRendering(targetSchema, schemaOptions))
  }

  val searchResponder: SearchResponderV2 = ProbeSearchResponder(this)

  def read(schemaRendering: SchemaRendering): UIO[ReadResourcesSequenceV2] = ZIO.succeed {
    readLog.updateAndGet(_ :+ schemaRendering)
    new ReadResourcesSequenceV2(Seq.empty) {
      override def format(opts: FormatOptions, config: AppConfig): String = {
        renderedLog.updateAndGet(_ :+ opts)
        ""
      }
    }
  }
}

object ServingProbe {

  val options: FormatOptions = FormatOptions(JsonLD, ApiV2Complex, Set(MarkupRendering.Xml))

  /**
   * Checks that `call` reads exactly once, that the read receives [[ServeStoredXml]] only when serving is enabled,
   * and that the renderer receives the options of the request unchanged.
   */
  def servingTests(name: String)(call: (ServingProbe, StoredXmlServing) => Task[Any]): Spec[Any, Throwable] =
    suite(name)(
      test("the read receives ServeStoredXml and the renderer the original options when serving is enabled") {
        val probe = ServingProbe()
        call(probe, StoredXmlServing.forTest(true)).as(
          assertTrue(
            probe.reads == Vector(SchemaRendering(ApiV2Complex, options.rendering + ServeStoredXml)),
            probe.rendered == Vector(options),
          ),
        )
      },
      test("the read does not receive ServeStoredXml when serving is disabled") {
        val probe = ServingProbe()
        call(probe, StoredXmlServing.forTest(false)).as(
          assertTrue(
            probe.reads == Vector(SchemaRendering(ApiV2Complex, options.rendering)),
            probe.rendered == Vector(options),
          ),
        )
      },
    )
}

/** A [[SearchResponderV2]] whose rendering-aware reads are recorded by the probe; every other method dies. */
class ProbeSearchResponder(probe: ServingProbe) extends SearchResponderV2 {
  protected def tracing: Tracing = null

  private def unused = ZIO.dieMessage("not used by this spec")

  override def gravsearchV2(
    query: IRI,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = probe.read(rendering)
  def gravsearchV2(
    query: ConstructQuery,
    schemaAndOptions: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = unused
  def gravsearchCountV2(
    query: ConstructQuery,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ResourceCountV2] = unused
  def searchIncomingLinksV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = probe.read(rendering)
  def searchStillImageRepresentationsV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = probe.read(rendering)
  def searchStillImageRepresentationsCountV2(
    resourceIri: ResourceIri,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ResourceCountV2] = unused
  def searchIncomingRegionsV2(
    resourceIri: ResourceIri,
    offset: Int,
    rendering: SchemaRendering,
    user: User,
    limitToProject: Option[ProjectIri],
  ): Task[ReadResourcesSequenceV2] = probe.read(rendering)
  def fulltextSearchCountV2(
    searchValue: IRI,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limitToStandoffClass: Option[SmartIri],
  ): Task[ResourceCountV2] = unused
  def fulltextSearchV2(
    searchValue: IRI,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limitToStandoffClass: Option[SmartIri],
    returnFiles: Boolean,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = probe.read(schemaAndOptions)
  def searchResourcesByLabelCountV2(
    searchValue: String,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): Task[ResourceCountV2] = unused
  def searchResourcesByLabelV2(
    searchValue: String,
    offset: Int,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unused
  def searchResourcesByProjectAndClassV2(
    projectIri: ProjectIri,
    resourceClass: SmartIri,
    orderByProperty: Option[SmartIri],
    page: Int,
    schemaAndOptions: SchemaRendering,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = probe.read(schemaAndOptions)
}

/** A [[ReadResourcesService]] on which every method dies; a spec overrides the reads it exercises. */
class UnusedReadResourcesService extends ReadResourcesService {
  private def unused = ZIO.dieMessage("not used by this spec")

  def readResourcesSequence(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri],
    valueUuid: Option[UUID],
    preview: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean,
    standoff: StandoffSelection,
    skipRetrievalChecks: Boolean,
    standoffTagFilter: Option[SmartIri],
  ): Task[ReadResourcesSequenceV2] = unused
  def readResourcesSequencePar(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri],
    valueUuid: Option[UUID],
    preview: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean,
    standoff: StandoffSelection,
    skipRetrievalChecks: Boolean,
    standoffTagFilter: Option[SmartIri],
  ): Task[ReadResourcesSequenceV2] = unused
  def getResources(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri],
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unused
  def getResourcesWithDeletedResource(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri],
    valueUuid: Option[UUID],
    versionDate: Option[VersionDate],
    withDeleted: Boolean,
    showDeletedValues: Boolean,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unused
  def getResourcePreviewWithDeletedResource(
    resourceIris: Seq[ResourceIri],
    withDeleted: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unused
  def getResourcePreview(
    resourceIris: Seq[ResourceIri],
    withDeleted: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unused
}
