/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import monocle.syntax.all.*
import zio.*

import java.util.UUID

import dsp.errors.NotFoundException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.*
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeomValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadOtherValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.RegionPreviewValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.resources.repo.GetResourcePropertiesAndValuesQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

trait ReadResourcesService {
  def readResourcesSequence(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
    queryStandoff: Boolean = false,
    skipRetrievalChecks: Boolean = false,
    standoffTagFilter: Option[SmartIri] = None,
  ): Task[ReadResourcesSequenceV2]

  def readResourcesSequencePar(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
    queryStandoff: Boolean = false,
    skipRetrievalChecks: Boolean = false,
    standoffTagFilter: Option[SmartIri] = None,
  ): Task[ReadResourcesSequenceV2]

  def getResources(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri] = None,
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  def getResourcesWithDeletedResource(
    resourceIris: Seq[ResourceIri],
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
    resourceIris: Seq[ResourceIri],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]

  def getResourcePreview(
    resourceIris: Seq[ResourceIri],
    withDeleted: Boolean = true,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2]
}

/**
 * Live implementation of [[ReadResourcesService]].
 *
 * Core: `readResourcesSequence_` is the core of this unit. It runs the triplestore query. It rebuilds the resources with
 * [[ConstructResponseUtilV2]]. It then post-processes every read.
 *
 * Public API: Every public trait method is a thin wrapper. Each one calls `readResourcesSequence_`. Each one just fixes a set of
 * flags: preview, version date, deletion handling, standoff querying, and so on.
 *
 * Post-processing: this step augments each `RegionPreviewValue` in the response. For each one, it fetches the referenced
 * region, to get its geometry. It also fetches the still image that the region is `isRegionOf`, to get the internal
 * filename. From those, it builds the IIIF preview URL.
 *
 * Those extra fetches are only used to build the URL. They are not added to the returned sequence.
 *
 * This is the only reason the service depends on [[AppConfig]]: it needs the Sipi base URL.
 */
final case class ReadResourcesServiceLive(
  private val appConfig: AppConfig,
  private val constructResponseUtilV2: ConstructResponseUtilV2,
  private val standoffTagUtilV2: StandoffTagUtilV2,
  private val triplestore: TriplestoreService,
)(implicit val stringFormatter: StringFormatter)
    extends ReadResourcesService {
  private def readResourcesSequence_(
    resourceIris: Seq[ResourceIri],
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
    standoffTagFilter: Option[SmartIri] = None,
  ): Task[ReadResourcesSequenceV2] =
    if (resourceIris.isEmpty)
      ZIO.succeed(ReadResourcesSequenceV2.Empty)
    else {
      val resourceIriStrings = resourceIris.distinct.map(_.value)
      for {
        resourcesWithValues <-
          triplestore
            .query(
              Construct(
                GetResourcePropertiesAndValuesQuery.build(
                  resourceIris = resourceIriStrings,
                  preview = preview,
                  withDeleted = withDeleted,
                  maybePropertyIri = propertyIri,
                  maybeValueUuid = valueUuid,
                  maybeVersionDate = versionDate.map(_.value),
                  queryAllNonStandoff = true,
                  queryStandoff = queryStandoff,
                  standoffTagFilter = standoffTagFilter,
                ),
                if (queryStandoff) SparqlTimeout.Maintenance else SparqlTimeout.Standard,
              ),
            )
            .flatMap(_.asExtended)
            .flatMap(constructResponseUtilV2.splitMainResourcesAndValueRdfData(_, requestingUser))

        mappings <-
          ZIO.when(queryStandoff) {
            constructResponseUtilV2.mappingsFromQueryResults(resourcesWithValues.resources)
          }

        baseSequence <-
          constructResponseUtilV2.createApiResponse(
            mainResourcesAndValueRdfData = resourcesWithValues,
            orderByResourceIri = resourceIriStrings,
            pageSizeBeforeFiltering = resourceIris.size, // doesn't matter because we're not doing paging
            mappings = mappings.getOrElse(Map.empty),
            queryStandoff = queryStandoff,
            versionDate = versionDate.map(_.value),
            calculateMayHaveMoreResults = false,
            targetSchema = targetSchema,
            requestingUser = requestingUser,
          )

        readSequence <- augmentRegionPreviewValues(baseSequence, targetSchema, requestingUser)

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
              val matching =
                readSequence.resources.exists(_.values.values.exists(_.exists(_.valueHasUUID == valueUuid)))
              ZIO.unless(matching) {
                ZIO.fail(NotFoundException(msg(UuidUtil.base64Encode(valueUuid))))
              }
            }
          }
      } yield readSequence
        .focus(_.resources)
        .modify(_.map(r => if (markDeletions) r.markDeleted(versionDate, showDeletedValues) else r))
    }

  def readResourcesSequencePar(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
    queryStandoff: Boolean = false,
    skipRetrievalChecks: Boolean = false,
    standoffTagFilter: Option[SmartIri] = None,
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
          queryStandoff = queryStandoff,
          skipRetrievalChecks = skipRetrievalChecks,
          standoffTagFilter = standoffTagFilter,
        )
      }
      .withParallelism(5)
      .map(_.fold(ReadResourcesSequenceV2(Seq.empty))(_ ++ _))

  /**
   * Augments every [[RegionPreviewValueContentV2]] in `seq` with a computed IIIF URL.
   *
   * For each referenced region we fetch the region resource (for its geometry) and the still image
   * the region is `isRegionOf` (for its internal filename and project shortcode). The geometry's
   * bounding box becomes the IIIF region selector `pct:x,y,w,h`, and the URL is rendered at full size:
   * `{sipiBaseUrl}/{shortcode}/{filename}/pct:x,y,w,h/full/0/default.jpg`.
   */
  private def augmentRegionPreviewValues(
    seq: ReadResourcesSequenceV2,
    ts: ApiV2Schema,
    user: User,
  ): Task[ReadResourcesSequenceV2] = {
    val regionIris = seq.resources
      .flatMap(_.values.values.flatten)
      .map(_.valueContent)
      .collect { case rp: RegionPreviewValueContentV2 => rp.regionIri }
      .distinct

    if (regionIris.isEmpty) ZIO.succeed(seq)
    else
      for {
        regions  <- readResourcesSequence_(regionIris, targetSchema = ts, requestingUser = user)
        imageIris = regions.resources.flatMap(_.isRegionOfValueReferredIri.toList).distinct
        images   <- readResourcesSequence_(imageIris, targetSchema = ts, requestingUser = user)
        iiifUrls  = regionIiifUrls(regions, images)
      } yield seq
        .focus(_.resources)
        .modify(_.map(r => augmentResourceRegionPreviews(r, iiifUrls)))
  }

  /** Builds a `regionIri -> iiifUrl` map from the fetched region and image resources. */
  private def regionIiifUrls(
    regions: ReadResourcesSequenceV2,
    images: ReadResourcesSequenceV2,
  ): Map[ResourceIri, String] = {
    val imagesByIri = images.resources.map(img => img.resourceIri -> img).toMap
    regions.resources.flatMap { region =>
      for {
        regionSelector <- geometryToIiifRegion(region)
        imageIri       <- region.isRegionOfValueReferredIri
        image          <- imagesByIri.get(imageIri)
        stillImage     <- image.values.values.flatten.map(_.valueContent).collectFirst {
                        case si: StillImageFileValueContentV2 => si
                      }
      } yield region.resourceIri ->
        s"${appConfig.sipi.externalBaseUrl}/${image.projectADM.shortcode}/${stillImage.fileValue.internalFilename}/$regionSelector/full/0/default.jpg"
    }.toMap
  }

  /** Derives the IIIF `pct:x,y,w,h` region selector from a region resource's geometry value. */
  private def geometryToIiifRegion(region: ReadResourceV2): Option[String] =
    region.values.values.flatten
      .map(_.valueContent)
      .collectFirst { case geom: GeomValueContentV2 => geom }
      .flatMap(geom => GeomValueContentV2.parsePoints(geom.valueHasGeometry).toOption)
      .filter(_.nonEmpty)
      .map { points =>
        val xs                     = points.map(_.x)
        val ys                     = points.map(_.y)
        val minX                   = xs.min
        val minY                   = ys.min
        val w                      = xs.max - minX
        val h                      = ys.max - minY
        def pct(d: Double): String =
          BigDecimal(d * 100).setScale(6, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString
        s"pct:${pct(minX)},${pct(minY)},${pct(w)},${pct(h)}"
      }

  /** Replaces the `iiifUrl` on each [[RegionPreviewValueContentV2]] of `resource` using `iiifUrls`. */
  private def augmentResourceRegionPreviews(
    resource: ReadResourceV2,
    iiifUrls: Map[ResourceIri, String],
  ): ReadResourceV2 =
    resource
      .focus(_.values)
      .modify(
        _.view
          .mapValues(_.map {
            case rov @ ReadOtherValueV2(_, _, _, _, _, _, rp: RegionPreviewValueContentV2, _, _) =>
              iiifUrls
                .get(rp.regionIri)
                .fold(rov: ReadValueV2)(url => rov.copy(valueContent = rp.copy(iiifUrl = Some(url))))
            case readValue => readValue
          })
          .toMap,
      )

  def readResourcesSequence(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri] = None,
    valueUuid: Option[UUID] = None,
    preview: Boolean = false,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean = true,
    queryStandoff: Boolean = false,
    skipRetrievalChecks: Boolean = false,
    standoffTagFilter: Option[SmartIri] = None,
  ): Task[ReadResourcesSequenceV2] =
    readResourcesSequence_(
      resourceIris,
      propertyIri,
      valueUuid = valueUuid,
      preview = preview,
      targetSchema,
      requestingUser,
      withDeleted = withDeleted,
      queryStandoff = queryStandoff,
      skipRetrievalChecks = skipRetrievalChecks,
      standoffTagFilter = standoffTagFilter,
    )

  def getResources(
    resourceIris: Seq[ResourceIri],
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
    resourceIris: Seq[ResourceIri],
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
    resourceIris: Seq[ResourceIri],
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
    resourceIris: Seq[ResourceIri],
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
