package org.knora.webapi.slice.ontology.api
import zio.*
import zio.test.*

import java.time.Instant
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeOntologyMetadataRequestV2
import org.knora.webapi.slice.common.JsonLdTestUtil.JsonLdTransformations
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import zio.test.check

object OntologyV2RequestParserSpec extends ZIOSpecDefault {
  private val sf = StringFormatter.getInitializedTestInstance

  private val parser = ZIO.serviceWithZIO[OntologyV2RequestParser]
  private val user   = TestDataFactory.User.rootUser

  private val changeOntologyMetadataRequestV2Suite =
    suite("ChangeOntologyMetadataRequestV2") {
      test("should parse correct jsonLd") {
        val instant = Instant.parse("2017-12-19T15:23:42.166Z")
        val jsonLd: String =
          """
            |{
            |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
            |  "@type" : "owl:Ontology",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "2017-12-19T15:23:42.166Z"
            |  },
            |  "rdfs:label" : {
            |    "@language" : "en",
            |    "@value" : "Some Label"
            |  },
            |  "rdfs:comment" : {
            |    "@language" : "en",
            |    "@value" : "Some Comment"
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

        check(JsonLdTransformations.allGen) { t =>
          for {
            uuid <- Random.nextUUID
            req  <- parser(_.changeOntologyMetadataRequestV2(t(jsonLd), uuid, user))
          } yield assertTrue(
            req == ChangeOntologyMetadataRequestV2(
              sf.toSmartIri("http://0.0.0.0:3333/ontology/0001/anything/v2"),
              Some("Some Label"),
              Some("Some Comment"),
              instant,
              uuid,
              user,
            ),
          )
        }
      }
    }

  val spec = suite("OntologyV2RequestParser")(changeOntologyMetadataRequestV2Suite)
    .provide(OntologyV2RequestParser.layer, IriConverter.layer, StringFormatter.test)
}
