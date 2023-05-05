-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--
-- Deletes old files from Sipi's tmp directory.
--

require "send_response"

--- Removes old temporary files.
-- @return true on success, false on failure.
local function clean_temp_dir()
    server.log("clean_temp_dir starting, max_temp_file_age is " .. config.max_temp_file_age, server.loglevel.LOG_DEBUG)
    local current_time = server.systime()
    local temp_dir = config.imgroot .. '/tmp'
    local success = clean_dir_entries(temp_dir, current_time)
    server.log("clean_temp_dir done", server.loglevel.LOG_DEBUG)
    return success
end

--- Recursively removes old files in a directory and its subdirectories.
-- @param dir_path the directory path.
-- @param current_time the current time in milliseconds since the epoch.
-- @return true on success, false on failure.
function clean_dir_entries(dir_path, current_time)
    local success, entries = server.fs.readdir(dir_path)

    if not success then
        server.log(entries, server.loglevel.LOG_ERR)
        return false
    end

    for _, entry in pairs(entries) do
        if entry == "." or entry == ".." then
            -- This should never happen.
            server.log("clean_temp_dir: got " .. entry .. " in " .. dir_path, server.loglevel.LOG_ERR)
            return false
        end

        local entry_path = dir_path .. "/" .. entry
        local entry_type
        success, entry_type = server.fs.ftype(entry_path)

        if not success then
            server.log(entry_type, server.loglevel.LOG_ERR)
            return false
        end

        if entry_type == "FILE" then
            success = maybe_delete_temp_file(entry_path, current_time)

            if not success then
                return false
            end
        elseif entry_type == "DIRECTORY" then
            success = clean_dir_entries(entry_path, current_time)

            if not success then
                return false
            end
        else
            server.log("clean_temp_dir: " .. entry_path .. " has type " .. entry_type, server.loglevel.LOG_WARNING)
        end
    end

    return true
end

--- Deletes a file if its last modification date is too far in the past.
-- @param file_path the file's path.
-- @param current_time the current time in milliseconds since the epoch.
-- @return true on success, false on failure.
function maybe_delete_temp_file(file_path, current_time)
    local success, last_mod_time = server.fs.modtime(file_path)

    if not success then
        -- If we couldn't get the file's last modification date, maybe it has already been deleted.
        server.log(last_mod_time, server.loglevel.LOG_WARNING)
        return true
    end

    local file_age = current_time - last_mod_time

    if file_age > config.max_temp_file_age then
        server.log("clean_temp_dir: removing " .. file_path, server.loglevel.LOG_DEBUG)
        local error_msg
        success, error_msg = server.fs.unlink(file_path)

        if not success then
            -- If we couldn't delete the file, maybe it has already been deleted.
            server.log(error_msg, server.loglevel.LOG_WARNING)
        end
    end

    return true
end

local _, auth = server.requireAuth()

local clean_temp_dir_user = os.getenv("CLEAN_TMP_DIR_USER")
local clean_temp_dir_pw = os.getenv("CLEAN_TMP_DIR_PW")

if auth.username == clean_temp_dir_user and auth.password == clean_temp_dir_pw then
    clean_temp_dir()
else
    server.log("clean_temp_dir.lua: failed to authenticate user", server.loglevel.LOG_DEBUG)
    send_error(401, "Failed to authenticate user. Wrong username or password.")
end
