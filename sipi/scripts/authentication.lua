-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "env"
require "log_util"
require "send_response"
require "strings"
require "util"

local NO_TOKEN_FOUND_ERROR = "No token found"

--- Extracts a JSON web token (JWT) from the HTTP request: header ('Authorization' and 'Cookie') or query param 'token'.
-- If present decodes token and validates claims exp, aud, iss.
-- Sends an HTTP error if the token is invalid.
-- @return The raw jwt string, if the token is present and valid.
--         'nil, error' if the token is missing or invalid, 'error' representing the reason as a string.
--         Sends a 401 server error if the token is invalid.
function auth_get_jwt_raw()
    local token, error = _token()
    return token.raw, error
end

function _token()
    local nil_token = { raw = nil, decoded = nil }
    local jwt_raw = _get_jwt_string_from_header_params_or_cookie()
    if jwt_raw == nil then
        return nil_token, NO_TOKEN_FOUND_ERROR
    else
        local decoded, error = _decode_jwt(jwt_raw)
        if decoded == nil then
            return nil_token, error
        else
            return { raw = jwt_raw, decoded = decoded }
        end
    end
end


--- Extracts a JSON web token (JWT) from the HTTP request: header ('Authorization' and 'Cookie') or query param 'token'.
-- If present decodes token and validates claims exp, aud, iss.
-- Sends an HTTP error if the token is invalid.
-- @return The decoded jwt string, if the token is present and valid.
--         'nil, error' if the token is missing or invalid, 'error' representing the reason as a string..
--         Sends a 401 server error if the token is invalid.
function auth_get_jwt_decoded()
    local token, error = _token()
    return token.decoded, error
end

function _send_unauthorized_error(error_msg)
    send_error(401, error_msg)
    log("authentication - unauthorized: " .. error_msg, server.loglevel.LOG_DEBUG)
    return nil, error_msg
end

function _decode_jwt(token_str)
    -- decode token
    log("authentication: decoding jwt token", server.loglevel.LOG_DEBUG)
    local success, decoded_token = server.decode_jwt(token_str)
    if not success then
        return _send_unauthorized_error("Invalid token, unable to decode jwt.")
    end

    -- check expiration date of token
    local expiration_date = decoded_token["exp"]
    if expiration_date == nil then
        return _send_unauthorized_error("Invalid 'exp' (expiration date) in token, token has no expiry date.")
    end
    if (expiration_date <= server.systime()) then
        send_error(401, "Invalid 'exp' (expiration date) in token, token is expired.")
        return nil
    end

    -- check audience of token
    local audience = decoded_token["aud"]
    local expected_audience = "Sipi"
    if audience == nil or not table.contains(audience, expected_audience) then
        return _send_unauthorized_error("Invalid 'aud' (audience) in token, expected: " .. expected_audience .. ".")
    end

    -- check issuer of token
    local token_issuer = env_dsp_api_host_port()
    if decoded_token["iss"] ~= token_issuer then
        return _send_unauthorized_error(401, "Invalid 'iss' (issuer) in token, expected: " .. token_issuer .. ".")
    end

    log("authentication: decoded jwt token for 'sub' " .. decoded_token["sub"], server.loglevel.LOG_DEBUG)
    return decoded_token
end

--- Extract the JSON web token from the HTTP request header or query parameter.
function _get_jwt_string_from_header_params_or_cookie()
    local from_header = _get_jwt_token_from_auth_header()
    if from_header ~= nil then
        log("authentication: token found in authorization header", server.loglevel.LOG_DEBUG)
        return from_header
    end

    local from_query_param = _get_jwt_token_from_query_param()
    if from_query_param ~= nil then
        log("authentication: token found in query param", server.loglevel.LOG_DEBUG)
        return from_query_param
    end

    local from_cookie = _get_jwt_token_from_cookie()
    if from_cookie ~= nil then
        log("authentication: token found in cookie header", server.loglevel.LOG_DEBUG)
        return from_cookie
    end

    log("authentication: no token found in request", server.loglevel.LOG_DEBUG)
    return nil
end

--- Extracts a JSON web token (JWT) from the HTTP request: header ('Authorization' and 'Cookie') or query param 'token'.
-- If present decodes token and validates claims exp, aud, iss.
-- Sends an HTTP error if the token is invalid.
-- @return The decoded jwt
--         'nil, string' if the token is missing or invalid, 'string' representing the reason.
--         Sends a 401 error if the token is invalid.
function _get_jwt_token_from_auth_header()
    log("authentication: checking for jwt token in authorization header", server.loglevel.LOG_DEBUG)
    local auth_header = _get_auth_header()
    local bearer_prefix = "Bearer "
    if str_starts_with(auth_header, bearer_prefix) then
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
    log("authentication: checking for jwt token in query param token", server.loglevel.LOG_DEBUG)
    if server.request ~= nil then
        return server.request["token"]
    else
        return nil
    end
end

--- Extracts the jwt token from the cookie.
-- @return jwt token or nil if the cookie is missing or invalid.
function _get_jwt_token_from_cookie()
    log("authentication: checking for jwt token in cookie header", server.loglevel.LOG_DEBUG)
    local cookie_name = env_knora_authentication_cookie_name()
    local cookies = _get_cookie_header()
    if cookies == nil then
        log("authentication: no cookie header found", server.loglevel.LOG_DEBUG)
        return nil
    end

    cookie_name = cookie_name:lower()
    for entry in cookies:gmatch("([^,]+)") do
        local key, value = entry:match("([^=]+)=(.+)")
        if key and value then
            if key:lower() == cookie_name then
                return value
            end
        end
    end
    log("authentication: cookie header does not contain " .. cookie_name, server.loglevel.LOG_DEBUG)
    return nil
end
