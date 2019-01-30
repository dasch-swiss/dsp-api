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

--- Removes old temporary files.
-- @return true on success, false on failure.
function clean_temp_dir()
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
        local success, entry_type = server.fs.ftype(entry_path)

        if not success then
            server.log(entry_type, server.loglevel.LOG_ERR)
            return false
        end

        if entry_type == "FILE" then
            local success = maybe_delete_temp_file(entry_path, current_time)

            if not success then
                return false
            end
        elseif entry_type == "DIRECTORY" then
            local success = clean_dir_entries(entry_path, current_time)

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
-- @param full_file_path the file's path.
-- @param current_time the current time in milliseconds since the epoch.
-- @return true on success, false on failure.
function maybe_delete_temp_file(file_path, current_time)
    local success, last_mod_time = server.fs.modtime(file_path)

    if not success then
        -- If we couldn't get the file's last modification date, maybe it has already been deleted.
        server.log(last_mod_time, server.loglevel.LOG_WARNING)
        return true
    end

    if success then
        local file_age = current_time - last_mod_time

        if file_age > config.max_temp_file_age then
            server.log("clean_temp_dir: removing " .. file_path, server.loglevel.LOG_DEBUG)
            local success, error_msg = server.fs.unlink(file_path)

            if not success then
                -- If we couldn't delete the file, maybe it has already been deleted.
                server.log(error_msg, server.loglevel.LOG_WARNING)
            end
        end
    end

    return true
end
