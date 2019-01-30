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

--
-- Deletes a file from temporary storage.
--

require "send_response"
require "jwt"

-- Buffer the response (helps with error handling).

local success, error_msg = server.setBuffer()

if not success then
    server.log("server.setBuffer() failed: " .. error_msg, server.loglevel.LOG_ERR)
    return
end

-- Check that this request is really from Knora and that the user has permission
-- to delete the file.

local token = get_knora_token()

if token == nil then
    return
end

local knora_data = token["knora-data"]

if knora_data == nil then
    send_error(403, "No knora-data in token")
    return
end

if knora_data["permission"] ~= "DeleteTempFile" then
    send_error(403, "Token does not grant permission to store file")
    return
end

local token_filename = knora_data["filename"]

if token_filename == nil then
    send_error(401, "Token does not specify a filename")
    return
end

-- Parse the URL to get the filename to be deleted.

local last_slash_pos = server.uri:match(".*/()")

if last_slash_pos == nil then
    send_error(400, "Invalid path: " .. server.uri)
    return
end

local filename = server.uri:sub(last_slash_pos)

if filename:len() == 0 or filename == "." or filename == ".." then
    send_error(400, "Invalid filename: " .. filename)
    return
end

-- Check that the filename matches the one in the token.

if filename ~= token_filename then
    send_error(401, "Incorrect filename in token")
    return
end

-- Check that the file exists in the temp directory.

local success, hashed_filename = helper.filename_hash(filename)

if not success then
    send_error(500, hashed_filename)
    return
end

local temp_file_path = config.imgroot .. '/tmp/' .. hashed_filename
local success, exists = server.fs.exists(temp_file_path)

if not success then
    send_error(500, exists)
    return
end

if not exists then
    send_error(404, filename .. " not found")
    return
end

local success, filetype = server.fs.ftype(temp_file_path)

if not success then
    send_error(400, filetype)
    return
end

if filetype ~= "FILE" then
    send_error(400, filename .. " is not a file")
    return
end

-- Delete the file.

local success, errmsg = server.fs.unlink(temp_file_path)

if not success then
    send_error(500, errmsg)
    return
end

server.log("delete_temp_file.lua: deleted " .. temp_file_path, server.loglevel.LOG_DEBUG)

local result = {
    status = 0
}

send_success(result)
