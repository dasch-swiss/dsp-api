/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.slice.common.ResourceIri

/**
 * Turns the link targets computed by `TextValueContentV2` into effects. The link-target accessors fail for a value
 * served from its stored XML, so a write path lifts them with these helpers before it changes anything.
 */
object StandoffLinkTargets {

  /**
   * Names the value in the failure of a link-target computation, so that a served value found in the triplestore
   * can be traced. The message carries no text content.
   */
  def withValueIri[A](valueIri: IRI, targets: Either[AssertionException, A]): Either[AssertionException, A] =
    targets.left.map(e => AssertionException(s"Value <$valueIri>: ${e.message}"))

  /**
   * Lifts the link targets of a text value and parses them as resource IRIs.
   * Fails with the [[AssertionException]] of a served value, or with a [[BadRequestException]] for a malformed IRI.
   */
  def resourceIris(targets: Either[AssertionException, Set[IRI]]): IO[Throwable, Set[ResourceIri]] =
    ZIO.fromEither(targets).flatMap(toResourceIris)

  def toResourceIris(iris: Set[IRI]): IO[BadRequestException, Set[ResourceIri]] =
    ZIO.foreach(iris)(iri => ZIO.fromEither(ResourceIri.from(iri))).mapError(BadRequestException.apply)
}
