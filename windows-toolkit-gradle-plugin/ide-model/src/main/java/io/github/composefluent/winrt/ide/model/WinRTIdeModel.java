package io.github.composefluent.winrt.ide.model;

import java.io.Serializable;
import java.util.List;

/** Gradle Tooling API contract. Contains configuration facts, never resolved build outputs or secrets. */
public interface WinRTIdeModel extends Serializable {
    int SCHEMA_VERSION = 1;

    int getSchemaVersion();
    boolean isEnabled();
    String getProjectPath();
    String getProjectDirectory();
    String getBuildDirectory();
    String getKotlinVersion();
    String getWindowsSdkVersion();
    List<SourceSet> getSourceSets();
    List<Target> getTargets();
    List<NuGetPackage> getNuGetPackages();
    List<String> getManifestFiles();

    interface SourceSet extends Serializable {
        String getName();
        List<String> getKotlinRoots();
        List<String> getDependsOn();
        /** Ordered from least to most specific, exactly as used by AppX resource staging. */
        List<String> getAppxResourceRoots();
    }

    interface Target extends Serializable {
        String getName();
        String getPlatform();
        List<String> getSourceSets();
    }

    interface NuGetPackage extends Serializable {
        String getId();
        String getVersion();
        boolean isGenerateProjection();
    }
}
