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
 * Retrieve the union set of a bunch of arrays.
 *
 * @param {Array} (array1,array2,...) The arrays to unify
 * @return {Array} The union set
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
 * Retrieve the intersection set of a bunch of arrays.
 *
 * @param {Array} (array1,array2,...) The arrays to intersect
 * @return {Array} The intersection set
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
 * contained in this array.
 *
 * @param {String|Object} val The String or Object to check
 * @return {Number} The index of the value, -1 if not found
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
 * Insert some object within the array at some index. If index is null, the element is
 * pushed to the end of the array.
 *
 * @param {Object} val Object to insert
 * @param {Number} index The place within the array to insert val
 * @throws Throws an error when you try to insert a null value into the array
 */
Array.prototype.insert = function(val, index) {
    if (!val) {
	throw "Cannot insert a null value into the array. val is null.";
    }

    if (!index) {
	this.push(val);
    } else {
	this.splice(index, 0, val);
    }
};


/**
 * Return the last index position of the specified value
 * contained in this array.
 *
 * @param {String|Object} val The String or Object to check
 * @return {Number} The last index of the value, -1 if not found
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
 * Check if this array contains the specified value.
 *
 * @param {String|Object} val The String or Object to check
 * @return {Boolean} Whether The value is contained in this array or not
 */
Array.prototype.contains = function(val) {
   if (this.indexOf(val) > -1)
      return true;
   return false;
};


/**
 * Pass through elements and return an array with only unique elements
 */
Array.prototype.unique = function() {
    var results = [];
    if (typeof this == "string") {
	results.push(this);
    } else {
	//can't rely on for each since that will convert objects(and functions)
	for (var i = 0; i < this.length; i++) {
	    var e = this[i];
	    if (!(results.contains(e))) results.push(e);
	}
    }

    return results;
};

/**
 * Remove item at the specified index
 *
 * @param {Number|String} index Index of the element to remove from the array.
 * @return {Object} Removed object
 */
Array.prototype.remove = function(index) {
    if (!(typeof index == 'number')) {
	index = parseInt(index, 10);
    }

    return this.splice(index, 1);
};

/**
 * Remove object
 *
 * @param {Object} object Object to remove from the array
 * @return {Object} Removed object
 */
Array.prototype.removeObject = function(object) {
    var index = this.indexOf(object);
    if (index >= 0) return this.remove(index);
    return null;
};


// prevent any newly added properties from being enumerated
for (var i in Array)
	if(i != 'prototype')
		Array.dontEnum(i);
for (var i in Array.prototype)
	Array.prototype.dontEnum(i);
