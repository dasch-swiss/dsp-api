/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.EndpointInput.PathCapture
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody
import zio.ZLayer
import zio.json.JsonCodec

import dsp.valueobjects.Iri
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetResponseADM
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.admin.api.FilesPathVar.filename
import org.knora.webapi.slice.admin.api.Foo.SparqlEncodedString
import org.knora.webapi.slice.common.api.BaseEndpoints

object FilesPathVar {

  val filename: PathCapture[SparqlEncodedString] = path[SparqlEncodedString]("filename")
}

object Foo {

  final case class SparqlEncodedString private (value: String) extends AnyVal
  object SparqlEncodedString {

    implicit val codec: JsonCodec[SparqlEncodedString] =
      JsonCodec[String].transformOrFail(SparqlEncodedString.from, _.value)

    implicit val tapirCodec: Codec[String, SparqlEncodedString, CodecFormat.TextPlain] =
      Codec.string.mapEither(SparqlEncodedString.from)(_.value)

    def unsafeFrom(str: String): SparqlEncodedString =
      from(str).fold(e => throw new IllegalArgumentException(e), identity)

    def from(str: String): Either[String, SparqlEncodedString] =
      Iri
        .toSparqlEncodedString(str)
        .map(SparqlEncodedString.apply)
        .toRight(s"May not be empty or contain a line break: '$str'")
  }
}

final case class FilesEndpoints(base: BaseEndpoints) {
  import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderResponseADMJsonProtocol.*

  val getAdminFilesShortcodeFileIri = base.withUserEndpoint.get
    .in("admin" / "files" / projectShortcode / filename)
    .out(jsonBody[SipiFileInfoGetResponseADM])
    .description("Returns the file IRI for a given shortcode and filename.")

  val endpoints: Seq[AnyEndpoint] = Seq(getAdminFilesShortcodeFileIri.endpoint)
}

object FilesEndpoints {
  val layer = ZLayer.derive[FilesEndpoints]
}
