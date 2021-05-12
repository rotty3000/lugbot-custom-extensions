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

import static com.liferay.lugbot.custom.extension.ProviderTester.contains;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liferay.ide.upgrade.plan.core.UpgradeProblem;
import com.liferay.ide.upgrade.problems.core.AutoFileMigrator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.service.ServiceExtension;

/**
 * @author Gregory Amerson
 */
@ExtendWith(ServiceExtension.class)
public class SpringMvcPortletMigratorTest {

	@Test
	public void testSpringMvcPortletMigrator(@TempDir Path tempDir) throws Exception {
		Path testPath = tempDir.resolve("HelloWorld.java");

		String source = _read(SpringMvcPortletMigratorTest.class.getResourceAsStream("HelloWorld.txt"));

		Files.write(testPath, source.getBytes());

		File testFile = testPath.toFile();

		assertTrue(testFile.exists());

		assertFalse(contains(testPath, "Hello, World", false));

		List<UpgradeProblem> upgradeProblems = springMvcPortletMigrator.analyze(testFile);

		assertEquals(1, upgradeProblems.size());

		UpgradeProblem upgradeProblem = upgradeProblems.get(0);

		assertEquals(55, upgradeProblem.getStartOffset());

		assertEquals(61, upgradeProblem.getEndOffset());

		int corrected = springMvcPortletMigrator.correctProblems(testFile, upgradeProblems);

		assertEquals(1, corrected);

		assertTrue(contains(testPath, "Hello, World", false));
	}

	@InjectService(filter = "(component.name=%s)", filterArguments = "spring-mvc-portlet-migrator")
	public AutoFileMigrator springMvcPortletMigrator;

	private static String _read(InputStream inputStream) throws IOException {
		byte[] buffer = new byte[8192];
		int offset = 0;

		while (true) {
			int count = inputStream.read(buffer, offset, buffer.length - offset);

			if (count == -1) {
				break;
			}

			offset += count;

			if (offset == buffer.length) {
				byte[] newBuffer = new byte[buffer.length << 1];

				System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);

				buffer = newBuffer;
			}
		}

		if (offset == 0) {
			return "";
		}

		return new String(buffer, 0, offset, "UTF-8");
	}

}