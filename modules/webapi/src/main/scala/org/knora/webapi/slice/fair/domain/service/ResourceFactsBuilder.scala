/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.service

import zio.*

import org.knora.webapi.config.FairConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.domain.FactsInputs
import org.knora.webapi.slice.fair.domain.LicenceLookup
import org.knora.webapi.slice.fair.domain.ResourceFacts
import org.knora.webapi.slice.fair.ports.*

final class ResourceFactsBuilder(
  resources: FairResources,
  projects: FairProjects,
  classLabels: FairClassLabels,
  assets: FairAssets,
  config: FairConfig,
  sf: StringFormatter,
) {

  /**
   * The facts for the route's shortcode and resource id as the anonymous user sees them; None when the id is malformed
   * or the resource is missing, deleted or not visible. Fails only on an infrastructure failure.
   */
  def build(shortcode: String, resourceId: String): Task[Option[ResourceFacts]] =
    parse(shortcode, resourceId) match {
      case None      => ZIO.none
      case Some(iri) =>
        resources.readAsAnonymous(iri).flatMap {
          case None           => ZIO.none
          case Some(resource) => gather(iri, resource).map(ResourceFacts.derive)
        }
    }

  // The IRI is always built from the uppercased shortcode so the canonical URLs never carry the request's casing.
  private def parse(shortcode: String, resourceId: String): Option[ResourceIri] = {
    val upper = shortcode.toUpperCase
    for {
      _   <- Shortcode.from(upper).toOption
      iri <- ResourceIri.from(s"http://rdfh.ch/$upper/$resourceId").toOption
    } yield iri
  }

  private def gather(iri: ResourceIri, resource: FairResource): Task[FactsInputs] =
    for {
      project <- projects
                   .findByShortcode(iri.shortcode)
                   .someOrFail(new IllegalStateException(s"Resource ${iri.value} exists but its project does not"))
      fileLicence    <- lookupLicence(resource.file.flatMap(_.licenseIri))
      projectLicence <- lookupLicence(project.dataLicenseIri)
      labels         <- classLabels.labels(resource.classIri)
      sidecar        <- findSidecar(iri, resource)
    } yield FactsInputs(
      resource = resource,
      project = project,
      fileLicence = fileLicence,
      projectLicence = projectLicence,
      classLabels = labels,
      sidecar = sidecar,
      ark = sf.resourceIriToArkUrl(iri),
      projectArk = sf.projectArkUrl(iri.shortcode),
      appExternalBaseUrl = config.appExternalBaseUrl,
    )

  private def lookupLicence(licenceIri: Option[String]): Task[LicenceLookup] =
    licenceIri match {
      case None      => ZIO.succeed(LicenceLookup.NotRecorded)
      case Some(iri) =>
        projects.findLicence(iri).map {
          case Some(licence) => LicenceLookup.Resolved(licence)
          case None          => LicenceLookup.Unresolved
        }
    }

  private def findSidecar(iri: ResourceIri, resource: FairResource): UIO[Option[FairAsset]] =
    resource.file match {
      case Some(f) if f.userPermission >= ObjectAccess.View && f.kind == FairFileKind.Stored =>
        f.assetId.fold(ZIO.none)(assets.findSidecar(iri.shortcode, _))
      case _ => ZIO.none
    }
}

object ResourceFactsBuilder {
  val layer: URLayer[
    FairResources & FairProjects & FairClassLabels & FairAssets & FairConfig & StringFormatter,
    ResourceFactsBuilder,
  ] = ZLayer.derive[ResourceFactsBuilder]
}
