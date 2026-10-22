/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.v2.responder.resourcemessages.DeleteOrEraseResourceRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resources.repo.service.ActiveValue
import org.knora.webapi.slice.resources.repo.service.ResourceModel.ActiveResource
import org.knora.webapi.slice.resources.repo.service.ValueRepo

/**
 * Lifecycle of the stored `knora-base:valueHasXml` literal of formatted text values: it survives mark-delete,
 * permission changes and reordering, and it is removed by every erase variant. Reads the triplestore only.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlLifecycleSpec extends E2EZSpec {
  import ValueHasXmlLifecycleSpec.*

  private val strongXml1 = xml("""<p>Dies ist <strong>fett</strong> und <strong>noch fetter</strong></p>""")
  private val emXml1     = xml("""<p>Kursiv: <em>Zeile eins</em></p><p>Absatz zwei <strong>mit</strong> Fett</p>""")
  private val strongXml2 = xml("""<p>Neu <strong>fett</strong> gesetzt</p><p>Und ein zweiter Absatz</p>""")
  private val emXml2     = xml("""<p><em>Kursiv</em> verdreht</p><p>Drei <strong>Worte</strong></p>""")

  override val e2eSpec: Spec[env, Any] = suite("ValueHasXmlLifecycleSpec")(
    test("mark-delete keeps the valueHasXml literal unchanged") {
      for {
        res     <- TestHelper.createResource
        value   <- TestHelper.createTextValueWithStandoff(res, strongXml1)
        before  <- valueHasXmlOf(value.iri)
        _       <- markDeleteTextValue(value, res)
        deleted <- objectsOf(value.iri, "isDeleted")
        after   <- valueHasXmlOf(value.iri)
      } yield assertTrue(before.size == 1, before.forall(_.nonEmpty), after == before, deleted == Set("true"))
    },
    test("erasing a two-version formatted value removes every triple of every version, including valueHasXml") {
      for {
        res       <- TestHelper.createResource
        v1        <- TestHelper.createTextValueWithStandoff(res, emXml1)
        v2        <- TestHelper.updateTextValueWithStandoff(v1, res, emXml2)
        iris      <- TestHelper.findAllPrevious(v2.iri).map(v2.iri +: _)
        xmlBefore <- ZIO.foreach(iris)(valueHasXmlOf)
        _         <- TestHelper.eraseTextValue(v2, res)
        triples   <- ZIO.foreach(iris)(triplesOf)
      } yield assertTrue(iris.size == 2, xmlBefore.forall(_.size == 1), triples.forall(_.isEmpty))
    },
    test("erasing a resource removes the valueHasXml of its formatted value") {
      for {
        res       <- TestHelper.createResource
        value     <- TestHelper.createTextValueWithStandoff(res, strongXml2)
        xmlBefore <- valueHasXmlOf(value.iri)
        fresh     <- TestHelper.findActiveResource(res.iri)
        uuid      <- Random.nextUUID
        _         <- ZIO.serviceWithZIO[TestHelper](
               _.resourcesResponderV2.eraseResourceV2(
                 DeleteOrEraseResourceRequestV2(
                   resourceIri = fresh.iri,
                   resourceClassIri = fresh.resourceClassIri.toComplexSchema,
                   maybeLastModificationDate = fresh.lastModificationDate,
                   requestingUser = rootUser,
                   apiRequestID = uuid,
                 ),
               ),
             )
        triples <- triplesOf(value.iri)
      } yield assertTrue(xmlBefore.size == 1, triples.isEmpty)
    },
    test("erasing only the history keeps valueHasXml of the current version and removes the previous version") {
      for {
        res         <- TestHelper.createResource
        v1          <- TestHelper.createTextValueWithStandoff(res, emXml1)
        v2          <- TestHelper.updateTextValueWithStandoff(v1, res, strongXml2)
        v1XmlBefore <- valueHasXmlOf(v1.iri)
        v2XmlBefore <- valueHasXmlOf(v2.iri)
        _           <- eraseTextValueHistory(v2, res)
        v1Triples   <- triplesOf(v1.iri)
        v2XmlAfter  <- valueHasXmlOf(v2.iri)
      } yield assertTrue(
        v1XmlBefore.size == 1,
        v2XmlBefore.size == 1,
        v1Triples.isEmpty,
        v2XmlAfter == v2XmlBefore,
      )
    },
    test("changing the permissions of a formatted value leaves valueHasXml unchanged") {
      for {
        res         <- TestHelper.createResource
        value       <- TestHelper.createTextValueWithStandoff(res, strongXml1)
        xmlBefore   <- valueHasXmlOfChain(value.iri)
        permBefore  <- permissionsOf(value.iri)
        newValueIri <- updatePermissions(value, res, "CR knora-admin:Creator|M knora-admin:ProjectMember")
        xmlAfter    <- valueHasXmlOfChain(newValueIri)
        permAfter   <- permissionsOf(newValueIri)
      } yield assertTrue(
        xmlBefore.values.forall(_.size == 1),
        permAfter != permBefore,
        // A permission change rewrites hasPermissions in place and creates no new version.
        newValueIri == value.iri,
        xmlAfter == xmlBefore,
      )
    },
    test("reordering formatted values leaves their valueHasXml unchanged") {
      for {
        res       <- TestHelper.createResource
        v1        <- TestHelper.createTextValueWithStandoff(res, emXml1)
        v2        <- TestHelper.createTextValueWithStandoff(res, strongXml2)
        xmlBefore <- ZIO.foreach(List(v1, v2))(v => valueHasXmlOf(v.iri))
        _         <- reorder(res, List(v2, v1))
        orders    <- ZIO.foreach(List(v2, v1))(v => orderOf(v.iri))
        xmlAfter  <- ZIO.foreach(List(v1, v2))(v => valueHasXmlOf(v.iri))
      } yield assertTrue(
        xmlBefore.forall(_.size == 1),
        xmlBefore.flatten.distinct.size == 2,
        orders == List(Set("0"), Set("1")),
        xmlAfter == xmlBefore,
      )
    },
  ).provideSomeAuto(TestHelper.layer, ValueRepo.layer)
}

object ValueHasXmlLifecycleSpec {
  private val KnoraBase = "http://www.knora.org/ontology/knora-base#"

  private def xml(body: String): String =
    s"""<?xml version="1.0" encoding="UTF-8"?><text documentType="html">$body</text>"""

  private type Triple = (String, String, String)

  /** All triples of the project data graph except the resources' lastModificationDate. */
  private def allTriples: RIO[TestHelper, Set[Triple]] =
    ZIO.serviceWithZIO[TestHelper](_.getProjectTriples()).map(_.toSet)

  private def triplesOf(valueIri: ValueIri): RIO[TestHelper, Set[Triple]] =
    allTriples.map(_.filter(_._1 == valueIri.value))

  private def objectsOf(valueIri: ValueIri, predicate: String): RIO[TestHelper, Set[String]] =
    triplesOf(valueIri).map(_.collect { case (_, p, o) if p == KnoraBase + predicate => o })

  private def valueHasXmlOf(valueIri: ValueIri): RIO[TestHelper, Set[String]] = objectsOf(valueIri, "valueHasXml")

  private def permissionsOf(valueIri: ValueIri): RIO[TestHelper, Set[String]] = objectsOf(valueIri, "hasPermissions")

  private def orderOf(valueIri: ValueIri): RIO[TestHelper, Set[String]] = objectsOf(valueIri, "valueHasOrder")

  /** The valueHasXml literals of a value and all its previous versions, keyed by version IRI. */
  private def valueHasXmlOfChain(valueIri: ValueIri): RIO[TestHelper, Map[ValueIri, Set[String]]] =
    for {
      iris <- TestHelper.findAllPrevious(valueIri).map(valueIri +: _)
      xmls <- ZIO.foreach(iris)(valueHasXmlOf)
    } yield iris.zip(xmls).toMap

  private def markDeleteTextValue(value: ActiveValue, resource: ActiveResource): RIO[TestHelper, Unit] =
    for {
      helper <- ZIO.service[TestHelper]
      uuid   <- Random.nextUUID
      delete  = DeleteValueV2(
                 resource.iri,
                 resource.resourceClassIri,
                 resource.ontologyIri.makeProperty("hasText"),
                 value.iri,
                 helper.toSmartIri(value.valueClass.value),
                 None,
                 None,
                 uuid,
               )
      _ <- helper.valuesResponder.deleteValueV2(delete, rootUser)
    } yield ()

  private def eraseTextValueHistory(value: ActiveValue, resource: ActiveResource): RIO[TestHelper, Unit] =
    for {
      helper <- ZIO.service[TestHelper]
      uuid   <- Random.nextUUID
      erase   = EraseValueHistoryV2(
                resource.iri,
                resource.resourceClassIri,
                resource.ontologyIri.makeProperty("hasText"),
                value.iri,
                helper.toSmartIri(value.valueClass.value),
                uuid,
              )
      project <- helper.knoraProjectService
                   .findByShortcode(resource.shortcode)
                   .someOrFail(IllegalStateException("Project not found"))
      _ <- helper.valuesResponder.eraseValueHistory(erase, rootUser, project)
    } yield ()

  private def updatePermissions(
    value: ActiveValue,
    resource: ActiveResource,
    permissions: String,
  ): RIO[TestHelper, ValueIri] =
    for {
      helper <- ZIO.service[TestHelper]
      uuid   <- Random.nextUUID
      update  = UpdateValuePermissionsV2(
                 resource.iri,
                 resource.resourceClassIri.toComplexSchema,
                 resource.ontologyIri.makeProperty("hasText").toComplexSchema,
                 value.iri,
                 helper.toSmartIri("http://api.knora.org/ontology/knora-api/v2#TextValue"),
                 permissions,
               )
      response <- helper.valuesResponder.updateValueV2(update, rootUser, uuid)
    } yield response.valueIri

  private def reorder(resource: ActiveResource, ordered: List[ActiveValue]): RIO[TestHelper, Unit] =
    for {
      helper  <- ZIO.service[TestHelper]
      project <- helper.knoraProjectService
                   .findByShortcode(resource.shortcode)
                   .someOrFail(IllegalStateException("Project not found"))
      now <- Clock.instant
      _   <- helper.valueRepo.reorderValues(
             ProjectService.projectDataNamedGraphV2(project),
             InternalIri(resource.iri.value),
             ordered.map(_.iri),
             now,
           )
    } yield ()
}
