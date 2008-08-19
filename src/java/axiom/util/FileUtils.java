/*
 * Axiom Stack Web Application Framework
 * Copyright (C) 2008  Axiom Software Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileUtils {

    // Deletes all files and subdirectories under dir.
    // Returns true if all deletions were successful.
    // If a deletion fails, the method stops attempting to delete and returns false.
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            final int length = children.length;
            for (int i = 0; i < length; i++) {
                if (!deleteDir(children[i])) {
                	return false;
                } 
            }
        }
    
        // The directory is now empty so delete it
        return dir.delete();
    }
    
    public static boolean copy(File src, File dst) {
    	boolean success = true;
        FileChannel srcChannel = null, dstChannel = null;
        try {
            srcChannel = new FileInputStream(src).getChannel();
            dstChannel = new FileOutputStream(dst).getChannel();
            dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
        } catch (IOException e) {
            success = false;
        } finally {
            if (srcChannel != null) {
                try {
                    srcChannel.close();
                } catch (IOException ignore) {
                }
                srcChannel = null;
            }
            if (dstChannel != null) {
                try {
                    dstChannel.close();
                } catch (IOException ignore) {
                }
                dstChannel = null;
            }
        }
        return success;
    }
    
}