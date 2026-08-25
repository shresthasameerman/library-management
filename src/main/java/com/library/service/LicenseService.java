package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.LicenseTier;

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;
import java.util.Enumeration;

public class LicenseService {
    private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuHTNbGGMCFhXcVK73HjLnP4qJpUrw/ByNCR2ObMNkPmOpx6Ry07gBmh+H+OPX5iapcCvmB5OxZ2Qj05VmD3dq1E7BAAZYnIH3J559612tFAK5PnTnlqcQPqoqfQvArJUiQJsq7F9DCbyQKPshcJ2lzOoTj/x21InwfLbncgM6PRk3c5QACPrvyNmPCKkoIpVhYY0QsQB9z3w7P9szIzjMPaotxyXEKAXw0jOCzfcvIYBQ9KG0LysZk3gfoN31TTNhf2vDbptI3+YFhBrBTMA57nKiNV6acWyywcA/rIAfpbo6Om1STEOadlfQQlR2gybFENnJk5yn0HvBgEY7UoUgQIDAQAB";

    public record LicenseInfo(LicenseTier tier, long expiresAt, String machineId) {
        public boolean isExpired() { return expiresAt > 0 && Instant.now().getEpochSecond() > expiresAt; }
    }

    public String getMachineId() {
        StringBuilder hardware = new StringBuilder();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                byte[] address = interfaces.nextElement().getHardwareAddress();
                if (address != null) hardware.append(Base64.getEncoder().encodeToString(address));
            }
        } catch (Exception ignored) {}
        try {
            Path serial = Path.of("/sys/class/dmi/id/product_serial");
            if (Files.isReadable(serial)) hardware.append(Files.readString(serial));
        } catch (IOException ignored) {}
        if (hardware.isEmpty()) hardware.append(System.getProperty("user.name", "unknown"));
        return sha256(hardware.toString());
    }

    public LicenseInfo validate(String licenseKey) {
        try {
            String[] parts = licenseKey.trim().split("\\.");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid license key format.");
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(readPublicKey());
            verifier.update(payload);
            if (!verifier.verify(Base64.getUrlDecoder().decode(parts[1]))) throw new IllegalArgumentException("License signature is invalid.");
            String[] values = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 4 || !"LIBRARYMS".equals(values[0])) throw new IllegalArgumentException("License payload is invalid.");
            LicenseInfo info = new LicenseInfo(LicenseTier.from(values[1]), Long.parseLong(values[2]), values[3]);
            if (!getMachineId().equals(info.machineId())) throw new IllegalArgumentException("This license belongs to another computer.");
            if (info.isExpired()) throw new IllegalArgumentException("This license has expired.");
            return info;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not validate license key.", e);
        }
    }

    public LicenseInfo activate(String licenseKey) throws Exception {
        LicenseInfo info = validate(licenseKey);
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO app_license (id, license_key, tier, machine_id, expires_at) VALUES (1, ?, ?, ?, ?)")) {
            stmt.setString(1, licenseKey.trim());
            stmt.setString(2, info.tier().name());
            stmt.setString(3, info.machineId());
            stmt.setLong(4, info.expiresAt());
            stmt.executeUpdate();
        }
        return info;
    }

    public LicenseInfo getActiveLicense() throws Exception {
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT license_key FROM app_license WHERE id = 1")) {
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? validate(rs.getString(1)) : null;
        }
    }

    public boolean canAddBook(int currentBookCount) {
        try {
            LicenseInfo info = getActiveLicense();
            return info != null && (info.tier() != LicenseTier.BASIC || currentBookCount < 5000);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canUseBarcodeScanner() {
        try {
            LicenseInfo info = getActiveLicense();
            return info != null && info.tier() != LicenseTier.BASIC;
        } catch (Exception e) {
            return false;
        }
    }

    private PublicKey readPublicKey() throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("Unable to identify this computer.", e); }
    }
}