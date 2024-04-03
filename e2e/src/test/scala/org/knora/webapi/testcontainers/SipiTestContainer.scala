/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import zio._
import zio.http
import zio.http.URL

import java.net.Inet6Address
import java.net.InetAddress

import org.knora.webapi.http.version.BuildInfo

final class SipiTestContainer
    extends GenericContainer[SipiTestContainer](s"daschswiss/knora-sipi:${BuildInfo.version}") {

  def sipiBaseUrl: URL = {
    val urlString = s"http://${SipiTestContainer.localHostAddress}:$getFirstMappedPort"
    val url       = URL.decode(urlString).getOrElse(throw new IllegalStateException(s"Invalid URL $urlString"))
    logger.info(s"SIPI URL: ${url.encode}")
    url
  }
}

object SipiTestContainer {

  private val imagesDir = "/sipi/images"

  val localHostAddress: String = {
    val localhost = InetAddress.getLocalHost
    if (localhost.isInstanceOf[Inet6Address]) {
      s"[${localhost.getHostAddress}]"
    } else {
      localhost.getHostAddress
    }
  }

  def portAndHost: ZIO[SipiTestContainer, Nothing, (Int, String)] =
    ZIO.serviceWith[SipiTestContainer](c => (c.getFirstMappedPort, localHostAddress))

  def resolveUrl(path: http.Path): URIO[SipiTestContainer, URL] =
    ZIO.serviceWith[SipiTestContainer](_.sipiBaseUrl.path(path))

  def make(imagesVolume: SharedVolumes.Images): SipiTestContainer =
    new SipiTestContainer()
      .withExposedPorts(1024)
      .withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST", "0.0.0.0")
      .withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT", "3333")
      .withEnv("SIPI_EXTERNAL_PROTOCOL", "http")
      .withEnv("SIPI_EXTERNAL_HOSTNAME", "0.0.0.0")
      .withEnv("SIPI_EXTERNAL_PORT", "1024")
      .withEnv("SIPI_WEBAPI_HOSTNAME", SipiTestContainer.localHostAddress)
      .withEnv("SIPI_WEBAPI_PORT", "3333")
      .withEnv("CLEAN_TMP_DIR_USER", "clean_tmp_dir_user")
      .withEnv("CLEAN_TMP_DIR_PW", "clean_tmp_dir_pw")
      .withCommand("--config=/sipi/config/sipi.docker-config.lua")
      .withClasspathResourceMapping(
        "/sipi.docker-config.lua",
        "/sipi/config/sipi.docker-config.lua",
        BindMode.READ_ONLY,
      )
      .withFileSystemBind(imagesVolume.hostPath, imagesDir, BindMode.READ_WRITE)
      .withLogConsumer(frame => print("SIPI:" + frame.getUtf8String))

  private val initSipi = ZLayer.fromZIO(
    for {
      container <- ZIO.service[SipiTestContainer]
      _ <- ZIO.attemptBlocking {
             container.execInContainer("mkdir", s"$imagesDir/tmp")
             container.execInContainer("chmod", "777", s"$imagesDir/tmp")
           }

    } yield container,
  )

  val layer: URLayer[SharedVolumes.Images, SipiTestContainer] = {
    val container =
      ZLayer.scoped(ZIO.service[SharedVolumes.Images].flatMap(it => ZioTestContainers.toZio(make(it))))
    (container >>> initSipi).orDie
  }
}
