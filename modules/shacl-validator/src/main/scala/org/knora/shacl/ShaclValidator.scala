/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.shacl

import org.apache.jena.graph.Node
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.RDFParserBuilder
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.sparql.core.Quad
import org.topbraid.shacl.validation.ValidationEngineConfiguration
import org.topbraid.shacl.validation.ValidationUtil
import zio.*

import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Path

// graphIri on TurtleFile/InMemoryTurtle: Turtle has no named-graph semantics, so graphIri is
// not used during loading. It is kept as metadata because all data in dsp-api belongs to a
// named graph, and this information may be used for future performance optimizations.
enum RdfData {
  case TurtleFile(path: Path, graphIri: String)
  case NQuadFile(path: Path)
  case InMemoryTurtle(triplesTtl: String, graphIri: String)
  case InMemoryNQuad(quads: String)
}

case class RdfGraphs(ontologies: NonEmptyChunk[RdfData], data: NonEmptyChunk[RdfData])

case class ShaclShapes(
  ontologyShapes: NonEmptyChunk[RdfData],
  dataShapes: NonEmptyChunk[RdfData],
)

enum ShaclValidationError {
  case LoadingError(cause: Throwable)
  case OntologyValidationError(reportTtl: String)
  case DataValidationError(reportTtl: String)

  def message: String = this match {
    case LoadingError(cause)                => s"Error loading data for SHACL validation: ${cause.getMessage}"
    case OntologyValidationError(reportTtl) => s"Ontology check failed:\n$reportTtl"
    case DataValidationError(reportTtl)     => s"Data check failed:\n$reportTtl"
  }
}

object ShaclValidator {

  private val defaultModel =
    ZIO.acquireRelease(ZIO.succeed(ModelFactory.createDefaultModel()))(m => ZIO.attempt(m.close()).logError.ignore)
  private val rdfsModel = defaultModel.flatMap(m =>
    ZIO.acquireRelease(ZIO.succeed(ModelFactory.createRDFSModel(m)))(m => ZIO.attempt(m.close()).logError.ignore),
  )

  def validate(
    graphs: RdfGraphs,
    shapes: ShaclShapes,
  ): IO[ShaclValidationError, Unit] = ZIO.scoped {
    for {
      rdfsModel <- rdfsModel
      // Step 1: load ontologies and validate ontology shapes
      _            <- ZIO.foreachDiscard(graphs.ontologies)(loadIntoModel(rdfsModel, _))
      shapesModel1 <- loadShapes(shapes.ontologyShapes)
      _            <- validateModel(rdfsModel, shapesModel1, ShaclValidationError.OntologyValidationError(_))
      // Step 2: load data and validate data shapes
      _            <- ZIO.foreachDiscard(graphs.data)(loadIntoModel(rdfsModel, _))
      shapesModel2 <- loadShapes(shapes.dataShapes)
      _            <- validateModel(rdfsModel, shapesModel2, ShaclValidationError.DataValidationError(_))
    } yield ()
  }

  private def loadIntoModel(model: Model, source: RdfData): IO[ShaclValidationError, Unit] =
    source match {
      case RdfData.TurtleFile(path, _) =>
        ZIO
          .attemptBlocking(RDFDataMgr.read(model, path.toUri.toString, Lang.TURTLE))
          .mapError(ShaclValidationError.LoadingError(_))
      case RdfData.NQuadFile(path) =>
        streamNQuadsIntoModel(model, RDFParser.source(path.toUri.toString).lang(Lang.NQUADS))
      case RdfData.InMemoryTurtle(content, _) =>
        ZIO
          .attemptBlocking(RDFDataMgr.read(model, new StringReader(content), null, Lang.TURTLE))
          .mapError(ShaclValidationError.LoadingError(_))
      case RdfData.InMemoryNQuad(content) =>
        streamNQuadsIntoModel(model, RDFParser.create().source(new StringReader(content)).lang(Lang.NQUADS))
    }

  /**
   * Streams NQuads directly into the model without buffering an intermediate Dataset.
   * Validates that all quads belong to exactly one named graph and none are in the default graph.
   */
  private def streamNQuadsIntoModel(
    model: Model,
    parser: RDFParserBuilder,
  ): IO[ShaclValidationError, Unit] =
    ZIO.attemptBlocking {
      val graph             = model.getGraph
      val graphNames        = scala.collection.mutable.Set.empty[Node]
      var hasDefaultTriples = false

      val sink: StreamRDF = new StreamRDFBase {
        override def quad(quad: Quad): Unit = {
          val g = quad.getGraph
          if (g == null || Quad.isDefaultGraph(g)) {
            hasDefaultTriples = true
          } else {
            graphNames += g
            graph.add(quad.asTriple)
          }
        }
        override def triple(triple: org.apache.jena.graph.Triple): Unit =
          hasDefaultTriples = true
      }

      parser.parse(sink)

      if (graphNames.size != 1 || hasDefaultTriples)
        throw new IllegalArgumentException(
          s"NQuad data must contain exactly one named graph, found ${graphNames.size} named graph(s)" +
            (if (hasDefaultTriples) " plus triples in the default graph" else ""),
        )
    }
      .mapError(ShaclValidationError.LoadingError(_))

  private def loadShapes(sources: NonEmptyChunk[RdfData]): ZIO[Scope, ShaclValidationError, Model] =
    defaultModel.flatMap { model =>
      ZIO.foreachDiscard(sources)(source => loadIntoModel(model, source)).as(model)
    }

  private def reportToTtl(report: Resource): Task[String] = ZIO.scoped {
    ZIO
      .acquireRelease(ZIO.succeed(ModelFactory.createDefaultModel()))(m => ZIO.attempt(m.close()).logError.ignore)
      .flatMap { reportModel =>
        ZIO.attemptBlocking {
          // Copy only the report node's own statements, not the entire data model
          val reportStmts = report.getModel.listStatements(report, null, null: org.apache.jena.rdf.model.RDFNode)
          while (reportStmts.hasNext) { reportModel.add(reportStmts.nextStatement()): Unit }
          // Also copy statements about each sh:result
          val shResult    = report.getModel.createProperty("http://www.w3.org/ns/shacl#result")
          val resultNodes = report.getModel.listObjectsOfProperty(report, shResult)
          while (resultNodes.hasNext) {
            val node = resultNodes.next()
            if (node.isResource) {
              val stmts =
                report.getModel.listStatements(node.asResource(), null, null: org.apache.jena.rdf.model.RDFNode)
              while (stmts.hasNext) { reportModel.add(stmts.nextStatement()): Unit }
            }
          }
          val sw = new StringWriter()
          RDFDataMgr.write(sw, reportModel, Lang.TURTLE)
          sw.toString
        }
      }
  }

  private def validateModel(
    dataModel: Model,
    shapesModel: Model,
    errorFactory: String => ShaclValidationError,
  ): IO[ShaclValidationError, Unit] =
    ZIO
      .attemptBlocking {
        val config   = new ValidationEngineConfiguration().setValidateShapes(false)
        val report   = ValidationUtil.validateModel(dataModel, shapesModel, config)
        val conforms =
          report.getRequiredProperty(report.getModel.createProperty("http://www.w3.org/ns/shacl#conforms")).getBoolean
        if (conforms) None else Some(report)
      }
      .mapError(ShaclValidationError.LoadingError(_))
      .flatMap {
        case Some(report) =>
          reportToTtl(report)
            .mapError(ShaclValidationError.LoadingError(_))
            .flatMap(ttl => ZIO.fail(errorFactory(ttl)))
        case None => ZIO.unit
      }
}
