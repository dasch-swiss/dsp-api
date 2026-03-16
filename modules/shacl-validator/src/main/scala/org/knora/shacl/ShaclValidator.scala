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

case class ShaclShapes(
  ontologyShapes: Chunk[RdfData],
  dataShapes: Chunk[RdfData],
)

enum ShaclValidationError {
  case LoadingError(cause: Throwable)
  case ValidationError(report: Resource)

  def message: String = this match {
    case LoadingError(cause)     => s"Error loading data for SHACL validation: ${cause.getMessage}"
    case ValidationError(report) =>
      val sw = new StringWriter()
      RDFDataMgr.write(sw, report.getModel, Lang.TURTLE)
      sw.toString
  }
}

object ShaclValidator {

  def validate(
    ontologies: NonEmptyChunk[RdfData],
    data: NonEmptyChunk[RdfData],
    shapes: ShaclShapes,
  ): IO[ShaclValidationError, Unit] =
    ZIO.attempt {
      val dataset = DatasetFactory.create()

      // Load ontologies first, then snapshot graph names → these are ontology graphs
      ontologies.foreach(source => loadIntoDataset(dataset, source))
      val ontologyGraphIris = datasetNames(dataset)

      // Load data, any new graph names are data graphs
      data.foreach(source => loadIntoDataset(dataset, source))
      val dataGraphIris = datasetNames(dataset) -- ontologyGraphIris

      (dataset, ontologyGraphIris, dataGraphIris)
    }
      .mapError(ShaclValidationError.LoadingError(_))
      .flatMap { case (dataset, ontologyGraphIris, dataGraphIris) =>
        val ontologyValidation =
          if (shapes.ontologyShapes.isEmpty) ZIO.unit
          else {
            // Validate ontology graphs with ontology shapes, using RDFS inference within ontologies
            val ontoModel   = mergeGraphs(dataset, ontologyGraphIris)
            val schemaModel = ModelFactory.createOntologyModel()
            schemaModel.add(ontoModel)
            val dataModel   = ModelFactory.createRDFSModel(schemaModel, ontoModel)
            val shapesModel = loadShapes(shapes.ontologyShapes)
            validateModel(dataModel, shapesModel)
          }

        val dataValidation =
          if (shapes.dataShapes.isEmpty || dataGraphIris.isEmpty) ZIO.unit
          else {
            // Validate data graphs with data shapes, using RDFS inference from ontology graphs
            val schemaModel = ModelFactory.createOntologyModel()
            val ontoModel   = mergeGraphs(dataset, ontologyGraphIris)
            schemaModel.add(ontoModel)
            val baseDataModel = mergeGraphs(dataset, dataGraphIris)
            val dataModel     = ModelFactory.createRDFSModel(schemaModel, baseDataModel)
            val shapesModel   = loadShapes(shapes.dataShapes)
            validateModel(dataModel, shapesModel)
          }

        ontologyValidation *> dataValidation
      }

  private def validateModel(dataModel: Model, shapesModel: Model): IO[ShaclValidationError, Unit] =
    ZIO.attempt {
      val config   = new ValidationEngineConfiguration().setValidateShapes(false)
      val report   = ValidationUtil.validateModel(dataModel, shapesModel, config)
      val conforms =
        report.getRequiredProperty(report.getModel.createProperty("http://www.w3.org/ns/shacl#conforms")).getBoolean
      (conforms, report)
    }
      .mapError(ShaclValidationError.LoadingError(_))
      .flatMap { case (conforms, report) =>
        if (conforms) ZIO.unit
        else ZIO.fail(ShaclValidationError.ValidationError(report))
      }

  private def loadIntoDataset(dataset: org.apache.jena.query.Dataset, source: RdfData): Unit =
    source match {
      case RdfData.TurtleFile(path, graphIri) =>
        val model = dataset.getNamedModel(graphIri)
        RDFDataMgr.read(model, path.toUri.toString, Lang.TURTLE)

      case RdfData.NQuadFile(path) =>
        val tmp = DatasetFactory.create()
        RDFDataMgr.read(tmp, path.toUri.toString, Lang.NQUADS)
        mergeDataset(tmp, dataset)

      case RdfData.InMemoryTurtle(content, graphIri) =>
        val model = dataset.getNamedModel(graphIri)
        RDFDataMgr.read(model, new StringReader(content), null, Lang.TURTLE)

      case RdfData.InMemoryNQuad(content) =>
        val tmp = DatasetFactory.create()
        RDFDataMgr.read(tmp, new StringReader(content), null, Lang.NQUADS)
        mergeDataset(tmp, dataset)
    }

  private def mergeDataset(source: org.apache.jena.query.Dataset, target: org.apache.jena.query.Dataset): Unit = {
    // Merge default graph
    val _ = target.getDefaultModel.add(source.getDefaultModel)
    // Merge named graphs
    val it = source.listNames()
    while (it.hasNext) {
      val name = it.next()
      val _    = target.getNamedModel(name).add(source.getNamedModel(name))
    }
  }

  private def datasetNames(dataset: org.apache.jena.query.Dataset): Set[String] = {
    val it    = dataset.listNames()
    val names = scala.collection.mutable.Set.empty[String]
    while (it.hasNext) names += it.next()
    names.toSet
  }

  private def mergeGraphs(dataset: org.apache.jena.query.Dataset, graphIris: Set[String]): Model = {
    val merged = ModelFactory.createDefaultModel()
    graphIris.foreach { iri =>
      val model = dataset.getNamedModel(iri)
      if (model != null) { val _ = merged.add(model) }
    }
    merged
  }

  private def loadShapes(sources: Chunk[RdfData]): Model = {
    val model = ModelFactory.createDefaultModel()
    sources.foreach {
      case RdfData.TurtleFile(path, _) => RDFDataMgr.read(model, path.toUri.toString, Lang.TURTLE)
      case RdfData.NQuadFile(path)     =>
        val ds = DatasetFactory.create()
        RDFDataMgr.read(ds, path.toUri.toString, Lang.NQUADS)
        ds.asDatasetGraph().find().forEachRemaining(q => model.getGraph.add(q.asTriple))
      case RdfData.InMemoryTurtle(content, _) =>
        RDFDataMgr.read(model, new StringReader(content), null, Lang.TURTLE)
      case RdfData.InMemoryNQuad(content) =>
        val ds = DatasetFactory.create()
        RDFDataMgr.read(ds, new StringReader(content), null, Lang.NQUADS)
        ds.asDatasetGraph().find().forEachRemaining(q => model.getGraph.add(q.asTriple))
    }
    model
  }
}
