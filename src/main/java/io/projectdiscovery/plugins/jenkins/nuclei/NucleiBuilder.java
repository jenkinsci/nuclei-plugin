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
import org.jenkinsci.Symbol;
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

public class NucleiBuilder extends Builder implements SimpleBuildStep {

    private final String targetUrl;
    private boolean additionalFlags;

    @DataBoundConstructor
    public NucleiBuilder(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    @DataBoundSetter
    public void setAdditionalFlags(boolean additionalFlags) {
        this.additionalFlags = additionalFlags;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, FilePath workspace, @Nonnull Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        final PrintStream logger = listener.getLogger();
        final Path workingDirectory = Paths.get(workspace.getRemote());
        logger.println("Workspace absolute path: " + workingDirectory);

        final SupportedOperatingSystem operatingSystem = SupportedOperatingSystem.getType(System.getProperty("os.name"));
        logger.println("Retrieved operating system: " + operatingSystem);

        if (workspace.isRemote()) {
            logger.println("Slave builds are not yet supported");
        } else {
            final Path nucleiPath = operatingSystem == SupportedOperatingSystem.Windows ? workingDirectory.resolve("nuclei.exe")
                                                                                        : workingDirectory.resolve("nuclei");

            final File nucleiExecutable = nucleiPath.toFile();
            if (!nucleiExecutable.exists()) {
                logger.println("Downloading latest version of Nuclei!");

                final SupportedArchitecture architecture = SupportedArchitecture.getType(System.getProperty("os.arch"));
                logger.println("Retrieved architecture: " + architecture);

                downloadAndUnpackLatestNuclei(logger, workingDirectory, operatingSystem, architecture);
            }

            if (!nucleiExecutable.canExecute()) {
                nucleiExecutable.setExecutable(true, true);
                // runCommand(logger, launcher, new String[] {"chmod", "+x", nucleiPath.toString()});
            }

            final String nucleiTemplatesPath = workingDirectory.resolve("nuclei-templates").toString();
            runCommand(logger, launcher, new String[]{nucleiPath.toString(), "-ud", nucleiTemplatesPath, "-ut"});

            final Path outputFilePath = workingDirectory.resolve(String.format("nucleiOutput-%s.txt", run.getId()));
            final String[] mandatoryCommands = {nucleiPath.toString(),
                                                "-t", nucleiTemplatesPath,
                                                "-u", targetUrl,
                                                "-o", outputFilePath.toString(),
                                                "-nc"};

            runCommand(logger, launcher, mandatoryCommands);
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

    private void runCommand(PrintStream logger, Launcher launcher, String[] command) throws IOException, InterruptedException {
        Launcher.ProcStarter procStarter = launcher.launch();

        Proc process = procStarter.cmds(command).stdout(logger).stderr(logger).start();

        process.join();
    }

//    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckTargetUrl(@QueryParameter String targetUrl, @QueryParameter String additionalFlags) {

            if (targetUrl.isEmpty())
                return FormValidation.error(Messages.NucleiBuilder_DescriptorImpl_errors_missingName());

            // TODO additonalFlags validation

            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.NucleiBuilder_DescriptorImpl_DisplayName();
        }
    }
}
