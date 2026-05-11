package automation.controller;

import automation.AutomationService;
import automation.LoginService;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/panel")
public class Controller {

  @PostMapping("/login")
  public String login(
      @RequestBody(required = false) Map<String, String> request,
      @RequestParam(value = "username", required = false) String username,
      @RequestParam(value = "password", required = false) String password,
      @RequestParam(value = "url", required = false) String url,
      @RequestParam(value = "type", required = false) String type) {

    if (request != null) {
      username = request.getOrDefault("username", username);
      password = request.getOrDefault("password", password);
      url = request.getOrDefault("url", url);
      type = request.getOrDefault("type", type);
    }

    if (username == null || password == null || url == null || type == null) {
      return "{\"status\":\"error\",\"message\":\"Missing required parameters\"}";
    }

    WebDriver driver = new FirefoxDriver();

    try {
      String result = LoginService.login(driver, username, password, url, type);
      driver.quit();
      return result;

    } catch (Exception e) {
      driver.quit();
      return "{\"status\":\"error\",\"message\":\"Wrong Login credentials\"}";
    }
  }

  @PostMapping("/get-report")
  public String getReport(
      @RequestBody(required = false) Map<String, String> request,
      @RequestParam(value = "username", required = false) String username,
      @RequestParam(value = "password", required = false) String password,
      @RequestParam(value = "url", required = false) String url,
      @RequestParam(value = "type", required = false) String type) {

    if (request != null) {
      username = request.getOrDefault("username", username);
      password = request.getOrDefault("password", password);
      url = request.getOrDefault("url", url);
      type = request.getOrDefault("type", type);
    }

    if (username == null || password == null || url == null || type == null) {
      return "{\"status\":\"error\",\"message\":\"Missing required parameters\"}";
    }

    WebDriver driver = new FirefoxDriver();

    try {
      String result = AutomationService.runAutomation(driver, username, password, url, type);
      driver.quit();
      return result;

    } catch (Exception e) {
      driver.quit();
      return "{\"status\":\"error\",\"message\":\"Something went wrong\"}";
    }
  }
}
