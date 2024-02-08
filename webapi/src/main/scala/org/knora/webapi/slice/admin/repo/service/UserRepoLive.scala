package org.knora.webapi.slice.admin.repo.service

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.UserRepo
import org.knora.webapi.slice.admin.repo.service.UserRepoLive.Queries
import org.knora.webapi.slice.common.repo.rdf.RdfModel
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.stream.ZStream

final case class UserRepoLive(triplestore: TriplestoreService) extends UserRepo {

  /**
   * Retrieves an entity by its id.
   *
   * @param id The identifier of type [[Id]].
   * @return the entity with the given id or [[None]] if none found.
   */
  override def findById(id: UserIri): Task[Option[KnoraUser]] = {
    val construct = Queries.findById(id)
    for {
      model    <- triplestore.queryRdfModel(construct)
      resource <- model.getResource(id.value).option
      user     <- ZIO.foreach(resource)(toUser)
    } yield user
  }

  private def toUser(resource: RdfResource) = {
    for {
      userIri <-
        resource.iri.flatMap(it => ZIO.fromEither(UserIri.from(it.value))).mapError(TriplestoreResponseException.apply)
      username     <- resource.getStringLiteralOrFail[Username](KnoraAdmin.Username)(Username.from)
      email        <- resource.getStringLiteralOrFail[Email](KnoraAdmin.Email)(Email.from)
      familyName   <- resource.getStringLiteralOrFail[FamilyName](KnoraAdmin.FamilyName)(FamilyName.from)
      givenName    <- resource.getStringLiteralOrFail[GivenName](KnoraAdmin.GivenName)(GivenName.from)
      passwordHash <- resource.getStringLiteralOrFail[Password](KnoraAdmin.Password)(Password.from)
      preferredLanguage <-
        resource.getStringLiteralOrFail[LanguageCode](KnoraAdmin.PreferredLanguage)(LanguageCode.from)
      status     <- resource.getBooleanLiteralOrFail[UserStatus](KnoraAdmin.StatusProp)(b => Right(UserStatus.from(b)))
      projects   <- resource.getObjectIris(KnoraAdmin.IsInProject)
      projectIris = projects.flatMap(iri => ProjectIri.from(iri.value).toOption)
      groups     <- resource.getObjectIris(KnoraAdmin.IsInGroup)
      groupIris   = groups.flatMap(iri => GroupIri.from(iri.value).toOption)
      isInSystemAdminGroup <-
        resource.getBooleanLiteralOrFail[SystemAdmin](KnoraAdmin.IsInSystemAdminGroup)(b => Right(SystemAdmin.from(b)))
    } yield KnoraUser(
      userIri,
      username,
      email,
      familyName,
      givenName,
      passwordHash,
      preferredLanguage,
      status,
      projectIris,
      groupIris,
      isInSystemAdminGroup
    )
  }.mapError(e => TriplestoreResponseException(e.toString))

  /**
   * Returns all instances of the type.
   *
   * @return all instances of the type.
   */
  override def findAll(): Task[List[KnoraUser]] =
    for {
      model     <- triplestore.queryRdfModel(Queries.findAll)
      resources <- model.getResources(KnoraAdmin.User).option.map(_.getOrElse(Iterator.empty))
      users     <- ZStream.fromIterator(resources).mapZIO(toUser).runCollect
    } yield users.toList

  override def save(user: KnoraUser): Task[KnoraUser] =
    triplestore.query(Queries.create(user)).as(user)
}

object UserRepoLive {
  private object Queries {
    private val adminGraphIri = AdminConstants.adminDataNamedGraph.value

    def findAll = Construct(
      s"""
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
         |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
         |
         |CONSTRUCT {
         |     ?s ?p ?o .
         |}
         |WHERE {
         |    GRAPH <$adminGraphIri>{
         |       ?s a knora-admin:User ;
         |          ?p ?o .
         |    }
         |}
         |""".stripMargin
    )
    def findById(id: UserIri) = Construct(
      s"""
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
         |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
         |
         |CONSTRUCT {
         |     ?userIriIri ?p ?o .
         |}
         |WHERE {
         |    GRAPH <$adminGraphIri>{
         |       BIND(IRI("${id.value}") AS ?userIriIri)
         |       ?userIriIri rdf:type knora-admin:User ;
         |          ?p ?o .
         |    }
         |}
         |""".stripMargin
    )
    def create(user: KnoraUser) = Update(
      s"""
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
         |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
         |
         |INSERT {
         |    GRAPH <$adminGraphIri> {
         |        ${toTriples(user)}
         |    }
         |}
         |WHERE
         |{
         |    BIND(IRI("${user.id.value}") AS ?userIri)
         |}
         |""".stripMargin
    )
    def update(user: KnoraUser) = Update(
      s"""
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
         |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
         |
         |WITH <$adminGraphIri>
         |DELETE {
         |  ?userIri knora-admin:username ?previousUsername .
         |  ?userIri knora-admin:email ?previousEmail .
         |  ?userIri knora-admin:givenName ?previousGivenName .
         |  ?userIri knora-admin:familyName ?previousFamilyName .
         |  ?userIri knora-admin:status ?previousStatus .
         |  ?userIri knora-admin:preferredLanguage ?previousPreferredLanguage .
         |  ?userIri knora-admin:isInProject ?previousIsInProject .
         |  ?userIri knora-admin:isInGroup ?previousIsInGroup .
         |  ?userIri knora-admin:isInSystemAdminGroup ?previousIsInSystemAdminGroup .
         |}
         |INSERT {
         |  ${toTriples(user)}
         |}
         |WHERE {
         |  BIND(IRI("${user.id.value}") AS ?userIri)
         |  ?userIri knora-admin:username ?previousUsername .
         |  ?userIri knora-admin:email ?previousEmail .
         |  ?userIri knora-admin:givenName ?previousGivenName .
         |  ?userIri knora-admin:familyName ?previousFamilyName .
         |  ?userIri knora-admin:status ?previousStatus .
         |  ?userIri knora-admin:preferredLanguage ?previousPreferredLanguage .
         |  ?userIri knora-admin:isInProject ?previousIsInProject .
         |  ?userIri knora-admin:isInGroup ?previousIsInGroup .
         |  ?userIri knora-admin:isInSystemAdminGroup ?previousIsInSystemAdminGroup .
         |}
         |""".stripMargin
    )

    private def toTriples(current: KnoraUser) =
      s"""
         |?userIri a knora-admin:User .
         |?userIri knora-admin:username "${current.username.value}"^^xsd:string .
         |?userIri knora-admin:email "${current.email.value}"^^xsd:string .
         |?userIri knora-admin:givenName "${current.givenName.value}"^^xsd:string .
         |?userIri knora-admin:familyName "${current.familyName.value}"^^xsd:string .
         |?userIri knora-admin:password "${current.passwordHash.value}"^^xsd:string .
         |?userIri knora-admin:preferredLanguage "${current.preferredLanguage.value}"^^xsd:string .
         |?userIri knora-admin:status "${current.status.value}"^^xsd:boolean .
         |${current.projects.map(prjIri => s"?userIri knora-admin:isInProject <${prjIri.value}> .").mkString("\n")}
         |${current.groups.map(grpIri => s"?userIri knora-admin:isInGroup <${grpIri.value}> .").mkString("\n")}
         |?userIri knora-admin:isInSystemAdminGroup "${current.isInSystemAdminGroup.value}"^^xsd:boolean .
         |""".stripMargin
  }
  val layer = ZLayer.derive[UserRepoLive]
}
