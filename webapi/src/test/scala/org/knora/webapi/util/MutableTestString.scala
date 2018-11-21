package org.knora.webapi.util

/**
  * Holds an optional, mutable IRI for use in tests.
  */
class MutableTestString {
    private var maybeString: Option[String] = None

    /**
      * Stores the string.
      * @param string the string to be stored.
      */
    def set(value: String): Unit = {
        maybeString = Some(value)
    }

    /**
      * Removes any stored string.
      */
    def unset(): Unit = {
        maybeString = None
    }

    /**
      * Gets the stored string, or throws an exception if the string is not set.
      * @return the stored string.
      */
    def get: String = {
        maybeString.getOrElse(throw TestUserProfileException("This test could not be run because a previous test failed"))
    }
}

/**
  * Thrown if a stored string was needed but was not set.
  */
case class TestStringException(message: String) extends Exception(message)
