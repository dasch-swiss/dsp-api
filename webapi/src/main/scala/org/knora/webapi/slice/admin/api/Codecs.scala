/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.domain.SparqlEncodedString

object Codecs {
  object TapirCodec {

    private type StringCodec[A] = Codec[String, A, CodecFormat.TextPlain]
    private def stringCodec[A <: StringValue](from: String => Either[String, A]): StringCodec[A] =
      stringCodec(from, _.value)
    private def stringCodec[A](from: String => Either[String, A], to: A => String): StringCodec[A] =
      Codec.string.mapEither(from)(to)

    implicit val assetId: StringCodec[AssetId]                         = stringCodec(AssetId.from, _.value)
    implicit val shortcode: StringCodec[Shortcode]                     = stringCodec(Shortcode.from)
    implicit val shortname: StringCodec[Shortname]                     = stringCodec(Shortname.from)
    implicit val projectIri: StringCodec[ProjectIri]                   = stringCodec(ProjectIri.from)
    implicit val sparqlEncodedString: StringCodec[SparqlEncodedString] = stringCodec(SparqlEncodedString.from)
  }

  object ZioJsonCodec {

    private type StringCodec[A] = JsonCodec[A]
    private def stringCodec[A <: StringValue](from: String => Either[String, A]): StringCodec[A] =
      stringCodec(from, _.value)
    private def stringCodec[A](from: String => Either[String, A], to: A => String): StringCodec[A] =
      JsonCodec[String].transformOrFail(from, to)

    implicit val assetId: StringCodec[AssetId]                         = stringCodec(AssetId.from, _.value)
    implicit val shortcode: StringCodec[Shortcode]                     = stringCodec(Shortcode.from)
    implicit val shortname: StringCodec[Shortname]                     = stringCodec(Shortname.from)
    implicit val projectIri: StringCodec[ProjectIri]                   = stringCodec(ProjectIri.from)
    implicit val sparqlEncodedString: StringCodec[SparqlEncodedString] = stringCodec(SparqlEncodedString.from)
  }
}
