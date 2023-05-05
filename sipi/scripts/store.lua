-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0
--
-- Moves a file from temporary to permanent storage.
--

require "file_specific_folder_util"
require "send_response"
require "jwt"


--------------------------------------------------------------------------------
-- Get the basename of a string determined by removing the extension
--------------------------------------------------------------------------------
local function get_file_basename(path)
    local temp = ""
    local result = ""
    local found = false

    for i = path:len(), 1, -1 do
        if path:sub(i, i) ~= "." then
            if found then
                temp = temp .. path:sub(i, i)
            end
        else
            found = true
        end
    end

    if found then
        -- Reverse order of full file name
        for j = temp:len(), 1, -1 do
            result = result .. temp:sub(j, j)
        end
    else
        result = path
    end

    return result
end


-- Buffer the response (helps with error handling).
local success, error_msg = server.setBuffer()
if not success then
    send_error(500, "store.lua: server.setBuffer() failed: " .. error_msg)
    return
end

-- Check for a valid JSON Web Token and permissions.
local token = get_knora_token()
if token == nil then
    return
end
local knora_data = token["knora-data"]
if knora_data == nil then
    send_error(403, "store.lua: No knora-data in token")
    return
end
if knora_data["permission"] ~= "StoreFile" then
    send_error(403, "store.lua: Token does not grant permission to store file")
    return
end

-- get token filename
local token_filename = knora_data["filename"]
if token_filename == nil then
    send_error(401, "store.lua: Token does not specify a filename")
    return
end

-- get token prefix
local token_prefix = knora_data["prefix"]
if token_prefix == nil then
    send_error(401, "store.lua: Token does not specify a prefix")
    return
end
local prefix = server.post["prefix"]

if prefix ~= token_prefix then
    send_error(401, "store.lua: Incorrect prefix in token")
    return
end

--
-- Get the submitted filename and check consistency.
--
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
    send_error(401, "store.lua: Incorrect filename in token")
    return
end

server.log("store.lua: start processing " .. tostring(filename))

--
-- Construct the path of that file under the temp directory.
--
local hashed_filename
success, hashed_filename = helper.filename_hash(filename)
if not success then
    send_error(500, "store.lua: helper.filename_hash() failed: " .. hashed_filename)
    return
end

local tmp_folder_root = config.imgroot .. '/tmp'
local source_file = get_file_specific_path(tmp_folder_root, hashed_filename)
local source_preview = source_file:match("(.+)%..+")

--
-- Make sure the source file is readable.
--
local readable
success, readable = server.fs.is_readable(source_file)
if not success then
    send_error(500, "store.lua: server.fs.is_readable() failed: " .. readable)
    return
end
if not readable then
    send_error(400, "store.lua: " .. source_file .. " not readable")
    return
end

--
-- Move the temporary files to the permanent storage directory.
--
local project_folder = config.imgroot .. "/" .. prefix
local destination_folder = check_and_create_file_specific_folder(project_folder, hashed_filename)
local destination_file = get_file_specific_path(project_folder, hashed_filename)
local destination_preview = destination_file:match("(.+)%..+")
success, error_msg = server.fs.moveFile(source_file, destination_file)
if not success then
    send_error(500, "store.lua: server.fs.moveFile() failed: " .. error_msg)
    return
end

-- In case of a movie file, move the folder with the preview file to the permanent storage directory
local source_preview_exists
_, source_preview_exists = server.fs.exists(source_preview)
if source_preview_exists then
    success, error_msg = os.rename(source_preview, destination_preview)
    if not success then
        send_error(500, "store.lua: moving folder with preview failed: " .. error_msg)
        return
    end
end

--
-- Move sidecar and original file to final storage location
--
local hashed_sidecar = get_file_basename(hashed_filename) .. ".info"
local source_sidecar = get_file_specific_path(tmp_folder_root, hashed_sidecar)
success, readable = server.fs.is_readable(source_sidecar)
if not success then
    send_error(500, "store.lua: server.fs.is_readable() failed: " .. readable)
    return
end

if readable then
    -- read the sidecar file into a string
    local f = io.open(source_sidecar)
    local jsonstr = f:read("*a")
    f:close()
    local sidecar
    success, sidecar = server.json_to_table(jsonstr)
    if not success then
        send_error(500, "store.lua: server.json_to_table() failed: " .. sidecar)
        return
    end

    -- move sidecar file to storage location
    local destination_sidecar = destination_folder .. "/" .. hashed_sidecar
    success, error_msg = server.fs.moveFile(source_sidecar, destination_sidecar)
    if not success then
        send_error(500, "store.lua: server.fs.moveFile() failed: " .. error_msg)
        return
    end

    -- move the original file to the storage location
    local source_original = get_file_specific_path(tmp_folder_root, sidecar["originalInternalFilename"])
    local destination_original = destination_folder .. "/" .. sidecar["originalInternalFilename"]
    success, error_msg = server.fs.moveFile(source_original, destination_original)
    if not success then
        send_error(500, "store.lua: server.fs.moveFile() failed: " .. error_msg)
        return
    end

    server.log("store.lua: moved file " .. source_file .. " to " .. destination_file, server.loglevel.LOG_DEBUG)
end

local result = {
    status = 0
}

send_success(result)
