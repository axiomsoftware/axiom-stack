/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2006 Helma Software. All Rights Reserved.
 *
 * $RCSfile: Array.js,v $
 * $Author: czv $
 * $Revision: 1.2 $
 * $Date: 2006/04/24 07:02:17 $
 */


/**
 * Retrieve the union set of a bunch of arrays
 *
 * @param {Array} (array1,array2,...) the arrays to unify
 * @return {Array} the union set
 */
Array.union = function() {
   var result = [];
   var map = {};
   for (var i=0; i<arguments.length; i+=1) {
      for (var n in arguments[i]) {
         var item = arguments[i][n];
         if (!map[item]) {
            result.push(item);
            map[item] = true;
         }
      }
   }
   return result;
};


/**
 * Retrieve the intersection set of a bunch of arrays
 *
 * @param {Array} (array1,array2,...) the arrays to intersect
 * @return {Array} the intersection set
 */
Array.intersection = function() {
   var all = Array.union.apply(this, arguments);
   var result = [];
   for (var n in all) {
      var chksum = 0;
      var item = all[n];
      for (var i=0; i<arguments.length; i+=1) {
         if (arguments[i].contains(item))
            chksum += 1;
         else
            break;
      }
      if (chksum == arguments.length)
         result.push(item);
   }
   return result;
};


/**
 * Return the first index position of the specified value
 * contained in this array
 *
 * @param {String|Object} val the String or Object to check
 * @return {Number} the index of the value, -1 if not found
 */
Array.prototype.indexOf = function(val) {
   var i = -1;
   while (i++ < this.length -1) {
      if (this[i] == val)
         return i;
   }
   return -1;
};


/**
 * Return the last index position of the specified value
 * contained in this array
 *
 * @param {String|Object} val the String or Object to check
 * @return {Number} the last index of the value, -1 if not found
 */
Array.prototype.lastIndexOf = function(val) {
   var i = 1;
   while (this.length - i++ >= 0) {
      if (this[i] == val)
         return i;
   }
   return -1;
};


/**
 * Check if this array contains the specified value
 *
 * @param {String|Object} val the String or Object to check
 * @return {Boolean} whether the value is contained in this array or not
 */
Array.prototype.contains = function(val) {
   if (this.indexOf(val) > -1)
      return true;
   return false;
};


// prevent any newly added properties from being enumerated
for (var i in Array)
	if(i != 'prototype')
		Array.dontEnum(i);
for (var i in Array.prototype)
	Array.prototype.dontEnum(i);
