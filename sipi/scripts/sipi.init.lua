-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "get_knora_session"
require "log_util"

-------------------------------------------------------------------------------
-- This function returns the filepath according to the old way the file was
-- stored in.
-------------------------------------------------------------------------------
local function get_old_tmp_filepath(shortcode, filename)
    local filepath = ''
    if config.prefix_as_path then
        filepath = config.imgroot .. '/' .. shortcode .. '/' .. filename
    else
        filepath = config.imgroot .. '/' .. filename
    end
    return filepath
end

-------------------------------------------------------------------------------
-- This function returns the filepath of the temporary location the file is
-- stored in. The path is created from the first four characters of the filename.
-------------------------------------------------------------------------------
local function get_tmp_filepath(shortcode, filename)
    local first_character_of_filename = string.lower(filename:sub(1, 1))
    local second_character_of_filename = string.lower(filename:sub(2, 2))
    local third_character_of_filename = string.lower(filename:sub(3, 3))
    local fourth_character_of_filename = string.lower(filename:sub(4, 4))

    local first_subfolder = first_character_of_filename .. second_character_of_filename
    local second_subfolder = third_character_of_filename .. fourth_character_of_filename

    local filepath = ''
    if config.prefix_as_path then
        filepath = config.imgroot .. '/' .. shortcode .. '/' .. first_subfolder .. '/' .. second_subfolder .. '/' ..
            filename
    else
        filepath = config.imgroot .. '/' .. first_subfolder .. '/' .. second_subfolder .. '/' .. filename
    end
    return filepath
end

-------------------------------------------------------------------------------
-- This function returns the segments from the identifier
-------------------------------------------------------------------------------
local function get_segments_from_identifier(identifier)
    local segments = {}
    for w in string.gmatch(identifier, "[^\\/]+") do
        table.insert(segments, w)
    end
    return segments
end

-------------------------------------------------------------------------------
-- This function checks the cookie and returns the cookie header
-------------------------------------------------------------------------------
local function check_and_get_cookie_header(cookie)
    -- tries to extract the DSP session name and id from the cookie:
    -- gets the digits between "sid=" and the closing ";" (only given in case of several key value pairs)
    -- returns nil if it cannot find it
    local session = get_session_id(cookie)
    local cookie_header

    if session == nil or session["name"] == nil or session["id"] == nil then
        -- no session could be extracted
        log("check_and_get_cookie_header - cookie key is invalid: " .. cookie, server.loglevel.LOG_ERR)
    else
        cookie_header = {
            Cookie = session["name"] .. "=" .. session["id"]
        }
        log("check_and_get_cookie_header - dsp_cookie_header: " .. cookie_header["Cookie"],
            server.loglevel.LOG_DEBUG)
    end

    return cookie_header
end

-------------------------------------------------------------------------------
-- This function gets the hostname of the DSP-API
-- either from the environment variable or from config
-------------------------------------------------------------------------------
local function get_api_hostname()
    local hostname = os.getenv("SIPI_WEBAPI_HOSTNAME")
    if hostname == nil then
        hostname = config.knora_path
    end

    return hostname
end

-------------------------------------------------------------------------------
-- This function gets the port of the DSP-API
-- either from the environment variable or from config
-------------------------------------------------------------------------------
local function get_api_port()
    local port = os.getenv("SIPI_WEBAPI_PORT")
    if port == nil then
        port = config.knora_port
    end

    return port
end

-------------------------------------------------------------------------------
-- This function returns the API URL from the given parameters
-------------------------------------------------------------------------------
local function get_api_url(webapi_hostname, webapi_port, prefix, identifier)
    return 'http://' .. webapi_hostname .. ':' .. webapi_port .. '/admin/files/' .. prefix .. '/' .. identifier
end

-------------------------------------------------------------------------------
-- This function gets the permissions defined on a file by requesting it from
-- the DSP-API.
-------------------------------------------------------------------------------
local function get_permission_on_file(shortcode, file_name, cookie_header)
    local webapi_hostname = get_api_hostname()
    local webapi_port = get_api_port()
    local api_url = get_api_url(webapi_hostname, webapi_port, shortcode, file_name)
    log("get_permission_on_file - api_url: " .. api_url, server.loglevel.LOG_DEBUG)

    -- request the permissions on the image from DSP-API
    local success, result = server.http("GET", api_url, cookie_header, 5000)
    if not success then
        log("get_permission_on_file - server.http() failed: " .. result, server.loglevel.LOG_ERR)
        return 'deny'
    end

    if result.status_code ~= 200 then
        log("get_permission_on_file - DSP-API returned HTTP status code " .. result.status_code, server.loglevel.LOG_ERR)
        log("get_permission_on_file - result body: " .. result.body, server.loglevel.LOG_ERR)
        return 'deny'
    end

    log("get_permission_on_file - response body: " .. tostring(result.body), server.loglevel.LOG_DEBUG)

    local response_json
    success, response_json = server.json_to_table(result.body)
    if not success then
        log("get_permission_on_file - server.json_to_table() failed: " .. response_json, server.loglevel.LOG_ERR)
        return 'deny'
    end

    return response_json
end

-------------------------------------------------------------------------------
-- This function is being called from Sipi before the file is served.
-- DSP-API is called to ask for the user's permissions on the file.
--
-- Parameters:
--    prefix: This is the prefix that is given in the IIIF URL
--    identifier: The identifier for the image
--    cookie: The cookie that may be present
--
-- Returns:
--    permission:
--       'allow': the view is allowed with the given IIIF parameters
--       'restrict:watermark=<path-to-watermark>': Add a watermark
--       'restrict:size=<iiif-size-string>': reduce size/resolution
--       'deny': no access!
--    filepath: path on the server where the master file is located
-------------------------------------------------------------------------------
function pre_flight(prefix, identifier, cookie)
    log("pre_flight called in sipi.init.lua", server.loglevel.LOG_DEBUG)
    log("pre_flight - param identifier: " .. identifier, server.loglevel.LOG_DEBUG)

    local filepath = get_tmp_filepath(prefix, identifier)
    log("pre_flight - filepath: " .. filepath, server.loglevel.LOG_DEBUG)

    if prefix == "tmp" then
        -- always allow access to tmp folder
        return 'allow', filepath
    end

    -- handle old way of file path - TODO: remove this block of code as soon as migration is done!
    local _, exists = server.fs.exists(filepath)
    log("pre_flight - does the file exist? " .. tostring(exists), server.loglevel.LOG_DEBUG)
    if not exists then
        filepath = get_old_tmp_filepath(prefix, identifier)
        log("pre_flight - couldn't find file at the given filepath, take old filepath instead: " .. filepath,
            server.loglevel.LOG_DEBUG)
    end

    local dsp_cookie_header = nil
    if cookie ~= '' then
        dsp_cookie_header = check_and_get_cookie_header(cookie)
    end

    local permission_info = get_permission_on_file(prefix, identifier, dsp_cookie_header)
    local permission_code = permission_info.permissionCode
    log("pre_flight - permission code: " .. permission_code, server.loglevel.LOG_DEBUG)

    if permission_code == 0 then
        -- no view permission on file
        log("pre_flight - permission code 0 (no view), access denied", server.loglevel.LOG_WARNING)
        return 'deny'
    elseif permission_code == 1 then
        -- restricted view permission on file
        -- either watermark or size (depends on project, should be returned by DSP-API)
        -- currently, only size is used

        local restrictedViewSize

        if permission_info.restrictedViewSettings ~= nil then
            log("pre_flight - restricted view settings - watermark: " ..
                tostring(permission_info.restrictedViewSettings.watermark), server.loglevel.LOG_DEBUG)

            if permission_info.restrictedViewSettings.size ~= nil then
                restrictedViewSize = permission_info.restrictedViewSettings.size
                log("pre_flight - restricted view settings - size: " .. tostring(restrictedViewSize),
                    server.loglevel.LOG_DEBUG)
            else
                log("pre_flight - using default restricted view size", server.loglevel.LOG_DEBUG)
                restrictedViewSize = config.thumb_size
            end
        else
            log("pre_flight - using default restricted view size", server.loglevel.LOG_DEBUG)
            restrictedViewSize = config.thumb_size
        end

        return {
            type = 'restrict',
            size = restrictedViewSize
        }, filepath
    elseif permission_code >= 2 then
        -- full view permissions on file
        return 'allow', filepath
    else
        -- invalid permission code
        return 'deny'
    end
end

-------------------------------------------------------------------------------
-- This function is being called from Sipi before the file is served.
-- DSP-API is called to ask for the user's permissions on the file.
--
-- Parameters:
--    identifier: The identifier for the image
--    cookie: The cookie that may be present
--
-- Returns:
--    permission:
--       'allow': the view is allowed with the given IIIF parameters
--       'deny': no access!
--    filepath: path on the server where the master file is located
-------------------------------------------------------------------------------
function file_pre_flight(identifier, cookie)
    log("file_pre_flight called in sipi.init.lua", server.loglevel.LOG_DEBUG)
    log("file_pre_flight - param identifier: " .. identifier, server.loglevel.LOG_DEBUG)

    local segments = get_segments_from_identifier(identifier)

    -- get the shortcode
    local shortcode = segments[3]
    log("file_pre_flight - shortcode: " .. shortcode, server.loglevel.LOG_DEBUG)

    -- get the file name
    local file_name = ''
    local file_name_preview = ''
    if #segments == 4 then
        file_name = segments[4]
        log("file_pre_flight - file name: " .. file_name, server.loglevel.LOG_DEBUG)
    elseif #segments == 5 then
        -- in case of a preview file of a video, get the file path of the video file to check permissions on the video
        log("file_pre_flight - found 5 segments, it's assumed to be the preview file for a video",
            server.loglevel.LOG_ERR)
        file_name = segments[4] .. '.mp4'
        file_name_preview = segments[4] .. '/' .. segments[5]
        log("file_pre_flight - file name: " .. file_name, server.loglevel.LOG_DEBUG)
        log("file_pre_flight - file name preview: " .. file_name_preview, server.loglevel.LOG_DEBUG)
    else
        log("file_pre_flight - wrong number of segments. Got: [" .. table.concat(segments, ",") .. "]",
            server.loglevel.LOG_ERR)
        return "deny"
    end

    local filepath = get_tmp_filepath(shortcode, file_name)
    local filepath_preview = get_tmp_filepath(shortcode, file_name_preview)
    log("file_pre_flight - filepath: " .. filepath, server.loglevel.LOG_DEBUG)

    -- handle old way of file path - TODO: remove this block of code as soon as migration is done!
    local _, exists = server.fs.exists(filepath)
    log("file_pre_flight - does the file exist? " .. tostring(exists), server.loglevel.LOG_DEBUG)
    if not exists then
        filepath = get_old_tmp_filepath(shortcode, file_name)
        filepath_preview = get_old_tmp_filepath(shortcode, file_name_preview)
        log("file_pre_flight - couldn't find file at the given filepath, take old filepath instead: " .. filepath,
            server.loglevel.LOG_DEBUG)
    end

    if shortcode == "082A" then -- SVA / 082A allows file access no matter what permissions are set!
        log("file_pre_flight - file requested for 082A: " .. identifier, server.loglevel.LOG_WARNING)
        return "allow", filepath
    end

    local dsp_cookie_header = nil
    if cookie ~= '' then
        dsp_cookie_header = check_and_get_cookie_header(cookie)
    end

    local permission_info = get_permission_on_file(shortcode, file_name, dsp_cookie_header)
    local permission_code = permission_info.permissionCode
    log("file_pre_flight - permission code: " .. permission_code, server.loglevel.LOG_DEBUG)

    if permission_code == 0 then
        -- no view permission on file
        log("file_pre_flight - permission code 0 (no view), access denied", server.loglevel.LOG_WARNING)
        return 'deny'
    elseif permission_code == 1 then
        -- restricted view permission on file means full access !! Because, at the moment, this doesn't have a meaning for files other than images.
        log("file_pre_flight - permission code 1 (restricted view), access granted", server.loglevel.LOG_DEBUG)
        if #segments == 5 then
            return 'allow', filepath_preview
        else
            return 'allow', filepath
        end
    elseif permission_code >= 2 then
        -- full view permissions on file
        log("file_pre_flight - access granted", server.loglevel.LOG_DEBUG)
        if #segments == 5 then
            return 'allow', filepath_preview
        else
            return 'allow', filepath
        end
    else
        -- invalid permission code
        return 'deny'
    end
end
