/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.trace.ReadableSpan
import org.junit.runner.RunWith
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.core.TestAppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.`export`.domain.CurrentDataTask
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskPersistence
import org.knora.webapi.slice.`export`.domain.DataTaskState
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.`export`.domain.ImportExistsError
import org.knora.webapi.slice.`export`.domain.ImportSlotBusyError
import org.knora.webapi.slice.`export`.domain.ProjectMigrationExportService
import org.knora.webapi.slice.`export`.domain.ProjectMigrationStorageService
import org.knora.webapi.slice.`export`.domain.TaskCreator
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.api.v3.ErrorDetail
import org.knora.webapi.slice.api.v3.V3ErrorCode
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.common.domain.ServiceName
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.OtelSetup
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.slice.security.ExportCaller
import org.knora.webapi.slice.security.ServicePrincipal
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class V3ProjectsRestServiceSpec extends ZIOSpecDefault {

  private val project = TestDataFactory.someProject

  private val dspRepository = ServiceName.unsafeFrom("dsp-repository")
  private val otherService  = ServiceName.unsafeFrom("other-service")

  private val admin: ExportCaller                                             = ExportCaller.Admin(TestDataFactory.User.rootUser)
  private def service(name: ServiceName, kid: String = "key-a"): ExportCaller =
    ExportCaller.Service(ServicePrincipal(name, kid, "jti-1"))

  private val byAdmin: TaskCreator         = TaskCreator.ByUser(TestDataFactory.User.rootUser.userIri)
  private val byDspRepository: TaskCreator = TaskCreator.ByService(dspRepository)
  private val byOtherService: TaskCreator  = TaskCreator.ByService(otherService)

  private val noPersistence = new DataTaskPersistence {
    def onChanged(task: CurrentDataTask): UIO[Unit] = ZIO.unit
    def onDeleted(taskId: DataTaskId): UIO[Unit]    = ZIO.unit
  }

  private final case class Env(rest: V3ProjectsRestService, slot: DataTaskState)

  private type Deps = KnoraProjectService & KnoraProjectRepoInMemory & TriplestoreService & Tracing

  /**
   * The service under test with `existing` in the export slot. The ingest client and the import collaborators are
   * null on purpose: the export handlers never call them (every trigger here skips assets). A handler that starts
   * calling one needs a real fake here, not a null.
   */
  private def envWith(existing: Option[CurrentDataTask]): URIO[Deps, Env] =
    for {
      _           <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project)).orDie
      ref         <- Ref.make(existing)
      slot         = new DataTaskState(ref, noPersistence)
      projects    <- ZIO.service[KnoraProjectService]
      triplestore <- ZIO.service[TriplestoreService]
      tracing     <- ZIO.service[Tracing]
      exports      =
        new ProjectMigrationExportService(slot, null, projects, new ProjectMigrationStorageService(), triplestore)
    } yield Env(new V3ProjectsRestService(exports, null, null, projects, null, null, tracing), slot)

  private def taskBy(creator: TaskCreator, status: DataTaskStatus): UIO[CurrentDataTask] =
    DataTaskId.makeNew.zipWith(Clock.instant)(CurrentDataTask.restore(_, project.id, status, creator, _))

  /** Waits until the export a trigger started has ended, then deletes it, so no export fiber or file outlives the test. */
  private def cleanUp(env: Env, id: DataTaskId): UIO[Unit] =
    env.slot
      .isInProgress(id)
      .repeat(Schedule.recurWhile[Boolean](identity) && Schedule.spaced(50.millis) && Schedule.recurs(200))
      .zipRight(env.rest.deleteProjectExport(admin)(project.id, id).ignore)

  private def codes(e: V3ErrorInfo): Chunk[V3ErrorCode] = e.errors.map(_.code)

  /**
   * The error codes the caller gets from status, download and delete of `task`, in that order. A call that succeeds
   * yields no codes, so it never matches an expected error.
   */
  private def answersTo(env: Env, caller: ExportCaller, task: CurrentDataTask): UIO[List[Chunk[V3ErrorCode]]] =
    ZIO.collectAll(
      List(
        env.rest.getProjectExportStatus(caller)(project.id, task.id).unit,
        env.rest.downloadProjectExport(caller)(project.id, task.id).unit,
        env.rest.deleteProjectExport(caller)(project.id, task.id),
      ).map(_.flip.fold(_ => Chunk.empty, codes)),
    )

  private val notFoundEverywhere = List.fill(3)(Chunk(V3ErrorCode.export_not_found))

  private def foreignExportTest(owner: TaskCreator, label: String) =
    test(s"a service gets 404 on status, download and delete of $label export, in every state, and it stays") {
      ZIO
        .foreach(List(DataTaskStatus.InProgress, DataTaskStatus.Completed, DataTaskStatus.Failed)) { status =>
          for {
            task    <- taskBy(owner, status)
            env     <- envWith(Some(task))
            answers <- answersTo(env, service(dspRepository), task)
            kept    <- env.slot.find(task.id).option
          } yield assertTrue(answers == notFoundEverywhere, kept.contains(task))
        }
        .map(TestResult.allSuccesses(_))
    }

  private val auditLines: UIO[Chunk[String]] =
    ZTestLogger.logOutput.map(_.map(_.message()).filter(_.startsWith("Export service call:")))

  private def auditLine(
    action: String,
    outcome: String,
    exportId: String,
    projectIri: ProjectIri = project.id,
    requestedProjectIri: ProjectIri = project.id,
  ) =
    s"Export service call: action=$action outcome=$outcome service=dsp-repository kid=key-a " +
      s"project_iri=${projectIri.value} requested_project_iri=${requestedProjectIri.value} " +
      s"export_id=$exportId jti=\"jti-1\""

  /** Runs `call` inside a server span, as the tracing middleware does, and returns the attributes it left there. */
  private def spanAttributesOf(call: IO[Any, Any]): URIO[Tracing, Map[String, String]] =
    ZIO.serviceWithZIO[Tracing] { tracing =>
      tracing.span("GET", SpanKind.SERVER) {
        call.ignore *> tracing.getCurrentSpanUnsafe.map {
          case span: ReadableSpan =>
            span.toSpanData.getAttributes.asMap.asScala.map((k, v) => k.getKey -> v.toString).toMap
          case other => throw new IllegalStateException(s"Not an SDK span: $other")
        }
      }
    }

  private val auditSuite = suite("service audit")(
    test("a service's status call writes one audit line with every field") {
      for {
        task  <- taskBy(byDspRepository, DataTaskStatus.Completed)
        env   <- envWith(Some(task))
        _     <- env.rest.getProjectExportStatus(service(dspRepository))(project.id, task.id)
        lines <- auditLines
      } yield assertTrue(lines == Chunk(auditLine("export.status", "ok", task.id.value)))
    },
    test("an ownership refusal is audited as not_owner with both project IRIs; the client gets the unknown-id 404") {
      val requested = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
      for {
        task    <- taskBy(byOtherService, DataTaskStatus.Completed)
        env     <- envWith(Some(task))
        refused <- env.rest.getProjectExportStatus(service(dspRepository))(requested, task.id).flip
        _       <- env.rest.deleteProjectExport(admin)(project.id, task.id)
        unknown <- env.rest.getProjectExportStatus(service(dspRepository))(requested, task.id).flip
        lines   <- auditLines
      } yield assertTrue(
        refused == unknown,
        lines == Chunk(
          auditLine("export.status", "not_owner", task.id.value, requestedProjectIri = requested),
          auditLine(
            "export.status",
            "not_found",
            task.id.value,
            projectIri = requested,
            requestedProjectIri = requested,
          ),
        ),
      )
    },
    test("a failed call is audited once with its outcome") {
      for {
        task  <- taskBy(byDspRepository, DataTaskStatus.Failed)
        env   <- envWith(Some(task))
        _     <- env.rest.downloadProjectExport(service(dspRepository))(project.id, task.id).flip
        lines <- auditLines
      } yield assertTrue(lines == Chunk(auditLine("export.download", "conflict", task.id.value)))
    },
    test("a trigger is audited with the new export's id, a slot collision as conflict") {
      for {
        env       <- envWith(None)
        triggered <- env.rest.triggerProjectExportCreate(service(dspRepository))(project.id, skipAssets = true)
        _         <- env.rest.triggerProjectExportCreate(service(dspRepository))(project.id, skipAssets = true).flip
        _         <- cleanUp(env, triggered.id)
        lines     <- auditLines
      } yield assertTrue(
        lines == Chunk(
          auditLine("export.create", "ok", triggered.id.value),
          auditLine("export.create", "conflict", triggered.id.value),
        ),
      )
    },
    test("a service call sets exactly the service name, kid, action and project shortcode on the span") {
      for {
        task       <- taskBy(byDspRepository, DataTaskStatus.Completed)
        env        <- envWith(Some(task))
        attributes <- spanAttributesOf(env.rest.getProjectExportStatus(service(dspRepository))(project.id, task.id))
      } yield assertTrue(
        attributes == Map(
          "dsp.service.name"      -> "dsp-repository",
          "dsp.service.kid"       -> "key-a",
          "dsp.service.action"    -> "export.status",
          "dsp.project.shortcode" -> project.shortcode.value,
        ),
      )
    },
    test("a refused service call gets no shortcode from the export it may not see") {
      for {
        task       <- taskBy(byAdmin, DataTaskStatus.Completed)
        env        <- envWith(Some(task))
        attributes <- spanAttributesOf(env.rest.getProjectExportStatus(service(dspRepository))(project.id, task.id))
      } yield assertTrue(attributes.keySet == Set("dsp.service.name", "dsp.service.kid", "dsp.service.action"))
    },
    test("each export action by a service writes exactly one audit line, a defect included") {
      val caller = service(dspRepository)
      // The download of a completed export whose file is missing dies, which exercises the defect path.
      val calls = List[(String, String, (Env, DataTaskId) => IO[Any, Any])](
        ("export.create", "conflict", (env, _) => env.rest.triggerProjectExportCreate(caller)(project.id, true)),
        ("export.status", "ok", (env, id) => env.rest.getProjectExportStatus(caller)(project.id, id)),
        ("export.download", "error", (env, id) => env.rest.downloadProjectExport(caller)(project.id, id)),
        ("export.delete", "ok", (env, id) => env.rest.deleteProjectExport(caller)(project.id, id)),
      )
      ZIO
        .foreach(calls) { case (action, outcome, call) =>
          for {
            task   <- taskBy(byDspRepository, DataTaskStatus.Completed)
            env    <- envWith(Some(task))
            before <- auditLines
            _      <- call(env, task.id).exit
            after  <- auditLines
          } yield assertTrue(
            after.drop(before.size).map(_.split(' ').slice(3, 5).mkString(" ")) ==
              Chunk(s"action=$action outcome=$outcome"),
          )
        }
        .map(TestResult.allSuccesses(_))
    },
    test("a defect or an interruption is audited as error") {
      val facts = V3ProjectsRestService.ExportCallFacts()
      assertTrue(
        V3ProjectsRestService.outcomeOf(Exit.die(new RuntimeException("boom")), facts) == ExportOutcome.Error,
        V3ProjectsRestService.outcomeOf(Exit.interrupt(FiberId.None), facts) == ExportOutcome.Error,
      )
    },
    test("the jti is one quoted field: quotes and backslashes escaped, line breaks and bidi overrides blanked") {
      val jti = "a\"b\\c\nd\u2028e\u2029f\u202eg"
      assertTrue(ExportServiceAudit.quoted(jti) == "\"a\\\"b\\\\c d e f g\"")
    },
    test("a SystemAdmin call writes no audit line and sets no service attributes") {
      for {
        task       <- taskBy(byDspRepository, DataTaskStatus.Completed)
        env        <- envWith(Some(task))
        attributes <- spanAttributesOf(env.rest.getProjectExportStatus(admin)(project.id, task.id))
        lines      <- auditLines
      } yield assertTrue(attributes.isEmpty, lines.isEmpty)
    },
  )

  private val importConflictSuite = suite("V3ProjectsRestService.importConflict")(
    test("a busy slot maps to import_running without details") {
      val conflict = V3ProjectsRestService.importConflict(ImportSlotBusyError())
      val message  = V3ErrorCode.import_running.template
      assertTrue(
        conflict.message == message,
        conflict.errors == Chunk(ErrorDetail(V3ErrorCode.import_running, message, Map.empty)),
      )
    },
    test("an existing import task maps to import_exists with its id and project") {
      for {
        task    <- CurrentDataTask.makeNew(TestDataFactory.someProject.id, byAdmin)
        conflict = V3ProjectsRestService.importConflict(ImportExistsError(task))
        details  = Map("id" -> task.id.value, "projectIri" -> task.projectIri.value)
        message  = V3ErrorCode.import_exists.template
                    .replace("{id}", task.id.value)
                    .replace("{projectIri}", task.projectIri.value)
      } yield assertTrue(
        conflict.message == message,
        conflict.errors == Chunk(ErrorDetail(V3ErrorCode.import_exists, message, details)),
      )
    },
  )

  private val exportCallerSuite = suite("V3ProjectsRestService export handlers")(
    test("a service sees the status of its own export") {
      for {
        task   <- taskBy(byDspRepository, DataTaskStatus.InProgress)
        env    <- envWith(Some(task))
        status <- env.rest.getProjectExportStatus(service(dspRepository))(project.id, task.id)
      } yield assertTrue(status.id == task.id, status.createdBy == byDspRepository)
    },
    foreignExportTest(byAdmin, "a user's"),
    foreignExportTest(byOtherService, "another service's"),
    test("a service that triggers while a user's export holds the slot gets export_exists with its id and project") {
      for {
        task  <- taskBy(byAdmin, DataTaskStatus.Completed)
        env   <- envWith(Some(task))
        error <- env.rest.triggerProjectExportCreate(service(dspRepository))(project.id, skipAssets = true).flip
        slot  <- env.slot.find(task.id).option
      } yield assertTrue(
        error == V3ProjectsRestService.conflict(V3ErrorCode.export_exists, task.projectIri, task.id),
        slot.contains(task),
      )
    },
    test("a service that triggers while another service's export holds the slot gets export_exists with its id") {
      for {
        task  <- taskBy(byOtherService, DataTaskStatus.InProgress)
        env   <- envWith(Some(task))
        error <- env.rest.triggerProjectExportCreate(service(dspRepository))(project.id, skipAssets = true).flip
      } yield assertTrue(
        codes(error) == Chunk(V3ErrorCode.export_exists),
        error.errors.head.details.get("id").contains(task.id.value),
      )
    },
    test("a service that triggers with its own export in the slot gets export_exists, then deletes and re-triggers") {
      val caller = service(dspRepository)
      for {
        task      <- taskBy(byDspRepository, DataTaskStatus.Completed)
        env       <- envWith(Some(task))
        error     <- env.rest.triggerProjectExportCreate(caller)(project.id, skipAssets = true).flip
        _         <- env.rest.deleteProjectExport(caller)(project.id, task.id)
        triggered <- env.rest.triggerProjectExportCreate(caller)(project.id, skipAssets = true)
        _         <- cleanUp(env, triggered.id)
      } yield assertTrue(
        codes(error) == Chunk(V3ErrorCode.export_exists),
        error.errors.head.details.get("id").contains(task.id.value),
        triggered.id != task.id,
        triggered.createdBy == byDspRepository,
      )
    },
    test("a service's own failed export: status works, download gives export_failed, delete works") {
      val caller = service(dspRepository)
      for {
        task    <- taskBy(byDspRepository, DataTaskStatus.Failed)
        env     <- envWith(Some(task))
        status  <- env.rest.getProjectExportStatus(caller)(project.id, task.id)
        error   <- env.rest.downloadProjectExport(caller)(project.id, task.id).flip
        _       <- env.rest.deleteProjectExport(caller)(project.id, task.id)
        deleted <- env.slot.find(task.id).option
      } yield assertTrue(
        status.status == DataTaskStatus.Failed,
        codes(error) == Chunk(V3ErrorCode.export_failed),
        deleted.isEmpty,
      )
    },
    test("an export created under one key is the service's under another key of the same name") {
      for {
        task   <- taskBy(byDspRepository, DataTaskStatus.Completed)
        env    <- envWith(Some(task))
        status <- env.rest.getProjectExportStatus(service(dspRepository, kid = "key-b"))(project.id, task.id)
      } yield assertTrue(status.id == task.id)
    },
    test("a SystemAdmin sees and deletes a service's export") {
      for {
        task    <- taskBy(byDspRepository, DataTaskStatus.Completed)
        env     <- envWith(Some(task))
        status  <- env.rest.getProjectExportStatus(admin)(project.id, task.id)
        _       <- env.rest.deleteProjectExport(admin)(project.id, task.id)
        deleted <- env.slot.find(task.id).option
      } yield assertTrue(status.createdBy == byDspRepository, deleted.isEmpty)
    },
    test("a SystemAdmin that triggers while a service's export holds the slot gets export_exists with its id") {
      for {
        task  <- taskBy(byDspRepository, DataTaskStatus.InProgress)
        env   <- envWith(Some(task))
        error <- env.rest.triggerProjectExportCreate(admin)(project.id, skipAssets = true).flip
      } yield assertTrue(
        codes(error) == Chunk(V3ErrorCode.export_exists),
        error.errors.head.details.get("id").contains(task.id.value),
      )
    },
    test("a SystemAdmin's trigger records the admin as creator") {
      for {
        env       <- envWith(None)
        triggered <- env.rest.triggerProjectExportCreate(admin)(project.id, skipAssets = true)
        _         <- cleanUp(env, triggered.id)
      } yield assertTrue(triggered.createdBy == byAdmin)
    },
    auditSuite,
  ).provide(
    KnoraProjectService.layer,
    KnoraProjectRepoInMemory.layer,
    LicenseRepo.layer,
    OntologyRepoInMemory.emptyLayer,
    IriConverter.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.emptyLayer,
    OtelSetup.stdOut,
  ) @@ TestAspect.fromLayer(TestAppConfig.layer()) @@ TestAspect.withLiveRandom @@ TestAspect.withLiveClock

  def spec: Spec[Any, Any] = suite("V3ProjectsRestService")(importConflictSuite, exportCallerSuite)
}
