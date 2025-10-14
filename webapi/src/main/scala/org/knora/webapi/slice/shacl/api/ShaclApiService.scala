/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import zio.*
import zio.stream.*

import java.io.FileInputStream

import org.knora.webapi.slice.shacl.domain.ShaclValidator
import org.knora.webapi.slice.shacl.domain.ValidationOptions

final case class ShaclApiService(private val validator: ShaclValidator) {

  def validate(formData: ValidationFormData): Task[ZStream[Any, Throwable, Byte]] = {
    val options = ValidationOptions(
      formData.validateShapes.getOrElse(ValidationOptions.default.validateShapes),
      formData.reportDetails.getOrElse(ValidationOptions.default.reportDetails),
      formData.addBlankNodes.getOrElse(ValidationOptions.default.addBlankNodes),
    )
    ZIO.scoped {
      for {
        dataStream  <- ZIO.fromAutoCloseable(ZIO.succeed(new FileInputStream(formData.`data.ttl`)))
        shaclStream <- ZIO.fromAutoCloseable(ZIO.succeed(new FileInputStream(formData.`shacl.ttl`)))
        report      <- validator.validate(dataStream, shaclStream, options)
        out          = ZStream.fromOutputStreamWriter(RDFDataMgr.write(_, report.getModel, RDFFormat.TURTLE))
      } yield out
    }
  }
}

object ShaclApiService {
  val layer = ZLayer.derive[ShaclApiService]
}
