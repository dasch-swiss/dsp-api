-- * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "file_specific_folder_util"
require "authentication"
require "log_util"
require "util"

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
-- This function returns the API URL from the given parameters
-------------------------------------------------------------------------------
local function get_api_url(webapi_hostname, webapi_port, prefix, identifier)
    return 'http://' .. webapi_hostname .. ':' .. webapi_port .. '/admin/files/' .. prefix .. '/' .. identifier
end

--- This function gets the access decision for a file by requesting it from
--- the DSP-API.
--- @param shortcode string The shortcode of the file's project.
--- @param file_name string The name of the file.
--- @param jwt_raw string|nil The (optional) raw JWT token.
--- @return table|nil The access decision for the file or nil if an error occurred.
local function get_permission_on_file(shortcode, file_name, jwt_raw)
    local webapi_hostname = get_api_hostname()
    local webapi_port = get_api_port()
    local api_url = get_api_url(webapi_hostname, webapi_port, shortcode, file_name)
    log("get_permission_on_file - api_url: " .. api_url, server.loglevel.LOG_DEBUG)

    -- request the permissions on the image from DSP-API
    local success, result = server.http("GET", api_url, _auth_header(jwt_raw), 5000)
    if not success then
        log("get_permission_on_file - server.http() failed: " .. result, server.loglevel.LOG_ERR)
        return nil
    end

    if result.status_code ~= 200 then
        log("get_permission_on_file - DSP-API returned HTTP status code " .. result.status_code, server.loglevel.LOG_ERR)
        log("get_permission_on_file - result body: " .. tostring(result.body), server.loglevel.LOG_ERR)
        return nil
    end

    log("get_permission_on_file - response body: " .. tostring(result.body), server.loglevel.LOG_DEBUG)

    local response_json
    success, response_json = server.json_to_table(result.body)
    if not success then
        log("get_permission_on_file - server.json_to_table() failed: " .. response_json, server.loglevel.LOG_ERR)
        return nil
    end

    return response_json
end

function _auth_header(jwt_raw)
    if jwt_raw == nil then
        return nil
    else
        return { Authorization = "Bearer " .. jwt_raw }
    end
end

--- The path Sipi uses to watermark a clamped image. DSP-API says *whether* to watermark; the file itself is a
--- Sipi deployment detail and never travels on the wire.
local WATERMARK_PATH = "/sipi/scripts/watermark.tif"

--- Translates the DSP-API `derivative` decision into the permission Sipi speaks. No permission arithmetic and
--- no knowledge of the media kind happens here - DSP-API has already decided both.
--- @param caller string The name of the calling hook, for log messages.
--- @param access table The access decision returned by DSP-API.
--- @return string|table A Sipi permission; 'deny' for a decision this hook does not recognise.
local function _sipi_permission(caller, access)
    local derivative = access.derivative

    if derivative == "full" then
        return 'allow'
    elseif derivative == "stream" then
        return 'stream'
    elseif derivative == "denied" then
        log(caller .. " - derivative 'denied', access denied", server.loglevel.LOG_WARNING)
        return 'deny'
    elseif derivative == "clamped" then
        local settings_for_sipi = { type = "restrict" }
        if access.watermark then
            settings_for_sipi['watermark'] = WATERMARK_PATH
        else
            settings_for_sipi['size'] = access.size
        end
        log(caller .. " - clamped: " .. tableToString(settings_for_sipi), server.loglevel.LOG_DEBUG)
        return settings_for_sipi
    else
        -- A literal this hook does not know must deny rather than fall through to something permissive, and it
        -- must be loud: a silent catch-all would make a vocabulary change look like mysterious denials. Sipi
        -- exposes no metrics binding to Lua, so this log line is what a counter would otherwise record.
        log(caller .. " - unrecognised derivative decision '" .. tostring(derivative) .. "', access denied",
                server.loglevel.LOG_ERR)
        return 'deny'
    end
end

-------------------------------------------------------------------------------
-- This function is being called from Sipi before the file is served.
-- DSP-API is called to ask for the access decision on the file.
--
-- Parameters:
--    prefix: This is the prefix that is given in the IIIF URL
--    identifier: The identifier for the image
--    cookie: The cookie that may be present, ignored for now
--
-- Returns:
--    permission:
--       'allow': the view is allowed with the given IIIF parameters
--       'stream': the view is allowed, but the file may not be handed over
--       'restrict:watermark=<path-to-watermark>': Add a watermark
--       'restrict:size=<iiif-size-string>': reduce size/resolution
--       'deny': no access!
--    filepath: path on the server where the master file is located
-------------------------------------------------------------------------------
function pre_flight(prefix, identifier, cookie)
    log("pre_flight - called with prefix:" .. prefix .. ", identifier: " .. identifier, server.loglevel.LOG_DEBUG)

    local filepath = find_file(identifier, prefix)
    if filepath == nil then
        return _file_not_found_response()
    end

    log("pre_flight - filepath: " .. filepath, server.loglevel.LOG_DEBUG)

    if prefix == "tmp" then
        log("pre_flight - always allow access to tmp folder", server.loglevel.LOG_DEBUG)
        return 'allow', filepath
    end

    local token, error = auth_get_jwt_decoded()
    if error == nil and _is_system_or_project_admin(token, prefix) then
        log("pre_flight - always allow access for system or project admin", server.loglevel.LOG_DEBUG)
        return 'allow', filepath
    end

    local jwt_raw = auth_get_jwt_raw()
    local access = get_permission_on_file(prefix, identifier, jwt_raw)
    if access == nil then
        return _file_not_found_response()
    end

    local permission = _sipi_permission("pre_flight", access)
    if permission == 'deny' then
        return 'deny'
    end
    return permission, filepath
end

--- Checks if the user is a system or project admin.
--- @param token table The decoded JWT token.
--- @param shortcode string The shortcode of the project.
--- @return boolean True if the user is a system or project admin, false otherwise.
function _is_system_or_project_admin(token, shortcode)
    if shortcode == nil or token == nil or token["scope"] == nil then
        return false
    else
        local write_prj_scope = "write:project:" .. shortcode
        local scopes = str_splitString(token["scope"], " ")
        return table_contains(scopes, "admin") or table_contains(scopes, write_prj_scope)
    end
end

function _file_not_found_response()
    return "allow", "file_does_not_exist"
end

-------------------------------------------------------------------------------
-- This function is being called from Sipi before the file is served.
-- DSP-API is called to ask for the access decision on the file.
--
-- Parameters:
--    identifier: The identifier for the image
--    cookie: The cookie that may be present // ignored for now
--
-- Returns:
--    permission:
--       'allow': the view is allowed with the given IIIF parameters
--       'stream': the view is allowed, but the file may not be handed over
--       'restrict:...': a clamp Sipi refuses on this route
--       'deny': no access!
--    filepath: path on the server where the master file is located
-------------------------------------------------------------------------------
function file_pre_flight(identifier, cookie)
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

    local filepath = find_file(file_name, shortcode)
    if filepath == nil then
        return _file_not_found_response()
    end

    local filepath_preview = find_file(file_name_preview, shortcode)
    log("file_pre_flight - filepath: " .. filepath, server.loglevel.LOG_DEBUG)
    local jwt_raw = auth_get_jwt_raw()
    local access = get_permission_on_file(shortcode, file_name, jwt_raw)
    if access == nil then
        return _file_not_found_response()
    end

    local permission = _sipi_permission("file_pre_flight", access)
    if permission == 'deny' then
        return 'deny'
    end
    if #segments == 5 then
        return permission, filepath_preview
    else
        return permission, filepath
    end
end
