/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import zio.Chunk
import zio.json.*
import zio.json.ast.Json

import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets

/**
 * Writes a [[ResourceFairGraph]] as Turtle by parsing the JSON-LD of [[SchemaOrgJsonLd]], so the two cannot disagree.
 * The JSON-LD's remote `https://schema.org` context is swapped for an inline one before parsing: nothing is fetched.
 */
object SchemaOrgTurtle {

  private val SchemaNamespace = "https://schema.org/"
  private val ProvNamespace   = "http://www.w3.org/ns/prov#"
  private val XsdDateTime     = "http://www.w3.org/2001/XMLSchema#dateTime"

  private def iriTerm(): Json = Json.Obj("@type" -> Json.Str("@id"))

  // Only the terms SchemaOrgJsonLd emits whose value is an IRI or a date; every other term is a plain schema.org literal.
  private val inlineContext: Json = Json.Obj(
    "@vocab"         -> Json.Str(SchemaNamespace),
    "prov"           -> Json.Str(ProvNamespace),
    "url"            -> iriTerm(),
    "contentUrl"     -> iriTerm(),
    "additionalType" -> iriTerm(),
    "dateCreated"    -> Json.Obj("@type" -> Json.Str(XsdDateTime)),
    "dateModified"   -> Json.Obj("@type" -> Json.Str(XsdDateTime)),
  )

  /** The graph's JSON-LD with its remote context replaced by the inline one. */
  def inlineContextJsonLd(g: ResourceFairGraph): Json = SchemaOrgJsonLd.render(g) match {
    case Json.Obj(fields) =>
      Json.Obj(Chunk.from(fields.map {
        case ("@context", _) => "@context" -> inlineContext
        case other           => other
      }))
    case other => other
  }

  /** The RDF model of the graph, parsed from [[inlineContextJsonLd]]. */
  def toModel(g: ResourceFairGraph): Model = {
    val model = ModelFactory.createDefaultModel()
    RDFDataMgr.read(
      model,
      ByteArrayInputStream(inlineContextJsonLd(g).toJson.getBytes(StandardCharsets.UTF_8)),
      Lang.JSONLD,
    )
    model
  }

  def render(g: ResourceFairGraph): String = {
    val model = toModel(g)
    try {
      model.removeNsPrefix("")
      model.setNsPrefix("schema", SchemaNamespace)
      model.setNsPrefix("prov", ProvNamespace)
      val out = StringWriter()
      RDFDataMgr.write(out, model, Lang.TURTLE)
      out.toString
    } finally model.close()
  }
}
