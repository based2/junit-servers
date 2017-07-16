/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 <mickael.jeanroy@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.github.mjeanroy.junit.servers.samples.tomcat.webxml;

import com.github.mjeanroy.junit.servers.rules.TomcatServerRule;
import com.github.mjeanroy.junit.servers.tomcat.EmbeddedTomcat;
import com.github.mjeanroy.junit.servers.tomcat.EmbeddedTomcatConfiguration;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.io.FileFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexWithRulesTest {

	private static final String PATH = "samples/spring-webxml-tomcat-jsp/";

	private static EmbeddedTomcatConfiguration configuration() {
		try {
			String current = new File(".").getCanonicalPath();
			if (!current.endsWith("/")) {
				current += "/";
			}

			String path = current.endsWith(PATH) ? current : current + PATH;

			return EmbeddedTomcatConfiguration.builder()
					.withWebapp(path + "src/main/webapp")
					.withOverrideDescriptor("src/test/resources/web.xml")
					.withClasspath(path + "target/classes")
					.withParentClasspath(IndexWithRulesTest.class, new FileFilter() {
						@Override
						public boolean accept(File pathname) {
							return pathname.getName().startsWith("apache-jstl");
						}
					})
					.build();
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private static final EmbeddedTomcat tomcat = new EmbeddedTomcat(configuration());

	@ClassRule
	public static TomcatServerRule serverRule = new TomcatServerRule(tomcat);

	@Test
	public void it_should_have_an_index() {
		String message = serverRule.getClient()
			.prepareGet("/index")
			.execute()
			.body();

		assertThat(message).isNotEmpty().contains("Hello");
	}
}
