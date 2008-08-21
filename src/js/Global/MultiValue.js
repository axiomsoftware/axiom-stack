/*
 * Axiom Stack Web Application Framework
 * Copyright (C) 2008  Axiom Software Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */

/** */
MultiValue.prototype.map = Array.prototype.map;
/** */
MultiValue.prototype.filter = Array.prototype.filter;
/** */
MultiValue.prototype.some = Array.prototype.some;
/** */
MultiValue.prototype.someforEach = Array.prototype.someforEach;
/** */
MultiValue.prototype.every  = Array.prototype.every;
/** */
MultiValue.prototype.indexOf = Array.prototype.indexOf;
/** */
MultiValue.prototype.lastIndexOf = Array.prototype.lastIndexOf;

for (var i in MultiValue.prototype)
   MultiValue.prototype.dontEnum(i);
