-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

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

    -- If this is an internal server error, log the message.
    if http_status // 100 == 5 then
        server.log(msg_str, server.loglevel.LOG_ERR)
    end

    local result
    result = {
        message = msg_str
    }
    
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
