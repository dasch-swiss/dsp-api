/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.AuthScope

final case class Principal(
  subject: String,
  scope: AuthScope = AuthScope.Empty,
  jwtRaw: String = "",
)
