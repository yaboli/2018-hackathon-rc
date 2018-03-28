package hackathon;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;

@RestController
public class DBController {

    @Autowired
    private DBService dbService;

    @GetMapping("/")
    public String greeting() {
        return "GO CONCORDIA-ENGAGE GO!";
    }

    @GetMapping("/send-data")
    public ArrayList<String> doGet() {
        return dbService.getData();
    }

    @PostMapping("/get-data")
    public void doPost() throws IOException {
        dbService.saveDataToDB();
    }

    @DeleteMapping("/delete-all")
    public void doDelete() {
        dbService.deleteAllData();
    }
}
