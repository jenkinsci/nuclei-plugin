package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.FilePath;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CompressionUtil {

    private CompressionUtil() {}

    public static void unTarGz(final Path pathInput, final Path pathOutput) throws IOException {
        try (final InputStream inputStream = Files.newInputStream(pathInput)) {
            unTarGz(inputStream, pathOutput);
        }
    }

    public static void unTarGz(URL url, Path pathOutput) throws IOException {
        try (final InputStream inputStream = url.openStream()) {
            unTarGz(inputStream, pathOutput);
        }
    }

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

    public static void unTarGz(InputStream inputStream, Path pathOutput) throws IOException {
        try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             final GzipCompressorInputStream gzipCompressorInputStream = new GzipCompressorInputStream(bufferedInputStream);
             final TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipCompressorInputStream)) {

            ArchiveEntry archiveEntry;
            while ((archiveEntry = tarArchiveInputStream.getNextEntry()) != null) {
                final Path pathEntryOutput = pathOutput.resolve(archiveEntry.getName());
                if (archiveEntry.isDirectory()) {
                    if (!Files.exists(pathEntryOutput))
                        Files.createDirectory(pathEntryOutput);
                } else
                    Files.copy(tarArchiveInputStream, pathEntryOutput, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void unZip(URL url, Path pathOutput) throws IOException {
        try (final InputStream inputStream = url.openStream()) {
            unZip(inputStream, pathOutput);
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

    public static void unZip(InputStream inputStream, Path pathOutput) throws IOException {
        try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             final ZipInputStream tarArchiveInputStream = new ZipInputStream(bufferedInputStream)) {

            ZipEntry archiveEntry;
            while ((archiveEntry = tarArchiveInputStream.getNextEntry()) != null) {
                final Path pathEntryOutput = pathOutput.resolve(archiveEntry.getName());
                if (archiveEntry.isDirectory()) {
                    if (!Files.exists(pathEntryOutput))
                        Files.createDirectory(pathEntryOutput);
                } else
                    Files.copy(tarArchiveInputStream, pathEntryOutput, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
