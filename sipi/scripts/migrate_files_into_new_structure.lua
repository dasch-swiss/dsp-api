-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "send_response"

--- Migrates files from the old structure to the new one.
function migrate_files()
    local img_root_dir = config.imgroot
    local success, folders = server.fs.readdir(img_root_dir)
    if not success then
        send_error(500, "migrate_files - server.fs.readdir() failed: " .. folders)
        return
    end

    for _, folder in pairs(folders) do
        -- if length is 4, it's assumed to be a project folder
        if string.len(folder) == 4 then
            server.log("migrate_files - start migrating project folder: " .. folder, server.loglevel.LOG_DEBUG)
            
            local project_folder_path = img_root_dir .. "/" .. folder
            local items
            success, items = server.fs.readdir(project_folder_path)
            if not success then
                send_error(500, "migrate_files - server.fs.readdir() failed: " .. items)
                return
            end

            -- check all items of the project folder
            for _, item in pairs(items) do
                local source_path = project_folder_path .. "/" .. item
                local destination_root = create_destination_folder(project_folder_path, item)
                local destination_path = destination_root .. "/" .. item

                success, entry_type = server.fs.ftype(source_path)
                if not success then
                    server.log(entry_type, server.loglevel.LOG_ERR)
                    return
                end

                if entry_type == "DIRECTORY" then
                    if string.len(item) ~= 2 then
                        migrate_folder(source_path, destination_path)
                    end
                elseif entry_type == "FILE" then
                    migrate_file(source_path, destination_path)
                else 
                    server.log("migrate_files - invalid entry_type " .. tostring(entry_type) .. " for " .. source_path, server.loglevel.LOG_ERR)
                end
            end
            server.log("migrate_files - end migrating project folder: " .. folder, server.loglevel.LOG_DEBUG)
        end
    end
end


function migrate_originals()
    local originals_dir = config.imgroot .. "/" .. "originals"

    local folders
    success, folders = server.fs.readdir(originals_dir)
    if not success then
        send_error(500, "migrate_originals - server.fs.readdir() failed: " .. folders)
        return
    end

    for _, folder in pairs(folders) do
        -- if length is 4, it's assumed to be a project folder
        if string.len(folder) == 4 then
            server.log("migrate_originals - start migrating 'originals' folder: " .. folder, server.loglevel.LOG_DEBUG)
            
            local orig_project_folder_path = originals_dir .. "/" .. folder
            local items
            success, items = server.fs.readdir(orig_project_folder_path)
            if not success then
                send_error(500, "migrate_originals - server.fs.readdir() failed: " .. items)
                return
            end

            -- move files from originals to destination project folder
            for _, item in pairs(items) do
                local source_path = orig_project_folder_path .. "/" .. item
                local destination_project_folder_path = config.imgroot .. "/" .. folder
                local destination_root = create_destination_folder(destination_project_folder_path, item)
                local destination_path = destination_root .. "/" .. item

                migrate_file(source_path, destination_path)
            end
            server.log("migrate_originals - end migrating 'originals' folder: " .. folder, server.loglevel.LOG_DEBUG)
        end
    end

    check_and_delete_folder(originals_dir)
end

-----------------------------------------------------------
-- Recreates all sidecar files after the migration
-----------------------------------------------------------
function recreate_sidecar_files()

end

-----------------------------------------------------------
-- Deletes the folder if it is empty
-----------------------------------------------------------
function check_and_delete_folder(path)
    local folders
    success, folders = server.fs.readdir(path)
    if not success then
        send_error(500, "check_and_delete_folder - server.fs.readdir() failed: " .. folders)
        return
    end
    
    for _, folder in pairs(folders) do
        local folder_to_delete = path .. "/" .. folder
        local items
        success, items = server.fs.readdir(folder_to_delete)
        if not success then
            send_error(500, "check_and_delete_folder - server.fs.readdir() failed: " .. items)
            return
        end

        if #items == 0 then
            -- delete the folder if it is empty
            os.remove(folder_to_delete)
            server.log("check_and_delete_folder - deleted " .. tostring(folder_to_delete), server.loglevel.LOG_DEBUG)
        end
    end
end


-----------------------------------------------------------
-- Move a file from source to destination
-----------------------------------------------------------
function migrate_file(source_path, destination_path)
    local success, error_msg = server.fs.moveFile(source_path, destination_path)
    if not success then
        send_error(500, "server.fs.moveFile() failed: " .. error_msg)
        return
    end
    server.log("migrate_file - migrated file from " .. source_path .. " to " .. destination_path, server.loglevel.LOG_DEBUG)
end


-----------------------------------------------------------
-- Move a folder from source to destination
-----------------------------------------------------------
function migrate_folder(source_path, destination_path)
    local success, error_msg = os.rename(source_path, destination_path)
    if not success then
        send_error(500, "os.rename() failed: " .. error_msg)
        return
    end
    server.log("migrate_folder - migrated folder from " .. source_path .. " to " .. destination_path, server.loglevel.LOG_DEBUG)
end


-----------------------------------------------------------
-- Check if a directory exists. If not, create it
-----------------------------------------------------------
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


-----------------------------------------------------------
-- Create the file specific folder from its filename if it
-- doesn't exist yet.
-- Returns the path of the destination folder
-----------------------------------------------------------
function create_destination_folder(root_folder, filename)
    local first_character_of_filename = filename:sub(1, 1)
    local second_character_of_filename = filename:sub(2, 2)
    local third_character_of_filename = filename:sub(3, 3)
    local fourth_character_of_filename = filename:sub(4, 4)

    local first_subfolder = first_character_of_filename .. second_character_of_filename
    local second_subfolder = third_character_of_filename .. fourth_character_of_filename

    local subfolder_level_1 = root_folder .. '/' .. first_subfolder
    success, error_msg = check_create_dir(subfolder_level_1)
    if not success then
        send_error(500, error_msg)
        return
    end

    local destination_folder = subfolder_level_1 .. '/' .. second_subfolder
    success, error_msg = check_create_dir(destination_folder)
    if not success then
        send_error(500, error_msg)
        return
    end

    return destination_folder
end

migrate_files()
migrate_originals()
recreate_sidecar_files()
