package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NucleiBuilder extends Builder implements SimpleBuildStep {

    /**
     * The fields must either be public or have public getters in order for Jenkins to be able to re-populate them on job configuration re-load.
     * The name of the fields must match the ones specified in <i>config.jelly</i>
     */
    private final String targetUrl;
    private String additionalFlags;
    private String reportingConfiguration;
    private String nucleiVersion;

    @DataBoundConstructor
    public NucleiBuilder(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    @DataBoundSetter
    public void setAdditionalFlags(String additionalFlags) {
        this.additionalFlags = additionalFlags;
    }

    @DataBoundSetter
    public void setReportingConfiguration(String reportingConfiguration) {
        this.reportingConfiguration = reportingConfiguration;
    }

    @DataBoundSetter
    public void setNucleiVersion(String nucleiVersion) {
        this.nucleiVersion = nucleiVersion;
    }

    /**
     * Getter is used by Jenkins to set the previously configured values within a job configuration.
     * Re-opening the configuration of an existing job should reload the previous values.
     */
    @SuppressWarnings("unused")
    public String getTargetUrl() {
        return targetUrl;
    }

    @SuppressWarnings("unused")
    public String getReportingConfiguration() {
        return reportingConfiguration;
    }

    @SuppressWarnings("unused")
    public String getAdditionalFlags() {
        return additionalFlags;
    }

    @SuppressWarnings("unused")
    public String getNucleiVersion() {
        return nucleiVersion;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, TaskListener listener) {
        final PrintStream logger = listener.getLogger();

        final FilePath workingDirectory = NucleiBuilderHelper.getWorkingDirectory(launcher, workspace, logger);
        final String nucleiBinaryPath = NucleiBuilderHelper.prepareNucleiBinary(workingDirectory, this.nucleiVersion, logger);
        final String[] resultCommand = createScanCommand(run, launcher, logger, workingDirectory, nucleiBinaryPath);

        NucleiBuilderHelper.runCommand(logger, launcher, resultCommand);
    }

    private String[] createScanCommand(Run<?, ?> run, Launcher launcher, PrintStream logger, FilePath workingDirectory, String nucleiBinaryPath) {
        final List<String> cliArguments = createMandatoryCliArguments(run, launcher, logger, workingDirectory, nucleiBinaryPath);
        addIssueTrackerConfig(workingDirectory, cliArguments);
        return NucleiBuilderHelper.mergeCliArguments(cliArguments, this.additionalFlags);
    }

    private List<String> createMandatoryCliArguments(Run<?, ?> run, Launcher launcher, PrintStream logger, FilePath filePathWorkingDirectory, String nucleiPath) {
        final String nucleiTemplatesPath = NucleiBuilderHelper.downloadTemplates(launcher, filePathWorkingDirectory, nucleiPath, logger);
        final FilePath outputFilePath = NucleiBuilderHelper.resolveFilePath(filePathWorkingDirectory, String.format("nucleiOutput-%s.txt", run.getId()));
        return new ArrayList<>(Arrays.asList(nucleiPath,
                                             "-templates", nucleiTemplatesPath,
                                             "-target", this.targetUrl,
                                             "-output", outputFilePath.getRemote(),
                                             "-no-color"));
    }

    private void addIssueTrackerConfig(FilePath workingDirectory, List<String> cliArguments) {
        if (this.reportingConfiguration != null && !this.reportingConfiguration.isEmpty()) {
            final FilePath reportConfigPath = NucleiBuilderHelper.resolveFilePath(workingDirectory, "reporting_config.yml");
            try {
                reportConfigPath.write(this.reportingConfiguration, StandardCharsets.UTF_8.name());

                cliArguments.add("-report-config");
                cliArguments.add(reportConfigPath.getRemote());
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(String.format("Error while writing the reporting/issue tracking configuration to '%s'", reportConfigPath.getRemote()));
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * This method is called by Jenkins to validate the input fields before saving the job's configuration.
         * The name of the method must start with <b>doCheck</b> followed by the name of one of the fields declared in the <i>config.jelly</i>,
         * using standard Java naming conventions and must return {@link FormValidation}.
         * The fields intended for validation must match the name of the fields within <i>config.jelly</i> and has to be annotated with {@link QueryParameter}.
         *
         * @param targetUrl              The URL of the desired application to be tested (mandatory)
         * @param additionalFlags        Additional CLI arguments (e.g. -v -debug)
         * @param reportingConfiguration Issue tracker configuration (e.g. Jira/GitHub)
         * @return {@link FormValidation#ok()} or {@link FormValidation#error(java.lang.String)} in case of a validation error.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckTargetUrl(@QueryParameter String targetUrl, @QueryParameter String additionalFlags, @QueryParameter String reportingConfiguration) {

            if (targetUrl.isEmpty()) {
                return FormValidation.error(Messages.NucleiBuilder_DescriptorImpl_errors_missingName());
            }

            // TODO additionalFlags/reportingConfiguration validation?
            return FormValidation.ok();
        }

        /**
         * Method called by Jenkins to populate the "Nuclei version" drop-down
         * @return the Nuclei versions retrieved from the GitHub release page in a <i>vX.Y.Z</i> format
         */
        @SuppressWarnings("unused")
        public ListBoxModel doFillNucleiVersionItems() {
            return NucleiDownloader.getNucleiVersions().stream()
                                   .map(ListBoxModel.Option::new)
                                   .collect(ListBoxModel::new, ArrayList::add, ArrayList::addAll);
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
}
