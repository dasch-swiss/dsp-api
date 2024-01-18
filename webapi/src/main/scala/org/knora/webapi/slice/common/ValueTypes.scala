/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.knora.webapi.slice.common.Value.StringValue

trait Value[A] extends Any {
  def value: A
}

object Value {
  type StringValue  = Value[String]
  type BooleanValue = Value[Boolean]
}

trait WithFrom[-I, +A] {

  def from(in: I): Either[String, A]

  final def unsafeFrom(in: I): A =
    from(in).fold(e => throw new IllegalArgumentException(e), identity)
}

trait StringValueCompanion[A <: StringValue] extends WithFrom[String, A]
