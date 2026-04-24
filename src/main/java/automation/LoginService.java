package automation;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;

public class LoginService {

    public static void main(String[] args) throws Exception {

        WebDriver driver = DriverFactory.createDriver();
        driver.quit();
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
            image.setDatapath(System.getProperty("user.dir") + "\\Tesseract-OCR\\tessdata");

            image.setTessVariable("tessedit_char_whitelist", "lo0123456789+");
            image.setPageSegMode(7); // Treat image as single line

            String result = image.doOCR(dest);

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

        if (parts.length != 2) {
            throw new RuntimeException("Invalid captcha format after cleaning: " + text);
        }

        int num1 = Integer.parseInt(parts[0]);
        int num2 = Integer.parseInt(parts[1]);

        return num1 + num2;
    }

    // 🧩 Core login logic shared between Firefox & Chrome
    public static String login(WebDriver driver, String username, String password, String url, String type) {

        try {
            switch (type) {
                case "partner_fiberish":
//                    url = "https://partner.fiberish.net.pk/";
//                    username = "saadnet", "friends";
//                    password = "asdfasdf", "123456";

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "cp_fiberish":
//                    url = "https://cp.fiberish.net.pk/";
//                    username = "Sanghar";
//                    password = "644444";
                    WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(20));

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "connect":

                    WebDriverWait wait4 = new WebDriverWait(driver, Duration.ofSeconds(20));

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "national":

                    WebDriverWait wait5 = new WebDriverWait(driver, Duration.ofSeconds(20));

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
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "mak_net":

                    WebDriverWait wait7 = new WebDriverWait(driver, Duration.ofSeconds(20));

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "daddy_sas":

                    WebDriverWait wait8 = new WebDriverWait(driver, Duration.ofSeconds(20));

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
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "turbo_zong":

                    WebDriverWait wait9 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    System.out.println("🚀 Opening Turbo Zong...");

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
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "galaxy":

                    WebDriverWait wait10 = new WebDriverWait(driver, Duration.ofSeconds(20));

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "alfa":

                    WebDriverWait wait11 = new WebDriverWait(driver, Duration.ofSeconds(20));

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
                        Thread.sleep(3000);

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
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
        }
        return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
    }
}