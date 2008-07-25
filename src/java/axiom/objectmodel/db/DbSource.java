/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: DbSource.java,v $
 * $Author: hannes $
 * $Revision: 1.13 $
 * $Date: 2005/11/17 16:23:51 $
 */

package axiom.objectmodel.db;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Hashtable;

import axiom.util.ResourceProperties;

/**
 *  This class describes a releational data source (URL, driver, user and password).
 */
public class DbSource {
    private static ResourceProperties defaultProps = null;
    private Properties conProps;
    private String name;
    private ResourceProperties props;
    protected String url;
    private String driver;
    private String type;
    private boolean isOracle;
    private long lastRead = 0L;
    private Hashtable dbmappings = new Hashtable();
    private int dbtype = -1;
    
    public static final int UNKNOWN = -1;
    public static final int ORACLE = 1;
    public static final int SQL_SERVER = 2;
    public static final int MYSQL = 3;
    public static final int H2 = 4;

    /**
     * Creates a new DbSource object.
     *
     * @param name the db source name
     * @param props the properties
     * @throws ClassNotFoundException if the JDBC driver couldn't be loaded
     */
    public DbSource(String name, ResourceProperties props)
             throws ClassNotFoundException {
        this.name = name;
        this.props = props;
        init();
    }

    /**
     * Get a JDBC connection to the db source.
     *
     * @return a JDBC connection
     *
     * @throws ClassNotFoundException if the JDBC driver couldn't be loaded
     * @throws SQLException if the connection couldn't be created
     */
    public synchronized Connection getConnection()
            throws ClassNotFoundException, SQLException {
       return getConnection(false);
    }
    
    /**
     * Get a JDBC connection to the db source.
     *
     * @return a JDBC connection
     *
     * @throws ClassNotFoundException if the JDBC driver couldn't be loaded
     * @throws SQLException if the connection couldn't be created
     */
    public synchronized Connection getConnection(boolean autocommit)
            throws ClassNotFoundException, SQLException {
        Connection con = null;
        Transactor tx = null;
        if (Thread.currentThread() instanceof Transactor) {
            tx = (Transactor) Thread.currentThread();
            con = tx.getConnection(this);
        }

        boolean fileUpdated = props.lastModified() > lastRead;

        if (!fileUpdated && (defaultProps != null)) {
            fileUpdated = defaultProps.lastModified() > lastRead;
        }

        if ((con == null) || con.isClosed() || fileUpdated) {
            init();
            Class.forName(driver);
            con = DriverManager.getConnection(url, conProps);
            // con = DriverManager.getConnection(url, user, password);

            con.setAutoCommit(autocommit); // allowing for jdbc transactionality
            
            // System.err.println ("Created new Connection to "+url);
            if (tx != null) {
                tx.registerConnection(this, con);
            }
        }

        return con;
    }

    /**
     * Initialize the db source from the properties
     *
     * @throws ClassNotFoundException if the JDBC driver couldn't be loaded
     */
    private synchronized void init() throws ClassNotFoundException {
        lastRead = (defaultProps == null) ? props.lastModified()
                                          : Math.max(props.lastModified(),
                                                     defaultProps.lastModified());
        // get JDBC URL and driver class name
        url = props.getProperty(name + ".url");
        driver = props.getProperty(name + ".driver");
        type = props.getProperty(name + ".type", "relational");
        if (type == null || type.equalsIgnoreCase("relational")) {
	        // sanity checks
	        if (url == null) {
	            throw new NullPointerException(name+".url is not defined in db.properties");
	        }
	        if (driver == null) {
	            throw new NullPointerException(name+".driver class not defined in db.properties");
	        }
	        this.dbtype = this.determineDbType(this.driver);
	        // test if this is an Oracle driver
	        isOracle = driver.startsWith("oracle.jdbc.driver");
	        // test if driver class is available
	        Class.forName(driver);
	
	        // set up driver connection properties
	        conProps=new Properties();
	        String prop = props.getProperty(name + ".user");
	        if (prop != null) {
	            conProps.put("user", prop);
	        }
	        prop = props.getProperty(name + ".password");
	        if (prop != null) {
	            conProps.put("password", prop);
	        }
	
	        // read any remaining extra properties to be passed to the driver
	        for (Enumeration e = props.keys(); e.hasMoreElements(); ) {
	            String fullkey = (String) e.nextElement();
	
	            int dot = fullkey.indexOf('.');
	            // filter out properties that don't belong to this data source
	            if (dot < 0 || !fullkey.substring(0, dot).equalsIgnoreCase(name)) {
	                continue;
	            }
	            String key = fullkey.substring(dot+1);
	            // filter out properties we alread have
	            if ("url".equalsIgnoreCase(key) ||
	                "driver".equalsIgnoreCase(key) ||
	                "user".equalsIgnoreCase(key) ||
	                "password".equalsIgnoreCase(key)) {
	                continue;
	            }
	            conProps.setProperty(key, props.getProperty(fullkey));
	        }
        }
        
    }

    /**
     * Return the class name of the JDBC driver
     *
     * @return the class name of the JDBC driver
     */
    public String getDriverName() {
        return driver;
    }

    /**
     * Return the name of the db dource
     *
     * @return the name of the db dource
     */
    public String getName() {
        return name;
    }

    /**
     * Return the type of the db dource
     *
     * @return the type of the db dource
     */
    public String getType() {
        return type;
    }

    /**
     * Set the default (server-wide) properties
     *
     * @param props server default db.properties
     */
    public static void setDefaultProps(ResourceProperties props) {
        defaultProps = props;
    }

    /**
     * Check if this DbSource represents an Oracle database
     *
     * @return true if we're using an oracle JDBC driver
     */
    public boolean isOracle() {
        return isOracle;
    }

    /**
     * Register a dbmapping by its table name.
     *
     * @param dbmap the DbMapping instance to register
     */
    protected void registerDbMapping(DbMapping dbmap) {
        if (!dbmap.inheritsStorage() && dbmap.getTableName() != null) {
            dbmappings.put(dbmap.getTableName().toUpperCase(), dbmap);
        }
    }

    /**
     * Look up a DbMapping instance for the given table name.
     *
     * @param tablename the table name
     * @return the matching DbMapping instance
     */
    protected DbMapping getDbMapping(String tablename) {
        return (DbMapping) dbmappings.get(tablename.toUpperCase());
    }
    
    public String getProperty(String key, String defaultValue) {
    	return this.props.getProperty(this.name + "." + key, defaultValue);
    }
    
    public int getDbType() {
    	return this.dbtype;
    }
    
    private int determineDbType(String driver) {
    	if (driver.startsWith("oracle.jdbc.driver")) {
    		return ORACLE;
    	} else if (driver.equalsIgnoreCase("net.sourceforge.jtds.jdbc.Driver")) {
    		return SQL_SERVER;
    	} else if (driver.equalsIgnoreCase("org.gjt.mm.mysql.Driver")) {
    		return MYSQL;
    	} else if (driver.equalsIgnoreCase("org.h2.Driver")) {
    		return H2;
    	}
    	
    	return UNKNOWN;
    }
    
}