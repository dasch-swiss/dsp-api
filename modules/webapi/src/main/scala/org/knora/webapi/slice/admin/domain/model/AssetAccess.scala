/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraBase

/**
 * What kind of medium an asset is, derived from its file value's RDF class. It names the medium, not where
 * the bytes live or how they are served.
 */
enum MediaKind {
  case RasterStillImage, Vector, MovingImage, Audio, Document, Archive, Text, ThreeD
}

object MediaKind {

  /**
   * Every concrete `knora-base` file value class. `StillImageExternalFileValue` is a raster still image whose
   * bytes are hosted elsewhere and never reach Sipi, so the decision derived from it is never observed.
   */
  private val byFileValueClass: Map[IRI, MediaKind] = Map(
    KnoraBase.StillImageFileValue         -> RasterStillImage,
    KnoraBase.StillImageExternalFileValue -> RasterStillImage,
    KnoraBase.StillImageVectorFileValue   -> Vector,
    KnoraBase.MovingImageFileValue        -> MovingImage,
    KnoraBase.AudioFileValue              -> Audio,
    KnoraBase.DocumentFileValue           -> Document,
    KnoraBase.ArchiveFileValue            -> Archive,
    KnoraBase.TextFileValue               -> Text,
    KnoraBase.DDDFileValue                -> ThreeD,
  )

  val fileValueClasses: Set[IRI] = byFileValueClass.keySet

  /**
   * The class IRI arrives as a string from the triplestore, so the compiler cannot make this total. A class it
   * does not know yields `None` and the caller must fail closed.
   */
  def fromFileValueClass(iri: IRI): Option[MediaKind] = byFileValueClass.get(iri)
}

/** The Original channel. Read by dsp-ingest and by nothing else. */
enum OriginalAccess {
  case Withhold, Grant
}

/** The Derivative channel. Read by Sipi's preflight hook and by nothing else. */
enum DerivativeAccess {

  /** No view permission, or a restriction with nothing to fall back on. */
  case Denied

  /** A raster still image served through the IIIF pipeline under a size cap or a watermark. */
  case Clamped(view: RestrictedView)

  /** Consumable in place, never handed over as a file. How strictly Sipi must enforce that is DEV-7244. */
  case Stream

  /** Full view permission. */
  case Full
}

/**
 * What a caller may receive for an asset, as two independent channels.
 *
 * The constructor is private: `from` is the whole policy, and the invariant "a granted Original implies a full
 * Derivative" is not expressible on the wire, so no caller may assemble a pair field by field.
 */
final case class AssetAccess private (original: OriginalAccess, derivative: DerivativeAccess)

object AssetAccess {

  /**
   * The single total policy function.
   *
   * `stored` is the project's own restricted view setting, `None` meaning it inherits the platform default.
   *
   * An archive under restricted view is denied rather than streamed: it has no in-place consumption and is
   * never transcoded, so serving it would hand over the Original by the other route.
   */
  def from(
    perm: Option[Permission.ObjectAccess],
    media: MediaKind,
    stored: Option[RestrictedView],
  ): AssetAccess =
    perm match {
      case None                                         => AssetAccess(OriginalAccess.Withhold, DerivativeAccess.Denied)
      case Some(Permission.ObjectAccess.RestrictedView) =>
        // Every kind is spelled out rather than caught by a wildcard: adding one to `MediaKind` must break
        // this match rather than silently inherit `Stream`.
        val derivative = media match {
          case MediaKind.RasterStillImage => DerivativeAccess.Clamped(stored.getOrElse(RestrictedView.default))
          case MediaKind.Archive          => DerivativeAccess.Denied
          case MediaKind.Vector | MediaKind.MovingImage | MediaKind.Audio | MediaKind.Document | MediaKind.Text |
              MediaKind.ThreeD =>
            DerivativeAccess.Stream
        }
        AssetAccess(OriginalAccess.Withhold, derivative)
      case Some(_) => AssetAccess(OriginalAccess.Grant, DerivativeAccess.Full)
    }

  /** The decision for an asset whose file value class has no [[MediaKind]]: withhold everything. */
  val failClosed: AssetAccess = AssetAccess(OriginalAccess.Withhold, DerivativeAccess.Denied)

  /**
   * Whether deciding this case reads the project's stored setting. Derived from `from` itself rather than
   * restated, so the caller can skip the project lookup without keeping a second copy of the policy.
   */
  def usesStoredRestrictedView(perm: Option[Permission.ObjectAccess], media: MediaKind): Boolean =
    from(perm, media, None).derivative match {
      case _: DerivativeAccess.Clamped => true
      case _                           => false
    }
}
