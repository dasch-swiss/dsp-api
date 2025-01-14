/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.util.ByteString
import zio.*

import java.io.FileInputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

import org.knora.webapi.slice.shacl.domain.ShaclValidator
import org.knora.webapi.slice.shacl.domain.ValidationOptions

final case class ShaclApiService(validator: ShaclValidator) {

  def validate(formData: ValidationFormData): Task[Source[ByteString, Any]] = {
    val options = ValidationOptions(
      formData.validateShapes.getOrElse(ValidationOptions.default.validateShapes),
      formData.reportDetails.getOrElse(ValidationOptions.default.reportDetails),
      formData.addBlankNodes.getOrElse(ValidationOptions.default.addBlankNodes),
    )
    val (out, src) = makeOutputStreamAndSource()
    ZIO.scoped {
      for {
        dataStream  <- ZIO.fromAutoCloseable(ZIO.succeed(new FileInputStream(formData.`data.ttl`)))
        shaclStream <- ZIO.fromAutoCloseable(ZIO.succeed(new FileInputStream(formData.`shacl.ttl`)))
        report      <- validator.validate(dataStream, shaclStream, options)
        _ <- ZIO.attemptBlockingIO {
               try { RDFDataMgr.write(out, report.getModel, RDFFormat.TURTLE) }
               finally { out.close() }
             }.forkDaemon
      } yield ()
    }.as(src)
  }

  private def makeOutputStreamAndSource(): (OutputStream, Source[ByteString, _]) = {
    val outputStream = new PipedOutputStream()
    val inputStream  = new PipedInputStream(outputStream)
    val source       = StreamConverters.fromInputStream(() => inputStream)
    (outputStream, source)
  }
}

object ShaclApiService {
  val layer = ZLayer.derive[ShaclApiService]
}
