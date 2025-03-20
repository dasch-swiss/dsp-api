package org.knora.webapi.slice.resources.repo.service

import scala.language.implicitConversions
import org.apache.jena.rdf.model.Resource
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*
import dsp.errors.InconsistentRepositoryDataException
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.jena.JenaConversions.given_Conversion_String_Property
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

import java.time.Instant

sealed trait ValueModel {
  def iri: ValueIri
  def valueClass: InternalIri
  def lastModificationDate: Option[Instant]
}
final case class ActiveValue(iri: ValueIri, valueClass: InternalIri, lastModificationDate: Option[Instant])
    extends ValueModel
final case class DeletedValue(
  iri: ValueIri,
  valueClass: InternalIri,
  previousValue: ValueIri,
  lastModificationDate: Option[Instant],
) extends ValueModel

final case class ValueRepo(triplestore: TriplestoreService)(implicit val sf: StringFormatter) {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  def findActiveById(iri: ValueIri): Task[Option[ActiveValue]] =
    findById(iri).map(_.collect { case v: ActiveValue => v })

  def findDeletedById(iri: ValueIri): Task[Option[DeletedValue]] =
    findById(iri).map(_.collect { case v: DeletedValue => v })

  def findById(iri: ValueIri): Task[Option[ValueModel]] =
    val id = Rdf.iri(iri.toString)
    val (clazz, isDeleted, previousValue, lastModificationDate) =
      (variable("valueClass"), variable("isDeleted"), variable("previousValue"), variable("lastModificationDate"))
    val subClassOfValue = clazz.has(RDFS.SUBCLASSOF, KB.Value)
    val graphP = id
      .isA(clazz)
      .andHas(KB.lastModificationDate, lastModificationDate)
      .andHas(KB.isDeleted, isDeleted)
      .andHas(KB.previousValue, previousValue)
    val prevValu = id
      .has(KB.previousValue, previousValue)
      .optional()
      .and(id.isA(clazz).andHas(KB.isDeleted, isDeleted).optional())
      .and(id.has(KB.lastModificationDate, lastModificationDate).optional())

    val query = Queries.CONSTRUCT(graphP).where(prevValu, subClassOfValue)
    triplestore
      .queryRdfModel(Construct(query))
      .flatMap(_.getResource(iri.toString).map(_.map(_.res)))
      .flatMap(mapResult)

  private def mapResult(maybe: Option[Resource]): Task[Option[ValueModel]] =
    maybe match
      case None => ZIO.none
      case Some(resource) =>
        ZIO
          .fromEither(
            for {
              valueIri      <- resource.uri.fold(Left("Value IRI not found"))(s => ValueIri.from(s.toSmartIri))
              lastModified  <- resource.objectInstantOption(KnoraBase.LastModificationDate)
              isDeleted     <- resource.objectBooleanOption(KnoraBase.IsDeleted).map(_.getOrElse(false))
              valueClassIri <- resource.rdfType.fold(Left("Value class IRI not found"))(s => Right(InternalIri(s)))
              previousValue <- resource.objectUriOption(KnoraBase.PreviousValue, s => ValueIri.from(s.toSmartIri))
            } yield
              if isDeleted then Some(DeletedValue(valueIri, valueClassIri, previousValue.get, lastModified))
              else Some(ActiveValue(valueIri, valueClassIri, lastModified)),
          )
          .mapError(s => InconsistentRepositoryDataException(s))
}

object ValueRepo {
  val layer = ZLayer.derive[ValueRepo]
}
