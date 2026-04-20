package automation;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;

public class AutomationService {

    public static void main(String[] args) throws Exception {

        WebDriver driver;

        if (isFirefoxInstalled()) {
            System.out.println("🦊 Firefox detected. Using GeckoDriver...");
            System.setProperty("webdriver.gecko.driver", "geckodriver.exe");

            FirefoxOptions options = new FirefoxOptions();
//            options.addArguments("--headless"); // headless mode
            options.addPreference("browser.download.folderList", 2);
//            options.addPreference("browser.download.dir", "C:\\data\\downloads");
            options.addPreference("browser.helperApps.neverAsk.saveToDisk",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            options.addPreference("pdfjs.disabled", true);
            options.addPreference("browser.download.manager.showWhenStarting", false);
            options.addPreference("browser.download.useDownloadDir", true);
            options.addPreference("browser.download.alwaysOpenPanel", false);
            options.addPreference("browser.download.manager.closeWhenDone", true);
            options.addPreference("signon.rememberSignons", false);
            options.addPreference("network.proxy.type", 0);
            options.addPreference("dom.webnotifications.enabled", false);

            driver = new FirefoxDriver(options);

        } else {
            System.out.println("⚙️ Firefox not found — falling back to Chrome. Using ChromeDriver...");
            System.setProperty("webdriver.chrome.driver", "chromeDriver.exe");

//        String downloadFilepath = "C:\\data\\downloads\\Fiberish Broadband  Billing Systems.xlsx";

            Map<String, Object> prefs = new HashMap<>();
//            prefs.put("download.default_directory", "C:\\data\\downloads");
            prefs.put("download.prompt_for_download", false);
            prefs.put("download.directory_upgrade", true);
            prefs.put("safebrowsing.enabled", true);
            prefs.put("profile.password_manager_leak_detection", false);

            ChromeOptions options = new ChromeOptions();
            options.setExperimentalOption("prefs", prefs);
//        options.addArguments("--headless=new");           // run headless
            options.addArguments("--window-size=1920,1080");  // correct element rendering
            options.addArguments("--disable-gpu");            // stability for headless

            driver = new ChromeDriver(options);
        }

//        String result = runAutomation(driver, "saadnet", "asdfasdf", "https://partner.fiberish.net.pk/", "partner_fiberish");
//        System.out.println(result);

        driver.quit();
    }

    // 🔍 Helper method to check if Firefox is installed
    private static boolean isFirefoxInstalled() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "where firefox"});
            Scanner scanner = new Scanner(process.getInputStream());
            boolean found = scanner.hasNext();
            scanner.close();
            return found;
        } catch (IOException e) {
            return false;
        }
    }

    public static String solveCaptcha(WebDriver driver) {
        try {
            WebElement captchaImg = driver.findElement(By.id("loginform-captcha-image"));

            // Take screenshot of element
            File src = captchaImg.getScreenshotAs(OutputType.FILE);
            File dest = new File("captcha.png");
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // OCR
            ITesseract image = new Tesseract();
            image.setDatapath("E:\\Tesseract-OCR\\tessdata");

            String result = image.doOCR(dest);

            System.out.println("🔍 Raw OCR Output: " + result);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int extractAndSolve(String text) {
        text = text.replaceAll("[^0-9+]", ""); // keep only digits & +

        String[] parts = text.split("\\+");

        if (parts.length != 2) throw new RuntimeException("Invalid captcha format");

        int num1 = Integer.parseInt(parts[0]);
        int num2 = Integer.parseInt(parts[1]);

        return num1 + num2;
    }

    // 🧩 Core automation logic shared between Firefox & Chrome
    public static String runAutomation(WebDriver driver, String username, String password, String url, String type) {

        File excelFile;
        try {
            switch (type) {
                case "partner_fiberish":
//                    url = "https://partner.fiberish.net.pk/";
//                    username = "saadnet", "friends";
//                    password = "asdfasdf", "123456";

                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

                    // ✅ Dynamic dates
                    LocalDate now = LocalDate.now();
                    String fromDate = now.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    String toDate = now.withDayOfMonth(now.lengthOfMonth()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                    String downloadDir = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Clear old Excel files before download

                    String[] targetFiles = {
                            "Fiberish Broadband Billing Systems.xlsx",
                            "Fiberish Broadband  Billing Systems.xlsx"
                    };
                    for (String fileName : targetFiles) {
                        File f = new File(downloadDir, fileName);
                        if (f.exists()) {
                            f.delete();
                            System.out.println("🧹 Deleted old Excel file(s): " + fileName);
                        }
                    }

                    System.out.println("🚀 Opening Partner Fiberish...");
                    driver.get(url);
//                    driver.get("https://cp.fiberish.net.pk/");
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

                    System.out.println("✅ Login successful, continuing...");

                    // Step 1: Try dealer page
                    System.out.println("📄 Trying dealer sale reports page...");

                    // Step 1: Try dealer
                    driver.get(url + "/dealer/salereports");

                    wait.until(ExpectedConditions.urlContains("/salereports"));
                    Thread.sleep(2000);
                    String currentUrl = driver.getCurrentUrl();
                    System.out.println("🌐 Current URL: " + currentUrl);

                    // Step 2: Check if we are actually on dealer page
                    if (!currentUrl.contains("/dealer/salereports")) {

                        System.out.println("⚠️ Not on dealer page. Trying subdealer...");

                        // Step 3: Fallback to subdealer
                        driver.get(url + "/subdealer/salereports");

                        wait.until(ExpectedConditions.urlContains("/salereports"));

                        currentUrl = driver.getCurrentUrl();
                        System.out.println("🌐 Current URL after fallback: " + currentUrl);

                        // Step 4: Final validation
                        if (!currentUrl.contains("/subdealer/salereports")) {
                            return "{\"status\":\"error\",\"message\":\"Unable to access both dealer and subdealer reports\"}";
                        }
                    }

                    // ✅ Continue execution from here
                    System.out.println("✅ Correct report page loaded: " + currentUrl);

                    wait.until(ExpectedConditions.jsReturnsValue(
                            "return document.readyState === 'complete';"
                    ));

                    java.util.List<WebElement> sellerList = driver.findElements(By.name("seller"));

                    if (!sellerList.isEmpty()) {
                        WebElement hiddenSelect = sellerList.get(0);

                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].value='630'; arguments[0].dispatchEvent(new Event('change'));",
                                hiddenSelect
                        );

                        System.out.println("✅ Seller dropdown found and selected.");
                    } else {
                        System.out.println("⚠️ Seller dropdown not found. Skipping...");
                    }


                    WebElement from = wait.until(
                            ExpectedConditions.elementToBeClickable(By.name("from"))
                    );
                    from.clear();
                    from.sendKeys(fromDate);

                    WebElement to = driver.findElement(By.name("to"));
                    to.clear();
                    to.sendKeys(toDate);
                    System.out.println("📅 Dates selected: " + fromDate + " → " + toDate);

                    System.out.println("🔍 Clicking Search button...");

                    WebElement searchBtn = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//button[@type='submit' and contains(@class,'btn-primary')]")
                            )
                    );

                    // Scroll + click (safe for all browsers)
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", searchBtn);
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchBtn);

                    System.out.println("📥 Clicking excel button...");
                    WebElement excelBtn = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//i[contains(@class,'ft-file-plus')]/parent::span")
                            )
                    );
                    excelBtn.click();

                    // Wait for file download
                    File dir = new File(downloadDir);

                    targetFiles = new String[]{
                            "Fiberish Broadband Billing Systems.xlsx",
                            "Fiberish Broadband  Billing Systems.xlsx"
                    };

                    File latestFile = null;

                    int waitTime = 0;
                    while (waitTime < 20) { // wait max 20 sec
                        for (String fileName : targetFiles) {
                            File f = new File(dir, fileName);
                            if (f.exists()) {
                                latestFile = f;
                                System.out.println("✅ Found file: " + fileName);
                                break;
                            }
                        }

                        if (latestFile != null) break;
                        Thread.sleep(1000);
                        waitTime++;
                    }

                    if (latestFile == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }
                    System.out.println("📊 Download completed. Now editing Excel file...");

                    excelFile = latestFile;

                    if (excelFile == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }


                    FileInputStream fis = new FileInputStream(excelFile);
                    XSSFWorkbook workbook = new XSSFWorkbook(fis);
                    XSSFSheet sheet = workbook.getSheetAt(0);

                    JSONArray jsonArray = new JSONArray();

                    for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                        JSONObject obj = new JSONObject();

                        if (sheet.getRow(i) == null) continue;

                        obj.put("int_id", getCellValue(sheet, i, 1));
                        obj.put("name", "");
                        obj.put("manager", "");
                        obj.put("cnic", "");
                        obj.put("adrs", "");
                        obj.put("stats", "");
                        obj.put("mob", "");
                        obj.put("reg", "");
                        obj.put("package", getCellValue(sheet, i, 2));
                        obj.put("rech_dt", getCellValue(sheet, i, 3));
                        obj.put("exp_dt", "");

                        jsonArray.put(obj);
                    }

                    workbook.close();
                    fis.close();

                    // 🗑️ delete after processing
                    if (excelFile.exists()) {
                        boolean deleted = excelFile.delete();
                        if (deleted) {
                            System.out.println("🗑️ File deleted after processing");
                        } else {
                            System.out.println("⚠️ Failed to delete file");
                        }
                    }

                    return jsonArray.toString();

                case "cp_fiberish":
//                    url = "https://cp.fiberish.net.pk/";
//                    username = "Sanghar";
//                    password = "644444";
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

//                    String fusername = "Sanghar";
//                    String fpassword = "644444";

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

                    System.out.println("📄 Navigating to sale reports...");
                    driver.get(url + "/reseller/salereports");

                    WebElement dateInput = wait2.until(
                            ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"panel\"]/div[2]/div[1]/div/div[1]/div/form/div/div[1]/div/input"))
                    );
                    dateInput.clear();
                    dateInput.sendKeys("2025-10-1");

                    Thread.sleep(2000);

                    driver.findElement(By.xpath("//*[@id=\"panel\"]/div[2]/div[1]/div/div[1]/div/form/div/div[4]/div")).click();

                    Thread.sleep(3000);

                    System.out.println("📥 Clicking download button...");
                    wait2.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//*[@id=\"DataTables_Table_0_wrapper\"]/div[2]/button[3]")
                    )).click();

                    // ⏳ Wait for file
                    File latestFile2 = null;
                    int waitTime2 = 0;

                    while (waitTime2 < 20) {
                        for (String fileName : targetFiles2) {
                            File f = new File(downloadDir2, fileName);
                            if (f.exists()) {
                                latestFile2 = f;
                                System.out.println("✅ Found file: " + fileName);
                                break;
                            }
                        }
                        if (latestFile2 != null) break;

                        Thread.sleep(1000);
                        waitTime2++;
                    }

                    if (latestFile2 == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
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
                        obj.put("name", "");
                        obj.put("manager", "");
                        obj.put("cnic", "");
                        obj.put("adrs", "");
                        obj.put("stats", "");
                        obj.put("mob", "");
                        obj.put("reg", "");
                        obj.put("package", getCellValue(sheet2, i, 2));
                        obj.put("rech_dt", getCellValue(sheet2, i, 3));
                        obj.put("exp_dt", "");

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

                    String downloadDir3 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old file if exists
                    String[] targetFiles3 = {"sas4_export.xlsx"};

                    // 🧹 Delete old files BEFORE
                    for (String fileName : targetFiles3) {
                        File f = new File(downloadDir3, fileName);
                        if (f.exists()) {
                            f.delete();
                            System.out.println("🧹 Deleted old file: " + fileName);
                        }
                    }

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

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//span[contains(@class,'pi-times')]/parent::button")
                    )).click();

                    // 📄 Navigate to report page
                    driver.get(url + "#/report/activations");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    wait3.until(ExpectedConditions.urlContains("activations"));
                    System.out.println("📄 Navigated to activations report");

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    // 📥 Click Export Button
                    WebElement exportBtn = wait3.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//a[@ptooltip='export table data']")
                            )
                    );

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", exportBtn);

                    System.out.println("📥 Export button clicked");

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile3 = null;
                    int waitTime3 = 0;

                    while (waitTime3 < 20) {
                        for (String fileName : targetFiles3) {
                            File f = new File(downloadDir3, fileName);
                            if (f.exists()) {
                                latestFile3 = f;
                                System.out.println("✅ Found file: " + fileName);
                                break;
                            }
                        }
                        if (latestFile3 != null) break;

                        Thread.sleep(1000);
                        waitTime3++;
                    }

                    if (latestFile3 == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }

                    // 📊 CONVERT EXCEL → JSON
                    FileInputStream fis3 = new FileInputStream(latestFile3);
                    XSSFWorkbook workbook3 = new XSSFWorkbook(fis3);
                    XSSFSheet sheet3 = workbook3.getSheetAt(0);

                    JSONArray jsonArray3 = new JSONArray();

                    for (int i = 1; i <= sheet3.getLastRowNum(); i++) {

                        if (sheet3.getRow(i) == null) continue;

                        JSONObject obj = new JSONObject();
                        obj.put("int_id", getCellValue(sheet3, i, 1));
                        String firstName = String.valueOf(getCellValue(sheet3, i, 2)).trim();
                        String lastName = String.valueOf(getCellValue(sheet3, i, 3)).trim();
                        obj.put("name", (firstName + " " + lastName).replaceAll("null", "").trim());
//                        obj.put("name", fullName);
                        obj.put("manager", getCellValue(sheet3, i, 4));
                        obj.put("cnic", "");
                        obj.put("adrs", "");
                        obj.put("stats", "");
                        obj.put("mob", "");
                        obj.put("reg", "");
                        obj.put("package", getCellValue(sheet3, i, 7));
                        obj.put("rech_dt", getCellValue(sheet3, i, 9));
                        obj.put("exp_dt", "");

                        jsonArray3.put(obj);
                    }

                    workbook3.close();
                    fis3.close();

                    wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    // 🗑️ DELETE FILE
                    if (latestFile3.exists()) {
                        latestFile3.delete();
                        System.out.println("🗑️ skynet file deleted");
                        System.out.println("The End");
                    }

                    return jsonArray3.toString();

                case "connect":

                    WebDriverWait wait4 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    // ✅ Dynamic dates
                    LocalDate now4 = LocalDate.now();
                    String fromDate4 = now4.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    String toDate4 = now4.withDayOfMonth(now4.lengthOfMonth()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                    System.out.println("🚀 Opening Connect...");
                    driver.get(url + "login/");

                    // 🔐 LOGIN
                    wait4.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("input[type='text'].form-control")
                    )).sendKeys(username);

                    driver.findElement(By.cssSelector("input[type='password'].form-control")).sendKeys(password);

                    wait4.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit' and contains(@class,'btn-submit')]")
                    )).click();

                    // ⏳ Wait after login click
                    Thread.sleep(2000);

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

                    // 📄 Navigate to recharge logs
                    driver.get(url + "customers/report/recharge-logs");

                    WebElement dateRangeInput4 = wait4.until(
                            ExpectedConditions.elementToBeClickable(By.name("daterange"))
                    );

                    // Click first (important)
                    dateRangeInput4.click();
                    Thread.sleep(500);

                    // Clear and type
                    dateRangeInput4.sendKeys(Keys.CONTROL + "a");
                    dateRangeInput4.sendKeys(Keys.DELETE);

                    dateRangeInput4.sendKeys(fromDate4 + " - " + toDate4);
                    dateRangeInput4.sendKeys(Keys.ENTER);

                    System.out.println("✅ Date range entered manually");

                    Thread.sleep(1000);

                    // 🔍 Click Search
                    WebElement searchBtn4 = wait4.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//input[@type='submit' and @value='Search']")
                            )
                    );
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", searchBtn4);
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchBtn4);

                    System.out.println("🔍 Search clicked. Waiting for table...");
                    Thread.sleep(3000);

                    // 📊 Extract table rows
                    wait4.until(ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//div[@class='table-responsive']//tbody/tr")
                    ));

                    java.util.List<WebElement> rows4 = driver.findElements(
                            By.xpath("//div[@class='table-responsive']//tbody/tr")
                    );

                    JSONArray jsonArray4 = new JSONArray();

                    for (WebElement row : rows4) {
                        java.util.List<WebElement> cols = row.findElements(By.tagName("td"));
                        if (cols.size() < 6) continue;

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
                        obj.put("stats", "");
                        obj.put("mob", "");
                        obj.put("reg", "");
                        obj.put("package", pkg);
                        obj.put("rech_dt", dateTime);
                        obj.put("exp_dt", "");


                        jsonArray4.put(obj);
                    }

                    System.out.println("✅ Extracted " + jsonArray4.length() + " rows from Connect.");
                    return jsonArray4.toString();

                case "national":

                    WebDriverWait wait5 = new WebDriverWait(driver, Duration.ofSeconds(20));
                    String downloadDir5 = System.getProperty("user.home") + "\\Downloads";

                    // 🧹 Delete old file if exists
                    File folder5 = new File(downloadDir5);
                    File[] files5 = folder5.listFiles();

                    if (files5 != null) {
                        for (File f : files5) {
                            if (f.getName().contains("Customer List")
                                    && f.getName().contains("-north")
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
                        Thread.sleep(3000);

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
                        if (currentUrl5.equals("https://partner.nationalbroadband.pk/") && !captchaError) {
                            loginSuccess = true;
                            break;
                        }

                        System.out.println("❌ Login failed, retrying...");
                    }

                    if (!loginSuccess) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login failed after retries\"}";
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

                    wait5.until(webDriver ->
                            ((JavascriptExecutor) webDriver)
                                    .executeScript("return document.readyState")
                                    .equals("complete")
                    );

                    System.out.println("🔍 Clicking Search button...");

                    WebElement searchBtn5 = wait5.until(
                            ExpectedConditions.visibilityOfElementLocated(By.id("btnSubmit"))
                    );
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", searchBtn5);

                    Thread.sleep(1000);

                    try {
                        searchBtn5.click();
                    } catch (Exception e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", searchBtn5);
                    }

                    System.out.println("🔍 Search clicked");

                    Thread.sleep(4000);

                    WebElement exportBtn5 = wait5.until(
                            ExpectedConditions.visibilityOfElementLocated(By.id("btnExport"))
                    );
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", exportBtn5);

                    Thread.sleep(1000);

                    try {
                        exportBtn5.click();
                    } catch (Exception e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", exportBtn5);
                    }

                    System.out.println("📥 Export button clicked");

                    // ⏳ WAIT FOR DOWNLOAD
                    File latestFile5 = null;
                    int waitTime5 = 0;

                    while (waitTime5 < 30) {

                        File folder = new File(downloadDir5);
                        File[] files = folder.listFiles();

                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().contains("Customer List") && f.getName().endsWith(".csv")) {
                                    latestFile5 = f;
                                    System.out.println("✅ Found file: " + f.getName());
                                    break;
                                }
                            }
                        }

                        if (latestFile5 != null) break;

                        Thread.sleep(1000);
                        waitTime5++;
                    }

                    if (latestFile5 == null) {
                        return "{\"status\":\"error\",\"message\":\"Excel not found\"}";
                    }
                    // 📊 CONVERT EXCEL → JSON
                    BufferedReader br = new BufferedReader(new FileReader(latestFile5));
                    String line;

                    JSONArray jsonArray5 = new JSONArray();
                    boolean isHeader = true;

                    while ((line = br.readLine()) != null) {

                        if (isHeader) { // skip header
                            isHeader = false;
                            continue;
                        }

                        String[] data = line.split(",");

                        JSONObject obj = new JSONObject();

                        obj.put("int_id", data.length > 3 ? data[3] : "");
                        obj.put("name", data.length > 5 ? data[5] : "");
                        obj.put("manager", "");
                        obj.put("cnic", data.length > 6 ? data[6] : "");
                        obj.put("adrs", data.length > 7 ? data[7] : "");
                        obj.put("stats", data.length > 8 ? data[8] : "");
                        obj.put("mob", data.length > 12 ? data[12] : "");
                        obj.put("reg", data.length > 15 ? data[15] : "");
                        obj.put("package", data.length > 17 ? data[17] : "");
                        obj.put("rech_dt", data.length > 9 ? data[9] : "");
                        obj.put("exp_dt", data.length > 23 ? data[23] : "");

                        jsonArray5.put(obj);
                    }

                    br.close();

                    // 🗑️ DELETE FILE
                    if (latestFile5.exists()) {
                        latestFile5.delete();
                        System.out.println("🗑️ national customer list file deleted");
                    }

                    System.out.println("✅ Done");

                    return jsonArray5.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
        }
        return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
    }

    private static String getCellValue(@NotNull XSSFSheet sheet, int rowIndex, int colIndex) {
        if (sheet.getRow(rowIndex) == null) return "";
        if (sheet.getRow(rowIndex).getCell(colIndex) == null) return "";
        return sheet.getRow(rowIndex).getCell(colIndex).toString();
    }
}