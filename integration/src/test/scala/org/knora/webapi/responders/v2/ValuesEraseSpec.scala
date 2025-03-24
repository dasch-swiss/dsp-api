package org.knora.webapi.responders.v2

import zio.*
import zio.test.*
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
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.resources.repo.service.ActiveValue
import org.knora.webapi.slice.resources.repo.service.ResourceModel
import org.knora.webapi.slice.resources.repo.service.ResourceModel.ActiveResource
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.resources.repo.service.ValueModel
import org.knora.webapi.slice.resources.repo.service.ValueRepo

import java.util.UUID

object ValuesEraseSpec extends E2EZSpec {

  override val e2eSpec = suite("ValuesEraseSpec")(
    test("erase an IntValue") {
      for {
        res1         <- TestHelper.createResource
        val1         <- TestHelper.createIntegerValue(res1)
        _            <- Console.printLine(s"val1: $val1")
        val1         <- TestHelper.updateIntegerValue(val1, res1, 666)
        _            <- Console.printLine(s"val update1: $val1")
        val1         <- TestHelper.updateIntegerValue(val1, res1, 123)
        _            <- Console.printLine(s"val update2: $val1")
        allValueIris <- TestHelper.findAllPrevious(val1.iri).map(val1.iri +: _)
        _            <- ZIO.fail(IllegalStateException("Not all expected values found")).when(allValueIris.size != 3)
        _            <- TestHelper.eraseIntegerValue(val1, res1)
        allValues    <- ZIO.foreach(allValueIris)(TestHelper.findValue).map(_.flatten)
      } yield assertTrue(allValues.isEmpty)
    },
    test("erase an IntValue's history") {
      for {
        res1              <- TestHelper.createResource
        val1              <- TestHelper.createIntegerValue(res1)
        _                 <- Console.printLine(s"val1: $val1")
        val1              <- TestHelper.updateIntegerValue(val1, res1, 666)
        _                 <- Console.printLine(s"val update1: $val1")
        val1              <- TestHelper.updateIntegerValue(val1, res1, 123)
        _                 <- Console.printLine(s"val update2: $val1")
        previousValueIris <- TestHelper.findAllPrevious(val1.iri)
        _                 <- ZIO.fail(IllegalStateException("Not all expected values found")).when(previousValueIris.size != 2)
        _                 <- TestHelper.eraseIntegerValueHistory(val1, res1)
        previousValues    <- ZIO.foreach(previousValueIris)(TestHelper.findValue).map(_.flatten)
        currentValue      <- TestHelper.findValue(val1.iri)
      } yield assertTrue(previousValues.isEmpty, currentValue.isDefined)
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
)(implicit
  val sf: StringFormatter,
) {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  def findValue(valueIri: ValueIri): Task[Option[ValueModel]] = valueRepo.findById(valueIri)

  def createLink(left: ActiveResource, right: ActiveResource): ZIO[Any, Throwable, ActiveValue] =
    val propertyIri = left.ontologyIri.makeProperty("hasOtherThingValue")
    for {
      uuid <- Random.nextUUID
      createVal = CreateValueV2(
                    left.iri.toString,
                    left.resourceClassIri.toComplexSchema,
                    propertyIri.toComplexSchema,
                    LinkValueContentV2(ApiV2Complex, right.iri.toString),
                  )
      value <- valuesResponder.createValueV2(createVal, rootUser, uuid)
      value <- valueRepo
                 .findActiveById(ValueIri.unsafeFrom(value.valueIri.toSmartIri))
                 .someOrFail(IllegalStateException("Value not found"))
    } yield value

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
      resp <- valuesResponder.eraseValue(erase, rootUser, project)
    } yield resp

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
      resp <- valuesResponder.eraseValueHistory(erase, rootUser, project)
    } yield resp

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

  def findValues(iri: ResourceIri): Task[Map[KnoraIris.PropertyIri, Seq[ValueIri]]] = resourceRepo.findValues(iri)

  def findLinks(iri: ResourceIri): Task[Map[KnoraIris.PropertyIri, Seq[ResourceIri]]] = resourceRepo.findLinks(iri)
}

object TestHelper {
  val layer = ZLayer.derive[TestHelper]

  def createLink(left: ActiveResource, right: ActiveResource): ZIO[TestHelper, Throwable, ActiveValue] =
    ZIO.serviceWithZIO[TestHelper](_.createLink(left, right))

  def findValue(valueIri: ValueIri): ZIO[TestHelper, Throwable, Option[ValueModel]] =
    ZIO.serviceWithZIO[TestHelper](_.findValue(valueIri))

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

  def findValues(iri: ResourceIri): ZIO[TestHelper, Throwable, Map[KnoraIris.PropertyIri, Seq[ValueIri]]] =
    ZIO.serviceWithZIO[TestHelper](_.findValues(iri))

  def findLinks(iri: ResourceIri): ZIO[TestHelper, Throwable, Map[KnoraIris.PropertyIri, Seq[ResourceIri]]] =
    ZIO.serviceWithZIO[TestHelper](_.findLinks(iri))

  def createResource: ZIO[TestHelper, Throwable, ActiveResource] =
    ZIO.serviceWithZIO[TestHelper](_.createResource)

  def eraseIntegerValue(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.eraseIntegerValue(value, resource))

  def eraseIntegerValueHistory(value: ActiveValue, resource: ActiveResource): ZIO[TestHelper, Throwable, Unit] =
    ZIO.serviceWithZIO[TestHelper](_.eraseIntegerValueHistory(value, resource))

  def findAllPrevious(valueIri: ValueIri): ZIO[TestHelper, Throwable, Seq[ValueIri]] =
    ZIO.serviceWithZIO[TestHelper](_.findAllPrevious(valueIri))
}
