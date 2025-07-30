/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.domain.model

object DomainTypes {

  opaque type ProjectIri       = String
  opaque type OntologyIri      = String
  opaque type ListIri          = String
  opaque type ClassIri         = String
  opaque type ProjectShortcode = String
  opaque type ProjectShortname = String
  opaque type LanguageCode     = String

  object ProjectIri {
    // Projects: http://rdfh.ch/projects/PROJECT_UUID (lenient on UUID format)
    private val projectIriPattern = """^http://rdfh\.ch/projects/.+$""".r

    def from(value: String): Either[String, ProjectIri] =
      projectIriPattern.findFirstIn(value) match {
        case Some(_) => Right(value)
        case None    => Left(s"Invalid project IRI format. Expected 'http://rdfh.ch/projects/PROJECT_UUID', got: $value")
      }

    def unsafeFrom(value: String): ProjectIri = value

    extension (iri: ProjectIri) def value: String = iri
  }

  object OntologyIri {
    // Internal ontology IRIs: http://www.knora.org/ontology/PROJECT_SHORTCODE/ONTOLOGY_NAME
    // or shared: http://www.knora.org/ontology/shared/ONTOLOGY_NAME
    private val internalOntologyPattern =
      """^http://www\.knora\.org/ontology/(?:[0-9A-F]{4}|shared)/[a-zA-Z][a-zA-Z0-9_-]*$""".r

    def from(value: String): Either[String, OntologyIri] =
      internalOntologyPattern.findFirstIn(value) match {
        case Some(_) => Right(value)
        case None =>
          Left(
            s"Invalid internal ontology IRI format. Expected 'http://www.knora.org/ontology/SHORTCODE/NAME' or 'http://www.knora.org/ontology/shared/NAME', got: $value",
          )
      }

    def unsafeFrom(value: String): OntologyIri = value

    extension (iri: OntologyIri) def value: String = iri
  }

  object ListIri {
    // Lists: http://rdfh.ch/lists/PROJECT_SHORTCODE/LIST_UUID (lenient on UUID format)
    private val listIriPattern = """^http://rdfh\.ch/lists/[0-9A-F]{4}/.+$""".r

    def from(value: String): Either[String, ListIri] =
      listIriPattern.findFirstIn(value) match {
        case Some(_) => Right(value)
        case None =>
          Left(s"Invalid list IRI format. Expected 'http://rdfh.ch/lists/PROJECT_SHORTCODE/LIST_UUID', got: $value")
      }

    def unsafeFrom(value: String): ListIri = value

    extension (iri: ListIri) def value: String = iri
  }

  object ClassIri {
    // Classes (ontology entities): http://www.knora.org/ontology/PROJECT_SHORTCODE/ONTOLOGY_NAME#CLASS_NAME
    // or built-in: http://www.knora.org/ontology/knora-base#ClassName
    // or shared: http://www.knora.org/ontology/shared/ONTOLOGY_NAME#CLASS_NAME
    private val classIriPattern =
      """^http://www\.knora\.org/ontology/(?:[0-9A-F]{4}|shared|knora-base)/[a-zA-Z][a-zA-Z0-9_-]*#[a-zA-Z][a-zA-Z0-9_-]*$""".r

    def from(value: String): Either[String, ClassIri] =
      classIriPattern.findFirstIn(value) match {
        case Some(_) => Right(value)
        case None =>
          Left(
            s"Invalid class IRI format. Expected 'http://www.knora.org/ontology/SHORTCODE/ONTOLOGY#CLASS', got: $value",
          )
      }

    def unsafeFrom(value: String): ClassIri = value

    extension (iri: ClassIri) def value: String = iri
  }

  object ProjectShortcode {
    // Project shortcode: 4-character uppercase hex string (e.g., "0001", "08FF")
    private val shortcodePattern = """^[0-9A-F]{4}$""".r

    def from(value: String): Either[String, ProjectShortcode] =
      shortcodePattern.findFirstIn(value) match {
        case Some(_) => Right(value)
        case None    => Left(s"Invalid project shortcode format. Expected 4-character uppercase hex string, got: $value")
      }

    def unsafeFrom(value: String): ProjectShortcode = value

    extension (shortcode: ProjectShortcode) def value: String = shortcode
  }

  object ProjectShortname {
    // Project shortname: must be a valid XML NCName (starts with letter/underscore, followed by letters/digits/hyphens/underscores/periods)
    private val ncNamePattern = """^[a-zA-Z_][a-zA-Z0-9_\-\.]*$""".r

    def from(value: String): Either[String, ProjectShortname] =
      if (value.nonEmpty && ncNamePattern.matches(value)) {
        Right(value)
      } else {
        Left(s"Invalid project shortname format. Must be a valid XML NCName, got: $value")
      }

    def unsafeFrom(value: String): ProjectShortname = value

    extension (shortname: ProjectShortname) def value: String = shortname
  }

  object LanguageCode {
    // ISO 639-1 language codes: 2-letter lowercase codes (e.g., "en", "de", "fr")
    private val languageCodePattern = """^[a-z]{2}$""".r

    def from(value: String): Either[String, LanguageCode] =
      languageCodePattern.findFirstIn(value) match {
        case Some(_) => Right(value)
        case None    => Left(s"Invalid language code format. Expected ISO 639-1 code (2 lowercase letters), got: $value")
      }

    def unsafeFrom(value: String): LanguageCode = value

    extension (code: LanguageCode) def value: String = code
  }

  // Type alias for multilingual content - maps language codes to text
  type MultilingualText = Map[LanguageCode, String]

  object MultilingualText {
    def from(textMap: Map[String, String]): Either[String, MultilingualText] = {
      val validatedEntries = textMap.toList.map { case (lang, text) =>
        LanguageCode.from(lang).map(_ -> text)
      }

      val errors = validatedEntries.collect { case Left(error) => error }
      if (errors.nonEmpty) {
        Left(s"Invalid language codes: ${errors.mkString(", ")}")
      } else {
        val validated = validatedEntries.collect { case Right(entry) => entry }.toMap
        Right(validated)
      }
    }

    def unsafeFrom(textMap: Map[String, String]): MultilingualText =
      textMap.map { case (lang, text) => LanguageCode.unsafeFrom(lang) -> text }

    def toMap(multilingualText: MultilingualText): Map[String, String] =
      multilingualText.map { case (lang, text) => (lang: String) -> text }
  }
}
