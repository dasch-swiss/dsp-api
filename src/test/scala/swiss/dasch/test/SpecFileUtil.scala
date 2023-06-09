package swiss.dasch.test

import zio.nio.file.Path

object SpecFileUtil {
  def pathFromResource(resource: String): Path = Path(getClass.getResource(resource).getPath)
}
