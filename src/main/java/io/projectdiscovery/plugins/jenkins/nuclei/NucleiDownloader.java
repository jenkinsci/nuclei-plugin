package io.projectdiscovery.plugins.jenkins.nuclei;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class NucleiDownloader {

    public static final String NUCLEI_RELEASE_URL = "https://github.com/projectdiscovery/nuclei/releases";
    public static final Pattern VERSION_PATTERN = Pattern.compile("v(?:(\\d+\\.)+\\d+|\\d+)");

    private NucleiDownloader() {
    }

    public static List<String> getNucleiVersions() {
        return getNucleiVersions(getNucleiReleasePageBody());
    }

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
                           .filter(e -> e.attr("href").contains("releases/tag"))
                           .map(Element::text)
                           .filter(v -> VERSION_PATTERN.matcher(v).matches())
                           .map(v -> v.substring(1))
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
