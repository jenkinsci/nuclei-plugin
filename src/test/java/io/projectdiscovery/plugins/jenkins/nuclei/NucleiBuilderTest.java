package io.projectdiscovery.plugins.jenkins.nuclei;

import org.junit.Assert;
import org.junit.Test;

public class NucleiBuilderTest {

    @Test
    public void testCliArgumentMerger() {
        final String[] mandatoryArguments = {"-target", "http://localhost:8080/vulnerableApp", "-no-color"};
        final String additionalFlags = "-a  space-prefix -b-b space between -c-c-c two more spaces  -dd \"c:/program files/asd\" -ee ''";

        final String[] result = NucleiBuilderHelper.mergeCliArguments(mandatoryArguments, additionalFlags);
        Assert.assertArrayEquals(result, new String[]{"-target", "http://localhost:8080/vulnerableApp",
                                                      "-no-color",
                                                      "-a", "space-prefix",
                                                      "-b-b", "space between",
                                                      "-c-c-c", "two more spaces",
                                                      "-dd", "c:/program files/asd",
                                                      "-ee", ""});
    }
}