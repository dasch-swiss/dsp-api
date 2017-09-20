package org.knora.webapi

/**
  * Indicates which API schema is being used in a search query.
  */
object ApiV2Schema extends Enumeration {
    val SIMPLE: Value = Value(0, "SIMPLE")
    val WITH_VALUE_OBJECTS: Value = Value(1, "WITH_VALUE_OBJECTS")
}
