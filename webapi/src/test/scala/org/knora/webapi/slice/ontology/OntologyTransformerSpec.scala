package org.knora.webapi.slice.ontology

import org.apache.jena.riot.Lang
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.jena.ModelOps
import zio.ZIO
import zio.test.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object OntologyTransformerSpec extends ZIOSpecDefault {

  private val transformer = ZIO.serviceWithZIO[OntologyTransformer]

  private val shortcode   = Shortcode.unsafeFrom("9999")
  private val resourceIri = ResourceIri.makeNew(shortcode)

  private val onto     = "http://0.0.0.0:3333/ontology/9999/onto/v2#"
  private val knoraApi = "http://api.knora.org/ontology/knora-api/v2#"
  private val xsd      = "http://www.w3.org/2001/XMLSchema#"

  private def writeTempFile(suffix: String, content: String): ZIO[Any, Throwable, Path] =
    ZIO.attemptBlocking {
      val p = Files.createTempFile("onto-transformer-test-", suffix)
      Files.write(p, content.getBytes(StandardCharsets.UTF_8))
      p
    }

  private def deleteIfExists(p: Path): ZIO[Any, Nothing, Unit] =
    ZIO.attempt(Files.deleteIfExists(p)).ignore.unit

  /** Wraps a single value object as a one-resource JSON-LD payload on a fixed resource IRI. */
  private def resourceWithValueJsonLd(
    valueProp: String,
    valueClass: String,
    inner: String,
  ): String =
    s"""
       |[{
       |    "@id": "$resourceIri",
       |    "rdfs:label": "test",
       |    "$valueProp": {
       |      "@type": "$valueClass",
       |      $inner
       |    },
       |    "@context": {
       |       "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
       |    }
       |}]""".stripMargin

  /** Expected Turtle for a resource carrying a single simple-scalar value. */
  private def expectedResourceWithSimpleValue(
    propLocalName: String,
    valueClass: String,
    valueHasProp: String,
    valueLiteral: String,
  ): String =
    s"""
       | PREFIX rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
       | PREFIX rdfs:       <http://www.w3.org/2000/01/rdf-schema#>
       | PREFIX xsd:        <http://www.w3.org/2001/XMLSchema#>
       | PREFIX onto:       <http://www.knora.org/ontology/9999/onto#>
       | PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
       |
       | <$resourceIri>
       |     rdfs:label  "test" ;
       |     onto:$propLocalName [
       |         a                        knora-base:$valueClass ;
       |         knora-base:$valueHasProp $valueLiteral
       |     ] .
       |""".stripMargin

  private def runTransform(jsonLd: String, expectedTurtle: String) =
    ZIO.scoped {
      for {
        inputPath  <- ZIO.acquireRelease(writeTempFile(".jsonld", jsonLd).orDie)(deleteIfExists)
        outputPath <- transformer(_.toKnoraBase(inputPath))
        _          <- ZIO.addFinalizer(deleteIfExists(outputPath))
        actualNQ   <- ZIO.attempt(new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8))
        actual     <- ModelOps.from(actualNQ, Lang.NTRIPLES)
        expected   <- ModelOps.fromTurtle(expectedTurtle)
        iso         = actual.isIsomorphicWith(expected)
      } yield assertTrue(iso)
    }

  private val simpleScalarValues = suite("Simple Scalar Values")(
    test("BooleanValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testBoolean",
          valueClass = s"${knoraApi}BooleanValue",
          inner = s""""${knoraApi}booleanValueAsBoolean": { "@type": "${xsd}boolean", "@value": true }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testBoolean",
          valueClass = "BooleanValue",
          valueHasProp = "valueHasBoolean",
          valueLiteral = "\"true\"^^xsd:boolean",
        ),
      )
    },
    test("ColorValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testColor",
          valueClass = s"${knoraApi}ColorValue",
          inner = s""""${knoraApi}colorValueAsColor": { "@type": "${xsd}string", "@value": "#00ff00" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testColor",
          valueClass = "ColorValue",
          valueHasProp = "valueHasColor",
          valueLiteral = "\"#00ff00\"",
        ),
      )
    },
    test("DecimalValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testDecimalSimpleText",
          valueClass = s"${knoraApi}DecimalValue",
          inner = s""""${knoraApi}decimalValueAsDecimal": { "@type": "${xsd}decimal", "@value": "2.71" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testDecimalSimpleText",
          valueClass = "DecimalValue",
          valueHasProp = "valueHasDecimal",
          valueLiteral = "\"2.71\"^^xsd:decimal",
        ),
      )
    },
    test("GeonameValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testGeoname",
          valueClass = s"${knoraApi}GeonameValue",
          inner = s""""${knoraApi}geonameValueAsGeonameCode": { "@type": "${xsd}string", "@value": "1111111" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testGeoname",
          valueClass = "GeonameValue",
          valueHasProp = "valueHasGeonameCode",
          valueLiteral = "\"1111111\"",
        ),
      )
    },
    test("IntValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testIntegerSimpleText",
          valueClass = s"${knoraApi}IntValue",
          inner = s""""${knoraApi}intValueAsInt": { "@type": "${xsd}int", "@value": "1" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testIntegerSimpleText",
          valueClass = "IntValue",
          valueHasProp = "valueHasInteger",
          valueLiteral = "\"1\"^^xsd:int",
        ),
      )
    },
    test("TimeValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testTimeValue",
          valueClass = s"${knoraApi}TimeValue",
          inner =
            s""""${knoraApi}timeValueAsTimeStamp": { "@type": "${xsd}dateTimeStamp", "@value": "2019-10-23T13:45:12.01-14:00" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testTimeValue",
          valueClass = "TimeValue",
          valueHasProp = "valueHasTimeStamp",
          valueLiteral = "\"2019-10-23T13:45:12.01-14:00\"^^xsd:dateTimeStamp",
        ),
      )
    },
    test("UriValue") {
      runTransform(
        jsonLd = resourceWithValueJsonLd(
          valueProp = s"${onto}testUriValue",
          valueClass = s"${knoraApi}UriValue",
          inner = s""""${knoraApi}uriValueAsUri": { "@type": "${xsd}anyURI", "@value": "https://dasch.swiss" }""",
        ),
        expectedTurtle = expectedResourceWithSimpleValue(
          propLocalName = "testUriValue",
          valueClass = "UriValue",
          valueHasProp = "valueHasUri",
          valueLiteral = "\"https://dasch.swiss\"^^xsd:anyURI",
        ),
      )
    },
  )

  override def spec = suite("OntologyTransformerSpec")(
    simpleScalarValues,
  ).provide(OntologyTransformer.layer, StringFormatter.test, TestAppConfig.layer())

}
