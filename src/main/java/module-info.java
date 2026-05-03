module com.thomazcollet.pomodoro {
    // 1. Módulos necessários (Dependências)
    requires javafx.controls;
    requires javafx.fxml;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind; // Necessário para o Jackson funcionar
    requires java.sql; // Necessário para o SQLite/JDBC

    // 2. Abrir pacotes para o JavaFX
    // O JavaFX precisa de reflexão para acessar seus controladores e arquivos FXML
    opens com.thomazcollet to javafx.fxml; 
    opens com.thomazcollet.ui to javafx.fxml;

    // 3. Exportar pacotes
    // Permite que outros módulos (incluindo o runtime do Java) vejam suas classes
    exports com.thomazcollet;
}