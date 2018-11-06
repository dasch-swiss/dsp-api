--
-- Copyright © 2016 Lukas Rosenthaler, Andrea Bianco, Benjamin Geer,
-- Ivan Subotic, Tobias Schweizer, André Kilchenmann, and André Fatton.
-- This file is part of Sipi.
-- Sipi is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published
-- by the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
-- Sipi is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
-- Additional permission under GNU AGPL version 3 section 7:
-- If you modify this Program, or any covered work, by linking or combining
-- it with Kakadu (or a modified version of that library), containing parts
-- covered by the terms of the Kakadu Software Licence, the licensors of this
-- Program grant you additional permission to convey the resulting work.
-- See the GNU Affero General Public License for more details.
-- You should have received a copy of the GNU Affero General Public
-- License along with Sipi.  If not, see <http://www.gnu.org/licenses/>.

-- Removes old temporary files.
function clean_tempdir()
    server.log("clean_tempdir starting, max_temp_file_age is " .. config.max_temp_file_age, server.loglevel.LOG_DEBUG)

    local tempdir = config.imgroot .. '/tmp'
    local current_time = server.systime()

    local success, filenames = server.fs.readdir(tempdir)

    if not success then
        server.log(filenames, server.loglevel.LOG_ERR)
        return false
    end

    for _, filename in pairs(filenames) do
        local full_file_path = tempdir .. "/" .. filename
        local success, filetype = server.fs.ftype(full_file_path)

        if success and filetype == "FILE" then
            -- server.log("clean_tempdir: checking file " .. full_file_path, server.loglevel.LOG_DEBUG)
            local success, modtime = server.fs.modtime(full_file_path)

            if success then
                local file_age = current_time - modtime

                if file_age > config.max_temp_file_age then
                    server.log("clean_tempdir: removing " .. full_file_path, server.loglevel.LOG_DEBUG)
                    local success, errormsg = server.fs.unlink(full_file_path)

                    if not success then
                        -- If we couldn't delete the file, maybe it has already been deleted.
                        server.log(errormsg, server.loglevel.LOG_NOTICE)
                    end
                end
            else
                -- If we couldn't get the file's last modification date, maybe it has already been deleted.
                server.log(modtime, server.loglevel.LOG_NOTICE)
            end
        end
    end

    server.log("clean_tempdir done", server.loglevel.LOG_DEBUG)
    return true
end
