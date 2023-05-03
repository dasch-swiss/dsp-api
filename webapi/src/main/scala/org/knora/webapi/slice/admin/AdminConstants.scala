/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin

import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object AdminConstants {
  val adminDataGraph: InternalIri       = InternalIri("http://www.knora.org/data/admin")
  val permissionsDataGraph: InternalIri = InternalIri("http://www.knora.org/data/permissions")
}
