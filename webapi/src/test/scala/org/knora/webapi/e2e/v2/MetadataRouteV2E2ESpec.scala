package org.knora.webapi.e2e.v2

import java.net.URLEncoder
import java.nio.file.Paths

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import org.knora.webapi._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.FileUtil

class MetadataRouteV2E2ESpec extends E2ESpec {
  private val rdfModelFactory: RdfModelFactory = RdfFeatureFactory.getRdfModelFactory(defaultFeatureFactoryConfig)
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)

  private val beolUserEmail = SharedTestDataADM.beolUser.email
  private val beolProjectIRI: IRI = SharedTestDataADM.BEOL_PROJECT_IRI
  private val password = SharedTestDataADM.testPass

  private val metadataAsTurtle: String = FileUtil.readTextFile(Paths.get("test_data/metadataE2EV2/metadata.ttl"))
  private val metadataAsFlatJsonLD: String =
    FileUtil.readTextFile(Paths.get("test_data/metadataE2EV2/metadata-flat.jsonld"))

  private val expectedRdfModel: RdfModel = rdfFormatUtil.parseToRdfModel(
    rdfStr = metadataAsTurtle,
    rdfFormat = Turtle
  )

  "The metadata v2 endpoint" should {
    "perform a put request for the metadata of beol project given as Turtle" in {
      val request = Put(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}",
                        HttpEntity(RdfMediaTypes.`text/turtle`, metadataAsTurtle)) ~>
        addCredentials(BasicHttpCredentials(beolUserEmail, password))

      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status.isSuccess())
      val responseString = responseToString(response)
      assert(responseString.contains(s"Project metadata was stored for project <$beolProjectIRI>."))
    }

    "perform a put request for the metadata of beol project given as JSON-LD" in {
      val request = Put(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}",
                        HttpEntity(RdfMediaTypes.`application/json`, metadataAsFlatJsonLD)) ~>
        addCredentials(BasicHttpCredentials(beolUserEmail, password))

      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status.isSuccess())
      val responseString = responseToString(response)
      assert(responseString.contains(s"Project metadata was stored for project <$beolProjectIRI>."))
    }

    "get the created metadata graph as JSON-LD" in {
      val request = Get(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status.isSuccess())
      val responseJSONLD: JsonLDDocument = responseToJsonLDDocument(response)
      val responseModel: RdfModel = responseJSONLD.toRdfModel(rdfModelFactory)
      assert(responseModel == expectedRdfModel)
    }

    "get the created metadata graph as flat JSON-LD" in {
      val expectedFlatJsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(metadataAsFlatJsonLD)

      val request = Get(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}")
        .addHeader(RawHeader(RouteUtilV2.JSON_LD_RENDERING_HEADER, RouteUtilV2.JSON_LD_RENDERING_FLAT))

      val response: HttpResponse = singleAwaitingRequest(request)
      val responseJSONLD: JsonLDDocument = responseToJsonLDDocument(response)
      assert(response.status.isSuccess())
      assert(responseJSONLD == expectedFlatJsonLDDocument)
    }

    "get the created metadata graph as Turtle" in {
      val turtleType = RdfMediaTypes.`text/turtle`.toString()
      val header = RawHeader("Accept", turtleType)
      val request = Get(s"$baseApiUrl/v2/metadata/${URLEncoder.encode(beolProjectIRI, "UTF-8")}") ~> addHeader(header)
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status.isSuccess())
      response.entity.contentType.mediaType.value should be(turtleType)
      val responseStr = responseToString(response)
      val responseModel: RdfModel = parseTurtle(responseStr)
      assert(responseModel == expectedRdfModel)
    }

    "not return metadata for an invalid project IRI" in {
      val request = Get(s"$baseApiUrl/v2/metadata/invalid-projectIRI")
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status.isFailure())
    }

    "not create metadata for an invalid project IRI" in {
      val request = Put(s"$baseApiUrl/v2/metadata/invalid-projectIRI",
                        HttpEntity(RdfMediaTypes.`text/turtle`, metadataAsTurtle)) ~>
        addCredentials(BasicHttpCredentials(beolUserEmail, password))

      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status.isFailure())
    }

    "return HTTP 404 if nonexistent metadata is requested" in {
      val turtleType = RdfMediaTypes.`text/turtle`.toString()
      val header = RawHeader("Accept", turtleType)
      val request = Get(
        s"$baseApiUrl/v2/metadata/${URLEncoder.encode(SharedTestDataADM.INCUNABULA_PROJECT_IRI, "UTF-8")}") ~> addHeader(
        header)
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.NotFound)
    }

  }
}
