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
    send_error(500, "server.setBuffer() failed: " .. error_msg)
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

local token_prefix = knora_data["prefix"]

if token_prefix == nil then
    send_error(401, "Token does not specify a prefix")
    return
end

-- Check that the permanent storage directory exists.

local prefix = server.post["prefix"]

if prefix ~= token_prefix then
    send_error(401, "Incorrect prefix in token")
    return
end

local storage_dir = config.imgroot .. "/" .. prefix .. "/"

local exists
success, exists = server.fs.exists(storage_dir)
if not success then
    send_error(500, "server.fs.exists() failed: " .. exists)
    return
end

if not exists then
    success, error_msg = server.fs.mkdir(storage_dir, 511)

    if not success then
        send_error(500, "server.fs.mkdir() failed: " .. error_msg)
        return
    end
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

local hashed_filename
success, hashed_filename = helper.filename_hash(filename)
if not success then
    send_error(500, "helper.filename_hash() failed: " .. hashed_filename)
    return
end

local source_path = config.imgroot .. "/tmp/" .. hashed_filename

-- Make sure the source file is readable.

local readable
success, readable = server.fs.is_readable(source_path)
if not success then
    send_error(500, "server.fs.is_readable() failed: " .. readable)
    return
end

if not readable then
    send_error(400, source_path .. " not readable")
    return
end

-- Move the temporary file to the permanent storage directory.

local destination_path = storage_dir .. hashed_filename
success, error_msg = server.fs.moveFile(source_path, destination_path)
if not success then
    send_error(500, "server.fs.moveFile() failed: " .. error_msg)
    return
end

server.log("store.lua: moved " .. source_path .. " to " .. destination_path, server.loglevel.LOG_DEBUG)

local result = {
    status = 0
}

send_success(result)
