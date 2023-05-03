-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--
-- Upload route for binary files.
--

require "file_info"
require "send_response"
require "jwt"
require "util"
local json = require "json"

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

-- Buffer the response (helps with error handling).
local success, error_msg = server.setBuffer()
if not success then
    send_error(500, "server.setBuffer() failed: " .. error_msg)
    return
end

-- Check for a valid JSON Web Token.
local token = get_knora_token()
if token == nil then
    return
end

-- Check that the temp folder is created
local tmp_folder_root = config.imgroot .. '/tmp'
success, error_msg = check_create_dir(tmp_folder_root)
if not success then
    send_error(500, error_msg)
    return
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
        server.log("file_info appears to be nil for: " .. tostring(original_filename), server.loglevel.LOG_ERR)
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

    -- create tmp folder and subfolders for the files
    local tmp_folder = create_tmp_folder(tmp_folder_root, hashed_tmp_storage_filename)

    local tmp_storage_file_path = tmp_folder .. '/' .. hashed_tmp_storage_filename
    local tmp_storage_sidecar_path = tmp_folder .. '/' .. hashed_tmp_storage_sidecar
    local tmp_storage_original_path = tmp_folder .. '/' .. hashed_tmp_storage_original


    -- Create a IIIF base URL for the converted file.
    local tmp_storage_url = get_external_protocol() .. "://" .. get_external_hostname() .. ":" .. get_external_port() .. '/' .. tmp_storage_file_path

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

        -- Normalize image orientation to top-left --
        success, error_msg = uploaded_image:topleft()
        if not success then
            server.log("upload.lua: normalize image orientation failed for: " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg), server.loglevel.LOG_ERR)
            send_error(500, "upload.lua: normalize image orientation failed for: " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
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
        success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
        if not success then
            send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
            return
        end
        -- extract the frames from video file; they will be used for preview
        success_key_frames, error_msg_key_frames = os.execute("./scripts/export-moving-image-frames.sh -i " .. tmp_storage_file_path)
        if not success_key_frames then
            send_error(500, "export-moving-image-frames.sh failed: " .. error_msg_key_frames)
            return
        end
        server.log("upload.lua: wrote video file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)

    else
        -- It's neither an image nor a video file. Move it to its temporary storage location.
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
        -- get video file information with ffprobe: width, height, duration and frame rate (fps)
        handle = io.popen("ffprobe -v error -select_streams v:0 -show_entries stream=width,height,bit_rate,duration,nb_frames,r_frame_rate -print_format json -i " .. tmp_storage_file_path)
        local file_meta = handle:read("*a")
        handle:close()
        -- decode ffprobe output into json, but only first stream
        local file_meta_json = json.decode( file_meta )['streams'][1]
        
        -- get video duration
        local duration = tonumber(file_meta_json['duration'])
        if not duration then
            send_error(417, "upload.lua: ffprobe get duration failed: " .. duration)
        end
        -- get video width (dimX)
        local width = tonumber(file_meta_json['width']);
        if not width then
            send_error(417, "upload.lua: ffprobe get width failed: " .. width)
        end
        -- get video height (dimY)
        local height = tonumber(file_meta_json['height'])
        if not height then
            send_error(417, "upload.lua: ffprobe get height failed: " .. height)
        end
        -- get video fps
        -- this is a bit tricky, because ffprobe returns something like 30/1 or 179/6; so, we have to convert into a floating point number;
        -- or we can calculate fps from number of frames divided by duration
        local fps
        local frames = tonumber(file_meta_json['nb_frames'])
        if not frames then
            send_error(417, "upload.lua: ffprobe get frames failed: " .. frames)
        else
            fps = frames / duration
            if not fps then
                send_error(417, "upload.lua: ffprobe get fps failed: " .. fps)
            end
        end

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

        -- TODO: similar setup for audio files; get duration with ffprobe and write extended sidecar data (DEV-770)
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

-- Return the file upload data in the response.
local response = {}
response["uploadedFiles"] = file_upload_data
send_success(response)
