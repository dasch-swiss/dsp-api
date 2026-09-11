/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.Chunk
import zio.Exit
import zio.IO
import zio.NonEmptyChunk
import zio.Ref
import zio.Task
import zio.ZIO
import zio.stream.ZStream

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.slice.common.repo.service.CrudRepository
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

trait EntityWithId[Id <: StringValue] {
  def id: Id
}

trait RdfEntityMapper[E] {

  /**
   * The triples describing the entity, as a complete predicate-object list terminated by a `.`,
   * e.g. `<id> a <SomeClass> ; p1 "o1" ; p2 <o2> .`. The fragment is inserted into the `INSERT`
   * clause of [[AbstractEntityRepo.saveQuery]], which declares the `knora-admin` prefix.
   */
  def toTriples(entity: E): Fragment
  def toEntity(resource: RdfResource): IO[RdfError, E]

}

final case class EntityProperties(req: NonEmptyChunk[Iri], opt: Chunk[Iri] = Chunk.empty) {
  def all: Chunk[Iri] = req ++ opt
}

abstract class AbstractEntityRepo[E <: EntityWithId[Id], Id <: StringValue](
  triplestore: TriplestoreService,
  mapper: RdfEntityMapper[E],
) extends CrudRepository[E, Id] {

  protected def resourceClass: Iri
  protected def namedGraphIri: Iri
  protected def entityProperties: EntityProperties

  /** The subject a lookup pattern passed to [[findOneByPattern]] / [[findAllByPattern]] must use. */
  protected val s: Variable = Variable("s")

  private val p = Variable("p")
  private val o = Variable("o")

  /** The variable bound to the object of the property at `index` in [[EntityProperties.all]]. */
  private def objectVar(index: Int): Variable = Variable(s"n$index")

  /** `p0 ?n0 ;\n  p1 ?n1 ; ...` — the given properties as a predicate-object list, indexed from 0. */
  private def propertyList(properties: Chunk[Iri]): Fragment =
    Fragment.join(
      properties.zipWithIndex.map { case (iri, index) => sparql"$iri ${objectVar(index)}" },
      Fragment.raw(" ;\n  "),
    )

  /** One `OPTIONAL { <subject> p ?nX . }` per given property, starting at index `offset`. */
  private def optionalBlocks(subject: SparqlValue, properties: Chunk[Iri], offset: Int): Fragment =
    properties.zipWithIndex.map { case (iri, index) =>
      sparql"OPTIONAL { $subject $iri ${objectVar(index + offset)} . }"
    }.joinLines

  override def findAll(): Task[Chunk[E]] = findAllResilient(findAllQuery)

  override def findById(id: Id): Task[Option[E]] = findOneByQuery(findByIdQuery(id))

  protected def findOneByPattern(pattern: Fragment): Task[Option[E]] =
    findOneByQuery(findByPatternQuery(pattern))

  protected def findAllByPattern(pattern: Fragment): Task[Chunk[E]] =
    findAllByQuery(findByPatternQuery(pattern))

  // Fetches every triple of every matching subject in one CONSTRUCT instead of enumerating each known
  // property (required triples + N OPTIONAL blocks): the per-property shape becomes a cross-product for
  // classes with many optional properties. Over-fetching unrelated predicates on the same subject is benign.
  private[service] def findAllQuery: Construct =
    Construct(
      sparql"""|CONSTRUCT { $s $p $o . }
               |WHERE {
               |  GRAPH $namedGraphIri {
               |    $s a $resourceClass ;
               |      $p $o .
               |  }
               |}""".render,
    )

  private[service] def findByIdQuery(id: Id): Construct = {
    val subject = Iri.unsafeFrom(id.value)
    Construct(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT {
               |  $subject a $resourceClass ;
               |  ${propertyList(entityProperties.all)} .
               |}
               |WHERE {
               |  GRAPH $namedGraphIri {
               |    $subject a $resourceClass ;
               |    ${propertyList(entityProperties.req.toChunk)} .
               |    ${optionalBlocks(subject, entityProperties.opt, entityProperties.req.size)}
               |  }
               |}""".render,
    )
  }

  private[service] def findByPatternQuery(pattern: Fragment): Construct =
    Construct(
      // The caller's pattern is the most selective part of the query (e.g. a shortcode lookup) and must
      // precede the OPTIONAL blocks within the same group: OPTIONALs are left-joins evaluated in document
      // order, so with the pattern at the end the triplestore computes them for every entity of the class
      // before restricting.
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT {
               |  $s a $resourceClass ;
               |  ${propertyList(entityProperties.all)} .
               |}
               |WHERE {
               |  GRAPH $namedGraphIri {
               |    $pattern
               |    $s a $resourceClass ;
               |    ${propertyList(entityProperties.req.toChunk)} .
               |    ${optionalBlocks(s, entityProperties.opt, entityProperties.req.size)}
               |  }
               |}""".render,
    )

  private[service] def saveQuery(entity: E): Update = {
    val subject = Iri.unsafeFrom(entity.id.value)
    Update(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |
               |WITH $namedGraphIri
               |DELETE {
               |  $subject a $resourceClass ;
               |  ${propertyList(entityProperties.all)} .
               |}
               |INSERT {
               |  ${mapper.toTriples(entity)}
               |}
               |WHERE {
               |  OPTIONAL {
               |    $subject a $resourceClass .
               |    ${optionalBlocks(subject, entityProperties.all, 0)}
               |  }
               |}""".render,
    )
  }

  private[service] def eraseQuery(entity: E): Update = {
    val subject = Iri.unsafeFrom(entity.id.value)
    Update(
      sparql"""|WITH $namedGraphIri
               |DELETE {
               |  $subject a $resourceClass ;
               |    $p $o .
               |}
               |WHERE {
               |  $subject a $resourceClass ;
               |    $p $o .
               |}""".render,
    )
  }

  private def findOneByQuery(construct: Construct): Task[Option[E]] =
    runQuery(construct)
      .map(_.nextOption())
      .flatMap(ZIO.foreach(_)(mapper.toEntity(_).mapError(TriplestoreResponseException.apply)))

  private def findAllByQuery(construct: Construct): Task[Chunk[E]] =
    runQuery(construct).flatMap(
      ZStream.fromIterator(_).mapZIO(mapper.toEntity(_).mapError(TriplestoreResponseException.apply)).runCollect,
    )

  // An unmappable subject (missing required property, malformed value, or a mapper defect) is skipped and
  // logged rather than failing the whole batch. Interruption is re-propagated explicitly: a blanket
  // catchAllCause/.sandbox would also catch interruption, which must instead cancel the outer `findAll()`.
  private def findAllResilient(construct: Construct): Task[Chunk[E]] =
    runQuery(construct).flatMap { resources =>
      for {
        skippedRef  <- Ref.make(0)
        resultChunk <- ZStream
                         .fromIterator(resources)
                         .mapZIO { r =>
                           mapper.toEntity(r).mapError(TriplestoreResponseException.apply).exit.flatMap {
                             case Exit.Success(e)                    => ZIO.some(e)
                             case Exit.Failure(c) if c.isInterrupted => ZIO.refailCause(c)
                             case Exit.Failure(c)                    =>
                               r.getSubjectIri.flatMap { subjectIri =>
                                 skippedRef.update(_ + 1) *>
                                   ZIO
                                     .logWarningCause(
                                       s"skipping unmappable entity in findAll (subject=${subjectIri.value})",
                                       c,
                                     )
                                     .as(None)
                               }
                           }
                         }
                         .collectSome
                         .runCollect
        skipped <- skippedRef.get
        _       <- ZIO
               .logWarning(s"findAll skipped $skipped of ${resultChunk.length + skipped} subjects")
               .when(skipped > 0)
      } yield resultChunk
    }

  private def runQuery(construct: Construct): Task[Iterator[RdfResource]] = for {
    model     <- triplestore.queryRdfModel(construct)
    resources <- model
                   .getResourcesRdfType(resourceClass.value)
                   .orElseFail(TriplestoreResponseException("Error while querying the triplestore"))
  } yield resources

  def save(entity: E): Task[E] =
    triplestore.query(saveQuery(entity)).as(entity)

  override def deleteById(id: Id): Task[Unit] = findById(id).flatMap {
    case None    => ZIO.unit
    case Some(e) => delete(e)
  }

  override def delete(entity: E): Task[Unit] = triplestore.query(eraseQuery(entity))
}
