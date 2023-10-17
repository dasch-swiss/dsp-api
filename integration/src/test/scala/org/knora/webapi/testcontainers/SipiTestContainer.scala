/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.knora.webapi.http.version.BuildInfo
import org.testcontainers.containers.{BindMode, GenericContainer}
import org.testcontainers.utility.{DockerImageName, MountableFile}
import zio.http.URL
import zio.nio.file.Path
import zio.{Task, UIO, URIO, ZIO, ZLayer, http}

import java.net.{Inet6Address, InetAddress}
import java.nio.file.Paths

final case class SipiTestContainer(container: GenericContainer[Nothing]) {
  def copyFileToImageFolderInContainer(prefix: String, filename: String): Task[Unit] = {
    val seg01  = filename.substring(0, 2).toLowerCase()
    val seg02  = filename.substring(2, 4).toLowerCase()
    val target = Path(s"/sipi/images/$prefix/$seg01/$seg02/$filename")
    copyTestFileToContainer(filename, target)
  }

  def copyTestFileToContainer(file: String, target: Path): Task[Unit] = {
    val resourceName  = s"sipi/testfiles/$file"
    val mountableFile = MountableFile.forClasspathResource(resourceName, 777)
    ZIO.attemptBlockingIO(container.copyFileToContainer(mountableFile, target.toFile.toString)) <* ZIO.logInfo(
      s"copied $resourceName to $target"
    )
  }

  val port: Int = container.getFirstMappedPort

  val host: String = SipiTestContainer.localHostAddress

  val sipiBaseUrl: URL = {
    val urlString = s"http://$host:$port"
    println(s"SIPI URL String: $urlString")
    val url = URL.decode(urlString).getOrElse(throw new IllegalStateException(s"Invalid URL $urlString"))
    println(s"SIPI URL: $url")
    url
  }
}

object SipiTestContainer {

  val localHostAddress: String = {
    val localhost = InetAddress.getLocalHost
    if (localhost.isInstanceOf[Inet6Address]) {
      s"[${localhost.getHostAddress}]"
    } else {
      localhost.getHostAddress
    }
  }

  def port: ZIO[SipiTestContainer, Nothing, Int]                  = ZIO.serviceWith[SipiTestContainer](_.port)
  def host: ZIO[SipiTestContainer, Nothing, String]               = ZIO.serviceWith[SipiTestContainer](_.host)
  def portAndHost: ZIO[SipiTestContainer, Nothing, (Int, String)] = port <*> host

  def resolveUrl(path: http.Path): URIO[SipiTestContainer, URL] =
    ZIO.serviceWith[SipiTestContainer](_.sipiBaseUrl.withPath(path))

  def copyFileToImageFolderInContainer(prefix: String, filename: String): ZIO[SipiTestContainer, Throwable, Unit] =
    ZIO.serviceWithZIO[SipiTestContainer](_.copyFileToImageFolderInContainer(prefix, filename))

  def copyTestFileToContainer(file: String, target: Path): ZIO[SipiTestContainer, Throwable, Unit] =
    ZIO.serviceWithZIO[SipiTestContainer](_.copyTestFileToContainer(file, target))

  /**
   * A functional effect that initiates a Sipi Testcontainer
   */
  val acquire: UIO[GenericContainer[Nothing]] = ZIO.attemptBlocking {
    // Uncomment the following line to use the latest version of Sipi for local development:
    // val sipiImageName: DockerImageName = DockerImageName.parse(s"daschswiss/knora-sipi:latest")
    val sipiImageName: DockerImageName = DockerImageName.parse(s"daschswiss/knora-sipi:${BuildInfo.version}")
    val sipiContainer                  = new GenericContainer(sipiImageName)
    sipiContainer.withExposedPorts(1024)
    sipiContainer.withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST", "0.0.0.0")
    sipiContainer.withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT", "3333")
    sipiContainer.withEnv("SIPI_EXTERNAL_PROTOCOL", "http")
    sipiContainer.withEnv("SIPI_EXTERNAL_HOSTNAME", "0.0.0.0")
    sipiContainer.withEnv("SIPI_EXTERNAL_PORT", "1024")
    sipiContainer.withEnv("SIPI_WEBAPI_HOSTNAME", SipiTestContainer.localHostAddress)
    sipiContainer.withEnv("SIPI_WEBAPI_PORT", "3333")
    sipiContainer.withEnv("CLEAN_TMP_DIR_USER", "clean_tmp_dir_user")
    sipiContainer.withEnv("CLEAN_TMP_DIR_PW", "clean_tmp_dir_pw")

    sipiContainer.withCommand("--config=/sipi/config/sipi.docker-config.lua")

    sipiContainer.withClasspathResourceMapping(
      "/sipi.docker-config.lua",
      "/sipi/config/sipi.docker-config.lua",
      BindMode.READ_ONLY
    )

    val incunabulaImageDirPath =
      Paths.get("..", "sipi/images/0001/b1/d0/B1D0OkEgfFp-Cew2Seur7Wi.jp2")
    sipiContainer.withFileSystemBind(
      incunabulaImageDirPath.toString,
      "/sipi/images/0001/b1/d0/B1D0OkEgfFp-Cew2Seur7Wi.jp2",
      BindMode.READ_ONLY
    )
    sipiContainer.withLogConsumer(frame => print("SIPI:" + frame.getUtf8String))

    sipiContainer.start()

    // Create '/sipi/images/tmp' folder inside running container
    sipiContainer.execInContainer("mkdir", "/sipi/images/tmp")
    sipiContainer.execInContainer("chmod", "777", "/sipi/images/tmp")

    sipiContainer
  }.orDie.zipLeft(ZIO.logInfo(">>> Acquire Sipi TestContainer <<<"))

  def release(container: GenericContainer[Nothing]): UIO[Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie.zipLeft(ZIO.logInfo(">>> Release Sipi TestContainer <<<"))

  val layer: ZLayer[Any, Nothing, SipiTestContainer] =
    ZLayer.scoped(ZIO.acquireRelease(acquire)(release).map(SipiTestContainer(_)))
}
