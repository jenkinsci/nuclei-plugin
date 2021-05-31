package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class NucleiBuilder extends Builder implements SimpleBuildStep {

    private static final String NUCLEI_BINARY_NAME = "nuclei";

    private final String targetUrl;
    private String additionalFlags;

    @DataBoundConstructor
    public NucleiBuilder(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    @DataBoundSetter
    public void setAdditionalFlags(String additionalFlags) {
        this.additionalFlags = additionalFlags;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, FilePath workspace, @Nonnull Launcher launcher, TaskListener listener) throws IOException {
        final PrintStream logger = listener.getLogger();
        final Path workingDirectory = Paths.get(workspace.getRemote());
        logger.println("Workspace absolute path: " + workingDirectory);

        final SupportedOperatingSystem operatingSystem = SupportedOperatingSystem.getType(System.getProperty("os.name"));
        logger.println("Retrieved operating system: " + operatingSystem);

        if (workspace.isRemote()) {
            logger.println("Slave builds are not yet supported");
        } else {
            final Path nucleiPath = operatingSystem == SupportedOperatingSystem.Windows ? workingDirectory.resolve(NUCLEI_BINARY_NAME + ".exe")
                                                                                        : workingDirectory.resolve(NUCLEI_BINARY_NAME);

            final File nucleiExecutable = nucleiPath.toFile();
            if (!nucleiExecutable.exists()) {
                logger.println("Downloading latest version of Nuclei!");

                final SupportedArchitecture architecture = SupportedArchitecture.getType(System.getProperty("os.arch"));
                logger.println("Retrieved architecture: " + architecture);

                downloadAndUnpackLatestNuclei(logger, workingDirectory, operatingSystem, architecture);
            }

            if (!nucleiExecutable.canExecute()) {
                if (!nucleiExecutable.setExecutable(true, true)) {
                    runCommand(logger, launcher, new String[]{"chmod", "+x", nucleiPath.toString()});
                }
            }

            final String nucleiTemplatesPath = workingDirectory.resolve("nuclei-templates").toString();
            runCommand(logger, launcher, new String[]{nucleiPath.toString(),
                                                      "-update-directory", nucleiTemplatesPath,
                                                      "-update-templates"});

            final Path outputFilePath = workingDirectory.resolve(String.format("nucleiOutput-%s.txt", run.getId()));
            final String[] mandatoryCommands = {nucleiPath.toString(),
                                                "-templates", nucleiTemplatesPath,
                                                "-target", this.targetUrl,
                                                "-output", outputFilePath.toString(),
                                                "-no-color"};

            final String[] resultCommand = mergeCliArguments(mandatoryCommands, this.additionalFlags);

            runCommand(logger, launcher, resultCommand);
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @SuppressWarnings("unused")
        public FormValidation doCheckTargetUrl(@QueryParameter String targetUrl, @QueryParameter String additionalFlags) {

            if (targetUrl.isEmpty()) {
                return FormValidation.error(Messages.NucleiBuilder_DescriptorImpl_errors_missingName());
            }

            // TODO additionalFlags validation?
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.NucleiBuilder_DescriptorImpl_DisplayName();
        }
    }

    private static void runCommand(PrintStream logger, Launcher launcher, String[] command) {
        try {
            Launcher.ProcStarter procStarter = launcher.launch();

            Proc process = procStarter.cmds(command).stdout(logger).stderr(logger).start();

            process.join();
        } catch (IOException | InterruptedException e) {
            logger.println("Error while trying to run the following command: " + String.join(" ", command));
        }
    }

    private void downloadAndUnpackLatestNuclei(PrintStream logger, Path workingDirectory, SupportedOperatingSystem operatingSystem, SupportedArchitecture architecture) throws IOException {
        final URL downloadUrl = NucleiDownloader.extractDownloadUrl(operatingSystem, architecture); // TODO log exceptions
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

    static String[] mergeCliArguments(String[] mandatoryCommands, String additionalFlags) {
        final Stream<String> additionalFlagStream = Arrays.stream(additionalFlags.split("(?= -)"))
                                                          .map(String::trim)
                                                          .flatMap(v -> Arrays.stream(v.split(" ", 2)).map(String::trim))
                                                          .map(v -> {
                                                              final Predicate<String> tester = (character) -> v.startsWith(character) && v.endsWith(character);
                                                              return tester.test("\"") || tester.test("'") ? v.substring(1, v.length() - 1) : v;
                                                          });

        return Stream.concat(Arrays.stream(mandatoryCommands), additionalFlagStream).toArray(String[]::new);
    }
}
