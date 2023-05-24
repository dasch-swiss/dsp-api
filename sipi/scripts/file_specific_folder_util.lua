-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--
-- Handling for file specific folders, e.g. a file specific path consists of
-- two folders created from the first characters of the file name
--

--- Finds a file in the file specific folder structure.
--- Returns the path to the file or nil if the file doesn't exist.
--- @param file_name string The name of the file.
--- @param prefix string The IIIF prefix of the file, i.e. the project's shortcode.
--- @return string|nil The path to the file or nil if the file doesn't exist.
function find_file(file_name, prefix)
    local root_folder = _root_folder(prefix)
    local file_name_and_prefix = prefix .. ' ' .. file_name

    local file_path = get_file_specific_path(root_folder, file_name)
    if _does_file_exist(file_path) then
        log("file " .. file_name_and_prefix .. " found in file specific folder: " .. file_path, server.loglevel.LOG_DEBUG)
        return file_path
    end

    -- deprecated way of storing files, still supported for backwards compatibility
    local file_path_old = root_folder .. '/' .. file_name
    if _does_file_exist(file_path_old) then
        log("file " .. file_name_and_prefix .. " found in deprecated folder: " .. file_path_old, server.loglevel.LOG_DEBUG)
        return file_path_old
    end

    return nil
end

function _does_file_exist(file_path)
    local _, exists = server.fs.exists(file_path)
    log("does the file exist? " .. file_path .. " exists " .. tostring(exists), server.loglevel.LOG_DEBUG)
    return exists
end

function _root_folder(prefix)
    if config.prefix_as_path then
        return config.imgroot .. '/' .. prefix
    else
        return config.imgroot
    end
end

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
