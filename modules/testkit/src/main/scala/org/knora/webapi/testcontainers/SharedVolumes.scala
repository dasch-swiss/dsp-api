/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import zio.Scope
import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.FileNotFoundException
import java.nio.file.FileSystems
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute
import java.util.Collections

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object SharedVolumes {

  type Volumes = Images & Temp

  final case class Images private (hostPath: String) extends AnyVal

  object Images {

    private val rwPermissions = asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"))

    val layer: ULayer[Images] =
      ZLayer scoped {
        val tmp = Path(Option(System.getenv("RUNNER_TEMP")).getOrElse(System.getProperty("java.io.tmpdir")))
        for {
          tmpPath <- Files.createTempDirectoryScoped(tmp, None, Seq(rwPermissions))
          _       <- createAssets(tmpPath).tap(n => ZIO.logInfo(s"Created $n assets")).logError
          absDir  <- tmpPath.toAbsolutePath.map(_.toString())
        } yield Images(absDir)
      }.orDie

    private def createAssets(assetDir: Path): ZIO[Scope, Throwable, Int] = {
      val shortcode = Shortcode.unsafeFrom("0001")
      resourceDirPath("/sipi/testfiles").flatMap { testfilesDir =>
        Files
          .walk(testfilesDir, 1)
          .filterZIO(p => Files.isRegularFile(p) && Files.isHidden(p).negate)
          .mapZIO(p => copyFileToAssetFolder(assetDir, p, shortcode).as(1))
          .runSum
      }
    }

    /**
     * `/sipi/testfiles` is a classpath resource *directory*. Under sbt it resolves to a real
     * filesystem directory (loose class/resource output), so `Path(uri)` + `Files.walk` works
     * directly. Under Bazel, test resources are packaged into a resource jar, so the resource URI
     * is a `jar:` one that the default NIO filesystem provider can't `Files.walk` into (same
     * failure mode the Phase 1 `ShaclValidatorSpec` fix hit for a single file). Open a `jar:`
     * filesystem for that case - closed with the enclosing scope - and fall back to the plain
     * `file:` path otherwise.
     *
     * `Images.layer` is built both from inside `TestContainerLayers`' `Scope.global`-memoized
     * singleton (never released) and independently by any other consumer (e.g. `SipiIT`, which
     * builds its own container stack). The JDK's jar filesystem provider only allows one open
     * filesystem per URI at a time (`FileSystems.newFileSystem` throws
     * `FileSystemAlreadyExistsException` on a second open) - if the memoized instance opens this
     * jar first and never closes it, a later independent consumer opening the *same* jar would die
     * with that exception. Attach to the already-open filesystem instead of failing, and only
     * close what we ourselves opened - the fs we merely attached to is owned by whichever scope
     * opened it first.
     */
    private final case class JarFs(fs: java.nio.file.FileSystem, owned: Boolean)

    private def resourceDirPath(resource: String): ZIO[Scope, Throwable, Path] = {
      val uri = getClass.getResource(resource).toURI
      if (uri.getScheme == "jar")
        ZIO
          .acquireRelease(openJarFileSystem(uri))(releaseJarFileSystem)
          .map(jarFs => Path.fromJava(jarFs.fs.getPath(resource)))
      else ZIO.succeed(Path(uri))
    }

    private def openJarFileSystem(uri: java.net.URI): ZIO[Any, Throwable, JarFs] =
      ZIO
        .attemptBlocking(FileSystems.newFileSystem(uri, Collections.emptyMap[String, Any]()))
        .map(JarFs(_, owned = true))
        .catchSome { case _: java.nio.file.FileSystemAlreadyExistsException =>
          ZIO.attemptBlocking(JarFs(FileSystems.getFileSystem(uri), owned = false))
        }

    private def releaseJarFileSystem(jarFs: JarFs): zio.UIO[Unit] =
      ZIO.attemptBlocking(jarFs.fs.close()).orDie.when(jarFs.owned).unit

    private def copyFileToAssetFolder(
      assetDir: Path,
      source: Path,
      shortcode: Shortcode,
    ) =
      ZIO.fail(new FileNotFoundException(s"File not found $source")).whenZIO(Files.notExists(source)).logError *> {
        val filename  = source.filename.toString()
        val seg01     = filename.substring(0, 2).toLowerCase()
        val seg02     = filename.substring(2, 4).toLowerCase()
        val targetDir = assetDir / shortcode.value / seg01 / seg02
        Files.createDirectories(targetDir, rwPermissions).logError *>
          Files.copy(source, targetDir / filename, StandardCopyOption.REPLACE_EXISTING)
      }
  }

  final case class Temp private (hostPath: String) extends AnyVal
  object Temp {
    val layer: ULayer[Temp] = ZLayer.succeed(Temp(System.getProperty("java.io.tmpdir")))
  }

  val layer: ULayer[Images & Temp] = Images.layer ++ Temp.layer
}
