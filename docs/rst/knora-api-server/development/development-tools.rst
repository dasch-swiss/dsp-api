.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

   This file is part of Knora.

   Knora is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Knora is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public
   License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

.. _development-tools:

Development Tools
=================

TODO: complete this file.

Local Development
-----------------

- Using GraphDb for development and how to initializing the 'knora-test-unit' repository
- Using Fuseki for development
- Using embedded JenaTDB
- Using docker for all of the above



Continuous Integration
----------------------

For continuous integration testing, we use Travis-CI. Every commit pushed to the git repository or every
pull request, triggers the build. Additionaly, in Github there is a litle checkmark beside every commit, signaling the
status of the build (successful, unsucessful, ongoing).

The build that is executed on Travis-CI is defined in ``.travis.yml`` situated in the root folder of the project, and
looks like this:

.. code-block:: yaml
    :linenos:
    
    sudo: required
    git:
        depth: 5
    language: scala
    scala:
    - 2.11.7
    jdk:
    - oraclejdk8
    cache:
      directories:
        - $HOME/.ivy2
    script:
    - cd webapi/_fuseki/ && ./fuseki-server &
    - sbt 'project webapi' 'test'
    notifications:
      slack:
        secure: AJZARDC7P6bwjFwk6gpe+p2ozLj+bH3h83PapfCTL0xi7frHd4y6/jXOs9ac+m7ia5FlnzgBxrf0lmaE+IkqlRzxo5dPNYkDIbMC3nrf48kS+uQjf87X1Pn6bDVBLL56L1xIeaEXAqLLWNZ8m1UQ3ykVHgUbUbimjm43eCMpUiretgOqQgreZuLGVxPDU4KrGYZ93FvT2Nzp1Iagld0KXJ1up/uKlpSZAIpJPhgWYIhSGwj9hYG50iENvtsOX/zTe2hjhKWaPmVxHWo8qNyyHfX/+3ODQhvKu3LQsFXbW8WQ1r86EUDrGWeT6mlCYbjR1Wk/7wEKvGts/7vnTNJ8H2xDG9ADc4zIzIpnz0+gndIXuguxuMZEdm1H9okcDqraa4OV01bobr43RVC4hTZCiEBt7wCd/c+C1lJahAQUQoKsbmp5idKrjUyESJ0ZbU6hgkKeOvEvUqv0msYJGWW/C5BlUlro08AgZ9h6nOfu8jJQ49x9QbSWLjTVDg8CEq3w8FDATGA6FKdDqgmsi/3ROjgdewPgyxE3XZ2UpAbAdMPeuGvFoU91X8gScm6ys6abLy0vdCL3LyqmBu/Vzdg1RzU7oqUqSbR5LSMh8pwAwy26j6Awp+pDEzBtyL59yN6r6wgxmbJ5KT+XJReEH6Ao0C6ay23E8T/3YmI3qbjX8xE=

I basically means:

 - use the virtual machine based environment (line 1)
 - checkout git with a shorter history (lines 2-3)
 - add scala libraries (lines 4-6)
 - add oracle jdk version 8 (lines 7-8)
 - cache some directories between builds to make it faster (line 9-11)
 - start fuseki and afterwards start all tests (lines 12-14)
 - send notification to our slack channel (lines 15-17)