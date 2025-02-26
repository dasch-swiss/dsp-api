/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core
import zio.ULayer

import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SharedVolumes
import org.knora.webapi.testcontainers.SipiTestContainer

object TestContainerLayers {
  val all: ULayer[SharedVolumes.Volumes & DspIngestTestContainer & FusekiTestContainer & SipiTestContainer] =
    SharedVolumes.layer >+> DspIngestTestContainer.layer >+> FusekiTestContainer.layer >+> SipiTestContainer.layer
}
