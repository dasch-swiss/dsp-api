/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import zio.*

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.slice.search.repo.GetResourceWithSpecifiedPropertiesGravsearchQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * Loads resources as `SystemUser`, hidden objects included, for [[ObjectAccess]] to decide on. What it returns must
 * not reach a response before the caller's permission is checked on it.
 */
final class ObjectLoader(
  readResources: ReadResourcesService,
  searchResponder: SearchResponderV2,
  triplestore: TriplestoreService,
)(implicit sf: StringFormatter) {

  /**
   * The resource's metadata and its values for one property; `None` for a missing resource. Excludes deleted resources
   * and values. For a link property the result holds the link values and the target resources' metadata; for a
   * text-valued property also the `knora-base:hasStandoffLinkTo` targets.
   *
   * @param propertyInfo the property in the internal schema; for a link, the link property, not the link value
   *                     property.
   */
  private[access] def withPropertyValues(resourceIri: ResourceIri, propertyInfo: ReadPropertyInfoV2) =
    propertyValues(resourceIri, propertyInfo).map(found(resourceIri))

  /** The resource with its values of one property, deleted values included; `None` for a missing resource. */
  private[access] def withProperty(resourceIri: ResourceIri, property: PropertyIri): Task[Option[ReadResourceV2]] =
    withValues(resourceIri, Some(property.toInternalSchema))

  /** The resource with all its values, deleted values included; `None` for a missing resource. */
  private[access] def withAllValues(resourceIri: ResourceIri): Task[Option[ReadResourceV2]] =
    withValues(resourceIri, None)

  private def withValues(resourceIri: ResourceIri, property: Option[SmartIri]) =
    orMissing(
      resourceIri,
      readResources.getResources(
        Seq(resourceIri),
        property,
        ApiV2Complex,
        Set.empty,
        KnoraSystemInstances.Users.SystemUser,
      ),
    )

  /** Every non-deleted value of `property`, hidden ones included, by value IRI, with its `knora-base:valueHasOrder`. */
  private[access] def valueOrders(resource: ResourceIri, property: PropertyIri): Task[Map[String, Option[Int]]] =
    triplestore.query(ReorderSlots.query(resource, property)).flatMap { result =>
      ZIO
        .foreach(result.results.bindings) { row =>
          def required[A](a: Option[A]) =
            ZIO
              .fromOption(a)
              .orElseFail(InconsistentRepositoryDataException("The values of the reordered property could not be read"))
          for {
            value <- required(row.rowMap.get("value"))
            order <- ZIO.foreach(row.rowMap.get("order"))(raw => required(raw.toIntOption))
          } yield value -> order
        }
        .map(_.toMap)
    }

  /** The resource's preview; a deleted resource loads as `knora-base:DeletedResource`. */
  private[access] def previewWithDeleted(resourceIri: ResourceIri): Task[Option[ReadResourceV2]] =
    orMissing(
      resourceIri,
      readResources.getResourcePreviewWithDeletedResource(
        Seq(resourceIri),
        targetSchema = ApiV2Complex,
        requestingUser = KnoraSystemInstances.Users.SystemUser,
      ),
    )

  private[access] def preview(resourceIri: ResourceIri): Task[Option[ReadResourceV2]] =
    orMissing(
      resourceIri,
      readResources.getResourcePreview(
        Seq(resourceIri),
        targetSchema = ApiV2Complex,
        requestingUser = KnoraSystemInstances.Users.SystemUser,
      ),
    )

  // ReadResourcesSequenceV2.checkResourceIris names a missing resource as `<iri>`; ObjectLoaderSpec pins it.
  private def orMissing(resourceIri: ResourceIri, load: Task[ReadResourcesSequenceV2]) =
    load.map(found(resourceIri)).catchSome {
      case e: NotFoundException if e.getMessage.contains(s"<${resourceIri.value}>") => ZIO.none
    }

  private def found(resourceIri: ResourceIri)(sequence: ReadResourcesSequenceV2) =
    sequence.resources.find(_.resourceIri == resourceIri)

  private def propertyValues(resourceIri: ResourceIri, propertyInfo: ReadPropertyInfoV2) =
    for {
      objectClassConstraint <-
        ZIO
          .fromOption(
            propertyInfo.entityInfoContent.getIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri),
          )
          .orElseFail(
            InconsistentRepositoryDataException(
              s"Property ${propertyInfo.entityInfoContent.propertyIri} has no knora-base:objectClassConstraint",
            ),
          )
      maybeStandoffLinkToPropertyIri =
        Option.when(objectClassConstraint.toString == OntologyConstants.KnoraBase.TextValue)(
          OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
        )
      propertyIris <-
        ZIO.foreach(Seq(propertyInfo.entityInfoContent.propertyIri) ++ maybeStandoffLinkToPropertyIri)(iri =>
          ZIO.fromEither(PropertyIri.from(iri)).mapError(BadRequestException(_)),
        )
      query     = GetResourceWithSpecifiedPropertiesGravsearchQuery.build(resourceIri, propertyIris)
      response <- searchResponder.gravsearchV2(
                    query,
                    SchemaRendering.default,
                    KnoraSystemInstances.Users.SystemUser,
                    None,
                  )
    } yield response
}

object ObjectLoader {
  val layer = ZLayer.derive[ObjectLoader]
}
