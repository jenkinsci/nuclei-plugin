package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class NucleiBuilderHelper {

    private static final String NUCLEI_BINARY_NAME = "nuclei";

    private NucleiBuilderHelper() {}

    static String[] mergeCliArguments(List<String> mandatoryCommands, String additionalFlags) {
        return mergeCliArguments(mandatoryCommands.toArray(new String[0]), additionalFlags);
    }

    static String[] mergeCliArguments(String[] mandatoryCommands, String additionalFlags) {
        final String[] result;

        if (additionalFlags == null || additionalFlags.isEmpty()) {
            result = mandatoryCommands;
        } else {
            final Stream<String> additionalFlagStream = Arrays.stream(additionalFlags.split("(?= -)"))
                                                              .map(String::trim)
                                                              .flatMap(v -> Arrays.stream(v.split(" ", 2)).map(String::trim))
                                                              .map(v -> {
                                                                  final Predicate<String> tester = (character) -> v.startsWith(character) && v.endsWith(character);
                                                                  return tester.test("\"") || tester.test("'") ? v.substring(1, v.length() - 1) : v;
                                                              });
            result = Stream.concat(Arrays.stream(mandatoryCommands), additionalFlagStream).toArray(String[]::new);
        }

        return result;
    }

    static void runCommand(PrintStream logger, Launcher launcher, String[] command) {
        try {
            Launcher.ProcStarter procStarter = launcher.launch();

            Proc process = procStarter.cmds(command).stdout(logger).stderr(logger).start();

            process.join();
        } catch (IOException | InterruptedException e) {
            logger.println("Error while trying to run the following command: " + String.join(" ", command));
        }
    }

    static FilePath resolveFilePath(FilePath directory, String fileName) {
        final String absolutePath = directory.getRemote();

        final String resultPath = absolutePath.endsWith(File.separator) ? (absolutePath + fileName)
                                                                        : (absolutePath + File.separator + fileName);
        return new FilePath(directory.getChannel(), resultPath);
    }

    static String downloadTemplates(Launcher launcher, FilePath workingDirectory, String nucleiPath, PrintStream logger) {
        final String nucleiTemplatesPath = resolveFilePath(workingDirectory, "nuclei-templates").getRemote();
        runCommand(logger, launcher, new String[]{nucleiPath,
                                                  "-update-directory", nucleiTemplatesPath,
                                                  "-update-templates",
                                                  "-no-color"});
        return nucleiTemplatesPath;
    }

    static String prepareNucleiBinary(PrintStream logger, FilePath workingDirectory) {
        try {
            final SupportedOperatingSystem operatingSystem = SupportedOperatingSystem.getType(System.getProperty("os.name"));
            logger.println("Retrieved operating system: " + operatingSystem);

            final String nucleiBinaryName = operatingSystem == SupportedOperatingSystem.Windows ? NUCLEI_BINARY_NAME + ".exe"
                                                                                                : NUCLEI_BINARY_NAME;
            final FilePath nucleiPath = NucleiBuilderHelper.resolveFilePath(workingDirectory, nucleiBinaryName);

            if (nucleiPath.exists()) {
                final int fullPermissionToOwner = 0700;
                nucleiPath.chmod(fullPermissionToOwner);
            } else {
                final SupportedArchitecture architecture = SupportedArchitecture.getType(System.getProperty("os.arch"));
                downloadAndUnpackLatestNuclei(operatingSystem, architecture, workingDirectory);
            }

            return nucleiPath.getRemote();
        } catch (Exception e) {
            throw new IllegalStateException("Error while obtaining Nuclei binary.");
        }
    }

    static void downloadAndUnpackLatestNuclei(SupportedOperatingSystem operatingSystem, SupportedArchitecture architecture, FilePath workingDirectory) throws IOException {
        final URL downloadUrl = NucleiDownloader.extractDownloadUrl(operatingSystem, architecture);

        final String downloadFilePath = downloadUrl.getPath().toLowerCase();
        if (downloadFilePath.endsWith(".zip")) {
            CompressionUtil.unZip(downloadUrl, workingDirectory);
        } else if (downloadFilePath.endsWith(".tar.gz")) {
            CompressionUtil.unTarGz(downloadUrl, workingDirectory);
        } else {
            throw new IllegalStateException(String.format("Unsupported file type ('%s'). It should be '.tar.gz' or '.zip'!", downloadFilePath));
        }
    }

    static FilePath getWorkingDirectory(Launcher launcher, FilePath workspace, PrintStream logger) {
        final VirtualChannel virtualChannel = launcher.getChannel();
        if (virtualChannel == null) {
            throw new IllegalStateException("The agent does not support remote operations!");
        }

        final FilePath workingDirectory = new FilePath(virtualChannel, workspace.getRemote());
        logger.println("Working directory: " + workingDirectory);
        return workingDirectory;
    }
}
