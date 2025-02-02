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
package de.flapdoodle.embed.process.config.io;

import de.flapdoodle.embed.process.io.Processors;
import de.flapdoodle.embed.process.io.Slf4jLevel;
import de.flapdoodle.embed.process.io.StreamProcessor;

/**
 * helper class to fix some spring boot issues
 * @see de.flapdoodle.embed.process.config.process.ProcessOutput#builder()
 */
@Deprecated
public class ProcessOutput implements de.flapdoodle.embed.process.config.process.ProcessOutput {

	private final StreamProcessor output;
	private final StreamProcessor error;
	private final StreamProcessor commands;

	public ProcessOutput(StreamProcessor output, StreamProcessor error, StreamProcessor commands) {
		this.output = output;
		this.error = error;
		this.commands = commands;
	}

	@Override
	public StreamProcessor output() {
		return output;
	}

	@Override
	public StreamProcessor error() {
		return error;
	}
	
	@Override
	public StreamProcessor commands() {
		return commands;
	}

	public static ProcessOutput namedConsole(String label) {
		return new ProcessOutput(
			Processors.namedConsole("["+label+" output]"),
			Processors.namedConsole("["+label+" error]"),
			Processors.console()
		);
	}

	public static ProcessOutput named(String label, org.slf4j.Logger logger) {
		return new ProcessOutput(
			Processors.named("["+label+" output]", Processors.logTo(logger, Slf4jLevel.INFO)),
			Processors.named("["+label+" error]", Processors.logTo(logger, Slf4jLevel.ERROR)),
			Processors.logTo(logger, Slf4jLevel.DEBUG));
	}
}
