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

-------------------------------------------------------------------------------
-- String constants to be used in error messages
-------------------------------------------------------------------------------
MIMETYPES_INCONSISTENCY = "MIME type and/or file extension are inconsistent"

FILE_NOT_READABLE = "Submitted file path could not be read: "

PARAMETERS_INCORRECT = "Parameters not set correctly."
-------------------------------------------------------------------------------

-------------------------------------------------------------------------------
-- This function is called from the route when an error occurred.
-- Parameters:
--     'status' (number):  HTTP status code to returned to the client
--     'msg'    (string):  error message describing the problem that occurred
--
-- Returns:
--    an unsuccessful HTTP response containing a JSON string with the member 'message'
-------------------------------------------------------------------------------
function send_error(status, msg)
    local http_status, msg_str, success, error_msg, jsonstr

    if type(status) == "number" then
        http_status = status
    else        
        http_status = 500
    end
    
    if type(msg) == "string" then
        msg_str = msg
    else
        msg_str = "Unknown error. Please report this as a possible bug in a Sipi route."
    end

    local result

    -- If this is an internal server error, log the message, and return a generic message to the client.
    if http_status // 100 == 5 then
        server.log(msg_str, server.loglevel.LOG_ERR)

        result = {
            message = "Internal server error"
        }
    else
        result = {
            message = msg_str
        }
    end
    
    success, error_msg = server.sendHeader("Content-Type", "application/json")
    if not success then
        server.log(error_msg, server.loglevel.LOG_ERR)
        return
    end

    server.sendStatus(http_status)

    success, jsonstr = server.table_to_json(result)
    if not success then
        server.log(error_msg, server.loglevel.LOG_ERR)
        return
    end

    success, error_msg = server.print(jsonstr)
    if not success then
        server.log(error_msg, server.loglevel.LOG_ERR)
        return
    end
end
-------------------------------------------------------------------------------

-------------------------------------------------------------------------------
-- This function is called from the route when the request could
-- be handled successfully.
--
-- Parameters:
--     'result' (table):  message to be returned to the client.
--
-- Returns:
--    a JSON string that represents the data contained in the table 'result'.
-------------------------------------------------------------------------------
function send_success(result)
    local success, error_msg, jsonstr

    if type(result) == "table" then
        success, error_msg = server.sendHeader("Content-Type", "application/json")
        if not success then
            send_error(500, "server.sendHeader() failed: " .. error_msg)
        end

        success, jsonstr = server.table_to_json(result)
        if not success then
            send_error(500, "server.table_to_json() failed: " .. jsonstr)
            return
        end

        success, error_msg = server.print(jsonstr)
        if not success then
            send_error(500, "server.print() failed: " .. error_msg)
        end
    else
        send_error(500, "send_response.lua:send_success: Expected the param 'result' to be of type table, but " .. type(result) .. " given")
    end
end
