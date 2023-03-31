package org.knora.webapi

import zio.test.Assertion._
import zio.test._

import java.time.Instant

import org.knora.webapi.messages.ValuesValidator

object ValuesValidatorSpec extends ZIOSpecDefault {

  val booleanSuite = suite("validateBoolean")(
    test("parse `true`") {
      val res = ValuesValidator.validateBoolean("true")
      assert(res)(isSome(isTrue))
    },
    test("parse `false`") {
      val res = ValuesValidator.validateBoolean("false")
      assert(res)(isSome(isFalse))
    },
    test("not parse invalid input") {
      val res = ValuesValidator.validateBoolean("foo")
      assert(res)(isNone)
    }
  )

  val integerSuite = suite("validateInt")(
    test("parse `1`") {
      val res = ValuesValidator.validateInt("1")
      assert(res)(isSome(equalTo(1)))
    },
    test("not parse invalid input") {
      val res = ValuesValidator.validateInt("1.1")
      assert(res)(isNone)
    }
  )

  val bigDecimalSuite = suite("validateBigDecimal")(
    test("parse `1`") {
      val res = ValuesValidator.validateBigDecimal("1")
      assert(res)(isSome(equalTo(BigDecimal(1))))
    },
    test("parse `1.1`") {
      val res = ValuesValidator.validateBigDecimal("1.1")
      assert(res)(isSome(equalTo(BigDecimal(1.1))))
    },
    test("parse `1.111e10`") {
      val res = ValuesValidator.validateBigDecimal("1.111e10")
      assert(res)(isSome(equalTo(BigDecimal(1.111e10))))
    },
    test("not parse invalid input") {
      val res = ValuesValidator.validateBigDecimal("false")
      assert(res)(isNone)
    }
  )

  val geometrySuite = suite("validateGeometryString")(
    test("parse valid JSON") {
      val jsonString = """|
                          |{
                          |    "status": "active",
                          |    "type": "rectangle",
                          |    "lineColor": "#ff1100",
                          |    "lineWidth": 5,
                          |    "points": [
                          |        {"x":0.1,"y":0.7},
                          |        {"x":0.3,"y":0.2}
                          |    ]
                          |}
                          |""".stripMargin
      val res = ValuesValidator.validateGeometryString(jsonString)
      assert(res)(isSome(equalTo(jsonString)))
    },
    test("not parse invalid JSON") {
      val res = ValuesValidator.validateGeometryString("not valid json")
      assert(res)(isNone)
    }
  )

  val colorSuite = suite("validateColor")(
    test("parse a 3-digit hexadecimal color code") {
      val colorCode = "#fff"
      val res       = ValuesValidator.validateColor(colorCode)
      assert(res)(isSome(equalTo(colorCode)))
    },
    test("parse a 6-digit hexadecimal color code with lower case letters") {
      val colorCode = "#3a3a3a"
      val res       = ValuesValidator.validateColor(colorCode)
      assert(res)(isSome(equalTo(colorCode)))
    },
    test("parse a 6-digit hexadecimal color code with upper case letters") {
      val colorCode = "#1F1F1F"
      val res       = ValuesValidator.validateColor(colorCode)
      assert(res)(isSome(equalTo(colorCode)))
    },
    test("not parse a a non-hexadecimal color code") {
      val res = ValuesValidator.validateColor("rgb(127,255,0)")
      assert(res)(isNone)
    }
  )

  val dateSuite = suite("validateDate")(
    suite("not accept date")(
      test("2017-05-10") {
        val dateString = "2017-05-10"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isNone)
      },
      test("GREGORIAN:2017-00:2017-02") {
        val dateString = "GREGORIAN:2017-00:2017-02"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isNone)
      },
      test("GREGORIAN:2017-01-00") {
        val dateString = "GREGORIAN:2017-01-00"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isNone)
      },
      test("GREGORIAN:0 BC") {
        val dateString = "GREGORIAN:0 BC"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isNone)
      }
    ),
    suite("accept date")(
      test("GREGORIAN:2017") {
        val dateString = "GREGORIAN:2017"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("GREGORIAN:2017-05") {
        val dateString = "GREGORIAN:2017-05"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("GREGORIAN:2017-05-10") {
        val dateString = "GREGORIAN:2017-05-10"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("GREGORIAN:2017-05-10:2017-05-12") {
        val dateString = "GREGORIAN:2017-05-10:2017-05-12"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("GREGORIAN:500-05-10 BC") {
        val dateString = "GREGORIAN:500-05-10 BC"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("GREGORIAN:500-05-10 AD") {
        val dateString = "GREGORIAN:500-05-10 AD"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("GREGORIAN:500-05-10 BC:5200-05-10 AD") {
        val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("JULIAN:50 BCE") {
        val dateString = "JULIAN:50 BCE"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("JULIAN:1560-05 CE") {
        val dateString = "JULIAN:1560-05 CE"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("JULIAN:217-05-10 BCE") {
        val dateString = "JULIAN:217-05-10 BCE"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("JULIAN:2017-05-10:2017-05-12") {
        val dateString = "JULIAN:2017-05-10:2017-05-12"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("JULIAN:2017:2017-5-12") {
        val dateString = "JULIAN:2017:2017-5-12"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("JULIAN:500 BCE:400 BCE") {
        val dateString = "JULIAN:500 BCE:400 BCE"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      },
      test("GREGORIAN:10 BC:1 AD") {
        val dateString = "GREGORIAN:10 BC:1 AD"
        val res        = ValuesValidator.validateDate(dateString)
        assert(res)(isSome(equalTo(dateString)))
      }
    )
  )

  val xsdTimestampSuite = suite("xsdDateTimeStampToInstant")(
    test("parse a valid timestamp") {
      val timestamp = "2018-06-04T08:56:22Z"
      val instant   = Instant.parse(timestamp)
      val res       = ValuesValidator.xsdDateTimeStampToInstant(timestamp)
      assert(res)(isSome(equalTo(instant)))
    },
    test("not parse an invalid timestamp") {
      val timestamp = "2018-06-04T08:xy:22Z"
      val res       = ValuesValidator.xsdDateTimeStampToInstant(timestamp)
      assert(res)(isNone)
    }
  )

  val arkTimestampSuite =
    suite("arkTimestampToInstant")(
      test("accept ARK timestamp with a fractional part") {
        val dateString = "20180604T0856229876543Z"
        val res        = ValuesValidator.arkTimestampToInstant(dateString)
        assert(res)(isSome(equalTo(Instant.parse("2018-06-04T08:56:22.9876543Z"))))
      },
      test("accept ARK timestamp with a leading zero") {
        val dateString = "20180604T085622098Z"
        val res        = ValuesValidator.arkTimestampToInstant(dateString)
        assert(res)(isSome(equalTo(Instant.parse("2018-06-04T08:56:22.098Z"))))
      },
      test("accept ARK timestamp without a fractional part") {
        val dateString = "20180604T085622Z"
        val res        = ValuesValidator.arkTimestampToInstant(dateString)
        assert(res)(isSome(equalTo(Instant.parse("2018-06-04T08:56:22Z"))))
      }
    )

  val optionStringToBooleanSuite = suite("optionStringToBoolean")(
    test("parse true") {
      val res = ValuesValidator.optionStringToBoolean(Some("true"))
      assert(res)(isSome(isTrue))
    },
    test("parse false") {
      val res = ValuesValidator.optionStringToBoolean(Some("false"))
      assert(res)(isSome(isFalse))
    },
    test("parse None to None") {
      val res = ValuesValidator.optionStringToBoolean(None)
      assertTrue(res.isEmpty)
    },
    test("not parse an invalid boolean") {
      val res = ValuesValidator.optionStringToBoolean(Some(""))
      assert(res)(isNone)
    },
    test("provide the fallback value for and invalid string") {
      val res = ValuesValidator.optionStringToBoolean(Some("invalid"), fallback = true)
      assertTrue(res)
    }
  )

  override def spec = suite("ValuesValidator")(
    booleanSuite,
    integerSuite,
    bigDecimalSuite,
    geometrySuite,
    colorSuite,
    dateSuite,
    xsdTimestampSuite,
    arkTimestampSuite,
    optionStringToBooleanSuite
  )
}
