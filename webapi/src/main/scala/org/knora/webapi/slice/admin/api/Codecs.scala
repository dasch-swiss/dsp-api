/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.json.JsonCodec

import dsp.valueobjects.V2
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.RestrictedViewSize
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.domain.SparqlEncodedString

object Codecs {
  object TapirCodec {

    private type StringCodec[A] = Codec[String, A, CodecFormat.TextPlain]
    private def stringCodec[A <: StringValue](from: String => Either[String, A]): StringCodec[A] =
      stringCodec(from, _.value)
    private def stringCodec[A](from: String => Either[String, A], to: A => String): StringCodec[A] =
      Codec.string.mapEither(from)(to)

    private def booleanCodec[A <: BooleanValue](from: Boolean => A): StringCodec[A] =
      booleanCodec(from, _.value)
    private def booleanCodec[A](from: Boolean => A, to: A => Boolean): StringCodec[A] =
      Codec.boolean.map(from)(to)

    implicit val assetId: StringCodec[AssetId]                         = stringCodec(AssetId.from, _.value)
    implicit val keyword: StringCodec[Keyword]                         = stringCodec(Keyword.from)
    implicit val logo: StringCodec[Logo]                               = stringCodec(Logo.from)
    implicit val longname: StringCodec[Longname]                       = stringCodec(Longname.from)
    implicit val projectIri: StringCodec[ProjectIri]                   = stringCodec(ProjectIri.from)
    implicit val restrictedViewSize: StringCodec[RestrictedViewSize]   = stringCodec(RestrictedViewSize.from)
    implicit val selfJoin: StringCodec[SelfJoin]                       = booleanCodec(SelfJoin.from)
    implicit val shortcode: StringCodec[Shortcode]                     = stringCodec(Shortcode.from)
    implicit val shortname: StringCodec[Shortname]                     = stringCodec(Shortname.from)
    implicit val sparqlEncodedString: StringCodec[SparqlEncodedString] = stringCodec(SparqlEncodedString.from)
    implicit val status: StringCodec[Status]                           = booleanCodec(Status.from)
    implicit val userIri: StringCodec[UserIri]                         = stringCodec(UserIri.from)
  }

  object ZioJsonCodec {

    private type StringCodec[A] = JsonCodec[A]
    private def stringCodec[A <: StringValue](from: String => Either[String, A]): StringCodec[A] =
      stringCodec(from, _.value)
    private def stringCodec[A](from: String => Either[String, A], to: A => String): StringCodec[A] =
      JsonCodec[String].transformOrFail(from, to)

    private def booleanCodec[A <: BooleanValue](from: Boolean => A): StringCodec[A] =
      booleanCodec(from, _.value)
    private def booleanCodec[A](from: Boolean => A, to: A => Boolean): StringCodec[A] =
      JsonCodec[Boolean].transform(from, to)

    // list properties
    implicit val description: StringCodec[Description] =
      JsonCodec[V2.StringLiteralV2].transformOrFail(Description.from, _.value)
    implicit val labels: StringCodec[Labels] =
      JsonCodec[Seq[V2.StringLiteralV2]].transformOrFail(Labels.from, _.value)

    // maintenance
    implicit val assetId: StringCodec[AssetId] = stringCodec(AssetId.from, _.value)

    // project
    implicit val keyword: StringCodec[Keyword]                         = stringCodec(Keyword.from)
    implicit val logo: StringCodec[Logo]                               = stringCodec(Logo.from)
    implicit val longname: StringCodec[Longname]                       = stringCodec(Longname.from)
    implicit val projectIri: StringCodec[ProjectIri]                   = stringCodec(ProjectIri.from)
    implicit val restrictedViewSize: StringCodec[RestrictedViewSize]   = stringCodec(RestrictedViewSize.from)
    implicit val selfJoin: StringCodec[SelfJoin]                       = booleanCodec(SelfJoin.from)
    implicit val shortcode: StringCodec[Shortcode]                     = stringCodec(Shortcode.from)
    implicit val shortname: StringCodec[Shortname]                     = stringCodec(Shortname.from)
    implicit val sparqlEncodedString: StringCodec[SparqlEncodedString] = stringCodec(SparqlEncodedString.from)
    implicit val status: StringCodec[Status]                           = booleanCodec(Status.from)
  }
}
