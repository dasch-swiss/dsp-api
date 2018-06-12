--
-- Copyright © 2016 Lukas Rosenthaler, Andrea Bianco, Benjamin Geer,
-- Ivan Subotic, Tobias Schweizer, André Kilchenmann, and André Fatton.
-- This file is part of Sipi.
-- Sipi is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published
-- by the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
-- Sipi is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
-- Additional permission under GNU AGPL version 3 section 7:
-- If you modify this Program, or any covered work, by linking or combining
-- it with Kakadu (or a modified version of that library) or Adobe ICC Color
-- Profiles (or a modified version of that library) or both, containing parts
-- covered by the terms of the Kakadu Software Licence or Adobe Software Licence,
-- or both, the licensors of this Program grant you additional permission
-- to convey the resulting work.
-- You should have received a copy of the GNU Affero General Public
-- License along with Sipi.  If not, see <http://www.gnu.org/licenses/>.
--
--
-- Configuration file for use with nginx to simulate Knora.
--
sipi = {
    --
    -- The user under which the Sipi server should run. Use this only if Sipi should setuid to a particular user after
    -- starting. Otherwise, leave this commented out. If this setting is used, Sipi must be started as root.
    --
    -- userid = '_www',

    --
    -- port number the server is listening to
    --
    port = 1024,

    --
    -- Number of seconds a connection (socket) remains open
    --
    keep_alive = 5,

    --
    -- indicates the path to the root of the image directory. Depending on the settings of the variable
    -- "prefix_as_path" the images are search at <imgroot>/<prefix>/<imageid> (prefix_as_path = TRUE)
    -- or <imgroot>/<imageid> (prefix_as_path = FALSE). Please note that "prefix" and "imageid" are
    -- expected to be urlencoded. Both will be decoded. That is, "/" will be recoignized and expanded
    -- in the final path the image file!
    --
    imgroot = './test/_test_data/images', -- directory for Knora Sipi integration testing

    --
    -- If FALSE, the prefix is not used to build the path to the image files
    --
    prefix_as_path = true,

    --
    -- Lua script which is executed on initialization of the Lua interpreter
    --
    initscript = './config/sipi.init-knora.lua',

    --
    -- path to the caching directory
    --
    cachedir = './cache',

    --
    -- maxcimal size of the cache
    --
    cachesize = '100M',

    --
    -- if the cache becomes full, the given percentage of file space is marked for reuase
    --
    cache_hysteresis = 0.1,

    --
    -- Path to the directory where the scripts for the routes defined below are to be found
    --
    scriptdir = './scripts',

    ---
    --- Size of the thumbnails
    ---
    thumb_size = '!128,128',

    --
    -- Path to the temporary directory
    --
    tmpdir = '/tmp',

    --
    -- Path to Knora Application
    --
    knora_path = 'localhost',

    --
    -- Port of Knora Application
    --
    knora_port = '3434',

    --
    -- If compiled with SSL support, the port the server is listening for secure connections
    --
    ssl_port = 1025,

    --
    -- If compiled with SSL support, the path to the certificate (must be .pem file)
    -- The follow commands can be used to generate a self-signed certificate
    -- # openssl genrsa -out key.pem 2048
    -- # openssl req -new -key key.pem -out csr.pem
    -- #openssl req -x509 -days 365 -key key.pem -in csr.pem -out certificate.pem
    --
    ssl_certificate = './certificate/certificate.pem',

    --
    -- If compiled with SSL support, the path to the key file (see above to create)
    --
    ssl_key = './certificate/key.pem',


    --
    -- The secret for generating JWT's (JSON Web Tokens) (42 characters)
    --
    jwt_secret = 'UP 4888, nice 4-8-4 steam engine',
    --            12345678901234567890123456789012


    --
    -- loglevel, one of "TRACE", "DEBUG", "INFO", "NOTICE", "WARN", "ERROR",
    --    "CRITICAL", "ALERT", "EMER", "OFF"
    --
    loglevel = "TRACE",
}

--
-- here we define routes that are handled by lua scripts. A route is a defined url:
-- http://<server-DNS>/<route>
-- executes the given script defined below
--
routes = {
    {
        method = 'POST',
        route = '/make_thumbnail',
        script = 'make_thumbnail.lua'
    },
    {
        method = 'POST',
        route = '/convert_from_binaries',
        script = 'convert_from_binaries.lua'
    },
    {
        method = 'POST',
        route = '/convert_from_file',
        script = 'convert_from_file.lua'
    },
    {
        method = 'POST',
        route = '/Knora_login',
        script = 'Knora_login.lua'
    },
    {
        method = 'POST',
        route = '/Knora_logout',
        script = 'Knora_logout.lua'
    },
    {
        method = 'GET',
        route = '/test_functions',
        script = 'test_functions.lua'
    },
    {
        method = 'GET',
        route = '/test_mediatype',
        script = 'test_mediatype.lua'
    },
    {
        method = 'GET',
        route = '/test_knora_session_cookie',
        script = 'test_knora_session_cookie.lua'
    }
}
