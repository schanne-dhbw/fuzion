# This file is part of the Fuzion language implementation.
#
# The Fuzion language implementation is free software: you can redistribute it
# and/or modify it under the terms of the GNU General Public License as published
# by the Free Software Foundation, version 3 of the License.
#
# The Fuzion language implementation is distributed in the hope that it will be
# useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
# License for more details.
#
# You should have received a copy of the GNU General Public License along with The
# Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.


# -----------------------------------------------------------------------
#
#  Tokiwa GmbH, Berlin
#
#  Source code of Fuzion test Makefile
#
#  Author: Fridtjof Siebert (siebert@tokiwa.eu)
#
# -----------------------------------------------------------------------

JAVA_STACK_SIZE = 4m # reqquired since tail recursion is not optimized yet
JAVA_OPTS = -Xss$(JAVA_STACK_SIZE)

all:
	$(FUZION) looptest 2>err.txt
