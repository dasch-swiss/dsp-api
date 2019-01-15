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
# Command-line program for converting between Knora resource IRIs and ARK URLs.
#################################################################################################


import re
import argparse


class ArkUrlException(Exception):
    def __init__(self, message):
        self.message = message


#################################################################################################
# Functions for generating and parsing Knora ARK URLs.


ark_resolver_host = "ark.dasch.swiss"
ark_assigned_number = 72163
ark_version = 1
resource_iri_regex = re.compile(r"http://rdfh.ch/([0-9A-F]+)/([A-Za-z0-9_-]+)")
ark_url_regex = re.compile(r"http://ark.dasch.swiss/ark:/72163/([0-9]+)\.([0-9A-F]+)\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-])(\.((-?(?:[1-9][0-9]*)?[0-9]{4})-(1[0-2]|0[1-9])-(3[01]|0[1-9]|[12][0-9])T(2[0-3]|[01][0-9]):([0-5][0-9]):([0-5][0-9])(\.[0-9]+)?Z|[+-](?:2[0-3]|[01][0-9]):[0-5][0-9]))?")


# Converts a Knora resource IRI to an ARK URL.
def resource_iri_to_ark_url(resource_iri, timestamp=None):
    match = resource_iri_regex.match(resource_iri)

    if match is None:
        raise ArkUrlException("Invalid resource IRI: {}".format(resource_iri))

    project_id = match.group(1)
    resource_id = match.group(2)
    check_digit = calculate_check_digit(resource_id)

    url = "http://{}/ark:/{}/{}.{}.{}.{}".format(
        ark_resolver_host,
        ark_assigned_number,
        ark_version,
        project_id,
        resource_id,
        check_digit
    )

    if timestamp is not None:
        url += "." + timestamp

    return url


# Converts an ARK URL to a tuple of (Knora resource IRI, timestamp).
def ark_url_to_resource_iri(ark_url):
    match = ark_url_regex.match(ark_url)

    if match is None:
        raise ArkUrlException("Invalid ARK URL: {}".format(ark_url))

    url_version = match.group(1)

    if url_version != "1":
        raise ArkUrlException("Invalid ARK URL: {}".format(ark_url))

    project_id = match.group(2)
    resource_id = match.group(3)
    check_digit = match.group(4)
    timestamp = match.group(6)

    resource_id_with_check_digit = resource_id + check_digit

    if not is_valid(resource_id_with_check_digit):
        raise ArkUrlException("Invalid ARK URL: {}".format(ark_url))

    resource_iri = "http://rdfh.ch/{}/{}".format(project_id, resource_id)
    return resource_iri, timestamp


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
    char_index_in_alphabet = base64url_alphabet.find(char)

    if char_index_in_alphabet == -1:
        raise ArkUrlException("Invalid base64url character: '{}'".format(char))

    # Use 1-based values, because otherwise an 'A' at the beginning of a string contributes nothing to the check digit.
    return char_index_in_alphabet + 1


# Converts an integer value to a check digit.
def to_check_digit(char_value):
    char_index_in_alphabet = char_value - 1

    if char_index_in_alphabet < 0 or char_index_in_alphabet >= base64url_alphabet_length:
        raise ArkUrlException("Invalid character value: {}".format(char_value))

    return base64url_alphabet[char_index_in_alphabet]


#################################################################################################
# Automated tests.


def test():
    correct_resource_id = "cmfk1DMHRBiR4-_6HXpEFA"

    print("reject a string without a check digit: ", end='')
    assert not is_valid(correct_resource_id)
    print("OK")

    print("calculate a check digit for a string and validate it: ", end='')
    correct_resource_id_check_digit = "T"
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
    assert ark_url == "http://ark.dasch.swiss/ark:/72163/1.0001.cmfk1DMHRBiR4-_6HXpEFA.T"
    print("OK")

    print("generate an ARK URL for a resource IRI with a timestamp: ", end='')
    ark_url = resource_iri_to_ark_url(resource_iri, "2018-12-07T00:00:00Z")
    assert ark_url == "http://ark.dasch.swiss/ark:/72163/1.0001.cmfk1DMHRBiR4-_6HXpEFA.T.2018-12-07T00:00:00Z"
    print("OK")

    print("parse an ARK URL without a timestamp: ", end='')
    (converted_resource_iri, timestamp) = ark_url_to_resource_iri("http://ark.dasch.swiss/ark:/72163/1.0001.cmfk1DMHRBiR4-_6HXpEFA.T")
    assert converted_resource_iri == "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
    assert timestamp is None
    print("OK")

    print("parse an ARK URL with a timestamp: ", end='')
    (converted_resource_iri, timestamp) = ark_url_to_resource_iri("http://ark.dasch.swiss/ark:/72163/1.0001.cmfk1DMHRBiR4-_6HXpEFA.T.2018-12-07T00:00:00Z")
    assert converted_resource_iri == "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
    assert timestamp == "2018-12-07T00:00:00Z"
    print("OK")

    print("reject an ARK URL that doesn't pass check digit validation: ", end='')
    rejected = False

    try:
        (converted_resource_iri, timestamp) = ark_url_to_resource_iri("http://ark.dasch.swiss/ark:/72163/1.0001.cmfk1DMHRBir4-_6HXpEFA.T")
    except ArkUrlException:
        rejected = True

    assert rejected
    print("OK")


#################################################################################################
# Command-line invocation.


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Convert between Knora resource IRIs and ARK URLs.")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("-a", "--ark", help="ARK URL")
    group.add_argument("-r", "--resource", help="resource IRI and optional ISO 8601 timestamp")
    group.add_argument("-t", "--test", help="run tests", action="store_true")
    parser.add_argument("-d", "--date", help="ISO 8601 timestamp (with -r)")

    args = parser.parse_args()

    if args.test:
        test()
    elif args.resource:
        print(resource_iri_to_ark_url(args.resource, args.date))
    elif args.ark:
        (resource_iri, timestamp) = ark_url_to_resource_iri(args.ark)

        print(resource_iri)

        if timestamp is not None:
            print(timestamp)
    else:
        parser.print_help()
