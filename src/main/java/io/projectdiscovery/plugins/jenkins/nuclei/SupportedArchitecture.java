package io.projectdiscovery.plugins.jenkins.nuclei;

import java.util.stream.Stream;

/**
 * Class that maps the OS architecture to supported Nuclei builds
 *
 * Possible values: https://github.com/golang/go/blob/master/src/go/build/syslist.go
 */
public enum SupportedArchitecture {
    i386("386"), AMD64("amd64"), ARM64("arm64"), ARM("armv6");

    private final String value;
    SupportedArchitecture(final String value) {
        this.value = value;
    }

    public String getDisplayName() {
        return value;
    }

    public static SupportedArchitecture getType(final String value) {
        final SupportedArchitecture result;

        if (value != null && !value.isEmpty()) {
            if (Stream.of("mips", "ppc", "risc", "sparc", "wasm", "s390").anyMatch(value::contains)) {
                throw new IllegalArgumentException(String.format("Current architecture '%s' is not mapped correctly or is not supported!", value));
            } else if (value.contains("64")) {
                result = value.contains("arm") || value.contains("aarch") ? ARM64 : AMD64;
            } else if (value.contains("arm")) {
                result = ARM;
            } else {
                result = i386;
            }
        } else {
            throw new IllegalArgumentException("The architecture should not be null or empty!");
        }
        return result;
    }
}
