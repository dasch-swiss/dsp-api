/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.sparql.core.Quad
import zio.Clock
import zio.Scope
import zio.ZIO
import zio.ZLayer

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import scala.jdk.CollectionConverters.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.jena.RdfDataMgr

final case class TransformerError(message: String)

/**
 * Supplies the metadata that cannot be derived from the input JSON-LD or from `Clock`.
 *
 * @param attachedToUser    the user every imported resource and value is attached to.
 * @param attachedToProject the project every imported resource belongs to; overrides any value in the input.
 * @param permissions       the formatted `knora-base:hasPermissions` string used for every resource and value.
 */
final case class ConversionContext(
  attachedToUser: UserIri,
  attachedToProject: ProjectIri,
  permissions: String,
)

final class OntologyTransformer(sf: StringFormatter) { self =>

  /**
   * Converts a JSON-LD payload in the public Knora-API v2 representation into the internal `knora-base` graph
   * representation, written out as NQuads. The returned file is a scoped temp file: it is deleted when the caller's
   * `Scope` closes.
   */
  def toKnoraBase(rdf: Path, ctx: ConversionContext): ZIO[Scope, TransformerError, Path] =
    toInternalSchema(rdf).flatMap(restructure(_, ctx))

  /**
   * Stage 1 — streams the JSON-LD through Jena and renames every IRI from the external to the internal schema. A pure
   * IRI rename with no structural changes. Package-private so the stage-1 contract suites can target it directly.
   */
  private[ontology] def toInternalSchema(rdf: Path): ZIO[Scope, TransformerError, Path] =
    (for {
      nq <- tempFile("onto-transformer-", ".nq")
      _  <- ZIO.attemptBlocking {
             val in = new BufferedInputStream(new FileInputStream(rdf.toFile))
             try {
               val os = new BufferedOutputStream(new FileOutputStream(nq.toFile))
               try {
                 val writer = StreamRDFLib.writer(os)
                 val sink   = rewritingSink(writer)
                 sink.start()
                 try RDFParser.source(in).lang(Lang.JSONLD).parse(sink)
                 finally sink.finish()
               } finally os.close()
             } finally in.close()
           }
    } yield nq)
      .mapError(e => TransformerError(s"Failed to transform RDF: ${e.getMessage}"))

  /**
   * Stage 2 — structural transformations on the stage-1 NQuads to produce valid `knora-base`. Loads the bounded
   * intermediate file into a Jena [[Model]] so cross-triple context is available.
   */
  private def restructure(nq: Path, ctx: ConversionContext): ZIO[Scope, TransformerError, Path] =
    (for {
      now   <- Clock.instant
      model <- RdfDataMgr.loadModel(nq, Lang.NTRIPLES)
      _     <- ZIO.attempt(addResourceMetadata(model, ctx, now))
      _     <- ZIO.attempt(addValueMetadata(model, ctx, now))
      kb    <- tempFile("onto-transformer-kb-", ".nq")
      _     <- RdfDataMgr.write(model, kb, Lang.NTRIPLES)
    } yield kb)
      .mapError(e => TransformerError(s"Failed to restructure RDF: ${e.getMessage}"))

  /**
   * A temp file under the configured `tmpDatadir`, scoped to the surrounding `Scope`: created on acquire, deleted on
   * release.
   */
  private def tempFile(prefix: String, suffix: String): ZIO[Scope, Throwable, Path] =
    for {
      dir  <- AppConfig.config(_.tmpDatadir).map(Path.of(_))
      _    <- ZIO.attemptBlocking(Files.createDirectories(dir))
      file <- ZIO.acquireRelease(ZIO.attemptBlocking(Files.createTempFile(dir, prefix, suffix)))(p =>
                ZIO.attemptBlocking(Files.deleteIfExists(p)).ignore,
              )
    } yield file

  /**
   * Step 1 — synthesise the cardinality-1 `knora-base` metadata on every resource. Resources are identified by IRI
   * shape ([[ResourceIri.from]] succeeds); value nodes carry an extra `/values/` segment and are skipped here.
   *
   * `isMainResource` is intentionally not emitted: `knora-base.ttl` documents it as a SPARQL-CONSTRUCT artifact that is
   * never persisted. `lastModificationDate` is omitted on first import.
   */
  private def addResourceMetadata(model: Model, ctx: ConversionContext, now: Instant): Unit = {
    val attachedToUser    = model.createProperty(KnoraBase.AttachedToUser)
    val attachedToProject = model.createProperty(KnoraBase.AttachedToProject)
    val hasPermissions    = model.createProperty(KnoraBase.HasPermissions)
    val creationDate      = model.createProperty(KnoraBase.CreationDate)
    val isDeleted         = model.createProperty(KnoraBase.IsDeleted)

    val userResource    = model.createResource(ctx.attachedToUser.value)
    val projectResource = model.createResource(ctx.attachedToProject.value)
    val creationDateLit = model.createTypedLiteral(now.toString, XSDDatatype.XSDdateTimeStamp)
    val falseLit        = model.createTypedLiteral("false", XSDDatatype.XSDboolean)

    val resources = model.listSubjects().asScala.toList.filter { s =>
      s.isURIResource && ResourceIri.from(s.getURI).isRight
    }
    resources.foreach { r =>
      r.addProperty(attachedToUser, userResource)
      r.addProperty(attachedToProject, projectResource)
      r.addProperty(hasPermissions, ctx.permissions)
      r.addProperty(creationDate, creationDateLit)
      r.addProperty(isDeleted, falseLit)
    }
  }

  /**
   * Step 2 — synthesise the cardinality-1 `knora-base` metadata on every value. Values are identified by IRI shape
   * ([[ValueIri.from]] succeeds) and keep their input IRI; `valueHasUUID` is the IRI's own UUID segment. Any incoming
   * system metadata is dropped first so synthesized values win. `valueHasString` is deferred.
   */
  private def addValueMetadata(model: Model, ctx: ConversionContext, now: Instant): Unit = {
    val attachedToUser    = model.createProperty(KnoraBase.AttachedToUser)
    val hasPermissions    = model.createProperty(KnoraBase.HasPermissions)
    val isDeleted         = model.createProperty(KnoraBase.IsDeleted)
    val valueCreationDate = model.createProperty(KnoraBase.ValueCreationDate)
    val valueHasUUID      = model.createProperty(KnoraBase.ValueHasUUID)

    val userResource    = model.createResource(ctx.attachedToUser.value)
    val creationDateLit = model.createTypedLiteral(now.toString, XSDDatatype.XSDdateTimeStamp)
    val falseLit        = model.createTypedLiteral("false", XSDDatatype.XSDboolean)

    def asValueIri(n: RDFNode): Option[ValueIri] =
      Option.when(n.isURIResource)(n.asResource.getURI).flatMap(ValueIri.from(_).toOption)

    model.listSubjects().asScala.toList.flatMap(s => asValueIri(s).map((s, _))).foreach { case (v, iri) =>
      v.removeAll(attachedToUser)
        .removeAll(hasPermissions)
        .removeAll(valueCreationDate)
        .removeAll(valueHasUUID)
        .removeAll(isDeleted)
      v.addProperty(attachedToUser, userResource)
      v.addProperty(hasPermissions, ctx.permissions)
      v.addProperty(valueCreationDate, creationDateLit)
      v.addProperty(valueHasUUID, iri.valueId.value)
      v.addProperty(isDeleted, falseLit)
    }
  }

  private def rewritingSink(downstream: StreamRDF): StreamRDF = {
    def rewriteUri(uri: String): String =
      if (uri != null) sf.toSmartIri(uri).toInternalSchema.toString else uri

    def rewriteNode(n: Node): Node =
      if (n != null && n.isURI) NodeFactory.createURI(rewriteUri(n.getURI)) else n

    new StreamRDFBase {
      override def start(): Unit                             = downstream.start()
      override def finish(): Unit                            = downstream.finish()
      override def base(base: String): Unit                  = downstream.base(base)
      override def prefix(prefix: String, iri: String): Unit = downstream.prefix(prefix, rewriteUri(iri))
      override def triple(t: Triple): Unit                   =
        downstream.triple(
          Triple.create(rewriteNode(t.getSubject), rewriteNode(t.getPredicate), rewriteNode(t.getObject)),
        )
      override def quad(q: Quad): Unit =
        downstream.quad(
          Quad.create(
            rewriteNode(q.getGraph),
            rewriteNode(q.getSubject),
            rewriteNode(q.getPredicate),
            rewriteNode(q.getObject),
          ),
        )
    }
  }
}

object OntologyTransformer {
  val layer: ZLayer[StringFormatter, Nothing, OntologyTransformer] = ZLayer.derive[OntologyTransformer]
}
