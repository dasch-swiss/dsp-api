/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.rdf.model.Statement

import java.time.Instant
import scala.util.Try

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ValuesValidator

object RDFNodeOps {
  extension (node: RDFNode) {
    def getUri(): Option[String] = node.toUriResourceOption.map(_.getURI)
    def getStringLiteral(property: String): Option[String] =
      node.getStringLiteral(ResourceFactory.createProperty(property))
    def getStringLiteral(property: Property): Option[String] =
      node.getLiteral(property).flatMap(lit => Try(lit.getString).toOption)
    def getLiteral(property: Property): Option[Literal] =
      node.getObject(property).flatMap(obj => Try(obj.asLiteral()).toOption)
    def getObject(property: Property): Option[RDFNode] =
      node.getStatement(property).map(_.getObject)
    def getStatement(property: Property): Option[Statement] =
      node.toResourceOption.flatMap(r => Option(r.getProperty(property)))
    def toResourceOption: Option[Resource]    = Try(node.asResource()).toOption
    def toUriResourceOption: Option[Resource] = toResourceOption.filter(_.isURIResource)
    def getDateTimeProperty(property: Property): Either[String, Option[Instant]] =
      node
        .getLiteral(property)
        .map { lit =>
          Right(lit)
            .filterOrElse(
              _.getDatatypeURI == OntologyConstants.Xsd.DateTimeStamp,
              s"Invalid data type (should be xsd:dateTimeStamp) for value: ${lit.getLexicalForm}",
            )
            .map(_.getLexicalForm)
            .flatMap(str =>
              ValuesValidator
                .xsdDateTimeStampToInstant(str)
                .toRight(s"Invalid xsd:dateTimeStamp value: $str")
                .map(Some(_)),
            )
        }
        .fold(Right(None))(identity)
  }
}
