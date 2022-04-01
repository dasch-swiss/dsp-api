-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--
-- Moves a file from temporary to permanent storage.
--

require "send_response"
require "jwt"

----------------------------------------
-- Extract the full filename form a path
----------------------------------------
function get_file_name(path)
    local str = path
    local temp = ""
    local result = ""

    -- Get file name + extension until first forward slash (/) and then break
    for i = str:len(), 1, -1 do
        if str:sub(i,i) ~= "/" then
            temp = temp..str:sub(i,i)
        else
            break
        end
    end

    -- Reverse order of full file name
    for j = temp:len(), 1, -1 do
        result = result..temp:sub(j,j)
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
        if str:sub(i,i) ~= "." then
            temp = temp..str:sub(i,i)
        else
            break
        end
    end

    -- Reverse order of full file name
    for j = temp:len(), 1, -1 do
        result = result..temp:sub(j,j)
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
        if str:sub(i,i) ~= "." then
            if pfound then temp = temp..str:sub(i,i) end
        else
            pfound = true
        end
    end

    if pfound then
        -- Reverse order of full file name
        for j = temp:len(), 1, -1 do
            result = result..temp:sub(j,j)
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

--
-- Check that this request is really from Knora and that the user has permission
-- to store the file.
--
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

-- Check that original file storage directory exists
local originals_dir = config.imgroot .. "/originals/" .. prefix .. "/"
success, msg = check_create_dir(config.imgroot .. "/originals/")
if not success then
    send_error(500, msg)
    return
end
success, msg = check_create_dir(originals_dir)
if not success then
    send_error(500, msg)
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

--
-- Make sure the source file is readable.
--
local source_path = config.imgroot .. "/tmp/" .. hashed_filename
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
local storage_dir = config.imgroot .. "/" .. prefix .. "/"
success, msg = check_create_dir(storage_dir)
if not success then
    send_error(500, msg)
    return
end

local destination_path = storage_dir .. hashed_filename
success, error_msg = server.fs.moveFile(source_path, destination_path)
if not success then
    send_error(500, "server.fs.moveFile() failed: " .. error_msg)
    return
end

--
-- Move sidecarfile if it exists
--
local originals_dir = config.imgroot .. "/originals/" .. prefix .. "/"
success, msg = check_create_dir(originals_dir)
if not success then
    send_error(500, msg)
    return
end

local hashed_sidecar =  get_file_basename(hashed_filename) .. ".info"
local source_sidecar = config.imgroot .. "/tmp/" .. hashed_sidecar
success, readable = server.fs.is_readable(source_sidecar)
if not success then
    send_error(500, "server.fs.is_readable() failed: " .. readable)
    return
end

if readable then
    -- first we read the sidecar file into a string
    local f = io.open(source_sidecar)
    local jsonstr = f:read("*a")
    f:close()
    success, sidecar = server.json_to_table(jsonstr)
    if not success then
        send_error(500, "server.json_to_table() failed: " .. sidecar)
        return
    end

    -- copy sidecar to IIIF directory for this project
    local destination_sidecar = storage_dir .. hashed_sidecar
    success, error_msg = server.fs.copyFile(source_sidecar, destination_sidecar)
    if not success then
        send_error(500, "server.fs.copyFile() failed: " .. error_msg)
        return
    end

    -- move sidecar file to originals directory
    local destination2_sidecar = originals_dir .. hashed_sidecar
    success, error_msg = server.fs.moveFile(source_sidecar, destination2_sidecar)
    if not success then
        send_error(500, "server.fs.moveFile() failed: " .. error_msg)
        return
    end

    -- move the original file to the originals directory
    local source_original = config.imgroot .. "/tmp/" .. sidecar["originalInternalFilename"]
    local destination_original = originals_dir .. sidecar["originalInternalFilename"]
    success, error_msg = server.fs.moveFile(source_original, destination_original)
    if not success then
        send_error(500, "server.fs.moveFile() failed: " .. error_msg)
        return
    end

    -- in case of a moving image, we have to extract the frames from video file; they will be used for preview stuff
    if sidecar["duration"] and sidecar["fps"] then
        success, error_msg = os.execute("./scripts/export-moving-image-frames.sh -i " .. storage_dir .. sidecar["internalFilename"])
        if not success then
            send_error(500, "export-moving-image-frames.sh failed: " .. error_msg)
            return
        end
    end

    server.log("store.lua: moved file " .. source_path .. " to " .. destination_path, server.loglevel.LOG_DEBUG)
end

local result = {
    status = 0
}

send_success(result)
