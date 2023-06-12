package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{ DeriveJsonCodec, DeriveJsonDecoder, DeriveJsonEncoder, JsonCodec, JsonDecoder, JsonEncoder }
import zio.nio.file.{ Files, Path }

import java.io.IOException

opaque type ProjectShortcode = String Refined MatchesRegex["""^\p{XDigit}{4,4}$"""]
type IiifPrefix              = ProjectShortcode

object ProjectShortcode {
  def make(shortcode: String): Either[String, ProjectShortcode] = refineV(shortcode.toUpperCase)
}

final case class DotInfoFileContent(
    internalFilename: String,
    originalInternalFilename: String,
    originalFilename: String,
    checksumOriginal: String,
    checksumDerivative: String,
  )

object DotInfoFileContent {
  implicit val codec: JsonCodec[DotInfoFileContent] = DeriveJsonCodec.gen[DotInfoFileContent]
}

trait AssetService  {
  def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]]
  def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]]
  def zipProject(shortcode: ProjectShortcode): Task[Option[Path]]
}
object AssetService {
  def listAllProjects(): ZIO[AssetService, IOException, Chunk[ProjectShortcode]] =
    ZIO.serviceWithZIO[AssetService](_.listAllProjects())

  def findProject(shortcode: ProjectShortcode): ZIO[AssetService, IOException, Option[Path]] =
    ZIO.serviceWithZIO[AssetService](_.findProject(shortcode))

  def zipProject(shortcode: ProjectShortcode): ZIO[AssetService, Throwable, Option[Path]] =
    ZIO.serviceWithZIO[AssetService](_.zipProject(shortcode))
}

final case class AssetServiceLive(config: StorageConfig) extends AssetService {

  private val existingProjectDirectories = Files.list(config.assetPath).filterZIO(Files.isDirectory(_))
  private val existingTempFiles          = Files.list(config.tempPath).filterZIO(Files.isRegularFile(_))

  override def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]] =
    existingProjectDirectories
      .filterZIO(directoryTreeContainsNonHiddenRegularFiles)
      .map(_.filename.toString)
      .runCollect
      .map(_.sorted.flatMap(ProjectShortcode.make(_).toOption))

  private def directoryTreeContainsNonHiddenRegularFiles(path: Path) =
    Files.walk(path).findZIO(it => Files.isRegularFile(it) && Files.isHidden(it).map(!_)).runCollect.map(_.nonEmpty)

  override def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]] =
    existingProjectDirectories.filter(_.filename.toString == shortcode.toString).runHead

  override def zipProject(shortcode: ProjectShortcode): Task[Option[Path]] =
    findProject(shortcode).flatMap(_.map(zipProjectPath(_, shortcode)).getOrElse(ZIO.none))

  private def zipProjectPath(projectPath: Path, shortcode: ProjectShortcode) = {
    val targetFolder = config.tempPath / "zipped"
    ZipUtility.zipFolder(projectPath, targetFolder).map(Some(_))
  }
}

object AssetServiceLive {
  val layer: URLayer[StorageConfig, AssetService] = ZLayer.fromFunction(AssetServiceLive.apply _)
}
