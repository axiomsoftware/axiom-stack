/*
 * Axiom License Notice
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


Date.ONESECOND    = 1000;
Date.ONEMINUTE    = 60 * Date.ONESECOND;
Date.ONEHOUR      = 60 * Date.ONEMINUTE;
Date.ONEDAY       = 24 * Date.ONEHOUR;
Date.ONEWEEK      =  7 * Date.ONEDAY;


/**
 * format a Date to a string
 * @param String Format pattern
 * @param Object Java Locale Object (optional)
 * @param Object Java TimeZone Object (optional)
 * @return String formatted Date
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


Date.prototype.dateValue = function() {
	var t = this.getTime() / 86400000;
	t = Packages.axiom.objectmodel.dom.LuceneDataFormatter.roundUpDouble(t);
    return new java.text.DecimalFormat("000000").format(0 + t);
};


Date.prototype.timeValue = function() {
	var t = this.getTime() / 1000;
	t = Packages.axiom.objectmodel.dom.LuceneDataFormatter.roundUpDouble(t);
    return new java.text.DecimalFormat("00000000000").format(0 + t);
};


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
