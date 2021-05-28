package io.projectdiscovery.plugins.jenkins.nuclei;

/**
 * Class that maps the OS to supported Nuclei builds
 *
 * Possible values: https://github.com/golang/go/blob/master/src/go/build/syslist.go
 */
public enum SupportedOperatingSystem {
    Windows("windows"), MacOS("macOS"), Linux("linux");

    private final String value;
    SupportedOperatingSystem(final String value) {
        this.value = value;
    }

    public String getDisplayName() {
        return value;
    }

    public static SupportedOperatingSystem getType(String value) {
        final SupportedOperatingSystem result;
        if (value != null && !value.isEmpty()) {
            value = value.toLowerCase();

            if (value.contains("win")) {
                result = Windows;
            } else if (value.contains("nux")) {
                result = Linux;
            } else if (value.contains("mac") || value.contains("darwin")) {
                result = MacOS;
            } else {
                throw new IllegalArgumentException(String.format("The operating system '%s' is not supported or it's not mapped correctly!", value));
            }
        } else {
            throw new IllegalArgumentException("The operating system name should not be null or empty!");
        }
        return result;
    }
}
