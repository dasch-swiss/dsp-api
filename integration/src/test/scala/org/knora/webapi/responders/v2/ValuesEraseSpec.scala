package org.knora.webapi.responders.v2

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.resources.repo.service.ResourceModel
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo

object ValuesEraseSpec extends E2EZSpec {

  override val e2eSpec = suite("ValuesEraseSpec")(
    test("erase a value") {
      for {
        res <- ResourceHelper.createSomeThingResource
        _   <- zio.Console.printLine(s"res: $res")
      } yield assertCompletes
    },
  ).provideSome[env](ResourceHelper.layer)
}

final case class ResourceHelper(
  projectService: ProjectService,
  resourcesResponderV2: ResourcesResponderV2,
  resourceRepo: ResourcesRepo,
)(implicit val stringFormatter: StringFormatter) {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  val shortcode   = Shortcode.unsafeFrom("0001")
  val ontologyIri = OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)

  def createSomeThingResource: Task[ResourceModel] = for {
    prj      <- projectService.findByShortcode(shortcode).someOrFail(IllegalStateException("Project not found"))
    uuid     <- Random.nextUUID
    createRes = CreateResourceV2(None, ontologyIri.makeClass("Thing").smartIri, "label", Map.empty, prj, None, None)
    createReq = CreateResourceRequestV2(createRes, rootUser, uuid)
    res      <- resourcesResponderV2.createResource(createReq)
    created <- resourceRepo
                 .findById(ResourceIri.unsafeFrom(res.resources.head.resourceIri.toSmartIri))
                 .someOrFail(IllegalStateException("Resource not found"))
  } yield created
}

object ResourceHelper {
  val layer = ZLayer.derive[ResourceHelper]

  def createSomeThingResource: ZIO[ResourceHelper, Throwable, ResourceModel] =
    ZIO.serviceWithZIO[ResourceHelper](_.createSomeThingResource)
}
