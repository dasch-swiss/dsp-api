--  Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
--  SPDX-License-Identifier: Apache-2.0

--
-- Upload route for binary files that skips transcoding. Directly puts the
-- files in the temp folder.
--

require "jwt"
require "send_response"

--------------------------------------------------------------------------
-- Calculate the SHA256 checksum of a file using the operating system tool
--------------------------------------------------------------------------
function file_checksum(path)
    local handle = io.popen("/usr/bin/sha256sum " .. path)
    local checksum_orig = handle:read("*a")
    handle:close()
    return string.match(checksum_orig, "%w*")
end
--------------------------------------------------------------------------

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

--------------------------------------------------------
-- Create the file specific tmp folder from its filename
-- Returns the path
--------------------------------------------------------
function create_tmp_folder(root_folder, filename)
    local first_character_of_filename = filename:sub(1, 1)
    local second_character_of_filename = filename:sub(2, 2)
    local third_character_of_filename = filename:sub(3, 3)
    local fourth_character_of_filename = filename:sub(4, 4)

    local first_subfolder = first_character_of_filename .. second_character_of_filename
    local second_subfolder = third_character_of_filename .. fourth_character_of_filename

    local tmp_folder_level_1 = root_folder .. '/' .. first_subfolder
    success, error_msg = check_create_dir(tmp_folder_level_1)
    if not success then
        send_error(500, error_msg)
        return
    end

    local tmp_folder = tmp_folder_level_1 .. '/' .. second_subfolder
    success, error_msg = check_create_dir(tmp_folder)
    if not success then
        send_error(500, error_msg)
        return
    end

    return tmp_folder
end
--------------------------------------------------------

--------------------------------------------------------
-- Gets the file specific path to the tmp location
--------------------------------------------------------
function get_tmp_filepath(tmp_folder, filename)
    local tmp_filepath = ''
    -- if it is the preview file of a movie, the tmp folder is the movie's folder
    if string.find(filename, "_m_") then
        file_base_name, _ = filename:match("(.+)_m_(.+)")
        success, error_msg = check_create_dir(tmp_folder .. "/" .. file_base_name)
        if not success then
            send_error(500, error_msg)
            return
        end
        tmp_filepath = tmp_folder .. "/" .. file_base_name .. "/" .. filename

    -- for all other cases
    else
        tmp_filepath = tmp_folder .. "/" .. filename
    end

    return tmp_filepath
end
--------------------------------------------------------


-- Buffer the response (helps with error handling).
local success, error_msg
success, error_msg = server.setBuffer()
if not success then
    send_error(500, "server.setBuffer() failed: " .. error_msg)
    return
end

-- Check for a valid JSON Web Token from Knora.
local token = get_knora_token()
if token == nil then
    return
end

-- Check that the root tmp folder is created
local tmp_folder_root = config.imgroot .. '/' .. 'tmp'
success, error_msg = check_create_dir(tmp_folder_root)
if not success then
    send_error(500, error_msg)
    return
end

-- A table of data about each file that was uploaded.
local file_upload_data = {}

-- Process the uploaded files.
for file_index, file_params in pairs(server.uploads) do
    local file_base_name = ""

    -- get the filename
    local filename = file_params["origname"]

    -- create the file specific tmp folder
    local tmp_folder = create_tmp_folder(tmp_folder_root, filename)

    -- get the filepath
    local tmp_storage_file_path = get_tmp_filepath(tmp_folder, filename)

    success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
    if not success then
        send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
        return
    end
    server.log("upload_without_processing.lua: wrote file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)

    -- Calculate the checksum of the file
    local checksum = file_checksum(tmp_storage_file_path)
    
    local this_file_upload_data = {
        filename = filename,
        checksum = checksum,
        checksumAlgorithm = "sha256"
    }
    file_upload_data[file_index] = this_file_upload_data

end

-- Return the file upload data in the response.
local response = {}
response["uploadedFiles"] = file_upload_data
send_success(response)
