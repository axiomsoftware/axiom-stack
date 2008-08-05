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
 * $RCSfile: String.js,v $
 * $Author: czv $
 * $Revision: 1.2 $
 * $Date: 2006/04/24 07:02:17 $
 */

/**
 * Calculates the md5 hash of a string
 *
 * @returns {String} md5 hash of the string
 */
String.prototype.md5 = function() {
    return Packages.axiom.util.MD5Encoder.encode(this);
};

// prevent any newly added properties from being enumerated
for (var i in String)
	if(i != 'prototype')
		String.dontEnum(i);
for (var i in String.prototype)
   String.prototype.dontEnum(i);
