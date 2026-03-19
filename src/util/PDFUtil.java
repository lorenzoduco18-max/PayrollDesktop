package util;

import model.Payslip;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PDFUtil {

    private static final DecimalFormat DF2 = new DecimalFormat("#,##0.00");

    // Colors (close to your sample)
    private static final Color C_BLACK = new Color(0x111111);
    private static final Color C_GRAY_LINE = new Color(0x2B2B2B);
    private static final Color C_LIGHT_GRAY = new Color(0xEDEDED);
    private static final Color C_GREEN = new Color(0xBFE6A8);
    private static final Color C_BLUE = new Color(0x8CC8F2);

    // Safe printable area margins (A4 landscape)
    private static final float M = 18f;

    // Font sizes
    private static final float FS_TITLE = 12f;
    private static final float FS_HDR = 10f;
    private static final float FS_SMALL = 8f;

    // Row heights
    private static final float H_HDR = 16f;
    private static final float H_ROW = 14f;

    public static void generatePayslipPDF(Payslip p, File outFile) throws IOException {
        if (p == null) throw new IllegalArgumentException("Payslip is null.");
        if (outFile == null) throw new IllegalArgumentException("Output file is null.");

        PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        PDPage page = new PDPage(pageSize);

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(page);

            FontPack fonts = loadFonts(doc);

            float W = pageSize.getWidth();
            float H = pageSize.getHeight();

            float x0 = M;
            float y0 = H - M;
            float contentW = W - (2 * M);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // ===== HEADER =====
                float headerH = 70f;
                float y = y0;

             // ===== HEADER LOGOS (left + right) =====
                float logoH = 58f;            // bigger
                float logoWLeft = 58f;        // left logo square
                float logoWRight = 120f;      // right logo wider (LOVE PH)
                float logoY = y - 60f;        // push DOWN so it’s not too high

                float leftLogoX = x0 + 8f;    // move slightly right from edge
                float rightLogoX = x0 + contentW - logoWRight + 15f;


                // Left: EBCT logo
                drawImageIfExists(doc, cs, "/EBCTLOGO.png", leftLogoX, logoY, logoWLeft, logoH);

             // Right logo: flush with right edge
                drawImageKeepRatioRight(
                        doc,
                        cs,
                        "/LOVE_PHILIPPINES.png",
                        x0 + contentW - 6f, // ← right border of content
                        logoY,
                        logoH
                );



                float centerX = x0 + contentW / 2f;
                textCenter(cs, fonts.bold, FS_HDR, C_BLACK,
                        "Exploring Bearcat Travel & Tours Services", centerX, y - 18f);
                textCenter(cs, fonts.reg, FS_SMALL, C_BLACK,
                        "G. Rodriguez St. New Public Market Rd. Brgy. Sta. Monica,", centerX, y - 30f);
                textCenter(cs, fonts.reg, FS_SMALL, C_BLACK,
                        "Puerto Princesa City, 5300 Palawan, Philippines", centerX, y - 40f);
                textCenter(cs, fonts.reg, FS_SMALL, C_BLACK,
                        "Mobile: (+63)920978-5624    Landline: (+6348)726-1145", centerX, y - 50f);

                textCenter(cs, fonts.bold, FS_TITLE, C_BLACK, "PAYSLIP", centerX, y - 64f);

                y -= headerH;

                // ===== TOP BLOCKS =====
                float gap = 10f;
                float leftW = contentW * 0.62f;
                float rightW = contentW - leftW - gap;

                // Increased height to fit the additional employee contact/email rows
                // (prevents the last rows from overlapping the green rate bar)
                final int EMP_INFO_ROWS = 4;
                float rowH = 20f;
                float blockH = rowH * EMP_INFO_ROWS;

                // LEFT (EMPLOYEE INFO)
                float lx = x0;
                float lyTop = y;
                drawBox(cs, lx, lyTop - blockH, leftW, blockH, 1f);

                float splitX = lx + leftW * 0.28f;
                line(cs, splitX, lyTop, splitX, lyTop - blockH, 1f);

                // Grid lines for the employee info table
                for (int i = 1; i < EMP_INFO_ROWS; i++) {
                    line(cs, lx, lyTop - (i * rowH), lx + leftW, lyTop - (i * rowH), 1f);
                }

                Object empObj = getObj(p, "emp", "employee");

                String empName = firstNonEmpty(
                        getString(p, "empName", "employeeName", "fullName", "name"),
                        buildFullName(empObj),
                        (empObj != null ? getString(empObj, "fullName", "name") : null)
                );
                if (empName == null) empName = "";

                String empNo = firstNonEmpty(
                        getString(p, "empNo", "employeeNo", "employeeNumber", "empNumber"),
                        (empObj != null ? getString(empObj, "empNo", "employeeNo", "employeeNumber", "empNumber", "id") : null)
                );
                if (empNo == null) empNo = "";

                String address = firstNonEmpty(
                        getString(p, "address", "empAddress"),
                        (empObj != null ? getString(empObj, "address", "empAddress") : null)
                );
                if (address == null) address = "";

                String mobile = firstNonEmpty(
                        getString(p, "mobile", "mobileNo", "phone", "contactNo"),
                        (empObj != null ? getString(empObj, "mobile", "mobileNo", "phone", "contactNo") : null)
                );
                if (mobile == null) mobile = "";

                String personalContactNo = firstNonEmpty(
                        getString(p, "personalContactNo", "personal_contact_no", "personalContact", "personalContactNumber"),
                        (empObj != null ? getString(empObj, "personalContactNo", "personal_contact_no", "personalContact", "personalContactNumber") : null),
                        mobile
                );
                if (personalContactNo == null) personalContactNo = "";

                String companyContactNo = firstNonEmpty(
                        getString(p, "companyContactNo", "company_contact_no", "companyContact", "companyContactNumber"),
                        (empObj != null ? getString(empObj, "companyContactNo", "company_contact_no", "companyContact", "companyContactNumber") : null)
                );
                if (companyContactNo == null) companyContactNo = "";

                String personalEmail = firstNonEmpty(
                        getString(p, "personalEmail", "personal_email", "emailPersonal"),
                        (empObj != null ? getString(empObj, "personalEmail", "personal_email", "emailPersonal") : null)
                );
                if (personalEmail == null) personalEmail = "";

                String companyEmail = firstNonEmpty(
                        getString(p, "companyEmail", "company_email", "emailCompany"),
                        (empObj != null ? getString(empObj, "companyEmail", "company_email", "emailCompany") : null)
                );
                if (companyEmail == null) companyEmail = "";


                String position = firstNonEmpty(
                        getString(p, "position", "jobTitle"),
                        (empObj != null ? getString(empObj, "position", "jobTitle") : null)
                );
                if (position == null) position = "";

                String payType = firstNonEmpty(
                        getString(p, "payType"),
                        (empObj != null ? getString(empObj, "payType") : null)
                );
                if (payType == null) payType = "";

                float ry = lyTop;
                labelValueRow(cs, fonts, lx + 6, ry - 12, "EMPLOYEE", safe(empName));
                ry -= rowH;

                labelValueRowWrapped(cs, fonts, lx + 6, ry - 12, "ADDRESS", safe(address), lx + 6 + 165, (lx + leftW) - 6);
                ry -= rowH;

                labelValueRow(cs, fonts, lx + 6, ry - 12, "MOBILE NO.", safe(firstNonEmpty(personalContactNo, mobile)));
                ry -= rowH;

                labelValueRow(cs, fonts, lx + 6, ry - 12, "EMPLOYEE NO.", safe(empNo));

                // RIGHT (ACCOUNT SUMMARY)
                float rx = x0 + leftW + gap;
                float rTop = y;
                float rRowH = 20f;
                float rBlockH = H_HDR + (4 * rRowH);
                drawBox(cs, rx, rTop - rBlockH, rightW, rBlockH, 1f);

                fillRect(cs, rx, rTop - H_HDR, rightW, H_HDR, C_LIGHT_GRAY);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "ACCOUNT SUMMARY", rx + rightW / 2f, rTop - 11f);

                float rBodyTop = rTop - H_HDR;
                for (int i = 1; i < 4; i++) {
                    line(cs, rx, rBodyTop - i * rRowH, rx + rightW, rBodyTop - i * rRowH, 1f);
                }

                String start = getString(p, "start", "periodStart", "payStart", "dateStart");
                String end = getString(p, "end", "periodEnd", "payEnd", "dateEnd");
                String period = (notEmpty(start) && notEmpty(end))
                        ? (start + " - " + end)
                        : safe(getString(p, "period", "payPeriod"));

                double totalHours = getDouble(p, "totalHours", "hours", "workHours");
                double net = getDouble(p, "netPay", "net", "netpay");

                summaryRow(cs, fonts, rx + 8, rBodyTop - (rRowH * 0) - 11, "PAY PERIOD :", period);
                summaryRow(cs, fonts, rx + 8, rBodyTop - (rRowH * 1) - 11, "PAY TYPE :", (notEmpty(payType) ? payType : "—"));
                summaryRow(cs, fonts, rx + 8, rBodyTop - (rRowH * 2) - 11, "NO. OF WORK HRS :", fmtHM(totalHours));
                summaryRow(cs, fonts, rx + 8, rBodyTop - (rRowH * 3) - 11, "NET PAY :", money(net, fonts.isUnicode));

                y -= (Math.max(blockH, rBlockH) + 10);

                // ===== GREEN RATE TAB =====

                // ===== GOVERNMENT IDS TABLE (RIGHT SIDE) =====
                float govRowH = 18f;
                int GOV_ROWS = 4;
                float govH = govRowH * (GOV_ROWS + 1);
                float govX = x0 + leftW + gap;
                float govYTop = y;
                float govBottom = govYTop - govH;

                drawBox(cs, govX, govYTop - govH, rightW, govH, 1f);
                fillRect(cs, govX, govYTop - govRowH, rightW, govRowH, C_LIGHT_GRAY);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "GOVERNMENT IDS", govX + rightW / 2f, govYTop - 12f);

                for (int i = 1; i <= GOV_ROWS; i++) {
                    line(cs, govX, govYTop - (govRowH * i), govX + rightW, govYTop - (govRowH * i), 1f);
                }

                Object empObj2 = getObj(p, "emp", "employee");

                String sssGov = firstNonEmpty(
                        getString(p, "sssNo", "sssNumber"),
                        (empObj2 != null ? getString(empObj2, "sssNo", "sssNumber") : null)
                );
                String philhealthGov = firstNonEmpty(
                        getString(p, "philhealthNo", "philHealthNo"),
                        (empObj2 != null ? getString(empObj2, "philhealthNo", "philHealthNo") : null)
                );
                String pagibigGov = firstNonEmpty(
                        getString(p, "pagibigNo", "pagIbigNo"),
                        (empObj2 != null ? getString(empObj2, "pagibigNo", "pagIbigNo") : null)
                );
                String tin = firstNonEmpty(
                        getString(p, "tinNo", "tin"),
                        (empObj2 != null ? getString(empObj2, "tinNo", "tin") : null)
                );

                float gy = govYTop - govRowH - 12;
                labelValueRow(cs, fonts, govX + 6, gy, "SSS NO.", safe(sssGov)); gy -= govRowH;
                labelValueRow(cs, fonts, govX + 6, gy, "PHILHEALTH NO.", safe(philhealthGov)); gy -= govRowH;
                labelValueRow(cs, fonts, govX + 6, gy, "PAG-IBIG NO.", safe(pagibigGov)); gy -= govRowH;
                labelValueRow(cs, fonts, govX + 6, gy, "TIN NO.", safe(tin));

                // NOTE: do NOT shift left layout because GOV IDS is right-side only


                float rateH2 = 32f;
                float rateW2 = leftW;
                fillRect(cs, x0, y - rateH2, rateW2, rateH2, C_GREEN);
                drawBox(cs, x0, y - rateH2, rateW2, rateH2, 1f);

                double dailyRate = getDoubleSmart(p, new String[]{"dailyRate","ratePerDay","dayRate"}, new String[]{"daily","dayrate","rateperday"});
                if (dailyRate == 0 && empObj != null) {
                    dailyRate = getDoubleSmart(empObj, new String[]{"rate","dailyRate","ratePerDay","dayRate"}, new String[]{"rate","daily"});
                }

                double hourRate = getDoubleSmart(p, new String[]{"hourlyRate","hourRate"}, new String[]{"hourly","hourrate"});
                if (hourRate == 0 && empObj != null) {
                    hourRate = getDoubleSmart(empObj, new String[]{"hourlyRate","hourRate"}, new String[]{"hourly","hourrate"});
                }

                double otRate = getDoubleSmart(p, new String[]{"otRateUsed","otRate","overtimeRate"}, new String[]{"otrate","overtimerate"});
                if (otRate == 0 && hourRate > 0) otRate = hourRate * 1.25;


                // OT Toggle (controls whether OT rows + OT rate show in PDF)
                boolean otEnabled = getBooleanSmart(p,
                        new String[]{"otEnabled","enableOT","enableOTPay","otPayEnabled","showOT"},
                        new String[]{"otenabled","enableot","enableotpay","otpayenabled","showot"}
                );

                double regHolHrs = getDoubleSmart(p,
                        new String[]{"regularHolidayHours","regHolidayHours","regularHolHours","regularHolidayHrs"},
                        new String[]{"regularholidayhours","regholidayhours","regularholhours"});
                double specHolHrs = getDoubleSmart(p,
                        new String[]{"specialHolidayHours","specHolidayHours","specialHolHours","specialHolidayHrs"},
                        new String[]{"specialholidayhours","specholidayhours","specialholhours"});

                double regHolPay = getDoubleSmart(p,
                        new String[]{"regularHolidayPay","regularHolidayPremiumPay","regHolidayPay","regularHolPay"},
                        new String[]{"regularholidaypay","regholidaypay","regularholidaypremium"});
                double specHolPay = getDoubleSmart(p,
                        new String[]{"specialHolidayPay","specialHolidayPremiumPay","specHolidayPay","specialHolPay"},
                        new String[]{"specialholidaypay","specholidaypay","specialholidaypremium"});

                double regHolDayRate = getDoubleSmart(p,
                        new String[]{"regularHolidayDayRate","regHolidayDayRate","regularHolidayRatePerDay","regHolidayRatePerDay",
                                "regularHolidayRateDay","regHolidayRateDay","regularHolidayRateOverride","regHolidayRateOverride"},
                        new String[]{"regularholiday","regholiday","perday","dayrate","override"});

                double specHolDayRate = getDoubleSmart(p,
                        new String[]{"specialHolidayDayRate","specHolidayDayRate","specialHolidayRatePerDay","specHolidayRatePerDay",
                                "specialHolidayRateDay","specHolidayRateDay","specialHolidayRateOverride","specHolidayRateOverride"},
                        new String[]{"specialholiday","specholiday","perday","dayrate","override"});

                if (regHolDayRate == 0) {
                    regHolDayRate = getDoubleSmart(p,
                            new String[]{"holidayRatePerDay","holidayDayRate","holidayRateDay","holidayRate"},
                            new String[]{"holidayrate","perday","dayrate"});
                }
                if (specHolDayRate == 0) {
                    specHolDayRate = getDoubleSmart(p,
                            new String[]{"holidayRatePerDay","holidayDayRate","holidayRateDay","holidayRate"},
                            new String[]{"holidayrate","perday","dayrate"});
                }

                if (regHolDayRate == 0 && regHolHrs > 0 && regHolPay > 0) regHolDayRate = (regHolPay / regHolHrs) * 8.0;
                if (specHolDayRate == 0 && specHolHrs > 0 && specHolPay > 0) specHolDayRate = (specHolPay / specHolHrs) * 8.0;

                // ✅ FIX: Use 3 segments, label-left + value-right => no overlap ever
                float seg1L = x0 + 6;
                float seg1R = x0 + rateW2 * 0.34f;
                float seg2L = seg1R;
                float seg2R = x0 + rateW2 * 0.67f;
                float seg3L = seg2R;
                float seg3R = x0 + rateW2 - 6;

                float ty1 = y - 11;
                float ty2 = y - 25;

                segmentKV(cs, fonts, "Daily Rate", money(dailyRate, fonts.isUnicode), seg1L, seg1R, ty1);
                segmentKV(cs, fonts, "Hour Rate", money(hourRate, fonts.isUnicode), seg2L + 6, seg2R, ty1);
                segmentKV(cs, fonts, "Reg Hol Rate",
                        (regHolDayRate > 0 ? (money(regHolDayRate, fonts.isUnicode) + "/day") : "—"),
                        seg3L + 6, seg3R, ty1);

                segmentKV(cs, fonts, "Pay Type", (notEmpty(payType) ? payType : "—"), seg1L, seg1R, ty2);
                if (otEnabled) {
                    segmentKV(cs, fonts, "OT Rate", money(otRate, fonts.isUnicode), seg2L + 6, seg2R, ty2);
                }
                segmentKV(cs, fonts, "Spec Hol Rate",
                        (specHolDayRate > 0 ? (money(specHolDayRate, fonts.isUnicode) + "/day") : "—"),
                        seg3L + 6, seg3R, ty2);

                y -= (rateH2 + 8);                // ===== MAIN TABLES =====
                float tablesH = 172f;
                float earnW = leftW;
                float dedW = rightW;

                // ---------- EARNINGS (WITH RATE COLUMN) ----------
                float earnX = x0;
                float earnTop = y;

                drawBox(cs, earnX, earnTop - tablesH, earnW, tablesH, 1f);

                fillRect(cs, earnX, earnTop - H_HDR, earnW, H_HDR, C_LIGHT_GRAY);
                drawBox(cs, earnX, earnTop - tablesH, earnW, tablesH, 1f);
             // ✅ slightly higher so it sits centered in the gray band
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "EARNINGS", earnX + earnW / 2f, earnTop - 9f);


                float eBodyTop = earnTop - H_HDR;

                // DESCR | HRS | RATE | AMOUNT
                float eColDesc = earnX + earnW * 0.48f;
                float eColHrs  = earnX + earnW * 0.66f;
                float eColRate = earnX + earnW * 0.82f;
                float eRight   = earnX + earnW;

             // ✅ vertical lines should NOT cross the gray header (start below header)
                line(cs, eColDesc, eBodyTop, eColDesc, earnTop - tablesH, 1f);
                line(cs, eColHrs,  eBodyTop, eColHrs,  earnTop - tablesH, 1f);
                line(cs, eColRate, eBodyTop, eColRate, earnTop - tablesH, 1f);


                line(cs, earnX, eBodyTop - H_ROW, eRight, eBodyTop - H_ROW, 1f);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "DESCR.", (earnX + eColDesc) / 2f, eBodyTop - 11f);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "HRS", (eColDesc + eColHrs) / 2f, eBodyTop - 11f);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "RATE", (eColHrs + eColRate) / 2f, eBodyTop - 11f);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "AMOUNT", (eColRate + eRight) / 2f, eBodyTop - 11f);

                double totalOTHours = getDouble(p, "totalOT", "totalOtHours", "otHours", "overtimeHours");

                double regHolOtHrs = getDoubleSmart(p,
                        new String[]{"regularHolidayOTHours","regularHolidayOtHours","regHolidayOTHours","regHolidayOtHours","regularHolidayOt"},
                        new String[]{"regularholidayot","regholidayot"});
                double specHolOtHrs = getDoubleSmart(p,
                        new String[]{"specialHolidayOTHours","specialHolidayOtHours","specHolidayOTHours","specHolidayOtHours","specialHolidayOt"},
                        new String[]{"specialholidayot","specholidayot"});

                double basicPay = getDoubleSmart(p, new String[]{"basicPay","basePay"}, new String[]{"basicpay","basepay"});
                double otPay = getDoubleSmart(p, new String[]{"otPay","overtimePay"}, new String[]{"otpay","overtimepay"});

                double regHolOtPay = getDoubleSmart(p,
                        new String[]{"regularHolidayOTPay","regularHolidayOtPay","regHolidayOTPay","regHolidayOtPay"},
                        new String[]{"regularholidayotpay","regholidayotpay"});
                double specHolOtPay = getDoubleSmart(p,
                        new String[]{"specialHolidayOTPay","specialHolidayOtPay","specHolidayOTPay","specHolidayOtPay"},
                        new String[]{"specialholidayotpay","specholidayotpay"});

                double holidayTotalPay = getDoubleSmart(p,
                        new String[]{"holidayPremiumPay","holidayPay","holidayTotalPay","holidayPayTotal"},
                        new String[]{"holidaypremium","holidaytotalpay","holidaypaytotal"});

                double incentives = getDoubleSmart(p,
                        new String[]{"incentives","incentive","incentivePay","incentivesPay","incentivesAmount","incentiveAmount"},
                        new String[]{"incentives","incentive"});
                double thirteenthMonthPay = getDoubleSmart(p,
                        new String[]{"thirteenthMonthPay","thirteenth_month_pay","month13Pay","month13_pay"},
                        new String[]{"thirteenthmonthpay","13thmonthpay"});
                double bonusPay = getDoubleSmart(p,
                        new String[]{"bonusPay","bonus","bonus_pay"},
                        new String[]{"bonuspay","bonus"});

                if (basicPay == 0 && totalHours > 0 && hourRate > 0) basicPay = totalHours * hourRate;
                if (otPay == 0 && totalOTHours > 0 && otRate > 0) otPay = totalOTHours * otRate;

                if (regHolPay == 0 && regHolHrs > 0 && regHolDayRate > 0) regHolPay = regHolHrs * (regHolDayRate / 8.0);
                if (specHolPay == 0 && specHolHrs > 0 && specHolDayRate > 0) specHolPay = specHolHrs * (specHolDayRate / 8.0);

                if (regHolOtPay == 0 && regHolOtHrs > 0 && otRate > 0) regHolOtPay = regHolOtHrs * otRate;
                if (specHolOtPay == 0 && specHolOtHrs > 0 && otRate > 0) specHolOtPay = specHolOtHrs * otRate;

                List<EarnRow4> rows = new ArrayList<>();
                rows.add(new EarnRow4("REGULAR PAY", totalHours, money(hourRate, fonts.isUnicode), basicPay));
                if (otEnabled) {
                    rows.add(new EarnRow4("OT PAY", totalOTHours, money(otRate, fonts.isUnicode), otPay));
                }

                rows.add(new EarnRow4("REG HOLIDAY PAY", regHolHrs,
                        (regHolDayRate > 0 ? (money(regHolDayRate, fonts.isUnicode) + "/day") : "—"),
                        regHolPay));

                if (otEnabled) {
                    rows.add(new EarnRow4("REG HOLIDAY OT", regHolOtHrs, money(otRate, fonts.isUnicode), regHolOtPay));
                }

                rows.add(new EarnRow4("SPECIAL HOL. PAY", specHolHrs,
                        (specHolDayRate > 0 ? (money(specHolDayRate, fonts.isUnicode) + "/day") : "—"),
                        specHolPay));

                if (otEnabled) {
                    rows.add(new EarnRow4("SPECIAL HOL. OT", specHolOtHrs, money(otRate, fonts.isUnicode), specHolOtPay));
                }

                rows.add(new EarnRow4("HOLIDAY PAY (TOTAL)", 0, "", holidayTotalPay));

                if (incentives > 0.0000001) {
                    rows.add(new EarnRow4("INCENTIVES", 0, "", incentives));
                }
                if (thirteenthMonthPay > 0.0000001) {
                    rows.add(new EarnRow4("13TH MONTH PAY", 0, "", thirteenthMonthPay));
                }
                if (bonusPay > 0.0000001) {
                    rows.add(new EarnRow4("BONUS", 0, "", bonusPay));
                }

                int rowCount = Math.max(8, rows.size());
                float eY = eBodyTop - H_ROW;

                for (int i = 0; i < rowCount; i++) {
                    EarnRow4 r = (i < rows.size()) ? rows.get(i) : null;

                    if (r != null) {
                        String hrsTxt = ("HOLIDAY PAY (TOTAL)".equals(r.descr) || "INCENTIVES".equals(r.descr) || "13TH MONTH PAY".equals(r.descr) || "BONUS".equals(r.descr)) ? "" : fmtHM(r.hrs);
                        earningsRow4(cs, fonts, earnX, eColDesc, eColHrs, eColRate, eRight, eY,
                                r.descr, hrsTxt, safe(r.rateText), money(r.amount, fonts.isUnicode));
                    } else {
                        earningsRow4(cs, fonts, earnX, eColDesc, eColHrs, eColRate, eRight, eY, "", "", "", "");
                    }

                    eY -= H_ROW;
                    line(cs, earnX, eY, eRight, eY, 1f);
                }

                // ---------- DEDUCTIONS ----------
                float dedX = x0 + earnW + gap;
                float dedTop = Math.min(earnTop, govBottom - 12f); // keep earnings high; push deductions below Government IDs if needed

                drawBox(cs, dedX, dedTop - tablesH, dedW, tablesH, 1f);

                fillRect(cs, dedX, dedTop - H_HDR, dedW, H_HDR, C_LIGHT_GRAY);
                drawBox(cs, dedX, dedTop - tablesH, dedW, tablesH, 1f);

                float dBodyTop = dedTop - H_HDR;

                float dColAmt = dedX + dedW * 0.55f;
                float dColRem = dedX + dedW * 0.78f;

             // ✅ vertical lines should NOT cross the gray header (start below header)
                line(cs, dColAmt, dBodyTop, dColAmt, dedTop - tablesH, 1f);
                line(cs, dColRem, dBodyTop, dColRem, dedTop - tablesH, 1f);


                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "DEDUCTIONS", (dedX + dColRem) / 2f, dedTop - 9f);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "REMARKS", (dColRem + dedX + dedW) / 2f, dedTop - 9f);

                line(cs, dedX, dBodyTop - H_ROW, dedX + dedW, dBodyTop - H_ROW, 1f);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "DESCR.", (dedX + dColAmt) / 2f, dBodyTop - 11f);
                textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, "AMOUNT", (dColAmt + dColRem) / 2f, dBodyTop - 11f);

                double cashAdv = getDoubleSmart(p,
                        new String[]{"cashAdvance","cashAdv","cashAdvanceAmount","dedCashAdvance","cash_advance"},
                        new String[]{"cashadvance","cash_adv","cash"});
                double smartBill = getDoubleSmart(p,
                        new String[]{"smartBilling","smartBill","smartBillingAmount","dedSmartBilling","smart_billing"},
                        new String[]{"smartbilling","smart_bill","smart"});
                double otherDed = getDoubleSmart(p,
                        new String[]{"otherDeductions","others","otherDeduction","otherDeductionAmount","dedOthers","other_deductions"},
                        new String[]{"otherded","other_ded","others"});

                // ✅ Statutory deductions (employee share)
                double sss = getDoubleSmart(p,
                        new String[]{"sssDeduction","sss","dedSSS","ded_sss"},
                        new String[]{"sss"});
                double pagibig = getDoubleSmart(p,
                        new String[]{"pagibigDeduction","pagIbigDeduction","pagibig","pag-ibig","dedPagibig","ded_pagibig"},
                        new String[]{"pagibig","pag-ibig"});
                double philhealth = getDoubleSmart(p,
                        new String[]{"philhealthDeduction","philHealthDeduction","philhealth","philHealth","dedPhilhealth","ded_philhealth"},
                        new String[]{"philhealth"});

                double deductions = getDoubleSmart(p,
                        new String[]{"deductions","deduction","totalDeductions","totalDeduction","total_deductions"},
                        new String[]{"totaldeduction","total_deductions","deductions"});
                if (deductions == 0) deductions = cashAdv + smartBill + otherDed + sss + pagibig + philhealth;
                String cashAdvNote = getStringSmart(p,
                        new String[]{"cashAdvanceNote","cashAdvNote","cashNote"},
                        new String[]{"cashadvancenote","cashnote"});
                String otherDedNote = getStringSmart(p,
                        new String[]{"otherDeductionNote","othersNote","otherNote"},
                        new String[]{"otherdeductionnote","othersnote","othernote"});

                float dY = dBodyTop - H_ROW;

                // Always show all deductions (even if zero / not activated)
                deductionRowAmount(cs, fonts, dedX, dColAmt, dColRem, dY, "Cash Advance", cashAdv, fonts.isUnicode);
                dY -= H_ROW; line(cs, dedX, dY, dedX + dedW, dY, 1f);

                deductionRowAmount(cs, fonts, dedX, dColAmt, dColRem, dY, "SMART Billing", smartBill, fonts.isUnicode);
                dY -= H_ROW; line(cs, dedX, dY, dedX + dedW, dY, 1f);

                deductionRowAmount(cs, fonts, dedX, dColAmt, dColRem, dY, "SSS", sss, fonts.isUnicode);
                dY -= H_ROW; line(cs, dedX, dY, dedX + dedW, dY, 1f);

                deductionRowAmount(cs, fonts, dedX, dColAmt, dColRem, dY, "Pag-IBIG", pagibig, fonts.isUnicode);
                dY -= H_ROW; line(cs, dedX, dY, dedX + dedW, dY, 1f);

                deductionRowAmount(cs, fonts, dedX, dColAmt, dColRem, dY, "PhilHealth", philhealth, fonts.isUnicode);
                dY -= H_ROW; line(cs, dedX, dY, dedX + dedW, dY, 1f);

                deductionRowAmount(cs, fonts, dedX, dColAmt, dColRem, dY, "Others", otherDed, fonts.isUnicode);
                dY -= H_ROW; line(cs, dedX, dY, dedX + dedW, dY, 1f);
deductionRowAmountBold(cs, fonts, dedX, dColAmt, dColRem, dY, "TOTAL DEDUCTIONS", deductions, fonts.isUnicode);
                dY -= H_ROW; line(cs, dedX, dY, dedX + dedW, dY, 1f);

                // Cash Advance and Others remarks on their own rows.
                textWrap(cs, fonts.reg, FS_SMALL, C_BLACK, (cashAdv > 0 ? safe(cashAdvNote) : ""),
                        dColRem + 4, dBodyTop - H_ROW - 11, (dedX + dedW) - (dColRem + 6), 1);
                textWrap(cs, fonts.reg, FS_SMALL, C_BLACK, (otherDed > 0 ? safe(otherDedNote) : ""),
                        dColRem + 4, dBodyTop - (H_ROW * 6) - 11, (dedX + dedW) - (dColRem + 6), 1);

                y -= (tablesH + 8);

                // ===== NET PAY BAR =====
                float netH = 18f;
                fillRect(cs, earnX, y - netH, earnW, netH, C_BLUE);
                drawBox(cs, earnX, y - netH, earnW, netH, 1f);
                textCenter(cs, fonts.bold, FS_HDR, C_BLACK, "NET PAY", earnX + earnW / 2f, y - 13f);

                y -= (netH + 12);

                double gross = getDouble(p, "grossPay", "gross", "grosspay");
                if (gross == 0) {
                    gross = basicPay + otPay + regHolPay + regHolOtPay + specHolPay + specHolOtPay + incentives + thirteenthMonthPay + bonusPay;
                    if (holidayTotalPay > 0) gross = (basicPay + otPay) + holidayTotalPay + incentives + thirteenthMonthPay + bonusPay;
                }
                if (net == 0) net = gross - deductions;

                text(cs, fonts.bold, FS_SMALL, C_BLACK, "GROSS PAY", x0 + 6, y);
                textRight(cs, fonts.bold, FS_SMALL, C_BLACK, money(gross, fonts.isUnicode), earnX + earnW - 6, y);

                text(cs, fonts.bold, FS_SMALL, C_BLACK, "TOTAL DEDUCTIONS", x0 + 6, y - 12);
                textRight(cs, fonts.bold, FS_SMALL, C_BLACK, money(deductions, fonts.isUnicode), earnX + earnW - 6, y - 12);

                text(cs, fonts.bold, FS_SMALL, C_BLACK, "TOTAL NET PAY", x0 + 6, y - 24);
                textRight(cs, fonts.bold, FS_SMALL, new Color(0xC40000), money(net, fonts.isUnicode), earnX + earnW - 6, y - 24);

                text(cs, fonts.reg, 7f, C_BLACK, "v2.4", x0 + 2, M - 6);
            }

            doc.save(outFile);
        }
    }

    // ===== Rows =====
    private static class EarnRow4 {
        final String descr;
        final double hrs;
        final String rateText;
        final double amount;
        EarnRow4(String descr, double hrs, String rateText, double amount) {
            this.descr = descr;
            this.hrs = hrs;
            this.rateText = rateText;
            this.amount = amount;
        }
    }

    // ===== Fonts =====
    private static class FontPack {
        final PDFont reg;
        final PDFont bold;
        final boolean isUnicode;
        FontPack(PDFont reg, PDFont bold, boolean isUnicode) {
            this.reg = reg;
            this.bold = bold;
            this.isUnicode = isUnicode;
        }
    }

    private static FontPack loadFonts(PDDocument doc) {
        try {
            PDFont reg = loadTTF(doc, "/fonts/DejaVuSans.ttf");
            PDFont bold = loadTTF(doc, "/fonts/DejaVuSans-Bold.ttf");
            if (reg != null && bold != null) return new FontPack(reg, bold, true);
        } catch (Exception ignored) {}
        return new FontPack(PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD, false);
    }

    private static PDFont loadTTF(PDDocument doc, String path) throws IOException {
        try (InputStream in = PDFUtil.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return PDType0Font.load(doc, in, true);
        }
    }
    
 // ✅ Draw image keeping aspect ratio and RIGHT-aligned
    private static void drawImageKeepRatioRight(
            PDDocument doc,
            PDPageContentStream cs,
            String resourcePath,
            float rightEdgeX,
            float y,
            float targetHeight
    ) {
        try (InputStream in = PDFUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) return;

            PDImageXObject img = PDImageXObject.createFromByteArray(
                    doc, in.readAllBytes(), resourcePath
            );

            float imgW = img.getWidth();
            float imgH = img.getHeight();

            float ratio = imgW / imgH;
            float drawW = targetHeight * ratio;

            // ⬅️ RIGHT aligned here
            float drawX = rightEdgeX - drawW;

            cs.drawImage(img, drawX, y, drawW, targetHeight);
        } catch (Exception ignored) {}
    }



    // ===== Drawing =====
    private static void drawImageIfExists(PDDocument doc, PDPageContentStream cs, String resourcePath,
                                         float x, float y, float w, float h) {
        try (InputStream in = PDFUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) return;
            PDImageXObject img = PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "img");
            cs.drawImage(img, x, y, w, h);
        } catch (Exception ignored) {}
    }

    private static void drawBox(PDPageContentStream cs, float x, float y, float w, float h, float stroke) throws IOException {
        cs.setStrokingColor(C_GRAY_LINE);
        cs.setLineWidth(stroke);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private static void fillRect(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
        cs.setNonStrokingColor(Color.BLACK);
    }

    private static void line(PDPageContentStream cs, float x1, float y1, float x2, float y2, float stroke) throws IOException {
        cs.setStrokingColor(C_GRAY_LINE);
        cs.setLineWidth(stroke);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, Color c, String s, float x, float y) throws IOException {
        if (s == null) s = "";
        cs.beginText();
        cs.setNonStrokingColor(c);
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
        cs.setNonStrokingColor(Color.BLACK);
    }

    private static void textCenter(PDPageContentStream cs, PDFont font, float size, Color c, String s, float centerX, float y) throws IOException {
        if (s == null) s = "";
        float w = font.getStringWidth(s) / 1000f * size;
        text(cs, font, size, c, s, centerX - (w / 2f), y);
    }

    private static void textRight(PDPageContentStream cs, PDFont font, float size, Color c, String s, float rightX, float y) throws IOException {
        if (s == null) s = "";
        float w = font.getStringWidth(s) / 1000f * size;
        text(cs, font, size, c, s, rightX - w, y);
    }

 // ✅ Green bar: keep value close to label (no far-right spacing)
    private static void segmentKV(PDPageContentStream cs, FontPack fonts, String label, String value,
                                  float segLeft, float segRight, float y) throws IOException {

        String lab = safe(label);
        String val = safe(value);

        // draw label
        text(cs, fonts.bold, FS_SMALL, C_BLACK, lab, segLeft, y);

        // value starts after label with a small gap
        float gap = 10f;
        float labelW = fonts.bold.getStringWidth(lab) / 1000f * FS_SMALL;
        float valueX = segLeft + labelW + gap;

        // safety: if value would overflow the segment, fall back to right align
        float valueW = fonts.reg.getStringWidth(val) / 1000f * FS_SMALL;
        if (valueX + valueW > segRight) {
            textRight(cs, fonts.reg, FS_SMALL, C_BLACK, val, segRight, y);
        } else {
            text(cs, fonts.reg, FS_SMALL, C_BLACK, val, valueX, y);
        }
    }


    private static void labelValueRow(PDPageContentStream cs, FontPack fonts, float x, float y, String label, String value) throws IOException {
        text(cs, fonts.bold, FS_SMALL, C_BLACK, label, x, y);
        text(cs, fonts.reg, FS_SMALL, C_BLACK, value, x + 165, y);
    }

    private static void labelValueRowWrapped(PDPageContentStream cs, FontPack fonts, float x, float y,
                                            String label, String value, float valueX, float rightEdge) throws IOException {
        text(cs, fonts.bold, FS_SMALL, C_BLACK, label, x, y);
        float maxW = (rightEdge - 4) - valueX;
        textWrap(cs, fonts.reg, FS_SMALL, C_BLACK, value, valueX, y, maxW, 2);
    }

    private static void summaryRow(PDPageContentStream cs, FontPack fonts, float x, float y, String label, String value) throws IOException {
        text(cs, fonts.bold, FS_SMALL, C_BLACK, label, x, y);
        text(cs, fonts.reg, FS_SMALL, C_BLACK, safe(value), x + 120, y);
    }

    // ✅ FIXED: Center text inside each cell box (including AMOUNT)
    private static void earningsRow4(PDPageContentStream cs, FontPack fonts,
                                     float xL, float xDesc, float xHrs, float xRate, float xR, float y,
                                     String descr, String hrs, String rate, String amt) throws IOException {

        float yText = y - 11;

        // DESCR cell = xL .. xDesc
        textCenter(cs, fonts.reg, FS_SMALL, C_BLACK, safe(descr), (xL + xDesc) / 2f, yText);

        // HRS cell = xDesc .. xHrs
        textCenter(cs, fonts.reg, FS_SMALL, C_BLACK, safe(hrs), (xDesc + xHrs) / 2f, yText);

        // RATE cell = xHrs .. xRate
        textCenter(cs, fonts.reg, FS_SMALL, C_BLACK, safe(rate), (xHrs + xRate) / 2f, yText);

        // AMOUNT cell = xRate .. xR
        textCenter(cs, fonts.reg, FS_SMALL, C_BLACK, safe(amt), (xRate + xR) / 2f, yText);
    }

    // ✅ Deductions: center descr and amount inside their columns
    private static void deductionRowAmount(PDPageContentStream cs, FontPack fonts,
                                          float xL, float xAmtCol, float xRemCol, float y,
                                          String descr, double amt, boolean unicodeMoney) throws IOException {

        float yText = y - 11;

        // DESCR cell = xL .. xAmtCol
        textCenter(cs, fonts.reg, FS_SMALL, C_BLACK, safe(descr), (xL + xAmtCol) / 2f, yText);

        // AMOUNT cell = xAmtCol .. xRemCol
        textCenter(cs, fonts.reg, FS_SMALL, C_BLACK, money(amt, unicodeMoney), (xAmtCol + xRemCol) / 2f, yText);
    }

    private static void deductionRowAmountBold(PDPageContentStream cs, FontPack fonts,
                                              float xL, float xAmtCol, float xRemCol, float y,
                                              String descr, double amt, boolean unicodeMoney) throws IOException {

        float yText = y - 11;

        textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, safe(descr), (xL + xAmtCol) / 2f, yText);
        textCenter(cs, fonts.bold, FS_SMALL, C_BLACK, money(amt, unicodeMoney), (xAmtCol + xRemCol) / 2f, yText);
    }

    private static void textWrap(PDPageContentStream cs, PDFont font, float size, Color c,
                                 String text, float x, float y, float maxW, int maxLines) throws IOException {
        String s = safe(text).trim();
        if (s.isEmpty()) return;

        String[] words = s.split("\\s+");
        StringBuilder line = new StringBuilder();
        float curY = y;
        int lines = 0;

        for (String w : words) {
            String test = (line.length() == 0) ? w : (line + " " + w);
            float tw = font.getStringWidth(test) / 1000f * size;
            if (tw <= maxW) {
                line.setLength(0);
                line.append(test);
            } else {
                text(cs, font, size, c, line.toString(), x, curY);
                lines++;
                if (maxLines > 0 && lines >= maxLines) return;
                curY -= (size + 2);
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0) text(cs, font, size, c, line.toString(), x, curY);
    }

    // ===== Formatting =====
    private static String money(double v, boolean unicodeSupported) {
        if (unicodeSupported) return "₱" + DF2.format(v);
        return "PHP " + DF2.format(v);
    }

    // ✅ Always "00h 00m"
    private static String fmtHM(double decimalHours) {
        if (decimalHours <= 0) return "00h 00m";
        int totalMinutes = (int) Math.round(decimalHours * 60.0);
        if (totalMinutes < 0) totalMinutes = 0;
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return String.format(Locale.US, "%02dh %02dm", h, m);
    }

    private static String safe(String s) { return (s == null) ? "" : s; }
    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (notEmpty(v)) return v;
        }
        return null;
    }

    private static String buildFullName(Object empObj) {
        if (empObj == null) return null;

        String direct = getString(empObj, "fullName", "name");
        if (notEmpty(direct) && !direct.contains("@")) return direct;

        String first = getString(empObj, "firstName", "fname", "first");
        String mid = getString(empObj, "middleName", "mname", "middle");
        String last = getString(empObj, "lastName", "lname", "last", "surname");

        StringBuilder sb = new StringBuilder();
        if (notEmpty(first)) sb.append(first.trim());
        if (notEmpty(mid)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(mid.trim());
        }
        if (notEmpty(last)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(last.trim());
        }

        String built = sb.toString().trim();
        if (notEmpty(built)) return built;

        String s = String.valueOf(empObj);
        if (s != null && s.contains("@")) return null;
        return s;
    }

    // ===== Reflection-safe getters =====
    // Backwards-compatible alias (some older patches used getObject name)
    private static Object getObject(Object o, String... names) {
        return getObj(o, names);
    }

    private static Object getObj(Object o, String... names) {
        if (o == null) return null;
        for (String n : names) {
            Object v = tryGet(o, n);
            if (v != null) return v;
        }
        return null;
    }

    private static String getString(Object o, String... names) {
        Object v = getObj(o, names);
        return v == null ? null : String.valueOf(v);
    }

    private static double getDouble(Object o, String... names) {
        Object v = getObj(o, names);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return 0.0; }
    }

    private static Object tryGet(Object o, String name) {
        try {
            String m1 = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Method m = o.getClass().getMethod(m1);
            return m.invoke(o);
        } catch (Exception ignored) {}

        try {
            Field f = o.getClass().getField(name);
            return f.get(o);
        } catch (Exception ignored) {}

        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Exception ignored) {}

        return null;
    }

    // ===== SMART SCAN =====
    private static double getDoubleSmart(Object o, String[] exactNames, String[] containsAnyLower) {
        if (o == null) return 0.0;

        if (exactNames != null) {
            for (String n : exactNames) {
                double v = getDouble(o, n);
                if (v != 0.0) return v;
            }
        }

        String[] keys = (containsAnyLower == null) ? new String[0] : containsAnyLower;

        for (Method m : o.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String mn = m.getName();
            if (!(mn.startsWith("get") || mn.startsWith("is"))) continue;

            String low = mn.toLowerCase(Locale.US);
            if (!containsAny(low, keys)) continue;

            try {
                Object val = m.invoke(o);
                if (val instanceof Number) {
                    double d = ((Number) val).doubleValue();
                    if (d != 0.0) return d;
                }
            } catch (Exception ignored) {}
        }

        for (Field f : o.getClass().getDeclaredFields()) {
            String low = f.getName().toLowerCase(Locale.US);
            if (!containsAny(low, keys)) continue;

            try {
                f.setAccessible(true);
                Object val = f.get(o);
                if (val instanceof Number) {
                    double d = ((Number) val).doubleValue();
                    if (d != 0.0) return d;
                }
            } catch (Exception ignored) {}
        }

        return 0.0;
    }
    private static boolean getBooleanSmart(Object obj, String[] fields, String[] lowerFields) {
        if (obj == null) return false;

        // 1) direct boolean/Boolean fields via reflection
        for (String f : fields) {
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(f);
                field.setAccessible(true);
                Object v = field.get(obj);
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof String) {
                    String s = ((String) v).trim().toLowerCase();
                    if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
                    if ("false".equals(s) || "0".equals(s) || "no".equals(s) || s.isEmpty()) return false;
                }
                if (v instanceof Number) return ((Number) v).intValue() != 0;
            } catch (Exception ignored) {}
        }

        // 2) try case-insensitive match for field names
        try {
            java.lang.reflect.Field[] all = obj.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : all) {
                String name = field.getName();
                if (name == null) continue;
                String ln = name.toLowerCase();
                boolean match = false;
                for (String lf : lowerFields) {
                    if (ln.equals(lf)) { match = true; break; }
                }
                if (!match) continue;

                field.setAccessible(true);
                Object v = field.get(obj);
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof String) {
                    String s = ((String) v).trim().toLowerCase();
                    if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
                    if ("false".equals(s) || "0".equals(s) || "no".equals(s) || s.isEmpty()) return false;
                }
                if (v instanceof Number) return ((Number) v).intValue() != 0;
            }
        } catch (Exception ignored) {}

        return false;
    }



    private static String getStringSmart(Object o, String[] exactNames, String[] containsAnyLower) {
        if (o == null) return null;

        if (exactNames != null) {
            for (String n : exactNames) {
                String s = getString(o, n);
                if (notEmpty(s)) return s;
            }
        }

        String[] keys = (containsAnyLower == null) ? new String[0] : containsAnyLower;

        for (Method m : o.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String mn = m.getName();
            if (!(mn.startsWith("get") || mn.startsWith("is"))) continue;

            String low = mn.toLowerCase(Locale.US);
            if (!containsAny(low, keys)) continue;

            try {
                Object val = m.invoke(o);
                if (val != null) {
                    String s = String.valueOf(val);
                    if (notEmpty(s)) return s;
                }
            } catch (Exception ignored) {}
        }

        for (Field f : o.getClass().getDeclaredFields()) {
            String low = f.getName().toLowerCase(Locale.US);
            if (!containsAny(low, keys)) continue;

            try {
                f.setAccessible(true);
                Object val = f.get(o);
                if (val != null) {
                    String s = String.valueOf(val);
                    if (notEmpty(s)) return s;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static boolean containsAny(String haystackLower, String[] needlesLower) {
        if (haystackLower == null) return false;
        if (needlesLower == null || needlesLower.length == 0) return false;
        for (String k : needlesLower) {
            if (k == null || k.isEmpty()) continue;
            if (haystackLower.contains(k)) return true;
        }
        return false;
    }
}