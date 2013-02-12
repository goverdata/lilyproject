/*
 * Copyright 2007 Outerthought bvba and Schaubroeck nv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kauriproject.runtime.repository;

import java.util.List;

public class ArtifactNotFoundException extends Exception {
    private final String groupId;
    private final String artifactId;
    private final String classifier;
    private final String version;
    private final List<String> searchLocations;

    public ArtifactNotFoundException(String groupId, String artifactId, String classifier, String version, List<String> searchLocations) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
        this.version = version;
        this.searchLocations = searchLocations;
    }


    public String getMessage() {
        StringBuilder locations = new StringBuilder();
        for (String searchLocation : searchLocations) {
            if (locations.length() > 0)
                locations.append(", ");
            locations.append(searchLocation);
        }

        return "Artifact " + artifactId + " not found, searched at: " + locations;
    }
}
