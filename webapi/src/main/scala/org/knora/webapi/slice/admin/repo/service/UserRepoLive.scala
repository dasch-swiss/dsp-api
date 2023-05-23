package org.knora.webapi.slice.admin.repo.service

import play.twirl.api.TxtFormat
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.twirl.queries._
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.UserRepo
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class UserRepoLive(private val triplestore: TriplestoreService, private val mapper: PredicateObjectMapper)
    extends UserRepo {

  override def findById(id: InternalIri): Task[Option[User]] =
    findOneByQuery(sparql.admin.txt.getUsers(maybeIri = Some(id.value)))
  override def findByEmail(email: String): Task[Option[User]] =
    findOneByQuery(sparql.admin.txt.getUsers(maybeEmail = Some(email)))
  override def findByUsername(username: String): Task[Option[User]] =
    findOneByQuery(sparql.admin.txt.getUsers(maybeUsername = Some(username)))

  private def findOneByQuery(query: TxtFormat.Appendable): Task[Option[User]] =
    triplestore
      .sparqlHttpExtendedConstruct(query)
      .map(_.statements.headOption)
      .flatMap(ZIO.foreach(_)(toUser))

  private def toUser(subjectPropsTuple: (SubjectV2, ConstructPredicateObjects)): Task[User] = {
    val id                                  = InternalIri(subjectPropsTuple._1.toString)
    val propsMap: ConstructPredicateObjects = subjectPropsTuple._2
    for {
      isActive             <- mapper.getSingleOrFail[BooleanLiteralV2](KnoraAdmin.Status, propsMap)
      username             <- mapper.getSingleOrFail[StringLiteralV2](KnoraAdmin.Username, propsMap)
      email                <- mapper.getSingleOrFail[StringLiteralV2](KnoraAdmin.Email, propsMap)
      familyName           <- mapper.getSingleOrFail[StringLiteralV2](KnoraAdmin.FamilyName, propsMap)
      givenName            <- mapper.getSingleOrFail[StringLiteralV2](KnoraAdmin.GivenName, propsMap)
      password             <- mapper.getSingleOrFail[StringLiteralV2](KnoraAdmin.Password, propsMap)
      preferredLanguage    <- mapper.getSingleOrFail[StringLiteralV2](KnoraAdmin.PreferredLanguage, propsMap)
      isInProject          <- mapper.getList[StringLiteralV2](KnoraAdmin.IsInProject, propsMap)
      isInGroup            <- mapper.getList[StringLiteralV2](KnoraAdmin.IsInGroup, propsMap)
      isInSystemAdminGroup <- mapper.getSingleOrFail[StringLiteralV2](KnoraAdmin.IsInSystemAdminGroup, propsMap)
    } yield User.make(
      id,
      isActive.value,
      username.value,
      email.value,
      familyName.value,
      givenName.value,
      password.value,
      preferredLanguage.value,
      isInProject.map(_.value).map(InternalIri),
      isInGroup.map(_.value).map(InternalIri),
      InternalIri(isInSystemAdminGroup.value)
    )
  }

  override def findAll(): Task[List[User]] = for {
    query  <- ZIO.attempt(sparql.admin.txt.getUsers(None, None, None))
    result <- triplestore.sparqlHttpExtendedConstruct(query).map(_.statements)
    users  <- ZIO.foreach(result.toList)(toUser)
  } yield users
}

object UserRepoLive {
  val layer: URLayer[TriplestoreService with PredicateObjectMapper, UserRepoLive] =
    ZLayer.fromFunction(UserRepoLive.apply _)
}
