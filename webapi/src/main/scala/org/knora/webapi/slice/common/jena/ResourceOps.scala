/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.Statement
import org.apache.jena.vocabulary.RDF

import java.time.Instant

import org.knora.webapi.slice.common.jena.StatementOps.*

object ResourceOps {

  extension (res: Resource) {
    def statementOption(p: Property): Option[Statement] = Option(res.getProperty(p))
    def statement(p: Property): Either[String, Statement] =
      statementOption(p).toRight(s"Required property not found ${p.getURI}")

    private inline def fromStatement[A](p: Property, f: Statement => Either[String, A]): Either[String, Option[A]] =
      statementOption(p) match
        case Some(stmt) => f.apply(stmt).map(Some(_))
        case None       => Right(None)

    def objectBigDecimal(p: Property): Either[String, BigDecimal]               = statement(p).flatMap(_.objectAsBigDecimal)
    def objectBigDecimalOption(p: Property): Either[String, Option[BigDecimal]] = fromStatement(p, _.objectAsBigDecimal)

    def objectBoolean(p: Property): Either[String, Boolean]               = statement(p).flatMap(_.objectAsBoolean)
    def objectBooleanOption(p: Property): Either[String, Option[Boolean]] = fromStatement(p, _.objectAsBoolean)

    def objectInstant(p: Property): Either[String, Instant]               = statement(p).flatMap(_.objectAsInstant)
    def objectInstantOption(p: Property): Either[String, Option[Instant]] = fromStatement(p, _.objectAsInstant)

    def objectInt(p: Property): Either[String, Int]               = statement(p).flatMap(_.objectAsInt)
    def objectIntOption(p: Property): Either[String, Option[Int]] = fromStatement(p, _.objectAsInt)

    def objectString(p: Property): Either[String, String]               = statement(p).flatMap(_.objectAsString)
    def objectStringOption(p: Property): Either[String, Option[String]] = fromStatement(p, _.objectAsString)

    def objectUri(p: Property): Either[String, String]               = statement(p).flatMap(stmt => stmt.objectAsUri)
    def objectUriOption(p: Property): Either[String, Option[String]] = fromStatement(p, _.objectAsUri)

    def rdfsType(): Option[String] = Option(res.getPropertyResourceValue(RDF.`type`)).flatMap(_.uri)
    def uri: Option[String]        = Option(res.getURI)
  }
}
