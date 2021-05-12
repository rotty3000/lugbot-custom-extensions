/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.lugbot.custom.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liferay.lugbot.api.ProposalDTO;
import com.liferay.lugbot.api.util.GitFunctions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import org.eclipse.jgit.api.Status;

/**
 * @author Gregory Amerson
 */
public class ProviderTester {

	public static void assertEmpty(Path repoPath, Status status) {
		Predicate<Set<String>> isEmpty = set -> set.isEmpty();

		assertTrue(isEmpty.test(status.getAdded()));
		assertTrue(isEmpty.test(status.getChanged()));
		assertTrue(isEmpty.test(status.getConflicting()));
		assertTrue(isEmpty.test(status.getMissing()));
		assertTrue(isEmpty.test(status.getModified()));
		assertTrue(isEmpty.test(status.getRemoved()));
		assertTrue(isEmpty.test(status.getUncommittedChanges()));
		assertTrue(isEmpty.test(status.getUntracked()));

		if (isEmpty.test(status.getUntrackedFolders())) {
			return;
		}

		Set<String> untrackedFolders = status.getUntrackedFolders();

		Stream<Path> untrackedFiles = untrackedFolders.stream(
		).map(
			folder -> repoPath.resolve(folder)
		).flatMap(
			folder -> {
				try {
					return Files.walk(
						folder
					).filter(
						Files::isRegularFile
					);
				}
				catch (IOException e) {
					return Stream.empty();
				}
			}
		);

		assertEquals(0, untrackedFiles.count());
	}

	public static void assertProposalDTO(ProposalDTO dto) {
		assertNotNull(dto);
		assertNotNull(dto.action);
		assertNotNull(dto.body);
		assertNotNull(dto.mergeAdvice);
		assertNotNull(dto.title);
	}

	public static boolean contains(Path path, String search, boolean regex) throws IOException {
		String content = new String(Files.readAllBytes(path));

		if (regex) {
			Pattern pattern = Pattern.compile(search, Pattern.DOTALL | Pattern.MULTILINE);

			Matcher matcher = pattern.matcher(content);

			return matcher.matches();
		}

		return content.contains(search);
	}

	public static Process exec(File workingDir, String... args) throws InterruptedException, IOException {
		ProcessBuilder processBuilder = new ProcessBuilder();

		processBuilder.directory(workingDir);

		processBuilder.command(args);

		Process process = processBuilder.start();

		process.waitFor(60, TimeUnit.SECONDS);

		return process;
	}

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
			InputStream inputStream = ProviderTester.class.getResourceAsStream(fileName)) {

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

		File gitDir = new File(repoDir, ".git");

		assertTrue(gitDir.exists(), gitDir.toString());

		assertEquals("master", GitFunctions.getCurrentBranchName(repoDir.toPath()));

		File gradlewFile = new File(repoDir, "gradlew");

		if (gradlewFile.exists()) {
			Files.setPosixFilePermissions(gradlewFile.toPath(), PosixFilePermissions.fromString("rwxrwxr-x"));
		}

		return repoDir;
	}

}