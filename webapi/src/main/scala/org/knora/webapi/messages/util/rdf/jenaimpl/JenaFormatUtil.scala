/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.jenaimpl

import org.apache.jena

import org.knora.webapi.IRI
import org.knora.webapi.messages.util.rdf.*

/**
 * Wraps a [[jena.riot.system.StreamRDF]] in a [[RdfStreamProcessor]].
 */
class StreamRDFAsStreamProcessor(streamRDF: jena.riot.system.StreamRDF) extends RdfStreamProcessor {

  override def start(): Unit = streamRDF.start()

  override def processNamespace(prefix: String, namespace: IRI): Unit =
    streamRDF.prefix(prefix, namespace)

  override def processStatement(statement: Statement): Unit =
    streamRDF.quad(JenaConversions.asJenaQuad(statement))

  override def finish(): Unit = streamRDF.finish()
}
