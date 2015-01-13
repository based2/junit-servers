/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 <mickael.jeanroy@gmail.com>
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

package com.github.mjeanroy.junit.servers.servers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.lang.System.clearProperty;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;

/**
 * Partial implementation of an embedded server.
 * Subclasses should implement {@link #doStart()} and {@link #doStop()} methods.
 * Synchronization is already managed by this abstract implementation.
 */
public abstract class AbstractEmbeddedServer implements EmbeddedServer {

	/**
	 * Flag to keep server status.
	 * Server can be started if and only if status is equal to {@link ServerStatus#STOPPED}.
	 * Server can be stopped if and only if status is equal to {@link ServerStatus#STARTED}.
	 */
	private volatile ServerStatus status;

	/**
	 * Force specific port.
	 * If port is set to zero, then a random port will be used.
	 */
	protected final int port;

	/**
	 * Server path, default is '/'.
	 * This path must be used to query server.
	 */
	protected final String path;

	/**
	 * Webapp Path.
	 * In a "classic" java application, this path should be "src/main/webapp".
	 */
	protected final String webapp;

	/**
	 * Additional classpath directory path.
	 * This classpath entry will be scanned when server starts.
	 * It can be used to register compiled classes that can be needed to start
	 * jetty or tomcat.
	 */
	protected final String classpath;

	/**
	 * Environment properties to create when server starts and destroy when server stops.
	 */
	protected final Map<String, String> properties;

	/**
	 * Old properties used to restore initial environment properties values when server stops.
	 * It can be used to set a spring profile property or anything else.
	 */
	protected final Map<String, String> oldProperties;

	/**
	 * Hooks that will be invoked before and after server execution.
	 * It can be used to start other dependencies (embedded database, start remove web server etc.)
	 * before server starts, and shutdown these dependencies when server stops.
	 * Hooks will be executed in order.
	 * Hooks should be stateless.
	 */
	protected final List<Hook> hooks;

	// Lock used to synchronize start and stop tasks
	private final Object lock = new Object();

	/**
	 * Build default embedded server.
	 *
	 * @param configuration Server configuration.
	 */
	@SuppressWarnings("unchecked")
	protected <T extends AbstractEmbeddedServerConfiguration> AbstractEmbeddedServer(T configuration) {
		this.status = ServerStatus.STOPPED;
		this.port = configuration.getPort();
		this.path = configuration.getPath();
		this.webapp = configuration.getWebapp();
		this.classpath = configuration.getClasspath();

		this.oldProperties = new HashMap<>();
		this.properties = configuration.getEnvProperties();
		this.hooks = configuration.getHooks();
	}

	@Override
	public void start() {
		if (status != ServerStatus.STARTED) {
			synchronized (lock) {
				if (status != ServerStatus.STARTED) {
					status = ServerStatus.STARTING;
					initEnvironment();
					execHooks(true);
					doStart();
					status = ServerStatus.STARTED;

					// Server is fully initialized
					onStarted();
				}
			}
		}
	}

	@Override
	public void stop() {
		if (status != ServerStatus.STOPPED) {
			synchronized (lock) {
				if (status != ServerStatus.STOPPED) {
					status = ServerStatus.STOPPING;
					doStop();
					execHooks(false);
					destroyEnvironment();
					status = ServerStatus.STOPPED;
				}
			}
		}
	}

	@Override
	public boolean isStarted() {
		return status == ServerStatus.STARTED;
	}

	@Override
	public void restart() {
		stop();
		start();
	}

	@Override
	public String getPath() {
		return path;
	}

	/**
	 * Add custom environment properties.
	 * Initial property value will be store in {@link #oldProperties} map
	 * and will be restore later.
	 */
	private void initEnvironment() {
		for (Map.Entry<String, String> property : properties.entrySet()) {
			String name = property.getKey();
			String newValue = property.getValue();

			String oldValue = getProperty(property.getKey());
			oldProperties.put(name, oldValue);

			setProperty(name, newValue);
		}
	}

	/**
	 * Reset custom environment properties.
	 * Initial values stored in {@link #oldProperties} will be restored
	 * or clear.
	 */
	private void destroyEnvironment() {
		for (Map.Entry<String, String> property : properties.entrySet()) {
			String name = property.getKey();

			String oldValue = oldProperties.get(name);
			oldProperties.remove(name);

			if (oldValue == null) {
				clearProperty(name);
			} else {
				setProperty(name, oldValue);
			}
		}
	}

	/**
	 * Exec hooks phase.
	 *
	 * @param pre Phase to execute (true => pre ; false => post).
	 */
	private void execHooks(boolean pre) {
		for (Hook hook : hooks) {
			if (pre) {
				hook.pre(this);
			} else {
				hook.post(this);
			}
		}
	}

	private void onStarted() {
		for (Hook hook : hooks) {
			hook.onStarted(this, getServletContext());
		}
	}

	@Override
	public String getUrl() {
		int port = getPort();
		String path = getPath();
		if (!path.isEmpty() && path.charAt(0) != '/') {
			path = "/" + path;
		}

		return format("http://localhost:%s%s", port, path);
	}

	/**
	 * Start embedded server.
	 * Must block until server is fully started.
	 */
	protected abstract void doStart();

	/**
	 * Stop embedded server.
	 * Must block until server is fully stopped.
	 */
	protected abstract void doStop();
}
