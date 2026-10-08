/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import com.apicatalog.jsonld.JsonLdError
import com.apicatalog.jsonld.JsonLdErrorCode
import com.apicatalog.jsonld.JsonLdOptions
import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.http.media.MediaType
import com.apicatalog.jsonld.loader.DocumentLoader
import com.apicatalog.jsonld.loader.DocumentLoaderOptions
import jakarta.json.JsonStructure
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.lang.LangJSONLD11

import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import scala.util.Try
import scala.util.Using

/**
 * Writes a [[ResourceFairGraph]] as Turtle by parsing the JSON-LD of [[SchemaOrgJsonLd]], so the two cannot disagree.
 * The `https://schema.org` context is resolved from a vendored copy on the classpath; any other remote document is
 * refused, so parsing never touches the network.
 */
object SchemaOrgTurtle {

  private val SchemaNamespace = "http://schema.org/"
  private val ProvNamespace   = "http://www.w3.org/ns/prov#"

  private val ContextResource = "org/knora/webapi/slice/export/fair/schemaorg-context.jsonld"
  private val ContextUrls     = Set("https://schema.org", "https://schema.org/", "http://schema.org", "http://schema.org/")

  // Parsed once; a Document is mutable, so each load wraps the shared structure in a new one.
  private lazy val schemaOrgContext: Either[String, JsonStructure] =
    Try(
      Using.resource(getClass.getClassLoader.getResourceAsStream(ContextResource))(
        JsonDocument.of(_).getJsonContent.get,
      ),
    ).toEither.left.map(e => s"Cannot read the vendored schema.org context: ${e.getMessage}")

  private object ClasspathLoader extends DocumentLoader {
    def loadDocument(url: URI, options: DocumentLoaderOptions): Document =
      if (ContextUrls.contains(url.toString))
        schemaOrgContext.fold(
          msg => throw JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED, msg),
          JsonDocument.of(MediaType.JSON_LD, _),
        )
      else throw JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, s"Refusing to fetch <$url>")
  }

  private val options = JsonLdOptions(ClasspathLoader)

  /** Parses JSON-LD whose only remote context is schema.org's. */
  def parseJsonLd(jsonLd: String): Either[String, Model] = {
    val model = ModelFactory.createDefaultModel()
    Try(
      RDFParser
        .create()
        .source(ByteArrayInputStream(jsonLd.getBytes(StandardCharsets.UTF_8)))
        .lang(Lang.JSONLD)
        .set(LangJSONLD11.JSONLD_OPTIONS, options)
        .parse(model),
    ).toEither.left.map { e =>
      model.close()
      s"Cannot parse the JSON-LD: ${e.getMessage}"
    }.map(_ => model)
  }

  def toModel(g: ResourceFairGraph): Either[String, Model] = parseJsonLd(SchemaOrgJsonLd.toJsonString(g))

  def render(g: ResourceFairGraph): Either[String, String] =
    toModel(g).map { model =>
      try {
        model.clearNsPrefixMap()
        model.setNsPrefix("schema", SchemaNamespace)
        model.setNsPrefix("prov", ProvNamespace)
        val out = StringWriter()
        RDFDataMgr.write(out, model, Lang.TURTLE)
        out.toString
      } finally model.close()
    }
}
