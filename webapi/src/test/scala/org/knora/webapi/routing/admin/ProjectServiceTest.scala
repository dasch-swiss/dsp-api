package org.knora.webapi.routing.admin
import zio.Task
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.IRI

case class ProjectServiceTest() extends ProjectService {
  override def getProjectByIri(
    iri: IRI,
    user: UserADM
  ): Task[ProjectGetResponseADM] = ???

}
object ProjectServiceTest {
  val layer = ZLayer.succeed(ProjectServiceTest)
}
