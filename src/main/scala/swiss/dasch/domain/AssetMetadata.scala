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

sealed trait AssetMetadata {
  def internalMimeType: Option[MimeType]
  def originalMimeType: Option[MimeType]
}

object AssetMetadata {
  extension (m: AssetMetadata) {
    def dimensionsOpt: Option[Dimensions] = m match {
      case mi: MovingImageMetadata => Some(mi.dimensions)
      case si: StillImageMetadata  => Some(si.dimensions)
      case _                       => None
    }

    def durationOpt: Option[DurationSecs] = m match {
      case mi: MovingImageMetadata => Some(mi.duration)
      case _                       => None
    }

    def fpsOpt: Option[Fps] = m match {
      case mi: MovingImageMetadata => Some(mi.fps)
      case _                       => None
    }
  }
}

final case class StillImageMetadata(
  dimensions: Dimensions,
  internalMimeType: Option[MimeType],
  originalMimeType: Option[MimeType]
) extends AssetMetadata

final case class MovingImageMetadata(
  dimensions: Dimensions,
  duration: DurationSecs,
  fps: Fps,
  internalMimeType: Option[MimeType],
  originalMimeType: Option[MimeType]
) extends AssetMetadata

final case class OtherMetadata(internalMimeType: Option[MimeType], originalMimeType: Option[MimeType])
    extends AssetMetadata

type DurationSecs = Double Refined Positive
object DurationSecs {
  def unsafeFrom(value: Double): DurationSecs =
    DurationSecs.from(value).fold(msg => throw new IllegalArgumentException(msg), identity)
  def from(value: Double): Either[String, DurationSecs] = refineV[Positive](value)
}

type Fps = Double Refined Positive
object Fps {
  def unsafeFrom(value: Double): Fps =
    Fps.from(value).fold(msg => throw new IllegalArgumentException(msg), identity)
  def from(value: Double): Either[String, Fps] = refineV[Positive](value)
}

final case class Dimensions(width: Int Refined Positive, height: Int Refined Positive)
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
