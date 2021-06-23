package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Utility class for unpacking data obtained from the given {@link URL} or {@link InputStream}
 */
public final class CompressionUtil {

    private CompressionUtil() {}

    public static void unTarGz(URL url, FilePath pathOutput) throws IOException {
        try (final InputStream inputStream = url.openStream()) {
            unTarGz(inputStream, pathOutput);
        }
    }

    public static void unTarGz(InputStream inputStream, FilePath pathOutput) throws IOException {
        try {
            pathOutput.untarFrom(inputStream, FilePath.TarCompression.GZIP);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Thread interrupted while trying to uncompress the file!");
        }
    }

    public static void unZip(URL url, FilePath pathOutput) throws IOException {
        try (final InputStream inputStream = url.openStream()) {
            unZip(inputStream, pathOutput);
        }
    }

    public static void unZip(InputStream inputStream, FilePath pathOutput) throws IOException {
        try {
            pathOutput.unzipFrom(inputStream);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Thread interrupted while trying to uncompress the file!");
        }
    }
}
