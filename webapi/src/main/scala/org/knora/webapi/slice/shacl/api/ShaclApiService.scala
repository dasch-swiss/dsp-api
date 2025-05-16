/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import zio.*

import java.io.FileInputStream
import java.io.StringWriter

import org.knora.webapi.slice.shacl.domain.ShaclValidator
import org.knora.webapi.slice.shacl.domain.ValidationOptions

final case class ShaclApiService(validator: ShaclValidator) {

  def validate(formData: ValidationFormData): Task[String] = {
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
        stringWriter = new StringWriter()
        _           <- ZIO.attemptBlockingIO(RDFDataMgr.write(stringWriter, report.getModel, RDFFormat.TURTLE))
      } yield stringWriter.toString
    }
  }
}

object ShaclApiService {
  val layer = ZLayer.derive[ShaclApiService]
}
