package org.knora.webapi.messages

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.util.Try
import scala.util.matching.Regex

object ValuesValidator {
  // TODO: I think we should rename this to `ValuesConvertor` as it fits better what it's doing

  def validateBoolean(s: String): Option[Boolean] = s.toBooleanOption

  def validateInt(s: String): Option[Int] = s.toIntOption

  def validateBigDecimal(s: String): Option[BigDecimal] = Try(BigDecimal(s)).toOption

  def validateGeometryString(s: String): Option[String] = Option(s)

  /**
   * Checks that a hexadecimal color code string is valid.
   *
   * @param s a string containing a hexadecimal color code.
   * @return  [[Option]] of [[String]].
   */
  def validateColor(s: String): Option[String] = {
    val colorRegex: Regex = "^#(?:[0-9a-fA-F]{3}){1,2}$".r

    colorRegex.findFirstIn(s) match {
      case Some(v) => Option(v)
      case None    => None
    }
  }

  /**
   * Checks that the format of a date string is valid.
   *
   * @param s a date string.
   * @return  [[Option]] of [[String]].
   */
  def validateDate(s: String): Option[String] = {
    // TODO: below separators still exists in SF - think about how all consts should be distributed
    val calendarSeparator: String  = ":"
    val precisionSeparator: String = "-"

    // The expected format of a date.
    // Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
    // EE being the era: one of BC or AD
    val dateRegex: Regex = ("""^(GREGORIAN|JULIAN|ISLAMIC)""" +
      calendarSeparator +          // calendar name
      """(?:[1-9][0-9]{0,3})(""" + // year
      precisionSeparator +
      """(?!00)[0-9]{1,2}(""" + // month
      precisionSeparator +
      """(?!00)[0-9]{1,2})?)?( BC| AD| BCE| CE)?(""" + // day
      calendarSeparator +                              // separator if a period is given
      """(?:[1-9][0-9]{0,3})(""" +                     // year 2
      precisionSeparator +
      """(?!00)[0-9]{1,2}(""" + // month 2
      precisionSeparator +
      """(?!00)[0-9]{1,2})?)?( BC| AD| BCE| CE)?)?$""").r // day 2

    dateRegex.findFirstIn(s) match {
      case Some(value) => Option(value)
      case None        => None
    }
  }

  /**
   * Parses an `xsd:dateTimeStamp`.
   *
   * @param s        the string to be parsed.
   * @return [[Option]] of [[Instant]].
   */
  def xsdDateTimeStampToInstant(s: String): Option[Instant] =
    Try(Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s))).toOption

  /**
   * Parses a DSP ARK timestamp.
   *
   * @param timestampStr the string to be parsed.
   * @return [[Option]] of [[Instant]].
   */
  def arkTimestampToInstant(timestampStr: String): Option[Instant] = {
    val arkTimestampRegex: Regex = """^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(\d{1,9})?Z$""".r
    timestampStr match {
      case arkTimestampRegex(year, month, day, hour, minute, second, fraction) =>
        val nanoOfSecond: Int = Option(fraction) match {
          case None                  => 0
          case Some(definedFraction) =>
            // Pad the nano-of-second with trailing zeroes so it has 9 digits, then convert it to an integer.
            definedFraction.padTo(9, '0').toInt
        }
        Try(
          Instant.from(
            OffsetDateTime.of(
              year.toInt,
              month.toInt,
              day.toInt,
              hour.toInt,
              minute.toInt,
              second.toInt,
              nanoOfSecond,
              ZoneOffset.UTC
            )
          )
        ).toOption
      case _ => None
    }
  }

  /**
   * Turn a possibly empty string value into a boolean value.
   *
   * Returns
   *  - `false` if None is provided
   *  - the boolean value, if the string represents a boolean value
   *  - `None` if the string does not represent a boolean value
   *
   * @param maybe    an optional string representation of a boolean value.
   * @return [[Option]] of [[Boolean]].
   */
  def optionStringToBoolean(maybe: Option[String]): Option[Boolean] =
    maybe match {
      case Some(value) => value.toBooleanOption
      case None        => Some(false)
    }

  /**
   * Checks that a name is valid as a project-specific ontology name.
   *
   * @param ontologyName the ontology name to be checked.
   */
  def validateProjectSpecificOntologyName(ontologyName: String): Option[String] = {
    // TODO: below separators still exists in SF - think about how all consts should be distributed

    val nCNamePattern = """[\p{L}_][\p{L}0-9_.-]*"""
    val nCNameRegex   = ("^" + nCNamePattern + "$").r
    val isNCName      = nCNameRegex.matches(ontologyName)

    val base64UrlPattern      = "[A-Za-z0-9_-]+"
    val base64UrlPatternRegex = ("^" + base64UrlPattern + "$").r
    val isUrlSafe             = base64UrlPatternRegex.matches(ontologyName)

    val apiVersionNumberRegex = "^v[0-9]+.*$".r
    val isNotAVersionNumber   = !apiVersionNumberRegex.matches(ontologyName.toLowerCase())

    val isNotABuiltInOntology = !OntologyConstants.BuiltInOntologyLabels.contains(ontologyName)

    val versionSegmentWords = Set("simple", "v2")
    val reservedIriWords =
      Set("knora", "ontology", "rdf", "rdfs", "owl", "xsd", "schema", "shared") ++ versionSegmentWords
    val isNotReservedIriWord =
      reservedIriWords.forall(reserverdWord => !ontologyName.toLowerCase().contains(reserverdWord))

    if (
      isNCName &&
      isUrlSafe &&
      isNotAVersionNumber &&
      isNotABuiltInOntology &&
      isNotReservedIriWord
    ) Some(ontologyName)
    else None
  }

}
