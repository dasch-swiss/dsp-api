/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

// Lives in `zio.test.junit` on purpose: driving a ZIO Test spec while emitting
// per-test JUnit events requires zio-test's `private[zio]` `runSpecAsApp`, which is
// unreachable from an application package. This is a trimmed variant of
// zio.test.junit.ZTestJUnitRunner (Apache-2.0) with two deliberate differences:
//   1. `getDescription` is coarse (the spec class only) and does NOT run the suite's
//      effects. The stock runner builds `spec.bootstrap` there AND again in `run`,
//      which would start our container-backed integration specs' containers twice.
//   2. per-test `Description`s carry the spec `Class` (not just its name) so
//      `Description.getTestClass` is non-null — Bazel's JUnit runner calls it and
//      NPEs on a name-only description when a test fails.
// The public entry point is org.knora.testrunner.DspZTestJUnitRunner, a thin subclass.
package zio.test.junit

import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import zio.*
import zio.test.*
import zio.test.TestFailure.Assertion
import zio.test.TestFailure.Runtime
import zio.test.TestSuccess.Ignored
import zio.test.TestSuccess.Succeeded
import zio.test.render.ConsoleRenderer
import zio.test.render.ExecutionResult.ResultType.Test
import zio.test.render.ExecutionResult.Status.Failed
import zio.test.render.LogLine.Message

abstract class DspZTestRunnerBase(klass: Class[?]) extends Runner with Filterable {

  private lazy val spec: ZIOSpecAbstract =
    klass.getDeclaredConstructor().newInstance().asInstanceOf[ZIOSpecAbstract]

  private var filter = Filter.ALL

  // Coarse: the spec class only. Carries the Class (so getTestClass is non-null) and
  // deliberately does not traverse the spec, so `spec.bootstrap` is not forced here.
  override lazy val getDescription: Description = Description.createSuiteDescription(klass)

  override def run(notifier: RunNotifier): Unit = {
    val _ = zio.Runtime.default.unsafe.run {
      val instrumented: Spec[spec.Environment & TestEnvironment & Scope, Any] =
        instrumentSpec(filteredSpec, new JUnitNotifier(notifier))
      spec
        .runSpecAsApp(instrumented, TestArgs.empty, Console.ConsoleLive)
        .provide(
          Scope.default >>> (liveEnvironment >>> TestEnvironment.live ++ ZLayer.environment[Scope] +!+ spec.bootstrap),
        )
    }(using Trace.empty, Unsafe.unsafe).getOrThrowFiberFailure()(using Unsafe.unsafe)
  }

  private def reportRuntimeFailure[E](
    notifier: JUnitNotifier,
    path: Vector[String],
    label: String,
    cause: Cause[E],
  ): UIO[Unit] = {
    val rendered = renderToString(ConsoleRenderer.renderCause(cause, 0))
    notifier.fireTestFailure(label, path, rendered, cause.dieOption.orNull) *> notifier.fireTestFinished(label, path)
  }

  private def reportAssertionFailure(
    notifier: JUnitNotifier,
    path: Vector[String],
    label: String,
    result: TestResult,
  ): UIO[Unit] = {
    val rendered = renderFailureDetails(label, result)
    notifier.fireTestFailure(label, path, renderToString(rendered)) *> notifier.fireTestFinished(label, path)
  }

  private def renderFailureDetails(label: String, result: TestResult): Message =
    Message(
      ConsoleRenderer
        .rendered(Test, label, Failed, 0, ConsoleRenderer.renderAssertionResult(result.result, 0).lines*)
        .streamingLines,
    )

  private def testDescription(label: String, path: Vector[String]): Description = {
    // JUnit 4 has no createTestDescription(Class, name, uniqueId) overload — the
    // uniqueId-bearing form takes a String class name and leaves getTestClass null,
    // which makes Bazel's JUnit runner NPE. Use the Class form (non-null test class)
    // and keep descriptions unique by qualifying the display name with the full path.
    val name = if (path.isEmpty) label else path.mkString(" / ")
    Description.createTestDescription(klass, name)
  }

  private def instrumentSpec[R, E](
    zspec: Spec[R, E],
    notifier: JUnitNotifier,
  ): Spec[R, E] = {
    type ZSpecCase = Spec.SpecCase[R, E, Spec[R, E]]
    def instrumentTest(label: String, path: Vector[String], test: ZIO[R, TestFailure[E], TestSuccess]) =
      test
        .tapBoth(
          {
            case Assertion(result, _) =>
              notifier.fireTestStarted(label, path) *>
                reportAssertionFailure(notifier, path, label, result)
            case Runtime(cause, _) =>
              notifier.fireTestStarted(label, path) *>
                reportRuntimeFailure(notifier, path, label, cause)
          },
          {
            case Succeeded(_) =>
              notifier.fireTestStarted(label, path) *>
                notifier.fireTestFinished(label, path)
            case Ignored(_) => notifier.fireTestIgnored(label, path)
          },
        )
        .tapDefect { e =>
          notifier.fireTestStarted(label, path) *>
            reportRuntimeFailure(notifier, path, label, e)
        }
    def loop(specCase: ZSpecCase, path: Vector[String] = Vector.empty): ZSpecCase =
      specCase match {
        case Spec.ExecCase(exec, spec) =>
          Spec.ExecCase(exec, Spec(loop(spec.caseValue, path)))
        case Spec.LabeledCase(label, spec) =>
          Spec.LabeledCase(label, Spec(loop(spec.caseValue, path :+ label)))
        case Spec.ScopedCase(scoped) =>
          Spec.ScopedCase[R, E, Spec[R, E]](
            scoped.map(spec => Spec(loop(spec.caseValue, path))),
          )
        case Spec.MultipleCase(specs) =>
          Spec.MultipleCase(specs.map(spec => Spec(loop(spec.caseValue, path))))
        case Spec.TestCase(test, annotations) =>
          Spec.TestCase(instrumentTest(path.lastOption.getOrElse(""), path, test), annotations)
      }
    Spec(loop(zspec.caseValue))
  }

  private def filteredSpec =
    spec.spec
      .filterLabels(l => filter.shouldRun(testDescription(l, Vector.empty)))
      .getOrElse(spec.spec)

  override def filter(filter: Filter): Unit =
    this.filter = filter

  private def renderToString(message: Message) =
    message.lines.map {
      _.fragments.map(_.text).fold("")(_ + _)
    }.mkString("\n")

  private class JUnitNotifier(notifier: RunNotifier) {
    def fireTestFailure(
      label: String,
      path: Vector[String],
      renderedText: String,
      throwable: Throwable = null,
    ): UIO[Unit] =
      ZIO.succeed {
        notifier.fireTestFailure(
          new Failure(testDescription(label, path), new TestFailed(renderedText, throwable)),
        )
      }

    def fireTestStarted(label: String, path: Vector[String]): UIO[Unit] = ZIO.succeed {
      notifier.fireTestStarted(testDescription(label, path))
    }

    def fireTestFinished(label: String, path: Vector[String]): UIO[Unit] = ZIO.succeed {
      notifier.fireTestFinished(testDescription(label, path))
    }

    def fireTestIgnored(label: String, path: Vector[String]): UIO[Unit] = ZIO.succeed {
      notifier.fireTestIgnored(testDescription(label, path))
    }
  }
}

private[junit] class TestFailed(message: String, cause: Throwable = null)
    extends Throwable(message, cause, false, false)
