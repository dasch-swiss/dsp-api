-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--
-- Handling for file specific folders, e.g. a file specific path consists of
-- two folders created from the first characters of the file name
--

--------------------------------------------------------
-- Create the file specific folder from its filename if
-- it doesn't exist yet.
-- Returns the path
--------------------------------------------------------
function check_and_create_file_specific_folder(root_folder, filename)
    local first_character_of_filename = string.lower(filename:sub(1, 1))
    local second_character_of_filename = string.lower(filename:sub(2, 2))
    local third_character_of_filename = string.lower(filename:sub(3, 3))
    local fourth_character_of_filename = string.lower(filename:sub(4, 4))

    local first_subfolder = first_character_of_filename .. second_character_of_filename
    local second_subfolder = third_character_of_filename .. fourth_character_of_filename

    local file_specific_folder_level_1 = root_folder .. '/' .. first_subfolder
    local success, error_msg = check_create_dir(file_specific_folder_level_1)
    if not success then
        send_error(500, error_msg)
        return
    end

    local file_specific_folder = file_specific_folder_level_1 .. '/' .. second_subfolder
    success, error_msg = check_create_dir(file_specific_folder)
    if not success then
        send_error(500, error_msg)
        return
    end

    return file_specific_folder
end

-------------------------------------------------------------------------------
-- Gets the file specific path, created from its filename.
-- Returns the path.
-------------------------------------------------------------------------------
function get_file_specific_path(root_folder, filename)
    local first_character_of_filename = string.lower(filename:sub(1, 1))
    local second_character_of_filename = string.lower(filename:sub(2, 2))
    local third_character_of_filename = string.lower(filename:sub(3, 3))
    local fourth_character_of_filename = string.lower(filename:sub(4, 4))

    local first_subfolder = first_character_of_filename .. second_character_of_filename
    local second_subfolder = third_character_of_filename .. fourth_character_of_filename

    return root_folder .. '/' .. first_subfolder .. '/' .. second_subfolder .. '/' .. filename
end

--------------------------------------------------------
-- Gets the path to a file from folder and filename
-- Movie preview files (they contain "_m_") have an extra level of folder
--------------------------------------------------------
function get_path_from_folder_and_filename(root_folder, filename)
    local path = ''
    -- if it is the preview file of a movie, the path is the movie's folder
    if string.find(filename, "_m_") then
        local file_base_name, _ = filename:match("(.+)_m_(.+)")
        local success, error_msg = check_create_dir(root_folder .. "/" .. file_base_name)
        if not success then
            send_error(500, error_msg)
            return
        end
        path = root_folder .. "/" .. file_base_name .. "/" .. filename

        -- for all other cases
    else
        path = root_folder .. "/" .. filename
    end

    return path
end

----------------------------------------------------
-- Check if a directory exists. If not, create it
----------------------------------------------------
function check_create_dir(path)
    local success, exists = server.fs.exists(path)
    if not success then
        return success, "server.fs.exists() failed: " .. exists
    end
    if not exists then
        local error_msg
        success, error_msg = server.fs.mkdir(path, 511)
        if not success then
            return success, "server.fs.mkdir() failed: " .. error_msg
        end
    end
    return true, "OK"
end
