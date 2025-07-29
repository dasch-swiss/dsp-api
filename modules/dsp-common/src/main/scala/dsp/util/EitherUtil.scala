/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.util

object EitherUtil {
  def joinOnLeftList[A, B](a: Either[List[A], B], b: Either[List[A], B]): Either[List[A], B] =
    (a, b) match {
      case (Left(l), Left(r))   => Left(l ::: r)
      case (Left(l), _)         => Left(l)
      case (_, Left(r))         => Left(r)
      case (Right(b), Right(_)) => Right(b)
    }

  def joinOnLeft[A, B](a: Either[A, B], b: Either[A, B]): Either[List[A], B] =
    (a, b) match {
      case (Left(l), Left(r))   => Left(List(l, r))
      case (Left(l), _)         => Left(List(l))
      case (_, Left(r))         => Left(List(r))
      case (Right(b), Right(_)) => Right(b)
    }
}