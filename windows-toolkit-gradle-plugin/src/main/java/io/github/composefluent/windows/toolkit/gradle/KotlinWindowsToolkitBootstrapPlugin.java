package io.github.composefluent.windows.toolkit.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Keeps an inactive plugin declaration usable in builds running on JDK 21. */
public final class KotlinWindowsToolkitBootstrapPlugin implements Plugin<Project> {
    @Override
    @SuppressWarnings("unchecked")
    public void apply(Project project) {
        if (Runtime.version().feature() < 25) {
            throw new GradleException(
                "The Windows toolkit requires JDK 25 or newer when applied. "
                    + "Run Gradle on JDK 25 to enable WinRT/WinUI; "
                    + "an inactive plugins { id(\"io.github.compose-fluent.windows-toolkit\") apply false } "
                    + "declaration is supported on JDK 21."
            );
        }
        try {
            Class<?> implementation = Class.forName(
                "io.github.composefluent.windows.toolkit.gradle.KotlinWindowsToolkitPlugin",
                true,
                getClass().getClassLoader()
            );
            ((Plugin<Project>) implementation.getDeclaredConstructor().newInstance()).apply(project);
        } catch (ReflectiveOperationException exception) {
            throw new GradleException("Could not load the Windows toolkit implementation.", exception);
        }
    }
}
