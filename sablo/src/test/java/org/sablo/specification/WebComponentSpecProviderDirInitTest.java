/*
 * Copyright (C) 2026 Servoy BV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sablo.specification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.property.ICustomType;

public class WebComponentSpecProviderDirInitTest {
	private static final File COMPONENTS_DIR =
		new File("C:\\Users\\blokj\\git\\pivoy\\frontend\\packages\\@servoy\\bootstrapcomponents");

	@Before
	public void setUp() {
		WebComponentSpecProvider.disposeInstance();
	}

	@After
	public void tearDown() {
		WebComponentSpecProvider.disposeInstance();
	}

	@Test
	public void shouldLoadSpecsFromDirectoryReader() {
		IPackageReader[] packageReaders = new IPackageReader[] { new Package.DirPackageReader(COMPONENTS_DIR) };
		WebComponentSpecProvider.init(packageReaders, null);

		SpecProviderState state = WebComponentSpecProvider.getSpecProviderState();
		assertThat(WebComponentSpecProvider.isLoaded()).isTrue();
		assertThat(state.getPackageNames()).contains("bootstrapcomponents");

		Collection<String> bootstrapcomponents = state.getWebObjectsInPackage("bootstrapcomponents");
		assertThat(bootstrapcomponents).isNotEmpty();
		assertThat(bootstrapcomponents.size()).isEqualTo(25);

		WebObjectSpecification buttonSpecification = state.getWebObjectSpecification("bootstrapcomponents-button");
		assertThat(buttonSpecification).isNotNull();

		WebObjectSpecification accordionSpecification = state.getWebObjectSpecification("bootstrapcomponents-accordion");
		assertThat(accordionSpecification).isNotNull();
		Map<String, ICustomType<?>> customJSONProperties = accordionSpecification.getDeclaredCustomObjectTypes();
		assertThat(customJSONProperties).isNotNull();
		assertThat(customJSONProperties.keySet()).contains("tab");
	}
}
