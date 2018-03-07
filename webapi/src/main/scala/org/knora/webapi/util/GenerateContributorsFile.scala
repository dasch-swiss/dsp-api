/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import scala.io.Source
import java.net.{URL, URLConnection}

import org.knora.webapi.AssertionException
import org.knora.webapi.twirl.Contributor
import spray.json._
import java.io.File

/**
  * Generates the file Contributors.md, using the GitHub API. Takes one argument, which is a GitHub API key.
  */
object GenerateContributorsFile extends App {

    // Configuration

    val contributorsUrl = "https://api.github.com/repos/dhlab-basel/Knora/contributors"

    if (args.isEmpty) {
        println("Usage: GenerateContributorsFile TOKEN")
        System.exit(1)
    }

    val gitHubApiToken = args(0)

    val headers: Map[String, String] = Map(
        "Authorization" -> s"token $gitHubApiToken"
    )

    // Get the list of contributors.

    val contributorsJson = getFromGitHubApi(contributorsUrl)

    val contributors: Vector[Contributor] = contributorsJson.asInstanceOf[JsArray].elements.map {
        case elem: JsObject =>
            Contributor(
                login = elem.fields("login").asInstanceOf[JsString].value,
                apiUrl = elem.fields("url").asInstanceOf[JsString].value,
                htmlUrl = elem.fields("html_url").asInstanceOf[JsString].value,
                contributions = elem.fields("contributions").asInstanceOf[JsNumber].value.toInt
            )

        case other => throw AssertionException(s"Expected JsObject, got $other")
    }

    // Get the names of the contributors.

    val contributorsWithNames: Seq[Contributor] = contributors.map {
        contributor =>
            val userJson = getFromGitHubApi(contributor.apiUrl)

            contributor.copy(
                name = Some(userJson.asJsObject.fields("name").asInstanceOf[JsString].value)
            )
    }

    // Sort them by number of contributions.
    val contributorsSorted = contributorsWithNames.sortBy(_.contributions).reverse

    // Generate Markdown.
    val contributorsText: String = queries.util.txt.generateContributorsMarkdown(contributorsSorted).toString

    // Write Contributors.md.
    FileUtil.writeTextFile(new File("../Contributors.md"), contributorsText)

    /**
      * Makes an HTTP GET connection to the GitHub API.
      *
      * @param url a GitHub API URL.
      * @return the response, parsed as JSON.
      */
    private def getFromGitHubApi(url: String): JsValue = {
        val connection: URLConnection = new URL(url).openConnection

        headers.foreach({
            case (name, value) => connection.setRequestProperty(name, value)
        })

        val responseStr: String = Source.fromInputStream(connection.getInputStream).mkString

        // Parse the JSON response.

        JsonParser(responseStr)
    }
}
