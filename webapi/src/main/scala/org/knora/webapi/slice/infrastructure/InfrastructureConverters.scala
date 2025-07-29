/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import org.knora.webapi.infrastructure.Shortcode as InfraShortcode
import org.knora.webapi.infrastructure.UserIri as InfraUserIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode as DomainShortcode
import org.knora.webapi.slice.admin.domain.model.UserIri as DomainUserIri

object InfrastructureConverters {
  extension (userIri: DomainUserIri) {
    def toInfrastructure: InfraUserIri = InfraUserIri(userIri.value)
  }

  extension (shortcode: DomainShortcode) {
    def toInfrastructure: InfraShortcode = InfraShortcode(shortcode.value)
  }
}
