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
-- Upload route for binary files (currently only images) to be used with Knora.
--

require "get_mediatype"
require "send_response"
require "jwt"
require "clean_temp_dir"

-- Buffer the response (helps with error handling).

local success, error_msg = server.setBuffer()

if not success then
    server.log("server.setBuffer() failed: " .. error_msg, server.loglevel.LOG_ERR)
    return
end

-- Check for a valid JSON Web Token from Knora.

local token = get_knora_token()

if token == nil then
  return
end

-- Create the temporary directory if necessary.

local temp_dir = config.imgroot .. '/tmp/'
local success, exists = server.fs.exists(temp_dir)

if not success then
    send_error(500, exists)
    return
end

if not exists then
    local success, error_msg = server.fs.mkdir(temp_dir, 511)
    if not success then
        send_error(500, error_msg)
        return
    end
end

-- A table of data about each file that was uploaded.
local file_upload_data = {}

-- Process the uploaded files.
for image_index, image_params in pairs(server.uploads) do
    -- Check that the file is an image (TODO: support other file types.)

    local success, mime_info = server.file_mimetype(image_index)

    if not success then
        send_error(500, mime_info)
        return
    end

    local mime_type = mime_info["mimetype"]

    if mime_type == nil then
        send_error(500, "Could not determine MIME type of uploaded file")
        return
    end

    local media_type = get_mediatype(mime_info["mimetype"])

    if media_type ~= IMAGE then
        send_error(400, "Unsupported MIME type: " .. mime_type)
        return
    end

    -- Get the file's original name.
    local original_filename = image_params["origname"]

    -- Create a new Lua image object. This reads the image into an
    -- internal in-memory representation independent of the original
    -- image format.
    local success, uploaded_image = SipiImage.new(image_index)

    if not success then
        send_error(500, uploaded_image)
        return
    end

    -- Make a random filename for the converted image file.
    local success, uuid62 = server.uuid62()

    if not success then
        send_error(500, "Could not generate random filename")
        return
    end

    local jp2_filename = uuid62 .. '.jp2'

    if server.secure then
        protocol = 'https://'
    else
        protocol = 'http://'
    end

    -- Create a IIIF base URL for the converted file.
    local iiif_base_url = protocol .. server.host .. '/tmp/' .. jp2_filename

    -- Construct response data about the file that was uploaded.
    local this_file_upload_data = {}
    this_file_upload_data["internalFilename"] = jp2_filename
    this_file_upload_data["originalFilename"] = original_filename
    this_file_upload_data["temporaryBaseIIIFUrl"] = iiif_base_url
    file_upload_data[image_index] = this_file_upload_data

    -- Convert the image to JPEG 2000 format, saving it in a subdirectory of
    -- the temporary directory.

    local success, hashed_jp2_filename = helper.filename_hash(jp2_filename)

    if not success then
        send_error(500, hashed_jp2_filename)
        return
    end

    local jp2_file_path = config.imgroot .. '/tmp/' .. hashed_jp2_filename
    local success, error_msg = uploaded_image:write(jp2_file_path)

    if not success then
        send_error(500, error_msg)
        return
    end

    server.log("upload.lua: wrote JPEG 2000 file to " .. jp2_file_path, server.loglevel.LOG_DEBUG)

end

-- Clean up old temporary files.
clean_temp_dir()

-- Return the file upload data in the response.
local response = {}
response["uploadedFiles"] = file_upload_data
send_success(response)
