/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.shacl

import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.topbraid.shacl.validation.ValidationEngineConfiguration
import org.topbraid.shacl.validation.ValidationUtil
import zio.*

import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Path

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
  case OntologyValidationError(report: Resource)
  case DataValidationError(report: Resource)

  def message: String = this match {
    case LoadingError(cause)             => s"Error loading data for SHACL validation: ${cause.getMessage}"
    case OntologyValidationError(report) =>
      val sw = new StringWriter()
      sw.write("Ontology check failed:\n")
      asTtlStr(sw, report)
    case DataValidationError(report) =>
      val sw = new StringWriter()
      sw.write("Data check failed:\n")
      asTtlStr(sw, report)
  }

  private def asTtlStr(sw: StringWriter, report: Resource) =
    RDFDataMgr.write(sw, report.getModel, Lang.TURTLE)
    sw.toString
}

object ShaclValidator {

  def validate(
    graphs: RdfGraphs,
    shapes: ShaclShapes,
  ): IO[ShaclValidationError, Unit] = {
    val model = ModelFactory.createDefaultModel()
    for {
      // Step 1: load ontologies and validate ontology shapes
      _            <- ZIO.foreachDiscard(graphs.ontologies)(loadIntoModel(model, _))
      shapesModel1 <- loadShapes(shapes.ontologyShapes)
      _            <- validateModel(
             ModelFactory.createRDFSModel(model),
             shapesModel1,
             ShaclValidationError.OntologyValidationError(_),
           )
      // Step 2: load data and validate data shapes
      _            <- ZIO.foreachDiscard(graphs.data)(loadIntoModel(model, _))
      shapesModel2 <- loadShapes(shapes.dataShapes)
      _            <- validateModel(ModelFactory.createRDFSModel(model), shapesModel2, ShaclValidationError.DataValidationError(_))
    } yield ()
  }

  private def loadIntoModel(model: Model, source: RdfData): IO[ShaclValidationError, Unit] =
    source match {
      case RdfData.TurtleFile(path, _) =>
        ZIO
          .attempt(RDFDataMgr.read(model, path.toUri.toString, Lang.TURTLE))
          .mapError(ShaclValidationError.LoadingError(_))
      case RdfData.NQuadFile(path) =>
        ZIO.attempt {
          val ds = DatasetFactory.create()
          RDFDataMgr.read(ds, path.toUri.toString, Lang.NQUADS)
          ds
        }
          .mapError(ShaclValidationError.LoadingError(_))
          .flatMap(ds => loadNQuadsIntoModel(model, ds))
      case RdfData.InMemoryTurtle(content, _) =>
        ZIO
          .attempt(RDFDataMgr.read(model, new StringReader(content), null, Lang.TURTLE))
          .mapError(ShaclValidationError.LoadingError(_))
      case RdfData.InMemoryNQuad(content) =>
        ZIO.attempt {
          val ds = DatasetFactory.create()
          RDFDataMgr.read(ds, new StringReader(content), null, Lang.NQUADS)
          ds
        }
          .mapError(ShaclValidationError.LoadingError(_))
          .flatMap(ds => loadNQuadsIntoModel(model, ds))
    }

  private def loadNQuadsIntoModel(
    model: Model,
    ds: org.apache.jena.query.Dataset,
  ): IO[ShaclValidationError, Unit] = {
    val it    = ds.listNames()
    val names = scala.collection.mutable.ListBuffer.empty[String]
    while (it.hasNext) names += it.next()
    if (names.size != 1 || !ds.getDefaultModel.isEmpty)
      ZIO.fail(
        ShaclValidationError.LoadingError(
          new IllegalArgumentException(
            s"NQuad data must contain exactly one named graph, found ${names.size} named graph(s)" +
              (if (!ds.getDefaultModel.isEmpty) " plus triples in the default graph" else ""),
          ),
        ),
      )
    else
      ZIO.attempt {
        ds.asDatasetGraph().find().forEachRemaining(q => model.getGraph.add(q.asTriple))
      }.mapError(ShaclValidationError.LoadingError(_))
  }

  private def loadShapes(sources: NonEmptyChunk[RdfData]): IO[ShaclValidationError, Model] = {
    val model = ModelFactory.createDefaultModel()
    ZIO.foreachDiscard(sources)(source => loadIntoModel(model, source)).as(model)
  }

  private def validateModel(
    dataModel: Model,
    shapesModel: Model,
    errorFactory: Resource => ShaclValidationError,
  ): IO[ShaclValidationError, Unit] =
    ZIO.attempt {
      val config   = new ValidationEngineConfiguration().setValidateShapes(false)
      val report   = ValidationUtil.validateModel(dataModel, shapesModel, config)
      val conforms =
        report.getRequiredProperty(report.getModel.createProperty("http://www.w3.org/ns/shacl#conforms")).getBoolean
      (conforms, report)
    }
      .mapError(ShaclValidationError.LoadingError(_))
      .flatMap { case (conforms, report) => ZIO.unless(conforms)(ZIO.fail(errorFactory(report))).unit }
}
