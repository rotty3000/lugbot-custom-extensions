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

package com.liferay.lugbot.custom.springmvcportlet;

import static com.liferay.lugbot.api.util.LogFunctions.logError;

import com.liferay.lugbot.custom.springmvcportlet.helper.FileFunctions;
import com.liferay.ide.upgrade.plan.core.UpgradeProblem;
import com.liferay.ide.upgrade.problems.core.AutoFileMigrator;
import com.liferay.lugbot.api.LugbotConfig;
import com.liferay.lugbot.api.ProposalCommentDTO;
import com.liferay.lugbot.api.ProposalDTO;
import com.liferay.lugbot.api.UpgradeProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import java.text.MessageFormat;

import java.util.*;
import java.util.stream.Collectors;


import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.Logger;

/**
 * @author Gregory Amerson
 */
@Component(name = "spring-mvc-portlet-auto-correct-breaking-changes")
public class SpringMVCPortletAutoCorrectBreakingChangesProvider implements UpgradeProvider {

	@Activate
	public void activate(ComponentContext componentContext) {
		this.componentContext = componentContext;
	}

	@Override
	public List<String> computePossibleUpgrades(Path repoPath, LugbotConfig lugbotConfig) {
		return Collections.singletonList("SpringMVCPortletAutoCorrectBreakingChanges");
	}

	@Override
	public Optional<ProposalDTO> provideUpgrade(Path repoPath, LugbotConfig lugbotConfig, String upgradeName) {
		Path workspacePath = Optional.of(
			lugbotConfig
		).filter(
			config -> config.tasks.upgrade.workspacePath != null
		).map(
			config -> repoPath.resolve(config.tasks.upgrade.workspacePath)
		).orElse(
			repoPath
		);

		Version currentVersion = new Version(getCurrentVersion(lugbotConfig));
		Version upgradeVersion = new Version(getUpgradeVersion(lugbotConfig));

		try {
			Collection<ServiceReference<AutoFileMigrator>> serviceReferences = componentContext.getBundleContext().getServiceReferences(
				AutoFileMigrator.class, null);

			List<ServiceReference<AutoFileMigrator>> refs = serviceReferences.stream(
			).filter(
				ref -> {

					return Optional.ofNullable(
						ref.getProperty("version")
					).map(
						Object::toString
					).map(
						VersionRange::valueOf
					).filter(
						range -> range.includes(upgradeVersion)
					).isPresent();
				}
			).collect(
				Collectors.toList()
			);

			Map<ServiceReference<AutoFileMigrator>, List<UpgradeProblem>> problems = _getUpgradeProblems(
				refs, workspacePath);

			if (!problems.isEmpty()) {

				Map<String, List<ProposalCommentDTO>> commitedUpgradeProblems = problems.keySet(
				).stream(
				).map(
					ref -> {
						AutoFileMigrator autoFileMigrator = componentContext.getBundleContext().getService(ref);

						List<UpgradeProblem> upgradeProblems = problems.get(ref);

						List<UpgradeProblem> correctedUpgradeProblems = upgradeProblems.stream(
						).map(
							upgradeProblem -> {
								try {
									int problemsCorrected = autoFileMigrator.correctProblems(
										upgradeProblem.getResource(), Collections.singleton(upgradeProblem));

									_logger.info(
										"\t{} corrected {} problems.", autoFileMigrator.getClass().getSimpleName(),
										problemsCorrected);

									if (problemsCorrected > 0) {
										return upgradeProblem;
									}
								}
								catch (Exception e) {
									logError(
										_logger, e, "Problem auto correcting file {}", upgradeProblem.getResource());
								}

								return null;
							}
						).filter(
							Objects::nonNull
						).collect(
							Collectors.toList()
						);

						return new AbstractMap.SimpleEntry<>(UUID.randomUUID().toString(), correctedUpgradeProblems);
					}
				).filter(
						Objects::nonNull
				).map(
						pair -> _toDTO(pair)
				).collect(
						Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
				);


				if (!commitedUpgradeProblems.isEmpty()) {
					StringBuilder sbDetails = new StringBuilder();

					commitedUpgradeProblems.entrySet(
					).forEach(
						entry -> {
							String name = entry.getKey();

							List<ProposalCommentDTO> upgradeProblems = entry.getValue();

							upgradeProblems.forEach(
								problem -> {
									sbDetails.append("- ");
									sbDetails.append(name);
								});
						}
					);


					return Optional.of(
						new ProposalDTO(
							"SpringMVCPortletAutoCorrectBreakingChanges", "autocorrect breaking changes", "required",
							MessageFormat.format(
								"Automatically fixed some breaking changes from Liferay {0} to {1}", currentVersion,
								upgradeVersion),
							"", "", commitedUpgradeProblems));
				}
			}
		}
		catch (Exception e) {
			logError(_logger, e, "Unable to get auto correct migrators.");
		}

		return Optional.empty();
	}

	private Map<ServiceReference<AutoFileMigrator>, List<UpgradeProblem>> _getUpgradeProblems(
		List<ServiceReference<AutoFileMigrator>> refs, Path repoPath) {

		Map<ServiceReference<AutoFileMigrator>, List<UpgradeProblem>> totalUpgradeProblems = new HashMap<>();

		FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (dir.endsWith(".git")) {
					return FileVisitResult.SKIP_SUBTREE;
				}

				if (dir.endsWith("WEB-INF/classes")) {
					return FileVisitResult.SKIP_SUBTREE;
				}

				if (dir.endsWith("WEB-INF/service")) {
					return FileVisitResult.SKIP_SUBTREE;
				}

				return super.preVisitDirectory(dir, attrs);
			}

			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
				File file = path.toFile();

				if (!file.isFile() || !attrs.isRegularFile() || (attrs.size() == 0)) {
					return super.visitFile(path, attrs);
				}

				String fileName = String.valueOf(path.getFileName());

				String extension = fileName.substring(fileName.lastIndexOf('.') + 1);

				if (Objects.equals(".java", extension)) {
					try (InputStream inputStream = Files.newInputStream(path)) {
						String content = FileFunctions.read(inputStream);

						if (content.contains("* @generated")) {
							return FileVisitResult.CONTINUE;
						}
					}
					catch (IOException e) {
						logError(_logger, e, "Problem encountered detecting generated java files.");
					}
				}

				refs.stream(
				).filter(
					ref -> {
						String fileExtensionsProp = (String)ref.getProperty("file.extensions");

						List<String> fileExtensions = Arrays.asList(fileExtensionsProp.split(","));

						return fileExtensions.contains(extension);
					}
				).forEach(
					ref -> {
						AutoFileMigrator autoFileMigrator = componentContext.getBundleContext().getService(ref);

						List<UpgradeProblem> upgradeProblems = autoFileMigrator.analyze(file);

						if (!upgradeProblems.isEmpty()) {
							_logger.info(
								"\t{} found {} breaking change problems for file {}", getComponentName(ref),
								upgradeProblems.size(), file.toString());

							List<UpgradeProblem> currentUpgradeProblems = totalUpgradeProblems.getOrDefault(
								ref, new ArrayList<>());

							currentUpgradeProblems.addAll(upgradeProblems);

							totalUpgradeProblems.put(ref, currentUpgradeProblems);
						}
					}
				);

				return super.visitFile(path, attrs);
			}

		};

		try {
			Files.walkFileTree(repoPath, visitor);
		}
		catch (IOException e) {
			logError(_logger, e, "Failure while walking repo.");
		}

		return totalUpgradeProblems;
	}

	private String getComponentName(ServiceReference<?> serviceReference) {
		return serviceReference.getProperty("component.name").toString();
	}

	private AbstractMap.SimpleEntry<String, List<ProposalCommentDTO>> _toDTO(AbstractMap.SimpleEntry<String, List<UpgradeProblem>> pair) {
		List<UpgradeProblem> upgradeProblems = pair.getValue();

		List<ProposalCommentDTO> dtos = upgradeProblems.stream(
		).map(
			problem -> {
				File file = problem.getResource();

				return new ProposalCommentDTO(
					problem.getTitle(), problem.getHtml(), file.getPath(), problem.getLineNumber());
			}
		).collect(
			Collectors.toList()
		);

		return new AbstractMap.SimpleEntry<>(pair.getKey(), dtos);
	}

	@Reference(service = org.osgi.service.log.LoggerFactory.class)
	private Logger _logger;

	private ComponentContext componentContext;

}
