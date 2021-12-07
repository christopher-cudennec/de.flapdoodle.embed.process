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
package de.flapdoodle.embed.process.store;

import de.flapdoodle.embed.process.config.store.DownloadConfig;
import de.flapdoodle.embed.process.config.store.PackageResolver;
import de.flapdoodle.embed.process.distribution.Distribution;
import de.flapdoodle.embed.process.extract.*;
import de.flapdoodle.embed.process.io.directories.Directory;
import de.flapdoodle.embed.process.store.ImmutableArtifactStore.Builder;
import org.immutables.value.Value.Auxiliary;
import org.immutables.value.Value.Immutable;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Immutable
public abstract class ArtifactStore implements IArtifactStore {
	abstract DownloadConfig downloadConfig();

	abstract Directory tempDirFactory();

	abstract TempNaming executableNaming();

	abstract Downloader downloader();

	@Auxiliary
	public ArtifactStore with(Directory tempDirFactory, TempNaming executableNaming) {
		return ImmutableArtifactStore.copyOf(this)
				.withTempDirFactory(tempDirFactory)
				.withExecutableNaming(executableNaming);
	}
	
	@Deprecated
	public ArtifactStore executableNaming(TempNaming tempNaming) {
		return ImmutableArtifactStore.copyOf(this)
				.withExecutableNaming(tempNaming);
	}
	
	public CachingArtifactStore withCache() {
		return new CachingArtifactStore(this);
	}

		private String getDownloadUrl(DownloadConfig runtime, Distribution distribution) {
				return runtime.getDownloadPath().getPath(distribution) + runtime.getPackageResolver().packageFor(distribution).archivePath();
		}

		private boolean checkDistribution(Distribution distribution) throws IOException {
		return LocalArtifactStore.checkArtifact(downloadConfig(), distribution) || LocalArtifactStore
				.store(downloadConfig(), distribution, downloader().download(downloadConfig(), getDownloadUrl(downloadConfig(), distribution)));
	}

	@Override
	public Optional<ExtractedFileSet> extractFileSet(Distribution distribution) throws IOException {
		if (checkDistribution(distribution)) {
			PackageResolver packageResolver = downloadConfig().getPackageResolver();
			FilesToExtract toExtract = filesToExtract(distribution);

			Extractor extractor = Extractors.getExtractor(packageResolver.packageFor(distribution).archiveType());

			File artifact = LocalArtifactStore.getArtifact(downloadConfig(), distribution);
			return Optional.of(extractor.extract(artifact, toExtract));
		}
		return Optional.empty();
	}

	FilesToExtract filesToExtract(Distribution distribution) {
		return new FilesToExtract(tempDirFactory(), executableNaming(),
				downloadConfig().getPackageResolver().packageFor(distribution).fileSet());
	}

	@Override
	public void removeFileSet(Distribution distribution, ExtractedFileSet all) {
		ExtractedFileSets.delete(all);
	}

	public static Builder builder() {
		return ImmutableArtifactStore.builder();
	}
}
