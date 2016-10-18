/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e

import java.io.{BufferedInputStream, File, FileInputStream, InputStream}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives.{extractRequestContext => _, provide => _}
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.FutureDirectives.{onSuccess => _}
import akka.http.scaladsl.server.directives.MarshallingDirectives.{as => _, entity => _}
import akka.http.scaladsl.server.directives.RouteDirectives.{complete => _, reject => _}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import org.knora.webapi.BadRequestException
import org.knora.webapi.messages.v1.responder.resourcemessages.CreateResourceApiRequestV1
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
  * This Spec holds examples on how certain akka features can be used.
  */
class E2ECookbookSpec extends E2ESpec {

    override def testConfigSource =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    "the uploadedFile directive" should {

        "write a posted file to a temporary file on disk" in {

            val xml = "<int>42</int>"

            val simpleMultipartUpload =
                Multipart.FormData(Multipart.FormData.BodyPart.Strict(
                    "fieldName",
                    HttpEntity(ContentTypes.`text/xml(UTF-8)`, xml),
                    Map("filename" → "age.xml")))

            @volatile var file: Option[File] = None

            try {
                Post("/", simpleMultipartUpload) ~> {
                    uploadedFile("fieldName") {
                        case (info, tmpFile) ⇒
                            file = Some(tmpFile)
                            complete(info.toString)
                    }
                } ~> check {
                    file.isDefined === true
                    responseAs[String] === FileInfo("fieldName", "age.xml", ContentTypes.`text/xml(UTF-8)`).toString
                    val in = new FileInputStream(file.get)
                    try {
                        val buffer = new Array[Byte](1024)
                        in.read(buffer)
                        new String(buffer, "UTF-8")
                    } finally {
                        in.close()
                    } === xml
                }
            } finally {
                file.foreach(_.delete())
            }
        }
    }
    "the route receiving the upload request " should {
        "write a posted file to a temporary file on disk and return file information (using uploadedFile directive) " in {
            val pathToFile = "_test_data/test_route/images/Chlaus.jpg"
            val fileToSend = new File(pathToFile)

            val formDataFile = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            @volatile var receivedFile: Option[File] = None

            try {
                Post("/", formDataFile) ~> {
                    uploadedFile("file") {
                        case (info, tmpFile) ⇒
                            receivedFile = Some(tmpFile)
                            complete(info.toString)
                    }
                } ~> check {
                    receivedFile.isDefined === true
                    assert(responseAs[String] === FileInfo("file", "Chlaus.jpg", MediaTypes.`image/jpeg`).toString)
                    assert(bincompare(fileToSend, receivedFile.get))
                }
            } finally {
                receivedFile.foreach(_.delete())
            }



        }

        "write a posted file to a temporary file on disk and return file information (using the akka-stream API) " in {
            val pathToFile = "_test_data/test_route/images/Chlaus.jpg"
            val fileToSend = new File(pathToFile)

            val source = """{ "some": "JSON source" }"""
            val jsonAst = source.parseJson // or JsonParser(source)

            val formDataFile = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(MediaTypes.`application/json`, jsonAst.compactPrint)
                ),
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            @volatile var receivedFile: Option[File] = None

            try {
                Post("/", formDataFile) ~> {
                    entity(as[Multipart.FormData]) { formdata =>
                        extractRequestContext { ctx ⇒
                            type Name = String

                            val jsonMap = formdata.parts.mapAsync(1) { p: Multipart.FormData.BodyPart =>
                                if (p.name == "json") {
                                    val read = p.entity.dataBytes.runFold(ByteString.empty)((whole, chunk) => whole ++ chunk)
                                    read.map(read =>
                                        Map(p.name -> read.utf8String.parseJson)
                                    )
                                } else {
                                    Future(Map.empty[Name, JsValue])
                                }
                            }.runFold(Map.empty[Name, JsValue])((set, value) => set ++ value)

                            val fileMap = formdata.parts.mapAsync(1) { p: Multipart.FormData.BodyPart =>
                                if (p.filename.isDefined) {
                                    //println(s"part named ${p.name} has file named ${p.filename}")
                                    val tmpFile = File.createTempFile(s"userfile_${p.name}_${p.filename.getOrElse("")}", "")
                                    val written = p.entity.dataBytes.runWith(FileIO.toPath(tmpFile.toPath))
                                    written.map { written =>
                                        //println(s"written result: ${written.wasSuccessful}, ${p.filename.get}, ${tmpFile.getAbsolutePath}")
                                        receivedFile = Some(tmpFile)
                                        Map(p.name -> FileInfo(p.name, p.filename.get, p.entity.contentType))
                                    }
                                } else {
                                    Future(Map.empty[Name, FileInfo])
                                }
                            }.runFold(Map.empty[Name, FileInfo])((set, value) => set ++ value)



                            val result = for {
                                jm <- jsonMap
                                fm <- fileMap

                                _ = if (jm.contains("json") === false) throw BadRequestException("no json")
                                res = fm.getOrElse("file", throw BadRequestException("no file")).toString
                            } yield res

                            complete(result)
                        }
                    }

                } ~> check {
                    //receivedFile.isDefined === true
                    //println(responseAs[String])
                    assert(responseAs[String] === FileInfo("file", "Chlaus.jpg", MediaTypes.`image/jpeg`).toString)
                    assert(bincompare(fileToSend, receivedFile.get))
                }
            } finally {
                //receivedFile.foreach(_.delete())
            }



        }
    }

    "the route receiving JSON " should {
        "convert it to an object " in {

            val textattrStringified =
                """
                  {
                      "bold": [{
                          "start": 0,
                          "end": 4
                      }]
                  }
                """.toJson.compactPrint

            val params =
                s"""
                  |{
                  |    "restype_id": "http://www.knora.org/ontology/anything#Thing",
                  |    "label": "A thing",
                  |    "project_id": "http://data.knora.org/projects/anything",
                  |    "properties": {
                  |        "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr": $textattrStringified ,"resource_reference" :[],"utf8str":"Test text"}}],
                  |        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                  |        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                  |        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                  |        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                  |        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                  |        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                  |        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                  |    }
                  |}
                """.stripMargin

            import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._

            Post("/", HttpEntity(ContentTypes.`application/json`, params)) ~> {
                entity(as[CreateResourceApiRequestV1]) { apiRequest =>
                    complete("OK")
                }
            } ~> check {
                    assert(response.status == StatusCodes.OK)
            }
        }
    }

    private def bincompare(file1: File, file2: File): Boolean = {

        if(file1.length != file2.length){
            return false;
        }

        try {
            val in1: InputStream =new BufferedInputStream(new FileInputStream(file1))
            val in2: InputStream = new BufferedInputStream(new FileInputStream(file2))

            var value1 = 0
            var value2 = 0

            do {
                //since we're buffered read() isn't expensive
                value1 = in1.read()
                value2 = in2.read()
                if(value1 !=value2){
                    return false
                }
            } while(value1 >= 0)

            //since we already checked that the file sizes are equal
            //if we're here we reached the end of both files without a mismatch
            true
        }
        catch {
            case e: Exception => throw e
        }
    }

}
