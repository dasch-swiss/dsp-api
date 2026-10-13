/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.fair.ports.FairFileKind
import org.knora.webapi.slice.fair.ports.FairFileValue
import org.knora.webapi.slice.fair.ports.FairResource
import org.knora.webapi.slice.fair.ports.FairResources
import org.knora.webapi.slice.resources.domain.ResourceMetadataRules

/**
 * Reads a resource as the anonymous user for the FAIR landing pages.
 */
final case class FairResourcesLive(
  readResources: ReadResourcesService,
  iriConverter: IriConverter,
  ingestConfig: DspIngestConfig,
) extends FairResources {

  // A missing, deleted or hidden resource is the same answer to an anonymous reader: there is nothing to show.
  override def readAsAnonymous(iri: ResourceIri): Task[Option[FairResource]] =
    for {
      descriptionProperty <- ZIO
                               .foreach(ResourceMetadataRules.descriptionPropertyIri(iri.shortcode))(
                                 iriConverter.asPropertyIri(_).mapError(new IllegalArgumentException(_)),
                               )
      read <- readResources
                .readResourcesSequence(
                  Seq(iri),
                  targetSchema = ApiV2Complex,
                  requestingUser = KnoraSystemInstances.Users.AnonymousUser,
                  withDeleted = false,
                  skipRetrievalChecks = false,
                )
                .map(Some(_))
                .catchSome {
                  case _: NotFoundException  => ZIO.none
                  case _: ForbiddenException => ZIO.none
                }
    } yield read
      .flatMap(_.resources.headOption)
      .map(FairResourcesLive.toFairResource(_, descriptionProperty, ingestConfig.externalBaseUrl))
}

object FairResourcesLive {

  def toFairResource(
    r: ReadResourceV2,
    descriptionProperty: Option[PropertyIri],
    ingestExternalBaseUrl: String,
  ): FairResource =
    FairResource(
      iri = r.resourceIri,
      label = r.label,
      classIri = r.resourceClassIri.toComplexSchema.toString,
      userPermission = r.userPermission,
      creationDate = r.creationDate,
      lastModificationDate = r.lastModificationDate,
      authorship = r.resourceAuthorship.map(_.value).toList,
      description = descriptionProperty
        .flatMap(p => r.findValues(p).headOption)
        .map(_.valueContent.valueHasString)
        .filter(_.trim.nonEmpty),
      file = toFairFileValue(r, ingestExternalBaseUrl),
    )

  private def toFairFileValue(r: ReadResourceV2, ingestExternalBaseUrl: String): Option[FairFileValue] =
    ResourceMetadataRules.selectFileValue(r).map { case (value, fc) =>
      val external = fc.isInstanceOf[StillImageExternalFileValueContentV2]
      val assetId  = Option.unless(external)(ResourceMetadataRules.assetIdOf(fc))
      FairFileValue(
        kind = if (external) FairFileKind.ExternalIiif else FairFileKind.Stored,
        userPermission = value.userPermission,
        typeOfData = ResourceMetadataRules.typeOfData(fc),
        assetId = assetId,
        originalUrl =
          assetId.map(ResourceMetadataRules.originalFileUrl(ingestExternalBaseUrl, r.resourceIri.shortcode, _)),
        licenseIri = fc.fileValue.licenseIri.map(_.value),
        authorship = fc.fileValue.authorship.toList.flatten.map(_.value),
      )
    }

  val layer = ZLayer.derive[FairResourcesLive]
}
