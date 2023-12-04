/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.*
import zio.nio.file.Path

enum FileTypes {
  case ImageFileType
  case VideoFileType
  case OtherFileType
}

object FileTypes {
  def fromPath(path: Path): Task[FileTypes] =
    for {
      isImage <- FileFilters.isImage(path)
      isVideo <- FileFilters.isVideo(path)
      isOther <- FileFilters.isOther(path)
      fileType <- (isImage, isVideo, isOther) match {
                    case (true, _, _) => ZIO.succeed(ImageFileType)
                    case (_, true, _) => ZIO.succeed(VideoFileType)
                    case (_, _, true) => ZIO.succeed(OtherFileType)
                    case _            => ZIO.fail(new NotImplementedError(s"The type of file $path is not supported"))
                  }
    } yield fileType
}
