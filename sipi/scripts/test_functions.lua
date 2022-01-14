-- * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "send_response"

-- Sample values for Content-Type that should parse correctly.

local html_utf8 = {
    mimetype = "text/html",
    charset = "utf-8"
}

local html_no_charset = {
    mimetype = "text/html",
    charset = nil
}

local mimetype_test_data = {
    {
        received = "text/html;charset=utf-8",
        expected = html_utf8
    },
    {
        received = "text/html;charset=UTF-8",
        expected = html_utf8
    },
    {
        received = "Text/HTML;Charset=\"utf-8\"",
        expected = html_utf8
    },
    {
        received = "text/html; charset=\"utf-8\"",
        expected = html_utf8
    },
    {
        received = "text/html",
        expected = html_no_charset
    }
}

local bad_mimetype = ";;"

success, errmsg = server.setBuffer()

if not success then
    send_error(500, "server.setBuffer() failed: " .. errmsg)
    return
end

result = {}

for i, test_data_item in ipairs(mimetype_test_data) do
    local success, parsed_mimetype = server.parse_mimetype(test_data_item.received)

    if not success then
        send_error(400, "Couldn't parse mimetype: " .. test_data_item.received)
        return -1
    end

    if parsed_mimetype == nil then
        send_error(400, "With input '" .. test_data_item.received .. "', parsed mimetype is nil")
        return -1
    end

    if parsed_mimetype.mimetype ~= test_data_item.expected.mimetype then
        send_error(400, "With input '" .. test_data_item.received .. "', parsed mimetype is '" .. parsed_mimetype.mimetype .. "' but should be '" .. test_data_item.expected.mimetype .. "'")
        return -1
    end

    if parsed_mimetype.charset ~= test_data_item.expected.charset then
        send_error(400, "With input '" .. test_data_item.received .. "', parsed charset is '" .. (parsed_mimetype.charset or "") .. "' but should be '" .. (test_data_item.expected.charset or "") .. "'")
        return -1
    end

    table.insert(result, { test_data_item, "OK" })
end

-- Try parsing a mimetype that should return an error.

local success, parsed_bad_mimetype = server.parse_mimetype(bad_mimetype)

if success then
    send_error(400, "MIME type '" .. bad_mimetype .. "' parsed, but should have caused an error")
    return -1
else
    table.insert(result, { { received = bad_mimetype, expected = "error" }, "OK" })
end

send_success(result)
