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

import com.liferay.lugbot.api.LugbotConfig;
import com.liferay.lugbot.api.ProposalDTO;
import com.liferay.lugbot.api.UpgradeProvider;
import com.liferay.lugbot.api.util.GitFunctions;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.liferay.lugbot.api.util.LogFunctions.logError;

/**
 * @author Rafael Oliveira
 */
@Component(name = "spring-mvc-portlet-create-modules")
public class SpringMVCPortletCreateModulesProvider implements UpgradeProvider {

	@Activate
	public void activate(BundleContext bundleContext) {

		LoggerFactory loggerFactory = bundleContext.getService(bundleContext.getServiceReference(LoggerFactory.class));

		_logger = loggerFactory.getLogger(SpringMVCPortletCreateModulesProvider.class);
	}

	@Override
	public List<String> computePossibleUpgrades(Path repoPath, LugbotConfig lugbotConfig) {
		return Collections.singletonList("SpringMVCPortletCreateModules");
	}

	@Override
	public Optional<ProposalDTO> provideUpgrade(Path repoPath, LugbotConfig lugbotConfig, String upgradeName) {

		try {

			Path workspacePath = repoPath.resolve(lugbotConfig.tasks.upgrade.workspacePath);
			Path modulesPath = workspacePath.resolve("modules");

			List<String> pluginNames = lugbotConfig.tasks.upgrade.plugins;

			String currentBranchName = GitFunctions.getCurrentBranchName(repoPath);
			pluginNames.forEach(pluginName -> {
				try {

					_createModules(modulesPath, pluginName);
				} catch (Exception e) {
					logError(_logger, e);
				}
			});

			return Optional.of(
						new ProposalDTO(
							"SpringMVCPortletMigradeCode", "SpringMVCPortlet [Migrate Code]", "required",
							"SpringMVCPortlet [Migrate Code]", "", currentBranchName));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		return Optional.empty();
	}

	private Optional<Path> _createModules(Path toPath, String moduleName) throws Exception {
		try {
			SpringMVCPortletHelper.unzipRepo("spring-mvc-portlet-7.2-standalone-template.zip", toPath, moduleName);
		}
		catch (Exception e) {
			logError(_logger, e);
		}

		return Optional.of(toPath);
	}

	private Logger _logger;

}
