package automation;

import com.opencsv.CSVReader;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AutomationService {

    public static void main(String[] args) throws Exception {

        WebDriver driver = DriverFactory.createDriver();
        driver.quit();
    }

    private static String getCellValue(@NotNull XSSFSheet sheet, int rowIndex, int colIndex) {
        if (sheet.getRow(rowIndex) == null) return "";
        if (sheet.getRow(rowIndex).getCell(colIndex) == null) return "";
        return sheet.getRow(rowIndex).getCell(colIndex).toString();
    }

    public static String solveCaptcha(WebDriver driver) {
        try {
            WebElement captchaImg = driver.findElement(By.id("loginform-captcha-image"));

            Thread.sleep(2000);
            // Take screenshot of element
            File src = captchaImg.getScreenshotAs(OutputType.FILE);
            File dest = new File("captcha.png");
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            BufferedImage img = ImageIO.read(dest);

            // Convert to grayscale
            BufferedImage gray = new BufferedImage(
                    img.getWidth(),
                    img.getHeight(),
                    BufferedImage.TYPE_BYTE_GRAY
            );

            Graphics g = gray.getGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();

            // Apply threshold (Binarization)
            for (int y = 0; y < gray.getHeight(); y++) {
                for (int x = 0; x < gray.getWidth(); x++) {
                    int pixel = gray.getRGB(x, y) & 0xFF;

                    if (pixel > 150) {
                        gray.setRGB(x, y, 0xFFFFFF); // white
                    } else {
                        gray.setRGB(x, y, 0x000000); // black
                    }
                }
            }

            // Save processed image
            File processed = new File("captcha_clean.png");
            ImageIO.write(gray, "png", processed);

            BufferedImage resized = new BufferedImage(
                    gray.getWidth() * 2,
                    gray.getHeight() * 2,
                    BufferedImage.TYPE_BYTE_GRAY
            );

            Graphics2D g2 = resized.createGraphics();
            g2.drawImage(gray, 0, 0, resized.getWidth(), resized.getHeight(), null);
            g2.dispose();

            ImageIO.write(resized, "png", processed);

            // OCR
            ITesseract image = new Tesseract();
            image.setDatapath(System.getProperty("user.dir") + "\\Tesseract-OCR\\tessdata");

            image.setTessVariable("tessedit_char_whitelist", "lo0123456789+");
            image.setPageSegMode(7); // Treat image as single line

            String result = image.doOCR(processed);

            System.out.println("🔍 Raw OCR Output: " + result);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int extractAndSolve(String text) {

        if (text == null) throw new RuntimeException("OCR returned null");

        System.out.println("🔍 Before cleaning: " + text);

        // 🔧 Normalize common OCR mistakes
        text = text.toLowerCase();

        text = text.replaceAll("[o]", "0");     // o → 0
        text = text.replaceAll("[l|i]", "1");   // l, i → 1
        text = text.replaceAll("t", "1");       // t → 1 (optional)
        text = text.replaceAll("r", "");        // remove noise

        // ✅ Keep only digits and +
        text = text.replaceAll("[^0-9+]", "");

        // 🔥 FIX 1: collapse multiple + into one
        text = text.replaceAll("\\++", "+");

        // 🔥 FIX 2: collapse repeated digits (00 → 0, 11 → 1)
        text = text.replaceAll("0+", "0");
        text = text.replaceAll("1+", "1");

        System.out.println("🧹 Cleaned OCR: " + text);

        String[] parts = text.split("\\+");

        // ✅ CASE 1: Proper format (1+2)
        if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            int num1 = Integer.parseInt(parts[0]);
            int num2 = Integer.parseInt(parts[1]);
            return num1 + num2;
        }

        // ✅ CASE 2: Partial format like "+1" or "1+"
        if (parts.length == 2) {
            if (!parts[0].isEmpty()) {
                return Integer.parseInt(parts[0]); // "1+"
            }
            if (!parts[1].isEmpty()) {
                return Integer.parseInt(parts[1]); // "+1"
            }
        }

        // ✅ CASE 3: Only one number without +
        if (parts.length == 1 && !parts[0].isEmpty()) {
            return Integer.parseInt(parts[0]);
        }

        // ❌ Still invalid
        throw new RuntimeException("Invalid captcha format after cleaning: " + text);
    }

    public static void clickCheckboxIfNeeded(WebDriver driver, WebDriverWait wait, String value) {

        try {
            By checkboxLocator = By.xpath(
                    "//input[@name='columnsMenuItem' and @value='" + value + "']"
            );

            WebElement checkboxInput = wait.until(
                    ExpectedConditions.presenceOfElementLocated(checkboxLocator)
            );

            WebElement label = checkboxInput.findElement(By.xpath("./ancestor::p-checkbox"));

            // if not already checked
            String ariaChecked = checkboxInput.getAttribute("aria-checked");

            if (!"true".equals(ariaChecked)) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", label);
                System.out.println("☑️ Enabled: " + value);
            } else {
                System.out.println("✔ Already enabled: " + value);
            }

        } catch (Exception e) {
            System.out.println("⚠️ Failed to toggle: " + value);
        }
    }

    private static String stripTags(String html) {
        return html.replaceAll("<[^>]*>", "")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .trim();
    }

    // 🧩 Core automation logic shared between Firefox & Chrome
    public static String runAutomation(WebDriver driver, String username, String password, String url, String type) {

        File excelFile;
        try {
            switch (type) {

                case "partner_fiberish":

                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

                    System.out.println("🚀 Opening Partner Fiberish...");
                    driver.get(url);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"username\"]"))).sendKeys(username);
                    driver.findElement(By.xpath("//*[@id=\"password\"]")).sendKeys(password);
                    WebElement loginBtn = wait.until(
                            ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(.,'Sign In')]"))
                    );
                    loginBtn.click();
                    // ⏳ Wait after login click
                    Thread.sleep(2000);

                    // ⏳ Wait for redirect after login
                    boolean loginSuccess = false;

                    try {
                        wait.until(ExpectedConditions.urlContains("/home.html"));
                        loginSuccess = true;
                    } catch (TimeoutException e) {
                        loginSuccess = false;
                    }

                    // ❌ If login failed → stop execution
                    if (!loginSuccess) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        System.out.println("The End");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful, fetching API data...");

                    // 🔥 STEP 2: HIT API DIRECTLY
                    String apiUrl = url + "/Ajax_Request/get_users_json?draw=3&columns[0][data]=user_full_name&columns[1][data]=address&columns[2][data]=package_name&columns[3][data]=seller_name&columns[5][data]=user_expiry_view&start=0&length=500&status=0";

                    JavascriptExecutor js = (JavascriptExecutor) driver;

                    String rawJson = (String) js.executeScript(
                            "return fetch(arguments[0], {credentials: 'include'})" +
                                    ".then(res => res.text())" +
                                    ".then(data => data);",
                            apiUrl
                    );

                    System.out.println("📦 Raw JSON received:\n" + rawJson);

                    System.out.println("📦 Raw JSON received");

                    // 🧠 PARSE JSON
                    JSONObject response = new JSONObject(rawJson);
                    JSONArray data = response.getJSONArray("data");

                    JSONArray finalArray = new JSONArray();

                    for (int i = 0; i < data.length(); i++) {

                        JSONObject row = data.getJSONObject(i);
                        JSONObject obj = new JSONObject();

                        // 🔑 Extract safely
                        String rawName = row.optString("user_full_name", "");

                        // Extract name inside <h6> tag
                        String name = "";

                        Matcher matcher = Pattern.compile("<h6[^>]*>(.*?)</h6>").matcher(rawName);
                        if (matcher.find()) {
                            name = matcher.group(1).trim();
                        }

                        // fallback if not found
                        if (name.isEmpty()) {
                            name = rawName.replaceAll("<.*?>", "").trim();
                        }
                        String address = row.optString("address", "").replaceAll("<.*?>", "").trim();
                        String pkg = row.optString("package_name", "");
                        String expiry = row.optString("user_expiry_view", "").replaceAll("<.*?>", ""); // remove HTML
                        String seller = row.optString("seller_name", "");

                        // Some extra fields (if exist in API)
                        String usernameVal = row.optString("username", "");
                        String mobile = row.optString("mobile", "");
                        String nic = row.optString("nic", "");
                        String regDate = row.optString("reg_date", "");
                        String statusRaw = row.optString("status", "");

                        String status = switch (statusRaw) {
                            case "1" -> "0";
                            case "2" -> "1";
                            default -> statusRaw;
                        };

                        // 🎯 Final structure (your required format)
                        obj.put("int_id", usernameVal);
                        obj.put("name", name);
                        obj.put("manager", seller);
                        obj.put("cnic", nic);
                        obj.put("adrs", address);
                        obj.put("status", status);
                        obj.put("mob", mobile);
                        obj.put("reg", regDate);
                        obj.put("package", pkg);
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", expiry);

                        finalArray.put(obj);
                    }

                    driver.quit();

                    return finalArray.toString();

                case "cp_fiberish":

                    WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    String downloadDir2 = System.getProperty("user.home") + "\\Downloads";

                    String[] targetFiles2 = {
                            "Fiberish Broadband Billing Systems.xlsx",
                            "Fiberish Broadband  Billing Systems.xlsx"
                    };

                    // 🧹 Delete old files BEFORE
                    for (String fileName : targetFiles2) {
                        File f = new File(downloadDir2, fileName);
                        if (f.exists()) {
                            f.delete();
                            System.out.println("🧹 Deleted old file: " + fileName);
                        }
                    }

                    System.out.println("🚀 Opening CP Fiberish...");
                    driver.get(url);

                    wait2.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(username);
                    driver.findElement(By.id("password")).sendKeys(password);
                    driver.findElement(By.id("send")).click();

                    // ⏳ Wait after login click
                    Thread.sleep(2000);

                    // ⏳ Wait for redirect after login
                    loginSuccess = false;

                    try {
                        wait2.until(ExpectedConditions.urlContains("/home.html"));
                        loginSuccess = true;
                    } catch (TimeoutException e) {
                        loginSuccess = false;
                    }

                    // ❌ If login failed → stop execution
                    if (!loginSuccess) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        System.out.println("The End");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful, continuing...");

                    System.out.println("📄 Navigating to user reports...");
                    driver.get(url + "/user/user/all");

                    // ⏳ Wait for table length dropdown
                    // ⏳ wait for page load
                    wait2.until(ExpectedConditions.presenceOfElementLocated(By.id("userListAll")));

                    // ⏳ wait for loader overlay to disappear
                    wait2.until(ExpectedConditions.invisibilityOfElementLocated(By.id("loading")));

                    // 🎯 now safely select dropdown
                    WebElement lengthDropdown = wait2.until(
                            ExpectedConditions.presenceOfElementLocated(By.name("userListAll_length"))
                    );

                    // 🚀 Force set value via JS (bypasses overlay)
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].value='-1'; arguments[0].dispatchEvent(new Event('change'));",
                            lengthDropdown
                    );

                    System.out.println("📊 Set table entries to ALL (JS)");

                    // ⏳ VERY IMPORTANT: wait for table to reload after changing length
                    wait2.until(d -> {
                        List<WebElement> rows = d.findElements(By.cssSelector("#userListAll tbody tr"));
                        return rows.size() > 20;
                    });

                    WebElement excelBtn = wait2.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//button[contains(@class,'buttons-excel')]")
                            )
                    );

                    // scroll (important for hidden/dynamic tables)
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", excelBtn);
                    Thread.sleep(1000);

                    try {
                        excelBtn.click();
                    } catch (Exception e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", excelBtn);
                    }

                    System.out.println("📥 Excel button clicked");

                    // ⏳ Wait for file
                    File latestFile2 = null;
                    int waitTime2 = 0;

                    while (waitTime2 < 40) {

                        File folder = new File(downloadDir2);
                        File[] files = folder.listFiles();

                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().contains("Fiberish Broadband Billing Systems") && f.getName().endsWith(".xlsx")) {

                                    // ✅ Check file is not empty
                                    if (f.length() == 0) {
                                        continue;
                                    }

                                    // ✅ Wait until file size stops changing (download complete)
                                    long size1 = f.length();
                                    Thread.sleep(1000);
                                    long size2 = f.length();

                                    if (size1 == size2) {
                                        latestFile2 = f;
                                        System.out.println("✅ File fully downloaded: " + f.getName());
                                        break;
                                    }
                                }
                            }
                        }

                        if (latestFile2 != null) break;

                        Thread.sleep(1000);
                        waitTime2++;
                    }

                    if (latestFile2.length() < 50) {
                        return "{\"status\":\"error\",\"message\":\"File is empty or not fully downloaded\"}";
                    }

                    // 📊 Convert Excel → JSON
                    FileInputStream fis2 = new FileInputStream(latestFile2);
                    XSSFWorkbook workbook2 = new XSSFWorkbook(fis2);
                    XSSFSheet sheet2 = workbook2.getSheetAt(0);

                    JSONArray jsonArray2 = new JSONArray();

                    for (int i = 2; i <= sheet2.getLastRowNum(); i++) {

                        if (sheet2.getRow(i) == null) continue;

                        JSONObject obj = new JSONObject();
                        obj.put("int_id", getCellValue(sheet2, i, 1));
                        String rawName = getCellValue(sheet2, i, 0);

                        // remove bullet + extra spaces
                        String cleanName = rawName
                                .replace("●", "")     // remove bullet
                                .trim();              // remove spaces

                        obj.put("name", cleanName);
                        obj.put("manager", getCellValue(sheet2, i, 4));
                        obj.put("cnic", "");
                        obj.put("adrs", getCellValue(sheet2, i, 2));
                        obj.put("status", "");
                        obj.put("mob", "");
                        obj.put("reg", "");
                        obj.put("package", getCellValue(sheet2, i, 3));
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", getCellValue(sheet2, i, 6));

                        jsonArray2.put(obj);
                    }

                    workbook2.close();
                    fis2.close();

                    // 🗑️ Delete after processing
                    if (latestFile2.exists()) {
                        latestFile2.delete();
                        System.out.println("🗑️ File deleted after processing");
                    }

                    return jsonArray2.toString();

                case "sky_net":

                    WebDriverWait wait3 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    System.out.println("🚀 Opening SkyNet...");

                    driver.get(url);

                    // 🔐 LOGIN
                    wait3.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//input[@name='username']")
                    )).sendKeys(username);

                    driver.findElement(By.xpath("//input[@name='password']")).sendKeys(password);

                    wait3.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit']//span[contains(text(),'Login')]")
                    )).click();

                    // ⏳ Wait after login click
                    Thread.sleep(2000);

                    // ⏳ Wait for redirect after login
                    loginSuccess = false;

                    try {
                        wait3.until(ExpectedConditions.urlContains("/dashboard"));
                        loginSuccess = true;
                    } catch (TimeoutException e) {
                        loginSuccess = false;
                    }

                    // ❌ If login failed → stop execution
                    if (!loginSuccess) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        System.out.println("The End");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful, continuing...");

                    wait3 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    try {
                        List<WebElement> closeBtns = driver.findElements(
                                By.xpath("//button[contains(@class,'p-dialog-header-close')]")
                        );

                        if (!closeBtns.isEmpty() && closeBtns.get(0).isDisplayed()) {

                            System.out.println("✅ Close popup found, clicking...");

                            WebElement closeBtn = closeBtns.get(0);

                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtn);

                        } else {
                            System.out.println("⚠️ Popup not present, skipping...");
                        }

                    } catch (Exception e) {
                        System.out.println("⚠️ Error while closing popup, skipping...");
                    }

                    // 📄 Navigate to users page
                    driver.get(url + "#/users/index");

                    wait3 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait3.until(ExpectedConditions.urlContains("users"));
                    System.out.println("📄 Navigated to users page");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait3 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    WebElement columnMenuBtn = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(@class,'ng-tns-c60') and .//i[contains(@class,'fa-list')]]")
                    ));

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", columnMenuBtn);
                    System.out.println("📊 Column menu opened");

                    Thread.sleep(1000);

                    wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("div.p-overlaypanel-content")
                    ));

                    clickCheckboxIfNeeded(driver, wait, "phone");        // Mobile
                    clickCheckboxIfNeeded(driver, wait, "address");      // Address
                    clickCheckboxIfNeeded(driver, wait, "national_id");  // National ID

                    try {
                        WebElement rows500 = wait3.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//a[normalize-space()='500']")
                        ));

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", rows500);

                        System.out.println("✅ Selected 500 rows");

                        Thread.sleep(3000); // wait for table reload
                    } catch (Exception e) {
                        System.out.println("⚠️ Could not select 500 rows");
                    }

                    wait3.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//table//tbody/tr")
                    ));

                    List<WebElement> rows = driver.findElements(
                            By.xpath("//table//tbody/tr")
                    );

                    JSONArray jsonArray = new JSONArray();

                    for (WebElement row : rows) {

                        List<WebElement> cols = row.findElements(By.tagName("td"));

                        if (cols.size() < 30) continue; // adjust based on actual table

                        // ⚠️ Adjust indexes based on actual UI
//                        String status = cols.get(2).getText().trim();
                        String username3 = cols.get(4).getText().trim();
                        String firstName = cols.get(5).getText().trim();
                        String lastName = cols.get(6).getText().trim();
                        String expiry = cols.get(7).getText().trim();
                        String parent = cols.get(8).getText().trim();
                        String profile = cols.get(9).getText().trim();
                        String phone = cols.get(22).getText().trim();
                        String address = cols.get(23).getText().trim();
                        String cnic = cols.get(27).getText().trim();

                        LocalDateTime now = LocalDateTime.now();

                        // 🧠 Parse expiry date (adjust format if needed)
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime expiryDateTime = null;

                        try {
                            expiryDateTime = LocalDateTime.parse(expiry, formatter);
                        } catch (DateTimeParseException e) {
                            System.out.println("⚠️ Invalid date format: " + expiry);
                        }

                        String status3 = "0"; // default expired

                        if (expiryDateTime != null && (expiryDateTime.isEqual(now) || expiryDateTime.isAfter(now))) {
                            status3 = "1"; // active
                        }

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", username3);
                        obj.put("name", (firstName + " " + lastName).trim());
                        obj.put("manager", parent);
                        obj.put("cnic", cnic);
                        obj.put("adrs", address);
                        obj.put("status", status3);
                        obj.put("mob", phone);
                        obj.put("reg", "");
                        obj.put("package", profile);
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", expiry);

                        jsonArray.put(obj);
                    }

                    System.out.println("✅ Extracted " + jsonArray.length() + " users");
                    return jsonArray.toString();

                case "connect":

                    WebDriverWait wait4 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    System.out.println("🚀 Opening Connect...");
                    driver.get(url + "login/");

                    try {
                        // 🔐 LOGIN
                        wait4.until(ExpectedConditions.visibilityOfElementLocated(
                                By.cssSelector("input[type='text'].form-control")
                        )).sendKeys(username);

                        driver.findElement(By.cssSelector("input[type='password'].form-control")).sendKeys(password);

                        wait4.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[@type='submit' and contains(@class,'btn-submit')]")
                        )).click();

                        // ⏳ Wait after login click
                        Thread.sleep(1000);

                        // ⏳ Wait for redirect after login
                        loginSuccess = false;

                        try {
                            wait4.until(ExpectedConditions.urlContains("/dashboard"));
                            loginSuccess = true;
                        } catch (TimeoutException e) {
                            loginSuccess = false;
                        }

                        // ❌ If login failed → stop execution
                        if (!loginSuccess) {
                            driver.quit();
                            System.out.println("❌ Wrong Login Credentials");
                            System.out.println("The End");
                            return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                        }

                        System.out.println("✅ Login successful, continuing...");

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"step\":\"login\",\"message\":\""
                                + e.getMessage().replace("\"", "'") + "\"}";
                    }

                    Thread.sleep(1000);

                    try {
                        WebElement closeBtn = wait4.until(ExpectedConditions.elementToBeClickable(
                                By.cssSelector("button.btn-close.ajax-source")
                        ));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtn);
                        System.out.println("✅ Notice modal closed");
                        Thread.sleep(1000);
                    } catch (TimeoutException e) {
                        System.out.println("⚠️ No notice modal appeared, continuing...");
                    }

                    try {
                        // 📄 Navigate to report page
                        System.out.println("📄 Opening report page...");
                        driver.get(url + "/customers/report");

                        wait4.until(ExpectedConditions.visibilityOfElementLocated(
                                By.xpath("//table")
                        ));

                        // 👉 Scroll horizontally (important)
                        ((JavascriptExecutor) driver).executeScript("window.scrollBy(2000,0)");
                        Thread.sleep(1000);

                        // 🔍 Find ALL user links (U column)
                        List<WebElement> userLinks = driver.findElements(
                                By.xpath("//a[contains(@href,'user-list')]")
                        );

                        if (userLinks.size() == 0) {
                            throw new Exception("No user-list links found");
                        }

                        // 👉 Pick the BIGGEST one (most users)
                        WebElement targetLink = userLinks.stream()
                                .filter(e -> !e.getText().trim().isEmpty())
                                .max(Comparator.comparingInt(e -> {
                                    try {
                                        return Integer.parseInt(e.getText().trim());
                                    } catch (Exception ex) {
                                        return 0;
                                    }
                                }))
                                .orElseThrow(() -> new Exception("No valid user link found"));

                        String totalCount = targetLink.getText().trim();
                        System.out.println("🎯 Clicking Users count: " + totalCount);

                        // 🆕 Handle new tab
                        String mainWindow = driver.getWindowHandle();

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", targetLink);

                        // wait for new tab
                        wait4.until(d -> d.getWindowHandles().size() > 1);

                        // 🔥 SAFE SWITCH
                        Set<String> handles = driver.getWindowHandles();
                        handles.remove(mainWindow);

                        String newTab = handles.iterator().next();

                        driver.switchTo().window(newTab);

                        // 🔥 WAIT FOR PAGE LOAD PROPERLY
                        wait4.until(d -> ((JavascriptExecutor) d)
                                .executeScript("return document.readyState").equals("complete"));

                        System.out.println("🆕 Switched to user list tab");

                        // 📊 Wait for table
                        wait4.until(ExpectedConditions.visibilityOfElementLocated(
                                By.xpath("//tbody/tr")
                        ));

                        wait4.until(ExpectedConditions.visibilityOfElementLocated(
                                By.xpath("//tbody/tr")
                        ));

                        String tableHtml = (String) ((JavascriptExecutor) driver)
                                .executeScript("return document.querySelector('tbody').innerHTML;");

                        // Split rows first
                        String[] rowBlocks = tableHtml.split("<tr>");

                        // Filter out empty rows upfront
                        List<String> validRows = new ArrayList<>();
                        for (String row : rowBlocks) {
                            if (!row.trim().isEmpty()) validRows.add(row);
                        }

                        int totalRows = validRows.size();
                        int chunkSize = 1000;
                        int numThreads = (int) Math.ceil((double) totalRows / chunkSize);

                        System.out.println("📊 Total rows: " + totalRows + " | Threads: " + numThreads);

                        // Thread-safe list to collect results
                        List<JSONObject> resultList = Collections.synchronizedList(new ArrayList<>());

                        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
                        List<Future<?>> futures = new ArrayList<>();

                        for (int t = 0; t < numThreads; t++) {
                            int start = t * chunkSize;
                            int end = Math.min(start + chunkSize, totalRows);
                            List<String> chunk = validRows.subList(start, end);

                            futures.add(executor.submit(() -> {
                                for (String row : chunk) {
                                    try {
                                        String[] cols = row.split("<td[^>]*>");
                                        if (cols.length < 11) continue;

                                        String pkg = stripTags(cols[2]);
                                        String statusText = stripTags(cols[3]);
                                        String status = statusText.equalsIgnoreCase("Active") ? "1" : "0";
                                        String userName = stripTags(cols[4]);
                                        String name = stripTags(cols[5]);
                                        String mobile = stripTags(cols[6]);
                                        String cnic = stripTags(cols[7]);
                                        String address1 = stripTags(cols[8]);
                                        String address2 = stripTags(cols[9]);
                                        String address = (address1 + " " + address2).trim();
                                        String expiry = stripTags(cols[10]);
                                        String addedOn = cols.length > 11 ? stripTags(cols[11]) : "";

                                        if (userName.isEmpty()) continue;

                                        JSONObject obj = new JSONObject();
                                        obj.put("int_id", userName);
                                        obj.put("name", name);
                                        obj.put("manager", "");
                                        obj.put("cnic", cnic);
                                        obj.put("adrs", address);
                                        obj.put("status", status);
                                        obj.put("mob", mobile);
                                        obj.put("reg", addedOn);
                                        obj.put("package", pkg);
                                        obj.put("rech_dt", "");
                                        obj.put("exp_dt", expiry);

                                        resultList.add(obj);

                                    } catch (Exception ex) {
                                        System.out.println("⚠️ Row parse error: " + ex.getMessage());
                                    }
                                }
                            }));
                        }

                        // Wait for ALL threads to finish
                        for (Future<?> f : futures) {
                            f.get();
                        }
                        executor.shutdown();

                        // Build final JSON
                        JSONArray jsonArray4 = new JSONArray();
                        for (JSONObject obj : resultList) {
                            jsonArray4.put(obj);
                        }

                        System.out.println("✅ Extracted users: " + jsonArray4.length());
                        return jsonArray4.toString();
                    } catch (Exception e) {

                        System.out.println("❌ ERROR IN CONNECT FLOW");
                        e.printStackTrace();

                        return "{\"status\":\"error\",\"step\":\"connect_flow\",\"message\":\""
                                + e.getMessage().replace("\"", "'") + "\"}";
                    }

                case "national":

                    WebDriverWait wait5 = new WebDriverWait(driver, Duration.ofSeconds(20));
                    String downloadDir5 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old file if exists
                    File folder5 = new File(downloadDir5);
                    File[] files5 = folder5.listFiles();

                    if (files5 != null) {
                        for (File f : files5) {
                            if (f.getName().contains("Customer List")
                                    && (f.getName().contains("-south") || f.getName().contains("-north"))
                                    && f.getName().endsWith(".csv")) {

                                f.delete();
                                System.out.println("🧹 Deleted old file: " + f.getName());
                            }
                        }
                    }

                    System.out.println("🚀 Opening National Portal...");

                    int maxAttempts = 3;
                    loginSuccess = false;

                    String currentUrl5 = null;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {

                        System.out.println("🔁 Login Attempt: " + attempt);

                        driver.get(url);

                        // 🔐 Enter username & password
                        wait5.until(ExpectedConditions.visibilityOfElementLocated(
                                By.id("loginform-username")
                        )).sendKeys(username);

                        driver.findElement(By.id("loginform-password")).sendKeys(password);

                        // 🧠 Solve captcha using OCR
                        String ocrText = solveCaptcha(driver);

                        if (ocrText == null || ocrText.isEmpty()) {
                            System.out.println("⚠️ OCR failed, retrying...");
                            continue;
                        }

                        int result;

                        try {
                            result = extractAndSolve(ocrText);
                            System.out.println("🧮 Captcha solved: " + result);
                        } catch (Exception e) {
                            System.out.println("⚠️ Captcha parse failed, retrying...");
                            continue;
                        }

                        // ✍️ Enter captcha
                        driver.findElement(By.id("loginform-captcha")).clear();
                        driver.findElement(By.id("loginform-captcha")).sendKeys(String.valueOf(result));

                        // 🔘 Click login
                        driver.findElement(By.xpath("//button[@type='submit']")).click();

                        // ⏳ Wait after login
                        Thread.sleep(7000);

                        // 🌐 Get current URL
                        currentUrl5 = driver.getCurrentUrl();
                        System.out.println("🌐 Current URL: " + currentUrl5);

                        // ❗ Check captcha error message
                        boolean captchaError = false;

                        try {
                            WebElement captchaErrorElement = driver.findElement(
                                    By.xpath("//*[contains(text(),'The verification code is incorrect.')]")
                            );
                            if (captchaErrorElement.isDisplayed()) {
                                captchaError = true;
                            }
                        } catch (NoSuchElementException ignored) {
                        }

                        // ❌ CASE 1: CAPTCHA WRONG
                        if (!captchaError) {
                            loginSuccess = true;
                            break;
                        }

                        System.out.println("❌ Login failed, retrying...");
                    }

                    if (!loginSuccess) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login failed after retries\"}";
                    }

                    if (!currentUrl5.equals("https://partner.nationalbroadband.pk/")) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    // ✅ LOGIN SUCCESS
                    System.out.println("✅ National login successful!");

                    // ❌ Close popup if appears
                    try {
                        WebElement closePopup = wait5.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[@class='close']")
                        ));
                        closePopup.click();
                        System.out.println("✅ Popup closed");
                    } catch (TimeoutException e) {
                        System.out.println("⚠️ No popup appeared");
                    }
                    System.out.println("📄 Navigating to customers page...");
                    driver.get(url + "/customer/customers");

                    wait5.until(d -> d.getCurrentUrl().contains("customer/customers"));
                    System.out.println("✅ Customers page loaded");

                    wait5.until(webDriver ->
                            ((JavascriptExecutor) webDriver)
                                    .executeScript("return document.readyState")
                                    .equals("complete")
                    );

                    System.out.println("🔍 Clicking Search button...");

                    try {
                        wait5.until(ExpectedConditions.elementToBeClickable(By.id("btnSubmit"))).click();
                    } catch (StaleElementReferenceException e) {
                        System.out.println("♻️ Retrying search click");
                        wait5.until(ExpectedConditions.elementToBeClickable(By.id("btnSubmit"))).click();
                    }

                    System.out.println("✅ Search clicked");

                    System.out.println("🔍 Search clicked");

                    Thread.sleep(4000);

                    wait5.until(ExpectedConditions.elementToBeClickable(By.id("btnExport")));

                    // 📥 Click Export
                    System.out.println("📥 Clicking Export button...");

                    try {
                        wait5.until(ExpectedConditions.elementToBeClickable(By.id("btnExport"))).click();
                    } catch (StaleElementReferenceException e) {
                        System.out.println("♻️ Retrying export click");
                        wait5.until(ExpectedConditions.elementToBeClickable(By.id("btnExport"))).click();
                    }

                    System.out.println("📥 Export button clicked");

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile5 = null;
                    int waitTime5 = 0;

                    while (waitTime5 < 40) {

                        File folder = new File(downloadDir5);
                        File[] files = folder.listFiles();

                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().contains("Customer List") && f.getName().endsWith(".csv")) {

                                    // ✅ Check file is not empty
                                    if (f.length() == 0) {
                                        continue;
                                    }

                                    // ✅ Wait until file size stops changing (download complete)
                                    long size1 = f.length();
                                    Thread.sleep(1000);
                                    long size2 = f.length();

                                    if (size1 == size2) {
                                        latestFile5 = f;
                                        System.out.println("✅ File fully downloaded: " + f.getName());
                                        break;
                                    }
                                }
                            }
                        }

                        if (latestFile5 != null) break;

                        Thread.sleep(1000);
                        waitTime5++;
                    }

                    if (latestFile5.length() < 50) {
                        return "{\"status\":\"error\",\"message\":\"File is empty or not fully downloaded\"}";
                    }
                    // 📊 CONVERT EXCEL → JSON
                    CSVReader reader = new CSVReader(new FileReader(latestFile5));
                    String[] data5;

                    JSONArray jsonArray5 = new JSONArray();
                    boolean isHeader = true;

                    while ((data5 = reader.readNext()) != null) {

                        if (isHeader) {
                            isHeader = false;
                            continue;
                        }

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", data5.length > 3 ? data5[3] : "");
                        obj.put("name", data5.length > 5 ? data5[5] : "");
                        obj.put("manager", "");
                        obj.put("cnic", data5.length > 6 ? data5[6] : "");
                        obj.put("adrs", data5.length > 7 ? data5[7] : "");
                        obj.put("status", data5.length > 8 ? data5[8] : "");
                        obj.put("mob", data5.length > 12 ? data5[12] : "");
                        obj.put("reg", data5.length > 15 ? data5[15] : "");
                        obj.put("package", data5.length > 17 ? data5[17] : "");
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", data5.length > 23 ? data5[23] : "");

                        jsonArray5.put(obj);
                    }

                    reader.close();

                    // 🗑️ DELETE FILE
                    if (latestFile5.exists()) {
                        latestFile5.delete();
                        System.out.println("🗑️ national customer list file deleted");
                    }

                    System.out.println("✅ Done");

                    return jsonArray5.toString();

                case "wancom":

                    WebDriverWait wait6 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    System.out.println("🚀 Opening Wancom...");

                    driver.get(url);

                    // 🔐 LOGIN
                    wait6.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//input[@name='username']")
                    )).sendKeys(username);

                    driver.findElement(By.xpath("//input[@name='password']")).sendKeys(password);

                    wait6.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit']//span[contains(text(),'Login')]")
                    )).click();

                    // ⏳ Wait after login click
                    Thread.sleep(2000);

                    // ⏳ Wait for redirect after login
                    loginSuccess = false;

                    try {
                        wait6.until(ExpectedConditions.urlContains("/dashboard"));
                        loginSuccess = true;
                    } catch (TimeoutException e) {
                        loginSuccess = false;
                    }

                    // ❌ If login failed → stop execution
                    if (!loginSuccess) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        System.out.println("The End");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful, continuing...");

                    wait6 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    // 📄 Navigate to report page
                    driver.get(url + "#/users/index");

                    wait6 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait6.until(ExpectedConditions.urlContains("users"));
                    System.out.println("📄 Navigated to users page");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait6 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    columnMenuBtn = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(@class,'ng-tns-c60') and .//i[contains(@class,'fa-list')]]")
                    ));

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", columnMenuBtn);
                    System.out.println("📊 Column menu opened");

                    Thread.sleep(1000);

                    wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("div.p-overlaypanel-content")
                    ));

                    clickCheckboxIfNeeded(driver, wait, "phone");        // Mobile
                    clickCheckboxIfNeeded(driver, wait, "address");      // Address
                    clickCheckboxIfNeeded(driver, wait, "national_id");  // National ID

                    try {
                        WebElement rows500 = wait6.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//a[normalize-space()='500']")
                        ));

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", rows500);

                        System.out.println("✅ Selected 500 rows");

                        Thread.sleep(3000); // wait for table reload
                    } catch (Exception e) {
                        System.out.println("⚠️ Could not select 500 rows");
                    }

                    wait6.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//table//tbody/tr")
                    ));

                    rows = driver.findElements(
                            By.xpath("//table//tbody/tr")
                    );

                    jsonArray = new JSONArray();

                    for (WebElement row : rows) {

                        List<WebElement> cols = row.findElements(By.tagName("td"));

                        if (cols.size() < 30) continue; // adjust based on actual table

                        // ⚠️ Adjust indexes based on actual UI
//                        String status = cols.get(2).getText().trim();
                        String username6 = cols.get(4).getText().trim();
                        String firstName = cols.get(5).getText().trim();
                        String lastName = cols.get(6).getText().trim();
                        String expiry = cols.get(7).getText().trim();
                        String parent = cols.get(8).getText().trim();
                        String profile = cols.get(9).getText().trim();
                        String phone = cols.get(22).getText().trim();
                        String address = cols.get(23).getText().trim();
                        String cnic = cols.get(27).getText().trim();

                        LocalDateTime now = LocalDateTime.now();

                        // 🧠 Parse expiry date (adjust format if needed)
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime expiryDateTime = null;

                        try {
                            expiryDateTime = LocalDateTime.parse(expiry, formatter);
                        } catch (DateTimeParseException e) {
                            System.out.println("⚠️ Invalid date format: " + expiry);
                        }

                        String status6 = "0"; // default expired

                        if (expiryDateTime != null && (expiryDateTime.isEqual(now) || expiryDateTime.isAfter(now))) {
                            status6 = "1"; // active
                        }

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", username6);
                        obj.put("name", (firstName + " " + lastName).trim());
                        obj.put("manager", parent);
                        obj.put("cnic", cnic);
                        obj.put("adrs", address);
                        obj.put("status", status6);
                        obj.put("mob", phone);
                        obj.put("reg", "");
                        obj.put("package", profile);
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", expiry);

                        jsonArray.put(obj);
                    }

                    System.out.println("✅ Extracted " + jsonArray.length() + " users");
                    return jsonArray.toString();

                case "mak_net":

                    WebDriverWait wait7 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    String downloadDir7 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old file if exists
                    String[] targetFiles7 = {"sas4_export.xlsx"};

                    // 🧹 Delete old files BEFORE
                    for (String fileName : targetFiles7) {
                        File f = new File(downloadDir7, fileName);
                        if (f.exists()) {
                            f.delete();
                            System.out.println("🧹 Deleted old file: " + fileName);
                        }
                    }

                    System.out.println("🚀 Opening MakNet...");

                    driver.get(url);

                    // 🔐 LOGIN
                    wait7.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//input[@name='username']")
                    )).sendKeys(username);

                    driver.findElement(By.xpath("//input[@name='password']")).sendKeys(password);

                    wait7.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit']//span[contains(text(),'Login')]")
                    )).click();

                    // ⏳ Wait after login click
                    Thread.sleep(2000);

                    // ⏳ Wait for redirect after login
                    loginSuccess = false;

                    try {
                        wait7.until(ExpectedConditions.urlContains("/dashboard"));
                        loginSuccess = true;
                    } catch (TimeoutException e) {
                        loginSuccess = false;
                    }

                    // ❌ If login failed → stop execution
                    if (!loginSuccess) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        System.out.println("The End");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful, continuing...");

                    try {
                        WebDriverWait popupWait = new WebDriverWait(driver, Duration.ofSeconds(5));

                        WebElement closeBtn = popupWait.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[contains(@class,'p-dialog-header-close')]")
                        ));

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtn);

                        System.out.println("❌ Popup closed");

                        // optional small wait to let UI settle
                        Thread.sleep(1000);

                    } catch (TimeoutException e) {
                        System.out.println("ℹ️ No popup found, continuing...");
                    } catch (Exception e) {
                        System.out.println("⚠️ Tried closing popup but failed, continuing...");
                    }

                    wait7 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    driver.get(url + "#/users/index");

                    wait7 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait7.until(ExpectedConditions.urlContains("users"));
                    System.out.println("📄 Navigated to users page");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait7 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    columnMenuBtn = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(@class,'ng-tns-c60') and .//i[contains(@class,'fa-list')]]")
                    ));

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", columnMenuBtn);
                    System.out.println("📊 Column menu opened");

                    Thread.sleep(1000);

                    wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("div.p-overlaypanel-content")
                    ));

                    clickCheckboxIfNeeded(driver, wait, "phone");        // Mobile
                    clickCheckboxIfNeeded(driver, wait, "address");      // Address
                    clickCheckboxIfNeeded(driver, wait, "national_id");  // National ID

                    try {
                        WebElement rows500 = wait7.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//a[normalize-space()='500']")
                        ));

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", rows500);

                        System.out.println("✅ Selected 500 rows");

                        Thread.sleep(3000); // wait for table reload
                    } catch (Exception e) {
                        System.out.println("⚠️ Could not select 500 rows");
                    }

                    wait7.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//table//tbody/tr")
                    ));

                    rows = driver.findElements(
                            By.xpath("//table//tbody/tr")
                    );

                    jsonArray = new JSONArray();

                    for (WebElement row : rows) {

                        List<WebElement> cols = row.findElements(By.tagName("td"));

                        if (cols.size() < 30) continue; // adjust based on actual table

                        // ⚠️ Adjust indexes based on actual UI
//                        String status = cols.get(2).getText().trim();
                        String username7 = cols.get(4).getText().trim();
                        String firstName = cols.get(5).getText().trim();
                        String lastName = cols.get(6).getText().trim();
                        String expiry = cols.get(7).getText().trim();
                        String parent = cols.get(8).getText().trim();
                        String profile = cols.get(9).getText().trim();
                        String phone = cols.get(22).getText().trim();
                        String address = cols.get(23).getText().trim();
                        String cnic = cols.get(27).getText().trim();

                        LocalDateTime now = LocalDateTime.now();

                        // 🧠 Parse expiry date (adjust format if needed)
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime expiryDateTime = null;

                        try {
                            expiryDateTime = LocalDateTime.parse(expiry, formatter);
                        } catch (DateTimeParseException e) {
                            System.out.println("⚠️ Invalid date format: " + expiry);
                        }

                        String status7 = "0"; // default expired

                        if (expiryDateTime != null && (expiryDateTime.isEqual(now) || expiryDateTime.isAfter(now))) {
                            status7 = "1"; // active
                        }

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", username7);
                        obj.put("name", (firstName + " " + lastName).trim());
                        obj.put("manager", parent);
                        obj.put("cnic", cnic);
                        obj.put("adrs", address);
                        obj.put("status", status7);
                        obj.put("mob", phone);
                        obj.put("reg", "");
                        obj.put("package", profile);
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", expiry);

                        jsonArray.put(obj);
                    }

                    System.out.println("✅ Extracted " + jsonArray.length() + " users");
                    return jsonArray.toString();

                case "daddy_sas":

                    WebDriverWait wait8 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    String downloadDir8 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old files containing "users"
                    File folder8 = new File(downloadDir8);
                    File[] files8 = folder8.listFiles();

                    if (files8 != null) {
                        for (File f : files8) {
                            if (f.getName().toLowerCase().contains("users") && f.getName().endsWith(".xlsx")) {
                                f.delete();
                                System.out.println("🧹 Deleted old file: " + f.getName());
                            }
                        }
                    }

                    System.out.println("🚀 Opening Daddy SAS...");

                    driver.get(url);

                    // 🔐 LOGIN
                    try {
                        wait8.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(username);
                        driver.findElement(By.id("password")).sendKeys(password);

                        wait8.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[contains(@class,'btn_login')]")
                        )).click();

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login elements not found\"}";
                    }

                    // ⏳ WAIT FOR LOGIN SUCCESS
                    boolean loginSuccess8 = false;

                    try {
                        wait8.until(ExpectedConditions.urlContains("/dashboard"));
                        loginSuccess8 = true;
                    } catch (TimeoutException e) {
                        loginSuccess8 = false;
                    }

                    if (!loginSuccess8) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful");

                    // 📄 Navigate to subscribers page
                    driver.get(url + "dashboard/subscribers");

                    // 📥 Click Export Button
                    try {
                        WebElement exportBtn8 = wait8.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//a[contains(@class,'btn_export')]")
                                )
                        );

                        exportBtn8.click();

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Export button not found\"}";
                    }

                    // ✅ Validate URL
                    try {
                        wait8.until(ExpectedConditions.urlContains("/dashboard/report-data-export-jobs"));
                    } catch (TimeoutException e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Did not navigate to export jobs page\"}";
                    }

                    Thread.sleep(4000);

                    // 🔄 Refresh once
                    driver.navigate().refresh();

                    wait8.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//div[contains(@class,'MuiDataGrid-root')]")
                    ));

                    try {
                        wait8.until(ExpectedConditions.presenceOfElementLocated(
                                By.xpath("//div[contains(@class,'MuiDataGrid-row')]")
                        ));
                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Table rows not loaded\"}";
                    }

                    // ☑️ Click first checkbox
                    // ☑️ Click FIRST ROW checkbox (STRICT targeting)
                    try {

                        // wait for first row specifically
                        WebElement firstRow = wait8.until(
                                ExpectedConditions.presenceOfElementLocated(
                                        By.xpath("(//div[@role='row' and @data-rowindex='0'])[1]")
                                )
                        );

                        // find checkbox inside that row
                        WebElement checkbox8 = firstRow.findElement(
                                By.xpath(".//input[@type='checkbox']")
                        );

                        // scroll into view (IMPORTANT for MUI)
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", checkbox8);

                        Thread.sleep(500);

                        // click using JS
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", checkbox8);

                        // ✅ VERIFY CHECKED
                        Thread.sleep(1000);

                        boolean isChecked = checkbox8.isSelected();

                        if (!isChecked) {
                            driver.quit();
                            return "{\"status\":\"error\",\"message\":\"Checkbox click failed (not selected)\"}";
                        }

                        System.out.println("✅ Checkbox clicked successfully");

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Checkbox not found or not clickable\"}";
                    }

                    // ⚙️ Click Actions button
                    try {
                        WebElement actionsBtn8 = wait8.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//button[contains(@class,'btn_actions')]")
                                )
                        );

                        actionsBtn8.click();

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Actions button not found\"}";
                    }

                    // 📥 Click Download option
                    try {
                        WebElement downloadOption8 = wait8.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//div[contains(@class,'box_utils')]//div[contains(@class,'item')]")
                                )
                        );

                        downloadOption8.click();

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Download option not found\"}";
                    }

                    System.out.println("📥 Download triggered");

                    // ⏳ WAIT FOR FILE (contains "users")
                    File latestFile8 = null;
                    int waitTime8 = 0;

                    while (waitTime8 < 25) {

                        File[] files = new File(downloadDir8).listFiles();

                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().contains("users") && f.getName().endsWith(".xlsx")) {
                                    latestFile8 = f;
                                    System.out.println("✅ Found file: " + f.getName());
                                    break;
                                }
                            }
                        }

                        if (latestFile8 != null) break;

                        Thread.sleep(1000);
                        waitTime8++;
                    }

                    if (latestFile8 == null) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }

                    // 📊 CONVERT EXCEL → JSON
                    FileInputStream fis8 = new FileInputStream(latestFile8);
                    XSSFWorkbook workbook8 = new XSSFWorkbook(fis8);
                    XSSFSheet sheet8 = workbook8.getSheetAt(0);

                    JSONArray jsonArray8 = new JSONArray();

                    for (int i = 1; i <= sheet8.getLastRowNum(); i++) {

                        if (sheet8.getRow(i) == null) continue;

                        JSONObject obj = new JSONObject();

                        String firstName = String.valueOf(getCellValue(sheet8, i, 2)).trim();
                        String lastName = String.valueOf(getCellValue(sheet8, i, 3)).trim();

                        obj.put("int_id", getCellValue(sheet8, i, 1));
                        obj.put("name", (firstName + " " + lastName).replaceAll("null", "").trim());
                        obj.put("manager", "");
                        obj.put("cnic", getCellValue(sheet8, i, 19));
                        obj.put("adrs", getCellValue(sheet8, i, 16));
                        obj.put("status", getCellValue(sheet8, i, 10));
                        obj.put("mob", getCellValue(sheet8, i, 4));
                        obj.put("reg", "");
                        obj.put("package", getCellValue(sheet8, i, 12));
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", getCellValue(sheet8, i, 10));

                        jsonArray8.put(obj);
                    }

                    workbook8.close();
                    fis8.close();

                    // 🗑️ Delete file
                    if (latestFile8.exists()) {
                        latestFile8.delete();
                        System.out.println("🗑️ File deleted");
                    }

                    System.out.println("🎯 Daddy SAS completed");

                    return jsonArray8.toString();

                case "turbo_zong":

                    WebDriverWait wait9 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    String downloadDir9 = System.getProperty("user.home") + "\\Downloads";

                    String targetFileName9 = "Customers List NBB1BAHWANA Zong Turbo Net.xlsx";

                    // 🧹 Delete old file
                    File oldFile9 = new File(downloadDir9, targetFileName9);
                    if (oldFile9.exists()) {
                        oldFile9.delete();
                        System.out.println("🧹 Deleted old file: " + targetFileName9);
                    }

                    System.out.println("🚀 Opening Zong Turbo...");

                    driver.get(url);

                    // 🔐 LOGIN
                    try {
                        wait9.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(username);
                        driver.findElement(By.id("password")).sendKeys(password);

                        wait9.until(ExpectedConditions.elementToBeClickable(
                                By.id("signin")
                        )).click();

                        System.out.println("🔐 Login button clicked");

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login elements not found\"}";
                    }

                    // ⏳ Wait after login
                    Thread.sleep(2000);

                    // ✅ Basic login check (URL change or page load)
                    try {
                        wait9.until(ExpectedConditions.urlContains("index_manager.php"));
                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login failed or page not loaded\"}";
                    }

                    System.out.println("✅ Login successful");

                    // 📄 Navigate to customers page
                    driver.get(url + "/customers.php");

                    try {
                        wait9.until(ExpectedConditions.presenceOfElementLocated(
                                By.xpath("//button[contains(@class,'buttons-excel')]")
                        ));
                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Customers page not loaded\"}";
                    }

                    System.out.println("📄 Navigated to customers page");

                    Thread.sleep(5000);

                    // 📥 Click Excel button
                    try {
                        WebElement excelBtn9 = wait9.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//button[contains(@class,'buttons-excel')]")
                                )
                        );

                        excelBtn9.click();

                        System.out.println("📥 Excel button clicked");

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Excel button not found\"}";
                    }

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile9 = null;
                    int waitTime9 = 0;

                    while (waitTime9 < 25) {

                        File f = new File(downloadDir9, targetFileName9);

                        if (f.exists()) {
                            latestFile9 = f;
                            System.out.println("✅ Found file: " + targetFileName9);
                            break;
                        }

                        Thread.sleep(1000);
                        waitTime9++;
                    }

                    if (latestFile9 == null) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }

                    // 📊 CONVERT EXCEL → JSON
                    FileInputStream fis9 = new FileInputStream(latestFile9);
                    XSSFWorkbook workbook9 = new XSSFWorkbook(fis9);
                    XSSFSheet sheet9 = workbook9.getSheetAt(0);

                    JSONArray jsonArray9 = new JSONArray();

                    for (int i = 2; i <= sheet9.getLastRowNum(); i++) {

                        if (sheet9.getRow(i) == null) continue;

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", getCellValue(sheet9, i, 3));
                        obj.put("name", getCellValue(sheet9, i, 4));
                        obj.put("manager", getCellValue(sheet9, i, 2));
                        obj.put("cnic", getCellValue(sheet9, i, 6));
                        obj.put("adrs", getCellValue(sheet9, i, 12));
                        obj.put("status", "");
                        obj.put("mob", getCellValue(sheet9, i, 5));
                        obj.put("reg", getCellValue(sheet9, i, 8));
                        obj.put("package", getCellValue(sheet9, i, 10));
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", getCellValue(sheet9, i, 9));

                        jsonArray9.put(obj);
                    }

                    workbook9.close();
                    fis9.close();

                    // 🗑️ Delete file
                    if (latestFile9.exists()) {
                        latestFile9.delete();
                        System.out.println("🗑️ File deleted");
                    }

                    System.out.println("🎯 Zong Turbo completed");

                    return jsonArray9.toString();

                case "galaxy":

                    WebDriverWait wait10 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    String downloadDir10 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old file if exists
                    File folder10 = new File(downloadDir10);
                    File[] files10 = folder10.listFiles();

                    if (files10 != null) {
                        for (File f : files10) {
                            if (f.getName().toLowerCase().contains("users") && f.getName().endsWith(".xlsx")) {
                                f.delete();
                                System.out.println("🧹 Deleted old file: " + f.getName());
                            }
                        }
                    }

                    System.out.println("🚀 Opening Galaxy...");

                    driver.get(url);

                    // 🔐 LOGIN
                    wait10.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//input[@name='username']")
                    )).sendKeys(username);

                    driver.findElement(By.xpath("//input[@name='password']")).sendKeys(password);

                    wait10.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit']//span[contains(text(),'Login')]")
                    )).click();

                    // ⏳ Wait after login click
                    Thread.sleep(2000);

                    // ⏳ Wait for redirect after login
                    loginSuccess = false;

                    try {
                        wait10.until(ExpectedConditions.urlContains("/dashboard"));
                        loginSuccess = true;
                    } catch (TimeoutException e) {
                        loginSuccess = false;
                    }

                    // ❌ If login failed → stop execution
                    if (!loginSuccess) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        System.out.println("The End");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful, continuing...");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    try {
                        List<WebElement> closeBtns = driver.findElements(
                                By.xpath("//button[contains(@class,'p-dialog-header-close')]")
                        );

                        if (!closeBtns.isEmpty() && closeBtns.get(0).isDisplayed()) {

                            System.out.println("✅ Close popup found, clicking...");

                            WebElement closeBtn = closeBtns.get(0);

                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtn);

                        } else {
                            System.out.println("⚠️ Popup not present, skipping...");
                        }

                    } catch (Exception e) {
                        System.out.println("⚠️ Error while closing popup, skipping...");
                    }

                    // 📄 Navigate to report page
                    driver.get(url + "#/users/index");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait10.until(ExpectedConditions.urlContains("#/users/index"));
                    System.out.println("📄 Navigated to activations report");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    // 📥 Click Export Button
                    WebElement exportBtn = wait10.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//a[.//i[contains(@class,'fa-file-export')]]")
                            )
                    );

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", exportBtn);

                    System.out.println("📥 Export button clicked");

                    try {
                        wait10.until(ExpectedConditions.urlContains("#/report/dataExportJobs"));
                    } catch (TimeoutException e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Did not navigate to export jobs page\"}";
                    }

                    Thread.sleep(4000);

                    // 🔄 Refresh once
                    driver.navigate().refresh();

                    wait10.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//tr[contains(@class,'ng-star-inserted')]")
                    ));

                    try {

                        // ✅ Click first row
                        WebElement firstRow = wait10.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("(//tr[contains(@class,'ng-star-inserted')])[1]")
                                )
                        );

                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", firstRow);
                        Thread.sleep(500);

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", firstRow);

                        System.out.println("✅ First row clicked");

                        // ✅ Click Actions button
                        // ✅ Click Actions button (FIXED VERSION)
                        WebElement actionsBtn = wait10.until(
                                ExpectedConditions.presenceOfElementLocated(
                                        By.xpath("//button[contains(@class,'actions-button')]")
                                )
                        );

                        // 🔽 Scroll with offset (VERY IMPORTANT)
                        ((JavascriptExecutor) driver).executeScript(
                                "window.scrollBy(0, -150);" // move slightly up to avoid header overlap
                        );

                        // OR better: center element in screen
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollIntoView({block: 'center'});", actionsBtn
                        );

                        Thread.sleep(500);

                        // 💥 Force click using JS (bypass overlay issue)
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].click();", actionsBtn
                        );

                        System.out.println("⚙️ Actions button clicked (JS)");

                        try {

                            // ✅ Wait for visible overlay menu
                            WebElement menu = wait10.until(ExpectedConditions.visibilityOfElementLocated(
                                    By.xpath("//div[contains(@class,'p-menu-overlay') and contains(@class,'ng-star-inserted')]")
                            ));

                            // ✅ Target the clickable <a> inside menu
                            WebElement downloadBtn = menu.findElement(
                                    By.xpath(".//span[normalize-space()='Download']/ancestor::a")
                            );

                            // scroll (safety)
                            ((JavascriptExecutor) driver).executeScript(
                                    "arguments[0].scrollIntoView({block:'center'});", downloadBtn
                            );

                            Thread.sleep(300);

                            // 💥 FORCE CLICK (PrimeNG fix)
                            ((JavascriptExecutor) driver).executeScript(
                                    "arguments[0].click();", downloadBtn
                            );

                            System.out.println("📥 Download clicked SUCCESS");

                        } catch (Exception e) {
                            e.printStackTrace();
                            driver.quit();
                            return "{\"status\":\"error\",\"message\":\"Download click failed\"}";
                        }
                    } finally {
                        System.out.println("📥 Download triggered");
                    }

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile10 = null;
                    int waitTime10 = 0;

                    while (waitTime10 < 25) {

                        File[] files = new File(downloadDir10).listFiles();

                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().contains("users") && f.getName().endsWith(".xlsx")) {
                                    latestFile10 = f;
                                    System.out.println("✅ Found file: " + f.getName());
                                    break;
                                }
                            }
                        }
                        if (latestFile10 != null) break;

                        Thread.sleep(1000);
                        waitTime10++;
                    }

                    if (latestFile10 == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }

                    // 📊 CONVERT EXCEL → JSON
                    FileInputStream fis10 = new FileInputStream(latestFile10);
                    XSSFWorkbook workbook10 = new XSSFWorkbook(fis10);
                    XSSFSheet sheet10 = workbook10.getSheetAt(0);

                    JSONArray jsonArray10 = new JSONArray();

                    for (int i = 1; i <= sheet10.getLastRowNum(); i++) {

                        if (sheet10.getRow(i) == null) continue;

                        JSONObject obj = new JSONObject();
                        obj.put("int_id", getCellValue(sheet10, i, 1));
                        String firstName = String.valueOf(getCellValue(sheet10, i, 2)).trim();
                        String lastName = String.valueOf(getCellValue(sheet10, i, 3)).trim();
                        obj.put("name", (firstName + " " + lastName).replaceAll("null", "").trim());
                        obj.put("manager", "");
                        obj.put("cnic", getCellValue(sheet10, i, 19));
                        obj.put("adrs", getCellValue(sheet10, i, 16));
                        obj.put("status", getCellValue(sheet10, i, 10));
                        obj.put("mob", getCellValue(sheet10, i, 5));
                        obj.put("reg", getCellValue(sheet10, i, 18));
                        obj.put("package", getCellValue(sheet10, i, 12));
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", getCellValue(sheet10, i, 7));

                        jsonArray10.put(obj);
                    }

                    workbook10.close();
                    fis10.close();

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    // 🗑️ DELETE FILE
                    if (latestFile10.exists()) {
                        latestFile10.delete();
                        System.out.println("🗑️ Galaxy file deleted");
                        System.out.println("The End");
                    }

                    return jsonArray10.toString();

                case "alfa":

                    WebDriverWait wait11 = new WebDriverWait(driver, Duration.ofSeconds(20));
                    String downloadDir11 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old file if exists
                    File folder11 = new File(downloadDir11);
                    File[] files11 = folder11.listFiles();

                    if (files11 != null) {
                        for (File f : files11) {
                            if (f.getName().contains("Customer List")
                                    && (f.getName().contains("-south") || f.getName().contains("-north"))
                                    && f.getName().endsWith(".csv")) {

                                f.delete();
                                System.out.println("🧹 Deleted old file: " + f.getName());
                            }
                        }
                    }

                    System.out.println("🚀 Opening Alfa Broadband...");

                    maxAttempts = 3;
                    loginSuccess = false;

                    String currentUrl11 = null;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {

                        System.out.println("🔁 Login Attempt: " + attempt);

                        driver.get(url);

                        // 🔐 Enter username & password
                        wait11.until(ExpectedConditions.visibilityOfElementLocated(
                                By.id("loginform-username")
                        )).sendKeys(username);

                        driver.findElement(By.id("loginform-password")).sendKeys(password);

                        // 🧠 Solve captcha using OCR
                        String ocrText = solveCaptcha(driver);

                        if (ocrText == null || ocrText.isEmpty()) {
                            System.out.println("⚠️ OCR failed, retrying...");
                            continue;
                        }

                        int result;

                        try {
                            result = extractAndSolve(ocrText);
                            System.out.println("🧮 Captcha solved: " + result);
                        } catch (Exception e) {
                            System.out.println("⚠️ Captcha parse failed, retrying...");
                            continue;
                        }

                        // ✍️ Enter captcha
                        driver.findElement(By.id("loginform-captcha")).clear();
                        driver.findElement(By.id("loginform-captcha")).sendKeys(String.valueOf(result));

                        // 🔘 Click login
                        driver.findElement(By.xpath("//button[@type='submit']")).click();

                        // ⏳ Wait after login
                        Thread.sleep(7000);

                        // 🌐 Get current URL
                        currentUrl11 = driver.getCurrentUrl();
                        System.out.println("🌐 Current URL: " + currentUrl11);

                        // ❗ Check captcha error message
                        boolean captchaError = false;

                        try {
                            WebElement captchaErrorElement = driver.findElement(
                                    By.xpath("//*[contains(text(),'The verification code is incorrect.')]")
                            );
                            if (captchaErrorElement.isDisplayed()) {
                                captchaError = true;
                            }
                        } catch (NoSuchElementException ignored) {
                        }

                        // ❌ CASE 1: CAPTCHA WRONG
                        if (!captchaError) {
                            loginSuccess = true;
                            break;
                        }

                        System.out.println("❌ Login failed, retrying...");
                    }

                    if (!loginSuccess) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login failed after retries\"}";
                    }

                    if (!currentUrl11.equals("https://partner.alfabroadband.com/")) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    // ✅ LOGIN SUCCESS
                    System.out.println("✅ Alfa Broadband login successful!");

                    // ❌ Close popup if appears
                    try {
                        WebElement closePopup = wait11.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[@class='close']")
                        ));
                        closePopup.click();
                        System.out.println("✅ Popup closed");
                    } catch (TimeoutException e) {
                        System.out.println("⚠️ No popup appeared");
                    }
                    System.out.println("📄 Navigating to customers page...");
                    driver.get(url + "/customer/customers");

                    wait11.until(d -> d.getCurrentUrl().contains("customer/customers"));
                    System.out.println("✅ Customers page loaded");

                    wait11.until(webDriver ->
                            ((JavascriptExecutor) webDriver)
                                    .executeScript("return document.readyState")
                                    .equals("complete")
                    );

                    System.out.println("🔍 Clicking Search button...");

                    try {
                        wait11.until(ExpectedConditions.elementToBeClickable(By.id("btnSubmit"))).click();
                    } catch (StaleElementReferenceException e) {
                        System.out.println("♻️ Retrying search click");
                        wait11.until(ExpectedConditions.elementToBeClickable(By.id("btnSubmit"))).click();
                    }

                    System.out.println("✅ Search clicked");

                    System.out.println("🔍 Search clicked");

                    Thread.sleep(4000);

                    wait11.until(ExpectedConditions.elementToBeClickable(By.id("btnExport")));

                    // 📥 Click Export
                    System.out.println("📥 Clicking Export button...");

                    try {
                        wait11.until(ExpectedConditions.elementToBeClickable(By.id("btnExport"))).click();
                    } catch (StaleElementReferenceException e) {
                        System.out.println("♻️ Retrying export click");
                        wait11.until(ExpectedConditions.elementToBeClickable(By.id("btnExport"))).click();
                    }

                    System.out.println("📥 Export button clicked");

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile11 = null;
                    int waitTime11 = 0;

                    while (waitTime11 < 40) {

                        File folder = new File(downloadDir11);
                        File[] files = folder.listFiles();

                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().contains("Customer List") && f.getName().endsWith(".csv")) {

                                    // ✅ Check file is not empty
                                    if (f.length() == 0) {
                                        continue;
                                    }

                                    // ✅ Wait until file size stops changing (download complete)
                                    long size1 = f.length();
                                    Thread.sleep(1000);
                                    long size2 = f.length();

                                    if (size1 == size2) {
                                        latestFile11 = f;
                                        System.out.println("✅ File fully downloaded: " + f.getName());
                                        break;
                                    }
                                }
                            }
                        }

                        if (latestFile11 != null) break;

                        Thread.sleep(1000);
                        waitTime11++;
                    }

                    if (latestFile11 == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }
                    // 📊 CONVERT EXCEL → JSON
                    reader = new CSVReader(new FileReader(latestFile11));
                    String[] data11;

                    JSONArray jsonArray11 = new JSONArray();
                    isHeader = true;

                    while ((data11 = reader.readNext()) != null) {

                        if (isHeader) {
                            isHeader = false;
                            continue;
                        }

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", data11.length > 3 ? data11[3] : "");
                        obj.put("name", data11.length > 5 ? data11[5] : "");
                        obj.put("manager", "");
                        obj.put("cnic", data11.length > 6 ? data11[6] : "");
                        obj.put("adrs", data11.length > 7 ? data11[7] : "");
                        obj.put("status", data11.length > 8 ? data11[8] : "");
                        obj.put("mob", data11.length > 12 ? data11[12] : "");
                        obj.put("reg", data11.length > 15 ? data11[15] : "");
                        obj.put("package", data11.length > 17 ? data11[17] : "");
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", data11.length > 23 ? data11[23] : "");

                        jsonArray11.put(obj);
                    }

                    reader.close();

                    // 🗑️ DELETE FILE
                    if (latestFile11.exists()) {
                        latestFile11.delete();
                        System.out.println("🗑️ Alfa Broadband customer list file deleted");
                    }

                    System.out.println("✅ Done");

                    return jsonArray11.toString();

                case "optix":

                    WebDriverWait waitOptix = new WebDriverWait(driver, Duration.ofSeconds(20));

                    System.out.println("🚀 Opening Optix...");
                    driver.get(url);

                    try {
                        // 🔐 LOGIN
                        waitOptix.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(username);
                        driver.findElement(By.id("password")).sendKeys(password);

                        WebElement loginBtnOptix = waitOptix.until(
                                ExpectedConditions.elementToBeClickable(By.id("send"))
                        );
                        loginBtnOptix.click();

                        Thread.sleep(2000);

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login page elements not found\"}";
                    }

                    // ✅ CHECK LOGIN SUCCESS
                    boolean loginSuccessOptix = false;

                    try {
                        waitOptix.until(ExpectedConditions.urlContains("/home.html"));
                        loginSuccessOptix = true;
                    } catch (TimeoutException e) {
                        loginSuccessOptix = false;
                    }

                    if (!loginSuccessOptix) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials or login failed\"}";
                    }

                    System.out.println("✅ Optix login successful, fetching API...");

                    // 🔥 HIT API
                    String apiUrlOptix = url + "/Ajax_Request/get_users_json";

                    String rawJsonOptix;

                    try {
                        js = (JavascriptExecutor) driver;

                        rawJsonOptix = (String) js.executeScript(
                                "return fetch(arguments[0], {credentials: 'include'})" +
                                        ".then(res => res.text())" +
                                        ".then(data => data);",
                                apiUrlOptix
                        );

                        System.out.println("📦 Raw JSON received");

                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Failed to fetch API data\"}";
                    }

                    // ❌ EMPTY OR INVALID RESPONSE
                    if (rawJsonOptix == null || rawJsonOptix.trim().isEmpty()) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Empty API response\"}";
                    }

                    JSONObject responseOptix;
                    JSONArray dataOptix;

                    try {
                        responseOptix = new JSONObject(rawJsonOptix);
                        dataOptix = responseOptix.getJSONArray("data");
                    } catch (Exception e) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Invalid JSON format from API\"}";
                    }

                    JSONArray finalArrayOptix = new JSONArray();

                    // 🔄 LOOP DATA
                    for (int i = 0; i < dataOptix.length(); i++) {

                        JSONObject row = dataOptix.getJSONObject(i);
                        JSONObject obj = new JSONObject();

                        try {

                            // 🧹 CLEAN HTML FIELDS
                            String rawName = row.optString("user_full_name", "");

                            String name = row.optString("name", "");
                            String address = row.optString("address", "").replaceAll("<.*?>", "").trim();
                            String pkg = row.optString("package_name", "");
                            String expiry = row.optString("user_expiry_view", "").replaceAll("<.*?>", "").trim();
                            String seller = row.optString("seller_name", "");

                            String usernameVal = row.optString("username", "");
                            String mobile = row.optString("mobile", "");
                            String nic = row.optString("nic", "");
                            String regDate = row.optString("reg_date", "");
                            String statusRaw = row.optString("status", "");

                            // 🎯 STATUS MAPPING (same as fiberish)
                            String status = switch (statusRaw) {
                                case "1" -> "0";
                                case "2" -> "1";
                                default -> statusRaw;
                            };

                            // 🎯 FINAL FORMAT
                            obj.put("int_id", usernameVal);
                            obj.put("name", name);
                            obj.put("manager", seller);
                            obj.put("cnic", nic);
                            obj.put("adrs", address);
                            obj.put("status", status);
                            obj.put("mob", mobile);
                            obj.put("reg", regDate);
                            obj.put("package", pkg);
                            obj.put("rech_dt", "");
                            obj.put("exp_dt", expiry);

                            finalArrayOptix.put(obj);

                        } catch (Exception e) {
                            System.out.println("⚠️ Error parsing row index: " + i);
                        }
                    }

                    driver.quit();

                    if (finalArrayOptix.length() == 0) {
                        return "{\"status\":\"error\",\"message\":\"No data found in API\"}";
                    }

                    System.out.println("✅ Optix data extraction complete");

                    return finalArrayOptix.toString();

                case "partner_cxtreme":

                    WebDriverWait wait13 = new WebDriverWait(driver, Duration.ofSeconds(20));
                    String downloadDir13 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old file if exists
                    File folder13 = new File(downloadDir13);
                    File[] files13 = folder13.listFiles();

                    if (files13 != null) {
                        for (File f : files13) {
                            if (f.getName().contains("Customer List")
                                    && f.getName().endsWith(".csv")) {

                                f.delete();
                                System.out.println("🧹 Deleted old file: " + f.getName());
                            }
                        }
                    }

                    System.out.println("🚀 Opening C Xtreme portal...");

                    maxAttempts = 3;
                    loginSuccess = false;

                    String currentUrl13 = null;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {

                        System.out.println("🔁 Login Attempt: " + attempt);

                        driver.get(url);

                        // 🔐 Enter username & password
                        wait13.until(ExpectedConditions.visibilityOfElementLocated(
                                By.id("loginform-username")
                        )).sendKeys(username);

                        driver.findElement(By.id("loginform-password")).sendKeys(password);

                        // 🧠 Solve captcha using OCR
                        String ocrText = solveCaptcha(driver);

                        if (ocrText == null || ocrText.isEmpty()) {
                            System.out.println("⚠️ OCR failed, retrying...");
                            continue;
                        }

                        int result;

                        try {
                            result = extractAndSolve(ocrText);
                            System.out.println("🧮 Captcha solved: " + result);
                        } catch (Exception e) {
                            System.out.println("⚠️ Captcha parse failed, retrying...");
                            continue;
                        }

                        // ✍️ Enter captcha
                        driver.findElement(By.id("loginform-captcha")).clear();
                        driver.findElement(By.id("loginform-captcha")).sendKeys(String.valueOf(result));

                        // 🔘 Click login
                        driver.findElement(By.xpath("//button[@type='submit']")).click();

                        // ⏳ Wait after login
                        Thread.sleep(7000);

                        // 🌐 Get current URL
                        currentUrl13 = driver.getCurrentUrl();
                        System.out.println("🌐 Current URL: " + currentUrl13);

                        // ❗ Check captcha error message
                        boolean captchaError = false;

                        try {
                            WebElement captchaErrorElement = driver.findElement(
                                    By.xpath("//*[contains(text(),'The verification code is incorrect.')]")
                            );
                            if (captchaErrorElement.isDisplayed()) {
                                captchaError = true;
                            }
                        } catch (NoSuchElementException ignored) {
                        }

                        // ❌ CASE 1: CAPTCHA WRONG
                        if (!captchaError) {
                            loginSuccess = true;
                            break;
                        }

                        System.out.println("❌ Login failed, retrying...");
                    }

                    if (!loginSuccess) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login failed after retries\"}";
                    }

                    if (!currentUrl13.equals("https://partner.cxtreme.pk/")) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    // ✅ LOGIN SUCCESS
                    System.out.println("✅ CXtreme login successful!");

                    // ❌ Close popup if appears
                    try {
                        WebElement closePopup = wait13.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[@class='close']")
                        ));
                        closePopup.click();
                        System.out.println("✅ Popup closed");
                    } catch (TimeoutException e) {
                        System.out.println("⚠️ No popup appeared");
                    }
                    System.out.println("📄 Navigating to customers page...");
                    driver.get(url + "/customer/customers");

                    wait13.until(d -> d.getCurrentUrl().contains("customer/customers"));
                    System.out.println("✅ Customers page loaded");

                    wait13.until(webDriver ->
                            ((JavascriptExecutor) webDriver)
                                    .executeScript("return document.readyState")
                                    .equals("complete")
                    );

                    System.out.println("🔍 Clicking Search button...");

                    try {
                        wait13.until(ExpectedConditions.elementToBeClickable(By.id("btnSubmit"))).click();
                    } catch (StaleElementReferenceException e) {
                        System.out.println("♻️ Retrying search click");
                        wait13.until(ExpectedConditions.elementToBeClickable(By.id("btnSubmit"))).click();
                    }

                    System.out.println("✅ Search clicked");

                    System.out.println("🔍 Search clicked");

                    Thread.sleep(4000);

                    wait13.until(ExpectedConditions.elementToBeClickable(By.id("btnExport")));

                    // 📥 Click Export
                    System.out.println("📥 Clicking Export button...");

                    try {
                        wait13.until(ExpectedConditions.elementToBeClickable(By.id("btnExport"))).click();
                    } catch (StaleElementReferenceException e) {
                        System.out.println("♻️ Retrying export click");
                        wait13.until(ExpectedConditions.elementToBeClickable(By.id("btnExport"))).click();
                    }

                    System.out.println("📥 Export button clicked");

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile13 = null;
                    int waitTime13 = 0;

                    while (waitTime13 < 40) {

                        File folder = new File(downloadDir13);
                        File[] files = folder.listFiles();

                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().contains("Customer List") && f.getName().endsWith(".csv")) {

                                    // ✅ Check file is not empty
                                    if (f.length() == 0) {
                                        continue;
                                    }

                                    // ✅ Wait until file size stops changing (download complete)
                                    long size1 = f.length();
                                    Thread.sleep(1000);
                                    long size2 = f.length();

                                    if (size1 == size2) {
                                        latestFile13 = f;
                                        System.out.println("✅ File fully downloaded: " + f.getName());
                                        break;
                                    }
                                }
                            }
                        }

                        if (latestFile13 != null) break;

                        Thread.sleep(1000);
                        waitTime13++;
                    }

                    if (latestFile13 == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }
                    // 📊 CONVERT EXCEL → JSON
                    reader = new CSVReader(new FileReader(latestFile13));
                    String[] data13;

                    JSONArray jsonArray13 = new JSONArray();
                    isHeader = true;

                    while ((data13 = reader.readNext()) != null) {

                        if (isHeader) {
                            isHeader = false;
                            continue;
                        }

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", data13.length > 3 ? data13[3] : "");
                        obj.put("name", data13.length > 5 ? data13[5] : "");
                        obj.put("manager", "");
                        obj.put("cnic", data13.length > 6 ? data13[6] : "");
                        obj.put("adrs", data13.length > 7 ? data13[7] : "");
                        obj.put("status", data13.length > 8 ? data13[8] : "");
                        obj.put("mob", data13.length > 12 ? data13[12] : "");
                        obj.put("reg", data13.length > 15 ? data13[15] : "");
                        obj.put("package", data13.length > 17 ? data13[17] : "");
                        obj.put("rech_dt", "");
                        obj.put("exp_dt", data13.length > 23 ? data13[23] : "");

                        jsonArray13.put(obj);
                    }

                    reader.close();

                    // 🗑️ DELETE FILE
                    if (latestFile13.exists()) {
                        latestFile13.delete();
                        System.out.println("🗑️ CXtreme Broadband customer list file deleted");
                    }

                    System.out.println("✅ Done");

                    return jsonArray13.toString();

             case "wellnet":

                    WebDriverWait wait14 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    System.out.println("🚀 Opening Wellnet...");

                    driver.get(url);

                    // 🔐 LOGIN
                    wait14.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//input[@name='username']")
                    )).sendKeys(username);

                    driver.findElement(By.xpath("//input[@name='password']")).sendKeys(password);

                    wait14.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit']//span[contains(text(),'Login')]")
                    )).click();

                    // ⏳ Wait after login click
                    Thread.sleep(2000);

                    // ⏳ Wait for redirect after login
                    loginSuccess = false;

                    try {
                        wait14.until(ExpectedConditions.urlContains("/dashboard"));
                        loginSuccess = true;
                    } catch (TimeoutException e) {
                        loginSuccess = false;
                    }

                    // ❌ If login failed → stop execution
                    if (!loginSuccess) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        System.out.println("The End");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Login successful, continuing...");

                    wait14 = new WebDriverWait(driver, Duration.ofSeconds(10));


                    // 📄 Navigate to report page
                    driver.get(url + "#/users/index");

                    wait14 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait14.until(ExpectedConditions.urlContains("users"));
                    System.out.println("📄 Navigated to users page");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait14 = new WebDriverWait(driver, Duration.ofSeconds(10));

                    columnMenuBtn = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(@class,'ng-tns-c60') and .//i[contains(@class,'fa-list')]]")
                    ));

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", columnMenuBtn);
                    System.out.println("📊 Column menu opened");

                    Thread.sleep(1000);

                    wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("div.p-overlaypanel-content")
                    ));

                    clickCheckboxIfNeeded(driver, wait, "phone");        // Mobile
                    clickCheckboxIfNeeded(driver, wait, "address");      // Address
                    clickCheckboxIfNeeded(driver, wait, "national_id");  // National ID


                    try {
                        WebElement rows500 = wait14.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//rows-count-selector//a[normalize-space()='500']")
                        ));

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", rows500);

                        System.out.println("✅ Selected 500 rows");

                        Thread.sleep(10000); // wait for table reload
                    } catch (Exception e) {
                        System.out.println("⚠️ Could not select 500 rows");
                    }

                    jsonArray = new JSONArray();
                    List<String> allRowsHtml = new ArrayList<>();
                    int totalUsers = 0;

                    boolean hasNextPage = true;

                    while (hasNextPage) {

                        wait14.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                                By.xpath("//table//tbody/tr")
                        ));

                        Thread.sleep(1000);

                        // 🔥 Extract FULL TABLE HTML for this page
                        String pageHtml = (String) ((JavascriptExecutor) driver)
                                .executeScript("return document.querySelector('tbody').innerHTML;");

                        String[] rowsSplit = pageHtml.split("<tr");

                        for (String row : rowsSplit) {
                            if (row.contains("<td")) {
                                allRowsHtml.add(row);
                            }
                        }

                        totalUsers += rowsSplit.length;

                        System.out.println("📄 Collected page. Total rows so far: " + allRowsHtml.size());

                        // 🔁 NEXT PAGE LOGIC (UNCHANGED)
                        try {
                            WebElement nextBtn = driver.findElement(
                                    By.xpath("//a[@aria-label='Next']/parent::li")
                            );

                            String classes = nextBtn.getAttribute("class");

                            if (classes.contains("disabled")) {
                                System.out.println("🚫 No more pages");
                                hasNextPage = false;
                            } else {
                                WebElement nextLink = nextBtn.findElement(By.tagName("a"));

                                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextLink);

                                System.out.println("➡️ Moving to next page");

                                Thread.sleep(3000);
                            }

                        } catch (Exception e) {
                            System.out.println("⚠️ Next button not found, stopping pagination");
                            hasNextPage = false;
                        }
                    }

                    int totalRows = allRowsHtml.size();
                    int chunkSize = 500;
                    int numThreads = (int) Math.ceil((double) totalRows / chunkSize);

                    System.out.println("📊 Total rows collected: " + totalRows + " | Threads: " + numThreads);

                    List<JSONObject> resultList = Collections.synchronizedList(new ArrayList<>());

                    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
                    List<Future<?>> futures = new ArrayList<>();

                    for (int t = 0; t < numThreads; t++) {

                        int start = t * chunkSize;
                        int end = Math.min(start + chunkSize, totalRows);

                        List<String> chunk = allRowsHtml.subList(start, end);

                        futures.add(executor.submit(() -> {

                            for (String row : chunk) {
                                try {

                                    String[] cols = row.split("<td[^>]*>");
                                    if (cols.length < 28) continue;

                                    String username14 = stripTags(cols[5]);
                                    String firstName = stripTags(cols[6]);
                                    String lastName = stripTags(cols[7]);
                                    String expiry = stripTags(cols[8]);
                                    String parent = stripTags(cols[9]);
                                    String profile = stripTags(cols[10]);
                                    String phone = stripTags(cols[23]);
                                    String address = stripTags(cols[24]);
                                    String cnic = stripTags(cols[28]);

                                    // 🧠 STATUS LOGIC
                                    String status14 = "0";
                                    try {
                                        LocalDateTime now = LocalDateTime.now();
                                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                        LocalDateTime expiryDateTime = LocalDateTime.parse(expiry, formatter);

                                        if (!expiryDateTime.isBefore(now)) {
                                            status14 = "1";
                                        }
                                    } catch (Exception ignore) {
                                    }

                                    JSONObject obj = new JSONObject();

                                    obj.put("int_id", username14);
                                    obj.put("name", (firstName + " " + lastName).trim());
                                    obj.put("manager", parent);
                                    obj.put("cnic", cnic);
                                    obj.put("adrs", address);
                                    obj.put("status", status14);
                                    obj.put("mob", phone);
                                    obj.put("reg", "");
                                    obj.put("package", profile);
                                    obj.put("rech_dt", "");
                                    obj.put("exp_dt", expiry);

                                    resultList.add(obj);

                                } catch (Exception ex) {
                                    System.out.println("⚠️ Row parse error: " + ex.getMessage());
                                }
                            }
                        }));
                    }

                    // wait all threads
                    for (Future<?> f : futures) {
                        f.get();
                    }
                    executor.shutdown();

                    // build final JSON
                    for (JSONObject obj : resultList) {
                        jsonArray.put(obj);
                    }

                    System.out.println("✅ Extracted " + jsonArray.length() + " users (parallel)");
                    return jsonArray.toString();

                case "billing_galaxy":

                    WebDriverWait wait15 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    String downloadDir15 = System.getProperty("user.home") + "\\Downloads";

                    String[] targetFiles15 = {"Galaxy Broadband.xlsx"};

                    // 🧹 Delete old file
                    for (String fileName : targetFiles15) {
                        File f = new File(downloadDir15, fileName);
                        if (f.exists()) {
                            f.delete();
                            System.out.println("🧹 Deleted old file: " + fileName);
                        }
                    }

                    System.out.println("🚀 Opening Galaxy Billing...");

                    try {
                        driver.get(url);
                    } catch (Exception e) {
                        return "{\"status\":\"error\",\"message\":\"Failed to open URL\"}";
                    }

                    // 🔐 LOGIN
                    try {
                        wait15.until(ExpectedConditions.visibilityOfElementLocated(By.id("signInFormUEP")))
                                .sendKeys(username);

                        driver.findElement(By.id("signInFormPass")).sendKeys(password);

                        wait15.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[@type='submit' and contains(.,'SUBMIT')]")
                        )).click();

                    } catch (Exception e) {
                        return "{\"status\":\"error\",\"message\":\"Login elements not found\"}";
                    }

                    // ⏳ Wait after login
                    Thread.sleep(3000);

                    // ✅ CHECK LOGIN SUCCESS
                    boolean loginSuccess15 = false;

                    try {
                        wait15.until(ExpectedConditions.urlContains("/dealer/index.php"));
                        loginSuccess15 = true;
                    } catch (TimeoutException e) {
                        loginSuccess15 = false;
                    }

                    if (!loginSuccess15) {
                        driver.quit();
                        System.out.println("❌ Wrong Login Credentials");
                        return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                    }

                    System.out.println("✅ Galaxy login successful!");

                    // 📄 Navigate to customers page
                    try {
                        driver.get(url + "/dealer/list-customers.php?filter=all");
                    } catch (Exception e) {
                        return "{\"status\":\"error\",\"message\":\"Failed to open customers page\"}";
                    }

                    // ⏳ Wait for table
                    try {
                        wait15.until(ExpectedConditions.presenceOfElementLocated(By.id("customers_table")));
                    } catch (Exception e) {
                        return "{\"status\":\"error\",\"message\":\"Customers table not loaded\"}";
                    }

                    // ⏳ wait for table to load
                    wait15.until(ExpectedConditions.presenceOfElementLocated(By.id("customers_table")));

                    // 🚀 STEP 1: Inject new option (1000)
                    ((JavascriptExecutor) driver).executeScript(
                            "let select = document.querySelector('select[name=\"customers_table_length\"]');" +
                                    "let exists = [...select.options].some(o => o.value == '1000');" +
                                    "if(!exists) {" +
                                    "   let opt = document.createElement('option');" +
                                    "   opt.value = '1000';" +
                                    "   opt.text = '1000';" +
                                    "   select.appendChild(opt);" +
                                    "}"
                    );

                    System.out.println("🧠 Injected 1000 option");

                    // 🚀 STEP 2: Set value to 1000 and trigger change
                    ((JavascriptExecutor) driver).executeScript(
                            "let select = document.querySelector('select[name=\"customers_table_length\"]');" +
                                    "select.value = '1000';" +
                                    "select.dispatchEvent(new Event('change'));"
                    );

                    System.out.println("📊 Set entries to 1000");

                    // ⏳ STEP 3: wait for table reload
                    wait15.until(d ->
                            d.findElements(By.cssSelector("#customers_table tbody tr")).size() > 50
                    );

                    // 📥 CLICK EXPORT BUTTON
                    try {
                        exportBtn = wait15.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//button[contains(@class,'buttons-collection')]")
                                )
                        );

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", exportBtn);
                        System.out.println("📤 Export dropdown opened");

                    } catch (Exception e) {
                        return "{\"status\":\"error\",\"message\":\"Export button not found or not clickable\"}";
                    }

                    // 📥 CLICK EXCEL OPTION
                    try {
                        WebElement excelOption = wait15.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//li[contains(@class,'buttons-excel')]//a[contains(text(),'Excel')]")
                                )
                        );

                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", excelOption);
                        System.out.println("📥 Excel option clicked");

                    } catch (Exception e) {
                        return "{\"status\":\"error\",\"message\":\"Excel option not found in dropdown\"}";
                    }

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile15 = null;
                    int waitTime15 = 0;

                    while (waitTime15 < 40) {

                        for (String fileName : targetFiles15) {
                            File f = new File(downloadDir15, fileName);

                            if (f.exists()) {

                                // ignore partial downloads
                                if (f.getName().endsWith(".crdownload") || f.getName().endsWith(".part")) {
                                    continue;
                                }

                                long size1 = f.length();
                                Thread.sleep(1000);
                                long size2 = f.length();

                                if (size1 == size2 && size1 > 0) {
                                    latestFile15 = f;
                                    System.out.println("✅ File fully downloaded: " + f.getName());
                                    break;
                                }
                            }
                        }

                        if (latestFile15 != null) break;

                        Thread.sleep(1000);
                        waitTime15++;
                    }

                    if (latestFile15 == null) {
                        return "{\"status\":\"error\",\"message\":\"Galaxy Excel file not downloaded\"}";
                    }

                    if (latestFile15.length() < 50) {
                        return "{\"status\":\"error\",\"message\":\"Downloaded file is empty\"}";
                    }

                    // 📊 CONVERT EXCEL → JSON
                    JSONArray jsonArray15 = new JSONArray();

                    try {
                        FileInputStream fis15 = new FileInputStream(latestFile15);
                        XSSFWorkbook workbook15 = new XSSFWorkbook(fis15);
                        XSSFSheet sheet15 = workbook15.getSheetAt(0);

                        for (int i = 2; i <= sheet15.getLastRowNum(); i++) {

                            if (sheet15.getRow(i) == null) continue;

                            JSONObject obj = new JSONObject();

                            String statusRaw = String.valueOf(getCellValue(sheet15, i, 7))
                                    .trim()
                                    .toUpperCase();

                            String status = switch (statusRaw) {
                                case "A" -> "1";
                                case "E" -> "0";
                                default -> "";
                            };

                            Row row = sheet15.getRow(i);
                            Cell cell = row.getCell(9);

                            DataFormatter formatter = new DataFormatter();
                            String phone = (cell != null) ? formatter.formatCellValue(cell).trim() : "";

                            obj.put("int_id", getCellValue(sheet15, i, 2));
                            obj.put("name", getCellValue(sheet15, i, 3));
                            obj.put("manager", getCellValue(sheet15, i, 21));
                            obj.put("cnic", "");
                            obj.put("adrs", getCellValue(sheet15, i, 4));
                            obj.put("status", status);
                            obj.put("mob", phone);
                            obj.put("reg", "");
                            obj.put("package", getCellValue(sheet15, i, 6));
                            obj.put("rech_dt", "");
                            obj.put("exp_dt", getCellValue(sheet15, i, 5));

                            jsonArray15.put(obj);
                        }

                        workbook15.close();
                        fis15.close();

                    } catch (Exception e) {
                        return "{\"status\":\"error\",\"message\":\"Failed to parse Excel file\"}";
                    }

                    // 🗑️ DELETE FILE
                    if (latestFile15.exists()) {
                        latestFile15.delete();
                        System.out.println("🗑️ Galaxy Billing file deleted");
                    }

                    System.out.println("✅ Galaxy Billing Done");

                    return jsonArray15.toString();


                case "connect1":

                    try {
                        WebDriverWait wait66 = new WebDriverWait(driver, Duration.ofSeconds(20));

                        // ✅ Dynamic dates
                        LocalDate now66 = LocalDate.now();
                        String fromDate66 = now66.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        String toDate66 = now66.withDayOfMonth(now66.lengthOfMonth()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                        System.out.println("🚀 Opening Connect...");
                        driver.get(url + "login/");

                        // 🔐 LOGIN
                        waitForElement(driver, wait66,
                                By.cssSelector("input[type='text'].form-control"),
                                "Username Input"
                        ).sendKeys(username);

                        waitForElement(driver, wait66,
                                By.cssSelector("input[type='password'].form-control"),
                                "Password Input"
                        ).sendKeys(password);

                        waitForClickable(driver, wait66,
                                By.xpath("//button[@type='submit' and contains(@class,'btn-submit')]"),
                                "Login Button"
                        ).click();

                        // ⏳ Wait after login click
                        Thread.sleep(2000);

                        // ⏳ Wait for redirect after login
                        loginSuccess = false;

                        try {
                            wait66.until(ExpectedConditions.urlContains("/dashboard"));
                            loginSuccess = true;
                        } catch (
                                TimeoutException e) {
                            loginSuccess = false;
                        }

                        // ❌ If login failed → stop execution
                        if (!loginSuccess) {
                            driver.quit();
                            System.out.println("❌ Wrong Login Credentials");
                            System.out.println("The End");
                            return "{\"status\":\"error\",\"message\":\"Wrong login credentials\"}";
                        }

                        System.out.println("✅ Login successful, continuing...");

                        Thread.sleep(5000);

                        try {
                            WebElement closeBtn = wait66.until(ExpectedConditions.elementToBeClickable(
                                    By.cssSelector("button.btn-close.ajax-source")
                            ));
                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtn);
                            System.out.println("✅ Notice modal closed");
                            Thread.sleep(1000);
                        } catch (TimeoutException e) {
                            System.out.println("⚠️ No notice modal appeared, continuing...");
                        }

                        // 📄 Navigate to recharge logs
                        driver.get(url + "customers/report/recharge-logs");

                        WebElement dateRangeInput66 = waitForClickable(driver, wait66,
                                By.name("daterange"),
                                "Date Range Input"
                        );

                        // Click first (important)
                        dateRangeInput66.click();
                        Thread.sleep(500);

                        // Clear and type
                        dateRangeInput66.sendKeys(Keys.CONTROL + "a");
                        dateRangeInput66.sendKeys(Keys.DELETE);

                        dateRangeInput66.sendKeys(fromDate66 + " - " + toDate66);
                        dateRangeInput66.sendKeys(Keys.ENTER);

                        System.out.println("✅ Date range entered manually");

                        Thread.sleep(1000);

                        // 🔍 Click Search
                        WebElement searchBtn66 = waitForClickable(driver, wait66,
                                By.xpath("//input[@type='submit' and @value='Search']"),
                                "Search Button"
                        );
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", searchBtn66);
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchBtn66);

                        System.out.println("🔍 Search clicked. Waiting for table...");
                        Thread.sleep(3000);

                        // 📊 Extract table rows
                        wait66.until(ExpectedConditions.visibilityOfElementLocated(
                                By.xpath("//div[@class='table-responsive']//tbody/tr")
                        ));

                        java.util.List<WebElement> rows66 = driver.findElements(
                                By.xpath("//div[@class='table-responsive']//tbody/tr")
                        );

                        JSONArray jsonArray66 = new JSONArray();

                        for (WebElement row : rows66) {
                            java.util.List<WebElement> cols = row.findElements(By.tagName("td"));
                            if (cols.size() < 6) {
                                throw new RuntimeException("UI CHANGE DETECTED: Table structure changed. Expected >=6 columns but found " + cols.size());
                            }
                            // Table columns (0-indexed):
                            // 0: S.No | 1: User Name | 2: Franchise | 3: By | 4: Date/Time | 5: Package

                            String userName = cols.get(1).getText().trim();
                            String by = cols.get(3).getText().trim();
                            String pkg = cols.get(5).getText().trim();
                            String dateTime = cols.get(4).getText().trim();


                            JSONObject obj = new JSONObject();
                            obj.put("int_id", userName);
                            obj.put("name", by);
                            obj.put("manager", "");
                            obj.put("cnic", "");
                            obj.put("adrs", "");
                            obj.put("status", "");
                            obj.put("mob", "");
                            obj.put("reg", "");
                            obj.put("package", pkg);
                            obj.put("rech_dt", dateTime);
                            obj.put("exp_dt", "");


                            jsonArray66.put(obj);
                        }


                        System.out.println("✅ Extracted " + jsonArray66.length() + " rows from Connect.");
                        return jsonArray66.toString();
                    } catch (Exception e) {
                        driver.quit();

                        System.out.println("❌ ERROR: " + e.getMessage());

                        JSONObject error = new JSONObject();
                        error.put("status", "error");
                        error.put("message", e.getMessage());

                        return error.toString();
                    }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
        }
        return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
    }

    private static WebElement waitForElement(WebDriver driver, WebDriverWait wait, By locator, String elementName) {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            throw new RuntimeException("UI CHANGE DETECTED: Element not found -> " + elementName + " | Locator: " + locator);
        }
    }

    private static WebElement waitForClickable(WebDriver driver, WebDriverWait wait, By locator, String elementName) {
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException e) {
            throw new RuntimeException("UI CHANGE DETECTED: Element not clickable -> " + elementName + " | Locator: " + locator);
        }
    }
}
