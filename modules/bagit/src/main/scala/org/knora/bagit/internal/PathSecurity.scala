/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import org.knora.bagit.BagItError

object PathSecurity {

  def validateEntryName(name: String): Either[BagItError.PathTraversalDetected, String] =
    if (name.contains("..")) Left(BagItError.PathTraversalDetected(name))
    else if (name.startsWith("/")) Left(BagItError.PathTraversalDetected(name))
    else if (name.startsWith("~")) Left(BagItError.PathTraversalDetected(name))
    else if (name.contains("\\")) Left(BagItError.PathTraversalDetected(name))
    else if (name.matches("^[A-Za-z]:.*")) Left(BagItError.PathTraversalDetected(name))
    else if (name.contains("\u0000")) Left(BagItError.PathTraversalDetected(name))
    else Right(name)
}
