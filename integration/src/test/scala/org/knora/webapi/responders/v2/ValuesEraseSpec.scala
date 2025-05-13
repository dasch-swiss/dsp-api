/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*
import zio.test.*

import java.util.UUID
import scala.collection.SortedSet

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.DeleteValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.EraseValueHistoryV2
import org.knora.webapi.messages.v2.responder.valuemessages.EraseValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.UpdateValueContentV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.resources.repo.service.ActiveValue
import org.knora.webapi.slice.resources.repo.service.ResourceModel
import org.knora.webapi.slice.resources.repo.service.ResourceModel.ActiveResource
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.resources.repo.service.ValueModel
import org.knora.webapi.slice.resources.repo.service.ValueRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object ValuesEraseSpec extends E2EZSpec {
  import TestHelper.DiffLib._

  override val e2eSpec = suite("ValuesEraseSpec")(
    test("erase an IntValue") {
      for {
        res1               <- TestHelper.createResource
        val0               <- TestHelper.createIntegerValue(res1)
        val1               <- TestHelper.updateIntegerValue(val0, res1, 666)
        activeValueVersion <- TestHelper.updateIntegerValue(val1, res1, 123)
        allValueIris       <- TestHelper.findAllPrevious(activeValueVersion.iri).map(activeValueVersion.iri +: _)
        _                  <- ZIO.fail(IllegalStateException("Not all expected values found")).when(allValueIris.size != 3)
        _                  <- TestHelper.eraseIntegerValue(activeValueVersion, res1)
        allValues          <- ZIO.foreach(allValueIris)(TestHelper.findValue).map(_.flatten)
      } yield assertTrue(allValues.isEmpty)
    },
    test("erase an IntValue's history") {
      for {
        res1               <- TestHelper.createResource
        val0               <- TestHelper.createIntegerValue(res1)
        val1               <- TestHelper.updateIntegerValue(val0, res1, 666)
        activeValueVersion <- TestHelper.updateIntegerValue(val1, res1, 123)
        previousValueIris  <- TestHelper.findAllPrevious(activeValueVersion.iri)
        _                  <- ZIO.fail(IllegalStateException("Not all expected values found")).when(previousValueIris.size != 2)
        _                  <- TestHelper.eraseIntegerValueHistory(activeValueVersion, res1)
        previousValues     <- ZIO.foreach(previousValueIris)(TestHelper.findValue).map(_.flatten)
        currentValue       <- TestHelper.findValue(activeValueVersion.iri)
      } yield assertTrue(previousValues.isEmpty, currentValue.isDefined)
    },
    test("erase a LinkValue") {
      for {
        res1 <- TestHelper.createResource
        res2 <- TestHelper.createResource
        res3 <- TestHelper.createResource

        _   <- TestHelper.createLinkValue(res2, res3, Some("res2 to res3"))

        stInit <- ZIO.serviceWithZIO[TestHelper](_.getProjectActiveTriples())
        val1   <- TestHelper.createLinkValue(res1, res2, Some("first"))
        val2   <- TestHelper.updateLinkValue(val1, res1, res3, "updated value")

        _     <- TestHelper.eraseLinkValue(val2, res1)
        stEnd <- ZIO.serviceWithZIO[TestHelper](_.getProjectActiveTriples())

        diff =
          diffList(stInit, stEnd).map(
            _.replaceAll(""",http://rdfh.ch/0001/([^/]+)/values/([^/]+)\)""", ",http://rdfh.ch/0001/.../values/...)"),
          )

        // This triple connects the value that overrided val1, which is marked deleted, so excluded from getProjectActiveTriples.
        diffMatches =
          diff == Set(
            s"+ (${res1.iri},http://www.knora.org/ontology/0001/anything#hasOtherThingValue,http://rdfh.ch/0001/.../values/...)",
          )
      } yield assertTrue(diffMatches).label(diff.mkString("\n"))
    },
    test("erase a TextValue with standoff without links") {
      def createAndErase(withUpdate: Boolean) =
        for {
          res1 <- TestHelper.createResource
          res2 <- TestHelper.createResource

          textValueAsXml1 =
            s"""<?xml version="1.0" encoding="UTF-8"?><text documentType="html">This has <p>text</p></text>"""
          textValueAsXml2 =
            s"""<?xml version="1.0" encoding="UTF-8"?><text documentType="html">This has more <p>text</p></text>"""

          // these shouldn't be deleted
          _ <- TestHelper.createTextValueWithStandoff(res2, textValueAsXml1).flatMap {
                 TestHelper.updateTextValueWithStandoff(_, res2, textValueAsXml2)
               }

          stInit <- ZIO.serviceWithZIO[TestHelper](_.getProjectTriples())

          val1 <- TestHelper.createTextValueWithStandoff(res1, textValueAsXml1)
          val2 <- if (withUpdate)
                    TestHelper.updateTextValueWithStandoff(val1, res1, textValueAsXml2)
                  else
                    ZIO.succeed(val1)

          _ <- TestHelper.eraseTextValue(val2, res1)

          stEnd <- ZIO.serviceWithZIO[TestHelper](_.getProjectTriples())

          initIsEnd = stInit == stEnd
        } yield assertTrue(initIsEnd).label(diffList(stEnd, stInit).mkString("\n"))

      createAndErase(false) && createAndErase(true)
    },
    test("erase a TextValue with standoff with links should not be supported") {
      def textValueAsXml(link: Option[ResourceIri], suffix: String) = {
        val linkXml = link.map(l => s"""<a class="salsah-link" href="$l">resource</a>""").getOrElse("")
        s"""<?xml version="1.0" encoding="UTF-8"?>
            <text documentType="html">Link: $linkXml, $suffix</text>"""
      }

      def testCase(lastVersionHasLink: Boolean) = for {
        res1 <- TestHelper.createResource
        res2 <- TestHelper.createResource

        val1        <- TestHelper.createTextValueWithStandoff(res1, textValueAsXml(Some(res2.iri), "123"))
        val2        <- TestHelper.updateTextValueWithStandoff(val1, res1, textValueAsXml(None, "456"))
        eraseResult <- TestHelper.eraseTextValue(val2, res1).either
      } yield assertTrue(
        eraseResult.left.toOption.map(_.getMessage) == Some("Erasing standoff text values with links is not supported"),
      )

      testCase(lastVersionHasLink = false) && testCase(lastVersionHasLink = true)
    },
  ).provideSome[env](TestHelper.layer, ValueRepo.layer)
}

final case class TestHelper(
  knoraProjectService: KnoraProjectService,
  projectService: ProjectService,
  resourceRepo: ResourcesRepo,
  resourcesResponderV2: ResourcesResponderV2,
  valueRepo: ValueRepo,
  valuesResponder: ValuesResponderV2,
  triplestoreService: TriplestoreService,
  requestParser: ApiComplexV2JsonLdRequestParser,
)(implicit
  val sf: StringFormatter,
) {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  def getProjectTriples(
    selectForGraph: String => String = graph => s"""
      SELECT * WHERE {
        GRAPH <$graph> {
          ?s ?p ?o .
          FILTER(?p != knora-base:lastModificationDate) .
        }
      }
      """,
  ): Task[SortedSet[TestHelper.Triple]] = for {
    prj <- projectService.findByShortcode(shortcode).someOrFail(IllegalStateException("Project not found"))
    query = s"""
      PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

      ${selectForGraph(ProjectService.projectDataNamedGraphV2(prj).value)}
    """
    // _ <- Console.printLine(s"query = $query")
    r <- triplestoreService.query(Select(query))
  } yield SortedSet.from(r.results.bindings.map(x => (x.rowMap("s"), x.rowMap("p"), x.rowMap("o"))).toSet)

  def getProjectActiveTriples() = getProjectTriples(graph => s"""
    SELECT DISTINCT ?s ?p ?o
    WHERE {
      ?rc rdf:type ?c .
      ?c rdfs:subClassOf knora-base:Resource .
      ?vt rdfs:subClassOf *knora-base:Value .
      {
        GRAPH <$graph> {
          ?r rdf:type ?c .
          ?r ?svp ?v.
          BIND(?r as ?s).
          BIND(?svp as ?p).
          BIND(?v as ?o).
        }
      } UNION {
        GRAPH <$graph> {
          ?r rdf:type ?c .
          ?r ?svp ?v.
          ?v a ?vt .
          ?v ?vp ?vo .
          ?v knora-base:isDeleted false .
          BIND(?v as ?s).
          BIND(?vp as ?p).
          BIND(?vo as ?o).
        }
      }
      FILTER(?p != knora-base:lastModificationDate) .
    }
    """)

  def printAll: Task[Unit] = getProjectTriples().map(_.size).flatMap(Console.printLine(_))

  def findValue(valueIri: ValueIri): Task[Option[ValueModel]] = valueRepo.findById(valueIri)

  def createLinkValue(
    left: ActiveResource,
    right: ActiveResource,
    comment: Option[String] = None,
  ): ZIO[Any, Throwable, ActiveValue] =
    val propertyIri = left.ontologyIri.makeProperty("hasOtherThingValue")
    for {
      uuid <- Random.nextUUID
      createVal = CreateValueV2(
                    left.iri.toString,
                    left.resourceClassIri.toComplexSchema,
                    propertyIri.toComplexSchema,
                    LinkValueContentV2(ApiV2Complex, right.iri.toString, comment = comment),
                  )
      value <- valuesResponder.createValueV2(createVal, rootUser, uuid)
      value <- valueRepo
                 .findActiveById(ValueIri.unsafeFrom(value.valueIri.toSmartIri))
                 .someOrFail(IllegalStateException("Value not found"))
    } yield value

  def updateLinkValue(
    value: ActiveValue,
    resource: ActiveResource,
    linkedResource: ActiveResource,
    newComment: String,
  ): ZIO[Any, Throwable, ActiveValue] =
    val hasOtherThingValue = resource.ontologyIri.makeProperty("hasOtherThingValue")
    val update = UpdateValueContentV2(
      resource.iri.toString,
      resource.resourceClassIri.toComplexSchema,
      hasOtherThingValue.toComplexSchema,
      value.iri.toString,
      LinkValueContentV2(ApiV2Complex, linkedResource.iri.toString, comment = Some(newComment)),
    )
    for {
      response <- valuesResponder.updateValueV2(update, rootUser, UUID.randomUUID())
      updated <- valueRepo
                   .findActiveById(ValueIri.unsafeFrom(response.valueIri.toSmartIri))
                   .someOrFail(IllegalStateException("Value not found"))
    } yield updated

  def createIntegerValue(resource: ActiveResource): Task[ActiveValue] =
    val hasInteger = resource.ontologyIri.makeProperty("hasInteger")
    for {
      uuid <- Random.nextUUID
      createVal = CreateValueV2(
                    resource.iri.toString,
                    resource.resourceClassIri.toComplexSchema,
                    hasInteger.toComplexSchema,
                    IntegerValueContentV2(ApiV2Complex, 1, None),
                  )
      value <- valuesResponder.createValueV2(createVal, rootUser, uuid)
      value <- valueRepo
                 .findActiveById(ValueIri.unsafeFrom(value.valueIri.toSmartIri))
                 .someOrFail(IllegalStateException("Value not found"))
    } yield value

  def updateIntegerValue(
    value: ActiveValue,
    resource: ActiveResource,
    newValue: Int,
  ): ZIO[Any, Throwable, ActiveValue] =
    val hasInteger = resource.ontologyIri.makeProperty("hasInteger")
    val update = UpdateValueContentV2(
      resource.iri.toString,
      resource.resourceClassIri.toComplexSchema,
      hasInteger.toComplexSchema,
      value.iri.toString,
      IntegerValueContentV2(ApiV2Complex, newValue, None),
    )
    for {
      response <- valuesResponder.updateValueV2(update, rootUser, UUID.randomUUID())
      updated <- valueRepo
                   .findActiveById(ValueIri.unsafeFrom(response.valueIri.toSmartIri))
                   .someOrFail(IllegalStateException("Value not found"))
    } yield updated

  def deleteIntegerValue(value: ActiveValue, resource: ActiveResource): ZIO[Any, Throwable, ValueModel] =
    val delete = DeleteValueV2(
      resource.iri,
      resource.resourceClassIri,
      resource.ontologyIri.makeProperty("hasInteger"),
      value.iri,
      value.valueClass.value.toSmartIri,
      None,
      None,
      UUID.randomUUID(),
    )
    for {
      _       <- valuesResponder.deleteValueV2(delete, rootUser)
      deleted <- valueRepo.findById(value.iri).someOrFail(IllegalStateException("Deleted value not found"))
    } yield deleted

  def eraseIntegerValue(value: ActiveValue, resource: ActiveResource): ZIO[Any, Throwable, Unit] =
    val erase = EraseValueV2(
      resource.iri,
      resource.resourceClassIri,
      resource.ontologyIri.makeProperty("hasInteger"),
      value.iri,
      value.valueClass.value.toSmartIri,
      UUID.randomUUID(),
    )
    for {
      project <-
        knoraProjectService.findByShortcode(resource.shortcode).someOrFail(IllegalStateException("Project not found"))
      _ <- valuesResponder.eraseValue(erase, rootUser, project)
    } yield ()

  def createTextValueWithStandoff(resource: ActiveResource, textValueAsXml: String): Task[ActiveValue] =
    for {
      textValueAsXmlEncoded <- ZIO.succeed(sf.toJsonEncodedString(textValueAsXml))
      jsonLd = s"""{
                  |  "@id" : "${resource.iri.toString}",
                  |  "@type" : "${ontologyIri.makeClass("Thing").toComplexSchema.toIri}",
                  |  "anything:hasText" : {
                  |    "@type" : "knora-api:TextValue",
                  |    "knora-api:textValueAsXml" : $textValueAsXmlEncoded,
                  |    "knora-api:textValueHasMapping" : {
                  |      "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
                  |    }
                  |  },
                  |  "@context" : {
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "anything" : "${ontologyIri.toComplexSchema.toIri}#"
                  |  }
                  |}""".stripMargin

      createVal <- requestParser.createValueV2FromJsonLd(jsonLd).mapError(Throwable(_))

      uuid  <- Random.nextUUID
      value <- valuesResponder.createValueV2(createVal, rootUser, uuid)
      value <- valueRepo
                 .findActiveById(ValueIri.unsafeFrom(value.valueIri.toSmartIri))
                 .someOrFail(IllegalStateException("Value not found"))
    } yield value

  def updateTextValueWithStandoff(
    value: ActiveValue,
    resource: ActiveResource,
    textValueAsXml: String,
  ): Task[ActiveValue] =
    for {
      textValueAsXmlEncoded <- ZIO.succeed(sf.toJsonEncodedString(textValueAsXml))
      jsonLd = s"""{
                  |  "@id" : "${resource.iri.toString}",
                  |  "@type" : "${ontologyIri.makeClass("Thing").toComplexSchema.toIri}",
                  |  "anything:hasText" : {
                  |    "@id" : "${value.iri}",
                  |    "@type" : "knora-api:TextValue",
                  |    "knora-api:textValueAsXml" : $textValueAsXmlEncoded,
                  |    "knora-api:textValueHasMapping" : {
                  |      "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
                  |    }
                  |  },
                  |  "@context" : {
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "anything" : "${ontologyIri.toComplexSchema.toIri}#"
                  |  }
                  |}""".stripMargin

      updateVal <- requestParser.updateValueV2fromJsonLd(jsonLd).mapError(Throwable(_))

      uuid  <- Random.nextUUID
      value <- valuesResponder.updateValueV2(updateVal, rootUser, uuid)
      value <- valueRepo
                 .findActiveById(ValueIri.unsafeFrom(value.valueIri.toSmartIri))
                 .someOrFail(IllegalStateException("Value not found"))
    } yield value

  def eraseTextValue(value: ActiveValue, resource: ActiveResource): ZIO[Any, Throwable, Unit] =
    val erase = EraseValueV2(
      resource.iri,
      resource.resourceClassIri,
      resource.ontologyIri.makeProperty("hasText"),
      value.iri,
      value.valueClass.value.toSmartIri,
      UUID.randomUUID(),
    )
    for {
      project <-
        knoraProjectService.findByShortcode(resource.shortcode).someOrFail(IllegalStateException("Project not found"))
      _ <- valuesResponder.eraseValue(erase, rootUser, project)
    } yield ()

  def eraseLinkValue(value: ActiveValue, resource: ActiveResource): ZIO[Any, Throwable, Unit] =
    val erase = EraseValueV2(
      resource.iri,
      resource.resourceClassIri,
      resource.ontologyIri.makeProperty("hasOtherThingValue"),
      value.iri,
      value.valueClass.value.toSmartIri,
      UUID.randomUUID(),
    )
    for {
      project <-
        knoraProjectService.findByShortcode(resource.shortcode).someOrFail(IllegalStateException("Project not found"))
      _ <- valuesResponder.eraseValue(erase, rootUser, project)
    } yield ()

  def eraseLinkValueHistory(value: ActiveValue, resource: ActiveResource): ZIO[Any, Throwable, Unit] =
    val erase = EraseValueHistoryV2(
      resource.iri,
      resource.resourceClassIri,
      resource.ontologyIri.makeProperty("hasOtherThingValue"),
      value.iri,
      value.valueClass.value.toSmartIri,
      UUID.randomUUID(),
    )
    for {
      project <-
        knoraProjectService.findByShortcode(resource.shortcode).someOrFail(IllegalStateException("Project not found"))
      _ <- valuesResponder.eraseValueHistory(erase, rootUser, project)
    } yield ()

  def eraseIntegerValueHistory(value: ActiveValue, resource: ActiveResource): ZIO[Any, Throwable, Unit] =
    val erase = EraseValueHistoryV2(
      resource.iri,
      resource.resourceClassIri,
      resource.ontologyIri.makeProperty("hasInteger"),
      value.iri,
      value.valueClass.value.toSmartIri,
      UUID.randomUUID(),
    )
    for {
      project <-
        knoraProjectService.findByShortcode(resource.shortcode).someOrFail(IllegalStateException("Project not found"))
      _ <- valuesResponder.eraseValueHistory(erase, rootUser, project)
    } yield ()

  def findAllPrevious(valueIri: ValueIri): Task[Seq[ValueIri]] = valueRepo.findAllPrevious(valueIri)

  def deleteLinkValue(value: ActiveValue, resource: ActiveResource): ZIO[Any, Throwable, ValueModel] =
    val delete = DeleteValueV2(
      resource.iri,
      resource.resourceClassIri,
      resource.ontologyIri.makeProperty("hasOtherThingValue"),
      value.iri,
      value.valueClass.value.toSmartIri,
      None,
      None,
      UUID.randomUUID(),
    )
    for {
      _       <- valuesResponder.deleteValueV2(delete, rootUser)
      deleted <- valueRepo.findById(value.iri).someOrFail(IllegalStateException("Deleted value not found"))
    } yield deleted

  val shortcode   = Shortcode.unsafeFrom("0001")
  val ontologyIri = OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)

  def createResource: Task[ActiveResource] = for {
    prj      <- projectService.findByShortcode(shortcode).someOrFail(IllegalStateException("Project not found"))
    uuid     <- Random.nextUUID
    createRes = CreateResourceV2(None, ontologyIri.makeClass("Thing").smartIri, "label", Map.empty, prj, None, None)
    createReq = CreateResourceRequestV2(createRes, rootUser, uuid)
    res      <- resourcesResponderV2.createResource(createReq)
    created <- resourceRepo
                 .findActiveById(ResourceIri.unsafeFrom(res.resources.head.resourceIri.toSmartIri))
                 .someOrFail(IllegalStateException("Resource not found"))
  } yield created

  def findValues(iri: ResourceIri): Task[Map[PropertyIri, Seq[ValueIri]]] = resourceRepo.findValues(iri)

  def findLinks(iri: ResourceIri): Task[Map[PropertyIri, Seq[ResourceIri]]] = resourceRepo.findLinks(iri)
}

object TestHelper {
  val layer = ZLayer.derive[TestHelper]

  def createLinkValue(
    left: ActiveResource,
    right: ActiveResource,
    comment: Option[String] = None,
  ): ZIO[TestHelper, Throwable, ActiveValue] =
    ZIO.serviceWithZIO[TestHelper](_.createLinkValue(left, right, comment))

  def updateLinkValue(
    value: ActiveValue,
    resource: ActiveResource,
    linkedResource: ActiveResource,
    newComment: String,
  ): ZIO[TestHelper, Throwable, ActiveValue] =
    ZIO.serviceWithZIO[TestHelper](_.updateLinkValue(value, resource, linkedResource, newComment))

  def eraseLinkValue(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.eraseLinkValue(value, resource))

  def eraseLinkValueHistory(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.eraseLinkValueHistory(value, resource))

  def ensureLinkWasRemoved(res1: ActiveResource, res2: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    for {
      links <- findLinks(res1.iri)
      _     <- ZIO.fail(IllegalStateException(s"Link was not removed: $links")).when(links.nonEmpty)
    } yield ()

  def deleteIntegerValue(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, ValueModel] =
    ZIO.serviceWithZIO[TestHelper](_.deleteIntegerValue(value, resource))

  def deleteLinkValue(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, ValueModel] =
    ZIO.serviceWithZIO[TestHelper](_.deleteLinkValue(value, resource))

  def createIntegerValue(resource: ActiveResource): RIO[TestHelper, ActiveValue] =
    ZIO.serviceWithZIO[TestHelper](_.createIntegerValue(resource))

  def updateIntegerValue(
    value: ActiveValue,
    resource: ActiveResource,
    newValue: Int,
  ): ZIO[TestHelper, Throwable, ActiveValue] =
    ZIO.serviceWithZIO[TestHelper](_.updateIntegerValue(value, resource, newValue))

  def eraseIntegerValue(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.eraseIntegerValue(value, resource))

  def eraseIntegerValueHistory(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.eraseIntegerValueHistory(value, resource))

  def findValue(valueIri: ValueIri): ZIO[TestHelper, Throwable, Option[ValueModel]] =
    ZIO.serviceWithZIO[TestHelper](_.findValue(valueIri))

  def findValues(iri: ResourceIri): ZIO[TestHelper, Throwable, Map[PropertyIri, Seq[ValueIri]]] =
    ZIO.serviceWithZIO[TestHelper](_.findValues(iri))

  def findLinks(iri: ResourceIri): ZIO[TestHelper, Throwable, Map[PropertyIri, Seq[ResourceIri]]] =
    ZIO.serviceWithZIO[TestHelper](_.findLinks(iri))

  def createResource: ZIO[TestHelper, Throwable, ActiveResource] =
    ZIO.serviceWithZIO[TestHelper](_.createResource)

  def createTextValueWithStandoff(
    resource: ActiveResource,
    textValueAsXml: String,
  ): ZIO[TestHelper, Throwable, ActiveValue] =
    ZIO.serviceWithZIO[TestHelper](_.createTextValueWithStandoff(resource, textValueAsXml))

  def updateTextValueWithStandoff(
    value: ActiveValue,
    resource: ActiveResource,
    textValueAsXml: String,
  ): ZIO[TestHelper, Throwable, ActiveValue] =
    ZIO.serviceWithZIO[TestHelper](_.updateTextValueWithStandoff(value, resource, textValueAsXml))

  def eraseTextValue(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.eraseTextValue(value, resource))

  def findAllPrevious(valueIri: ValueIri): ZIO[TestHelper, Throwable, Seq[ValueIri]] =
    ZIO.serviceWithZIO[TestHelper](_.findAllPrevious(valueIri))

  type Triple = (String, String, String)

  def printAll: ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.printAll)

  object DiffLib {
    def diff[A](a: SortedSet[A], b: SortedSet[A]): (SortedSet[A], SortedSet[A]) =
      (a.diff(b), b.diff(a))

    def diffList[A](a: SortedSet[A], b: SortedSet[A]): SortedSet[String] = {
      val (aa, bb) = diff(a, b)
      aa.map(a => s"- ${a}") ++ bb.map(b => s"+ ${b}")
    }

    if (diffList(SortedSet(1), SortedSet(2)) != SortedSet("- 1", "+ 2"))
      throw new Exception(s"!!! diffList(SortedSet(1), SortedSet(2)) != ${diffList(SortedSet(1), SortedSet(2))}")
  }
}
