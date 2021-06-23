package io.projectdiscovery.plugins.jenkins.nuclei;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class that helps extracting information about Nuclei releases from GitHub.
 */
public final class NucleiDownloadHelper {

    private static final String NUCLEI_VERSION_REGEX = "((?:\\d+\\.)+\\d+|\\d+)";
    public static final Pattern NUCLEI_VERSION_PATTERN = Pattern.compile(NUCLEI_VERSION_REGEX);

    private static final String NUCLEI_RELEASE_URL = "https://github.com/projectdiscovery/nuclei/releases";
    private static final Pattern RELEASE_TAG_URL_PATTERN = Pattern.compile("/projectdiscovery/nuclei/releases/tag/v" + NUCLEI_VERSION_REGEX);

    private NucleiDownloadHelper() {}

    /**
     * @return a list of released versions of Nuclei to GitHub
     */
    public static List<String> getNucleiVersions() {
        return getNucleiVersions(getNucleiReleasePageBody());
    }

    /**
     * @param operatingSystem The identified operating system mapped to a supported Nuclei build.
     * @param architecture The identified architecture mapped to a supported Nuclei build.
     * @param version An existing version of Nuclei, retrieved by {@link NucleiDownloadHelper#getNucleiVersions()}
     * @return The URL from which the desired Nuclei build can be downloaded.
     */
    public static URL createDownloadUrl(SupportedOperatingSystem operatingSystem, SupportedArchitecture architecture, String version) {
        final Element nucleiReleasePageBody = getNucleiReleasePageBody();
        return createDownloadUrl(nucleiReleasePageBody, operatingSystem, architecture, version);
    }

    private static Element getNucleiReleasePageBody() {
        final Element documentBody;
        try {
            documentBody = Jsoup.connect(NUCLEI_RELEASE_URL).get().body();
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not access the Nuclei GitHub release URL (%s)", NUCLEI_RELEASE_URL));
        }
        return documentBody;
    }

    private static List<String> getNucleiVersions(Element documentBody) {

        return documentBody.select("div.release-header a")
                           .stream()
                           .map(e -> e.attr("href"))
                           .map(url -> {
                               final Matcher matcher = RELEASE_TAG_URL_PATTERN.matcher(url);
                               return matcher.find() ? matcher.group(1) : null;
                           })
                           .filter(Objects::nonNull)
                           .collect(Collectors.toList());
    }

    private static URL createDownloadUrl(Element documentBody, SupportedOperatingSystem operatingSystem, SupportedArchitecture architecture, String version) {
        final Element downloadUrlElement = documentBody.selectFirst(String.format("a[href~=nuclei_%s_%s_%s.]", version, operatingSystem.getDisplayName(), architecture.getDisplayName()));

        final Supplier<IllegalStateException> urlNotFoundException = () -> new IllegalStateException(String.format("Could not identify the download URL for version (%s), platform (%s), architecture(%s)", version, operatingSystem, architecture));

        if (downloadUrlElement == null) {
            throw urlNotFoundException.get();
        }

        final String downloadUrl = downloadUrlElement.attr("abs:href");
        if (downloadUrl.isEmpty()) {
            throw urlNotFoundException.get();
        }

        try {
            return new URL(downloadUrl);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(String.format("The extracted download URL '%s' is not valid!", downloadUrl));
        }
    }
}
