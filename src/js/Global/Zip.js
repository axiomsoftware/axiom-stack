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
 * $RCSfile: Zip.js,v $
 * $Author: hannes $
 * $Revision: 1.3 $
 * $Date: 2006/06/02 15:46:20 $
 */

if (!global.axiom) {
    global.axiom = {};
}

/**
 * Constructor function for Zip Objects.
 * @param {Object|String} file The constructor accepts an object representing a File object, an instance of axiom.SystemFile,
 * an instance of java.io.File or a String representing the path to the zip file
 * @constructor
 */
axiom.Zip = function (file) {

    /**
     * Private function that extracts the data of a file in a .zip archive. If a destination Path is given it
     * writes the extracted data directly to disk using the name of the ZipEntry Object, otherwise it returns the
     * byte array containing the extracted data.
	 * @param {Object} zFile java.util.zip.ZipFile Object
     * @param {Object} entry java.util.zip.ZipEntry Object to extract
     * @param {String} destPath destination path to extract ZipEntry Object to
     * @returns {java.lang.Byte[]} containing the data of the ZipEntry
     */
    var extractEntry = function(zFile, entry, destPath) {
        var size = entry.getSize();
        if (entry.isDirectory() || size <= 0)
            return null;

        var zInStream = new java.io.BufferedInputStream(zFile.getInputStream(entry));
        var buf = new java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, size);
        zInStream.read(buf, 0, size);
        zInStream.close();

        if (!destPath) {
            // no filesystem destination given, so return
            // the byte array containing the extracted data
            return buf;
        }
        // extract the file to the given path
        var dest = new java.io.File(destPath, entry.getName());
        if (entry.isDirectory())
            dest.mkdirs();
        else if (buf) {
            if (!dest.getParentFile().exists())
                dest.getParentFile().mkdirs();
            try {
                var outStream = new java.io.BufferedOutputStream(new java.io.FileOutputStream(dest));
                outStream.write(buf, 0, size);
            } finally {
                outStream.close();
            }
        }
        return null;
    };

    /**
     * Private function for adding a single file to the .zip archive.
     * @param {Object} zOutStream java.util.zip.ZipOutputStream
     * @param {Object} f An instance of java.io.File representing the file to be added
     * @param {int} level Compression-level (0-9)
     * @param {String} [pathPrefix] Path of the directory in the archive to which the file should be added
     */
    var addFile = function(zOutStream, f, level, pathPrefix) {
        var fInStream = new java.io.BufferedInputStream(new java.io.FileInputStream(f));
        buf = new java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, f.length());
        fInStream.read(buf, 0, f.length());

        var name = new java.lang.StringBuffer();
        if (pathPrefix) {
            // append clean pathPrefix to name buffer
            var st = new java.util.StringTokenizer(pathPrefix, "\\/");
            while (st.hasMoreTokens()) {
                name.append(st.nextToken());
                name.append("/");
            }
        }
        name.append(f.getName());
        var entry = new java.util.zip.ZipEntry(name.toString());
        entry.setSize(f.length());
        entry.setTime(f.lastModified());
        zOutStream.setLevel(level);
        zOutStream.putNextEntry(entry);
        zOutStream.write(buf, 0, buf.length);
        zOutStream.closeEntry();
        fInStream.close();
        return true;
    };

    /**
     * Private function that constructs an instance of java.io.File based on a JS File or Axiom.SystemFile object.
     * @param {Object} file Either a string or an instance of java.io.File, File or Axiom.SystemFile
     * @returns {Object} An innstance of java.io.File
     */
    var evalFile = function(f) {
        if (f instanceof java.io.File)
            return f;
        var result;
        if (typeof f == "string")
            result = new java.io.File(f);
        else
            result = new java.io.File(f.getAbsolutePath());
        if (!result.exists())
            throw("Error creating Zip Object: File '" + f + "' doesn't exist.");
        return result;
    };

    /**
     * Returns an array containing the entries of a .zip file as axiom.Zip.Entry objects.
     * @returns {Object} An array containing the entries of a .zip file as axiom.Zip.Entry objects
     */
    this.list = function() {
        var result = new axiom.Zip.Content();
        var zFile = new java.util.zip.ZipFile(file);
        var entries = zFile.entries();
        while (entries.hasMoreElements())
            result.add(new axiom.Zip.Entry(entries.nextElement()));
        zFile.close();
        return result;
    };

    /**
     * Extracts a single file from a .zip archive.  If a destination path is given it directly writes
     * the extracted file to disk.
     * @param {String} name Name of the file to extract
     * @param {String} [destPath] Path to write file to disk
     * @returns {axiom.Zip.Entry} An object representing the metadata of a zip entry
     */
    this.extract = function(name, destPath) {
        var zFile = new java.util.zip.ZipFile(file);
        var entry = zFile.getEntry(name);
        if (!entry)
            return null;
        var result = new axiom.Zip.Entry(entry);
        result.data = extractEntry(zFile, entry, destPath);
        zFile.close();
        return result;
    };

    /**
     * Extracts all files in a .zip archive.  If a destination path is given it directly writes
     * the extracted files to disk preserving the directory structure of .zip archive if existing.
     * @param {String} [destPath] Destination path to write file to
     * @returns {axiom.Zip.Content} An Object containing the table of contents of .zip file
     * and the file tree of the .zip file
     */
    this.extractAll = function(destPath) {
        var result = new axiom.Zip.Content();
        var zFile = new java.util.zip.ZipFile(file);
        var entries = zFile.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var e = new axiom.Zip.Entry(entry);
            e.data = extractEntry(zFile, entry, destPath);
            result.add(e);
        }
        zFile.close();
        return result;
    };

    /**
     * Adds a single file or a whole directory(recursive) to the .zip archive.
     * @param {Object|String} file Accepts an object representing a File object, an instance of axiom.SystemFile,
	 * an instance of java.io.File or a String representing the path to the file that should be added
     * @param {Number} level To use for compression (default: 9 = best compression)
     * @param {String} [name] Name of the directory in the archive into which the file should be added
     * @returns {Boolean} True if successful, false otherwise
     */
    this.add = function (f, level, pathPrefix) {
        var f = evalFile(f);

        // evaluate arguments
        if (arguments.length == 2) {
            if (typeof arguments[1] == "string") {
                pathPrefix = arguments[1];
                level = 9;
            } else {
                level = parseInt(arguments[1], 10);
                pathPrefix = null;
            }
        } else if (level == null || isNaN(level)) {
            level = 9;
        }
        // only levels between 0 and 9 are allowed
        level = Math.max(0, Math.min(9, level));

        if (f.isDirectory()) {
            // add a whole directory to the zip file (recursive!)
            var files = (new axiom.SystemFile(f.getAbsolutePath())).listRecursive();
            for (var i in files) {
                var fAdd = new java.io.File(files[i]);
                if (!fAdd.isDirectory()) {
                    var p = fAdd.getPath().substring(f.getAbsolutePath().length, fAdd.getParent().length);
                    if (pathPrefix)
                        p = pathPrefix + p;
                    addFile(zOutStream, fAdd, level, p);
                }
            }
        } else
            addFile(zOutStream, f, level, pathPrefix);
        return true;
    };

    /**
     * Adds a new entry to the zip file.
     * @param {java.lang.Byte[]} buf A java.lang.byte[] containing the data to add
     * @param {String} name Name of the file to add
     * @param {Number} level Compression level (0-9, default: 9)
     * @returns {Boolean} True if successful, false otherwise
     */
    this.addData = function(buf, name, level) {
        var entry = new java.util.zip.ZipEntry(name);
        entry.setSize(buf.length);
        entry.setTime(new Date());
        if (level == null || isNaN(level))
            zOutStream.setLevel(9);
        else
            zOutStream.setLevel(Math.max(0, Math.min(9, parseInt(level, 10))));
        zOutStream.putNextEntry(entry);
        zOutStream.write(buf, 0, buf.length);
        zOutStream.closeEntry();
        return true;
    };

    /**
     * Closes the Zip File.
     */
    this.close = function() {
        zOutStream.close();
        return;
    };

    /**
     * Returns the binary data of the zip file.
     * @returns {java.lang.Byte[]} A java.lang.Byte[] containing the binary data of the zip file
     */
    this.getData = function() {
        return bOutStream.toByteArray();
     };

    /**
     * Saves the archive by closing the output stream.
     * @param {String} path The path(including the name) to save the zip file to
     * @returns {Boolean} True if successful, false otherwise
     */
    this.save = function(dest) {
        if (!dest)
            throw new Error("no destination for ZipFile given");
        // first of all, close the ZipOutputStream
        zOutStream.close();
        var destFile = new java.io.File(dest);
        try {
            var outStream = new java.io.FileOutputStream(destFile);
            bOutStream.writeTo(outStream);
        } finally {
            outStream.close();
        }
        return true;
    };

    this.toString = function() {
        if (file)
            return "[Zip Object " + file.getAbsolutePath() + "]";
        else
            return "[Zip Object]";
    };

    /**
     * constructor body
     */
    var bOutStream = new java.io.ByteArrayOutputStream();
    var zOutStream = new java.util.zip.ZipOutputStream(bOutStream);
    if (file) {
        file = evalFile(file);
    }

    for (var i in this)
        this.dontEnum(i);

    return this;
}

/**
 * Constructor for axiom.Zip.Content, an object that maintains a table of contents and file tree of
 * the .zip file.
 * @constructor
 */
axiom.Zip.Content = function() {
	/**
	 * Table of contents.
	 * @type Array
	 */
    this.toc = [];

	/**
	 * File tree.
	 * @type Object
	 */
    this.files = {};

    /**
     * Adds a Zip Entry object to the table of contents and the files collection.
     * @param {axiom.Zip.Entry} entry An instance of axiom.Zip.Entry
     */
    this.add = function(entry) {
        // add the file to the table of contents array
        this.toc.push(entry);
        // plus add it to the files tree
        var re = /[\\\/]/;
        var arr = entry.name.split(re);
        var cnt = 0;
        var curr = this.files;
        var propName;
        while (cnt < arr.length-1) {
            propName = arr[cnt++];
            if (!curr[propName])
                curr[propName] = new Object();
            curr = curr[propName];
        }
        curr[arr[cnt]] = entry;
        return;
    };

	for (var i in this)
		this.dontEnum(i);

    return this;
};


/**
 * Constructor for axiom.Zip.Entry, an object that holds the metadata of a zip entry.
 * @param {java.util.zip.ZipEntry} entry A java.util.zip.ZipEntry Object
 * @constructor
 */
axiom.Zip.Entry = function(entry) {
	/**
	 * Name of the entry.
	 * @type String
	 */
    this.name = entry.getName();

	/**
	 * Decompressed size of the entry in bytes.
	 * @type Number
	 */
	this.size = entry.getSize();

	/**
	 * Last modification timestamp of the entry or null.
	 * @type Date
	 */
    this.time = entry.getTime() ? new Date(entry.getTime()) : null;

	/**
	 * True in case entry is a directory, false otherwise.
	 * @type Boolean
	 */
    this.isDirectory = entry.isDirectory();

	/**
	 * A java.lang.Byte[] containing the data of the entry.
	 * @type java.lang.Byte[]
	 */
	this.data = null;

	for (var i in this)
        this.dontEnum(i);
    return this;
};


/**
 * Extract all files in a ByteArray passed as argument and return them as an Axiom.Zip.Content object.
 * @param {java.lang.InputStream | java.lang.Byte[]} stream Either a java.lang.InputStream, sub-classes included, or a java.lang.Byte[] containing the data of the .zip file
 * @returns {axiom.Zip.Content} An instance of axiom.Zip.Content
 */
axiom.Zip.extractData = function(stream) {
    if (!stream) {
	return null;
    };

    if (!(stream instanceof Packages.java.io.InputStream)) {
	app.log("not stream");
	stream = new java.io.ByteArrayInputStream(stream);
    };

    var zInStream = new java.util.zip.ZipInputStream(stream);

    if (!zInStream) {
	return null;
    };

    var result = new axiom.Zip.Content();

    var entry;
    while ((entry = zInStream.getNextEntry()) != null) {
        var eParam = new axiom.Zip.Entry(entry);
        if (eParam.isDirectory)
            continue;
        if (eParam.size == -1)
            eParam.size = 16384;
        var bos = new java.io.ByteArrayOutputStream(eParam.size);
        var buf = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 8192);
        var count;
        while ((count = zInStream.read(buf)) != -1)
            bos.write(buf, 0, count);
        eParam.data = bos.toByteArray();
        eParam.size = bos.size();
        result.add(eParam);
    }
    zInStream.close();
    return result;
};

/**
 * Extract all files AND folders in a ByteArray passed as argument and return them as an Axiom.Zip.Content object.
 * @param {java.lang.InputStream | java.lang.Byte[]} stream Either a java.lang.InputStream, sub-classes included, or a java.lang.Byte[] containing the data of the .zip file
 * @returns {axiom.Zip.Content} An instance of axiom.Zip.Content
 */
axiom.Zip.extractDataWithFolders = function(stream) {
    if (!stream) {
	return null;
    };

    if (!(stream instanceof Packages.java.io.InputStream)) {
	stream = new java.io.ByteArrayInputStream(stream);
    };

    var zInStream = new java.util.zip.ZipInputStream(stream);

    if (!zInStream) {
	return null;
    };

    var result = new axiom.Zip.Content();

    var entry;
    while ((entry = zInStream.getNextEntry()) != null) {
		var tree = entry.toString().split('/');
		var directory = tree.length > 1 ? tree[tree.length-2] : null;
		var path = directory ? tree.splice(0, tree.length - 1).join('/') : null;
        var eParam = new axiom.Zip.Entry(entry);
        if (eParam.isDirectory) {
            continue;
		}
        if (eParam.size == -1)
            eParam.size = 16384;
        var bos = new java.io.ByteArrayOutputStream(eParam.size);
        var buf = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 8192);
        var count;
        while ((count = zInStream.read(buf)) != -1)
            bos.write(buf, 0, count);
        eParam.data = bos.toByteArray();
        eParam.size = bos.size();
		eParam.directory = directory;
		eParam.path = path;
        result.add(eParam);
    }
    zInStream.close();
    return result;
};

axiom.lib = "Zip";
axiom.dontEnum(axiom.lib);
for (var i in axiom[axiom.lib])
	if(i != 'prototype')
		axiom[axiom.lib].dontEnum(i);
for (var i in axiom[axiom.lib].prototype)
    axiom[axiom.lib].prototype.dontEnum(i);
delete axiom.lib;
