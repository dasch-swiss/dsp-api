-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "env"
require "send_response"
require "strings"
require "util"

--- Extracts a JSON web token from the HTTP request: header ('Authorization' and 'Cookie') or query param 'token'.
-- Decodes token and validates claims exp, aud, iss.
-- Sends an HTTP error if the token is missing or invalid.
-- @return a string, the raw jwt token.
--         or sends an error in the following cases:
--             * 400 if the token is missing
--             * 401 if the token is invalid
function auth_get_jwt()
    server.log("auth_get_jwt: Looking for jwt token in header, params or cookie", server.loglevel.LOG_DEBUG)
    local token_str = _get_jwt_token_from_header_params_or_cookie()

    if token_str == nil then
        send_error(400, "Token missing")
        return nil
    end

    -- decode token
    server.log("auth_get_jwt: Found jwt token: " .. token_str, server.loglevel.LOG_DEBUG)
    local success, token = server.decode_jwt(token_str)
    if not success then
        send_error(401, "Invalid token, unable to decode jwt.")
        return nil
    end

    -- check expiration date of token
    local expiration_date = token["exp"]
    if expiration_date == nil then
        send_error(401, "Invalid 'exp' (expiration date) in token, token has no expiry date.")
        return nil
    end
    local systime = server.systime()
    if (expiration_date <= systime) then
        send_error(401, "Invalid 'exp' (expiration date) in token, token is expired.")
        return nil
    end

    -- check audience of token
    local audience = token["aud"]
    local expected_audience = "Sipi"
    if audience == nil or not table.contains(audience, expected_audience) then
        send_error(401, "Invalid 'aud' (audience) in token, expected: " .. expected_audience .. ".")
        return nil
    end

    -- check issuer of token
    local token_issuer = env_dsp_api_host_port()
    if token["iss"] ~= token_issuer then
        send_error(401, "Invalid 'iss' (issuer) in token, expected: " .. token_issuer .. ".")
        return nil
    end

    return token_str
end

--- Extract the JSON web token from the HTTP request header or query parameter.
function _get_jwt_token_from_header_params_or_cookie()
    local from_header = _get_jwt_token_from_auth_header()
    if from_header ~= nil then
        return from_header
    end

    local from_query_param = _get_jwt_token_from_query_param()
    if from_query_param ~= nil then
        return from_query_param
    end

    local from_cookie = _get_jwt_token_from_cookie()
    if from_cookie ~= nil then
        return from_cookie
    end

    server.log("_get_jwt_token_from_header_params_or_cookie: No token found", server.loglevel.LOG_DEBUG)
    return nil
end

--- Extract the "Bearer" JSON web token from the HTTP request "Authorization" header.
-- @return the token
--         or nil if the header is missing
--         or nil if the header value is not a "Bearer" token.
function _get_jwt_token_from_auth_header()
    server.log("_get_jwt_token_from_auth_header: checking for jwt token in header", server.loglevel.LOG_DEBUG)
    local auth_header = _get_auth_header()
    local bearer_prefix = "Bearer "
    if str_starts_with(auth_header, bearer_prefix) then
        server.log("_get_jwt_token_from_auth_header: jwt token found in header", server.loglevel.LOG_DEBUG)
        return str_strip_prefix(auth_header, bearer_prefix)
    else
        return nil
    end
end

--- Extract the "Authorization" header from the HTTP request.
-- @return the header value or nil if the header is missing.
function _get_auth_header()
    return _get_header("authorization")
end

--- Extract the "Cookie" header from the HTTP request.
-- @return the header value or nil if the header is missing.
function _get_cookie_header()
    return _get_header("cookie")
end

--- Extract a header from the HTTP request.
-- @param key the header key
-- @return the header value or nil if the header is missing.
function _get_header(key)
    if server.header ~= nil and server.header[key] ~= nil then
        return server.header[key]
    else
        return nil
    end
end

--- Extract the JSON web token from the HTTP request query parameter "token".
-- @return the header value or nil if the header is missing.
function _get_jwt_token_from_query_param()
    server.log("_get_jwt_token_from_query_param: checking for jwt token in query params", server.loglevel.LOG_DEBUG)
    if server.request ~= nil then
        local jwt_token = server.request["token"]
        if (jwt_token ~= nil) then
            server.log("_get_jwt_token_from_query_param: jwt token found in query params", server.loglevel.LOG_DEBUG)
        else
            server.log("_get_jwt_token_from_query_param: jwt token not found in query param token", server.loglevel.LOG_DEBUG)
        end
        return jwt_token
    else
        server.log("_get_jwt_token_from_query_param: jwt token not found in query params", server.loglevel.LOG_DEBUG)
        return nil
    end
end

--- Extracts the jwt token from the cookie.
-- @return jwt token or nil if the cookie is missing or invalid.
function _get_jwt_token_from_cookie()
    server.log("_get_jwt_token_from_cookie: checking for jwt token in cookie header", server.loglevel.LOG_DEBUG)
    local cookie_name = env_knora_authentication_cookie_name()
    local cookie_header_value = _get_cookie_header()
    if cookie_header_value == nil then
        server.log("_get_jwt_token_from_cookie: no header found for: Cookie. headers " .. tableToString(server.header), server.loglevel.LOG_DEBUG)
        return nil
    end
    if (type(cookie_header_value) ~= "string") then
        server.log("parameter 'cookie' for function 'get_session_id' is expected to be a string", server.loglevel.LOG_ERR)
        return nil
    end
    local jwt_token = str_strip_prefix(cookie_header_value, cookie_name .. "=")
    server.log("_get_jwt_token_from_cookie: jwt token found in cookie", server.loglevel.LOG_DEBUG)
    return jwt_token
end

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