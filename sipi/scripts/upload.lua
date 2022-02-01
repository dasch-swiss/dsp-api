-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--
-- Upload route for binary files (currently only images) to be used with Knora.
--

require "file_info"
require "send_response"
require "jwt"
require "clean_temp_dir"
require "util"
Json = require "json"
-- local sh = require "sh"

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
-- Write sidecar file
function write_sidecar_file()


end

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
if not success then -- tests server.fs.exists
    -- fs.exist was not run successful. This does not mean, that the tmp folder is not there.
    send_error(500, "server.fs.exists() failed: " .. exists)
    return
end
if not exists then -- checks the response of server.fs.exists
    -- tmp folder does not exist
    server.log("temp folder missing: " .. tmpFolder, server.loglevel.LOG_ERR)
    success, error_msg = server.fs.mkdir(tmpFolder, 511)
    if not success then
        send_error(500, "server.fs.mkdir() failed: " .. error_msg)
        return
    end
end

-- A table of data about each file that was uploaded.
local file_upload_data = {}

-- additional sidecar data in case of video or audio (e.g. duration)
local additional_sidecar_data = {}

-- Process the uploaded files.
for file_index, file_params in pairs(server.uploads) do
    --
    -- Check that the file's MIME type is supported.
    --
    local mime_info
    success, mime_info = server.file_mimetype(file_index)
    if not success then
        send_error(415, "server.file_mimetype() failed: " .. tostring(mime_info))
        return
    end
    local mime_type = mime_info["mimetype"]
    if mime_type == nil then
        send_error(415, "Could not determine MIME type of uploaded file")
        return
    end

    --
    -- get some more MIME type related information
    --
    local original_filename = file_params["origname"]
    local file_info = get_file_info(original_filename, mime_type)
    if file_info == nil then
        send_error(415, "Unsupported MIME type: " .. tostring(mime_type))
        return
    end

    -- Make a random filename for the temporary file.
    local uuid62
    success, uuid62 = server.uuid62()
    if not success then
        send_error(500, "server.uuid62() failed: " .. uuid62)
        return
    end


    -- Construct response data about the file that was uploaded.
    local media_type = file_info["media_type"]

    -- Add a subdirectory path if necessary.
    local tmp_storage_filename
    if media_type == IMAGE then
        tmp_storage_filename = uuid62 .. ".jp2"
    else
        tmp_storage_filename = uuid62 .. "." .. file_info["extension"]
    end
    local hashed_tmp_storage_filename
    success, hashed_tmp_storage_filename = helper.filename_hash(tmp_storage_filename)
    if not success then
        send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_filename))
        return
    end

    -- filename for sidecar file
    local tmp_storage_sidecar = uuid62 .. ".info"
    local hashed_tmp_storage_sidecar
    success, hashed_tmp_storage_sidecar = helper.filename_hash(tmp_storage_sidecar)
    if not success then
        send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_sidecar))
        return
    end

    -- filename for original file copy
    local tmp_storage_original = uuid62 .. "." .. file_info["extension"] .. ".orig"
    local hashed_tmp_storage_original
    success, hashed_tmp_storage_original = helper.filename_hash(tmp_storage_original)
    if not success then
        send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_original))
        return
    end

    local tmp_storage_file_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_filename
    local tmp_storage_sidecar_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_sidecar
    local tmp_storage_original_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_original

    -- Create a IIIF base URL for the converted file.
    local tmp_storage_url = get_external_protocol() .. "://" .. get_external_hostname() .. ":" .. get_external_port() .. '/tmp/' .. tmp_storage_filename

    -- Copy original file also to tmp
    success, error_msg = server.copyTmpfile(file_index, tmp_storage_original_path)
    if not success then
        send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_original_path) .. ": " .. tostring(error_msg))
        return
    end

    -- Is this an image file?
    if media_type == IMAGE then
        --
        -- Yes. Create a new Lua image object. This reads the image into an
        -- internal in-memory representation independent of the original image format.
        --
        local uploaded_image
        success, uploaded_image = SipiImage.new(file_index, {original = original_filename, hash = "sha256"})
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

    -- Is this a video file?
    elseif media_type == VIDEO then
        server.log("upload.lua: video file type " .. media_type, server.loglevel.LOG_DEBUG)
        success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
        if not success then
            send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
            return
        end
        server.log("upload.lua: wrote video file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)

        
    --     server.log("I'm a video")
    --     local uploaded_video
    --     -- success, uploaded_video = SipiImage.new(file_index, {original = original_filename, hash = "sha256"})
    --     -- if not success then
    --     --     send_error(500, "SipiImage.new() failed: " .. tostring(uploaded_image))
    --     --     return
    --     -- end

    --     expected (to close 'for' at line 63) near <eof>, scriptname: /sipi/scripts/upload.lua


    --     -- move it to its temporary storage location
    --     success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
    --     if not success then
    --         send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
    --         return
    --     end


    --     -- run shell script to convert video and extract preview frames
    --     -- use os.execute
        -- os.execute("ffprobe -i " .. tmp_storage_file_path)

        -- get video file info with ffprobe and save as json file
        -- local tmp_storage_ffprobe = uuid62 .. ".json"
        -- local hashed_tmp_storage_ffprobe
        -- success, hashed_tmp_storage_ffprobe = helper.filename_hash(tmp_storage_ffprobe)
        -- if not success then
        --     send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_ffprobe))
        --     return
        -- else
        -- end

        -- local tmp_storage_ffprobe_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_ffprobe

        -- os.execute("ffprobe -v quiet -print_format json -show_format -show_streams " .. tmp_storage_file_path .. " > " .. tmp_storage_ffprobe_path)

    else
        -- It's neither an image nor a video file. Just move it to its temporary storage location.
        success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
        if not success then
            send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
            return
        end
        server.log("upload.lua: wrote non-image file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)
    end

    --
    -- Calculate checksum of original file
    --
    local checksum_original = file_checksum(tmp_storage_original_path)

    --
    -- Calculate checksum of derivative file
    --
    local checksum_derivative = file_checksum(tmp_storage_file_path)


    --
    -- prepare and write sidecar file
    --
    local sidecar_data = {}

    if media_type == VIDEO then
        
        local handle

        -- get video duration
        handle = io.popen("ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 " .. tmp_storage_file_path)
        local duration = handle:read("*a")
        duration = duration:gsub("[\n\r]", "")
        duration = tonumber(duration);
        handle:close()
        -- success, command = os.execute("ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 " .. tmp_storage_file_path .. " > " .. duration)
        -- server.log("ffprobe get duration: " .. duration, server.loglevel.LOG_DEBUG)
        if not duration then
            send_error(417, "upload.lua: ffprobe get duration failed: " .. duration)
        else
            server.log("upload.lua: ffprobe get duration: " .. duration, server.loglevel.LOG_DEBUG)
        end
        -- get video width (dimX)
        handle = io.popen("ffprobe -v error -show_entries stream=width -select_streams v -of default=noprint_wrappers=1:nokey=1 " .. tmp_storage_file_path)
        local width = handle:read("*a")
        width = width:gsub("[\n\r]", "")
        width = tonumber(width);
        handle:close()
        if not width then
            send_error(417, "upload.lua: ffprobe get width failed: " .. width)
        else
            server.log("upload.lua: ffprobe get width: " .. width, server.loglevel.LOG_DEBUG)
        end
        -- get video height (dimY)
        handle = io.popen("ffprobe -v error -show_entries stream=height -select_streams v -of default=noprint_wrappers=1:nokey=1 " .. tmp_storage_file_path)
        local height = handle:read("*a")
        height = height:gsub("[\n\r]", "")
        height = tonumber(height);
        handle:close()
        if not height then
            send_error(417, "upload.lua: ffprobe get height failed: " .. height)
        else
            server.log("upload.lua: ffprobe get height: " .. height, server.loglevel.LOG_DEBUG)
        end
        -- get video fps
        -- handle = io.popen("ffprobe -v error -show_entries stream=r_frame_rate -select_streams v -of default=noprint_wrappers=1:nokey=1 " .. tmp_storage_file_path)
        -- local fps = handle:read("*a")
        local fps = 29.9997 -- fps:gsub("[\n\r]", "")
        -- fps = tonumber(fps);
        -- handle:close()
        -- if not fps then
        --     send_error(417, "upload.lua: ffprobe get fps failed: " .. fps)
        -- else
        --     server.log("upload.lua: ffprobe get fps: " .. fps, server.loglevel.LOG_DEBUG)
        -- end

        sidecar_data = {
            originalFilename = original_filename,
            checksumOriginal = checksum_original,
            originalInternalFilename = hashed_tmp_storage_original,
            internalFilename = tmp_storage_filename,
            checksumDerivative = checksum_derivative,
            width = width,
            height = height,
            duration = duration,
            fps = fps
        }

    else
        sidecar_data = {
            originalFilename = original_filename,
            checksumOriginal = checksum_original,
            originalInternalFilename = hashed_tmp_storage_original,
            internalFilename = tmp_storage_filename,
            checksumDerivative = checksum_derivative
        }
    end


    local success, jsonstr = server.table_to_json(sidecar_data)
    if not success then
        send_error(500, "Couldn't create json string!")
        return
    end
    local sidecar = io.open(tmp_storage_sidecar_path, "w")
    sidecar:write(jsonstr)
    sidecar:close()

    local this_file_upload_data = {
        internalFilename = tmp_storage_filename,
        originalFilename = original_filename,
        temporaryUrl = tmp_storage_url,
        fileType = media_type,
        sidecarFile = tmp_storage_sidecar,
        checksumOriginal = checksum_orig,
        checksumDerivative = checksum_derivative
    }
    file_upload_data[file_index] = this_file_upload_data

end

-- Clean up old temporary files.
clean_temp_dir()
-- Return the file upload data in the response.
local response = {}
response["uploadedFiles"] = file_upload_data
send_success(response)
