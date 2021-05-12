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

import com.liferay.ide.upgrade.plan.core.UpgradeProblem;
import com.liferay.ide.upgrade.problems.core.FileMigrator;
import com.liferay.ide.upgrade.problems.core.FileSearchResult;
import com.liferay.ide.upgrade.problems.core.SourceFile;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.Path;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;

/**
 * @author Gregory Amerson
 * @author Simon Jiang
 */
public abstract class AbstractFileMigrator<T extends SourceFile> implements FileMigrator {

	public AbstractFileMigrator(Class<T> type) {
		this.type = type;
	}

	@Activate
	public void activate(ComponentContext ctx) {
		context = ctx.getBundleContext();

		Dictionary<String, Object> properties = ctx.getProperties();

		String fileExtensionsValue = _safeGet(properties, "file.extensions");

		fileExtentions = Arrays.asList(fileExtensionsValue.split(","));

		problemTitle = _safeGet(properties, "problem.title");
		problemSummary = _safeGet(properties, "problem.summary");
		problemTickets = _safeGet(properties, "problem.tickets");
		sectionKey = _safeGet(properties, "problem.section");

		String versionValue = _safeGet(properties, "version");

		if (versionValue.isEmpty()) {
			version = versionValue;
		}
		else {
			VersionRange versionRange = new VersionRange(versionValue);

			Version left = versionRange.getLeft();

			version = left.getMajor() + "." + left.getMinor();
		}
	}

	@Override
	public List<UpgradeProblem> analyze(File file) {
		List<UpgradeProblem> problems = new ArrayList<>();

		Path path = new Path(file.getAbsolutePath());

		String fileExtension = path.getFileExtension();

		List<FileSearchResult> searchResults = searchFile(file, createFileService(type, file, fileExtension));

		for (FileSearchResult searchResult : searchResults) {
			if (searchResult != null) {
				problems.add(
					new UpgradeProblem(
						problemTitle, problemSummary, fileExtension, problemTickets, version, file,
						searchResult.startLine, searchResult.startOffset, searchResult.endOffset, "html content",
						searchResult.autoCorrectContext, UpgradeProblem.STATUS_NOT_RESOLVED,
						UpgradeProblem.DEFAULT_MARKER_ID, UpgradeProblem.MARKER_ERROR));
			}
		}

		return problems;
	}

	@Override
	public int reportProblems(File file, Collection<UpgradeProblem> upgradeProblems) {
		Path path = new Path(file.getAbsolutePath());

		SourceFile sourceFile = createFileService(type, file, path.getFileExtension());

		sourceFile.setFile(file);

		return upgradeProblems.stream(
		).map(
			problem -> {
				try {
					sourceFile.appendComment(problem.getLineNumber(), "FIXME: " + problem.getTitle());

					return 1;
				}
				catch (IOException ioe) {
					ioe.printStackTrace(System.err);
				}

				return 0;
			}
		).reduce(
			0, Integer::sum
		);
	}

	protected T createFileService(Class<T> type, File file, String fileExtension) {
		try {
			Collection<ServiceReference<T>> refs = context.getServiceReferences(
				type, "(file.extension=" + fileExtension + ")");

			if ((refs != null) && !refs.isEmpty()) {
				Iterator<ServiceReference<T>> iterator = refs.iterator();

				T fileCheckerFile = type.cast(context.getService(iterator.next()));

				if (fileCheckerFile == null) {
					throw new IllegalArgumentException(
						"Could not find " + type.getSimpleName() + " service for specified file " + file.getName());
				}

				fileCheckerFile.setFile(file);

				return fileCheckerFile;
			}
		}
		catch (InvalidSyntaxException ise) {
		}

		return null;
	}

	protected abstract List<FileSearchResult> searchFile(File file, T fileChecker);

	protected BundleContext context;
	protected List<String> fileExtentions;
	protected String problemSummary;
	protected String problemTickets;
	protected String problemTitle;
	protected String sectionKey;
	protected final Class<T> type;
	protected String version;

	private String _safeGet(Dictionary<String, Object> properties, String key) {
		return Optional.ofNullable(
			properties.get(key)
		).map(
			String::valueOf
		).orElse(
			""
		);
	}

}