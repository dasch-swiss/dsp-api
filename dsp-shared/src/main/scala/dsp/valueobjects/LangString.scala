package dsp.valueobjects

import dsp.errors.ValidationException
import zio.prelude.Validation

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
   * Warning: skipps all validation. Should not be used unless there is no possibility for the data to be invalid.
   *
   * @param languagea [[LanguageCode]] value object representing the language of the LangString.
   * @param value     the string value of the LangString.
   * @return a LanguageCode value object
   */
  def unsafeMake(language: LanguageCode, value: String): LangString =
    new LangString(language, value) {}
}

/**
 * LanguageCode value object.
 */
sealed abstract case class LanguageCode private (value: String)
object LanguageCode { self =>
  def make(value: String): Validation[ValidationException, LanguageCode] =
    if (value.isEmpty) {
      Validation.fail(ValidationException(UserErrorMessages.LanguageCodeMissing))
    } else if (!V2.SupportedLanguageCodes.contains(value)) {
      Validation.fail(ValidationException(UserErrorMessages.LanguageCodeInvalid))
    } else {
      Validation.succeed(new LanguageCode(value) {})
    }

  lazy val en: LanguageCode = new LanguageCode(V2.EN) {}
  lazy val de: LanguageCode = new LanguageCode(V2.DE) {}
  lazy val fr: LanguageCode = new LanguageCode(V2.FR) {}
  lazy val it: LanguageCode = new LanguageCode(V2.IT) {}
  lazy val rm: LanguageCode = new LanguageCode(V2.RM) {}
}

object LangStringErrorMessages {
  val LangStringValueEmpty = "String value cannot be empty."
}
