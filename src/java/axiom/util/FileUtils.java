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