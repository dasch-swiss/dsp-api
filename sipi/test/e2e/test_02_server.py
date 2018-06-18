# Copyright © 2016 Lukas Rosenthaler, Andrea Bianco, Benjamin Geer,
# Ivan Subotic, Tobias Schweizer, André Kilchenmann, and André Fatton.
# This file is part of Sipi.
# Sipi is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# Sipi is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# Additional permission under GNU AGPL version 3 section 7:
# If you modify this Program, or any covered work, by linking or combining
# it with Kakadu (or a modified version of that library) or Adobe ICC Color
# Profiles (or a modified version of that library) or both, containing parts
# covered by the terms of the Kakadu Software Licence or Adobe Software Licence,
# or both, the licensors of this Program grant you additional permission
# to convey the resulting work.
# See the GNU Affero General Public License for more details.
# You should have received a copy of the GNU Affero General Public
# License along with Sipi.  If not, see <http://www.gnu.org/licenses/>.

import pytest

# Tests basic functionality of the Sipi server.

class TestServer:
    component = "The Sipi server"

    def test_sipi_starts(self, manager):
        """start"""
        assert manager.sipi_is_running()

    def test_sipi_log_output(self, manager):
        """add routes"""
        assert "Added route" in manager.get_sipi_output()

    def test_lua_functions(self, manager):
        """call C++ functions from Lua scripts"""
        manager.expect_status_code("/test_functions", 200)

    def test_lua_scripts(self, manager):
        """call Lua functions for mediatype handling"""
        manager.expect_status_code("/test_mediatype", 200)

    def test_knora_session_parsing(self, manager):
        """call Lua function that gets the Knora session id from the cookie header sent to Sipi"""
        manager.expect_status_code("/test_knora_session_cookie", 200)


    def test_restrict(self, manager):
        """return a restricted image in a smaller size"""
        image_info = manager.get_image_info("/knora/RestrictLeaves.jpg/full/full/0/default.jpg")
        page_geometry = [line.strip().split()[-1] for line in image_info.splitlines() if line.strip().startswith("Page geometry:")][0]
        assert page_geometry == "128x128+0+0"

    def test_deny(self, manager):
        """return 401 Unauthorized if the user does not have permission to see the image"""
        manager.expect_status_code("/knora/DenyLeaves.jpg/full/full/0/default.jpg", 401)