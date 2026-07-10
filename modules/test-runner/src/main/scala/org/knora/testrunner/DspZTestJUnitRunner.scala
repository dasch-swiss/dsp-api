/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.testrunner

import zio.test.junit.DspZTestRunnerBase

/**
 * The single custom JUnit 4 runner both sbt (via `junit-interface`) and Bazel
 * (`scala_junit_test`) use to run dsp-api's ZIO Test specs. A spec is a class (JUnit
 * cannot run Scala `object`s) annotated with `@RunWith(classOf[DspZTestJUnitRunner])`.
 *
 * The implementation lives in [[zio.test.junit.DspZTestRunnerBase]] because it needs
 * zio-test internals; this thin subclass keeps the public runner in dsp-api's own
 * namespace so specs reference a DaSCH type, not a zio-test one.
 */
final class DspZTestJUnitRunner(klass: Class[?]) extends DspZTestRunnerBase(klass)
