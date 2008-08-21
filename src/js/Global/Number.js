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

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */

/**
 * Return the SMALLINT string representation of this Number.  When doing a search with a
 * NativeFilter against a property that is declared as a SMALLINT in Axiom, 
 * the value passed into the search must be in its SMALLINT string representation.  
 * For example, suppose the following: <br><br>
 *
 * Page's prototype.properties:<br>
 * <code>
 * count<br>
 * count.type = SMALLINT<br><br>
 * </code>
 *
 * Then, to query for Page objects with a count value of 10 in the query API, execute the
 * following: <br><br>
 * <code>
 * var num = 10;<br>
 * app.getObjects("Page",new NativeFilter("count: " + num.smallFloatValue()));<br><br>
 * </code>  
 *
 * @return {String} The SMALLINT string representation
 */
Number.prototype.smallIntValue = function() {
	return new java.text.DecimalFormat("0000").format(0 + this);
}

/**
 * Return the SMALLFLOAT string representation of this Number.  When doing a search with a 
 * NativeFilter against a property that is declared as a SMALLFLOAT in Axiom, 
 * the value passed into the search must be in its SMALLFLOAT string representation.  
 * For example, suppose the following: <br><br>
 *
 * Page's prototype.properties:<br>
 * <code>
 * percent<br>
 * percent.type = SMALLFLOAT<br><br>
 * </code>
 *
 * Then, to query for Page objects with a count value of 10 in the query API, execute the
 * following: <br><br>
 * <code>
 * var num = 10;<br>
 * app.getObjects("Page",new NativeFilter("percent: " + num.smallFloatValue()));<br><br>
 * </code>  
 *
 * @return {String} The SMALLFLOAT string representation
 */
Number.prototype.smallFloatValue = function() {
	return new java.text.DecimalFormat("0000.0000").format(0 + this);
}


// prevent any newly added properties from being enumerated
for (var i in Number)
	if(i != 'prototype')
		Number.dontEnum(i);
for (var i in Number.prototype)
   Number.prototype.dontEnum(i);
