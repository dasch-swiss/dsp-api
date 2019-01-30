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
-- Moves a file from temporary to permanent storage.
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
-- to store the file.

local token = get_knora_token()

if token == nil then
    return
end

local knora_data = token["knora-data"]

if knora_data == nil then
    send_error(403, "No knora-data in token")
    return
end

if knora_data["permission"] ~= "StoreFile" then
    send_error(403, "Token does not grant permission to store file")
    return
end

local token_filename = knora_data["filename"]

if token_filename == nil then
    send_error(401, "Token does not specify a filename")
    return
end

-- Check that the permanent storage directory exists.

local storage_dir = config.imgroot .. "/knora/" -- TODO: use project-specific dir
local success, exists = server.fs.exists(storage_dir)

if not success then
    send_error(500, exists)
    return
end

if not exists then
    send_error(500, "Directory " .. storage_dir .. " not found (it must exist when Sipi starts)")
    return
end

-- Get the submitted filename.

if server.post == nil then
    send_error(400, PARAMETERS_INCORRECT)
    return
end

local filename = server.post["filename"]

if filename == nil then
    send_error(400, PARAMETERS_INCORRECT)
    return
end

if filename ~= token_filename then
    send_error(401, "Incorrect filename in token")
    return
end

-- Construct the path of that file under the temp directory.

local success, hashed_filename = helper.filename_hash(filename)

if not success then
    send_error(500, hashed_source_filename)
    return
end

local source_path = config.imgroot .. "/tmp/" .. hashed_filename

-- Make sure the source file is readable.

local success, readable = server.fs.is_readable(source_path)

if not success then
    send_error(500, readable)
    return
end

if not readable then
    send_error(500, source_path .. " not readable")
    return
end

-- Move the temporary file to the permanent storage directory.

local destination_path = storage_dir .. hashed_filename
local success, error_msg = server.fs.moveFile(source_path, destination_path)

if not success then
    send_error(500, error_msg)
    return
end

server.log("store.lua: moved " .. source_path .. " to " .. destination_path, server.loglevel.LOG_DEBUG)

local result = {
    status = 0
}

send_success(result)
