/**
 *   Copyright 2007-2008 Axiom Software Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

MultiValue.prototype.map = Array.prototype.map;
MultiValue.prototype.filter = Array.prototype.filter;
MultiValue.prototype.some = Array.prototype.some;
MultiValue.prototype.someforEach = Array.prototype.someforEach;
MultiValue.prototype.every  = Array.prototype.every;
MultiValue.prototype.indexOf = Array.prototype.indexOf;
MultiValue.prototype.lastIndexOf = Array.prototype.lastIndexOf;

for (var i in MultiValue.prototype)
   MultiValue.prototype.dontEnum(i);
