package io.aeron.rpc.versioning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a semantic version of a service.
 */
public class ServiceVersion implements Comparable<ServiceVersion> {
    private final int major;
    private final int minor;
    private final int patch;

    @JsonCreator
    public ServiceVersion(
            @JsonProperty("major") int major,
            @JsonProperty("minor") int minor,
            @JsonProperty("patch") int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static ServiceVersion parse(String version) {
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid version format: " + version);
        }
        return new ServiceVersion(
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        );
    }

    public boolean isCompatibleWith(ServiceVersion other) {
        // Major version must match exactly
        if (this.major != other.major) {
            return false;
        }
        // Minor version of the client must be less than or equal to the server
        if (this.minor > other.minor) {
            return false;
        }
        return true;
    }

    public boolean isBackwardCompatibleWith(ServiceVersion other) {
        // Only check major version for backward compatibility
        return this.major == other.major;
    }

    @Override
    public int compareTo(ServiceVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return String.format("%d.%d.%d", major, minor, patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceVersion that = (ServiceVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        return result;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public ServiceVersion nextMajor() {
        return new ServiceVersion(major + 1, 0, 0);
    }

    public ServiceVersion nextMinor() {
        return new ServiceVersion(major, minor + 1, 0);
    }

    public ServiceVersion nextPatch() {
        return new ServiceVersion(major, minor, patch + 1);
    }
}
