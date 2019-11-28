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
-- Upload route for binary files (currently only images) to be used with Knora.
--

require "file_info"
require "send_response"
require "jwt"
require "clean_temp_dir"
require "util"

-- Buffer the response (helps with error handling).

local success, error_msg = server.setBuffer()

if not success then
    send_error(500, "server.setBuffer() failed: " .. error_msg)
    return
end

-- Check for a valid JSON Web Token from Knora.

local token = get_knora_token()

if token == nil then
  return
end

-- A table of data about each file that was uploaded.
local file_upload_data = {}

-- Process the uploaded files.
for file_index, file_params in pairs(server.uploads) do
    -- Check that the file's MIME type is supported.

    local mime_info
    success, mime_info = server.file_mimetype(file_index)

    if not success then
        send_error(500, "server.file_mimetype() failed: " .. tostring(mime_info))
        return
    end

    local mime_type = mime_info["mimetype"]

    if mime_type == nil then
        send_error(400, "Could not determine MIME type of uploaded file")
        return
    end

    local original_filename = file_params["origname"]
    local file_info = get_file_info(original_filename, mime_type)

    if file_info == nil then
        send_error(400, "Unsupported MIME type: " .. tostring(mime_type))
        return
    end

    -- Make a random filename for the temporary file.
    local uuid62
    success, uuid62 = server.uuid62()

    if not success then
        send_error(500, "server.uuid62() failed: " .. uuid62)
        return
    end

    if server.secure then
        protocol = 'https://'
    else
        protocol = 'http://'
    end
    
    local tmp_storage_filename = uuid62 .. "." .. file_info["extension"]
    
    -- Add a subdirectory path if necessary.
    local hashed_tmp_storage_filename
    success, hashed_tmp_storage_filename = helper.filename_hash(tmp_storage_filename)

    if not success then
        send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_filename))
        return
    end

    local tmp_storage_file_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_filename

    -- Create a IIIF base URL for the converted file.
    local tmp_storage_url = get_external_protocol() .. "://" .. get_external_hostname() .. ":" .. get_external_port() .. '/tmp/' .. tmp_storage_filename

    -- Construct response data about the file that was uploaded.

    local media_type = file_info["media_type"]
    local this_file_upload_data = {}
    this_file_upload_data["internalFilename"] = tmp_storage_filename
    this_file_upload_data["originalFilename"] = original_filename
    this_file_upload_data["temporaryUrl"] = tmp_storage_url
    this_file_upload_data["fileType"] = media_type
    file_upload_data[file_index] = this_file_upload_data

    -- Is this an image file?
    if media_type == IMAGE then
        -- Yes. Create a new Lua image object. This reads the image into an
        -- internal in-memory representation independent of the original
        -- image format.
        local uploaded_image
        success, uploaded_image = SipiImage.new(file_index)

        if not success then
            send_error(500, "SipiImage.new() failed: " .. tostring(uploaded_image))
            return
        end

        -- Check that the file extension is correct for the file's MIME type.
        local check
        success, check = uploaded_image:mimetype_consistency(mime_type, original_filename)

        if not success then
            send_error(500, "upload.lua: uploaded_image:mimetype_consistency() failed: " .. check)
            return
        end

        if not check then
            send_error(400, MIMETYPES_INCONSISTENCY)
            return
        end

        -- Convert the image to JPEG 2000 format.

        success, error_msg = uploaded_image:write(tmp_storage_file_path)

        if not success then
            send_error(500, "uploaded_image:write() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
            return
        end

        server.log("upload.lua: wrote image file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)
    else
        -- It's not an image file. Just move it to its temporary storage location.
        
        success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
        
        if not success then
            send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
            return
        end

        server.log("upload.lua: wrote non-image file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)
    end
end

-- Clean up old temporary files.
clean_temp_dir()

-- Return the file upload data in the response.
local response = {}
response["uploadedFiles"] = file_upload_data
send_success(response)
