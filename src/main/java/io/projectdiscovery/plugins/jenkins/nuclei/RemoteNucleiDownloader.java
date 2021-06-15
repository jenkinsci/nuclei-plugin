package io.projectdiscovery.plugins.jenkins.nuclei;

import hudson.FilePath;
import jenkins.security.MasterToSlaveCallable;

import java.io.IOException;
import java.net.URL;

class RemoteNucleiDownloader extends MasterToSlaveCallable<FilePath, Exception> {

    public static final String NUCLEI_BINARY_NAME = "nuclei";
    private final FilePath workingDirectory;
    private final SupportedOperatingSystem operatingSystem;

    public RemoteNucleiDownloader(FilePath workingDirectory, SupportedOperatingSystem operatingSystem) {
        this.workingDirectory = workingDirectory;
        this.operatingSystem = operatingSystem;
    }

    @Override
    public FilePath call() throws Exception {
        final String nucleiBinaryName = this.operatingSystem == SupportedOperatingSystem.Windows ? NUCLEI_BINARY_NAME + ".exe"
                                                                                                 : NUCLEI_BINARY_NAME;
        final FilePath nucleiPath = NucleiBuilderHelper.resolveFilePath(this.workingDirectory, nucleiBinaryName);

        if (!nucleiPath.exists()) {
            final SupportedArchitecture architecture = SupportedArchitecture.getType(System.getProperty("os.arch"));
            downloadAndUnpackLatestNuclei(this.operatingSystem, architecture, this.workingDirectory);
        } else {
            final int fullPermissionToOwner = 0700;
            nucleiPath.chmod(fullPermissionToOwner);
        }

        return nucleiPath;
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
}
