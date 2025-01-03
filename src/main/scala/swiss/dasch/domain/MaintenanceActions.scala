/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.api.ActionName
import swiss.dasch.domain
import swiss.dasch.domain.AugmentedPath.*
import zio.*
import zio.nio.file
import zio.nio.file.Path

trait MaintenanceActions {
  def updateAssetMetadata(projects: Iterable[ProjectFolder]): Task[Unit]
  def importProjectsToDb(): Task[Unit]
}

final case class MaintenanceActionsLive(
  assetInfoService: AssetInfoService,
  imageService: StillImageService,
  mimeTypeGuesser: MimeTypeGuesser,
  movingImageService: MovingImageService,
  otherFilesService: OtherFilesService,
  projectService: ProjectService,
  sipiClient: SipiClient,
  storageService: StorageService,
) extends MaintenanceActions {

  override def updateAssetMetadata(projects: Iterable[ProjectFolder]): Task[Unit] = {
    def updateSingleAsset(info: AssetInfo): Task[Unit] =
      for {
        assetType <- ZIO
                       .fromOption(SupportedFileType.fromPath(Path(info.originalFilename.value)))
                       .orElseFail(new Exception(s"Could not get asset type from path ${info.originalFilename.value}"))
        newMetadata <- getMetadata(info, assetType)
        _           <- assetInfoService.save(info.copy(metadata = newMetadata))
      } yield ()

    def getMetadata(info: AssetInfo, assetType: SupportedFileType): Task[AssetMetadata] = {
      val original = Original(OrigFile.unsafeFrom(info.original.file), info.originalFilename)
      assetType match {
        case SupportedFileType.StillImage =>
          imageService.extractMetadata(original, JpxDerivativeFile.unsafeFrom(info.derivative.file))

        case SupportedFileType.MovingImage =>
          movingImageService.extractMetadata(original, MovingImageDerivativeFile.unsafeFrom(info.derivative.file))

        case SupportedFileType.Audio | SupportedFileType.OtherFiles =>
          otherFilesService.extractMetadata(original, OtherDerivativeFile.unsafeFrom(info.derivative.file))
      }
    }

    for {
      _ <- ZIO.foreachDiscard(projects) { projectPath =>
             projectService
               .findAssetInfosOfProject(projectPath.shortcode)
               .mapZIOPar(8)(updateSingleAsset(_).logError.ignore)
               .runDrain
           }
      _ <- ZIO.logInfo(s"Finished ${ActionName.UpdateAssetMetadata}")
    } yield ()
  }

  override def importProjectsToDb(): Task[Unit] = for {
    prjFolders <- projectService.listAllProjects()
    _          <- ZIO.foreachDiscard(prjFolders.map(_.shortcode))(projectService.addProjectToDb)
  } yield ()
}

object MaintenanceActionsLive {
  val layer = ZLayer.derive[MaintenanceActionsLive]
}
