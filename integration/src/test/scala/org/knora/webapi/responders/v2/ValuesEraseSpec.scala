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
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.UpdateValueContentV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.ProjectService
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
    test("erase a value") {
      for {
        res            <- ResourceHelper.createResource
        value          <- ValueHelper.createIntegerValue(res)
        updated        <- ValueHelper.updateIntegerValue(value, res, 666)
        old            <- ValueHelper.findValue(value.iri)
        _              <- zio.Console.printLine(s"res: $res,\n\n $value \n\n $updated \n\n $old")
        updatedDeleted <- ValueHelper.deleteIntegerValue(updated, res)
        valueDeleted   <- ValueHelper.findValue(value.iri)
        _              <- zio.Console.printLine(s"Deleted: $updatedDeleted,\n\n $valueDeleted")
      } yield assertCompletes
    },
  ).provideSome[env](ResourceHelper.layer, ValueHelper.layer, ValueRepo.layer)
}

final case class ValueHelper(valuesResponder: ValuesResponderV2, valueRepo: ValueRepo)(implicit
  val sf: StringFormatter,
) {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  def findValue(valueIri: ValueIri): Task[ValueModel] = valueRepo
    .findById(valueIri)
    .someOrFail(IllegalStateException("Value not found"))

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
}

object ValueHelper {
  val layer = ZLayer.derive[ValueHelper]

  def findValue(valueIri: ValueIri): ZIO[ValueHelper, Throwable, ValueModel] =
    ZIO.serviceWithZIO[ValueHelper](_.findValue(valueIri))

  def deleteIntegerValue(value: ActiveValue, resource: ActiveResource): ZIO[ValueHelper, Throwable, ValueModel] =
    ZIO.serviceWithZIO[ValueHelper](_.deleteIntegerValue(value, resource))

  def createIntegerValue(resource: ActiveResource): RIO[ValueHelper, ActiveValue] =
    ZIO.serviceWithZIO[ValueHelper](_.createIntegerValue(resource))

  def updateIntegerValue(
    value: ActiveValue,
    resource: ActiveResource,
    newValue: Int,
  ): ZIO[ValueHelper, Throwable, ActiveValue] =
    ZIO.serviceWithZIO[ValueHelper](_.updateIntegerValue(value, resource, newValue))
}

final case class ResourceHelper(
  projectService: ProjectService,
  resourcesResponderV2: ResourcesResponderV2,
  resourceRepo: ResourcesRepo,
)(implicit val stringFormatter: StringFormatter) {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  val shortcode   = Shortcode.unsafeFrom("0001")
  val ontologyIri = OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)

  def createSomeThingResource: Task[ActiveResource] = for {
    prj      <- projectService.findByShortcode(shortcode).someOrFail(IllegalStateException("Project not found"))
    uuid     <- Random.nextUUID
    createRes = CreateResourceV2(None, ontologyIri.makeClass("Thing").smartIri, "label", Map.empty, prj, None, None)
    createReq = CreateResourceRequestV2(createRes, rootUser, uuid)
    res      <- resourcesResponderV2.createResource(createReq)
    created <- resourceRepo
                 .findActiveById(ResourceIri.unsafeFrom(res.resources.head.resourceIri.toSmartIri))
                 .someOrFail(IllegalStateException("Resource not found"))
  } yield created
}

object ResourceHelper {
  val layer = ZLayer.derive[ResourceHelper]

  def createResource: ZIO[ResourceHelper, Throwable, ActiveResource] =
    ZIO.serviceWithZIO[ResourceHelper](_.createSomeThingResource)
}
