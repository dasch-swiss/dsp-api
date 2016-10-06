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

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.MissingFormFieldRejection
import akka.http.scaladsl.server.directives.BasicDirectives.{extractRequestContext => _, provide => _, _}
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.FutureDirectives.{onSuccess => _, _}
import akka.http.scaladsl.server.directives.MarshallingDirectives.{as => _, entity => _, _}
import akka.http.scaladsl.server.directives.RouteDirectives.{complete => _, reject => _, _}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
  * This Spec holds examples on how certain akka features can be used.
  */
class E2ECookbookSpec extends E2ESpec {

    override def testConfigSource =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin

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

        "write a posted file to a temporary file on disk and return file information (using the stream API) " in {
            val pathToFile = "_test_data/test_route/images/Chlaus.jpg"
            val fileToSend = new File(pathToFile)

            val formDataFile = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )



            try {
                Post("/", formDataFile) ~> {
                    entity(as[Multipart.FormData]) { formData =>
                        extractRequestContext { ctx ⇒
                            implicit val mat = ctx.materializer
                            implicit val ec = ctx.executionContext

                            val multiPartSource: Source[(FileInfo, Source[ByteString, Any]), Any] = formData.parts
                                    .map(part ⇒ (FileInfo(part.name, part.filename.get, part.entity.contentType), part.entity.dataBytes))

                            val multiPartF: Future[Map[FileInfo, ByteString]] = multiPartSource.runWith(Sink.seq[(FileInfo, Source[ByteString, Any])].to)

                        }
                    }

                } ~> check {
                    //receivedFile.isDefined === true
                    assert(responseAs[String] === FileInfo("file", "Chlaus.jpg", MediaTypes.`image/jpeg`).toString)
                    //assert(bincompare(fileToSend, receivedFile.get))
                }
            } finally {
                //receivedFile.foreach(_.delete())
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
