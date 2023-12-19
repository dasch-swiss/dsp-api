/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import zio.json.interop.refined.{decodeRefined, encodeRefined}
import zio.json.{DeriveJsonCodec, JsonCodec}

sealed trait AssetMetadata

type StillImageMetadata = Dimensions
final case class Dimensions(width: Int Refined Positive, height: Int Refined Positive) extends AssetMetadata
object Dimensions {
  given codec: JsonCodec[Dimensions] = DeriveJsonCodec.gen[Dimensions]

  def unsafeFrom(width: Int, height: Int): Dimensions =
    Dimensions.from(width, height).fold(msg => throw new IllegalArgumentException(msg), identity)
  def from(width: Int, height: Int): Either[String, Dimensions] =
    for {
      w <- refineV[Positive](width)
      h <- refineV[Positive](height)
    } yield Dimensions(w, h)
}

final case class MovingImageMetadata(dimensions: Dimensions, duration: Double, fps: Double) extends AssetMetadata

case object EmptyMetadata extends AssetMetadata
