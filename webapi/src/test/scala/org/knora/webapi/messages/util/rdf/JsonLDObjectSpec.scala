package org.knora.webapi.messages.util.rdf

import zio._
import zio.test._

import dsp.errors.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object JsonLDObjectSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("JsonLDObject")(
      iriValueSuite + stringValueSuite + objectValuesSuite + arrayValueSuite + intValueSuite + booleanValueSuite + idValueSuite + uuidValueSuite + smartIriValueSuite
    ).provide(IriConverter.layer, StringFormatter.test)

  private val emptyJsonLdObject                            = JsonLDObject(Map.empty)
  private val someKey                                      = "someKey"
  private def noValidation: (String, => Nothing) => String = (a, _) => a

  private val iriValueSuite = suite("getting iri values")(
    suite("when given an empty map")(
      test("getIri should fail") {
        for {
          actual <- emptyJsonLdObject.getIri().exit
        } yield assertTrue(actual == Exit.fail("This JSON-LD object does not represent an IRI: JsonLDObject(Map())"))
      },
      test("toIri should fail") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.toIri(noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("This JSON-LD object does not represent an IRI: JsonLDObject(Map())"))
        )
      },
      test("maybeIriInObject should return None") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.maybeIriInObject(someKey, noValidation))
        } yield assertTrue(actual.isEmpty)
      },
      test("requireIriInObject should fail") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireIriInObject(someKey, noValidation)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
      },
      test("getIdIriInObject should return None") {
        for {
          actual <- emptyJsonLdObject.getIdIriInObject(someKey)
        } yield assertTrue(actual.isEmpty)
      }
    ),
    suiteAll("when given a correct value") {
      val iriValue     = "http://www.knora.org/ontology/0001/anything#Thing"
      val jsonLdObject = JsonLDObject(Map(JsonLDKeywords.ID -> JsonLDString(iriValue)))
      test("getIri returns expected value")(for {
        actual <- jsonLdObject.getIri()
      } yield assertTrue(actual == iriValue))
      test("toIri returns expected value")(assertTrue(jsonLdObject.toIri(noValidation) == iriValue))

      val jsonLdObjectWithIdObject =
        JsonLDObject(Map(someKey -> JsonLDObject(Map(JsonLDKeywords.ID -> JsonLDString(iriValue)))))
      test("maybeIriInObject contains expected value") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIdObject.maybeIriInObject(someKey, noValidation))
        } yield assertTrue(actual.contains(iriValue))
      }
      test("requireIriInObject contains expected value") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIdObject.requireIriInObject(someKey, noValidation))
        } yield assertTrue(actual.contains(iriValue))
      }
      test("getIdIriInObject contains expected value") {
        for {
          actual <- jsonLdObjectWithIdObject.getIdIriInObject(someKey)
        } yield assertTrue(actual.contains(iriValue))
      }
    }
  )

  private val stringValueSuite = suite("getting string values")(
    suite("when given an empty map")(
      test("maybeString should return None") {
        assertTrue(emptyJsonLdObject.maybeString(someKey).isEmpty)
      },
      test("maybeStringWithValidation should return None") {
        assertTrue(emptyJsonLdObject.maybeStringWithValidation(someKey, noValidation).isEmpty)
      },
      test("getString should return None") {
        for {
          actual <- emptyJsonLdObject.getString(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("requireString should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireString(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
      },
      test("requireStringWithValidation should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireStringWithValidation(someKey, noValidation)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
      },
      test("getRequiredString should fail with correct message") {
        for {
          actual <- emptyJsonLdObject.getRequiredString(someKey).exit
        } yield assertTrue(actual == Exit.fail("No someKey provided"))
      }
    )
  )

  private val objectValuesSuite = zio.test.suite("getting object values")(
    suite("when given an empty map")(
      test("maybeObject should return None") {
        assertTrue(emptyJsonLdObject.maybeObject(someKey).isEmpty)
      },
      test("requireObject should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireObject(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
      },
      test("getObject should return None") {
        for {
          actual <- emptyJsonLdObject.getObject(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("getRequiredObject should fail") {
        for {
          actual <- emptyJsonLdObject.getRequiredObject(someKey).exit
        } yield assertTrue(actual == Exit.fail("No someKey provided"))
      }
    )
  )

  private val arrayValueSuite = suite("getting array values")(
    suite("when given an empty map")(
      test("maybeArray should return None") {
        assertTrue(emptyJsonLdObject.maybeArray(someKey).isEmpty)
      },
      test("requireArray should fail with a BadRequestException ") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireArray(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
      },
      test("getArray should return a None") {
        for {
          actual <- emptyJsonLdObject.getArray(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("getRquiredArray should fail with correct error message") {
        for {
          actual <- emptyJsonLdObject.getRequiredArray(someKey).exit
        } yield assertTrue(actual == Exit.fail("No someKey provided"))
      }
    )
  )
  private val intValueSuite = suite("getting int values")(
    suite("when given an empty map")(
      test("maybeInt should return None") {
        assertTrue(emptyJsonLdObject.maybeInt(someKey).isEmpty)
      },
      test("requireInt should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireInt(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
      },
      test("getInt should return None") {
        for {
          actual <- emptyJsonLdObject.getInt(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("getRequiredInt should fail with correct error message") {
        for {
          actual <- emptyJsonLdObject.getRequiredInt(someKey).exit
        } yield assertTrue(actual == Exit.fail("No someKey provided"))
      }
    )
  )

  private val booleanValueSuite = suite("getting boolean values")(
    suite("when given an empty map")(
      // Boolean value
      test("maybeBoolean should return None") {
        assertTrue(emptyJsonLdObject.maybeBoolean(someKey).isEmpty)
      },
      test("requireBoolean should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireBoolean(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
      },
      test("getBoolean should return None") {
        for {
          actual <- emptyJsonLdObject.getBoolean(someKey)
        } yield assertTrue(actual.isEmpty)
      },
      test("getRequiredBoolean should fail with correct error message") {
        for {
          actual <- emptyJsonLdObject.getRequiredBoolean(someKey).exit
        } yield assertTrue(actual == Exit.fail("No someKey provided"))
      }
    )
  )

  private val idValueSuite = suite("getting id values")(
    suite("when given an empty map")(
      test("maybeIDAsKnoraDataIri should return None") {
        assertTrue(emptyJsonLdObject.maybeIDAsKnoraDataIri.isEmpty)
      },
      test("requireIDAsKnoraDataIri should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireIDAsKnoraDataIri).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No @id provided")))
      },
      test("getIDAsKnoraDataIri should return None") {
        for {
          actual <- emptyJsonLdObject.getIdValueAsKnoraDataIri
        } yield assertTrue(actual.isEmpty)
      },
      test("getRequiredIDAsKnoraDataIri should fail with correct error message") {
        for {
          actual <- emptyJsonLdObject.getRequiredIdValueAsKnoraDataIri.exit
        } yield assertTrue(actual == Exit.fail("No @id provided"))
      }
    )
  )
  private val uuidValueSuite = suite("getting uuid values")(
    suite("when given an empty map")(
      // uuid value
      test("maybeUUID should return None") {
        assertTrue(emptyJsonLdObject.maybeUUID(someKey).isEmpty)
      },
      test("getUuid should return None") {
        for {
          actual <- emptyJsonLdObject.getUuid(someKey)
        } yield assertTrue(actual.isEmpty)
      }
    )
  )

  private val smartIriValueSuite = suite("getting smart iri values")(
    suite("when given an empty map")(
      test("requireTypeAsKnoraApiV2ComplexTypeIri should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireTypeAsKnoraApiV2ComplexTypeIri).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No @type provided")))
      },
      test("getRequiredTypeAsKnoraApiV2ComplexTypeIri should fail with correct message") {
        for {
          actual <- emptyJsonLdObject.getRequiredTypeAsKnoraApiV2ComplexTypeIri.exit
        } yield assertTrue(actual == Exit.fail("No @type provided"))
      },
      test("requireResourcePropertyApiV2ComplexValue should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(emptyJsonLdObject.requireResourcePropertyApiV2ComplexValue).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException("No value submitted")))
      },
      test("getRequiredResourcePropertyApiV2ComplexValue should fail with correct message") {
        for {
          actual <- emptyJsonLdObject.getRequiredResourcePropertyApiV2ComplexValue.exit
        } yield assertTrue(actual == Exit.fail("No value submitted"))
      }
    )
  )
}
