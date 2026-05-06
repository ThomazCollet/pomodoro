module com.thomazcollet.pomodoro {
    // 1. Módulos necessários (Dependências)
    requires javafx.controls;
    requires javafx.fxml;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires java.sql;

    // 2. Acesso para Reflexão (FUNDAMENTAL PARA TESTES)
    // Abrir o pacote de forma geral resolve o conflito com o "unnamed module" do JUnit
    opens com.thomazcollet.service; 

    // O JavaFX precisa de reflexão para acessar seus controladores e arquivos FXML
    opens com.thomazcollet to javafx.fxml;
    opens com.thomazcollet.ui to javafx.fxml;

    // 3. Exportar pacotes
    exports com.thomazcollet;
}