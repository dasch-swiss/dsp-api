/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import javax.imageio.ImageIO

/**
 * A route used for faking the image server.
 */
final case class AssetsRouteV1()(
  private implicit val runtime: Runtime[Any]
) {

  /**
   * Returns the route.
   */
  def makeRoute: Route =
    path("v1" / "assets" / Remaining) { assetId =>
      get { requestContext =>
        requestContext.complete {
          val (width, height, text) = assetId match {
            case string if string.contains("big".toCharArray) => (1024, 1024, assetId)
            case _                                            => (16, 16, assetId)
          }

          val dummyImage = if (text.contains("http://rdfh.ch/0a077e5a93bf".toCharArray)) {
            // calling this should get me here: http://localhost:3333/v1/assets/http%3A%2F%2Frdfh.ch%2F0a077e5a93bf
            val tmpImage = ImageIO.read(Paths.get("_assets/4KUN_7_000169.png").toFile)
            tmpImage
          } else {
            /* make dummy images with the image name as content */
            val tmpImage    = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g: Graphics = tmpImage.getGraphics
            // g.setColor(new Color(0,0,0)) //background color
            g.setColor(new Color(255, 125, 65)) // background color
            g.fillRect(0, 0, width, height)
            g.setColor(new Color(0, 0, 0)) // foreground color
            g.setFont(g.getFont.deriveFont(Font.BOLD, 8f))
            g.drawString(text, 0, height / 2)
            g.dispose()
            tmpImage
          }

          val baos: ByteArrayOutputStream = new ByteArrayOutputStream()
          ImageIO.write(dummyImage, "PNG", baos)
          baos.flush()

          val byteArr: Array[Byte] = baos.toByteArray
          baos.close()

          HttpResponse(entity = HttpEntity(MediaTypes.`image/png`, byteArr))
        }
      }
    }
}
