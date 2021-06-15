package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class NucleiBuilderHelper {

    private static final String NUCLEI_BINARY_NAME = "nuclei";

    private NucleiBuilderHelper() {
    }

    static void downloadAndUnpackLatestNuclei(SupportedOperatingSystem operatingSystem, SupportedArchitecture architecture, Path workingDirectory, PrintStream logger) throws IOException {
        final URL downloadUrl = NucleiDownloader.extractDownloadUrl(operatingSystem, architecture);
        logger.println("Extracted download URL: " + downloadUrl);

        final String downloadFilePath = downloadUrl.getPath().toLowerCase();
        if (downloadFilePath.endsWith(".zip")) {
            CompressionUtil.unZip(downloadUrl, workingDirectory);
        } else if (downloadFilePath.endsWith(".tar.gz")) {
            CompressionUtil.unTarGz(downloadUrl, workingDirectory);
        } else {
            throw new IllegalStateException(String.format("Unsupported file type ('%s'). It should be '.tar.gz' or '.zip'!", downloadFilePath));
        }
    }

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

    static Path prepareNucleiBinary(SupportedOperatingSystem operatingSystem, Launcher launcher, Path workingDirectory, PrintStream logger) throws IOException {
        final Path nucleiPath = operatingSystem == SupportedOperatingSystem.Windows ? workingDirectory.resolve(NUCLEI_BINARY_NAME + ".exe")
                                                                                    : workingDirectory.resolve(NUCLEI_BINARY_NAME);
        final File nucleiExecutable = nucleiPath.toFile();
        if (!nucleiExecutable.exists()) {
            final SupportedArchitecture architecture = SupportedArchitecture.getType(System.getProperty("os.arch"));
            logger.println("Retrieved architecture: " + architecture);

            logger.println("Downloading latest version of Nuclei!");
            downloadAndUnpackLatestNuclei(operatingSystem, architecture, workingDirectory, logger);
        }

        if (!nucleiExecutable.canExecute()) {
            if (!nucleiExecutable.setExecutable(true, true)) {
                runCommand(logger, launcher, new String[]{"chmod", "+x", nucleiPath.toString()});
            }
        }

        return nucleiPath;
    }

    static String downloadTemplates(Launcher launcher, Path workingDirectory, Path nucleiPath, PrintStream logger) {
        final String nucleiTemplatesPath = workingDirectory.resolve("nuclei-templates").toString();
        runCommand(logger, launcher, new String[]{nucleiPath.toString(),
                                                  "-update-directory", nucleiTemplatesPath,
                                                  "-update-templates"});
        return nucleiTemplatesPath;
    }

    static FilePath resolveFilePath(FilePath directory, String fileName) {
        final String absolutePath = directory.getRemote();

        final String resultPath = absolutePath.endsWith(File.separator) ? (absolutePath + fileName)
                                                                        : (absolutePath + File.separator + fileName);
        return new FilePath(directory.getChannel(), resultPath);
    }
}
