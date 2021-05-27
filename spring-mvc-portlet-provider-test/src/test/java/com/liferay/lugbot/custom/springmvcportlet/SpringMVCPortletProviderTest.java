package com.liferay.lugbot.custom.springmvcportlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.junit5.service.ServiceExtension;

import com.liferay.lugbot.api.LugbotConfig;
import com.liferay.lugbot.api.ProposalDTO;
import com.liferay.lugbot.api.UpgradeProvider;

/**
 * @author Rafael Oliveira
 */
@ExtendWith(ServiceExtension.class)
public class SpringMVCPortletProviderTest {

	@Test
	public void testSpringMVCPortletUpgradeFrom62To72(@TempDir Path tempDir) throws Exception {
		tempDir.toFile().mkdirs();

		File repoDir = ZipFunctions.unzipTestRepo("workspace_springmvcportlet.zip", tempDir);

		Path repoPath = repoDir.toPath();

		LugbotConfig lugbotConfig = initLugbot();

		testProvider(springMVCPortletCreateModules, "SpringMVCPortletCreateModules", repoPath, lugbotConfig);

		testProvider(springMVCPortletMigrateCode, "SpringMVCPortletMigradeCode", repoPath, lugbotConfig);

		testProvider(springMVCPortletAutoCorrectBreakingChanges, "SpringMVCPortletAutoCorrectBreakingChanges", repoPath, lugbotConfig);
	}

	private LugbotConfig initLugbot() {
		LugbotConfig lugbotConfig = new LugbotConfig();
		lugbotConfig.tasks = new LugbotConfig.Tasks();
		lugbotConfig.tasks.upgrade = new LugbotConfig.CodeUpgrade();

		lugbotConfig.tasks.upgrade.currentVersion = "6.2";
		lugbotConfig.tasks.upgrade.upgradeVersion = "7.2";

		lugbotConfig.tasks.upgrade.pluginsSDKPath = "";
		lugbotConfig.tasks.upgrade.workspacePath = "7.2/";

		lugbotConfig.tasks.upgrade.plugins = Collections.singletonList("spring-mvc-portlet-sample");

		return lugbotConfig;
	}


	private void testProvider(UpgradeProvider provider, String providerName, Path repoPath, LugbotConfig lugbotConfig) {
		assertNotNull(provider);

		List<String> upgradeNames = provider.computePossibleUpgrades(repoPath, lugbotConfig);

		assertNotNull(upgradeNames);

		assertEquals(1, upgradeNames.size());

		assertEquals(providerName, upgradeNames.get(0));

		Optional<ProposalDTO> upgradeProposal = provider.provideUpgrade(repoPath, lugbotConfig, providerName);

		assertTrue(upgradeProposal.isPresent());
	}

	@InjectService(filter = "(component.name=%s)", filterArguments = "spring-mvc-portlet-create-modules")
	public UpgradeProvider springMVCPortletCreateModules;

	@InjectService(filter = "(component.name=%s)", filterArguments = "spring-mvc-portlet-migrate-code")
	public UpgradeProvider springMVCPortletMigrateCode;

	@InjectService(filter = "(component.name=%s)", filterArguments = "spring-mvc-portlet-auto-correct-breaking-changes")
	public UpgradeProvider springMVCPortletAutoCorrectBreakingChanges;

}
