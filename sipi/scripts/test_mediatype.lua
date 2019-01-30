-- Copyright © 2015-2019 the contributors (see Contributors.md).
--
-- This file is part of Knora.
--
-- Knora is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published
-- by the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- Knora is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public
-- License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

require "send_response"
require "get_mediatype"

-- Sample values for mediatype handling.

local mediatype_test_data = {
    {
        received = "application/xml",
        expected = "text"
    },
    {
        received = "text/xml",
        expected = "text"
    },
    {
        received = "text/plain",
        expected = "text"
    },
    {
        received = "image/jp2",
        expected = "image"
    },
    {
        received = "image/tiff",
        expected = "image"
    },
    {
        received = "image/png",
        expected = "image"
    },
    {
        received = "image/jpeg",
        expected = "image"
    },
    {
        received = "garbage",
        expected = false
    }
}

success, errmsg = server.setBuffer()

if not success then
    server.log("server.setBuffer() failed: " .. errmsg, server.loglevel.LOG_ERR)
    send_error(500, "buffer could not be set correctly")
    return
end

result = {}

for i, test_data_item in ipairs(mediatype_test_data) do
    local mediatype = get_mediatype(test_data_item.received)

    if (mediatype ~= test_data_item.expected) then
        send_error(500, "Could not determine correct mediatype for " .. test_data_item.received .. ", got " .. tostring(mediatype))
    end

    table.insert(result, { test_data_item, "OK" })
end

local file_extension_test_data = {
    {
        received1 = "application/xml",
        received2 = "test.xml",
        expected = true
    },
    {
        received1 = "application/xml",
        received2 = "test.xsl",
        expected = true
    },
    {
        received1 = "application/xml",
        received2 = "test.xsd",
        expected = true
    },
    {
        received1 = "text/xml",
        received2 = "test.xml",
        expected = true
    },
    {
        received1 = "text/xml",
        received2 = "test.xsl",
        expected = true
    },
    {
        received1 = "text/xml",
        received2 = "test.xsd",
        expected = true
    },
    {
        received1 = "text/plain",
        received2 = "test.txt",
        expected = true
    },
    {
        received1 = "text/xml",
        received2 = "test.jpg",
        expected = false
    }
}


for i, test_data_item in ipairs(file_extension_test_data) do
    local check = check_file_extension(test_data_item.received1, test_data_item.received2)

    if (check ~= test_data_item.expected) then
        send_error(500, "Could not correctly check consistency between mimetype and file extension for " .. test_data_item.received1 .. ", "..  test_data_item.received2)
    end

    table.insert(result, { test_data_item, "OK" })
end


send_success(result)
