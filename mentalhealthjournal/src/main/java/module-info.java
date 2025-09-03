module com.example.mentalhealthjournal {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.mentalhealthjournal to javafx.fxml;
    exports com.example.mentalhealthjournal;
}