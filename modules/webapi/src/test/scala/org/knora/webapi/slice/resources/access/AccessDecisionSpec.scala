/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.IRI
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess as Level
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.access.AccessDecision.*
import org.knora.webapi.slice.resources.access.AccessRequest.*

/**
 * The decision table of the object-access gate. The expected outcomes come from a hand-written oracle: the level each
 * test caller holds under each literal, and the levels each request case requires, in the order the gate checks them.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class AccessDecisionSpec extends ZIOSpecDefault {

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  private val project: KnoraProject = TestDataFactory.someProject
  private val projectIri: IRI       = project.id.value
  private val otherProject          =
    TestDataFactory.someProject.copy(id = KnoraProject.ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002"))

  private val resourceIri   = ResourceIri.unsafeFrom("http://rdfh.ch/0001/requested-resource")
  private val valueIri      = ValueIri.unsafeFrom("http://rdfh.ch/0001/requested-resource/values/requested-value")
  private val property      = PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri)
  private val resourceClass =
    ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)

  private def group(name: String): IRI = s"http://www.knora.org/ontology/knora-admin#$name"

  private def user(name: String, groups: Map[IRI, Seq[IRI]], admin: Set[Permission.Administrative] = Set.empty): User =
    TestDataFactory.User.rootUser.copy(
      id = s"http://rdfh.ch/users/$name",
      username = name,
      email = s"$name@example.com",
      permissions = PermissionsDataADM(
        groupsPerProject = groups,
        administrativePermissionsPerProject =
          if (admin.isEmpty) Map.empty else Map(projectIri -> admin.map(PermissionADM.from)),
      ),
    )

  private val creator      = user("creator", Map.empty)
  private val member       = user("member", Map(projectIri -> Seq(group("ProjectMember"))))
  private val projectAdmin = user(
    "project-admin",
    Map(projectIri -> Seq(group("ProjectMember"), group("ProjectAdmin"))),
    Set(Permission.Administrative.ProjectAdminAll, Permission.Administrative.ProjectResourceCreateAll),
  )
  private val systemAdmin =
    user(
      "system-admin",
      Map(TestDataFactory.Project.systemProjectIri -> Seq(KnoraGroupRepo.builtIn.SystemAdmin.id.value)),
    )

  private val callers = List(creator, member, projectAdmin, systemAdmin)

  /** Each literal with the level it gives the creator and the project member; both admins always hold CR. */
  private val literals: List[(String, Option[Level], Option[Level])] = List(
    ("CR knora-admin:Creator", Some(Level.ChangeRights), None),
    ("D knora-admin:ProjectMember", None, Some(Level.Delete)),
    ("M knora-admin:KnownUser", Some(Level.Modify), Some(Level.Modify)),
    ("V knora-admin:UnknownUser", Some(Level.View), Some(Level.View)),
    ("RV knora-admin:KnownUser", Some(Level.RestrictedView), Some(Level.RestrictedView)),
    ("CR http://rdfh.ch/groups/0001/no-caller-is-in-it", None, None),
  )

  private def levelOf(caller: User, literal: String): Option[Level] =
    if (caller == projectAdmin || caller == systemAdmin) Some(Level.ChangeRights)
    else
      literals.collectFirst {
        case (l, forCreator, forMember) if l == literal => if (caller == creator) forCreator else forMember
      }.flatten

  private val differentLiteral = "CR knora-admin:ProjectAdmin"

  private enum Change { case Unchanged, Same, Different }

  private def requestsOf(change: String => PermissionsChange): List[AccessRequest[?]] = List(
    CreateValue(resourceIri, property),
    UpdateValue(resourceIri, property, valueIri, change("value")),
    ChangeValueRights(resourceIri, property, valueIri),
    DeleteValue(resourceIri, property, valueIri),
    EraseValue(resourceIri, property, valueIri, project, onlyHistory = false),
    ReorderValues(resourceIri, property),
    UpdateResource(resourceIri, change("resource")),
    DeleteResource(resourceIri),
    CreateResource(project, resourceClass),
  )

  /** The levels a case requires, in the gate's order, and whether it names a value. */
  private def required(
    request: AccessRequest[?],
    resourceLit: String,
    valueLit: Option[String],
    link: Boolean,
  ): (Boolean, Seq[(Level, On)]) = {
    def changed(p: PermissionsChange, current: String) = p match {
      case PermissionsChange.Unchanged   => false
      case PermissionsChange.To(literal) => literal != current
    }
    val viewResource = Level.View -> On.Resource
    request match {
      case _: CreateValue => (false, Seq(Level.Modify -> On.Resource))
      case r: UpdateValue =>
        val level =
          if (valueLit.exists(changed(r.permissions, _))) Level.ChangeRights else Level.Modify
        (true, Seq(viewResource, level -> On.Value) ++ Option.when(link)(Level.Modify -> On.Resource))
      case _: ChangeValueRights => (true, Seq(viewResource, Level.ChangeRights -> On.Value))
      case _: DeleteValue       => (true, Seq(viewResource, Level.Delete -> On.Value))
      case _: EraseValue        => (true, Seq(viewResource, Level.Delete -> On.Value))
      case _: ReorderValues     => (false, Seq(Level.Modify -> On.Resource))
      case r: UpdateResource    =>
        (
          false,
          Seq(Level.Modify -> On.Resource) ++
            Option.when(changed(r.permissions, resourceLit))(Level.ChangeRights -> On.Resource),
        )
      case _: DeleteResource => (false, Seq(Level.Delete -> On.Resource))
      case _: CreateResource => (false, Seq.empty)
    }
  }

  private def expected(
    caller: User,
    request: AccessRequest[?],
    resourceLit: String,
    valueLit: Option[String],
    link: Boolean,
  ): AccessDecision = request match {
    case _: CreateResource =>
      if (caller == projectAdmin || caller == systemAdmin) Permit else CreateDenied
    case _ =>
      val onResource           = levelOf(caller, resourceLit)
      val onValue              = valueLit.flatMap(levelOf(caller, _))
      val (namesValue, levels) = required(request, resourceLit, valueLit, link)
      if (onResource.isEmpty) HiddenResource
      else if (namesValue && onValue.isEmpty) MissingValue
      else
        levels.collectFirst {
          case (level, on) if !(if (on == On.Resource) onResource else onValue).exists(_ >= level) =>
            LevelDenied(level, on)
        }.getOrElse(Permit)
  }

  private final case class Row(
    request: AccessRequest[?],
    caller: User,
    resourceLit: String,
    valueLit: Option[String],
    link: Boolean,
  )

  private val valueLiterals = None :: literals.map(l => Some(l._1))

  private val rows: List[Row] = for {
    change      <- Change.values.toList
    resourceLit <- literals.map(_._1)
    valueLit    <- valueLiterals
    caller      <- callers
    link        <- List(false, true)
    request     <- requestsOf {
                 case "value" => submitted(change, valueLit.getOrElse(resourceLit))
                 case _       => submitted(change, resourceLit)
               }
  } yield Row(request, caller, resourceLit, valueLit, link)

  private def submitted(change: Change, current: String): PermissionsChange = change match {
    case Change.Unchanged => PermissionsChange.Unchanged
    case Change.Same      => PermissionsChange.To(current)
    case Change.Different => PermissionsChange.To(differentLiteral)
  }

  private def decideRow(row: Row, creatorIri: IRI = creator.id): AccessDecision =
    AccessDecision.decide(
      row.caller,
      row.request,
      Some(
        Loaded(
          project.id,
          Protection(creatorIri, row.resourceLit),
          row.valueLit.map(Protection(creatorIri, _)),
        ),
      ),
      row.link,
    )

  private val genRow: Gen[Any, Row] = Gen.fromIterable(rows)

  val spec: Spec[Any, Any] = suite("AccessDecision.decide")(
    test("has one row per combination of request case, literals, caller, link property and permissions change") {
      val cases   = requestsOf(_ => PermissionsChange.Unchanged).size
      val product = cases * Change.values.size * literals.size * valueLiterals.size * callers.size * 2
      val count   = rows.size
      assertTrue(cases == 9, count == product)
    },
    test("decides every row as the oracle does") {
      val mismatches =
        rows.filter(row => decideRow(row) != expected(row.caller, row.request, row.resourceLit, row.valueLit, row.link))
      assertTrue(mismatches.isEmpty)
    },
    test("answers a missing resource as missing, for every case but resource creation") {
      val decisions = requestsOf(_ => PermissionsChange.Unchanged).map(request =>
        request -> AccessDecision.decide(member, request, None, isLinkProperty = false),
      )
      val asExpected = decisions.forall {
        case (_: CreateResource, decision) => decision == CreateDenied
        case (_, decision)                 => decision == MissingResource
      }
      assertTrue(asExpected)
    },
    test("asks for ChangeRights only when the submitted literal grants different permissions") {
      val current                  = "CR knora-admin:Creator|M knora-admin:ProjectMember"
      val reordered                = "M knora-admin:ProjectMember|CR knora-admin:Creator"
      val loaded                   = Some(Loaded(project.id, Protection(creator.id, current), Some(Protection(creator.id, current))))
      def decided(literal: String) = List(
        AccessDecision
          .decide(member, UpdateValue(resourceIri, property, valueIri, PermissionsChange.To(literal)), loaded, false),
        AccessDecision.decide(member, UpdateResource(resourceIri, PermissionsChange.To(literal)), loaded, false),
      )
      val same      = decided(reordered)
      val different = decided(differentLiteral)
      assertTrue(
        same == List(Permit, Permit),
        different == List(LevelDenied(Level.ChangeRights, On.Value), LevelDenied(Level.ChangeRights, On.Resource)),
      )
    },
    test("answers erasing a value through another project's shortcode as a wrong project") {
      val request = EraseValue(resourceIri, property, valueIri, otherProject, onlyHistory = true)
      val loaded  = Loaded(
        project.id,
        Protection(creator.id, "CR knora-admin:Creator"),
        Some(Protection(creator.id, "CR knora-admin:Creator")),
      )
      val decision            = AccessDecision.decide(creator, request, Some(loaded), isLinkProperty = false)
      val namesRequestProject =
        AccessDecision.failure(request, decision).exists(_.getMessage.contains("http://rdfh.ch/projects/0002"))
      assertTrue(decision == WrongProject, namesRequestProject)
    },
    test("answers a hidden value as a missing one, with the same message") {
      checkAll(genRow.filter(_.valueLit.isDefined)) { row =>
        val hidden   = row.copy(valueLit = Some("CR http://rdfh.ch/groups/0001/no-caller-is-in-it"))
        val missing  = row.copy(valueLit = None)
        val pair     = List(hidden, missing).filter(r => r.caller == creator || r.caller == member)
        val outcomes =
          pair.map(r => decideRow(r) -> AccessDecision.failure(r.request, decideRow(r)).map(_.getMessage)).distinct
        assertTrue(outcomes.size <= 1)
      }
    },
    test("never names an IRI that is not in the request") {
      val iriPattern = "<([^>]+)>".r
      checkAll(genRow) { row =>
        val loadedCreator = "http://rdfh.ch/users/LOADED-creator"
        val messages      = AccessDecision.failure(row.request, decideRow(row, loadedCreator)).map(_.getMessage).toList
        val named         = messages.flatMap(m => iriPattern.findAllMatchIn(m).map(_.group(1)))
        val noLoadedIri   = messages.forall(!_.contains("LOADED"))
        val onlyRequested = named.forall(iri => row.request.toString.contains(iri) || iri == project.id.value)
        assertTrue(noLoadedIri, onlyRequested)
      }
    },
    test("never turns a permit into a denial when the caller's permission rises") {
      val raised = Map(
        member       -> List(projectAdmin, systemAdmin),
        creator      -> List(projectAdmin, systemAdmin),
        projectAdmin -> List(systemAdmin),
        systemAdmin  -> Nil,
      )
      checkAll(genRow) { row =>
        val permitted = decideRow(row) == Permit
        val higher    = raised(row.caller).map(c => decideRow(row.copy(caller = c)))
        val monotone  = !permitted || higher.forall(_ == Permit)
        assertTrue(monotone)
      }
    },
  )
}
