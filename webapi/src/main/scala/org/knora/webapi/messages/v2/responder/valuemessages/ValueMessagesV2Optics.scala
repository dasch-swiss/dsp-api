/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import monocle.*
import monocle.Optional
import monocle.macros.*

import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.KnoraProject.License

object ValueMessagesV2Optics {

  object FileValueV2Optics {

    val copyrightAttributionOption: Lens[FileValueV2, Option[CopyrightAttribution]] =
      GenLens[FileValueV2](_.copyrightAttribution)

    val licenseOption: Lens[FileValueV2, Option[License]] =
      GenLens[FileValueV2](_.license)

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
    val copyRightAttributionOption: Lens[FileValueContentV2, Option[CopyrightAttribution]] =
      fileValueV2.andThen(FileValueV2Optics.copyrightAttributionOption)
    val licenseOption: Lens[FileValueContentV2, Option[License]] =
      fileValueV2.andThen(FileValueV2Optics.licenseOption)
  }
}
