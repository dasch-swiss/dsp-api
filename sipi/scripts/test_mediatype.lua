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
require "file_info"

-- Sample values for mediatype handling.

local mediatype_test_data = {
    {
        filename = "test.xml",
        received = "application/xml",
        expected = "text"
    },
    {
        filename = "test.xml",
        received = "text/xml",
        expected = "text"
    },
    {
        filename = "test.txt",
        received = "text/plain",
        expected = "text"
    },
    {
        filename = "test.jp2",
        received = "image/jp2",
        expected = "image"
    },
    {
        filename = "test.tif",
        received = "image/tiff",
        expected = "image"
    },
    {
        filename = "test.png",
        received = "image/png",
        expected = "image"
    },
    {
        filename = "test.jpg",
        received = "image/jpeg",
        expected = "image"
    },
    {
        filename = "test.pdf",
        received = "application/pdf",
        expected = "document"
    },
    {
        filename = "garbage.grb",
        received = "application/garbage",
        expected = nil
    }
}

success, errmsg = server.setBuffer()

if not success then
    send_error(500, "server.setBuffer() failed: " .. errmsg)
    return
end

result = {}

for i, test_data_item in ipairs(mediatype_test_data) do
    local file_info = get_file_info(test_data_item.filename, test_data_item.received)
    local success = false
    
    if file_info == nil then
        if test_data_item.expected == nil then
            success = true
        else
            send_error(500, "Could not determine any mediatype for " .. test_data_item.received)
        end
    elseif file_info["media_type"] == test_data_item.expected then
        success = true
    else
        send_error(500, "Could not determine correct mediatype for " .. test_data_item.received .. ", got " .. file_info["media_type"])
    end
        
    if success then
        table.insert(result, { test_data_item, "OK" })
    else
        return
    end

end

send_success(result)
