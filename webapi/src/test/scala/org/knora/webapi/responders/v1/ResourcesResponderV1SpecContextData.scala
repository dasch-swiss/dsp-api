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

package org.knora.webapi.responders.v1

import java.io.File

import akka.actor.ActorSystem
import org.knora.webapi.Settings
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.util.FileUtil
import spray.json.JsonParser

object ResourcesResponderV1SpecContextData {

    implicit lazy val system = ActorSystem("webapi")

    val settings = Settings(system)

    /*

    The expected Incunabula book context response is so large if it is included here in the source code, the Scala
    compiler runs out of memory compiling it in some development environments. So we store a JSON representation of it
    in a file, and load that at runtime.

    */

    // The file contains a placeholder, RESTYPE_ICONSRC, which we replace here with the correct value.
    private val expectedBookResourceContextResponseStr = FileUtil.readTextFile(new File("src/test/resources/test-data/expectedBookContextResponse.json")).replace(
        "RESTYPE_ICONSRC",
        settings.salsahBaseUrl + settings.salsahProjectIconsBasePath + "incunabula/book.gif"
    )

    val expectedBookResourceContextResponse = JsonParser(expectedBookResourceContextResponseStr)

    val expectedPageResourceContextResponse = ResourceContextResponseV1(
        resource_context = ResourceContextV1(
            parent_resinfo = Some(ResourceInfoV1(
                firstproperty = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                value_of = 0,
                lastmod = "0000-00-00 00:00:00",
                resclass_has_location = false,
                resclass_name = "object",
                locdata = None,
                locations = None,
                preview = None,
                restype_iconsrc = Some(settings.salsahBaseUrl + settings.salsahProjectIconsBasePath + "incunabula/book.gif"),
                restype_description = Some("Diese Resource-Klasse beschreibt ein Buch"),
                restype_label = Some("Buch"),
                restype_name = Some("http://www.knora.org/ontology/incunabula#book"),
                restype_id = "http://www.knora.org/ontology/incunabula#book",
                person_id = "http://rdfh.ch/users/91e19f1e01",
                project_id = "http://rdfh.ch/projects/77275339"
            )),
            parent_res_id = Some("http://data.knora.org/c5058f3a"),
            resinfo = Some(ResourceInfoV1(
                firstproperty = Some("a1r, Titelblatt"),
                value_of = 0,
                lastmod = "0000-00-00 00:00:00",
                resclass_has_location = true,
                resclass_name = "object",
                locdata = Some(LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg",
                    ny = Some(3505),
                    nx = Some(2613),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                )),
                locations = Some(Vector(
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jpg/full/full/0/default.jpg",
                        ny = Some(128),
                        nx = Some(95),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/82,110/0/default.jpg",
                        ny = Some(110),
                        nx = Some(82),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/163,219/0/default.jpg",
                        ny = Some(219),
                        nx = Some(163),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/327,438/0/default.jpg",
                        ny = Some(438),
                        nx = Some(327),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/653,876/0/default.jpg",
                        ny = Some(876),
                        nx = Some(653),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/1307,1753/0/default.jpg",
                        ny = Some(1753),
                        nx = Some(1307),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg",
                        ny = Some(3505),
                        nx = Some(2613),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    )
                )),
                preview = Some(LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jpg/full/full/0/default.jpg",
                    ny = Some(128),
                    nx = Some(95),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG"
                )),
                restype_iconsrc = Some(settings.salsahBaseUrl + settings.salsahProjectIconsBasePath + "incunabula/page.gif"),
                restype_description = Some("Eine Seite ist ein Teil eines Buchs"),
                restype_label = Some("Seite"),
                restype_name = Some("http://www.knora.org/ontology/incunabula#page"),
                restype_id = "http://www.knora.org/ontology/incunabula#page",
                person_id = "http://rdfh.ch/users/91e19f1e01",
                project_id = "http://rdfh.ch/projects/77275339"
            )),
            canonical_res_id = "http://data.knora.org/8a0b1e75",
            context = ResourceContextCodeV1.RESOURCE_CONTEXT_IS_PARTOF,
            region = None,
            firstprop = None,
            preview = None,
            resclass_name = None,
            res_id = None
        )
    )
}