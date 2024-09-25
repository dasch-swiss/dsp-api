/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.util.ByteString
import zio.*

import java.io.ByteArrayInputStream
import org.knora.webapi.slice.shacl.domain.ShaclValidator
import org.knora.webapi.slice.shacl.domain.ValidationOptions

import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

final case class ShaclApiService(validator: ShaclValidator) {

  def validate(formData: ValidationFormData): Task[Source[ByteString, Any]] = {
    val dataStream  = ByteArrayInputStream(formData.`data.ttl`.getBytes)
    val shaclSthream = ByteArrayInputStream(formData.`shacl.ttl`.getBytes)
    validator
      .validate(
        dataStream,
        shaclStream,
        ValidationOptions(
          formData.validateShapes.getOrElse(ValidationOptions.default.validateShapes),
          formData.reportDetails.getOrElse(ValidationOptions.default.reportDetails),
          formData.addBlankNodes.getOrElse(ValidationOptions.default.addBlankNodes),
        ),
      )
      .flatMap(report =>
        ZIO.attemptBlockingIO {
          val (out, src) = makeOutputStreamAndSource()
          RDFDataMgr.write(out, report.getModel, RDFFormat.TURTLE)
          out.close()
          src
        },
      )
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
