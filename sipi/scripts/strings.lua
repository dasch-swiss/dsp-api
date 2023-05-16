-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

--- Utility functions for working with strings.

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

--- Transforms a table into a string.
-- @param tbl the table to transform.
-- @return a string representation of the table.
function tableToString(tbl)
    local str = "{"
    local isFirst = true

    for key, value in pairs(tbl) do
        if not isFirst then
            str = str .. ", "
        end

        if type(value) == "table" then
            str = str .. key .. "=" .. tableToString(value)
        else
            str = str .. key .. "=" .. tostring(value)
        end

        isFirst = false
    end

    str = str .. "}"

    return str
end
