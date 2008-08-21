/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2005 Helma Software. All Rights Reserved.
 *
 * $RCSfile: Date.js,v $
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
 * Value of one second in milliseconds. 
 * @type Number
 */
Date.ONESECOND    = 1000;
/** 
 * Value of one minute in milliseconds. 
 * @type Number
 */
Date.ONEMINUTE    = 60 * Date.ONESECOND;
/** 
 * Value of one hour in milliseconds. 
 * @type Number
 */
Date.ONEHOUR      = 60 * Date.ONEMINUTE;
/**
 * Value of one day in milliseconds. 
 * @type Number
 */
Date.ONEDAY       = 24 * Date.ONEHOUR;
/** 
 * Value of one week in milliseconds. 
 * @type Number
 */
Date.ONEWEEK      =  7 * Date.ONEDAY;


/**
 * Format a Date to a string.
 *
 * @param {String} pattern Format pattern
 * @param {Object} [locale] Java Locale Object
 * @param {Object} [timezone] Java TimeZone Object
 * @return {String} The formatted date
 */
Date.prototype.format = function (format, locale, timezone) {
    if (!format)
        return this.toString();
    var sdf = locale ? new java.text.SimpleDateFormat(format, locale)
                     : new java.text.SimpleDateFormat(format);
    if (timezone && timezone != sdf.getTimeZone())
        sdf.setTimeZone(timezone);
    return sdf.format(this);
};

/**
 * Return the DATE string representation of this Date.  When doing a search with a
 * NativeFilter against a property that is declared as a DATE in Axiom, 
 * the value passed into the search must be in its DATE string representation (which 
 * provides day level granularity).  
 * For example, suppose the following: <br><br>
 *
 * Page's prototype.properties:<br>
 * <code>
 * date<br>
 * date.type = DATE<br><br>
 * </code>
 *
 * Then, to query for Page objects with a date value of 01/01/2008 in the query API, 
 * execute the following: <br><br>
 * <code>
 * var date = new Date("01/01/2008");<br>
 * app.getObjects("Page",new NativeFilter("date: " + date.dateValue()));<br><br>
 * </code>  
 *
 * @return {String} The DATE type string representation
 */
Date.prototype.dateValue = function() {
	var t = this.getTime() / 86400000;
	t = Packages.axiom.objectmodel.dom.LuceneDataFormatter.roundUpDouble(t);
    return new java.text.DecimalFormat("000000").format(0 + t);
};

/**
 * Return the TIME string representation of this Date.  When doing a search with a
 * NativeFilter against a property that is declared as a TIME in Axiom, 
 * the value passed into the search must be in its TIME string representation (which 
 * provides seconds level granularity).  
 * For example, suppose the following: <br><br>
 *
 * Page's prototype.properties:<br>
 * <code>
 * date<br>
 * date.type = TIME<br><br>
 * </code>
 *
 * Then, to query for Page objects with a date value of 01/01/2008 at noon in the query API, 
 * execute the following: <br><br>
 * <code>
 * var date = new Date("01/01/2008 12:00:00");<br>
 * app.getObjects("Page",new NativeFilter("date: " + date.timeValue()));<br><br>
 * </code>  
 *
 * @return {String} The TIME type string representation
 */
Date.prototype.timeValue = function() {
	var t = this.getTime() / 1000;
	t = Packages.axiom.objectmodel.dom.LuceneDataFormatter.roundUpDouble(t);
    return new java.text.DecimalFormat("00000000000").format(0 + t);
};

/**
 * Return the TIMESTAMP string representation of this Date.  When doing a search with a
 * NativeFilter against a property that is declared as a TIMESTAMP in Axiom, 
 * the value passed into the search must be in its TIMESTAMP string representation (which 
 * provides millisecond level granularity).  
 * For example, suppose the following: <br><br>
 *
 * Page's prototype.properties:<br>
 * <code>
 * date<br>
 * date.type = TIMESTAMP<br><br>
 * </code>
 *
 * Then, to query for Page objects with a date value of 01/01/2008 in the query API, 
 * execute the following: <br><br>
 * <code>
 * var date = new Date("01/01/2008");<br>
 * app.getObjects("Page",new NativeFilter("date: " + date.timestampValue()));<br><br>
 * </code>  
 *
 * @return {String} The TIMESTAMP type string representation
 */
Date.prototype.timestampValue = function() {
	var t = Packages.axiom.objectmodel.dom.LuceneDataFormatter.roundUpDouble(this.getTime());
    return new java.text.DecimalFormat("00000000000000").format(0 + t);
};


// prevent any newly added properties from being enumerated
for (var i in Date)
	if(i != 'prototype')
		Date.dontEnum(i);
for (var i in Date.prototype)
   Date.prototype.dontEnum(i);
