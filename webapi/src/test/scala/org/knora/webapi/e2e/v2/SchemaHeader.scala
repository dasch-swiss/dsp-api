package org.knora.webapi.e2e.v2

import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import org.knora.webapi.routing.RouteUtilV2

import scala.util.Try

/**
  * A custom Akka HTTP header representing [[RouteUtilV2.SCHEMA_HEADER]], which a client can send to specify
  * which ontology schema should be used in an API response.
  *
  * The definition follows [[https://doc.akka.io/docs/akka-http/current/common/http-model.html#custom-headers]].
  */
final class SchemaHeader(token: String) extends ModeledCustomHeader[SchemaHeader] {
    override def renderInRequests = true
    override def renderInResponses = true
    override val companion: SchemaHeader.type = SchemaHeader
    override def value: String = token
}

object SchemaHeader extends ModeledCustomHeaderCompanion[SchemaHeader] {
    override val name: String = RouteUtilV2.SCHEMA_HEADER
    override def parse(value: String) = Try(new SchemaHeader(value))
}
