package org.knora.webapi.slice.resources.repo.service

import org.apache.jena.rdf.model.Resource
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*

import dsp.errors.InconsistentRepositoryDataException
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

sealed trait ValueModel {
  def iri: ValueIri
  def valueClass: InternalIri
}
final case class ActiveValue(iri: ValueIri, valueClass: InternalIri)  extends ValueModel
final case class DeletedValue(iri: ValueIri, valueClass: InternalIri) extends ValueModel

final case class ValueRepo(triplestore: TriplestoreService)(implicit val sf: StringFormatter) {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  def findActiveById(iri: ValueIri): Task[Option[ActiveValue]] =
    findById(iri).map(_.collect { case v: ActiveValue => v })

  def findDeletedById(iri: ValueIri): Task[Option[DeletedValue]] =
    findById(iri).map(_.collect { case v: DeletedValue => v })

  def findById(iri: ValueIri): Task[Option[ValueModel]] =
    val id              = Rdf.iri(iri.toString)
    val clazz           = variable("valueClass")
    val subClassOfValue = clazz.has(RDFS.SUBCLASSOF, KB.Value)
    val graphP          = id.isA(clazz)
    val queryP          = id.isA(clazz)
    val query           = Queries.CONSTRUCT(graphP).where(queryP, subClassOfValue)
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
              valueIri  <- resource.uri.fold(Left("Value IRI not found"))(s => ValueIri.from(s.toSmartIri))
              isDeleted <- resource.objectBooleanOption(KnoraBase.IsDeleted).map(_.getOrElse(false))
              valueClassIri <-
                resource.rdfType.fold(Left("Value class IRI not found"))(s => Right(InternalIri(s)))
            } yield
              if isDeleted then Some(DeletedValue(valueIri, valueClassIri))
              else Some(ActiveValue(valueIri, valueClassIri)),
          )
          .mapError(s => InconsistentRepositoryDataException(s))
}

object ValueRepo {
  val layer = ZLayer.derive[ValueRepo]
}
