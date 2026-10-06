/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.ResourceId
import org.knora.webapi.slice.common.ResourceIri

/**
 * The validated parts of a dsp-app resource URL, `/resource/{shortcode}/{resourceId}?version=...`.
 */
final case class ResourceLandingRef(resourceIri: ResourceIri, version: Option[VersionDate])

object ResourceLandingRef {

  def from(shortcode: String, resourceId: String, version: Option[String]): Either[String, ResourceLandingRef] =
    for {
      sc  <- Shortcode.from(shortcode)
      id  <- ResourceId.from(resourceId)
      iri <- ResourceIri.from(s"http://rdfh.ch/${sc.value}/${id.value}")
      ver <- version match {
               case None    => Right(None)
               case Some(v) => VersionDate.from(v).map(Some(_))
             }
    } yield ResourceLandingRef(iri, ver)
}
