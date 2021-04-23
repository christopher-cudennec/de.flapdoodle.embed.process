/**
 * Copyright (C) 2011
 *   Michael Mosmann <michael@mosmann.de>
 *   Martin Jöhren <m.joehren@googlemail.com>
 *
 * with contributions from
 * 	konstantin-ba@github,
	Archimedes Trajano (trajano@github),
	Kevin D. Keck (kdkeck@github),
	Ben McCann (benmccann@github)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.flapdoodle.embed.process.runtime;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import de.flapdoodle.embed.process.collections.Collections;
import de.flapdoodle.embed.process.config.ISupportConfig;
import de.flapdoodle.embed.process.config.process.ProcessConfig;
import de.flapdoodle.embed.process.distribution.Platform;
import de.flapdoodle.embed.process.io.IStreamProcessor;
import de.flapdoodle.embed.process.io.LogWatchStreamProcessor;
import de.flapdoodle.embed.process.io.Processors;
import de.flapdoodle.embed.process.io.StreamToLineProcessor;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;

public abstract class Processes {

	private static Logger logger = LoggerFactory.getLogger(ProcessControl.class);

	private static final PidHelper PID_HELPER;

	static {
		if (processHasPidMethod()) {
			PID_HELPER = PidHelper.PID_METHOD;
		} else {
			PID_HELPER = PidHelper.LEGACY;
		}
	}

	private static boolean processHasPidMethod() {
		try {
			Process.class.getMethod("pid");
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	private Processes() {
		// no instance
	}

	public static Long processId(Process process) {
		return PID_HELPER.getPid(process);
	}

	private static Long unixLikeProcessId(Process process) {
		Class<?> clazz = process.getClass();
		try {
			if (SystemUtils.IS_OS_UNIX) {
				Field pidField = clazz.getDeclaredField("pid");
				pidField.setAccessible(true);
				Object value = pidField.get(process);
				if (value instanceof Integer) {
					logger.debug("Detected pid: {}", value);
					return ((Integer) value).longValue();
				}
			}
		} catch (SecurityException sx) {
			logger.debug("Could not determine pid", sx);
		} catch (NoSuchFieldException e) {
			logger.debug("Could not determine pid", e);
		} catch (IllegalArgumentException e) {
			logger.debug("Could not determine pid", e);
		} catch (IllegalAccessException e) {
			logger.debug("Could not determine pid", e);
		}
		return null;
	}

	/**
	 * @see http://www.golesny.de/p/code/javagetpid
	 *
	 * @return
	 */
	private static Long windowsProcessId(Process process) {
		if (process.getClass().getName().equals("java.lang.Win32Process")
				|| process.getClass().getName().equals("java.lang.ProcessImpl")) {
			/* determine the pid on windows plattforms */
			try {
				Field f = process.getClass().getDeclaredField("handle");
				f.setAccessible(true);
				long handl = f.getLong(process);

				Kernel32 kernel = Kernel32.INSTANCE;
				WinNT.HANDLE handle = new WinNT.HANDLE();
				handle.setPointer(Pointer.createConstant(handl));
				int ret = kernel.GetProcessId(handle);
				logger.debug("Detected pid: {}", ret);
				return Long.valueOf(ret);
			} catch (Throwable e) {
				logger.debug("Could not determine pid", e);
			}
		}
		return null;
	}

	public static boolean killProcess(ISupportConfig support,Platform platform, IStreamProcessor output, long pid) {
		if (platform.isUnixLike()) {
			return ProcessControl.executeCommandLine(support, "[kill process]",
					new ProcessConfig(Collections.newArrayList("kill", "-2", "" + pid), output));
		}
		return false;
	}

	public static boolean termProcess(ISupportConfig support,Platform platform, IStreamProcessor output, long pid) {
	    if (platform.isUnixLike()) {
		return ProcessControl.executeCommandLine(support, "[term process]",
			new ProcessConfig(Collections.newArrayList("kill", "" + pid), output));
	    }
	    return false;
	}

	public static boolean tryKillProcess(ISupportConfig support,Platform platform, IStreamProcessor output, long pid) {
		if (platform == Platform.Windows) {
			return ProcessControl.executeCommandLine(support, "[taskkill process]",
					new ProcessConfig(Collections.newArrayList("taskkill", "/F", "/pid", "" + pid), output));
		}
		return false;
	}

	public static boolean isProcessRunning(Platform platform, long pid) {

		try {
			final Process pidof;
			if (platform.isUnixLike()) {
				pidof = Runtime.getRuntime().exec(
						new String[] { "kill", "-0", "" + pid });
				return pidof.waitFor() == 0;
			} else {
				// windows
				// process might be in either NOT RESPONDING due to
				// firewall blocking, or could be RUNNING
				final String[] cmd = { "tasklist.exe",
						"/FI", "PID eq " + pid ,"/FO", "CSV" };
				logger.trace("Command: {}", Arrays.asList(cmd));
				ProcessBuilder processBuilder = ProcessControl
						.newProcessBuilder(Arrays.asList(cmd), true);
				Process process = processBuilder.start();
				// look for the PID in the output, pass it in for 'success' state
				LogWatchStreamProcessor logWatch = new LogWatchStreamProcessor(""+pid,
					new HashSet<String>(), StreamToLineProcessor.wrap(Processors.silent()));
				try {
					Processors.connect(new InputStreamReader(process.getInputStream()), logWatch);
					logWatch.waitForResult(2000);
					logger.trace("logWatch output: {}", logWatch.getOutput());
					return logWatch.isInitWithSuccess();
				} finally {
					logWatch.markResultAsRetrieved();
				}
			}

		} catch (IOException | InterruptedException e) {
			logger.error("Trying to get process status", e);
		}
		return false;
	}

	private enum PidHelper {

		PID_METHOD {
			@Override
			Long getPid(Process process) {
				try {
					Method pid = Process.class.getMethod("pid");
					return (Long) pid.invoke(process);
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
		},
		LEGACY {
			@Override
			Long getPid(Process process) {
				Long pid = unixLikeProcessId(process);
				if (pid == null) {
					pid = windowsProcessId(process);
				}
				return pid;
			}
		};

		abstract Long getPid(Process process);
	}
}
