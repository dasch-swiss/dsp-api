package org.knora.webapi.slice.ontology.api

import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeOntologyMetadataRequestV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.common.jena.JenaConversions.given_Conversion_String_Property

import java.util.UUID
import zio.*

import java.time.Instant

final case class OntologyV2RequestParser(iriConverter: IriConverter) {

  def changeOntologyMetadataRequestV2(
    jsonLd: String,
    apiRequestID: UUID,
    requestingUser: User,
  ): IO[String, ChangeOntologyMetadataRequestV2] = ZIO.scoped {
    for {
      model <- ModelOps.fromJsonLd(jsonLd)
      r     <- ZIO.fromEither(model.singleRootResource)
      ontologyIri: SmartIri <-
        ZIO.fromOption(r.uri).orElseFail("No IRI found").flatMap(iriConverter.asSmartIri(_).mapError(_.getMessage))
      label                 = None
      comment               = None
      lastModificationDate <- ZIO.fromEither(r.objectInstant(LastModificationDate))
    } yield ChangeOntologyMetadataRequestV2(
      ontologyIri,
      label,
      comment,
      lastModificationDate,
      apiRequestID,
      requestingUser,
    )
  }

}

object OntologyV2RequestParser {
  val layer = ZLayer.derive[OntologyV2RequestParser]
}
