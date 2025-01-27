/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model
import zio.prelude.Validation
import zio.prelude.ZValidation

import scala.util.matching.Regex

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

final case class OntologyName(value: String, isInternal: Boolean) extends StringValue
object OntologyName extends StringValueCompanion[OntologyName] {

  private val nCNameRegex: Regex           = "^[\\p{L}_][\\p{L}0-9_.-]*$".r
  private val urlSafeRegex: Regex          = "^[A-Za-z0-9_-]+$".r
  private val apiVersionNumberRegex: Regex = "^v[0-9]+.*$".r
  private val reservedWords: Set[String] =
    Set(
      "ontology",
      "rdf",
      "rdfs",
      "owl",
      "xsd",
      "schema",
      "shared",
      "simple",
    )

  private def matchesRegex(regex: Regex, msg: String): String => Validation[String, String] =
    (str: String) => Validation.fromPredicateWith(s"$msg and must match regex: ${regex.toString()}")(str)(regex.matches)

  private def notMatchesRegex(regex: Regex, msg: String): String => Validation[String, String] =
    (str: String) =>
      Validation.fromPredicateWith(s"$msg and must not match regex: ${regex.toString()}: $msg")(str)(!regex.matches(_))

  private def notContainsReservedWord(reservedWords: Set[String]): String => Validation[String, String] =
    (str: String) =>
      Validation.fromPredicateWith(s"must not contain reserved words: ${reservedWords.mkString(", ")}")(str)(value =>
        reservedWords.forall(word => !value.toLowerCase().contains(word.toLowerCase)),
      )

  private def notContainKnoraIfNotInternal: String => Validation[String, String] =
    (str: String) =>
      Validation.fromPredicateWith("must not contain 'knora' if not internal")(str)(value =>
        if (OntologyConstants.BuiltInOntologyLabels.contains(str)) true
        else !value.toLowerCase().contains("knora"),
      )

  private def fromValidations(
    typ: String,
    validations: List[String => Validation[String, String]],
  ): String => Either[String, OntologyName] = value =>
    ZValidation
      .validateAll(validations.map(_(value)))
      .as(OntologyName(value, OntologyConstants.BuiltInOntologyLabels.contains(value)))
      .toEither
      .left
      .map(_.mkString(s"$typ ", ", ", "."))

  def from(str: String): Either[String, OntologyName] =
    fromValidations(
      "OntologyName",
      List(
        matchesRegex(nCNameRegex, "must be a valid NCName"),
        matchesRegex(urlSafeRegex, "must be url safe"),
        notMatchesRegex(apiVersionNumberRegex, "must not start with 'v' followed by a number"),
        notContainsReservedWord(reservedWords),
        notContainKnoraIfNotInternal,
      ),
    )(str)
}
