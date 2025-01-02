package org.knora.webapi.slice.ontology.api
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeOntologyMetadataRequestV2
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import zio.*
import zio.test.ZIOSpecDefault
import zio.test.assertCompletes
import zio.test.assertTrue

import java.time.Instant

object OntologyV2RequestParserSpec extends ZIOSpecDefault {
  private val sf = StringFormatter.getInitializedTestInstance

  private val parser = ZIO.serviceWithZIO[OntologyV2RequestParser]

  private val changeOntologyMetadataRequestV2Suite =
    suite("ChangeOntologyMetadataRequestV2") {
      test("should parse correct jsonLd") {
        val instant = Instant.parse("2017-12-19T15:23:42.166Z")
        val jsonLd =
          """
            |{
            |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
            |  "@type" : "owl:Ontology",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "2017-12-19T15:23:42.166Z"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "owl" : "http://www.w3.org/2002/07/owl#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}
            |""".stripMargin
        for {
          req <- parser(_.changeOntologyMetadataRequestV2(jsonLd, null, null))
        } yield assertTrue(
          req == ChangeOntologyMetadataRequestV2(
            sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2"),
            None,
            None,
            instant,
            null,
            null,
          ),
        )
      }
    }

  val spec = suite("OntologyV2RequestParser")(changeOntologyMetadataRequestV2Suite)
    .provide(OntologyV2RequestParser.layer, IriConverter.layer, StringFormatter.test)
}
