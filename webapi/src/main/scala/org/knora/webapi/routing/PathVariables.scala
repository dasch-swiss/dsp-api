/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.*

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier

object PathVariables {

  val projectIri: EndpointInput.PathCapture[IriIdentifier] =
    path[IriIdentifier]
      .name("projectIri")
      .description("The IRI of a project. Must be URL-encoded.")
      .example(IriIdentifier.fromString("http://rdfh.ch/projects/0001").fold(e => throw e.head, identity))

  private val projectShortcodeCodec: Codec[String, ShortcodeIdentifier, TextPlain] =
    Codec.string.mapDecode(str =>
      ShortcodeIdentifier
        .fromString(str)
        .fold(err => DecodeResult.Error(str, BadRequestException(err.head.msg)), DecodeResult.Value(_))
    )(_.value.value)

  val projectShortcode: EndpointInput.PathCapture[ShortcodeIdentifier] =
    path[ShortcodeIdentifier](projectShortcodeCodec)
      .name("projectShortcode")
      .description("The shortcode of a project. Must be a 4 digit hexadecimal String.")
      .example(ShortcodeIdentifier.fromString("0001").fold(e => throw e.head, identity))

  private val projectShortnameCodec: Codec[String, ShortnameIdentifier, TextPlain] =
    Codec.string.mapDecode(str =>
      ShortnameIdentifier
        .fromString(str)
        .fold(err => DecodeResult.Error(str, BadRequestException(err.head.msg)), DecodeResult.Value(_))
    )(_.value.value)

  val projectShortname: EndpointInput.PathCapture[ShortnameIdentifier] =
    path[ShortnameIdentifier](projectShortnameCodec)
      .name("projectShortname")
      .description("The shortname of a project.")
      .example(ShortnameIdentifier.fromString("someShortname").fold(e => throw e.head, identity))
}
