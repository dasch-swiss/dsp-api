#!/usr/bin/env python3

# Copyright © 2015-2019 the contributors (see Contributors.md).
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


#################################################################################################
# Knora ARK redirect server and conversion utility
#################################################################################################


import re
import argparse
from urllib import parse
import configparser
from string import Template
from sanic import Sanic, response


class ArkUrlException(Exception):
    def __init__(self, message):
        self.message = message


#################################################################################################
# Functions for generating and parsing Knora ARK URLs.

knora_ark_version = 1
resource_iri_regex = re.compile(r"^http://rdfh.ch/([0-9A-F]+)/([A-Za-z0-9_-]+)$")
resource_int_id_factor = 982451653


# Represents the information retrieved from a Knora ARK URL.
class ArkUrlInfo:
    ark_url_regex = re.compile(r"^http://ark.dasch.swiss/ark:/72163/([0-9]+)(?:/([0-9A-F]+)(?:/([A-Za-z0-9_=]+)(?:\.((?:-?(?:[1-9][0-9]*)?[0-9]{4})(?:1[0-2]|0[1-9])(?:3[01]|0[1-9]|[12][0-9])T(?:2[0-3]|[01][0-9])(?:[0-5][0-9])(?:[0-5][0-9])(\.[0-9]+)?Z))?)?)?$")

    def __init__(self, ark_url):
        match = ArkUrlInfo.ark_url_regex.match(ark_url)

        if match is None:
            raise ArkUrlException("Invalid ARK URL: {}".format(ark_url))

        self.url_version = int(match.group(1))

        if self.url_version != knora_ark_version:
            raise ArkUrlException("Invalid ARK URL: {}".format(ark_url))

        self.project_id = match.group(2)
        escaped_resource_id_with_check_digit = match.group(3)

        if escaped_resource_id_with_check_digit is not None:
            # '-' is escaped as '=' in the resource ID and check digit, because '-' can be ignored in ARK URLs.
            resource_id_with_check_digit = escaped_resource_id_with_check_digit.replace('=', '-')

            if not is_valid(resource_id_with_check_digit):
                raise ArkUrlException("Invalid ARK URL: {}".format(ark_url))

            self.resource_id = resource_id_with_check_digit[0:-1]
            self.timestamp = match.group(4)
        else:
            self.resource_id = None
            self.timestamp = None

        self.template_dict = {
            "url_version": self.url_version,
            "project_id": self.project_id,
            "resource_id": self.resource_id,
            "timestamp": self.timestamp
        }

    # Converts an ARK URL to the URL that the client should be redirected to.
    def to_redirect_url(self):
        if self.project_id is None:
            return top_config["TopLevelObjectUrl"]
        else:
            project_config = config[self.project_id]

            if project_config.getboolean("UsePhp"):
                return self.to_php_redirect_url(project_config)
            else:
                return self.to_knora_redirect_url(project_config)

    def to_knora_redirect_url(self, project_config):
        resource_iri_template = Template(project_config["KnoraResourceIri"])
        project_iri_template = Template(project_config["KnoraProjectIri"])

        if self.resource_id is None:
            request_template = Template(project_config["KnoraProjectInfoUrl"])
        elif self.timestamp is None:
            request_template = Template(project_config["KnoraResourceRequestUrl"])
        else:
            request_template = Template(project_config["KnoraResourceVersionRequestUrl"])

        template_dict = self.template_dict.copy()
        template_dict["host"] = project_config["Host"]

        resource_iri = resource_iri_template.substitute(template_dict)
        url_encoded_resource_iri = parse.quote(resource_iri, safe="")
        template_dict["resource_iri"] = url_encoded_resource_iri

        project_iri = project_iri_template.substitute(template_dict)
        url_encoded_project_iri = parse.quote(project_iri, safe="")
        template_dict["project_iri"] = url_encoded_project_iri

        return request_template.substitute(template_dict)

    def to_php_redirect_url(self, project_config):
        template_dict = self.template_dict.copy()

        if self.timestamp is None:
            request_template = Template(project_config["PhpResourceRequestUrl"])
        else:
            request_template = Template(project_config["PhpResourceVersionRequestUrl"])

            # The PHP server only takes timestamps in the format YYYYMMDD
            template_dict["timestamp"] = self.timestamp[0:8]

        template_dict["host"] = project_config["Host"]
        resource_int_id = (int(self.resource_id, 16) // resource_int_id_factor) - 1
        template_dict["resource_int_id"] = resource_int_id

        return request_template.substitute(template_dict)


# Converts a Knora resource IRI to an ARK URL.
def resource_iri_to_ark_url(resource_iri, timestamp=None):
    match = resource_iri_regex.match(resource_iri)

    if match is None:
        raise ArkUrlException("Invalid resource IRI: {}".format(resource_iri))

    project_id = match.group(1)
    resource_id = match.group(2)
    check_digit = calculate_check_digit(resource_id)
    resource_id_with_check_digit = resource_id + check_digit

    # Escape '-' as '=' in the resource ID and check digit, because '-' can be ignored in ARK URLs.
    escaped_resource_id_with_check_digit = resource_id_with_check_digit.replace('-', '=')

    return format_ark_url(
        project_id=project_id,
        resource_id_with_check_digit=escaped_resource_id_with_check_digit,
        timestamp=timestamp
    )


# Converts information about a PHP resource to an ARK URL.
def php_resource_to_ark_url(php_resource_id, project_id, timestamp=None):
    knora_resource_id = format((php_resource_id + 1) * resource_int_id_factor, 'x')
    check_digit = calculate_check_digit(knora_resource_id)
    resource_id_with_check_digit = knora_resource_id + check_digit

    return format_ark_url(
        project_id=project_id,
        resource_id_with_check_digit=resource_id_with_check_digit,
        timestamp=timestamp
    )


# Formats a Knora ARK URL.
def format_ark_url(project_id,
                   resource_id_with_check_digit,
                   timestamp):
    url = "http://{}/ark:/{}/{}/{}/{}".format(
        top_config["ArkResolverHost"],
        top_config["ArkAssignedNumber"],
        knora_ark_version,
        project_id,
        resource_id_with_check_digit
    )

    # If there's a timestamp, add it as an object variant.
    if timestamp is not None:
        url += "." + timestamp

    return url


#################################################################################################
# Functions for generating and validating check codes for base64url-encoded IDs. The algorithm
# is based on org.apache.commons.validator.routines.checkdigit.ModulusCheckDigit.


# The base64url alphabet (without padding) from RFC 4648, Table 2.
base64url_alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
base64url_alphabet_length = len(base64url_alphabet)


# Checks whether a code with a check digit is valid.
def is_valid(code):
    if code is None or 0 == len(code):
        return False

    try:
        modulus_result = calculate_modulus(code, True)
        return modulus_result == 0
    except ArkUrlException:
        return False


# Calculates the check digit for a code.
def calculate_check_digit(code):
    if code is None or 0 == len(code):
        raise ArkUrlException("No code provided")

    modulus_result = calculate_modulus(code, False)
    char_value = (base64url_alphabet_length - modulus_result) % base64url_alphabet_length
    return to_check_digit(char_value)


# Calculates the modulus for a code.
def calculate_modulus(code, includes_check_digit):
    length = len(code)

    if not includes_check_digit:
        length += 1

    total = 0
    i = 0

    while i < len(code):
        right_pos = length - i
        char_value = to_int(code[i])
        total += weighted_value(char_value, right_pos)
        i += 1

    if total == 0:
        raise ArkUrlException("Invalid code: {}".format(code))

    return total % base64url_alphabet_length


# Calculates the weighted value of a character in the code at a specified position.
def weighted_value(char_value, right_pos):
    return char_value * right_pos


# Converts a character at a specified position to an integer value.
def to_int(char):
    char_value = base64url_alphabet.find(char)

    if char_value == -1:
        raise ArkUrlException("Invalid base64url character: '{}'".format(char))

    return char_value


# Converts an integer value to a check digit.
def to_check_digit(char_value):
    if char_value < 0 or char_value >= base64url_alphabet_length:
        raise ArkUrlException("Invalid character value: {}".format(char_value))

    return base64url_alphabet[char_value]


#################################################################################################
# Server implementation.

app = Sanic()


@app.get('/')
@app.get('/<path:path>')
async def catch_all(req, path=''):
    ark_url = "http://{}/{}".format(top_config["ArkResolverHost"], path)

    try:
        redirect_url = ArkUrlInfo(ark_url).to_redirect_url()
    except ArkUrlException as ex:
        return response.text(
            body=ex.message,
            status=400
        )
    except KeyError:
        return response.text(
            body="Invalid ARK URL",
            status=400
        )

    return response.redirect(redirect_url)


def server():
    app.run(host=top_config["LocalServerHost"], port=top_config.getint("LocalServerPort"))


#################################################################################################
# Automated tests.

def test():
    correct_resource_id = "cmfk1DMHRBiR4-_6HXpEFA"

    print("reject a string without a check digit: ", end='')
    assert not is_valid(correct_resource_id)
    print("OK")

    print("calculate a check digit for a string and validate it: ", end='')
    correct_resource_id_check_digit = "n"
    check_digit = calculate_check_digit(correct_resource_id)
    assert check_digit == correct_resource_id_check_digit
    correct_resource_id_with_correct_check_digit = correct_resource_id + check_digit
    assert is_valid(correct_resource_id_with_correct_check_digit)
    print("OK")

    print("reject a string with an incorrect check digit: ", end='')
    correct_resource_id_with_incorrect_check_digit = correct_resource_id + "m"
    assert not is_valid(correct_resource_id_with_incorrect_check_digit)
    print("OK")

    print("reject a string with a missing character: ", end='')
    resource_id_with_missing_character = "cmfk1DMHRBiR4-6HXpEFA"
    resource_id_with_missing_character_and_correct_check_digit = resource_id_with_missing_character + correct_resource_id_check_digit
    assert not is_valid(resource_id_with_missing_character_and_correct_check_digit)
    print("OK")

    print("reject a string with an incorrect character: ", end='')
    resource_id_with_incorrect_character = "cmfk1DMHRBir4-_6HXpEFA"
    resource_id_with_incorrect_character_and_correct_check_digit = resource_id_with_incorrect_character + correct_resource_id_check_digit
    assert not is_valid(resource_id_with_incorrect_character_and_correct_check_digit)
    print("OK")

    print("reject a string with swapped characters: ", end='')
    resource_id_with_swapped_characters = "cmfk1DMHRBiR4_-6HXpEFA"
    resource_id_with_swapped_characters_and_correct_check_digit = resource_id_with_swapped_characters + correct_resource_id_check_digit
    assert not is_valid(resource_id_with_swapped_characters_and_correct_check_digit)
    print("OK")

    print("generate an ARK URL for a resource IRI without a timestamp: ", end='')
    resource_iri = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
    ark_url = resource_iri_to_ark_url(resource_iri)
    assert ark_url == "http://ark.dasch.swiss/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn"
    print("OK")

    print("generate an ARK URL for a resource IRI with a timestamp: ", end='')
    ark_url = resource_iri_to_ark_url(resource_iri, "20181207T000000Z")
    assert ark_url == "http://ark.dasch.swiss/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20181207T000000Z"
    print("OK")

    print("generate an ARK URL for a PHP resource without a timestamp: ", end='')
    ark_url = php_resource_to_ark_url(php_resource_id=1, project_id="0803")
    assert ark_url == "http://ark.dasch.swiss/ark:/72163/1/0803/751e0b8am"
    print("OK")

    print("generate an ARK URL for a PHP resource with a timestamp: ", end='')
    ark_url = php_resource_to_ark_url(php_resource_id=1, project_id="0803", timestamp="20181207T000000Z")
    assert ark_url == "http://ark.dasch.swiss/ark:/72163/1/0803/751e0b8am.20181207T000000Z"
    print("OK")

    print("parse an ARK URL representing the top-level object: ", end='')
    ark_url_info = ArkUrlInfo("http://ark.dasch.swiss/ark:/72163/1")
    redirect_url = ark_url_info.to_redirect_url()
    assert redirect_url == "http://dasch.swiss"
    print("OK")

    print("parse an ARK project URL: ", end='')
    ark_url_info = ArkUrlInfo("http://ark.dasch.swiss/ark:/72163/1/0001")
    redirect_url = ark_url_info.to_redirect_url()
    assert redirect_url == "http://0.0.0.0:3333/admin/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"
    print("OK")

    print("parse an ARK URL for a Knora resource without a timestamp: ", end='')
    ark_url_info = ArkUrlInfo("http://ark.dasch.swiss/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn")
    redirect_url = ark_url_info.to_redirect_url()
    assert redirect_url == "http://0.0.0.0:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0001%2Fcmfk1DMHRBiR4-_6HXpEFA"
    print("OK")

    print("parse an ARK URL for a Knora resource with a timestamp: ", end='')
    ark_url_info = ArkUrlInfo("http://ark.dasch.swiss/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20181207T000000Z")
    redirect_url = ark_url_info.to_redirect_url()
    assert redirect_url == "http://0.0.0.0:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0001%2Fcmfk1DMHRBiR4-_6HXpEFA?version=20181207T000000Z"
    print("OK")

    print("parse an ARK URL for a PHP resource without a timestamp: ", end='')
    ark_url_info = ArkUrlInfo("http://ark.dasch.swiss/ark:/72163/1/0803/751e0b8am")
    redirect_url = ark_url_info.to_redirect_url()
    assert redirect_url == "http://data.dasch.swiss/resources/1"
    print("OK")

    print("parse an ARK URL for a PHP resource with a timestamp: ", end='')
    ark_url_info = ArkUrlInfo("http://ark.dasch.swiss/ark:/72163/1/0803/751e0b8am.20181207T000000Z")
    redirect_url = ark_url_info.to_redirect_url()
    assert redirect_url == "http://data.dasch.swiss/resources/1?citdate=20181207"
    print("OK")

    print("reject an ARK URL that doesn't pass check digit validation: ", end='')
    rejected = False

    try:
        ArkUrlInfo("http://ark.dasch.swiss/ark:/72163/1/0001/cmfk1DMHRBir4=_6HXpEFAn")
    except ArkUrlException:
        rejected = True

    assert rejected
    print("OK")


#################################################################################################
# Command-line invocation.

if __name__ == "__main__":
    default_config_filename = "ark-config.ini"
    parser = argparse.ArgumentParser(description="Convert between Knora resource IRIs and ARK URLs.")
    parser.add_argument("-c", "--config", help="config file (default {})".format(default_config_filename))
    group = parser.add_mutually_exclusive_group()
    group.add_argument("-s", "--server", help="start server", action="store_true")
    group.add_argument("-a", "--ark", help="ARK URL")
    group.add_argument("-i", "--iri", help="resource IRI")
    group.add_argument("-n", "--number", help="resource number for PHP server")
    group.add_argument("-t", "--test", help="run tests", action="store_true")
    parser.add_argument("-d", "--date", help="ISO 8601 timestamp (with -i or -p)")
    parser.add_argument("-p", "--project", help="project ID (with -n)")

    args = parser.parse_args()
    config = configparser.ConfigParser()

    try:
        if args.config is not None:
            config.read_file(open(args.config))
        else:
            config.read_file(open(default_config_filename))

        top_config = config["DEFAULT"]

        if args.server:
            server()
        elif args.test:
            test()
        elif args.iri:
            print(resource_iri_to_ark_url(args.iri, args.date))
        elif args.number:
            print(php_resource_to_ark_url(int(args.number), args.project, args.date))
        elif args.ark:
            print(ArkUrlInfo(args.ark).to_redirect_url())
        else:
            parser.print_help()
    except ArkUrlException as ex:
        print(ex.message)
        exit(1)
