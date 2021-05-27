package com.liferay.lugbot.custom.springmvcportlet;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipFunctions {

    public static void un7zip(Path repoZipPath, Path tempDir) throws IOException {
        SevenZFile sevenZFile = new SevenZFile(repoZipPath.toFile());

        SevenZArchiveEntry sevenZArchiveEntry;

        while ((sevenZArchiveEntry = sevenZFile.getNextEntry()) != null) {
            if (sevenZArchiveEntry.isDirectory()) {
                continue;
            }

            Path curPath = tempDir.resolve(sevenZArchiveEntry.getName());

            Path parentPath = curPath.getParent();

            if (!Files.exists(parentPath)) {
                Files.createDirectories(parentPath);
            }

            byte[] content = new byte[(int)sevenZArchiveEntry.getSize()];

            sevenZFile.read(content, 0, content.length);

            try (FileOutputStream fileOutputStream = new FileOutputStream(curPath.toFile())) {
                fileOutputStream.write(content);
            }
        }
    }


    public static void unzip(Path srcFile, Path destDir) throws Exception {
        byte[] buffer = new byte[1024];

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(srcFile))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();

            while (zipEntry != null) {
                File newFile = new File(destDir.toFile(), zipEntry.getName());

                String newDirPath = String.valueOf(destDir.toAbsolutePath());

                String newFilePath = newFile.getAbsolutePath();

                if (!newFilePath.startsWith(newDirPath + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                }

                if (!newFile.isDirectory()) {
                    FileOutputStream fileOutputStream = new FileOutputStream(newFile);

                    int len;

                    while ((len = zipInputStream.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, len);
                    }

                    fileOutputStream.close();
                }

                zipEntry = zipInputStream.getNextEntry();
            }
        }
    }

    public static File unzipTestRepo(String fileName, Path tempDir) throws Exception {
        Path tempPath = tempDir.resolve(fileName);

        try (FileOutputStream outputStream = new FileOutputStream(tempPath.toFile());
             InputStream inputStream = ZipFunctions.class.getResourceAsStream(fileName)) {

            int read;

            byte[] bytes = new byte[1024];

            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        }

        if (fileName.endsWith(".7z")) {
            un7zip(tempPath, tempDir);
        }
        else if (fileName.endsWith(".zip")) {
            unzip(tempPath, tempDir);
        }

        File repoDir = new File(tempDir.toFile(), fileName.substring(0, fileName.indexOf(".")));

        return repoDir;
    }
}
