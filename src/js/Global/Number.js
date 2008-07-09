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
 * $RCSfile: Number.js,v $
 * $Author: czv $
 * $Revision: 1.2 $
 * $Date: 2006/04/24 07:02:17 $
 */


Number.prototype.smallIntValue = function() {
	return new java.text.DecimalFormat("0000").format(0 + this);
}

Number.prototype.smallFloatValue = function() {
	return new java.text.DecimalFormat("0000.0000").format(0 + this);
}


// prevent any newly added properties from being enumerated
for (var i in Number)
	if(i != 'prototype')
		Number.dontEnum(i);
for (var i in Number.prototype)
   Number.prototype.dontEnum(i);
