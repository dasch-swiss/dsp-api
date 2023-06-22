-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--- Utility functions for working with strings.

-- Break a string up at occurrences of the first single character.
-- In Lua, there is no built-in function for splitting a string at a specific character.
-- http://lua-users.org/wiki/SplitJoin
-- @param str the string to split.
-- @param separator the separator character.
-- @return a table containing:
--- * the split string or
--- * the original string if the separator was not found.
function str_splitString(str, separator)
    local result = {}
    local pattern = string.format("([^%s]+)", separator)

    for match in str:gmatch(pattern) do
        table.insert(result, match)
    end

    return result
end


--- Checks if a string starts with a specific prefix.
-- In Lua, there is no built-in function for checking if a string starts with a specific prefix.
-- This function implements this functionality.
-- @param str the string to check.
-- @param prefix the prefix to check for.
-- @return true if the string starts with the prefix, false otherwise.
function str_starts_with(str, prefix)
    if str == nil or prefix == nil then
        return false
    else
        return string.sub(str, 1, string.len(prefix)) == prefix
    end
end

--- Strips a prefix from a string, if it starts with that prefix.
-- @param str the string to strip the prefix from.
-- @param prefix the prefix to strip.
-- @return the string with the prefix stripped, or the original string if it doesn't start with the prefix.
function str_strip_prefix(str, prefix)
    if str_starts_with(str, prefix) then
        return string.sub(str, string.len(prefix) + 1)
    else
        return str
    end
end

function str_trim(str)
    local _, i = string.find(str, "^%s*") -- Find the starting whitespace
    local j, _ = string.find(str, "%s*$") -- Find the trailing whitespace

    if i == nil or j == nil then
        return str -- No whitespace found at either end, return the original string
    else
        return string.sub(str, i + 1, j - 1) -- Return the trimmed string
    end
end

--- Transforms a table into a string.
-- @param tbl the table to transform.
-- @return a string representation of the table.
function tableToString(tbl)
    local str = "{\n"
    local isFirst = true

    for key, value in pairs(tbl) do
        if not isFirst then
            str = str .. "\n"
        end

        if type(value) == "table" then
            str = str .. key .. " = " .. tableToString(value) .. "\n"
        else
            str = str .. key .. " = " .. tostring(value) .. "\n"
        end

        isFirst = false
    end

    str = str .. "}"

    return str
end
