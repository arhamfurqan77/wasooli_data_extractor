package automation.controller;

import automation.AutomationService;
import automation.LoginService;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/fiberish")
public class Controller {

    @GetMapping("/login")
    public String login(@RequestBody Map<String, String> request) {

        String username = request.get("username");
        String password = request.get("password");
        String url = request.get("url");
        String type = request.get("type");

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
    public String getReport(@RequestBody Map<String, String> request) {

        String username = request.get("username");
        String password = request.get("password");
        String url = request.get("url");
        String type = request.get("type");

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