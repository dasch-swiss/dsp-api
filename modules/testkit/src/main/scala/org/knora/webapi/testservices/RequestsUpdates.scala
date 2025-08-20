/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.client4.Request
import sttp.model.MediaType

object RequestsUpdates {

  type RequestUpdate[A] = Request[Either[String, A]] => Request[Either[String, A]]

  private val schemaQueryKey           = "schema"
  private val simpleSchemaValue        = "simple"
  private val xKnoraAcceptSchemaHeader = "X-Knora-Accept-Schema"

  def addSimpleSchemaHeader[A]: RequestUpdate[A] =
    _.header(xKnoraAcceptSchemaHeader, simpleSchemaValue)
  def addSimpleSchemaQueryParam[A]: RequestUpdate[A] =
    r => r.copy(uri = r.uri.addParam(schemaQueryKey, simpleSchemaValue))

  def addVersionQueryParam[A](version: String): RequestUpdate[A] =
    r => r.copy(uri = r.uri.addParam("version", version))

  def addAcceptHeader[A](accept: MediaType): RequestUpdate[A] =
    addAcceptHeader(accept.toString)
  def addAcceptHeaderTurtle[A]: RequestUpdate[A] =
    _.header("Accept", "text/turtle")
  def addAcceptHeaderRdfXml[A]: RequestUpdate[A] =
    _.header("Accept", "application/rdf+xml")
  def addAcceptHeader[A](accept: String): RequestUpdate[A] =
    _.header("Accept", accept)
}
