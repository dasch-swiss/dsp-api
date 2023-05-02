-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "get_knora_session"
require "log_util"

-------------------------------------------------------------------------------
-- This function returns the filepath of the temporary location the file is 
-- stored in. The path is created from the first four characters of the filename.
-------------------------------------------------------------------------------
function get_tmp_filepath(shortcode, filename)
    local first_character_of_filename = filename:sub(1, 1)
    local second_character_of_filename = filename:sub(2, 2)
    local third_character_of_filename = filename:sub(3, 3)
    local fourth_character_of_filename = filename:sub(4, 4)

    local first_subfolder = first_character_of_filename .. second_character_of_filename
    local second_subfolder = third_character_of_filename .. fourth_character_of_filename

    local filepath = ''
    if config.prefix_as_path then
        filepath = config.imgroot .. '/' .. shortcode .. '/' .. first_subfolder .. '/' .. second_subfolder .. '/' .. filename
    else
        filepath = config.imgroot .. '/' .. first_subfolder .. '/' .. second_subfolder .. '/' .. filename
    end
    return filepath
end

-------------------------------------------------------------------------------
-- This function returns the segments from the identifier
-- The identifier is expected to look like: /sipi/images/:shortcode/:filename
-------------------------------------------------------------------------------
function get_segments_from_identifier(identifier)
    local segments = {}
    for w in string.gmatch(identifier, "[^\\/]+") do
        table.insert(segments, w)
    end
    return segments
end

-------------------------------------------------------------------------------
-- This function checks the cookie and returns the cookie header
-------------------------------------------------------------------------------
function get_cookie_header(cookie)
    -- tries to extract the DSP session name and id from the cookie:
    -- gets the digits between "sid=" and the closing ";" (only given in case of several key value pairs)
    -- returns nil if it cannot find it
    local session = get_session_id(cookie)

    if session == nil or session["name"] == nil or session["id"] == nil then
        -- no session could be extracted
        log("check_cookie - cookie key is invalid: " .. cookie, server.loglevel.LOG_ERR)
    else
        dsp_cookie_header = { Cookie = session["name"] .. "=" .. session["id"] }
        log("check_cookie - dsp_cookie_header: " ..
        dsp_cookie_header["Cookie"], server.loglevel.LOG_DEBUG)
    end

    return dsp_cookie_header
end

-------------------------------------------------------------------------------
-- This function gets the hostname of the DSP-API
-- either from the environment variable or from config
-------------------------------------------------------------------------------
function get_api_hostname()
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
function get_api_port()
    local port = os.getenv("SIPI_WEBAPI_PORT")
    if port == nil then
        port = config.knora_port
    end

    return port
end


-------------------------------------------------------------------------------
-- This function returns the API URL from the given parameters
-------------------------------------------------------------------------------
function get_api_url(webapi_hostname, webapi_port, prefix, identifier)
    return 'http://' .. webapi_hostname .. ':' .. webapi_port .. '/admin/files/' .. prefix .. '/' .. identifier
end
-------------------------------------------------------------------------------
-- This function is being called from sipi before the file is served.
-- DSP-API is called to ask for the user's permissions on the file
-- Parameters:
--    prefix: This is the prefix that is given on the IIIF url
--    identifier: the identifier for the image
--    cookie: The cookie that may be present
--
-- Returns:
--    permission:
--       'allow' : the view is allowed with the given IIIF parameters
--       'restrict:watermark=<path-to-watermark>' : Add a watermark
--       'restrict:size=<iiif-size-string>' : reduce size/resolution
--       'deny' : no access!
--    filepath: server-path where the master file is located
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

    local dsp_cookie_header = nil

    if cookie ~= '' then
        dsp_cookie_header = get_cookie_header(cookie)
    end

    local webapi_hostname = get_api_hostname()
    local webapi_port = get_api_port()

    local api_url = get_api_url(webapi_hostname, webapi_port, prefix, identifier)
    log("pre_flight - api_url: " .. api_url, server.loglevel.LOG_DEBUG)

    -- request the image over HTTP
    local success, result = server.http("GET", api_url, dsp_cookie_header, 5000)

    if not success then
        log("pre_flight - Server.http() failed: " .. result, server.loglevel.LOG_ERR)
        return 'deny'
    end

    if result.status_code ~= 200 then
        log("pre_flight - DSP-API returned HTTP status code " .. result.status_code, server.loglevel.LOG_ERR)
        log("result body: " .. result.body, server.loglevel.LOG_ERR)
        return 'deny'
    end

    log("pre_flight - response body: " .. tostring(result.body), server.loglevel.LOG_DEBUG)

    local success, response_json = server.json_to_table(result.body)
    if not success then
        log("pre_flight - Server.http() failed: " .. response_json, server.loglevel.LOG_ERR)
        return 'deny'
    end

    log("pre_flight - permission code: " .. response_json.permissionCode, server.loglevel.LOG_DEBUG)

    if response_json.permissionCode == 0 then
        -- no view permission on file
        log("pre_flight - permission code 0 (no view), access denied", server.loglevel.LOG_WARNING)
        return 'deny'
    elseif response_json.permissionCode == 1 then
        -- restricted view permission on file
        -- either watermark or size (depends on project, should be returned with permission code by Sipi responder)
        -- currently, only size is used

        local restrictedViewSize

        if response_json.restrictedViewSettings ~= nil then
            log("pre_flight - restricted view settings - watermark: " .. tostring(response_json.restrictedViewSettings.watermark), server.loglevel.LOG_DEBUG)

            if response_json.restrictedViewSettings.size ~= nil then
                log("pre_flight - restricted view settings - size: " .. tostring(response_json.restrictedViewSettings.size), server.loglevel.LOG_DEBUG)
                restrictedViewSize = response_json.restrictedViewSettings.size
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
    elseif response_json.permissionCode >= 2 then
        -- full view permissions on file
        return 'allow', filepath
    else
        -- invalid permission code
        return 'deny'
    end
end

-------------------------------------------------------------------------------
-- This function is being called from sipi before the file is served.
-- DSP-API is called to ask for the user's permissions on the file
-- Parameters:
--    identifier: the file path for the file
--    cookie: The cookie that may be present
--
-- Returns:
--    permission:
--       'allow' : the view is allowed with the given IIIF parameters
--       'deny' : no access!
--    filepath: server-path where the master file is located
-------------------------------------------------------------------------------

function file_pre_flight(identifier, cookie)
    log("file_pre_flight called in sipi.init.lua", server.loglevel.LOG_DEBUG)
    log("file_pre_flight - param identifier: " .. identifier, server.loglevel.LOG_DEBUG)

    local segments = get_segments_from_identifier(identifier)
    
    -- get the shortcode
    local shortcode = segments[3]
    log("file_pre_flight - shortcode: " .. shortcode, server.loglevel.LOG_DEBUG)

    if shortcode == "082A" then -- SVA / 082A allows file access even when permissions are set to restricted!
        return "allow", identifier
    end

    -- get the file name
    local file_name = ''
    local file_name_preview  = ''
    if #segments == 4 then
        file_name = segments[4]
        log("file_pre_flight - file name: " .. file_name, server.loglevel.LOG_DEBUG)
    -- in case of a preview file of a video, get the file path of the video file to check permissions on the video
    elseif #segments == 5 then
        log("file_pre_flight - found 5 segments, it's assumed to the preview file for a video", server.loglevel.LOG_ERR)
        file_name = segments[4] .. '.mp4'
        file_name_preview = segments[4] .. '/' .. segments[5]
        log("file_pre_flight - file name: " .. file_name, server.loglevel.LOG_DEBUG)
        log("file_pre_flight - file name preview: " .. file_name_preview, server.loglevel.LOG_DEBUG)
    else
        log("file_pre_flight - wrong number of segments. Got: [" .. table.concat(segments, ",") .. "]", server.loglevel.LOG_ERR)
        return "deny"
    end

    local filepath = get_tmp_filepath(shortcode, file_name)
    local filepath_preview = get_tmp_filepath(shortcode, file_name_preview)

    log("file_pre_flight - filepath: " .. filepath, server.loglevel.LOG_DEBUG)

    local dsp_cookie_header = nil

    if cookie ~= '' then
        local dsp_cookie_header = get_cookie_header(cookie)
    end

    local webapi_hostname = get_api_hostname()
    local webapi_port = get_api_port()

    local api_url = get_api_url(webapi_hostname, webapi_port, shortcode, file_name)
    log("file_pre_flight - api_url: " .. api_url, server.loglevel.LOG_DEBUG)

    -- request the image over HTTP
    local success, result = server.http("GET", api_url, dsp_cookie_header, 5000)

    if not success then
        log("file_pre_flight - Server.http() failed: " .. result, server.loglevel.LOG_ERR)
        return 'deny'
    end

    if result.status_code ~= 200 then
        log("file_pre_flight - DSP-API returned HTTP status code " .. result.status_code, server.loglevel.LOG_ERR)
        log("result body: " .. result.body, server.loglevel.LOG_ERR)
        return 'deny'
    end

    log("file_pre_flight - response body: " .. tostring(result.body), server.loglevel.LOG_DEBUG)

    local success, response_json = server.json_to_table(result.body)
    if not success then
        log("file_pre_flight - Server.http() failed: " .. response_json, server.loglevel.LOG_WARNING)
        return 'deny'
    end

    log("file_pre_flight - permission code: " .. response_json.permissionCode, server.loglevel.LOG_DEBUG)

    if response_json.permissionCode == 0 then
        -- no view permission on file
        log("file_pre_flight - permission code 0 (no view), access denied", server.loglevel.LOG_WARNING)
        return 'deny'
    elseif response_json.permissionCode == 1 then
        -- restricted view permission on file means full access !! Because, at the moment, this doesn't have a meaning for files other than images.
        log("file_pre_flight - permission code 1 (restricted view), access granted", server.loglevel.LOG_DEBUG)
        if #segments == 5 then
            return 'allow', filepath_preview
        else
            return 'allow', filepath
        end
    elseif response_json.permissionCode >= 2 then
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
