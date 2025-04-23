package org.jobrunr.server.licensing;

public class LicenseKeyValidator {

    public static LicenseKey validateLicenseKey(String licenseKeyAsJwt) {
        return new LicenseKey(licenseKeyAsJwt);
    }
}
