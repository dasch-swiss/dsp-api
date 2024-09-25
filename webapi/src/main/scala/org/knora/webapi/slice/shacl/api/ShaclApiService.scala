package org.knora.webapi.slice.shacl.api

import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import zio.*

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

import org.knora.webapi.slice.shacl.domain.ShaclValidator
import org.knora.webapi.slice.shacl.domain.ValidationOptions

final case class ShaclApiService(validator: ShaclValidator) {

  def validate(formData: ValidationFormData): Task[String] = {
    val out = new ByteArrayOutputStream()
    validator
      .validate(
        ByteArrayInputStream(formData.`data.ttl`.getBytes),
        ByteArrayInputStream(formData.`shacl.ttl`.getBytes),
        ValidationOptions(
          formData.validateShapes.getOrElse(ValidationOptions.default.validateShapes),
          formData.reportDetails.getOrElse(ValidationOptions.default.reportDetails),
          formData.addBlankNodes.getOrElse(ValidationOptions.default.addBlankNodes),
        ),
      )
      .map(resource => RDFDataMgr.write(out, resource.getModel, RDFFormat.TURTLE))
      .as(out.toString)
  }
}

object ShaclApiService {
  val layer = ZLayer.derive[ShaclApiService]
}
