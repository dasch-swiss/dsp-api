package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecFileUtil.pathFromResource
import zio.nio.file.{ Files, Path }
import zio.{ Chunk, Scope, ZIO, ZLayer }
import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertCompletes, assertTrue }

object AssetServiceSpec extends ZIOSpecDefault {

  private val configLayer = ZLayer.scoped {
    for {
      tmpDir <- Files.createTempDirectoryScoped(None, List.empty)
    } yield StorageConfig(
      assetDir = pathFromResource("/test-folder-structure").toFile.getAbsolutePath,
      tempDir = tmpDir.toFile.toString,
    )
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetServiceSpec")(
      test("should list all projects which contain assets in the asset directory") {
        for {
          projects <- AssetService.listAllProjects()
        } yield assertTrue(projects == Chunk(existingProject))
      },
      suite("findProject path")(
        test("should find existing projects which contain at least one non hidden regular file") {
          for {
            project <- AssetService.findProject(existingProject)
          } yield assertTrue(project.isDefined)
        },
        test("should not find not existing projects") {
          for {
            project <- AssetService.findProject(nonExistentProject)
          } yield assertTrue(project.isEmpty)
        },
      ),
      suite("zipping a project")(
        test("given it does not exist, should return None") {
          for {
            zip <- AssetService.zipProject(nonExistentProject)
          } yield assertTrue(zip.isEmpty)
        },
        test("given it does exists, should zip and return file path") {
          for {
            tempDir <- ZIO.serviceWith[StorageConfig](_.tempPath)
            zip     <- AssetService.zipProject(existingProject)
          } yield assertTrue(zip.contains(tempDir / "zipped" / "0001.zip"))
        },
      ),
    ).provide(AssetServiceLive.layer, configLayer)
}
