package automation;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class DriverFactory {

    public static WebDriver createDriver() {

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

            return new FirefoxDriver(options);

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

            return new ChromeDriver(options);
        }
    }

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
}