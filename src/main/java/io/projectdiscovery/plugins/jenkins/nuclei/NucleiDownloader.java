package io.projectdiscovery.plugins.jenkins.nuclei;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class NucleiDownloader {

    public static final String NUCLEI_RELEASE_URL = "https://github.com/projectdiscovery/nuclei/releases";
    public static final Pattern VERSION_PATTERN = Pattern.compile("v(?:(\\d+\\.)+\\d+|\\d+)");

    private NucleiDownloader() {}

    public static URL extractDownloadUrl(final SupportedOperatingSystem platform, final SupportedArchitecture architecture) {
        final Function<String, RuntimeException> exceptionSupplier = IllegalStateException::new;

        final Element documentBody;
        try {
            documentBody = Jsoup.connect(NUCLEI_RELEASE_URL).get().body();
        } catch (IOException e) {
            throw exceptionSupplier.apply(String.format("Could not access the Nuclei GitHub release URL (%s)", NUCLEI_RELEASE_URL));
        }

        final Element releaseHeaderElement = documentBody.selectFirst("div.release-header a");
        if (releaseHeaderElement != null) {
            final String latestVersion = releaseHeaderElement.text();

            if (VERSION_PATTERN.matcher(latestVersion).matches()) {
                final String version = Pattern.quote(latestVersion.substring(1));
                final String downloadUrl = documentBody.selectFirst(String.format("a[href~=nuclei_%s_%s_%s.]", version, platform.getDisplayName(), architecture.getDisplayName()))
                                                       .attr("abs:href");

                if (!downloadUrl.isEmpty()) {
                    try {
                        return new URL(downloadUrl);
                    } catch (MalformedURLException e) {
                        throw exceptionSupplier.apply(String.format("The extracted download URL %s is not valid", downloadUrl));
                    }
                } else {
                    throw exceptionSupplier.apply(String.format("Could not identify the download URL(%s) for version (%s), platform (%s), architecture(%s)", downloadUrl, version, platform, architecture));
                }
            } else {
                throw exceptionSupplier.apply(String.format("'%s' is not an accepted versioning type!", latestVersion));
            }
        } else {
            throw exceptionSupplier.apply("Cannot identify the first anchor within the DIV with class 'release-header'. The project does not have any published release, or GitHub has changed its HTML structure");
        }
    }
}
