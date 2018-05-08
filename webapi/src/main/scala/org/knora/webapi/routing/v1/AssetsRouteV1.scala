/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.v1

import java.awt.image.BufferedImage
import java.awt.{Color, Font, Graphics}
import java.io.{ByteArrayOutputStream, File}

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import javax.imageio.ImageIO
import org.knora.webapi.SettingsImpl
import org.knora.webapi.routing.Authenticator

/**
  * A route used for faking the image server.
  */
object AssetsRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout

        path("v1" / "assets" / Remaining) { assetId =>
            get {
                requestContext => {
                    requestContext.complete {
                        log.debug(s"got request: ${requestContext.toString}")

                        val (width, height, text) = assetId match {
                            case string if string.contains("big".toCharArray) => (1024, 1024, assetId)
                            case _ => (16, 16, assetId)
                        }

                        val dummyImage = if (text.contains("http://rdfh.ch/0a077e5a93bf".toCharArray)) {
                            //calling this should get me here: http://localhost:3333/v1/assets/http%3A%2F%2Frdfh.ch%2F0a077e5a93bf
                            val tmpImage = ImageIO.read(new File("_assets/4KUN_7_000169.png"))
                            tmpImage
                        } else {
                            /* make dummy images with the image name as content */
                            val tmpImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                            val g: Graphics = tmpImage.getGraphics
                            //g.setColor(new Color(0,0,0)) //background color
                            g.setColor(new Color(255, 125, 65)) //background color
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
    }
}