/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.testrunner

import zio.test.junit.DspZTestRunnerBase

import java.nio.file.Files
import java.nio.file.Paths

/**
 * The single custom JUnit 4 runner both sbt (via `junit-interface`) and Bazel
 * (`scala_junit_test`) use to run dsp-api's ZIO Test specs. A spec is a class (JUnit
 * cannot run Scala `object`s) annotated with `@RunWith(classOf[DspZTestJUnitRunner])`.
 *
 * The implementation lives in [[zio.test.junit.DspZTestRunnerBase]] because it needs
 * zio-test internals; this thin subclass keeps the public runner in dsp-api's own
 * namespace so specs reference a DaSCH type, not a zio-test one.
 */
final class DspZTestJUnitRunner(klass: Class[?]) extends DspZTestRunnerBase(klass) {
  DspZTestJUnitRunner.ensureStableTmpDir
}

object DspZTestJUnitRunner {
  // The container-backed test modules need java.io.tmpdir on a stable directory under
  // the real home directory (see the _JVM_FLAGS comment in //modules/test-it:BUILD.bazel).
  // The BUILD flag cannot carry that path itself: Bazel scrubs the test environment and
  // sets HOME to the ephemeral TEST_TMPDIR, so a $HOME reference in jvm_flags resolves to
  // the wrong place. Instead the flag passes a home-relative path via this marker property
  // and the runner resolves it against `user.home` (pwuid-based, unaffected by the env
  // scrubbing), creates it, and repoints java.io.tmpdir. The runner is constructed before
  // any spec code, so this runs before testcontainers, WireMock, or any temp-file use -
  // and before the JDK's temp-file machinery caches the property on first use. Pure-JVM
  // unit-test targets don't set the property and keep Bazel's hermetic TEST_TMPDIR.
  private lazy val ensureStableTmpDir: Unit =
    Option(System.getProperty("dsp.test.tmpdir.in-home")).foreach { relative =>
      val dir = Paths.get(System.getProperty("user.home"), relative)
      Files.createDirectories(dir)
      System.setProperty("java.io.tmpdir", dir.toString)
    }
}
