/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import java.nio.file.Path

/**
 * Represents a project's data in TriG format.
 *
 * @param projectDataFile a file containing the project's data in TriG format.
 */
case class ProjectDataGetResponseADM(projectDataFile: Path)
