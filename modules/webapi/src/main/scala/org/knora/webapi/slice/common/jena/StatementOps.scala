/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.Statement

import java.time.Instant
import java.util.UUID
import scala.util.Try

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.common.jena.ResourceOps.*

object StatementOps {
  extension (stmt: Statement) {
    def subjectUri(): Option[String]                 = Option(stmt.getSubject.getURI)
    def objectAsResource(): Either[String, Resource] =
      Try(stmt.getObject.asResource()).toEither.left.map(e =>
        s"Object is not a resource: ${stmt.getPredicate} ${e.getMessage}",
      )

    def objectAsBigDecimal: Either[String, BigDecimal] =
      objectAsDataType(OntologyConstants.Xsd.Decimal).flatMap(str =>
        Try(BigDecimal(str)).toEither.left.map(_ => s"Invalid decimal value for property ${stmt.getPredicate}"),
      )

    def objectAsBoolean: Either[String, Boolean] =
      Try(stmt.getBoolean).toEither.left.map(_ => s"Invalid boolean value for property ${stmt.getPredicate}")

    def objectAsInstant: Either[String, Instant] =
      objectAsDataType(OntologyConstants.Xsd.DateTimeStamp).flatMap(str =>
        Try(Instant.parse(str)).toEither.left.map(_ =>
          s"Invalid date time timestamp value for property ${stmt.getPredicate}",
        ),
      )

    def objectAsInt: Either[String, Int] =
      Try(stmt.getInt).toEither.left.map(_ => s"Invalid integer value for property ${stmt.getPredicate}")

    def objectAsString: Either[String, String] =
      Try(stmt.getString).toEither.left.map(_ => s"Invalid string value for property ${stmt.getPredicate}")

    def objectAsUri: Either[String, String] =
      objectAsResource().flatMap(_.uri.toRight(s"Invalid URI value for property ${stmt.getPredicate}"))

    def objectAsUuid: Either[String, UUID] =
      objectAsString.flatMap(str =>
        UuidUtil.base64Decode(str).toEither.left.map(e => s"Invalid UUID '$str': ${e.getMessage}"),
      )

    def objectAsDataType(dataTypeUri: String): Either[String, String] =
      stmt.getObject match
        case l: Literal =>
          l.getDatatypeURI match
            case `dataTypeUri` => Right(l.getLexicalForm)
            case _             => Left(s"Invalid datatype for property ${stmt.getPredicate}, ${dataTypeUri} expected")

    def predicateUri: String = stmt.getPredicate.getURI
  }
}
