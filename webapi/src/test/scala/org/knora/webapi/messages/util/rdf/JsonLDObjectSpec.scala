/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import zio.*
import zio.test.*
import zio.test.check

import java.net.URI
import java.util.UUID

import dsp.errors.BadRequestException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object JsonLDObjectSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JsonLDObject")(
      iriValueSuite +
        stringValueSuite +
        objectValuesSuite +
        arrayValueSuite +
        intValueSuite +
        booleanValueSuite +
        idValueSuite +
        uuidValueSuite +
        smartIriValueSuite +
        uriValueSuite,
    ).provide(IriConverter.layer, StringFormatter.test)

  private val emptyJsonLdObject                            = JsonLDObject(Map.empty)
  private val someKey                                      = "someKey"
  private val someResourceIri                              = "http://www.knora.org/ontology/0001/anything#Thing"
  private def noValidation: (String, => Nothing) => String = (a, _) => a

  // IRI value related tests
  private val iriValueSuite = suite("getting iri values")(
    iriValueWhenGivenAnEmptyMap,
    iriValueWhenGivenCorrectValues,
    iriValueWhenGivenInvalidValues,
  )

  private def iriValueWhenGivenAnEmptyMap = suite("when given an empty map")(
    test("getIri should fail") {
      assertTrue(
        emptyJsonLdObject.getIri == Left("This JSON-LD object does not represent an IRI: JsonLDObject(Map())"),
      )
    },
    test("toIri should fail") {
      for {
        actual <- ZIO.attempt(emptyJsonLdObject.toIri(noValidation)).exit
      } yield assertTrue(
        actual == Exit.fail(BadRequestException("This JSON-LD object does not represent an IRI: JsonLDObject(Map())")),
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
        actual <- emptyJsonLdObject.getIriInObject(someKey)
      } yield assertTrue(actual.isEmpty)
    },
  )

  private def iriValueWhenGivenCorrectValues = {
    val jsonLdObject = JsonLDObject(Map(JsonLDKeywords.ID -> JsonLDString(someResourceIri)))
    val jsonLdObjectWithIriInObject = JsonLDObject(
      Map(someKey -> JsonLDObject(Map(JsonLDKeywords.ID -> JsonLDString(someResourceIri)))),
    )
    suite("when given a correct value")(
      test("getIri returns expected value")(assertTrue(jsonLdObject.getIri == Right(someResourceIri))),
      test("toIri returns expected value")(assertTrue(jsonLdObject.toIri(noValidation) == someResourceIri)),
      test("maybeIriInObject contains expected value") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIriInObject.maybeIriInObject(someKey, noValidation))
        } yield assertTrue(actual.contains(someResourceIri))
      },
      test("requireIriInObject contains expected value") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIriInObject.requireIriInObject(someKey, noValidation))
        } yield assertTrue(actual.contains(someResourceIri))
      },
      test("getIdIriInObject contains expected value") {
        for {
          actual <- jsonLdObjectWithIriInObject.getIriInObject(someKey)
        } yield assertTrue(actual.contains(someResourceIri))
      },
    )
  }

  private def iriValueWhenGivenInvalidValues = {
    val invalidIdMap                = Map(JsonLDKeywords.ID -> JsonLDBoolean(true))
    val jsonLdObject                = JsonLDObject(invalidIdMap)
    val jsonLdObjectWithIriInObject = JsonLDObject(Map(someKey -> JsonLDObject(invalidIdMap)))
    val expectedError               = "Invalid @id: JsonLDBoolean(true) (string expected)"
    suite("when given an invalid value")(
      test("getIri returns should fail with correct error message")(
        assertTrue(jsonLdObject.getIri == Left(expectedError)),
      ),
      test("toIri should fail with a BadRequestException")(
        for {
          actual <- ZIO.attempt(jsonLdObject.toIri(noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException(expectedError)),
        ),
      ),
      test("maybeIriInObject should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIriInObject.maybeIriInObject(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException(expectedError)),
        )
      },
      test("requireIriInObject  should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIriInObject.requireIriInObject(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException(expectedError)),
        )
      },
      test("getIdIriInObject fails with correct error messageexpected value") {
        for {
          actual <- jsonLdObjectWithIriInObject.getIriInObject(someKey).exit
        } yield assertTrue(
          actual == Exit.fail(expectedError),
        )
      },
    )
  }

  // String value related tests
  private val stringValueSuite = suite("getting string values")(
    stringValueWhenGivenAnEmptyMap + stringValueWhenGivenValidString + stringValueWhenGivenInvalidString,
  )

  private def stringValueWhenGivenAnEmptyMap = suite("when given an empty map")(
    test("maybeStringWithValidation should return None") {
      assertTrue(emptyJsonLdObject.maybeStringWithValidation(someKey, noValidation).isEmpty)
    },
    test("getString should return None") {
      assertTrue(emptyJsonLdObject.getString(someKey) == Right(None))
    },
    test("requireStringWithValidation should fail with a BadRequestException") {
      for {
        actual <- ZIO.attempt(emptyJsonLdObject.requireStringWithValidation(someKey, noValidation)).exit
      } yield assertTrue(actual == Exit.fail(BadRequestException("No someKey provided")))
    },
    test("getRequiredString should fail with correct message") {
      assertTrue(emptyJsonLdObject.getRequiredString(someKey) == Left("No someKey provided"))
    },
    test("getRequiredString(key*) should fail with correct message") {
      assertTrue(
        emptyJsonLdObject.getRequiredString(Array(someKey)*) == Left(s"Property not found for '$someKey'"),
        emptyJsonLdObject.getRequiredString("unknown", someKey) == Left(s"Property not found for 'unknown or $someKey'"),
      )
    },
  )

  def stringValueWhenGivenValidString: Spec[Any, Serializable] = {
    val someString   = "someString"
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDString(someString)))
    suite("when given a valid String")(
      test("maybeStringWithValidation should return  correct value") {
        assertTrue(jsonLdObject.maybeStringWithValidation(someKey, noValidation).contains(someString))
      },
      test("getString should return correct value") {
        assertTrue(jsonLdObject.getString(someKey) == Right(Some(someString)))
      },
      test("requireStringWithValidation should return correct value") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireStringWithValidation(someKey, noValidation))
        } yield assertTrue(actual == someString)
      },
      test("getRequiredString should return correct value") {
        assertTrue(jsonLdObject.getRequiredString(someKey) == Right(someString))
      },
      test("getRequiredString(key*) should return correct value") {
        assertTrue(
          jsonLdObject.getRequiredString("unknown", someKey) == Right(someString),
          jsonLdObject.getRequiredString(someKey, "unknown") == Right(someString),
        )
      },
    )
  }

  def stringValueWhenGivenInvalidString: Spec[Any, Serializable] = {
    val jsonLdObject  = JsonLDObject(Map(someKey -> JsonLDBoolean(true)))
    val expectedError = "Invalid someKey: JsonLDBoolean(true) (string expected)"
    suite("when given an invalid String")(
      test("maybeStringWithValidation should fail with BadRequestExcpetion") {
        for {
          actual <- ZIO.attempt(jsonLdObject.maybeStringWithValidation(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException(expectedError)),
        )
      },
      test("getString should fail with correct error message") {
        assertTrue(jsonLdObject.getString(someKey) == Left(expectedError))
      },
      test("requireStringWithValidation should fail with BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireStringWithValidation(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException(expectedError)),
        )
      },
      test("getRequiredString should return correct value") {
        assertTrue(jsonLdObject.getRequiredString(someKey) == Left(expectedError))
      },
    )
  }

  // object  value related tests
  private val objectValuesSuite = suite("getting object values")(
    suite("when given an empty map")(
      test("getObject should return None") {
        assertTrue(emptyJsonLdObject.getObject(someKey) == Right(None))
      },
      test("getRequiredObject should fail") {
        assertTrue(emptyJsonLdObject.getRequiredObject(someKey) == Left("No someKey provided"))
      },
    ),
  )

  // array value related tests
  private val arrayValueSuite = suite("getting array values")(
    arrayValueSuiteWhenGivenEmptyMap + arrayValueSuiteWhenGivenValidArray,
  )

  private def arrayValueSuiteWhenGivenEmptyMap = suite("when given an empty map")(
    test("getArray should return a None") {
      assertTrue(emptyJsonLdObject.getArray(someKey).isEmpty)
    },
    test("getRequiredArray should fail with correct error message") {
      assertTrue(emptyJsonLdObject.getRequiredArray(someKey) == Left("No someKey provided"))
    },
  )

  private def arrayValueSuiteWhenGivenValidArray = {
    val stringValue = JsonLDString(someResourceIri)
    val jsonLdArray = JsonLDArray(List(stringValue))
    val suiteWithArray = {
      val jsonLdObjectWithArray = JsonLDObject(Map(someKey -> jsonLdArray))
      suite("when given a jsonLdObject with an array")(
        test("getArray should return value in List") {
          assertTrue(jsonLdObjectWithArray.getArray(someKey).contains(jsonLdArray))
        },
        test("getRequiredArray should return value in List") {
          assertTrue(jsonLdObjectWithArray.getRequiredArray(someKey) == Right(jsonLdArray))
        },
      )
    }
    val suiteWithSingleValue = {
      val jsonLdObjectWithSingleValue = JsonLDObject(Map(someKey -> stringValue))
      suite("when given a jsonLdObject with a single value")(
        test("getArray should return value in List") {
          assertTrue(jsonLdObjectWithSingleValue.getArray(someKey).contains(jsonLdArray))
        },
        test("getRequiredArray should return value in List") {
          assertTrue(jsonLdObjectWithSingleValue.getRequiredArray(someKey) == Right(jsonLdArray))
        },
      )
    }
    suiteWithArray + suiteWithSingleValue
  }

  // int value related tests
  private val intValueSuite = suite("getting int values")(
    intValueSuiteWhenGivenAnEmptyMap + intValueSuiteWhenGivenValidValue + intValueSuiteWhenGivenInvalidValue,
  )

  private def intValueSuiteWhenGivenAnEmptyMap =
    suite("when given an empty map")(
      test("getInt should return None") {
        assertTrue(emptyJsonLdObject.getInt(someKey) == Right(None))
      },
      test("getRequiredInt should fail with correct error message") {
        assertTrue(emptyJsonLdObject.getRequiredInt(someKey) == Left("No someKey provided"))
      },
    )

  private def intValueSuiteWhenGivenValidValue = {
    val intValue     = 42
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDInt(intValue)))
    suite("when given a valid value")(
      test("getInt should return int value") {
        assertTrue(jsonLdObject.getInt(someKey) == Right(Some(intValue)))
      },
      test("getRequiredInt return int value") {
        assertTrue(jsonLdObject.getRequiredInt(someKey) == Right(intValue))
      },
    )
  }

  private def intValueSuiteWhenGivenInvalidValue = {
    val jsonLdObject  = JsonLDObject(Map(someKey -> JsonLDBoolean(false)))
    val expectedError = "Invalid someKey: JsonLDBoolean(false) (integer expected)"
    suite("when given an invalid value")(
      test("getInt should return None") {
        assertTrue(jsonLdObject.getInt(someKey) == Left(expectedError))
      },
      test("getRequiredInt should fail with correct error message") {
        assertTrue(jsonLdObject.getRequiredInt(someKey) == Left(expectedError))
      },
    )
  }

  // boolean value related tests
  private val booleanValueSuite = suite("getting boolean values")(
    booleanValueSuiteWhenGivenAnEmptyMap + booleanValueSuiteWhenGivenValidValue + booleanValueSuiteWhenGivenInvalidValue,
  )

  private def booleanValueSuiteWhenGivenAnEmptyMap = suite("when given an empty map")(
    test("getBoolean should return None") {
      assertTrue(emptyJsonLdObject.getBoolean(someKey) == Right(None))
    },
    test("getRequiredBoolean should fail with correct error message") {
      assertTrue(emptyJsonLdObject.getRequiredBoolean(someKey) == Left("No someKey provided"))
    },
  )

  private def booleanValueSuiteWhenGivenValidValue = {
    val booleanValue = true
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDBoolean(booleanValue)))
    suite("when given a valid value")(
      test("getBoolean should return some value") {
        assertTrue(jsonLdObject.getBoolean(someKey) == Right(Some(booleanValue)))
      },
      test("getRequiredBoolean should return value") {
        assertTrue(jsonLdObject.getRequiredBoolean(someKey) == Right(booleanValue))
      },
    )
  }

  private def booleanValueSuiteWhenGivenInvalidValue = {
    val jsonLdObject  = JsonLDObject(Map(someKey -> JsonLDInt(42)))
    val expectedError = "Invalid someKey: JsonLDInt(42) (boolean expected)"
    suite("when given an empty map")(
      test("getBoolean should fail with correct error message") {
        assertTrue(jsonLdObject.getBoolean(someKey) == Left(expectedError))
      },
      test("getRequiredBoolean should fail with correct error message") {
        assertTrue(jsonLdObject.getRequiredBoolean(someKey) == Left(expectedError))
      },
    )
  }

  // id value related tests
  private val idValueSuite = suite("getting id values")(
    knoraDataIdValueSuiteWhenGivenAnEmptyMap + knoraDataIdValueSuiteWhenGivenValidValue + knoraDataIdValueSuiteWhenGivenInvalidValue,
  )

  private def knoraDataIdValueSuiteWhenGivenAnEmptyMap = suite("when given an empty map")(
    test("getIdValueAsKnoraDataIri should return None") {
      for {
        actual <- emptyJsonLdObject.getIdValueAsKnoraDataIri
      } yield assertTrue(actual.isEmpty)
    },
    test("getRequiredIdValueAsKnoraDataIri should fail with correct error message") {
      for {
        actual <- emptyJsonLdObject.getRequiredIdValueAsKnoraDataIri.exit
      } yield assertTrue(actual == Exit.fail("No @id provided"))
    },
  )

  private def knoraDataIdValueSuiteWhenGivenValidValue = {
    val validValue    = "http://rdfh.ch/0001/a-thing"
    val validSmartIri = StringFormatter.getInitializedTestInstance.toSmartIri(validValue)
    val jsonLdObject  = JsonLDObject(Map("@id" -> JsonLDString(validValue)))
    suite("when given a valid value")(
      test("getIdValueAsKnoraDataIri should return smart iri") {
        for {
          actual <- jsonLdObject.getIdValueAsKnoraDataIri
        } yield assertTrue(actual.contains(validSmartIri))
      },
      test("getRequiredIdValueAsKnoraDataIri should return smart iri") {
        for {
          actual <- jsonLdObject.getRequiredIdValueAsKnoraDataIri
        } yield assertTrue(actual == validSmartIri)
      },
    )
  }

  private def knoraDataIdValueSuiteWhenGivenInvalidValue = {
    val jsonLdObject  = JsonLDObject(Map("@id" -> JsonLDInt(42)))
    val expectedError = "Invalid @id: JsonLDInt(42) (string expected)"
    suite("when given an invalid value")(
      test("getIdValueAsKnoraDataIri should fail with correct error message") {
        for {
          actual <- jsonLdObject.getIdValueAsKnoraDataIri.exit
        } yield assertTrue(actual == Exit.fail(expectedError))
      },
      test("getRequiredIdValueAsKnoraDataIri should fail with correct error message") {
        for {
          actual <- jsonLdObject.getRequiredIdValueAsKnoraDataIri.exit
        } yield assertTrue(actual == Exit.fail(expectedError))
      },
    )
  }

  // uuid value related tests
  private val uuidValueSuite = suite("getting uuid values")(
    uuidValueSuiteGivenAnEmptyMap + uuidValueSuiteGivenValidValue + uuidValueSuiteGivenInvalidValue,
  )

  private def uuidValueSuiteGivenAnEmptyMap =
    suite("when given an empty map")(
      test("getUuid should return None") {
        for {
          actual <- emptyJsonLdObject.getUuid(someKey)
        } yield assertTrue(actual.isEmpty)
      },
    )

  private def uuidValueSuiteGivenValidValue = {
    val someUuid     = UUID.randomUUID()
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDString(UuidUtil.base64Encode(someUuid))))
    suite("when given a valid value")(
      test("getUuid should return None") {
        for {
          actual <- jsonLdObject.getUuid(someKey)
        } yield assertTrue(actual.contains(someUuid))
      },
    )
  }

  private def uuidValueSuiteGivenInvalidValue = {
    val invalid       = "not a uuid"
    val expectedError = "Invalid someKey: not a uuid"
    val jsonLdObject  = JsonLDObject(Map(someKey -> JsonLDString(invalid)))
    suite("when given an invalid value")(
      // uuid value
      test("getUuid should fail with correct error message") {
        for {
          actual <- jsonLdObject.getUuid(someKey).exit
        } yield assertTrue(actual == Exit.fail(expectedError))
      },
    )
  }

  // smartIri related tests
  private val smartIriValueSuite = suite("getting smart iri values")(
    smartIriValueSuiteGivenEmptyMap + smartIriValueSuiteGivenValidValue + smartIriValueSuiteGivenInvalidValue,
  )

  private def smartIriValueSuiteGivenEmptyMap = suite("when given an empty map")(
    test("getRequiredTypeAsKnoraApiV2ComplexTypeIri should fail with correct message") {
      for {
        actual <- emptyJsonLdObject.getRequiredTypeAsKnoraApiV2ComplexTypeIri.exit
      } yield assertTrue(actual == Exit.fail("No @type provided"))
    },
    test("getRequiredResourcePropertyApiV2ComplexValue should fail with correct message") {
      for {
        actual <- emptyJsonLdObject.getRequiredResourcePropertyApiV2ComplexValue.exit
      } yield assertTrue(actual == Exit.fail("No value submitted"))
    },
  )

  private def smartIriValueSuiteGivenValidValue = {
    val sf = StringFormatter.getInitializedTestInstance

    val typeIri                 = "http://api.knora.org/ontology/knora-api/v2#TextValue"
    val smartTypeIri            = sf.toSmartIri(typeIri)
    val jsonLdObjectWithTypeIri = JsonLDObject(Map("@type" -> JsonLDString(typeIri)))

    val propertyIri                 = "http://api.knora.org/ontology/knora-api/v2#hasText"
    val someText                    = "some text"
    val textJsonLdObject            = JsonLDObject(Map("@valueAsString" -> JsonLDString(someText)))
    val smartPropertyIri            = sf.toSmartIri(propertyIri)
    val jsonLDObjectWithPropertyIri = JsonLDObject(Map(propertyIri -> textJsonLdObject))

    suite("when given a valid value")(
      test("getRequiredTypeAsKnoraApiV2ComplexTypeIri should return smart iri") {
        for {
          actual <- jsonLdObjectWithTypeIri.getRequiredTypeAsKnoraApiV2ComplexTypeIri
        } yield assertTrue(actual == smartTypeIri)
      },
      test("getRequiredResourcePropertyApiV2ComplexValue should return smart iri") {
        for {
          actual <- jsonLDObjectWithPropertyIri.getRequiredResourcePropertyApiV2ComplexValue
        } yield assertTrue(actual == (smartPropertyIri, textJsonLdObject))
      },
    )
  }

  private def smartIriValueSuiteGivenInvalidValue = {
    def typeIriSuite = {
      val invalidTypeIri                 = "http://api.knora.org/ontology/knora-api/v2"
      val expectedError                  = s"Invalid Knora API v2 complex type IRI: $invalidTypeIri"
      val jsonLDObjectWithInvalidTypeIri = JsonLDObject(Map("@type" -> JsonLDString(invalidTypeIri)))
      suite("when given an invalid type value")(
        test("getRequiredTypeAsKnoraApiV2ComplexTypeIri should fail with correct message") {
          for {
            actual <- jsonLDObjectWithInvalidTypeIri.getRequiredTypeAsKnoraApiV2ComplexTypeIri.exit
          } yield assertTrue(actual == Exit.fail(expectedError))
        },
      )
    }

    def propertyIriSuite = {
      val hasTextPropIri    = "http://api.knora.org/ontology/knora-api/v2#hasText"
      val hasCommentPropIri = "http://api.knora.org/ontology/knora-api/v2#hasComment"
      val invalidPropIri    = "http://api.knora.org/ontology/knora-api/v2"
      val someText          = "some text"
      val textJsonLdObject  = JsonLDObject(Map("@valueAsString" -> JsonLDString(someText)))

      suite("when given an invalid property value")(
        {
          val jsonLDObjectWithMoreThanOnePropertyIri =
            JsonLDObject(Map(hasTextPropIri -> textJsonLdObject, hasCommentPropIri -> textJsonLdObject))
          val expectedError = "Only one value can be submitted per request using this route"
          suite("case more than one property")(
            test("getRequiredResourcePropertyApiV2ComplexValue should fail with correct message") {
              for {
                actual <- jsonLDObjectWithMoreThanOnePropertyIri.getRequiredResourcePropertyApiV2ComplexValue.exit
              } yield assertTrue(actual == Exit.fail(expectedError))
            },
          )
        }, {
          val jsonLDObjectWithInvalidPropertyValue = JsonLDObject(Map(invalidPropIri -> textJsonLdObject))
          val expectedError2                       = s"Invalid Knora API v2 complex property IRI: $invalidPropIri"
          suite("case invalid property iri")(
            test("getRequiredResourcePropertyApiV2ComplexValue should fail with correct message") {
              for {
                actual <- jsonLDObjectWithInvalidPropertyValue.getRequiredResourcePropertyApiV2ComplexValue.exit
              } yield assertTrue(actual == Exit.fail(expectedError2))
            },
          )
        },
      )
    }
    typeIriSuite + propertyIriSuite
  }

  private def uriValueSuite = suite("getting uri values")(
    test("given no uri getRequiredUri should fail") {
      for {
        actual <- ZIO.fromEither(emptyJsonLdObject.getRequiredUri(someKey)).exit
      } yield assertTrue(actual == Exit.fail("No someKey provided"))
    },
    test("given a String getRequiredUri should fail") {
      for {
        actual <- ZIO.fromEither(JsonLDObject(Map(someKey -> JsonLDString("String"))).getRequiredUri(someKey)).exit
      } yield assertTrue(actual == Exit.fail("Invalid someKey: JsonLDString(String) (object expected)"))
    },
    test("given a valid URI getRequiredUri should return") {
      check(Gen.fromIterable(List("xsd:anyURI", OntologyConstants.Xsd.Uri))) { typ =>
        for {
          actual <- ZIO.fromEither(
                      JsonLDObject(
                        Map(
                          someKey -> JsonLDObject(
                            Map(
                              JsonLDKeywords.TYPE  -> JsonLDString(typ),
                              JsonLDKeywords.VALUE -> JsonLDString("http://example.com"),
                            ),
                          ),
                        ),
                      ).getRequiredUri(someKey),
                    )
        } yield assertTrue(actual == URI.create("http://example.com"))
      }
    },
    test("given an invalid URI getRequiredUri should fail") {
      for {
        actual <- ZIO
                    .fromEither(
                      JsonLDObject(
                        Map(
                          someKey -> JsonLDObject(
                            Map(
                              JsonLDKeywords.TYPE  -> JsonLDString("xsd:anyURI"),
                              JsonLDKeywords.VALUE -> JsonLDString("-\\\\\not-a-uri-"),
                            ),
                          ),
                        ),
                      ).getRequiredUri(someKey),
                    )
                    .exit
      } yield assertTrue(actual == Exit.fail("Invalid URI: '-\\\\\not-a-uri-'"))
    },
    test("given a invalid type getRequiredUri should fail") {
      for {
        actual <- ZIO
                    .fromEither(
                      JsonLDObject(
                        Map(
                          someKey -> JsonLDObject(
                            Map(
                              JsonLDKeywords.TYPE  -> JsonLDString("xsd:timestamp"),
                              JsonLDKeywords.VALUE -> JsonLDString("http://example.com"),
                            ),
                          ),
                        ),
                      ).getRequiredUri(someKey),
                    )
                    .exit
      } yield assertTrue(
        actual == Exit.fail("Invalid object type for 'someKey', expected 'xsd:anyURI'"),
      )
    },
  )
}
