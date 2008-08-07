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
 * $RCSfile: File.js,v $
 * $Author: czv $
 * $Revision: 1.2 $
 * $Date: 2006/04/24 07:02:17 $
 */


if (!global.axiom) {
    global.axiom = {};
}
/**
 * A file library used for easy file access.
 * @constructor
 * @param {String} path The path to the file on the file system
 */
axiom.SystemFile = function(path) {
   var BufferedReader            = java.io.BufferedReader;
   var File                      = java.io.File;
   var Reader                    = java.io.Reader;
   var Writer                    = java.io.Writer;
   var FileReader                = java.io.FileReader;
   var FileWriter                = java.io.FileWriter;
   var PrintWriter               = java.io.PrintWriter;
   var EOFException              = java.io.EOFException;
   var IOException               = java.io.IOException;
   var IllegalStateException     = java.lang.IllegalStateException;
   var IllegalArgumentException  = java.lang.IllegalArgumentException;

   var self = this;
   var file;

   try {
      if (arguments.length > 1)
         file = new File(path, arguments[1]);
      else
         file = new File(path);
   } catch (e) {
      throw(e);
   }

   var readerWriter;
   var atEOF = false;
   var lastLine = null;

   var setError = function(e) {
      this.lastError = e;
   };

   this.lastError = null;

   /**
    * Returns the java.io.File.toString() of the underlying File object.
    *
    * @return {String} The File's toString()
    */
   this.toString = function() {
      return file.toString();
   };

   /**
    * Returns the java.io.File.getName() of the underlying File object
    * (i.e. the name of the file or directory).
    *
    * @return {String} The File's getName()
    */
   this.getName = function() {
      var name = file.getName();
      return (name == null ? "" : name);
   };

   /**
    * Returns whether or not this file has been opened or not.
    *
    * @return {Boolean} Value of <code> True </code> if the file is opened, 
    					<code> false </code> otherwise
    */
   this.isOpened = function() {
      return (readerWriter != null);
   };

   /**
    * Opens the file represented by this File object.  If an exception occurs during this
    * operation, use <code> this.error() </code> to retrieve the error.
    *
    * @param {String} [mode] The mode to open the file in.  Possible values are 'w' for
    *                        write and 'a' for append.  Defaults to opening the file in 
    *                        read mode
    * @return {Boolean} Whether the open was a success or not.  <code> false </code> will
    *                   be returned if an exception was thrown or the file does not exist
    */
   this.open = function(mode) {
      if (self.isOpened()) {
         setError(new IllegalStateException("File already open"));
         return false;
      }
       try {
	   if (mode && mode == 'w') {
	       //open to write to the file
	       readerWriter = new PrintWriter(new FileWriter(file));
	   } else if (mode && mode == 'a') {
	       //append anything written to the file
	       readerWriter = new PrintWriter(new FileWriter(file, true));
	   } else if (file.exists()) {
	       //default to reading the file
               readerWriter = new BufferedReader(new FileReader(file));
	   } else {
	       setError("File does not exist and cannot be read.");
	       return false;
	   }
	   return true;
       } catch (e) {
           setError(e);
           return false;
       }
       return;
   };

   /**
    * Returns whether the file or directory represented by this File object exists or not.
    *
    * @return {Boolean} <code> True </code> if the file or directory exists, 
    * 					<code> false </code> otherwise
    */
   this.exists = function() {
      return file.exists();
   };

   /**
    * Returns a new axiom.SystemFile object representing the parent file/directory 
    * to this object.
    *
    * @return {axiom.SystemFile} The parent file/directory
    */
   this.getParent = function() {
      if (!file.getParent())
         return null;
      return new axiom.SystemFile(file.getParent());
   };

   /**
    * Reads characters until an end of line/file is encountered then returns the string 
    * for these characters (without any end of line character). 
    *
    * @return {String} A string representing the line that was read
    */
   this.readln = function() {
      if (!self.isOpened()) {
         setError(new IllegalStateException("File not opened"));
         return null;
      }
      if (!(readerWriter instanceof BufferedReader)) {
         setError(new IllegalStateException("File not opened for reading"));
         return null;
      }
      if (atEOF) {
         setError(new EOFException());
         return null;
      }
      if (lastLine != null) {
         var line = lastLine;
         lastLine = null;
         return line;
      }
      var reader = readerWriter;
      // Here lastLine is null, return a new line
      try {
         var line = readerWriter.readLine();
         if (line == null) {
            atEOF = true;
            setError(new EOFException());
         }
         return line;
      } catch (e) {
         setError(e);
         return null;
      }
      return;
   };

   /**
    * Appends a string to the file represented by this File object.
    *
    * @param {String} what The string to write to the file
    */
   this.write = function(what) {
      if (!self.isOpened()) {
         setError(new IllegalStateException("File not opened"));
         return false;
      }
      if (!(readerWriter instanceof PrintWriter)) {
         setError(new IllegalStateException("File not opened for writing"));
         return false;
      }
      if (what != null) {
         readerWriter.print(what.toString());
      }
      return true;
   };

   /**
    * Appends a string with a platform specific end of line to the file 
    * represented by this File object. 
    *
    * @param {String} what The string to write to the file
    */
   this.writeln = function(what) {
      if (self.write(what)) {
         readerWriter.println();
         return true;
      }
      return false;
   };

   /**
    * Returns whether this File object's pathname is absolute or not.
    *
    * @return {Boolean} <code> True </code> if the pathname is absolute, 
    *                   <code> false </code> otherwise
    */
   this.isAbsolute = function() {
      return file.isAbsolute();
   };

   /**
    * Deletes the file or directory represented by this File object.  This operation will 
    * fail on a file that is currently open.  If this operation fails, 
    * <code> this.error() </code> can be used to retrieve the error.
    *
    * @return {Boolean} <code> True </code> if the operation was a success,
    *                   <code> false </code> otherwise
    */
   this.remove = function() {
      if (self.isOpened()) {
         setError(new IllegalStateException("An openened file cannot be removed"));
         return false;
      }
      return file["delete"]();
   };

   /**
    * List all file/directory names within a directory.  Pass an optional RegExp Pattern to 
    * return just files matching this pattern.
    *
    * @example var xmlFiles = dir.list(/.*\.xml/);
    * @param {RegExp} [pattern] The pattern to test each file name against
    * @return {Array} The list of file names
    */
   this.list = function(pattern) {
      if (self.isOpened())
         return null;
      if (!file.isDirectory())
         return null;
      if (pattern) {
         var fileList = file.list();
         var result = [];
         for (var i in fileList) {
            if (pattern.test(fileList[i]))
               result.push(fileList[i]);
         }
         return result;
      }
      return file.list();
   };

   /**
    * Flushes the content of the file represented by this File object to disk.  If the file
    * is not opened for write, or an exception occurs during the flush, the error may
    * be retrieved through <code> this.error()</code>.
    *
    * @return {Boolean} <code> True </code> if the operation was a success, 
    *                   <code> false </code> otherwise
    */
   this.flush = function() {
      if (!self.isOpened()) {
         setError(new IllegalStateException("File not opened"));
         return false;
      }
      if (readerWriter instanceof Writer) {
         try {
            readerWriter.flush();
         } catch (e) {
           setError(e);
           return false;
         }
      } else {
         setError(new IllegalStateException("File not opened for write"));
         return false; // not supported by reader
      }
      return true;
   };

   /**
    * Closes the file represented by this File object.
    *
    * @return {Boolean} <code> True </code> if the operation was a success,
    *					<code> false </code> otherwise
    */
   this.close = function() {
      if (!self.isOpened())
         return false;
      try {
         readerWriter.close();
         readerWriter = null;
         return true;
      } catch (e) {
         setError(e);
         readerWriter = null;
         return false;
      }
   };

   /**
    * Returns the pathname string of this File object. The resulting string uses the default
    * name-separator character to separate the names in the name sequence. 
    *
    * @return {String} The path of the file
    */
   this.getPath = function() {
      var path = file.getPath();
      return (path == null ? "" : path);
   };

   /**
    * A string representation of the last error that occurred, if any.
    *
    * @return {String} The error message
    */
   this.error = function() {
      if (this.lastError == null) {
         return "";
      } else {
         var exceptionName = this.lastError.getClass().getName();
         var l = exceptionName.lastIndexOf(".");
         if (l > 0)
            exceptionName = exceptionName.substring(l + 1);
         return exceptionName + ": " + this.lastError.getMessage();
      }
   };

   /**
    * Clears any error message that may otherwise be returned by the 
    * <code> error() </code> method. 
    */
   this.clearError = function() {
      this.lastError = null;
      return;
   };

   /**
    * Returns whether the application can read the file represented by 
    * this File object or not.
    *
    * @return {Boolean} <code> True </code> if the file can be read,
    *					<code> false </code> otherwise
    */
   this.canRead = function() {
      return file.canRead();
   };

   /**
    * Returns whether the application can write to the file represented by 
    * this File object or not.
    *
    * @return {Boolean} <code> True </code> if the file can be written to,
    *					<code> false </code> otherwise
    */
   this.canWrite = function() {
      return file.canWrite();
   };

   /**
    * Returns the absolute pathname string of this file. 
    *
    * @return {String} The absolute pathname string of this file
    */
   this.getAbsolutePath = function() {
      var absolutPath = file.getAbsolutePath();
      return (absolutPath == null ? "" : absolutPath);
   };

   /**
    * Returns the length of the file in bytes represented by this File object. 
    *
    * @return {Number} The length of the file in bytes
    */
   this.getLength = function() {
      return file.length();
   };

   /**
    * Returns whether the file represented by this File object is a directory or not.
    *
    * @return {Boolean} <code> True </code> if this File object is a directory,
    *					<code> false </code> otherwise
    */
   this.isDirectory = function() {
      return file.isDirectory();
   };

   /**
    * Returns whether the file represented by this File object is a file or not.
    *
    * @return {Boolean} <code> True </code> if this File object is a file,
    *					<code> false </code> otherwise
    */
   this.isFile = function() {
      return file.isFile();
   };

   /**
    * Returns the date when the file represented by this File object was last modified.
    *
    * @return {Date} Last modified date
    */
   this.lastModified = function() {
      return file.lastModified();
   };

   /**
    * Make the directory at the path represented by this File object, if it does not
    * already exist.
    *
    * @return {Boolean} <code> True </code> if the directory was created, 
    *					<code> false </code> if the directory already existed or the create
    *					was unsuccessful
    */
   this.makeDirectory = function() {
      if (self.isOpened())
         return false;
      // don't do anything if file exists or use multi directory version
      return (file.exists() || file.mkdirs());
   };

   /**
    * Renames the file represented by this File object to the name and path represented by
    * the input parameter File object.  If an error occurred during this operation, the 
    * error may be obtained through <code> this.error() </code>.
    *
    * @param {axiom.SystemFile} toFile The file to rename this file to
    * @return {Boolean} <code> True </code> if the rename was successful,
    *					<code> false </code> otherwise
    */
   this.renameTo = function(toFile) {
      if (toFile == null) {
         setError(new IllegalArgumentException("Uninitialized target File object"));
         return false;
      }
      if (self.isOpened()) {
         setError(new IllegalStateException("An openened file cannot be renamed"));
         return false;
      }
      if (toFile.isOpened()) {
         setError(new IllegalStateException("You cannot rename to an openened file"));
         return false;
      }
      return file.renameTo(new java.io.File(toFile.getAbsolutePath()));
   };

   /**
    * Returns true if the file represented by this File object has been read entirely and 
    * the end of file has been reached. 
    *
    * @return {Boolean} <code> True </code> if the end of file has been reached,
    *					<code> false </code> otherwise
    */
   this.eof = function() {
      if (!self.isOpened()) {
         setError(new IllegalStateException("File not opened"));
         return true;
      }
      if (!(readerWriter instanceof BufferedReader)) {
         setError(new IllegalStateException("File not opened for read"));
         return true;
      }
      if (atEOF)
         return true;
      if (lastLine != null)
         return false;
      try {
         lastLine = readerWriter.readLine();
         if (lastLine == null)
            atEOF = true;
         return atEOF;
      } catch (e) {
         setError(e);
         return true;
      }
   };

   /**
    * Read all the lines contained in the file and returns the contents as a string.
    *
    * @return {String} All the lines contained in the file
    */
   this.readAll = function() {
      // Open the file for readAll
      if (self.isOpened()) {
         setError(new IllegalStateException("File already open"));
         return null;
      }
      try {
         if (file.exists()) {
            readerWriter = new BufferedReader(new FileReader(file));
         } else {
            setError(new IllegalStateException("File does not exist"));
            return null;
         }
         if (!file.isFile()) {
            setError(new IllegalStateException("File is not a regular file"));
            return null;
         }

         // read content line by line to setup proper eol
         var buffer = new java.lang.StringBuffer(file.length() * 1.10);
         while (true) {
            var line = readerWriter.readLine();
            if (line == null)
               break;
            if (buffer.length() > 0)
               buffer.append("\n");  // EcmaScript EOL
            buffer.append(line);
         }

         // Close the file
         readerWriter.close();
         readerWriter = null;
         return buffer.toString();
      } catch (e) {
         readerWriter = null;
         setError(e);
         return null;
      }
   };

   /**
    * Removes a directory recursively without any warning or precautious measures.  
    * USE WITH PRECAUTION!
    *
    * @return {Boolean} <code> True </code> if the operation was a success,
    *					<code> false </code> otherwise
    */
   this.removeDirectory = function() {
      if (!file.isDirectory())
         return false;
      var arr = file.list();
      for (var i=0; i<arr.length; i++) {
         var f = new axiom.SystemFile(file, arr[i]);
         if (f.isDirectory())
            f.removeDirectory();
         else
            f.remove();
      }
      file["delete"]();
      return true;
   };

   /**
    * Recursively lists all files below a given directory.  Pass a RegExp Pattern to return 
    * just files matching this pattern.
    *
    * @param {RegExp} [pattern] The pattern to test each file name against
    * @return {Array} The list of absolute file paths
    */
   this.listRecursive = function(pattern) {
      if (!file.isDirectory())
         return false;
      if (!pattern || pattern.test(file.getName()))
         var result = [file.getAbsolutePath()];
      else
         var result = [];
      var arr = file.list();
      for (var i=0; i<arr.length; i++) {
         var f = new axiom.SystemFile(file, arr[i]);
         if (f.isDirectory())
            result = result.concat(f.listRecursive(pattern));
         else if (!pattern || pattern.test(arr[i]))
            result.push(f.getAbsolutePath());
      }
      return result;
   }

   /**
    * Makes a copy of a file over partitions.
    *
    * @param {String} path Full path of the new file
    * @return {Boolean} <code> True </code> if the operation was a success,
    *					<code> false </code> otherwise
    */
   this.hardCopy = function(dest) {
      var inStream = new java.io.BufferedInputStream(
         new java.io.FileInputStream(file)
      );
      var outStream = new java.io.BufferedOutputStream(
         new java.io.FileOutputStream(dest)
      );
      var buffer = java.lang.reflect.Array.newInstance(
         java.lang.Byte.TYPE, 4096
      );
      var bytesRead = 0;
      while ((bytesRead = inStream.read(buffer, 0, buffer.length)) != -1) {
         outStream.write(buffer, 0, bytesRead);
      }
      outStream.flush();
      inStream.close();
      outStream.close();
      return true;
   }

   /**
    * Moves a file to a new location.
    *
    * @param {String} path Full path of the new file
    * @return {Boolean} <code> True </code> if the file could be moved, 
    * 				  	<code> false </code> otherwise
    */
   this.move = function(dest) {
      // instead of using the standard File method renameTo()
      // do a hardCopy and then remove the source file. This way
      // file locking shouldn't be an issue
      self.hardCopy(dest);
      // remove the source file
      file["delete"]();
      return true;
   }

   /**
    * Returns the contents of this file object as a byte array.  Useful for passing it to 
    * a function instead of an request object.
    *
    * @return {byte[]} The contents as a byte array
    */
   this.toByteArray = function() {
      if (!this.exists())
         return null;
      var body = new java.io.ByteArrayOutputStream();
      var stream = new java.io.BufferedInputStream(
         new java.io.FileInputStream(this.getAbsolutePath())
      );
      var buf = java.lang.reflect.Array.newInstance(
         java.lang.Byte.TYPE, 1024
      );
      var read;
      while ((read = stream.read(buf)) > -1)
         body.write(buf, 0, read);
      stream.close();
      return body.toByteArray();
   };

   for (var i in this)
      this.dontEnum(i);

   return this;
}


axiom.SystemFile.toString = function() {
   return "[axiom.SystemFile]";
};

/**
 * Return the string content of the file. Caller should handle potential I/O exceptions.
 *
 * @param {String} filename The path of the file to read from
 * @return {String} The contents of the file as a string
 */
axiom.SystemFile.readFromFile = function(filename){
	var reader;
	var lines = [];
	try{
		var f = new java.io.File(filename);
		if(!f.exists()) {
			return '';
		}
		reader = new java.io.BufferedReader(new java.io.FileReader(f));

		var line;
		while((line = reader.readLine()) !== null){
			lines.push(line);
		}
	} finally{
		if(reader)
			reader.close();
		reader = null;
	}
	return lines.join('\n');
}

/**
 * Write the given string to the target path or File. 
 * Caller should handle potential I/O exceptions.
 *
 * @param {String} str The string to write
 * @param {String} filename The path of the file to write to
 */
axiom.SystemFile.writeToFile = function(str, filename){
	var writer;
	try{
		writer = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(filename)));
		writer.print(str);
	} finally{
		if(writer)
			writer.close();
		writer = null;
	}
}

/** 
 * The operating system specific path separator.
 * @type String
 */
axiom.SystemFile.separator = java.io.File.separator;


axiom.lib = "SystemFile";
axiom.dontEnum(axiom.lib);
for (var i in axiom[axiom.lib])
	if(i != 'prototype')
		axiom[axiom.lib].dontEnum(i);
for (var i in axiom[axiom.lib].prototype)
   axiom[axiom.lib].prototype.dontEnum(i);
delete axiom.lib;
