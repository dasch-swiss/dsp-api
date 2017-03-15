.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

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

.. highlightlang:: rest

.. _documentation:

Documentation Guidelines
========================

.. contents:: :local:

The Knora documentation uses `reStructuredText`_ as its markup language and is
built using `Sphinx`_.

For more details, see `The Sphinx Documentation <http://sphinx.pocoo.org/contents.html>`_
and `Quick reStructuredText <http://docutils.sourceforge.net/docs/user/rst/quickref.html>`_.

Sections
--------

Section headings are very flexible in reST. We use the following convention in
the Knora documentation based on the `Python Documentation Conventions`_:

* ``#`` (over and under) for parts
* ``*`` (over and under) for chapters
* ``=`` for sections
* ``-`` for subsections
* ``^`` for subsubsections
* ``~`` for subsubsubsections


Cross-referencing
-----------------

Sections that may be cross-referenced across the documentation should be marked
with a reference. To mark a section use ``.. _ref-name:`` before the section
heading. The section can then be linked with ``:ref:`ref-name```. These are
unique references across the entire documentation.

For example::

  .. _knora_part::

  ##########
  Knora Part
  ##########

  .. _knora-chapter:

  *************
  Knora Chapter
  *************

  This is the chapter documentation.

  .. _knora-section:

  Knora Section
  =============

  Knora Subsection
  ----------------

  Here is a reference to "knora section": :ref:`knora-section` which will have the
  name "Knora Section".

Build the documentation
-----------------------

First install `Sphinx`_. See below.

For the html version of the docs::

    sbt sphinx:generateHtml

    open <project-dir>/akka-docs/target/sphinx/html/index.html

For the pdf version of the docs::

    sbt sphinx:generatePdf

    open <project-dir>/akka-docs/target/sphinx/latex/AkkaJava.pdf
    or
    open <project-dir>/akka-docs/target/sphinx/latex/AkkaScala.pdf

Installing Sphinx on OS X
-------------------------

Install `Homebrew <https://github.com/mxcl/homebrew>`_.

Install Python with Homebrew:

::

  brew install python

Homebrew will automatically add Python executable to your $PATH and pip is a part of the default Python installation with Homebrew.

More information in case of trouble: `Homebrew and Python <https://github.com/mxcl/homebrew/wiki/Homebrew-and-Python>`_.

Install sphinx:

::

  pip install sphinx

Install the `BasicTeX package <http://www.tug.org/mactex/morepackages.html>`_.

Add texlive bin to $PATH:

::

  export TEXLIVE_PATH=/usr/local/texlive/2015basic/bin/universal-darwin
  export PATH=$TEXLIVE_PATH:$PATH

Add missing tex packages:

::

  sudo tlmgr update --self
  sudo tlmgr install titlesec
  sudo tlmgr install framed
  sudo tlmgr install threeparttable
  sudo tlmgr install wrapfig
  sudo tlmgr install helvetic
  sudo tlmgr install courier
  sudo tlmgr install multirow

If you get the error ``unknown locale: UTF-8`` when generating the documentation, the solution is to define the following environment variables:

::

  export LANG=en_GB.UTF-8
  export LC_ALL=en_GB.UTF-8

.. _reStructuredText: http://docutils.sourceforge.net/rst.html
.. _Sphinx: http://sphinx.pocoo.org
.. _Python Documentation Conventions: https://docs.python.org/devguide/documenting.html
