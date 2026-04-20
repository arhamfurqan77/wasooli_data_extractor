package automation;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;

public class Autofiberish {

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
//        driver.get("https://cp.fiberish.net.pk/");
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"username\"]"))).sendKeys(username);
                    driver.findElement(By.xpath("//*[@id=\"password\"]")).sendKeys(password);
                    WebElement loginBtn = wait.until(
                            ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(.,'Sign In')]"))
                    );
                    loginBtn.click();
                    Thread.sleep(2000);

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
                        obj.put("package", getCellValue(sheet, i, 2));
                        obj.put("exp_dt", getCellValue(sheet, i, 3));

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

                    Thread.sleep(2000);

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
                        obj.put("package", getCellValue(sheet2, i, 2));
                        obj.put("exp_dt", getCellValue(sheet2, i, 3));

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
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
        }
        return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
    }
    private static String getCellValue (@NotNull XSSFSheet sheet, int rowIndex, int colIndex){
        if (sheet.getRow(rowIndex) == null) return "";
        if (sheet.getRow(rowIndex).getCell(colIndex) == null) return "";
        return sheet.getRow(rowIndex).getCell(colIndex).toString();
    }
}