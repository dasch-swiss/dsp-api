package org.knora.webapi.messages.util.rdf

import zio._
import zio.test._

import dsp.errors.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object JsonLDObjectSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("JsonLDObject")(
      iriValueSuite +
        stringValueSuite +
        objectValuesSuite +
        arrayValueSuite +
        intValueSuite +
        booleanValueSuite +
        idValueSuite +
        uuidValueSuite +
        smartIriValueSuite
    ).provide(IriConverter.layer, StringFormatter.test)

  private val emptyJsonLdObject                            = JsonLDObject(Map.empty)
  private val someKey                                      = "someKey"
  private val someResourceIri                              = "http://www.knora.org/ontology/0001/anything#Thing"
  private def noValidation: (String, => Nothing) => String = (a, _) => a

  // IRI value related tests
  private val iriValueSuite = suite("getting iri values")(
    iriValueWhenGivenAnEmptyMap,
    iriValueWhenGivenCorrectValues,
    iriValueWhenGivenInvalidValues
  )

  private def iriValueWhenGivenAnEmptyMap = suite("when given an empty map")(
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
  )

  private def iriValueWhenGivenCorrectValues = {
    val jsonLdObject = JsonLDObject(Map(JsonLDKeywords.ID -> JsonLDString(someResourceIri)))
    val jsonLdObjectWithIriInObject = JsonLDObject(
      Map(someKey -> JsonLDObject(Map(JsonLDKeywords.ID -> JsonLDString(someResourceIri))))
    )
    suite("when given a correct value")(
      test("getIri returns expected value")(for {
        actual <- jsonLdObject.getIri()
      } yield assertTrue(actual == someResourceIri)),
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
          actual <- jsonLdObjectWithIriInObject.getIdIriInObject(someKey)
        } yield assertTrue(actual.contains(someResourceIri))
      }
    )
  }

  private def iriValueWhenGivenInvalidValues = {
    val invalidIdMap                = Map(JsonLDKeywords.ID -> JsonLDBoolean(true))
    val jsonLdObject                = JsonLDObject(invalidIdMap)
    val jsonLdObjectWithIriInObject = JsonLDObject(Map(someKey -> JsonLDObject(invalidIdMap)))
    suite("when given an invalid value")(
      test("getIri returns should fail with correct error message")(for {
        actual <- jsonLdObject.getIri().exit
      } yield assertTrue(actual == Exit.fail("Invalid @id: JsonLDBoolean(true) (string expected)"))),
      test("toIri should fail with a BadRequestException")(
        for {
          actual <- ZIO.attempt(jsonLdObject.toIri(noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid @id: JsonLDBoolean(true) (string expected)"))
        )
      ),
      test("maybeIriInObject should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIriInObject.maybeIriInObject(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid @id: JsonLDBoolean(true) (string expected)"))
        )
      },
      test("requireIriInObject  should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObjectWithIriInObject.requireIriInObject(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid @id: JsonLDBoolean(true) (string expected)"))
        )
      },
      test("getIdIriInObject fails with correct error messageexpected value") {
        for {
          actual <- jsonLdObjectWithIriInObject.getIdIriInObject(someKey).exit
        } yield assertTrue(
          actual == Exit.fail("Invalid @id: JsonLDBoolean(true) (string expected)")
        )
      }
    )
  }

  // String value related tests
  private val stringValueSuite = suite("getting string values")(
    stringValueWhenGivenAnEmptyMap + stringValueWhenGivenValidString + stringValueWhenGivenInvalidString
  )

  private def stringValueWhenGivenAnEmptyMap = suite("when given an empty map")(
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

  def stringValueWhenGivenValidString: Spec[Any, Serializable] = {
    val someString   = "someString"
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDString(someString)))
    suite("when given a valid String")(
      test("maybeString should return correct value") {
        assertTrue(jsonLdObject.maybeString(someKey).contains(someString))
      },
      test("maybeStringWithValidation should return  correct value") {
        assertTrue(jsonLdObject.maybeStringWithValidation(someKey, noValidation).contains(someString))
      },
      test("getString should return correct value") {
        for {
          actual <- jsonLdObject.getString(someKey)
        } yield assertTrue(actual.contains(someString))
      },
      test("requireString should return correct value") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireString(someKey))
        } yield assertTrue(actual == someString)
      },
      test("requireStringWithValidation should return correct value") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireStringWithValidation(someKey, noValidation))
        } yield assertTrue(actual == someString)
      },
      test("getRequiredString should return correct value") {
        for {
          actual <- jsonLdObject.getRequiredString(someKey)
        } yield assertTrue(actual == someString)
      }
    )
  }

  def stringValueWhenGivenInvalidString: Spec[Any, Serializable] = {
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDBoolean(true)))
    suite("when given an invalid String")(
      test("maybeString should fail with BadRequestExcpetion") {
        for {
          actual <- ZIO.attempt(jsonLdObject.maybeString(someKey)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid someKey: JsonLDBoolean(true) (string expected)"))
        )
      },
      test("maybeStringWithValidation should fail with BadRequestExcpetion") {
        for {
          actual <- ZIO.attempt(jsonLdObject.maybeStringWithValidation(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid someKey: JsonLDBoolean(true) (string expected)"))
        )
      },
      test("getString should fail with correct error message") {
        for {
          actual <- jsonLdObject.getString(someKey).exit
        } yield assertTrue(actual == Exit.fail("Invalid someKey: JsonLDBoolean(true) (string expected)"))
      },
      test("requireString should fails with BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireString(someKey)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid someKey: JsonLDBoolean(true) (string expected)"))
        )
      },
      test("requireStringWithValidation should fail with BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireStringWithValidation(someKey, noValidation)).exit
        } yield assertTrue(
          actual == Exit.fail(BadRequestException("Invalid someKey: JsonLDBoolean(true) (string expected)"))
        )
      },
      test("getRequiredString should return correct value") {
        for {
          actual <- jsonLdObject.getRequiredString(someKey).exit
        } yield assertTrue(actual == Exit.fail("Invalid someKey: JsonLDBoolean(true) (string expected)"))
      }
    )
  }

  // object  value related tests
  private val objectValuesSuite = suite("getting object values")(
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

  // array value related tests
  private val arrayValueSuite = suite("getting array values")(
    arrayValueSuiteWhenGivenEmptyMap + arrayValueSuiteWhenGivenValidArray
  )

  private def arrayValueSuiteWhenGivenEmptyMap = suite("when given an empty map")(
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
    test("getRequiredArray should fail with correct error message") {
      for {
        actual <- emptyJsonLdObject.getRequiredArray(someKey).exit
      } yield assertTrue(actual == Exit.fail("No someKey provided"))
    }
  )

  private def arrayValueSuiteWhenGivenValidArray = {
    val stringValue = JsonLDString(someResourceIri)
    val jsonLdArray = JsonLDArray(List(stringValue))
    val suiteWithArray = {
      val jsonLdObjectWithArray = JsonLDObject(Map(someKey -> jsonLdArray))
      suite("when given a jsonLdObject with an array")(
        test("maybeArray should return value in List") {
          assertTrue(jsonLdObjectWithArray.maybeArray(someKey).contains(jsonLdArray))
        },
        test("requireArray should return value in List") {
          for {
            actual <- ZIO.attempt(jsonLdObjectWithArray.requireArray(someKey))
          } yield assertTrue(actual == jsonLdArray)
        },
        test("getArray should return value in List") {
          for {
            actual <- jsonLdObjectWithArray.getArray(someKey)
          } yield assertTrue(actual.contains(jsonLdArray))
        },
        test("getRequiredArray should return value in List") {
          for {
            actual <- jsonLdObjectWithArray.getRequiredArray(someKey)
          } yield assertTrue(actual == jsonLdArray)
        }
      )
    }
    val suiteWithSingleValue = {
      val jsonLdObjectWithSingleValue = JsonLDObject(Map(someKey -> stringValue))
      suite("when given a jsonLdObject with a single value")(
        test("maybeArray should return value in List") {
          assertTrue(jsonLdObjectWithSingleValue.maybeArray(someKey).contains(jsonLdArray))
        },
        test("requireArray should return value in List") {
          for {
            actual <- ZIO.attempt(jsonLdObjectWithSingleValue.requireArray(someKey))
          } yield assertTrue(actual == jsonLdArray)
        },
        test("getArray should return value in List") {
          for {
            actual <- jsonLdObjectWithSingleValue.getArray(someKey)
          } yield assertTrue(actual.contains(jsonLdArray))
        },
        test("getRequiredArray should return value in List") {
          for {
            actual <- jsonLdObjectWithSingleValue.getRequiredArray(someKey)
          } yield assertTrue(actual == jsonLdArray)
        }
      )
    }
    suiteWithArray + suiteWithSingleValue
  }

  // int value related tests
  private val intValueSuite = suite("getting int values")(
    intValueSuiteWhenGivenAnEmptyMap + intValueSuiteWhenGivenValidValue + intValueSuiteWhenGivenInvalidValue
  )

  private def intValueSuiteWhenGivenAnEmptyMap =
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

  private def intValueSuiteWhenGivenValidValue = {
    val intValue     = 42
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDInt(intValue)))
    suite("when given a valid value")(
      test("maybeInt should return int value") {
        assertTrue(jsonLdObject.maybeInt(someKey).contains(intValue))
      },
      test("requireInt should return int value") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireInt(someKey))
        } yield assertTrue(actual == intValue)
      },
      test("getInt should return int value") {
        for {
          actual <- jsonLdObject.getInt(someKey)
        } yield assertTrue(actual.contains(intValue))
      },
      test("getRequiredInt return int value") {
        for {
          actual <- jsonLdObject.getRequiredInt(someKey)
        } yield assertTrue(actual == intValue)
      }
    )
  }

  private def intValueSuiteWhenGivenInvalidValue = {
    val jsonLdObject  = JsonLDObject(Map(someKey -> JsonLDBoolean(false)))
    val expectedError = "Invalid someKey: JsonLDBoolean(false) (integer expected)"
    suite("when given an invalid value")(
      test("maybeInt should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObject.maybeInt(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException(expectedError)))
      },
      test("requireInt should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireInt(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException(expectedError)))
      },
      test("getInt should return None") {
        for {
          actual <- jsonLdObject.getInt(someKey).exit
        } yield assertTrue(actual == Exit.fail(expectedError))
      },
      test("getRequiredInt should fail with correct error message") {
        for {
          actual <- jsonLdObject.getRequiredInt(someKey).exit
        } yield assertTrue(actual == Exit.fail(expectedError))
      }
    )
  }

  // boolean value related tests
  private val booleanValueSuite = suite("getting boolean values")(
    booleanValueSuiteWhenGivenAnEmptyMap + booleanValueSuiteWhenGivenValidValue + booleanValueSuiteWhenGivenInvalidValue
  )

  private def booleanValueSuiteWhenGivenAnEmptyMap = suite("when given an empty map")(
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

  private def booleanValueSuiteWhenGivenValidValue = {
    val booleanValue = true
    val jsonLdObject = JsonLDObject(Map(someKey -> JsonLDBoolean(booleanValue)))
    suite("when given a valid value")(
      // Boolean value
      test("maybeBoolean should return boolean value") {
        assertTrue(jsonLdObject.maybeBoolean(someKey).contains(booleanValue))
      },
      test("requireBoolean should return boolean value") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireBoolean(someKey))
        } yield assertTrue(actual)
      },
      test("getBoolean should return None") {
        for {
          actual <- jsonLdObject.getBoolean(someKey)
        } yield assertTrue(actual.contains(booleanValue))
      },
      test("getRequiredBoolean should fail with correct error message") {
        for {
          actual <- jsonLdObject.getRequiredBoolean(someKey)
        } yield assertTrue(actual)
      }
    )
  }

  private def booleanValueSuiteWhenGivenInvalidValue = {
    val jsonLdObject  = JsonLDObject(Map(someKey -> JsonLDInt(42)))
    val expectedError = "Invalid someKey: JsonLDInt(42) (boolean expected)"
    suite("when given an empty map")(
      test("maybeBoolean should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObject.maybeBoolean(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException(expectedError)))
      },
      test("requireBoolean should fail with a BadRequestException") {
        for {
          actual <- ZIO.attempt(jsonLdObject.requireBoolean(someKey)).exit
        } yield assertTrue(actual == Exit.fail(BadRequestException(expectedError)))
      },
      test("getBoolean should fail with correct error message") {
        for {
          actual <- jsonLdObject.getBoolean(someKey).exit
        } yield assertTrue(actual == Exit.fail(expectedError))
      },
      test("getRequiredBoolean should fail with correct error message") {
        for {
          actual <- jsonLdObject.getRequiredBoolean(someKey).exit
        } yield assertTrue(actual == Exit.fail(expectedError))
      }
    )
  }

  // id value related tests
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
