package automation;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.openqa.selenium.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;

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

                    System.out.println("✅ Optix login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "partner_cxtreme":

                    WebDriverWait wait13 = new WebDriverWait(driver, Duration.ofSeconds(20));
                    String downloadDir13 = System.getProperty("user.home") + "\\Downloads";

                    System.out.println("🚀 Opening Alfa Broadband...");

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
                        Thread.sleep(3000);

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
                    System.out.println("✅ Alfa Broadband login successful!");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

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

                    System.out.println("✅ Login successful");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "billing_galaxy":

                    WebDriverWait wait15 = new WebDriverWait(driver, Duration.ofSeconds(20));

                    String downloadDir15 = System.getProperty("user.home") + "\\Downloads";

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
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";

                case "jazz_ftth":

                    WebDriverWait wait16 = new WebDriverWait(driver, Duration.ofSeconds(25));

                    System.out.println("🚀 Opening Jazz FTTH Portal...");

                    int maxAttempts16 = 3;
                    loginSuccess = false;

                    for (int attempt = 1; attempt <= maxAttempts16; attempt++) {

                        System.out.println("🔁 Login Attempt: " + attempt);

                        try {
                            driver.get(url);

                            // ⏳ Wait for username field
                            wait16.until(ExpectedConditions.visibilityOfElementLocated(
                                    By.id("user_login")
                            )).sendKeys(username);

                            driver.findElement(By.id("user_pass")).sendKeys(password);

                            // 🧠 Get value directly from hidden input
                            String sumValue = wait16.until(ExpectedConditions.presenceOfElementLocated(
                                    By.id("sum")
                            )).getAttribute("value");

                            System.out.println("🧮 Captcha (from hidden): " + sumValue);

                            // ✍️ Enter value
                            WebElement captchaInput = driver.findElement(By.id("user"));
                            captchaInput.clear();
                            captchaInput.sendKeys(sumValue);

                            // 🔥 IMPORTANT: trigger JS event manually
                            ((JavascriptExecutor) driver).executeScript(
                                    "arguments[0].dispatchEvent(new Event('keyup'));", captchaInput
                            );

                            // 🔘 Wait until button enabled then click
                            loginBtn = wait16.until(ExpectedConditions.presenceOfElementLocated(
                                    By.id("login_btn")
                            ));

                            wait16.until(driver1 ->
                                    driver1.findElement(By.id("login_btn")).isEnabled()
                            );

                            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginBtn);

                            System.out.println("🔐 Login clicked");

                            // ⏳ Wait for redirect
                            Thread.sleep(5000);

                            String currentUrl = driver.getCurrentUrl();
                            System.out.println("🌐 Current URL: " + currentUrl);

                            if (!currentUrl.contains("login")) {
                                loginSuccess = true;
                                break;
                            }

                        } catch (Exception e) {
                            System.out.println("⚠️ Login attempt error: " + e.getMessage());
                        }

                        System.out.println("❌ Login failed, retrying...");
                    }

                    if (!loginSuccess) {
                        driver.quit();
                        return "{\"status\":\"error\",\"message\":\"Login failed after retries\"}";
                    }

                    System.out.println("✅ Jazz FTTH login successful!");
                    return "{\"status\":\"success\",\"message\":\"Login successful\"}";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
        }
        return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
    }
}