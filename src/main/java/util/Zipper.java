package util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zipper {
    final private Path srcPath;
    final private Path zipPath;
    final private List<String> foldersToExclude = new ArrayList<>();


    public Zipper(Path srcPath, Path zipPath) {
        this.srcPath = srcPath;
        this.zipPath = zipPath;
    }

    public Zipper excludeFolders(String... exDir) {
        foldersToExclude.addAll(Arrays.asList(exDir));
        return this;
    }


    public Zipper excludeFolder(String exDir) {
        foldersToExclude.add(exDir);
        return this;
    }


    public void zip() throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Files.walk(srcPath)
                    .filter(path -> !isPathExcluded(path, foldersToExclude))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(srcPath.relativize(path).toString());
                        if (Files.isDirectory(path)) {
                            zipEntry = new ZipEntry(zipEntry.getName() + "/");
                        }
                        try {
                            zos.putNextEntry(zipEntry);
                            if (!Files.isDirectory(path)) {
                                Files.copy(path, zos);
                            }
                            zos.closeEntry();
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    });
        }
    }

    private static boolean isPathExcluded(Path path, List<String> foldersToExclude) {
        for (String folder : foldersToExclude) {
            if(Files.isDirectory(path) && path.toString().endsWith(File.separator + folder)) {
                return true;
            } else if (path.toString().contains(File.separator + folder + File.separator)) {
                return true;
            }
        }
        return false;
    }
}
