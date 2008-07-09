package axiom.objectmodel.dom;

import java.io.File;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

public class LuceneDataFormatter {
    
    private MetadataRetriever metaData;
    private DecimalFormat decimalFormatter = null;
    private DecimalFormat longFormatter = null;
    private DecimalFormat timestampFormatter = null;
    private DecimalFormat smallLongFormatter = null;
    private DecimalFormat smallDecimalFormatter = null;
    private DecimalFormat dateFormatter = null;
    private DecimalFormat timeFormatter = null;
        
    public static final String INT = "int";
    public static final String FLOAT = "float";
    public static final String DATE = "date";
    public static final String FORMAT = "format";
    
    public static final double DATE_DIVIDER = (1000d * 60d * 60d * 24d);
    public static final long DATE_MULTIPLIER = (1000L * 60L * 60L * 24L);
        
    public LuceneDataFormatter(File appHome) {
        metaData = new MetadataRetriever(appHome);
        
        HashMap typeAttrs = (HashMap) metaData.get(FLOAT);
        String format = null;
        if (typeAttrs != null) {
            format = typeAttrs.get(FORMAT).toString();
        }
        if (format != null) {
            decimalFormatter = new DecimalFormat(format);
        } else {
            decimalFormatter = new DecimalFormat("0000000000.0000");
        }
        
        typeAttrs = (HashMap) metaData.get(INT);
        format = null;
        if (typeAttrs != null) {
            format = typeAttrs.get(FORMAT).toString();
        }
        if (format != null) {
            longFormatter = new DecimalFormat(format);
        } else {
            longFormatter = new DecimalFormat("0000000000");
        }
        
        smallLongFormatter = new DecimalFormat("0000");
        smallDecimalFormatter = new DecimalFormat("0000.0000");
        dateFormatter = new DecimalFormat("000000");
        timeFormatter = new DecimalFormat("00000000000");
        timestampFormatter = new DecimalFormat("00000000000000");
    }
    
    public String formatTimestamp(Date date) {
        return timestampFormatter.format(date.getTime());
    }
    
    public String formatTime(Date date) {
        double l = roundUpDouble(date.getTime() / 1000d);
        return timeFormatter.format((long) l);
    }
    
    public String formatDate(Date date) {
        double l = roundUpDouble(date.getTime() / DATE_DIVIDER);
        return dateFormatter.format((long) l);
    }
    
    public Date strToTimestamp(String strDate) throws ParseException {
        return new Date(Long.parseLong(strDate));
    } 
    
    public Date strToTime(String strDate) throws ParseException {
        return new Date(Long.parseLong(strDate) * 1000L);
    }
    
    public Date strToDate(String strDate) throws ParseException {
        return new Date(Long.parseLong(strDate) * DATE_MULTIPLIER);
    }
    
    public String formatFloat(double fl) {
        return decimalFormatter.format(fl);
    }
    
    public String formatInt(long l) {
        return longFormatter.format(l);
    }
    
    public String formatSmallFloat(double fl) {
        return smallDecimalFormatter.format(fl);
    }
    
    public String formatSmallInt(long l) {
        return smallLongFormatter.format(l);
    }

    public static double roundUpDouble(double dub){
        double fraction = dub % 1;
        double retVal = dub - fraction;
        if (fraction > 0) {
            retVal++;
        }
        return retVal;
    }

}