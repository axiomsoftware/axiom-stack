/*
 *  License Notice
 *
 * The contents of this file are subject to the  License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele..org/download//license.txt
 *
 * Copyright 1998-2003  Software. All Rights Reserved.
 *
 * $RCSfile: Main.java,v $
 * $Author: hannes $
 * $Revision: 1.21 $
 * $Date: 2006/01/30 16:15:24 $
 */
package axiom.main.launcher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.net.MalformedURLException;
import java.util.ArrayList;

/**
 *   bootstrap class. Basically this is a convenience wrapper that takes over
 *  the job of setting the class path and  install directory before launching
 *  the static main(String[]) method in <code>axiom.main.Server</code>. This class
 *  should be invoked from a jar file in the  install directory in order to
 *  be able to set up class and install paths.
 */
public class Main {
     /**
     *  boot method. This retrieves the  home directory, creates the
     * classpath and invokes main() in axiom.main.Server.
     *
     * @param args command line arguments
     *
     */
    public static void main(String[] args) {
    	String mainClass = System.getProperty("mainClass");
    	if(mainClass == null){
    		mainClass = "axiom.main.Server";
    	}
    	System.out.println("using mainClass "+mainClass);
        try {
            String installDir = getInstallDir(args);
            
            ClassLoader loader = createClassLoader(installDir);
            // get the main server class
            Class clazz = loader.loadClass(mainClass);
            Class[] cargs = new Class[]{args.getClass()};
            Method main = clazz.getMethod("main", cargs);
            Object[] nargs = new Object[]{args};

            // and invoke the static main(String, String[]) method
            main.invoke(null, nargs);
        } catch (Exception x) {
            // unable to get installation dir from launcher jar
            System.err.println("Error invoking main class "+mainClass);
            x.printStackTrace();
            System.exit(2);
        } 
    }


    /**
     * Create a server-wide ClassLoader from our install directory. 
     * This will be used as parent ClassLoader for all application 
     * ClassLoaders.
     *
     * @param installDir
     * @return the main classloader we'll be using
     * @throws MalformedURLException
     */
    public static ClassLoader createClassLoader(String installDir)
            throws MalformedURLException {

        // decode installDir in case it is URL-encoded
        installDir = URLDecoder.decode(installDir);

        // set up the class path
        File libdir = new File(installDir, "lib");
        ArrayList jarlist = new ArrayList();

         // add all jar files from the lib/ext directory
         File[] files = libdir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    String n = name.toLowerCase();
                    
                    return n.endsWith(".jar") || n.endsWith(".zip");
                }
            });

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                // WORKAROUND: add the files in lib/ext before
                // lib/apache-dom.jar, since otherwise putting a full version
                // of Xerces in lib/ext would cause a version conflict with the
                // xerces classes in lib/apache-dom.jar. Generally, having some pieces
                // of Xerces in lib/apache-dom.jar is kind of problematic.
            	if( files[i].getName().toLowerCase().contains("xerces")){
	                jarlist.add(0, new URL("file:" + files[i].getAbsolutePath()));
	                System.err.println("Adding to classpath: " + files[i].getAbsolutePath());
            	}else{
                    jarlist.add(i, new URL("file:" + files[i].getAbsolutePath()));
                    System.err.println("Adding to classpath: " + files[i].getAbsolutePath());
            	}
            }
        }

        URL[] urls = new URL[jarlist.size()];

        jarlist.toArray(urls);

        // find out if system classes should be excluded from class path
        String excludeSystemClasses = System.getProperty(".excludeSystemClasses");

        ClassLoader loader;

        if ("true".equalsIgnoreCase(excludeSystemClasses)) {
            loader = new URLClassLoader(urls, null);
        } else {
            loader = new URLClassLoader(urls);
        }

        // set the new class loader as context class loader
        Thread.currentThread().setContextClassLoader(loader);
        return loader;
    }


    /**
     * Get the  install directory from the command line -i argument or
     * from the Jar URL from which this class was loaded. Additionally, the
     * System property ".home" is set to the install directory path.
     *
     * @param args
     * @return the base install directory we're running in
     * @throws IOException
     * @throws MalformedURLException
     */
    public static String getInstallDir(String[] args)
            throws IOException, MalformedURLException {
        // check if home directory is set via command line arg. If not,
        // we'll get it from the location of the jar file this class
        // has been loaded from.
        String installDir = null;

        // first, try to get  home dir from command line options
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-i") && ((i + 1) < args.length)) {
                installDir = args[i + 1];
            }
        }

        URLClassLoader apploader = (URLClassLoader)
                                   ClassLoader.getSystemClassLoader();

        // try to get  installation directory
        if (installDir == null) {
            URL launcherUrl = apploader.findResource("axiom/main/launcher/Main.class");

            // this is a  JAR URL of the form
            //    jar:<url>!/{entry}
            // we strip away the jar: prefix and the !/{entry} suffix
            // to get the original jar file URL

            String jarUrl = launcherUrl.toString();

            if (!jarUrl.startsWith("jar:") || jarUrl.indexOf("!") < 0) {
                installDir = System.getProperty("user.dir");
                System.err.println("Warning: Axiom install dir not set by -i parameter ");
                System.err.println("         and not started from launcher.jar. Using ");
                System.err.println("         current working directory as install dir.");
            } else {
                jarUrl = jarUrl.substring(4);

                int excl = jarUrl.indexOf("!");
                jarUrl = jarUrl.substring(0, excl);
                launcherUrl = new URL(jarUrl);

                File f = new File(launcherUrl.getPath()).getAbsoluteFile();

                installDir = f.getParentFile().getCanonicalPath();
            }
        }
        // set System property
        System.setProperty("axiom.home", installDir);
        // and return install dir
        return installDir;
    }

}
