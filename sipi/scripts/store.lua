-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0
--
-- Moves a file from temporary to permanent storage.
--
require "send_response"
require "jwt"

----------------------------------------
-- Extract the full filename from a path
----------------------------------------
function get_file_name(path)
    local str = path
    local temp = ""
    local result = ""

    -- Get file name + extension until first forward slash (/) and then break
    for i = str:len(), 1, -1 do
        if str:sub(i, i) ~= "/" then
            temp = temp .. str:sub(i, i)
        else
            break
        end
    end

    -- Reverse order of full file name
    for j = temp:len(), 1, -1 do
        result = result .. temp:sub(j, j)
    end

    return result
end
----------------------------------------

--------------------------------------------------------------------------------
-- Get the extension of a string determined by a dot . at the end of the string.
--------------------------------------------------------------------------------
function get_file_extension(path)
    local str = path
    local temp = ""
    local result = ""

    for i = str:len(), 1, -1 do
        if str:sub(i, i) ~= "." then
            temp = temp .. str:sub(i, i)
        else
            break
        end
    end

    -- Reverse order of full file name
    for j = temp:len(), 1, -1 do
        result = result .. temp:sub(j, j)
    end

    return result
end
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Get the basename of a string determined by removing the extension
--------------------------------------------------------------------------------
function get_file_basename(path)
    local str = path
    local temp = ""
    local result = ""
    local pfound = false

    for i = str:len(), 1, -1 do
        if str:sub(i, i) ~= "." then
            if pfound then
                temp = temp .. str:sub(i, i)
            end
        else
            pfound = true
        end
    end

    if pfound then
        -- Reverse order of full file name
        for j = temp:len(), 1, -1 do
            result = result .. temp:sub(j, j)
        end
    else
        result = str
    end

    return result
end
-------------------------------------------------------------------------------

----------------------------------------------------
-- Check if a directory exists. If not, create it --
----------------------------------------------------
function check_create_dir(path)
    local exists
    success, exists = server.fs.exists(path)
    if not success then
        return success, "server.fs.exists() failed: " .. exists
    end
    if not exists then
        success, error_msg = server.fs.mkdir(path, 511)
        if not success then
            return success, "server.fs.mkdir() failed: " .. error_msg
        end
    end
    return true, "OK"
end
----------------------------------------------------

-- Buffer the response (helps with error handling).
local success, error_msg = server.setBuffer()
if not success then
    send_error(500, "server.setBuffer() failed: " .. error_msg)
    return
end

-- Check for a valid JSON Web Token and permissions.
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

-- get token filename
local token_filename = knora_data["filename"]
if token_filename == nil then
    send_error(401, "Token does not specify a filename")
    return
end

-- get token prefix
local token_prefix = knora_data["prefix"]
if token_prefix == nil then
    send_error(401, "Token does not specify a prefix")
    return
end
local prefix = server.post["prefix"]

if prefix ~= token_prefix then
    send_error(401, "Incorrect prefix in token")
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
    send_error(401, "Incorrect filename in token")
    return
end

--
-- Construct the path of that file under the temp directory.
--
local hashed_filename
success, hashed_filename = helper.filename_hash(filename)
if not success then
    send_error(500, "helper.filename_hash() failed: " .. hashed_filename)
    return
end

local first_character_of_filename = string.lower(hashed_filename:sub(1, 1))
local second_character_of_filename = string.lower(hashed_filename:sub(2, 2))
local third_character_of_filename = string.lower(hashed_filename:sub(3, 3))
local fourth_character_of_filename = string.lower(hashed_filename:sub(4, 4))

local first_subfolder = first_character_of_filename .. second_character_of_filename
local second_subfolder = third_character_of_filename .. fourth_character_of_filename

local tmp_folder_root = config.imgroot .. '/tmp'
local tmp_folder = tmp_folder_root .. '/' .. first_subfolder .. '/' .. second_subfolder

local source_path = tmp_folder .. '/' .. hashed_filename
local source_key_frames = source_path:match("(.+)%..+")

--
-- Make sure the source file is readable.
--
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

--
-- Move the temporary files to the permanent storage directory.
--
local project_folder_root = config.imgroot .. "/" .. prefix
success, error_msg = check_create_dir(project_folder_root)
if not success then
    send_error(500, error_msg)
    return
end

local project_folder_level_1 = project_folder_root .. '/' .. first_subfolder
success, error_msg = check_create_dir(project_folder_level_1)
if not success then
    send_error(500, error_msg)
    return
end
local project_folder = project_folder_level_1 .. '/' .. second_subfolder
success, error_msg = check_create_dir(project_folder)
if not success then
    send_error(500, error_msg)
    return
end

local destination_path = project_folder .. '/' .. hashed_filename
local destination_key_frames = destination_path:match("(.+)%..+")
success, error_msg = server.fs.moveFile(source_path, destination_path)
if not success then
    send_error(500, "server.fs.moveFile() failed: " .. error_msg)
    return
end

-- In case of a movie file, move the key frames folder to the permanent storage directory
local source_key_frames_exists
_, source_key_frames_exists = server.fs.exists(source_key_frames)
if source_key_frames_exists then
    success, error_msg = os.rename(source_key_frames, destination_key_frames)
    if not success then
        send_error(500, "moving key frames folder failed: " .. error_msg)
        return
    end
end

--
-- Move sidecar and original file to final storage location
--
local hashed_sidecar = get_file_basename(hashed_filename) .. ".info"
local source_sidecar = tmp_folder .. "/" .. hashed_sidecar
success, readable = server.fs.is_readable(source_sidecar)
if not success then
    send_error(500, "server.fs.is_readable() failed: " .. readable)
    return
end

if readable then
    -- read the sidecar file into a string
    local f = io.open(source_sidecar)
    local jsonstr = f:read("*a")
    f:close()
    success, sidecar = server.json_to_table(jsonstr)
    if not success then
        send_error(500, "server.json_to_table() failed: " .. sidecar)
        return
    end

    -- move sidecar file to storage location
    local destination_sidecar = project_folder .. "/" .. hashed_sidecar
    success, error_msg = server.fs.moveFile(source_sidecar, destination_sidecar)
    if not success then
        send_error(500, "server.fs.moveFile() failed: " .. error_msg)
        return
    end

    -- move the original file to the storage location
    local source_original = tmp_folder .. "/" .. sidecar["originalInternalFilename"]
    local destination_original = project_folder .. "/" .. sidecar["originalInternalFilename"]
    success, error_msg = server.fs.moveFile(source_original, destination_original)
    if not success then
        send_error(500, "server.fs.moveFile() failed: " .. error_msg)
        return
    end

    server.log("store.lua: moved file " .. source_path .. " to " .. destination_path, server.loglevel.LOG_DEBUG)
end

local result = {
    status = 0
}

send_success(result)
