package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class NucleiBuilderHelper {

    private NucleiBuilderHelper() {
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

    static String prepareNucleiBinary(PrintStream logger, VirtualChannel virtualChannel, FilePath workingDirectory) {
        final SupportedOperatingSystem operatingSystem = SupportedOperatingSystem.getType(System.getProperty("os.name"));
        logger.println("Retrieved operating system: " + operatingSystem);
        return downloadNuclei(operatingSystem, virtualChannel, workingDirectory);
    }

    private static String downloadNuclei(SupportedOperatingSystem operatingSystem, VirtualChannel virtualChannel, FilePath filePathWorkingDirectory) {
        final RemoteNucleiDownloader remoteNucleiDownloader = new RemoteNucleiDownloader(filePathWorkingDirectory, operatingSystem);
        try {
            return virtualChannel.call(remoteNucleiDownloader).getRemote();
        } catch (Exception e) {
            throw new IllegalStateException("Error while obtaining Nuclei binary.");
        }
    }
}
