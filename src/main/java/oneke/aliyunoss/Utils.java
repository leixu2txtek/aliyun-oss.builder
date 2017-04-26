package oneke.aliyunoss;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;

public class Utils {

    /**
     * Gets all files with child directory
     * @param root
     * @param files
     * @return all files with absolutePath
     */
    public static ArrayList<String> GetFilesWithChildren(File root, ArrayList<String> files) {

        File[] list = root.listFiles();

        if (list == null) return files;

        for (File p : list) {

            if (p.isDirectory()) {

                GetFilesWithChildren(p, files);
            } else {

                files.add(p.getAbsolutePath());
            }
        }

        return files;
    }
}