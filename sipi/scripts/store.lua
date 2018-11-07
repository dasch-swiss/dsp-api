-- Copyright © 2015-2018 the contributors (see Contributors.md).
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

-- Check that the permanent storage directory exists. It needs to be created
-- before sipi is started, so that sipi can create the directory sublevels on
-- startup.

local knoraDir = config.imgroot .. "/knora/"

local success, exists = server.fs.exists(knoraDir)

if not exists then
    local errorMsg = "Directory " .. knoraDir .. " not found. Please make sure it exists before starting Sipi."
    send_error(500, errorMsg)
    server.log(errorMsg, server.loglevel.LOG_ERR)
    return
end

local success, errmsg = server.setBuffer()

if not success then
    server.log("server.setBuffer() failed: " .. errmsg, server.loglevel.LOG_ERR)
    return
end

if server.post == nil then
    send_error(400, PARAMETERS_INCORRECT)
    return
end

local filename = server.post["filename"]

-- check if all the expected params are set

if filename == nil then
    send_error(400, PARAMETERS_INCORRECT)
    return
end

if filename ~= token_filename then
    send_error(401, "Incorrect filename in token")
    return
end

-- file with name given in param "filename" has been saved by upload.lua beforehand
local tmpDir = config.imgroot .. "/tmp/"
local sourcePath = tmpDir .. filename

-- check if source is readable
local success, readable = server.fs.is_readable(sourcePath)

if not success then
    server.log("Source: " .. sourcePath .. "not readable, " .. readable, server.loglevel.LOG_ERR)
    return
end

if not readable then
    send_error(500, FILE_NOT_READABLE .. sourcePath)
    return
end

-- Move temporary file to permanent image file storage path with sublevels.
local success, newFilePath = helper.filename_hash(filename);

if not success then
    server.sendStatus(500)
    server.log(newFilePath, server.loglevel.LOG_ERR)
    return
end

local success, errmsg = server.fs.moveFile(sourcePath, newFilePath)

if not success then
    server.sendStatus(500)
    server.log(errmsg, server.loglevel.LOG_ERR)
    return
end

-- delete tmp file
local success, errmsg = server.fs.unlink(sourcePath)

if not success then
    server.log("server.fs.unlink failed: " .. errmsg, server.loglevel.LOG_ERR)
    return
end

server.log(sourcePath .. " moved to " .. newFilePath, server.loglevel.DEBUG)

local result = {
    status = 0
}

send_success(result)
