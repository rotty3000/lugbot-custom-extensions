package com.liferay.lugbot.custom.springmvcportlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

public class SpringMVCPortletHelper {

	public static boolean isValidMavenPath(Path path) {
		if (Files.exists(path)) {
			Path pom = path.resolve("pom.xml");

			if (Files.exists(pom)) {

				return true;
			}
		}

		return false;
	}

	public static void un7zip(Path sevenZPath, Path toPath, String newName) throws IOException {
		SevenZFile sevenZFile = new SevenZFile(sevenZPath.toFile());

		SevenZArchiveEntry sevenZArchiveEntry;

		toPath = toPath.resolve(newName);

		while ((sevenZArchiveEntry = sevenZFile.getNextEntry()) != null) {
			if (sevenZArchiveEntry.isDirectory()) {
				continue;
			}

			Path curPath = toPath.resolve(sevenZArchiveEntry.getName());

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

	public static void unzip(Path zipPath, Path toPath, String newName) throws Exception {
		byte[] buffer = new byte[1024];

		toPath = toPath.resolve(newName);
		toPath.toFile().mkdir();

		try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry zipEntry = zipInputStream.getNextEntry();

			while (zipEntry != null) {
				File newFile = new File(toPath.toFile(), zipEntry.getName());

				String newDirPath = String.valueOf(toPath.toAbsolutePath());

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

	public static File unzipRepo(String fileName, Path toPath, String newName) throws Exception {
		toPath.toFile().mkdirs();

		Path tempPath = toPath.resolve(fileName);

		try (FileOutputStream outputStream = new FileOutputStream(tempPath.toFile());
			InputStream inputStream = SpringMVCPortletHelper.class.getResourceAsStream(fileName)) {

			int read;

			byte[] bytes = new byte[1024];

			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}
		}

		if (fileName.endsWith(".7z")) {
			un7zip(tempPath, toPath, newName);
		}
		else if (fileName.endsWith(".zip")) {
			unzip(tempPath, toPath, newName);
		}

		File repoDir = new File(toPath.toFile(), fileName.substring(0, fileName.indexOf(".")));


		return repoDir;
	}

}
