/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import scala.reflect.ClassTag

import PartialFunction.{condOpt, cond}

trait WithAsIs[A] {
  def asOpt[T <: A: ClassTag]: Option[T] = condOpt(this) { case a: T => a }
  def is[T <: A: ClassTag]: Boolean      = cond(this) { case _: T => true }
}
