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
package de.flapdoodle.embed.process.archives;

import de.flapdoodle.embed.process.config.store.FileSet;
import de.flapdoodle.embed.process.config.store.FileType;
import de.flapdoodle.embed.process.extract.AbstractExtractor;
import de.flapdoodle.embed.process.extract.Archive;
import de.flapdoodle.embed.process.extract.CommonsArchiveEntryAdapter;
import de.flapdoodle.embed.process.io.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractExtractFileSet implements ExtractFileSet {

	private static Logger _logger= LoggerFactory.getLogger(AbstractExtractor.class);

	protected abstract Archive.Wrapper archiveStream(Path source) throws IOException;

	private Archive.Wrapper archiveStreamWithExceptionHint(Path source) throws IOException {
		try {
			return archiveStream(source);
		} catch (IOException iox) {
			_logger.warn("\n--------------------------\n"
				+ "If you get this exception more than once, you should check if the file is corrupt.\n"
				+ "If you remove the file ({}), it will be downloaded again.\n"
				+ "--------------------------", source.toAbsolutePath(), iox);
			throw new IOException("File "+source.toAbsolutePath(),iox);
		}
	}

	@Override
	public ExtractedFileSet extract(Path destination, Path archive, FileSet filesToExtract) throws IOException {
		ImmutableExtractedFileSet.Builder builder = ExtractedFileSet.builder(destination);

		Archive.Wrapper wrapper = archiveStreamWithExceptionHint(archive);
		Tracker tracker = new Tracker(filesToExtract);

		try {
			org.apache.commons.compress.archivers.ArchiveEntry archiveEntry;
			while ((archiveEntry = wrapper.getNextEntry()) != null) {
				Optional<FileSet.Entry> matchingEntry = tracker.find(new CommonsArchiveEntryAdapter(archiveEntry));
				if (matchingEntry.isPresent()) {
					if (wrapper.canReadEntryData(archiveEntry)) {
						long size = archiveEntry.getSize();
						FileType type = matchingEntry.get().type();
						Path dest = destination.resolve(matchingEntry.get().destination());
						File output = dest.toFile();
						Files.write(wrapper.asStream(archiveEntry), size, output);

						if (type==FileType.Executable) {
							builder.executable(dest);
							if (!output.setExecutable(true)) {
								throw new IllegalArgumentException("could not set executable flag on "+output);
							}
						} else {
							builder.addLibraryFiles(dest);
						}
					} else {
						throw new IllegalArgumentException("could not read "+archiveEntry);
					}
				}
				if (tracker.nothingLeft()) {
					break;
				}
			}

		} finally {
			wrapper.close();
		}

		return builder.build();
	}

	static class Tracker {
		private final ArrayList<FileSet.Entry> files;

		public Tracker(FileSet fileSet) {
			this.files = new ArrayList<>(fileSet.entries());
		}

		public boolean nothingLeft() {
			return files.isEmpty();
		}

		public Optional<FileSet.Entry> find(Archive.Entry entry) {
			Optional<FileSet.Entry> ret = Optional.empty();

			if (!entry.isDirectory()) {
				ret = findMatchingEntry(files, entry);

				if (ret.isPresent()) {
					files.remove(ret.get());
				}
			}
			return ret;
		}

	}


	static Optional<FileSet.Entry> findMatchingEntry(List<FileSet.Entry> files, Archive.Entry entry) {
		return files.stream()
			.filter(e -> e.matchingPattern().matcher(entry.getName()).matches())
			.findFirst();
	}
}
