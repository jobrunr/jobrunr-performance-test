package org.jobrunr.server.licensing;

public class LicenseKeyValidator {

    public static LicenseKey validateLicenseKey(String licenseKeyAsJwt) {
        return new LicenseKey(licenseKeyAsJwt);
    }

    public static boolean validateLicenseIsNewer(String newLicenseKeyAsJWT, String oldLicenseKeyAsJWT) {
        return true;
    }
}
