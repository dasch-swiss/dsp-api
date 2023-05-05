--  Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
--  SPDX-License-Identifier: Apache-2.0
--

-- Upload route for binary files that skips transcoding. Directly puts the
-- files in the temp folder.
--

require "file_specific_folder_util"
require "jwt"
require "send_response"
require "util"


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
    -- get the filename
    local filename = file_params["origname"]

    -- create the file specific tmp folder
    local tmp_folder = check_and_create_file_specific_folder(tmp_folder_root, filename)

    -- get the filepath
    local tmp_storage_file_path = get_path_from_folder_and_filename(tmp_folder, filename)

    success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
    if not success then
        send_error(500,
            "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
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
