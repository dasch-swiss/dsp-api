package org.knora.webapi.messages

import dsp.errors.AssertionException
import org.knora.webapi.CoreSpec

class ValuesValidatorSpec extends CoreSpec {
  "The ValuesValidator" should {
    "not accept 2017-05-10" in {
      val dateString = "2017-05-10"
      assertThrows[AssertionException] {
        ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
      }
    }

    "accept GREGORIAN:2017" in {
      val dateString = "GREGORIAN:2017"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept GREGORIAN:2017-05" in {
      val dateString = "GREGORIAN:2017-05"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept GREGORIAN:2017-05-10" in {
      val dateString = "GREGORIAN:2017-05-10"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept GREGORIAN:2017-05-10:2017-05-12" in {
      val dateString = "GREGORIAN:2017-05-10:2017-05-12"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept GREGORIAN:500-05-10 BC" in {
      val dateString = "GREGORIAN:500-05-10 BC"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept GREGORIAN:500-05-10 AD" in {
      val dateString = "GREGORIAN:500-05-10 AD"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept GREGORIAN:500-05-10 BC:5200-05-10 AD" in {
      val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept JULIAN:50 BCE" in {
      val dateString = "JULIAN:50 BCE"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept JULIAN:1560-05 CE" in {
      val dateString = "JULIAN:1560-05 CE"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept JULIAN:217-05-10 BCE" in {
      val dateString = "JULIAN:217-05-10 BCE"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept JULIAN:2017-05-10:2017-05-12" in {
      val dateString = "JULIAN:2017-05-10:2017-05-12"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept JULIAN:2017:2017-5-12" in {
      val dateString = "JULIAN:2017:2017-5-12"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept JULIAN:500 BCE:400 BCE" in {
      val dateString = "JULIAN:500 BCE:400 BCE"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "accept GREGORIAN:10 BC:1 AD" in {
      val dateString = "GREGORIAN:10 BC:1 AD"
      ValuesValidator.validateDate(dateString).getOrElse(throw AssertionException(s"Not accepted $dateString"))
    }

    "not accept month 00" in {
      val dateString = "GREGORIAN:2017-00:2017-02"
      assertThrows[AssertionException] {
        ValuesValidator
          .validateDate(dateString)
          .getOrElse(throw AssertionException(s"month 00 in $dateString Not accepted"))
      }
    }

    "not accept day 00" in {
      val dateString = "GREGORIAN:2017-01-00"
      assertThrows[AssertionException] {
        ValuesValidator
          .validateDate(dateString)
          .getOrElse(throw AssertionException(s"day 00 in $dateString Not accepted"))
      }
    }

    "not accept year 0" in {
      val dateString = "GREGORIAN:0 BC"
      assertThrows[AssertionException] {
        ValuesValidator
          .validateDate(dateString)
          .getOrElse(throw AssertionException(s"Year 0 is Not accepted $dateString"))
      }
    }
  }
}
