/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.other.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.util.MutableUserADM

import java.util.UUID
import scala.concurrent.duration._

object DrawingsGodsV1Spec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Test specification for testing a complex permissions structure of the drawings-gods-project.
 */
class DrawingsGodsV1Spec extends CoreSpec(DrawingsGodsV1Spec.config) with TriplestoreJsonProtocol {

  private val timeout = 5.seconds

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_admin-data.ttl",
      name = "http://www.knora.org/data/admin"
    ),
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_permissions-data.ttl",
      name = "http://www.knora.org/data/permissions"
    ),
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_ontology.ttl",
      name = "http://www.knora.org/ontology/0105/drawings-gods"
    ),
    RdfDataObject(
      path = "test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_data.ttl",
      name = "http://www.knora.org/data/0105/drawings-gods"
    )
  )

  /**
   * issues:
   * - https://github.com/dhlab-basel/Knora/issues/416
   * - https://github.com/dhlab-basel/Knora/issues/610
   */
  "Using the DrawingsGods project data" should {

    val drawingsGodsProjectIri  = "http://rdfh.ch/projects/0105"
    val drawingsGodsOntologyIri = "http://www.knora.org/ontology/0105/drawings-gods"
    val rootUserIri             = "http://rdfh.ch/users/root"
    val rootUser                = new MutableUserADM
    val ddd1UserIri             = "http://rdfh.ch/users/drawings-gods-test-ddd1"
    val ddd1                    = new MutableUserADM
    val ddd2UserIri             = "http://rdfh.ch/users/drawings-gods-test-ddd2"
    val ddd2                    = new MutableUserADM

    "retrieve the drawings gods user's profile" in {
      responderManager ! UserGetADM(
        identifier = UserIdentifierADM(maybeIri = Some(rootUserIri)),
        userInformationTypeADM = UserInformationTypeADM.Full,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      rootUser.set(expectMsgType[Option[UserADM]](timeout).get)

      responderManager ! UserGetADM(
        identifier = UserIdentifierADM(maybeIri = Some(ddd1UserIri)),
        userInformationTypeADM = UserInformationTypeADM.Full,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      ddd1.set(expectMsgType[Option[UserADM]](timeout).get)

      responderManager ! UserGetADM(
        UserIdentifierADM(maybeIri = Some(ddd2UserIri)),
        userInformationTypeADM = UserInformationTypeADM.Full,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      ddd2.set(expectMsgType[Option[UserADM]](timeout).get)
    }

    "return correct drawings-gods:QualityData resource permissions string for drawings-gods-test-ddd2 user" in {
      val qualityDataResourceClass = s"$drawingsGodsOntologyIri#QualityData"
      responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
        drawingsGodsProjectIri,
        qualityDataResourceClass,
        targetUser = ddd2.get,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )
      expectMsg(
        DefaultObjectAccessPermissionsStringResponseADM(
          "CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team,knora-admin:Creator|M http://rdfh.ch/groups/0105/drawings-gods-add-drawings,http://rdfh.ch/groups/0105/drawings-gods-meta-annotators"
        )
      )
    }

    "return correct drawings-gods:Person resource class permissions string for drawings-gods-test-ddd1 user" in {
      val personResourceClass = s"$drawingsGodsOntologyIri#Person"
      responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
        drawingsGodsProjectIri,
        personResourceClass,
        targetUser = ddd1.get,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )
      expectMsg(
        DefaultObjectAccessPermissionsStringResponseADM(
          "CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team,knora-admin:Creator|M http://rdfh.ch/groups/0105/drawings-gods-add-drawings,http://rdfh.ch/groups/0105/drawings-gods-meta-annotators|V knora-admin:KnownUser,knora-admin:ProjectMember,knora-admin:UnknownUser"
        )
      )
    }

    "return correct drawings-gods:hasLastname property permissions string for drawings-gods-test-ddd1 user" in {
      val personResourceClass = s"$drawingsGodsOntologyIri#Person"
      val hasLastnameProperty = s"$drawingsGodsOntologyIri#hasLastname"
      responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
        drawingsGodsProjectIri,
        personResourceClass,
        hasLastnameProperty,
        targetUser = ddd1.get,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )
      expectMsg(
        DefaultObjectAccessPermissionsStringResponseADM(
          "CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team"
        )
      )
    }

    "return correct drawings-gods:DrawingPublic / knora-base:hasStillImageFileValue combination permissions string for drawings-gods-test-ddd1 user" in {
      val drawingPublicResourceClass = s"$drawingsGodsOntologyIri#DrawingPublic"
      val hasStillImageFileValue     = OntologyConstants.KnoraBase.HasStillImageFileValue
      responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
        drawingsGodsProjectIri,
        drawingPublicResourceClass,
        hasStillImageFileValue,
        targetUser = ddd1.get,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )
      expectMsg(
        DefaultObjectAccessPermissionsStringResponseADM(
          "CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team|M http://rdfh.ch/groups/0105/drawings-gods-add-drawings|V http://rdfh.ch/groups/0105/drawings-gods-meta-annotators,knora-admin:KnownUser,knora-admin:ProjectMember,knora-admin:UnknownUser"
        )
      )
    }

    "return correct drawings-gods:DrawingPrivate / knora-base:hasStillImageFileValue combination permissions string for drawings-gods-test-ddd1 user" in {
      val drawingPrivateResourceClass = s"$drawingsGodsOntologyIri#DrawingPrivate"
      val hasStillImageFileValue      = OntologyConstants.KnoraBase.HasStillImageFileValue
      responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
        drawingsGodsProjectIri,
        drawingPrivateResourceClass,
        hasStillImageFileValue,
        targetUser = ddd1.get,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )
      expectMsg(
        DefaultObjectAccessPermissionsStringResponseADM(
          "CR http://rdfh.ch/groups/0105/drawings-gods-admin|D http://rdfh.ch/groups/0105/drawings-gods-snf-team|M http://rdfh.ch/groups/0105/drawings-gods-add-drawings,http://rdfh.ch/groups/0105/drawings-gods-meta-annotators|V knora-admin:ProjectMember"
        )
      )
    }

    "allow drawings-gods-test-ddd1 user to create a resource, then query it and see its label and properties" in {

      val valuesToBeCreated = Map(
        s"$drawingsGodsOntologyIri#hasLastname" -> Vector(
          CreateValueV1WithComment(TextValueSimpleV1("PersonTest DDD1"))
        ),
        s"$drawingsGodsOntologyIri#hasCodePerson" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("Code"))),
        s"$drawingsGodsOntologyIri#hasPersonGender" -> Vector(
          CreateValueV1WithComment(
            HierarchicalListValueV1("http://rdfh.ch/lists/0105/drawings-gods-2016-list-FiguresHList-polysexual")
          )
        ),
        s"$drawingsGodsOntologyIri#hasDrawingChildTotal" -> Vector(CreateValueV1WithComment(IntegerValueV1(99)))
      )

      responderManager ! ResourceCreateRequestV1(
        resourceTypeIri = s"$drawingsGodsOntologyIri#Person",
        label = "Test-Person",
        projectIri = drawingsGodsProjectIri,
        values = valuesToBeCreated,
        file = None,
        userProfile = ddd1.get,
        apiRequestID = UUID.randomUUID
      )

      val createResponse = expectMsgType[ResourceCreateResponseV1](timeout)
      val resourceIri    = createResponse.res_id

      responderManager ! ResourceFullGetRequestV1(
        iri = resourceIri,
        userADM = ddd1.get
      )

      val getResponse = expectMsgType[ResourceFullResponseV1](timeout)

      val maybeLabel: Option[String] = getResponse.resinfo.get.firstproperty
      assert(maybeLabel.isDefined, "Response returned no resource label")
      assert(maybeLabel.get == "Test-Person")

      val maybeLastNameProp: Option[PropertyV1] =
        getResponse.props.get.properties.find(prop => prop.pid == s"$drawingsGodsOntologyIri#hasLastname")
      assert(maybeLastNameProp.isDefined, "Response returned no property hasLastname")
      assert(maybeLastNameProp.get.values.head.asInstanceOf[TextValueV1].utf8str == "PersonTest DDD1")
    }

    "allow root user (SystemAdmin) to create a resource" in {

      val valuesToBeCreated = Map(
        s"$drawingsGodsOntologyIri#hasLastname" -> Vector(
          CreateValueV1WithComment(TextValueSimpleV1("PersonTest DDD1"))
        ),
        s"$drawingsGodsOntologyIri#hasCodePerson" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("Code"))),
        s"$drawingsGodsOntologyIri#hasPersonGender" -> Vector(
          CreateValueV1WithComment(
            HierarchicalListValueV1("http://rdfh.ch/lists/0105/drawings-gods-2016-list-FiguresHList-polysexual")
          )
        ),
        s"$drawingsGodsOntologyIri#hasDrawingChildTotal" -> Vector(CreateValueV1WithComment(IntegerValueV1(99)))
      )

      responderManager ! ResourceCreateRequestV1(
        resourceTypeIri = s"$drawingsGodsOntologyIri#Person",
        label = "Test-Person",
        projectIri = drawingsGodsProjectIri,
        values = valuesToBeCreated,
        file = None,
        userProfile = rootUser.get,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ResourceCreateResponseV1](timeout)
    }
  }
}
