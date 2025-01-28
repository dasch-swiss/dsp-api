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
import org.knora.webapi.slice.ontology.domain.model.OntologyName.KnoraApi
import org.knora.webapi.slice.ontology.domain.model.OntologyName.KnoraBase

final case class OntologyName(value: String) extends AnyVal with StringValue { self =>
  def asInternal: OntologyName =
    if (self == KnoraApi) { KnoraBase }
    else { self }

  def asExternal: OntologyName =
    if (self == KnoraBase) { KnoraApi }
    else { self }

  def isBuiltIn: Boolean = OntologyName.BuiltIn.contains(self)
}
object OntologyName extends StringValueCompanion[OntologyName] {
  val KnoraApi: OntologyName     = OntologyName.unsafeFrom(OntologyConstants.KnoraApi.KnoraApiOntologyLabel)
  val KnoraBase: OntologyName    = OntologyName.unsafeFrom(OntologyConstants.KnoraBase.KnoraBaseOntologyLabel)
  val BuiltIn: Set[OntologyName] = OntologyConstants.BuiltInOntologyLabels.map(OntologyName.unsafeFrom)

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

  private def matchesRegexes(regex: List[Regex], msg: Option[String] = None): String => Validation[String, String] =
    (str: String) => {
      val msgStr = msg.getOrElse(s"must match regexes: ${regex.mkString("'", "', '", "'")}")
      Validation.fromPredicateWith(msgStr)(str)(value => regex.forall(_.matches(value)))
    }

  private def notMatchesRegex(regex: Regex, msg: Option[String]): String => Validation[String, String] =
    (str: String) => {
      val msgStr = msg.getOrElse(s"must not match regex: ${regex.toString()}")
      Validation.fromPredicateWith(msgStr)(str)(!regex.matches(_))
    }

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
      .as(OntologyName(value))
      .toEither
      .left
      .map(_.mkString(s"$typ ", ", ", "."))

  def from(str: String): Either[String, OntologyName] =
    fromValidations(
      "OntologyName",
      List(
        matchesRegexes(
          List(nCNameRegex, urlSafeRegex),
          Some(
            "starts with a letter or an underscore and is followed by one or more alphanumeric characters, underscores, or hyphens, and does not contain any other characters",
          ),
        ),
        notMatchesRegex(apiVersionNumberRegex, Some("must not start with 'v' followed by a number")),
        notContainsReservedWord(reservedWords),
        notContainKnoraIfNotInternal,
      ),
    )(str)
}
