package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.SpecFileUtil.pathFromResource
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

  private val nonExistentProject = ProjectShortcode.unsafeFrom("0042")
  private val existingProject    = ProjectShortcode.unsafeFrom("0001")
  private val emptyProject       = ProjectShortcode.unsafeFrom("0002")

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetServiceSpec")(
      test("should list all projects which contain assets in the asset directory") {
        for {
          projects <- AssetService.listAllProjects()
        } yield assertTrue(projects == Chunk(existingProject))
      },
      suite("findProject path")(
        test("should find existing project") {
          for {
            project <- AssetService.findProject(existingProject)
          } yield assertTrue(project.isDefined)
        },
        test("should not find not existing projects which contain non hidden regular files") {
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

object SpecFileUtil {
  def pathFromResource(resource: String): Path = Path(getClass.getResource(resource).getPath)
}
