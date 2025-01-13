/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import monocle.*
import monocle.macros.*

import org.knora.webapi.slice.admin.domain.model.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.LicenseText
import org.knora.webapi.slice.admin.domain.model.LicenseUri

object ValueMessagesV2Optics {

  object FileValueV2Optics {

    val copyrightAttributionOption: Lens[FileValueV2, Option[CopyrightAttribution]] =
      GenLens[FileValueV2](_.copyrightAttribution)

    val licenseTextOption: Lens[FileValueV2, Option[LicenseText]] = GenLens[FileValueV2](_.licenseText)

    val licenseUriOption: Lens[FileValueV2, Option[LicenseUri]] = GenLens[FileValueV2](_.licenseUri)

  }

  object FileValueContentV2Optics {

    val fileValueV2: Lens[FileValueContentV2, FileValueV2] =
      Lens[FileValueContentV2, FileValueV2](_.fileValue)(fv => {
        case vc: MovingImageFileValueContentV2        => vc.copy(fileValue = fv)
        case vc: StillImageFileValueContentV2         => vc.copy(fileValue = fv)
        case vc: AudioFileValueContentV2              => vc.copy(fileValue = fv)
        case vc: DocumentFileValueContentV2           => vc.copy(fileValue = fv)
        case vc: StillImageExternalFileValueContentV2 => vc.copy(fileValue = fv)
        case vc: ArchiveFileValueContentV2            => vc.copy(fileValue = fv)
        case vc: TextFileValueContentV2               => vc.copy(fileValue = fv)
      })
    val copyrightAttributionOption: Lens[FileValueContentV2, Option[CopyrightAttribution]] =
      fileValueV2.andThen(FileValueV2Optics.copyrightAttributionOption)
    val licenseTextOption: Lens[FileValueContentV2, Option[LicenseText]] =
      fileValueV2.andThen(FileValueV2Optics.licenseTextOption)
    val licenseUriOption: Lens[FileValueContentV2, Option[LicenseUri]] =
      fileValueV2.andThen(FileValueV2Optics.licenseUriOption)
  }
}
