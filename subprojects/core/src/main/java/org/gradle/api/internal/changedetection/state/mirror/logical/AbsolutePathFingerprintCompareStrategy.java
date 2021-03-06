/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AbsolutePathFingerprintCompareStrategy implements FingerprintCompareStrategy.Impl {

    @Override
    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        Set<String> unaccountedForPreviousSnapshots = new LinkedHashSet<String>(previous.keySet());

        for (Map.Entry<String, NormalizedFileSnapshot> currentEntry : current.entrySet()) {
            String currentAbsolutePath = currentEntry.getKey();
            NormalizedFileSnapshot currentNormalizedSnapshot = currentEntry.getValue();
            FileContentSnapshot currentSnapshot = currentNormalizedSnapshot.getSnapshot();
            if (unaccountedForPreviousSnapshots.remove(currentAbsolutePath)) {
                NormalizedFileSnapshot previousNormalizedSnapshot = previous.get(currentAbsolutePath);
                FileContentSnapshot previousSnapshot = previousNormalizedSnapshot.getSnapshot();
                if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                    if (!visitor.visitChange(FileChange.modified(currentAbsolutePath, propertyTitle, previousSnapshot.getType(), currentSnapshot.getType()))) {
                        return false;
                    }
                }
                // else, unchanged; check next file
            } else if (includeAdded) {
                if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentSnapshot.getType()))) {
                    return false;
                }
            }
        }

        for (String previousAbsolutePath : unaccountedForPreviousSnapshots) {
            if (!visitor.visitChange(FileChange.removed(previousAbsolutePath, propertyTitle, previous.get(previousAbsolutePath).getSnapshot().getType()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots) {
        NormalizedPathFingerprintCompareStrategy.appendSortedToHasher(hasher, snapshots);
    }
}
