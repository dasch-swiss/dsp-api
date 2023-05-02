package org.knora.webapi.slice.admin.api.model

import java.nio.file.Path

/**
 * Represents a project's data in TriG format.
 *
 * @param projectDataFile a file containing the project's data in TriG format.
 */
case class ProjectDataGetResponseADM(projectDataFile: Path)
