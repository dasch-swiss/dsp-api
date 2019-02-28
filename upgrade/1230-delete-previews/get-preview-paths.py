#!/usr/bin/env python3

# Copyright @ 2015-2019 the contributors (see Contributors.md).
#
# This file is part of Knora.
#
# Knora is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Knora is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public
# License along with Knora.  If not, see <http://www.gnu.org/licenses/>.


#######################################################################################
# Reads the file paths of all preview image file values and writes them to a text file.
#######################################################################################


import requests
import argparse
import getpass
import re


# Makes a request to GraphDB to get data about preview file values, and writes that data to a text file.
def do_request(graphdb_url, username, password, output):
    file_value_iri_regex = re.compile(r"^http://rdfh.ch/([0-9A-F]+)/[A-Za-z0-9_-]+/values/[A-Za-z0-9_-]+$")

    with open("sparql/get-preview-data.rq", 'r') as request_file:
        sparql = request_file.read()

    headers = {
        "Accept": "application/sparql-results+json"
    }

    data = {
        "query": sparql
    }

    r = requests.post(graphdb_url, data=data, headers=headers, auth=(username, password))
    r.raise_for_status()
    json_response = r.json()

    bindings = json_response["results"]["bindings"]

    with open(output, "w") as output_file:
        for row in bindings:
            value_iri = row["fileValue"]["value"]
            filename = row["filename"]["value"]
            match = file_value_iri_regex.match(value_iri)

            if match is None:
                raise Exception("Could not parse value IRI:", value_iri)

            project_shortcode = match.group(1)
            output_file.write("{}/{}\n".format(project_shortcode, filename))


# Command-line invocation.
def main():
    default_graphdb_host = "localhost"
    defaut_repository = "knora-test"
    default_output_file = "preview-paths.txt"

    parser = argparse.ArgumentParser(description="Gets the file paths of preview images.")
    parser.add_argument("-g", "--graphdb", help="GraphDB host (default '{}')".format(default_graphdb_host), type=str)
    parser.add_argument("-r", "--repository", help="GraphDB repository (default '{}')".format(defaut_repository), type=str)
    parser.add_argument("-u", "--username", help="GraphDB username", type=str, required=True)
    parser.add_argument("-p", "--password", help="GraphDB password (if not provided, will prompt for password)", type=str)
    parser.add_argument("-o", "--output", help="output file (default '{}')".format(default_output_file), type=str)

    args = parser.parse_args()
    graphdb_host = args.graphdb

    if not graphdb_host:
        graphdb_host = default_graphdb_host

    repository = args.repository

    if not repository:
        repository = defaut_repository

    graphdb_url = "http://{}:7200/repositories/{}".format(graphdb_host, repository)
    password = args.password

    if not password:
        password = getpass.getpass()

    output = args.output

    if not output:
        output = default_output_file

    do_request(
        graphdb_url=graphdb_url,
        username=args.username,
        password=password,
        output=output
    )


if __name__ == "__main__":
    main()
