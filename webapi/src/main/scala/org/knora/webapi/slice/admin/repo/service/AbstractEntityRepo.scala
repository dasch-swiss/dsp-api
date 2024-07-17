/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.common.net.ParsedIRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfSubject
import zio.Chunk
import zio.IO
import zio.NonEmptyChunk
import zio.Task
import zio.ZIO
import zio.stream.ZStream

import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.service.CrudRepository
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

trait EntityWithId[Id <: StringValue] {
  def id: Id
}

trait RdfEntityMapper[E] {

  def toTriples(entity: E): TriplePattern
  def toEntity(resource: RdfResource): IO[RdfError, E]

}

final case class EntityProperties(req: NonEmptyChunk[Iri], opt: Chunk[Iri] = Chunk.empty) {
  def all: Chunk[Iri] = req ++ opt
}

abstract class AbstractEntityRepo[E <: EntityWithId[Id], Id <: StringValue](
  triplestore: TriplestoreService,
  mapper: RdfEntityMapper[E],
) extends CrudRepository[E, Id] {
  self =>

  protected def resourceClass: ParsedIRI
  protected def namedGraphIri: Iri
  protected def entityProperties: EntityProperties

  override def findAll(): Task[Chunk[E]] = {
    val sub = variable("s")
    findAllByQuery(Construct(entityQuery(tripleP(sub), graphP(sub)).getQueryString))
  }

  override def findById(id: Id): Task[Option[E]] = {
    val s             = Rdf.iri(id.value)
    val query: String = entityQuery(tripleP(s), graphP(s)).getQueryString
    findOneByQuery(Construct(query))
  }

  private def entityQuery(constructPattern: TriplePattern, where: GraphPattern) =
    Queries
      .CONSTRUCT(constructPattern)
      .prefix(Vocabulary.KnoraAdmin.NS, Vocabulary.KnoraBase.NS)
      .where(where.from(namedGraphIri))

  private def tripleP(sub: RdfSubject): TriplePattern = {
    val req: TriplePattern = requiredTriples(sub.isA(Rdf.iri(resourceClass.toString)))
    self.entityProperties.opt.zipWithIndex.foldLeft(req) { case (p, (iri, index)) =>
      p.andHas(iri, variable(s"n${index + self.entityProperties.req.size}"))
    }
  }

  private def requiredTriples(pattern: TriplePattern): TriplePattern =
    self.entityProperties.req.zipWithIndex.foldLeft(pattern) { case (p, (iri, index)) =>
      p.andHas(iri, variable(s"n$index"))
    }

  private def graphP(sub: RdfSubject): GraphPattern = {
    val req: GraphPattern = requiredTriples(sub.isA(Rdf.iri(resourceClass.toString)))
    self.entityProperties.opt.zipWithIndex.foldLeft(req) { case (p, (iri, index)) =>
      p.and(sub.has(iri, variable(s"n${index + self.entityProperties.req.size}")).optional())
    }
  }

  protected def findOneByTriplePattern(pattern: RdfSubject => TriplePattern): Task[Option[E]] =
    findOneByQuery(findByTriplePatternQuery(pattern))

  protected def findAllByTriplePattern(pattern: RdfSubject => TriplePattern): Task[Chunk[E]] =
    findAllByQuery(findByTriplePatternQuery(pattern))

  private def findByTriplePatternQuery(pattern: RdfSubject => TriplePattern) = {
    val s             = variable("s")
    val query: String = entityQuery(tripleP(s), graphP(s).and(pattern(s))).getQueryString
    Construct(query)
  }

  protected def findOneByQuery(construct: Construct): Task[Option[E]] =
    runQuery(construct)
      .map(_.nextOption())
      .flatMap(ZIO.foreach(_)(mapper.toEntity(_).mapError(TriplestoreResponseException.apply)))

  protected def findAllByQuery(construct: Construct): Task[Chunk[E]] =
    runQuery(construct).flatMap(
      ZStream.fromIterator(_).mapZIO(mapper.toEntity(_).mapError(TriplestoreResponseException.apply)).runCollect,
    )

  private def runQuery(construct: Construct): Task[Iterator[RdfResource]] = for {
    model <- triplestore.queryRdfModel(construct)
    resources <- model
                   .getResourcesRdfType(resourceClass.toString)
                   .orElseFail(TriplestoreResponseException("Error while querying the triplestore"))
  } yield resources

  def save(entity: E): Task[E] =
    triplestore.query(saveQuery(entity)).as(entity)

  private def saveQuery(entity: E): Update = {
    val iris  = self.entityProperties.all
    val idIri = Rdf.iri(entity.id.value)
    val deletePattern =
      iris.zipWithIndex.foldLeft(idIri.isA(Rdf.iri(resourceClass.toString))) { case (p, (iri, index)) =>
        p.andHas(iri, variable(s"n$index"))
      }
    val wherePattern =
      iris.zipWithIndex.foldLeft(idIri.isA(Rdf.iri(resourceClass.toString)).optional()) { case (p, (iri, index)) =>
        p.and(idIri.has(iri, variable(s"n$index")).optional())
      }
    val query = Queries
      .MODIFY()
      .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
      .`with`(namedGraphIri)
      .insert(mapper.toTriples(entity))
      .delete(deletePattern)
      .where(wherePattern)

    Update(query.getQueryString)
  }

  override def deleteById(id: Id): Task[Unit] = findById(id).flatMap {
    case None    => ZIO.unit
    case Some(e) => delete(e)
  }

  override def delete(entity: E): Task[Unit] = triplestore.query(eraseQuery(entity))

  private def eraseQuery(entity: E): Update = {
    val deletePattern = Rdf
      .iri(entity.id.value)
      .isA(Rdf.iri(resourceClass.toString))
      .andHas(variable("p"), variable("o"))
    val query = Queries
      .MODIFY()
      .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
      .`with`(namedGraphIri)
      .delete(deletePattern)
      .where(deletePattern)
    Update(query.getQueryString)
  }
}
