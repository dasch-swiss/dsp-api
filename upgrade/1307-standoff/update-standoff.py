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
# Adds knora-base:valueHasMaxStandoffStartIndex to the repository.
#######################################################################################


import requests
import argparse
import getpass
import time
from datetime import timedelta


def do_sparql_update_request(graphdb_url, username, password, sparql_file_path):
    with open(sparql_file_path, 'r') as request_file:
        sparql = request_file.read()

    data = {
        "update": sparql
    }

    r = requests.post(graphdb_url, data=data, auth=(username, password))
    r.raise_for_status()


def do_update(graphdb_url, username, password):
    start = time.time()

    print("Updating knora-base...")

    do_sparql_update_request(
        graphdb_url=graphdb_url,
        username=username,
        password=password,
        sparql_file_path="sparql/update-knora-base.rq"
    )

    print("Updating text values...")

    do_sparql_update_request(
        graphdb_url=graphdb_url,
        username=username,
        password=password,
        sparql_file_path="sparql/update-standoff.rq"
    )

    elapsed = time.time() - start
    print("Update complete. Elapsed time: {}.".format(str(timedelta(seconds=elapsed))))


# Command-line invocation.
def main():
    default_graphdb_host = "localhost"
    default_repository = "knora-test"

    parser = argparse.ArgumentParser(description="Updates knora-base and text values that have standoff markup.")
    parser.add_argument("-g", "--graphdb", help="GraphDB host (default '{}')".format(default_graphdb_host), type=str)
    parser.add_argument("-r", "--repository", help="GraphDB repository (default '{}')".format(default_repository), type=str)
    parser.add_argument("-u", "--username", help="GraphDB username", type=str, required=True)
    parser.add_argument("-p", "--password", help="GraphDB password (if not provided, will prompt for password)", type=str)

    args = parser.parse_args()
    graphdb_host = args.graphdb

    if not graphdb_host:
        graphdb_host = default_graphdb_host

    repository = args.repository

    if not repository:
        repository = default_repository

    graphdb_url = "http://{}:7200/repositories/{}/statements".format(graphdb_host, repository)
    password = args.password

    if not password:
        password = getpass.getpass()

    do_update(
        graphdb_url=graphdb_url,
        username=args.username,
        password=password
    )


if __name__ == "__main__":
    main()
