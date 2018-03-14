/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.local;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;
import org.gradle.util.RelativePathUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * File store that accepts the target path as the key for the entry.
 *
 * This implementation is explicitly NOT THREAD SAFE. Concurrent access must be organised externally.
 * <p>
 * There is always at most one entry for a given key for this file store. If an entry already exists at the given path, it will be overwritten.
 * Paths can contain directory components, which will be created on demand.
 * <p>
 * This file store is self repairing in so far that any files partially written before a fatal error will be ignored and
 * removed at a later time.
 * <p>
 * This file store also provides searching via relative ant path patterns.
 */
@NonNullApi
public class DefaultPathKeyFileStore implements PathKeyFileStore {

    /*
        When writing a file into the filestore a marker file with this suffix is written alongside,
        then removed after the write. This is used to detect partially written files (due to a serious crash)
        and to silently clean them.
     */
    private static final String IN_PROGRESS_MARKER_FILE = ".fslck";

    final Marker marker;
    private final File baseDir;

    public DefaultPathKeyFileStore(File baseDir) {
        this.baseDir = baseDir;
        try {
            this.marker = new Marker(new File(baseDir, IN_PROGRESS_MARKER_FILE));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    protected File getBaseDir() {
        return baseDir;
    }

    private File getFile(String path) {
        return new File(baseDir, path);
    }

    private File getFileWhileCleaningInProgress(String path) {
        File file = getFile(path);
        File markerFile = getInProgressMarkerFile();
        if (markerFile != null) {
            deleteFileQuietly(markerFile);
        }
        return file;
    }

    @Override
    public LocallyAvailableResource add(final String path, final Action<File> addAction) {
        try {
            return doAdd(path, new Action<File>() {
                @Override
                public void execute(File file) {
                    try {
                        addAction.execute(file);
                    } catch (Throwable e) {
                        throw new FileStoreAddActionException(String.format("Failed to add into filestore '%s' at '%s' ", getBaseDir().getAbsolutePath(), path), e);
                    }
                }
            });
        } catch (FileStoreAddActionException e) {
            throw e;
        } catch (Throwable e) {
            throw new FileStoreException(String.format("Failed to add into filestore '%s' at '%s' ", getBaseDir().getAbsolutePath(), path), e);
        }
    }

    @Override
    public LocallyAvailableResource move(String path, final File source) {
        if (!source.exists()) {
            throw new FileStoreException(String.format("Cannot move '%s' into filestore @ '%s' as it does not exist", source, path));
        }

        try {
            return doAdd(path, new Action<File>() {
                public void execute(File file) {
                    if (source.isDirectory()) {
                        GFileUtils.moveExistingDirectory(source, file);
                    } else {
                        GFileUtils.moveExistingFile(source, file);
                    }
                }
            });
        } catch (Throwable e) {
            throw new FileStoreException(String.format("Failed to move file '%s' into filestore at '%s' ", source, path), e);
        }
    }

    private LocallyAvailableResource doAdd(String path, Action<File> action) {
        File destination = getFile(path);
        doAdd(path, destination, action);
        return entryAt(path);
    }

    protected void doAdd(String path, File destination, Action<File> action) {
        GFileUtils.parentMkdirs(destination);
        marker.startWrite(path);
        try {
            FileUtils.deleteQuietly(destination);
            action.execute(destination);
        } catch (Throwable t) {
            FileUtils.deleteQuietly(destination);
            throw UncheckedException.throwAsUncheckedException(t);
        } finally {
            marker.endWrite();
        }
    }

    @Override
    public Set<? extends LocallyAvailableResource> search(String pattern) {
        if (!getBaseDir().exists()) {
            return Collections.emptySet();
        }

        final Set<LocallyAvailableResource> entries = new HashSet<LocallyAvailableResource>();
        final File markerFile = getInProgressMarkerFile();
        findFiles(pattern).visit(new EmptyFileVisitor() {
            public void visitFile(FileVisitDetails fileDetails) {
                final File file = fileDetails.getFile();
                if (!file.getName().equals(IN_PROGRESS_MARKER_FILE)) {
                    // We cannot clean in progress markers, or in progress files here because
                    // the file system visitor stuff can't handle the file system mutating while visiting
                    if (markerFile == null || !file.equals(markerFile)) {
                        entries.add(entryAt(file));
                    }
                }
            }
        });

        return entries;
    }

    @Nullable
    private File getInProgressMarkerFile() {
        String writtenFile = marker.getWrittenFile();
        if (writtenFile != null) {
            return new File(baseDir, writtenFile);
        }
        return null;
    }

    private MinimalFileTree findFiles(String pattern) {
        return new SingleIncludePatternFileTree(baseDir, pattern);
    }

    protected LocallyAvailableResource entryAt(File file) {
        return entryAt(RelativePathUtil.relativePath(baseDir, file));
    }

    protected LocallyAvailableResource entryAt(final String path) {
        return new DefaultLocallyAvailableResource(getFile(path));
    }

    @Override
    public LocallyAvailableResource get(String key) {
        final File file = getFileWhileCleaningInProgress(key);
        if (file.exists()) {
            return new DefaultLocallyAvailableResource(file);
        } else {
            return null;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void deleteFileQuietly(File file) {
        file.delete();
    }

    static class Marker {
        private static final int MAX_LENGTH = 4096;
        private final MappedByteBuffer buffer;

        private Marker(File markerFile) throws IOException {
            if (!markerFile.exists() && markerFile.getParentFile().mkdirs()) {
                Files.write(new byte[MAX_LENGTH], markerFile);
            }
            buffer = Files.map(markerFile, FileChannel.MapMode.READ_WRITE, MAX_LENGTH);
        }

        public void startWrite(String path) {
            try {
                byte[] arr = path.getBytes("UTF-8");
                if (arr.length + 4 > MAX_LENGTH) {
                    throw new UnsupportedOperationException("Path too long: " + path);
                }
                buffer.clear();
                buffer.putInt(arr.length);
                buffer.put(arr);
                buffer.force();
            } catch (UnsupportedEncodingException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        public void endWrite() {
            buffer.putInt(0, 0);
        }

        @Nullable
        public String getWrittenFile() {
            buffer.clear();
            int len = buffer.getInt();
            if (len == 0) {
                return null;
            }
            byte[] tmp = new byte[len];
            buffer.get(tmp);
            try {
                return new String(tmp, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
