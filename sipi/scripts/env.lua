-- * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0
basexx = require("basexx")

local DSP_API_HOST_NAME_KEY = "KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST"
local DSP_API_PORT_KEY = "KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT"

-- Returns the external hostname of the DSP-API.
function env_dsp_api_hostname()
    return _get_env(DSP_API_HOST_NAME_KEY)
end

-- Returns the external port of the DSP-API.
function env_dps_api_port()
    return _get_env(DSP_API_PORT_KEY)
end

function _get_env(key)
    local hostname = os.getenv(key)
    if hostname == nil then
        local error_msg = key .. " not set"
        send_error(500, error_msg)
        return nil
    else
        return hostname
    end
end

-- Returns the external hostname:port of the DSP-API.
function env_dsp_api_host_port()
    return env_dsp_api_hostname() .. ':' .. env_dps_api_port()
end

-- Returns the name of the cookie used for authentication.
function env_knora_authentication_cookie_name()
    local host_port_base32 = basexx.to_base32Custom(env_dsp_api_host_port())
    return string.lower("KnoraAuthentication" .. host_port_base32)
end
