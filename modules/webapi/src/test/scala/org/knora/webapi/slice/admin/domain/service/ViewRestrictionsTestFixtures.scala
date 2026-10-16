/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake

/** Ontology-cache and row-cache builders shared by the view restrictions service specs. */
object ViewRestrictionsTestFixtures {

  def iri(s: String)(using sf: StringFormatter): SmartIri = sf.toSmartIri(s, requireInternal = true)

  def resourceClass(classIri: String)(using StringFormatter): (SmartIri, ReadClassInfoV2) =
    iri(classIri) -> ReadClassInfoV2(
      ClassInfoContentV2(iri(classIri), ontologySchema = InternalSchema),
      allBaseClasses = Seq.empty,
      isResourceClass = true,
    )

  def property(
    propertyIri: String,
    isLinkProp: Boolean,
    isLinkValueProp: Boolean,
    isFileValueProp: Boolean,
  )(using StringFormatter): (SmartIri, ReadPropertyInfoV2) =
    iri(propertyIri) -> ReadPropertyInfoV2(
      PropertyInfoContentV2(iri(propertyIri), ontologySchema = InternalSchema),
      isResourceProp = true,
      isLinkProp = isLinkProp,
      isLinkValueProp = isLinkValueProp,
      isFileValueProp = isFileValueProp,
    )

  def valueProp(propertyIri: String)(using StringFormatter): (SmartIri, ReadPropertyInfoV2) =
    property(propertyIri, false, false, false)
  def fileProp(propertyIri: String)(using StringFormatter): (SmartIri, ReadPropertyInfoV2) =
    property(propertyIri, false, false, true)
  def linkProp(propertyIri: String)(using StringFormatter): (SmartIri, ReadPropertyInfoV2) =
    property(propertyIri, true, false, false)
  def linkValueProp(propertyIri: String)(using StringFormatter): (SmartIri, ReadPropertyInfoV2) =
    property(propertyIri, false, true, false)

  def ontology(
    ontologyIri: String,
    classes: Seq[(SmartIri, ReadClassInfoV2)],
    properties: Seq[(SmartIri, ReadPropertyInfoV2)],
  )(using StringFormatter): (SmartIri, ReadOntologyV2) =
    iri(ontologyIri) -> ReadOntologyV2(
      OntologyMetadataV2(iri(ontologyIri)),
      classes = classes.toMap,
      properties = properties.toMap,
    )

  /** An ontology cache holding the given ontologies, built with the test `StringFormatter`. */
  def ontologyCacheLayer(
    ontologies: StringFormatter ?=> Seq[(SmartIri, ReadOntologyV2)],
  ): ZLayer[StringFormatter, Nothing, OntologyCacheFake] =
    ZLayer.fromZIO(
      ZIO.serviceWithZIO[StringFormatter](sf =>
        Ref.make(OntologyCacheFake.emptyData.copy(ontologies = ontologies(using sf).toMap)).map(OntologyCacheFake(_)),
      ),
    )

  /**
   * A row cache whose loaders count the resource-row and value-row reads and return one resource row of
   * `classIri`, no graphs and no value rows.
   */
  def countingCache(
    resourceLoads: Ref[Int],
    valueLoads: Ref[Int],
    classIri: String,
  ): ULayer[ViewRestrictionsRowCache] =
    ZLayer.fromZIO {
      for {
        resources <- ViewRestrictionsRowCache.makeCache[ProjectIri, Seq[ResourceCountRow]](10, 1.minute)(_ =>
                       resourceLoads.update(_ + 1).as(Seq(ResourceCountRow(classIri, "V knora-admin:UnknownUser", 1))),
                     )
        graphs <- ViewRestrictionsRowCache.makeCache[ProjectIri, Seq[String]](10, 1.minute)(_ => ZIO.succeed(Seq.empty))
        values <- ViewRestrictionsRowCache.makeCache[ProjectIri, Seq[ValueCountRow]](10, 1.minute)(_ =>
                    valueLoads.update(_ + 1).as(Seq.empty),
                  )
      } yield ViewRestrictionsRowCache(resources, graphs, values)
    }
}
