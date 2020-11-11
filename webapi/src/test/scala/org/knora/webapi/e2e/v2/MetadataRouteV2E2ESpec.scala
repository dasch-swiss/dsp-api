package org.knora.webapi.e2e.v2

import java.net.URLEncoder

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi._

class MetadataRouteV2E2ESpec extends E2ESpec {
    private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)

    private val beolUserEmail = SharedTestDataADM.beolUser.email
    private val beolProjectIRI: IRI = SharedTestDataADM.BEOL_PROJECT_IRI
    private val password = SharedTestDataADM.testPass

    private val metadataContent: String =
        s"""
           |@prefix dsp-repo: <http://ns.dasch.swiss/repository#> .
           |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
           |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
           |@prefix owl: <http://www.w3.org/2002/07/owl#> .
           |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
           |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
           |@prefix xml: <http://www.w3.org/XML/1998/namespace> .
           |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
           |@prefix foaf:<http://xmlns.com/foaf/0.1/> .
           |@prefix vowl: <http://purl.org/vowl/spec/v2/> .
           |@prefix prov: <http://www.w3.org/ns/prov#> .
           |@prefix dc: <http://purl.org/dc/elements/1.1/> .
           |@prefix dct: <http://purl.org/dc/terms/> .
           |@prefix locn: <http://www.w3.org/ns/locn#> .
           |@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .
           |@prefix schema: <https://schema.org/>.
           |@prefix skos: <http://www.w3.org/2004/02/skos/core#> .
           |@prefix unesco6: <http://skos.um.es/unesco6/> .
           |@base <http://ns.dasch.swiss/repository#> .
           |
           |<beol> rdf:type dsp-repo:Project .
           |<beol> dsp-repo:hasName "Bernoulli-Euler Online (BEOL)" .
           |<beol> dsp-repo:hasDescription "The project Bernoulli-Euler Online (BEOL) integrates the two edition projects Basler Edition der Bernoulli-Briefwechsel (BEBB) and Leonhardi Euleri Opera Omnia (LEOO) into one digital platform available on the web. In addition, Jacob Bernoulli's scientific notebook Meditationes - a document of outstanding significance for the history of mathematics at its turning point around 1700 - is published for the first time in its entirety on the BEOL platform as a region-based multilayer interactive digital edition providing access to facsimiles, transcriptions, translations, indices, and commentaries. Besides being an edition platform, BEOL is a virtual research environment for the study of early modern mathematics and science as a data graph using sophisticated analysis tools. Currently BEOL is connected to two third-party repositories: The Newton Project and the Briefportal Leibniz, initiating the formation of a network of digital editions of the early modern scientific correspondence data.The goal of BEOL is thus twofold: it focuses on the mathematics influenced by the Bernoulli dynasty and Leonhard Euler and undertakes a methodological effort to present these materials to the public and researchers in a highly functional way." .
           |<beol> dsp-repo:hasKeywords "mathematics" .
           |<beol> dsp-repo:hasKeywords "science" .
           |<beol> dsp-repo:hasKeywords "history of science" .
           |<beol> dsp-repo:hasKeywords "history of mathematics" .
           |<beol> dsp-repo:hasKeywords "Bernoulli" .
           |<beol> dsp-repo:hasKeywords "Euler" .
           |<beol> dsp-repo:hasKeywords "Newton" .
           |<beol> dsp-repo:hasKeywords "Leibniz" .
           |<beol> dsp-repo:hasCategories "mathematics" .
           |<beol> dsp-repo:hasStartDate "2016.07" .
           |<beol> dsp-repo:hasEndDate "2020.01" .
           |<beol> dsp-repo:hasTemporalCoverage "17th century and 18th century CE" .
           |<beol> dsp-repo:hasSpatialCoverage "Europe" .
           |<beol> dsp-repo:hasSpatialCoverage "Russia" .
           |<beol> dsp-repo:hasSpatialCoverage "France" .
           |<beol> dsp-repo:hasSpatialCoverage "Switzerland" .
           |<beol> dsp-repo:hasSpatialCoverage "Germany" .
           |<beol> dsp-repo:hasSpatialCoverage "Italy" .
           |<beol> dsp-repo:hasSpatialCoverage "England" .
           |<beol> dsp-repo:hasFunder "Schweizerischer Nationalfonds (SNSF)" .
           |<beol> dsp-repo:hasURL "https://beol.dasch.swiss/" .
           |<beol> dsp-repo:hasDateCreated "09.2017" .
           |<beol> dsp-repo:hasDateModified "04.2020" .
           |<beol> dsp-repo:hasShortcode "0801" .
           |<beol> dsp-repo:hasAlternateName "beol" .
           |""".stripMargin

    private val metadataAsJsonLD: String =
        """
          |{
          |    "http://ns.dasch.swiss/repository#hasDateModified": "04.2020",
          |    "http://ns.dasch.swiss/repository#hasShortcode": "0801",
          |    "http://ns.dasch.swiss/repository#hasName": "Bernoulli-Euler Online (BEOL)",
          |    "http://ns.dasch.swiss/repository#hasSpatialCoverage": [
          |        "Italy",
          |        "Switzerland",
          |        "England",
          |        "France",
          |        "Russia",
          |        "Germany",
          |        "Europe"
          |    ],
          |    "http://ns.dasch.swiss/repository#hasFunder": "Schweizerischer Nationalfonds (SNSF)",
          |    "http://ns.dasch.swiss/repository#hasKeywords": [
          |        "Leibniz",
          |        "science",
          |        "Euler",
          |        "Bernoulli",
          |        "mathematics",
          |        "history of science",
          |        "Newton",
          |        "history of mathematics"
          |    ],
          |    "http://ns.dasch.swiss/repository#hasEndDate": "2020.01",
          |    "http://ns.dasch.swiss/repository#hasTemporalCoverage": "17th century and 18th century CE",
          |    "http://ns.dasch.swiss/repository#hasCategories": "mathematics",
          |    "http://ns.dasch.swiss/repository#hasDescription": "The project Bernoulli-Euler Online (BEOL) integrates the two edition projects Basler Edition der Bernoulli-Briefwechsel (BEBB) and Leonhardi Euleri Opera Omnia (LEOO) into one digital platform available on the web. In addition, Jacob Bernoulli's scientific notebook Meditationes - a document of outstanding significance for the history of mathematics at its turning point around 1700 - is published for the first time in its entirety on the BEOL platform as a region-based multilayer interactive digital edition providing access to facsimiles, transcriptions, translations, indices, and commentaries. Besides being an edition platform, BEOL is a virtual research environment for the study of early modern mathematics and science as a data graph using sophisticated analysis tools. Currently BEOL is connected to two third-party repositories: The Newton Project and the Briefportal Leibniz, initiating the formation of a network of digital editions of the early modern scientific correspondence data.The goal of BEOL is thus twofold: it focuses on the mathematics influenced by the Bernoulli dynasty and Leonhard Euler and undertakes a methodological effort to present these materials to the public and researchers in a highly functional way.",
          |    "@type": "http://ns.dasch.swiss/repository#Project",
          |    "http://ns.dasch.swiss/repository#hasAlternateName": "beol",
          |    "http://ns.dasch.swiss/repository#hasStartDate": "2016.07",
          |    "http://ns.dasch.swiss/repository#hasURL": "https://beol.dasch.swiss/",
          |    "http://ns.dasch.swiss/repository#hasDateCreated": "09.2017",
          |    "@id": "http://ns.dasch.swiss/beol"
          |}
          |""".stripMargin

    "The metadata v2 endpoint" should {
        "perform a put request for the metadata of beol project given as Turtle" in {
            val request = Put(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}",
                HttpEntity(RdfMediaTypes.`text/turtle`, metadataContent)) ~>
                addCredentials(BasicHttpCredentials(beolUserEmail, password))

            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status.isSuccess())
            val responseString = responseToString(response)
            assert(responseString.contains(s"Project metadata was stored for project <$beolProjectIRI>."))
        }

        "perform a put request for the metadata of beol project given as JSON-LD" in {
            val request = Put(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}",
                HttpEntity(RdfMediaTypes.`application/json`, metadataAsJsonLD)) ~>
                addCredentials(BasicHttpCredentials(beolUserEmail, password))

            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status.isSuccess())
            val responseString = responseToString(response)
            assert(responseString.contains(s"Project metadata was stored for project <$beolProjectIRI>."))
        }

        "get the created metadata graph as JSON-LD" in {
            val request = Get(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseJSONLD = responseToJsonLDDocument(response)
            assert(response.status.isSuccess())

            val expectedGraphJSONLD: JsonLDDocument = rdfFormatUtil.parseToJsonLDDocument(
                rdfStr = metadataContent,
                rdfFormat = Turtle
            )

            assert(expectedGraphJSONLD.body == responseJSONLD.body)
        }

        "get the created metadata graph as Turtle" in {
            val turtleType = RdfMediaTypes.`text/turtle`.toString()
            val header = RawHeader("Accept", turtleType)
            val request = Get(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}") ~> addHeader(header)
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status.isSuccess())
            response.entity.contentType.mediaType.value should be(turtleType)
        }

        "not return metadata for an invalid project IRI" in {
            val request = Get(s"$baseApiUrl/v2/metadata/invalid-projectIRI")
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status.isFailure())
        }

        "not create metadata for an invalid project IRI" in {
            val request = Put(s"$baseApiUrl/v2/metadata/invalid-projectIRI",
                HttpEntity(RdfMediaTypes.`text/turtle`, metadataContent)) ~>
                addCredentials(BasicHttpCredentials(beolUserEmail, password))

            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status.isFailure())
        }

    }
}
