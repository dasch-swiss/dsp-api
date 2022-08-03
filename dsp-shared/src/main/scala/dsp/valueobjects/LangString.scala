package dsp.valueobjects

import dsp.errors.ValidationException
import zio.prelude.Validation
import zio._

/**
 * LangString value object
 */
sealed abstract case class LangString private (language: LanguageCode, value: String)

object LangString {

  /**
   * Creates a [[zio.prelude.Validation]] that either fails with a ValidationException or succeeds with a LangString value object.
   *
   * @param language a [[LanguageCode]] value object representing the language of the LangString.
   * @param value    the string value of the LangString.
   * @return a Validation of a LangString value object.
   */
  def make(language: LanguageCode, value: String): Validation[ValidationException, LangString] =
    if (value.isBlank()) {
      Validation.fail(ValidationException(LangStringErrorMessages.LangStringValueEmpty))
    } else {
      Validation.succeed(new LangString(language, value) {})
    }

  /**
   * Creates a [[zio.prelude.Validation]] that either fails with a ValidationException or succeeds with a LangString value object.
   *
   * @param language a two-digit language code string representing the language of the LangString.
   * @param value    the string value of the LangString.
   * @return a Validation of a LangString value object.
   */
  def makeFromStrings(language: String, value: String): Validation[ValidationException, LangString] =
    for {
      languageCode <- LanguageCode.make(language)
      langString   <- LangString.make(languageCode, value)
    } yield langString

  /**
   * Unsafely creates a LangString value object.
   * Warning: skips all validation. Should not be used unless there is no possibility for the data to be invalid.
   *
   * @param languagea [[LanguageCode]] value object representing the language of the LangString.
   * @param value     the string value of the LangString.
   * @return a LanguageCode value object
   */
  def unsafeMake(language: LanguageCode, value: String): LangString =
    LangString
      .make(language = language, value = value)
      .fold(
        e => {
          val unsafe = new LangString(language, value) {}
          ZIO.logWarning(s"Called unsafeMake() for an invalid $unsafe: $e") // TODO-BL: get this to actually log
          unsafe
        },
        langString => langString
      )
}

/**
 * MultiLangString value object
 */
sealed abstract case class MultiLangString private (langStrings: Set[LangString])

object MultiLangString {

  /**
   * Creates a [[zio.prelude.Validation]] that either fails with a ValidationException, or succeeds with a MultiLangString.
   * Ensures that the MultiLangString is not empty and that the languages are unique.
   *
   * @param values a set of LangString value objects
   * @return a validation of a MultiLangString value object
   */
  def make(values: Set[LangString]): Validation[ValidationException, MultiLangString] =
    if (values.isEmpty) {
      Validation.fail(ValidationException(MultiLangStringErrorMessages.MultiLangStringEmptySet))
    } else if (values.map(_.language).size < values.size) {
      val nonUnique = values.toList.map(_.language).groupBy(identity).mapValues(_.size).filter(_._2 > 1).keys.toSet
      Validation.fail(ValidationException(MultiLangStringErrorMessages.LanguageNotUnique(nonUnique)))
    } else {
      Validation.succeed(new MultiLangString(values) {})
    }
}

object LangStringErrorMessages {
  val LangStringValueEmpty = "String value cannot be empty."
}

object MultiLangStringErrorMessages {
  val MultiLangStringEmptySet = "MultiLangString must consist of at least one LangStirng."
  val LanguageNotUnique = (nonUniqueLanguages: Set[LanguageCode]) => {
    val issuesString = nonUniqueLanguages.toList.map(_.value).sorted
    s"Each Language must only appear once. $issuesString"
  }
}
