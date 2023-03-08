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

--------------------------------------------------------------------------
-- Checks if the folder for video frames exists and if not, creates it.
--------------------------------------------------------------------------
function check_if_frames_folder_exists_and_create_if_not(folder_name)
    success, exists = server.fs.exists(folder_name)
    if not success then
        -- fs.exist was not run successful.
        send_error(500, "server.fs.exists() failed: " .. exists)
        return
    end
    if not exists then
        -- frames folder does not exist, try to create one
        success, error_msg = server.fs.mkdir(folder_name, 511)
        if not success then
            server.log("frames folder missing and not able to create one: " .. folder_name, server.loglevel.LOG_ERR)
            send_error(500, "server.fs.mkdir() failed: " .. error_msg)
            return
        end
    end
end
--------------------------------------------------------------------------



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

-- Check that the temp folder is created
local tmpFolder = config.imgroot .. '/tmp/'
local exists
success, exists = server.fs.exists(tmpFolder)
if not success then
    -- fs.exist was not run successful. This does not mean, that the tmp folder is not there.
    send_error(500, "server.fs.exists() failed: " .. exists)
    return
end
if not exists then
    -- tmp folder does not exist, trying to create one
    success, error_msg = server.fs.mkdir(tmpFolder, 511)
    if not success then
        server.log("temp folder missing and not able to create one: " .. tmpFolder, server.loglevel.LOG_ERR)
        send_error(500, "server.fs.mkdir() failed: " .. error_msg)
        return
    end
end

-- A table of data about each file that was uploaded.
local file_upload_data = {}


-- Process the uploaded files.
for file_index, file_params in pairs(server.uploads) do
    local tmp_storage_file_path = ""
    local file_base_name = ""

    -- get the filename
    local filename = file_params["origname"]

    -- if it is the preview file of a move, move it to the movie's folder
    if string.find(filename, "_m_") then
        file_base_name, _ = filename:match("(.+)_m_(.+)")
        check_if_frames_folder_exists_and_create_if_not(tmpFolder .. file_base_name)
        tmp_storage_file_path = config.imgroot .. "/tmp/" .. file_base_name .. "/" .. filename
        
         
    -- if it is a movie frame file, move it to the movie's frame folder
    elseif string.find(filename, "_f_") then
        file_base_name, _ = filename:match("(.+)_f_(.+)")
        check_if_frames_folder_exists_and_create_if_not(tmpFolder .. file_base_name)
        check_if_frames_folder_exists_and_create_if_not(tmpFolder .. file_base_name .. "/frames")
        tmp_storage_file_path = config.imgroot .. "/tmp/" .. file_base_name .. "/frames/".. filename

    -- in all other cases, move it to Sipi's temporary storage location
    else
        tmp_storage_file_path = config.imgroot .. "/tmp/" .. filename
    end

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
