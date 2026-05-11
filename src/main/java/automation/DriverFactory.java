package automation;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class DriverFactory {

  public static WebDriver createDriver() {

    if (isFirefoxInstalled()) {
      System.out.println("🦊 Firefox detected. Using GeckoDriver...");
      //            System.setProperty("webdriver.gecko.driver", "geckodriver.exe");

      try {
        Runtime.getRuntime().exec("taskkill /F /IM firefox.exe /T");
        Runtime.getRuntime().exec("taskkill /F /IM geckodriver.exe /T");
      } catch (Exception e) {
        System.out.println("No existing processes to kill");
      }

      WebDriverManager.firefoxdriver().setup();

      FirefoxOptions options = new FirefoxOptions();
      //            options.addArguments("--headless"); // headless mode
      options.addArguments("--remote-allow-origins=*");
      options.addArguments("--no-sandbox");
      options.addArguments("--disable-dev-shm-usage");
      options.addPreference("browser.download.folderList", 2);
      //            options.addPreference("browser.download.dir", "C:\\data\\downloads");
      options.addPreference(
          "browser.helperApps.neverAsk.saveToDisk",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      options.addPreference("pdfjs.disabled", true);
      options.addPreference("browser.download.manager.showWhenStarting", false);
      options.addPreference("browser.download.useDownloadDir", true);
      options.addPreference("browser.download.alwaysOpenPanel", false);
      options.addPreference("browser.download.manager.closeWhenDone", true);
      options.addPreference("signon.rememberSignons", false);
      options.addPreference("network.proxy.type", 0);
      options.addPreference("dom.webnotifications.enabled", false);
      options.addPreference("dom.webdriver.enabled", false);
      options.addPreference("useAutomationExtension", false);

      return new FirefoxDriver(options);

    } else {
      System.out.println("⚙️ Firefox not found — falling back to Chrome. Using ChromeDriver...");
      //            System.setProperty("webdriver.chrome.driver", "chromeDriver.exe");
      WebDriverManager.chromedriver().setup();
      //        String downloadFilepath = "C:\\data\\downloads\\Fiberish Broadband  Billing
      // Systems.xlsx";

      Map<String, Object> prefs = new HashMap<>();
      //            prefs.put("download.default_directory", "C:\\data\\downloads");
      prefs.put("download.prompt_for_download", false);
      prefs.put("download.directory_upgrade", true);
      prefs.put("safebrowsing.enabled", true);
      prefs.put("profile.password_manager_leak_detection", false);

      ChromeOptions options = new ChromeOptions();
      options.setExperimentalOption("prefs", prefs);
      //        options.addArguments("--headless=new");           // run headless
      options.addArguments("--window-size=1920,1080"); // correct element rendering
      options.addArguments("--disable-gpu"); // stability for headless
      options.addArguments("--no-sandbox");
      options.addArguments("--disable-dev-shm-usage");

      return new ChromeDriver(options);
    }
  }

  private static boolean isFirefoxInstalled() {
    try {
      Process process = Runtime.getRuntime().exec(new String[] {"cmd", "/c", "where firefox"});
      Scanner scanner = new Scanner(process.getInputStream());
      boolean found = scanner.hasNext();
      scanner.close();
      return found;
    } catch (IOException e) {
      return false;
    }
  }
}
