package util;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Properties;

public final class EmailUtil {

    private static final String CONFIG_PATH = "/config.properties";
    private static final String LOGO_CLASSPATH = "/EBCTLOGO.png";

    private EmailUtil() {}

    public static void sendProofEmail(String subject, String body, File photoFile) throws Exception {
        Properties cfg = loadConfig();

        String smtpHost = needAny(cfg, "smtp.host", "mail.smtp.host", "email.smtp.host");
        int smtpPort = parseIntOrDefault(any(cfg, "smtp.port", "mail.smtp.port", "email.smtp.port"), 587);

        String username = needAny(cfg, "smtp.user", "mail.username", "smtp.username", "email.username");
        String appPassword = needAny(cfg, "smtp.appPassword", "mail.appPassword", "mail.password", "smtp.password", "email.password");

        String toEmail = needAny(cfg, "owner.email", "mail.to", "email.to", "hr.email", "admin.email");

        String companyName = any(cfg, "company.name", "app.companyName", "mail.fromName", "email.fromName");
        if (companyName == null || companyName.trim().isEmpty()) {
            companyName = "Exploring Bearcat Travel & Tours Services";
        }

        String systemName = any(cfg, "system.name", "app.name", "app.systemName");
        if (systemName == null || systemName.trim().isEmpty()) {
            systemName = "Payroll System";
        }

        sendInternal(
                smtpHost, smtpPort,
                username, appPassword,
                toEmail,
                companyName, systemName,
                subject, body,
                photoFile
        );
    }

    private static void sendInternal(
            String smtpHost,
            int smtpPort,
            String username,
            String appPassword,
            String toEmail,
            String companyName,
            String systemName,
            String subject,
            String body,
            File photoFile
    ) throws Exception {

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, appPassword);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username, companyName));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        final String cidLogo = "logo-" + System.currentTimeMillis();
        final String cidPhoto = "proof-" + System.currentTimeMillis();

        byte[] logoBytes = loadResourceBytes(LOGO_CLASSPATH);

        byte[] photoThumbBytes = null;
        String photoFileName = null;

        if (photoFile != null && photoFile.exists()) {
            photoFileName = photoFile.getName();
            try {
                BufferedImage img = ImageIO.read(photoFile);
                if (img != null) {
                    BufferedImage scaled = scaleToWidth(img, 520);
                    photoThumbBytes = toJpegBytes(scaled, 0.88f);
                }
            } catch (Exception ignored) {
                photoThumbBytes = null;
            }
        }

        MimeMultipart mixed = new MimeMultipart("mixed");

        MimeBodyPart relatedWrapper = new MimeBodyPart();
        MimeMultipart related = new MimeMultipart("related");

        String html = buildProfessionalHtml(
                companyName,
                systemName,
                subject,
                body,
                (logoBytes != null),
                (photoThumbBytes != null),
                cidLogo,
                cidPhoto,
                photoFileName
        );

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        related.addBodyPart(htmlPart);

        if (logoBytes != null) {
            MimeBodyPart logoPart = new MimeBodyPart();
            DataSource ds = new ByteArrayDataSource(logoBytes, "image/png");
            logoPart.setDataHandler(new DataHandler(ds));
            logoPart.setFileName("EBCTLOGO.png");
            logoPart.setDisposition(MimeBodyPart.INLINE);
            logoPart.setHeader("Content-ID", "<" + cidLogo + ">");
            related.addBodyPart(logoPart);
        }

        if (photoThumbBytes != null) {
            MimeBodyPart thumbPart = new MimeBodyPart();
            DataSource ds = new ByteArrayDataSource(photoThumbBytes, "image/jpeg");
            thumbPart.setDataHandler(new DataHandler(ds));
            thumbPart.setFileName("proof_thumbnail.jpg");
            thumbPart.setDisposition(MimeBodyPart.INLINE);
            thumbPart.setHeader("Content-ID", "<" + cidPhoto + ">");
            related.addBodyPart(thumbPart);
        }

        relatedWrapper.setContent(related);
        mixed.addBodyPart(relatedWrapper);

        if (photoFile != null && photoFile.exists()) {
            MimeBodyPart attach = new MimeBodyPart();
            attach.attachFile(photoFile);
            attach.setFileName(photoFile.getName());
            attach.setDisposition(MimeBodyPart.ATTACHMENT);
            mixed.addBodyPart(attach);
        }

        message.setContent(mixed);
        Transport.send(message);
    }

    private static String buildProfessionalHtml(
            String companyName,
            String systemName,
            String subject,
            String body,
            boolean hasLogo,
            boolean hasInlinePhoto,
            String cidLogo,
            String cidPhoto,
            String photoFileName
    ) {
        String c = esc(companyName != null ? companyName : "Company");
        String s = esc(systemName != null ? systemName : "Payroll System");
        String subj = esc(subject != null ? subject : "Time Log Confirmation");

        String emp = findLine(body, "Employee:");
        String empId = findLine(body, "Employee ID:");
        String action = findLine(body, "Action:");
        String dt = findLine(body, "Date/Time:");
        if (dt.isEmpty()) dt = findLine(body, "Date/Time");

        if (emp.isEmpty()) emp = "—";
        if (empId.isEmpty()) empId = "—";
        if (action.isEmpty()) action = "—";
        if (dt.isEmpty()) dt = "—";

        empId = formatIdIfNumeric(empId);

        // ✅ NEW: logo gets breathing room (padded “pill”)
        String logoHtml = hasLogo
                ? ("<div style='background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;"
                   + "padding:8px 10px;display:inline-flex;align-items:center;justify-content:center;'>"
                   + "<img src='cid:" + esc(cidLogo) + "' alt='Company Logo' style='height:34px;display:block;'/>"
                   + "</div>")
                : ("<div style='font-size:14px;font-weight:800;color:#111827;'>" + c + "</div>");

        String photoBlock;
        if (hasInlinePhoto) {
            String fileText = (photoFileName != null && !photoFileName.trim().isEmpty())
                    ? "<div style='font-size:12px;color:#6b7280;margin-top:6px;'>File: " + esc(photoFileName) + "</div>"
                    : "";
            photoBlock = ""
                    + "<div style='margin-top:14px;'>"
                    + "  <div style='font-weight:800;color:#111827;margin-bottom:8px;'>Verification Photo</div>"
                    + "  <div style='background:#0b0f16;border-radius:12px;padding:10px;display:inline-block;'>"
                    + "    <img src='cid:" + esc(cidPhoto) + "' alt='Verification Photo' "
                    + "         style='display:block;max-width:520px;width:100%;border-radius:10px;'/>"
                    + "  </div>"
                    + fileText
                    + "</div>";
        } else {
            photoBlock = "<div style='margin-top:14px;color:#374151;'>Verification photo is attached for record purposes.</div>";
        }

        return ""
                + "<div style='font-family:Segoe UI,Arial,sans-serif;color:#111827;line-height:1.45;'>"

                // ✅ NEW: outer spacing so header doesn’t feel cramped
                + "  <div style='padding:16px 18px;background:#f3f4f6;border-radius:16px;margin-bottom:8px;'>"
                + "    <div style='display:flex;align-items:center;gap:14px;'>"
                + "      <div style='flex:0 0 auto;'>" + logoHtml + "</div>"
                + "      <div style='flex:1 1 auto;'>"
                + "        <div style='font-size:12px;color:#6b7280;'>" + c + "</div>"
                + "        <div style='font-size:18px;font-weight:900;margin-top:2px;'>" + subj + "</div>"
                + "        <div style='font-size:12px;color:#6b7280;margin-top:4px;'>Automated notification from " + s + "</div>"
                + "      </div>"
                + "    </div>"
                + "  </div>"

                + "  <div style='padding:10px 4px 0 4px;'>"
                + "    <p style='margin:12px 0 8px 0;'>Hello,</p>"
                + "    <p style='margin:0 0 12px 0;'>This is to confirm the following employee activity:</p>"

                + "    <table style='border-collapse:collapse;width:100%;max-width:620px;'>"
                + "      <tr>"
                + "        <td style='padding:10px;background:#f9fafb;border:1px solid #e5e7eb;font-weight:800;width:170px;'>Employee</td>"
                + "        <td style='padding:10px;border:1px solid #e5e7eb;'>" + esc(emp) + "</td>"
                + "      </tr>"
                + "      <tr>"
                + "        <td style='padding:10px;background:#f9fafb;border:1px solid #e5e7eb;font-weight:800;'>Employee ID</td>"
                + "        <td style='padding:10px;border:1px solid #e5e7eb;'>" + esc(empId) + "</td>"
                + "      </tr>"
                + "      <tr>"
                + "        <td style='padding:10px;background:#f9fafb;border:1px solid #e5e7eb;font-weight:800;'>Action</td>"
                + "        <td style='padding:10px;border:1px solid #e5e7eb;'>" + esc(action) + "</td>"
                + "      </tr>"
                + "      <tr>"
                + "        <td style='padding:10px;background:#f9fafb;border:1px solid #e5e7eb;font-weight:800;'>Date &amp; Time</td>"
                + "        <td style='padding:10px;border:1px solid #e5e7eb;'>" + esc(dt) + "</td>"
                + "      </tr>"
                + "    </table>"

                + photoBlock

                + "    <hr style='margin:18px 0;border:none;border-top:1px solid #e5e7eb;'/>"
                + "    <p style='margin:0;font-size:12px;color:#6b7280;'>This is an automated message. Please do not reply.</p>"
                + "  </div>"
                + "</div>";
    }

    private static Properties loadConfig() throws Exception {
        try (InputStream in = EmailUtil.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) throw new IllegalStateException("Missing " + CONFIG_PATH + " in resources.");
            Properties p = new Properties();
            p.load(in);
            return p;
        }
    }

    private static byte[] loadResourceBytes(String path) {
        try (InputStream in = EmailUtil.class.getResourceAsStream(path)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String any(Properties p, String... keys) {
        for (String k : keys) {
            String v = p.getProperty(k);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    private static String needAny(Properties p, String... keys) {
        String v = any(p, keys);
        if (v == null) throw new IllegalStateException("Missing email config. Add ONE of: " + String.join(" OR ", keys));
        return v;
    }

    private static int parseIntOrDefault(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static String findLine(String body, String prefix) {
        if (body == null) return "";
        String[] lines = body.split("\\r?\\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith(prefix)) return t.substring(prefix.length()).trim();
        }
        return "";
    }

    private static String formatIdIfNumeric(String raw) {
        if (raw == null) return "—";
        String t = raw.trim();
        String digits = t.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return t;
        try {
            int n = Integer.parseInt(digits);
            return String.format("%04d", n);
        } catch (Exception e) {
            return t;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static BufferedImage scaleToWidth(BufferedImage src, int targetW) {
        if (src == null) return null;
        if (targetW <= 0) return src;

        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= targetW) return src;

        double scale = (double) targetW / (double) w;
        int nw = targetW;
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] toJpegBytes(BufferedImage img, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0.1f, Math.min(quality, 1.0f)));
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}
